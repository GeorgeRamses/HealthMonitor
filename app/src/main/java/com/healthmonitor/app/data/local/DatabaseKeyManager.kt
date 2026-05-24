package com.healthmonitor.app.data.local

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

object DatabaseKeyManager {

    private const val TAG = "DatabaseKeyManager"
    private const val PREFS_FILE   = "db_key_store"
    private const val KEY_DB_PASS  = "db_encryption_key"
    private const val KEY_LENGTH   = 32  // 256-bit key → 64 hex chars
    private const val DB_NAME = "health_monitor_db"
    private const val ENCRYPTION_FLAG_FILE = "db_encrypted.flag"

    /**
     * Returns the database passphrase as a ByteArray.
     * On first call a cryptographically random key is generated and stored
     * in EncryptedSharedPreferences, which is backed by the Android Keystore.
     * On subsequent calls the stored key is returned unchanged.
     *
     * The ByteArray is zeroed out by the caller (Room/SQLCipher) after use.
     */
    fun getOrCreateKey(context: Context): ByteArray {
        var prefs = runCatching { encryptedPrefs(context) }
            .recoverCatching { error ->
                Log.e(TAG, "Encrypted key store is unreadable; resetting encrypted database", error)
                resetEncryptedDatabaseState(context)
                encryptedPrefs(context)
            }
            .getOrThrow()

        prefs.getString(KEY_DB_PASS, null)?.let { existing ->
            val decoded = runCatching { hexToBytes(existing) }
            if (decoded.isSuccess) return decoded.getOrThrow()

            Log.e(TAG, "Stored database key is invalid; resetting encrypted database", decoded.exceptionOrNull())
            resetEncryptedDatabaseState(context)
            prefs = encryptedPrefs(context)
        }

        // First launch — generate a new random key
        val keyBytes = ByteArray(KEY_LENGTH).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_DB_PASS, bytesToHex(keyBytes)).apply()
        return keyBytes
    }

    /**
     * Clears the stored encryption key, forcing a new one to be generated on next call.
     * Used when the database is corrupted and needs to be rebuilt.
     */
    fun clearKey(context: Context) {
        try {
            val prefs = encryptedPrefs(context)
            prefs.edit().remove(KEY_DB_PASS).apply()
        } catch (e: Exception) {
            // Silently fail — the key will be regenerated anyway
        }
    }

    private fun encryptedPrefs(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    private fun resetEncryptedDatabaseState(context: Context) {
        context.deleteSharedPreferences(PREFS_FILE)

        listOf(
            DB_NAME,
            "$DB_NAME-wal",
            "$DB_NAME-shm",
            "$DB_NAME-journal"
        ).forEach { name ->
            runCatching { context.getDatabasePath(name).delete() }
        }

        runCatching { java.io.File(context.filesDir, ENCRYPTION_FLAG_FILE).delete() }
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
