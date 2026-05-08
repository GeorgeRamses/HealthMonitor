package com.healthmonitor.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.healthmonitor.app.data.local.dao.*
import com.healthmonitor.app.data.local.entities.*

@Database(
    entities = [
        PatientEntity::class,
        MedicationEntity::class,
        MedicationScheduleEntity::class,
        MedicationLogEntity::class,
        BloodPressureEntity::class,
        BodyTemperatureEntity::class,
        SymptomEntity::class,
        InhalerLogEntity::class,
        LabTestEntity::class,
        DoctorNoteEntity::class,
        CaseEntity::class,
        LabReportEntity::class,
        LabReportItemEntity::class
    ],
    version = 10,
    exportSchema = true
)
abstract class HealthMonitorDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun medicationDao(): MedicationDao
    abstract fun medicationScheduleDao(): MedicationScheduleDao
    abstract fun medicationLogDao(): MedicationLogDao
    abstract fun bloodPressureDao(): BloodPressureDao
    abstract fun bodyTemperatureDao(): BodyTemperatureDao
    abstract fun symptomDao(): SymptomDao
    abstract fun inhalerLogDao(): InhalerLogDao
    abstract fun labTestDao(): LabTestDao
    abstract fun doctorNoteDao(): DoctorNoteDao
    abstract fun caseDao(): CaseDao
    abstract fun labReportDao(): LabReportDao
    abstract fun labReportItemDao(): LabReportItemDao

    companion object {
        @Volatile
        private var INSTANCE: HealthMonitorDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE blood_pressure_readings ADD COLUMN oxygenSaturation INTEGER")
                db.execSQL("ALTER TABLE medication_logs ADD COLUMN scheduledTime TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medications ADD COLUMN caseId INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS cases (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, patientId INTEGER NOT NULL, doctorName TEXT, title TEXT NOT NULL, notes TEXT, isClosed INTEGER NOT NULL DEFAULT 0, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN KEY(patientId) REFERENCES patients(id) ON DELETE CASCADE)"
                )
            }
        }

        // Migration 3→4: full schema rebuild — old DB used INTEGER PKs and updatedAt;
        // new schema requires TEXT UUID PKs, lastModifiedAt, isDeleted on every table.
        // All existing data is dropped (dev-only database, incompatible structure).
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Drop all old tables in reverse FK dependency order
                db.execSQL("DROP TABLE IF EXISTS medication_schedules")
                db.execSQL("DROP TABLE IF EXISTS medication_logs")
                db.execSQL("DROP TABLE IF EXISTS medications")
                db.execSQL("DROP TABLE IF EXISTS cases")
                db.execSQL("DROP TABLE IF EXISTS doctor_notes")
                db.execSQL("DROP TABLE IF EXISTS lab_tests")
                db.execSQL("DROP TABLE IF EXISTS inhaler_logs")
                db.execSQL("DROP TABLE IF EXISTS symptoms")
                db.execSQL("DROP TABLE IF EXISTS blood_pressure_readings")
                db.execSQL("DROP TABLE IF EXISTS patients")

                // Recreate all tables matching current Room entity definitions exactly
                db.execSQL(
                    """
                    CREATE TABLE patients (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        age INTEGER NOT NULL,
                        gender TEXT NOT NULL,
                        bloodType TEXT,
                        medicalConditions TEXT NOT NULL,
                        emergencyContact TEXT,
                        emergencyPhone TEXT,
                        createdAt INTEGER NOT NULL,
                        lastModifiedAt INTEGER NOT NULL,
                        isDeleted INTEGER NOT NULL
                    )
                """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE cases (
                        id TEXT PRIMARY KEY NOT NULL,
                        patientId TEXT NOT NULL,
                        doctorName TEXT,
                        title TEXT NOT NULL,
                        notes TEXT,
                        isClosed INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        lastModifiedAt INTEGER NOT NULL,
                        isDeleted INTEGER NOT NULL
                    )
                """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE medications (
                        id TEXT PRIMARY KEY NOT NULL,
                        patientId TEXT NOT NULL,
                        caseId TEXT,
                        name TEXT NOT NULL,
                        dosage TEXT NOT NULL,
                        unit TEXT NOT NULL,
                        frequency TEXT NOT NULL,
                        timesPerDay INTEGER NOT NULL,
                        scheduledTimes TEXT NOT NULL,
                        startDate INTEGER NOT NULL,
                        endDate INTEGER,
                        notes TEXT,
                        isActive INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        lastModifiedAt INTEGER NOT NULL,
                        isDeleted INTEGER NOT NULL
                    )
                """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE medication_schedules (
                        id TEXT PRIMARY KEY NOT NULL,
                        medicationId TEXT NOT NULL,
                        scheduledTime TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        lastModifiedAt INTEGER NOT NULL,
                        isDeleted INTEGER NOT NULL,
                        FOREIGN KEY(medicationId) REFERENCES medications(id) ON DELETE CASCADE
                    )
                """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE medication_logs (
                        id TEXT PRIMARY KEY NOT NULL,
                        medicationId TEXT NOT NULL,
                        patientId TEXT NOT NULL,
                        date INTEGER NOT NULL,
                        scheduledTime TEXT NOT NULL,
                        time INTEGER NOT NULL,
                        taken INTEGER NOT NULL,
                        notes TEXT,
                        createdAt INTEGER NOT NULL,
                        lastModifiedAt INTEGER NOT NULL,
                        isDeleted INTEGER NOT NULL
                    )
                """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE blood_pressure_readings (
                        id TEXT PRIMARY KEY NOT NULL,
                        patientId TEXT NOT NULL,
                        systolic INTEGER NOT NULL,
                        diastolic INTEGER NOT NULL,
                        pulse INTEGER,
                        oxygenSaturation INTEGER,
                        date INTEGER NOT NULL,
                        time INTEGER NOT NULL,
                        notes TEXT,
                        createdAt INTEGER NOT NULL,
                        lastModifiedAt INTEGER NOT NULL,
                        isDeleted INTEGER NOT NULL
                    )
                """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE symptoms (
                        id TEXT PRIMARY KEY NOT NULL,
                        patientId TEXT NOT NULL,
                        date INTEGER NOT NULL,
                        time INTEGER NOT NULL,
                        symptomType TEXT NOT NULL,
                        severity TEXT NOT NULL,
                        notes TEXT,
                        inhalerUsed INTEGER NOT NULL,
                        improvementAfterInhaler INTEGER,
                        createdAt INTEGER NOT NULL,
                        lastModifiedAt INTEGER NOT NULL,
                        isDeleted INTEGER NOT NULL
                    )
                """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE inhaler_logs (
                        id TEXT PRIMARY KEY NOT NULL,
                        patientId TEXT NOT NULL,
                        date INTEGER NOT NULL,
                        time INTEGER NOT NULL,
                        inhalerType TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        notes TEXT,
                        createdAt INTEGER NOT NULL,
                        lastModifiedAt INTEGER NOT NULL,
                        isDeleted INTEGER NOT NULL
                    )
                """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE lab_tests (
                        id TEXT PRIMARY KEY NOT NULL,
                        patientId TEXT NOT NULL,
                        testName TEXT NOT NULL,
                        testDate INTEGER NOT NULL,
                        resultValue REAL,
                        resultUnit TEXT,
                        referenceRange TEXT,
                        status TEXT,
                        notes TEXT,
                        createdAt INTEGER NOT NULL,
                        lastModifiedAt INTEGER NOT NULL,
                        isDeleted INTEGER NOT NULL
                    )
                """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE doctor_notes (
                        id TEXT PRIMARY KEY NOT NULL,
                        patientId TEXT NOT NULL,
                        doctorName TEXT,
                        visitDate INTEGER NOT NULL,
                        diagnosis TEXT,
                        recommendations TEXT,
                        medicationsPrescribed TEXT NOT NULL,
                        nextVisitDate INTEGER,
                        createdAt INTEGER NOT NULL,
                        lastModifiedAt INTEGER NOT NULL,
                        isDeleted INTEGER NOT NULL
                    )
                """.trimIndent()
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medications ADD COLUMN durationDays INTEGER NOT NULL DEFAULT 7")
            }
        }
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Creates the index Room now expects on the foreign key column.
                // Without this, existing installs crash with a schema mismatch
                // even though the table itself is fine.
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_medication_schedules_medicationId " +
                            "ON medication_schedules (medicationId)"
                )
            }
        }
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medications ADD COLUMN inventoryMode TEXT NOT NULL DEFAULT 'course'")
                db.execSQL("ALTER TABLE medications ADD COLUMN totalQuantity REAL")
                db.execSQL("ALTER TABLE medications ADD COLUMN currentQuantity REAL")
                db.execSQL("ALTER TABLE medications ADD COLUMN quantityPerDose REAL NOT NULL DEFAULT 1.0")
            }
        }
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add closedAt to cases table for date-range filtering of BP/symptoms
                db.execSQL("ALTER TABLE cases ADD COLUMN closedAt INTEGER")

                // New body temperature readings table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS body_temperature_readings (
                        id TEXT PRIMARY KEY NOT NULL,
                        patientId TEXT NOT NULL,
                        temperature REAL NOT NULL,
                        site TEXT NOT NULL DEFAULT 'oral',
                        date INTEGER NOT NULL,
                        time INTEGER NOT NULL,
                        notes TEXT,
                        createdAt INTEGER NOT NULL,
                        lastModifiedAt INTEGER NOT NULL,
                        isDeleted INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent()
                )
            }
        }
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS lab_reports (
                id TEXT PRIMARY KEY NOT NULL,
                patientId TEXT NOT NULL,
                caseId TEXT,
                reportName TEXT NOT NULL,
                reportDate INTEGER NOT NULL,
                capturedAt INTEGER NOT NULL,
                notes TEXT,
                createdAt INTEGER NOT NULL,
                lastModifiedAt INTEGER NOT NULL,
                isDeleted INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()
                )

                db.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS lab_report_items (
                id TEXT PRIMARY KEY NOT NULL,
                reportId TEXT NOT NULL,
                testItem TEXT NOT NULL,
                result TEXT NOT NULL,
                unit TEXT,
                referenceRange TEXT,
                status TEXT NOT NULL DEFAULT 'Normal',
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(reportId) REFERENCES lab_reports(id) ON DELETE CASCADE
            )
        """.trimIndent()
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_lab_report_items_reportId ON lab_report_items (reportId)"
                )
            }
        }
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE lab_report_items ADD COLUMN simpleDescription TEXT"
                )
            }
        }
        fun getDatabase(context: Context): HealthMonitorDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HealthMonitorDatabase::class.java,
                    "health_monitor_db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10
                    )
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}