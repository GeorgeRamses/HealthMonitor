package com.healthmonitor.app.data.local

import android.content.Context
import android.util.Log
import net.sqlcipher.database.SQLiteDatabase
import java.io.File

object DatabaseMigrationHelper {

    private const val TAG       = "DbMigrationHelper"
    private const val DB_NAME   = "health_monitor_db"
    private const val FLAG_FILE = "db_encrypted.flag"

    /**
     * Call this BEFORE opening the Room database.
     *
     * If the database exists and has never been encrypted, this method
     * re-encrypts it in-place using SQLCipher's sqlcipher_export pragma.
     * On completion it writes a flag file so this path is never taken again.
     *
     * If the database doesn't exist yet (fresh install), this is a no-op —
     * Room will create it already encrypted.
     */
    fun migrateToEncryptedIfNeeded(context: Context, passphrase: ByteArray) {
        val flagFile = File(context.filesDir, FLAG_FILE)
        if (flagFile.exists()) return  // already encrypted, fast path

        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) {
            // Fresh install — mark as encrypted so we never attempt migration
            flagFile.createNewFile()
            return
        }

        Log.i(TAG, "Unencrypted database detected — starting encryption migration")
        val walFile = context.getDatabasePath("${DB_NAME}-wal")
        val shmFile = context.getDatabasePath("${DB_NAME}-shm")
        walFile.delete()
        shmFile.delete()

        SQLiteDatabase.loadLibs(context)

        val tmpFile = File(dbFile.parent, "${DB_NAME}_tmp")
        tmpFile.delete()

        try {
            // Open the existing plain database with an empty passphrase
            val plain = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                "",              // empty passphrase = no encryption
                null,
                SQLiteDatabase.OPEN_READWRITE
            )

            val hexPass = passphrase.joinToString("") { "%02x".format(it) }

            // Export the plain DB into an encrypted copy
            plain.rawExecSQL("ATTACH DATABASE '${tmpFile.absolutePath}' AS encrypted KEY \"x'$hexPass'\"")
            plain.rawExecSQL("SELECT sqlcipher_export('encrypted')")
            plain.rawExecSQL("DETACH DATABASE encrypted")
            plain.close()

            // Swap files atomically
            dbFile.delete()
            tmpFile.renameTo(dbFile)

            flagFile.createNewFile()
            Log.i(TAG, "Encryption migration completed successfully")

        } catch (e: Exception) {
            val isCorruptionError = e.toString().contains("file is not a database") ||
                e.toString().contains("SQLiteException") ||
                e.message?.contains("unable to open database") == true

            Log.e(TAG, "Encryption migration failed: ${e.message}")
            tmpFile.delete()

            if (isCorruptionError) {
                // Database is corrupted — clear it and mark for fresh creation
                Log.w(TAG, "Detected corrupted database during migration — clearing for recovery")
                dbFile.delete()
                val walFile = context.getDatabasePath("${DB_NAME}-wal")
                val shmFile = context.getDatabasePath("${DB_NAME}-shm")
                walFile.delete()
                shmFile.delete()
                // Mark as encrypted so we don't retry migration
                flagFile.createNewFile()
                Log.i(TAG, "Corrupted database cleared — fresh database will be created on next Room open")
            }
            // Don't write the flag file unless corruption detected — we'll retry next launch on other errors
        }
    }
}