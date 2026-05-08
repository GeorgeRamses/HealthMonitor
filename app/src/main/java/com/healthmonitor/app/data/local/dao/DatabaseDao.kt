package com.healthmonitor.app.data.local.dao

import androidx.room.*
import com.healthmonitor.app.data.local.entities.*
import kotlinx.coroutines.flow.Flow

// ── Patient ──────────────────────────────────────────────────────────────────

@Dao
interface PatientDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(p: PatientEntity)

    @Update
    suspend fun update(p: PatientEntity)

    @Query("SELECT * FROM patients WHERE id = :id")
    suspend fun getPatient(id: String): PatientEntity?

    @Query("SELECT * FROM patients WHERE isDeleted = 0 LIMIT 1")
    fun getFirstPatient(): Flow<PatientEntity?>

    /** One-shot (non-Flow) version used for alarm scheduling at startup. */
    @Query("SELECT * FROM patients WHERE isDeleted = 0 LIMIT 1")
    suspend fun getFirstPatientOnce(): PatientEntity?

    @Query("SELECT * FROM patients WHERE isDeleted = 0 ORDER BY name")
    fun getAllPatients(): Flow<List<PatientEntity>>

    @Query("SELECT * FROM patients WHERE lastModifiedAt > :since")
    suspend fun getModifiedSince(since: Long): List<PatientEntity>
}

// ── Medication ───────────────────────────────────────────────────────────────

@Dao
interface MedicationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(m: MedicationEntity)

    @Update
    suspend fun update(m: MedicationEntity)

    @Delete
    suspend fun delete(m: MedicationEntity)

    @Query("SELECT * FROM medications WHERE patientId = :pid AND isActive = 1 AND isDeleted = 0 ORDER BY name")
    fun getMedicationsByPatient(pid: String): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications WHERE patientId = :pid AND isDeleted = 0 ORDER BY name")
    fun getAllMedicationsByPatient(pid: String): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications WHERE patientId = :pid AND caseId = :cid AND isDeleted = 0 ORDER BY name")
    fun getAllMedicationsByPatientAndCase(pid: String, cid: String): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications WHERE patientId = :pid AND isActive = 1 AND isDeleted = 0")
    suspend fun getMedicationsByPatientOnce(pid: String): List<MedicationEntity>

    @Query("SELECT * FROM medications WHERE patientId = :pid AND caseId = :cid AND isActive = 1 AND isDeleted = 0")
    suspend fun getMedicationsByPatientAndCaseOnce(pid: String, cid: String): List<MedicationEntity>

    @Query("SELECT * FROM medications WHERE id = :id AND isDeleted = 0 LIMIT 1")
    suspend fun getMedicationById(id: String): MedicationEntity?

    @Query("SELECT * FROM medications WHERE isActive = 1 AND isDeleted = 0")
    suspend fun getAllMedicationsOnce(): List<MedicationEntity>

    @Query("SELECT * FROM medications WHERE lastModifiedAt > :since")
    suspend fun getModifiedSince(since: Long): List<MedicationEntity>
}

// ── Case ─────────────────────────────────────────────────────────────────────

@Dao
interface CaseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(c: CaseEntity)

    @Update
    suspend fun update(c: CaseEntity)

    @Query("SELECT * FROM cases WHERE patientId = :pid AND isDeleted = 0 ORDER BY createdAt DESC")
    fun getCasesByPatient(pid: String): Flow<List<CaseEntity>>

    @Query("SELECT * FROM cases WHERE id = :id AND isDeleted = 0 LIMIT 1")
    suspend fun getCaseById(id: String): CaseEntity?

    @Query("SELECT * FROM cases WHERE lastModifiedAt > :since")
    suspend fun getModifiedSince(since: Long): List<CaseEntity>
}

// ── Medication log ────────────────────────────────────────────────────────────

@Dao
interface MedicationLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(l: MedicationLogEntity)

    @Update
    suspend fun update(l: MedicationLogEntity)

    @Query("""
        SELECT * FROM medication_logs
        WHERE medicationId = :mid AND date = :date AND scheduledTime = :scheduledTime AND isDeleted = 0
        LIMIT 1
    """)
    suspend fun getLogForDose(mid: String, date: Long, scheduledTime: String): MedicationLogEntity?

    @Query("""
    SELECT ml.* FROM medication_logs ml
    INNER JOIN medications m ON ml.medicationId = m.id
    WHERE ml.patientId = :pid
    AND ml.date = :date
    AND ml.isDeleted = 0
    AND m.caseId = :caseId
    AND m.isDeleted = 0
    ORDER BY ml.time
""")
    fun getLogsForDate(pid: String, caseId: String, date: Long): Flow<List<MedicationLogEntity>>


    @Query("""
    SELECT * FROM medication_logs 
    WHERE medicationId IN (:medicationIds) 
    AND date = :date 
    AND isDeleted = 0 
    ORDER BY time
""")
    fun getLogsForMedications(
        medicationIds: List<String>,
        date: Long
    ): Flow<List<MedicationLogEntity>>

    @Query("""
    SELECT * FROM medication_logs
    WHERE medicationId IN (:medicationIds)
    AND isDeleted = 0
    ORDER BY date DESC, time DESC
""")
    fun getLogsForMedications(medicationIds: List<String>): Flow<List<MedicationLogEntity>>

    @Query("""
    SELECT * FROM medication_logs
    WHERE medicationId = :medicationId
    AND taken = 1
    AND isDeleted = 0
    ORDER BY time DESC
    LIMIT 1
""")
    fun getLastTakenLog(medicationId: String): Flow<MedicationLogEntity?>

    @Query("""
    SELECT COUNT(*) FROM medication_logs
    WHERE medicationId = :medicationId
    AND taken = 1
    AND isDeleted = 0
""")
    fun getTakenDoseCount(medicationId: String): Flow<Int>

    @Query("""
    SELECT COUNT(*) FROM medication_logs ml
    INNER JOIN medications m ON ml.medicationId = m.id
    WHERE ml.patientId = :pid
    AND ml.date = :date
    AND ml.taken = 1
    AND ml.isDeleted = 0
    AND m.caseId = :caseId
    AND m.isDeleted = 0
""")
    suspend fun getTakenCount(pid: String, caseId: String, date: Long): Int

    @Query("SELECT * FROM medication_logs WHERE lastModifiedAt > :since")
    suspend fun getModifiedSince(since: Long): List<MedicationLogEntity>
}

// ── Blood pressure ────────────────────────────────────────────────────────────

@Dao
interface BloodPressureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(r: BloodPressureEntity)

    @Delete
    suspend fun delete(r: BloodPressureEntity)

    @Query("SELECT * FROM blood_pressure_readings WHERE patientId = :pid AND isDeleted = 0 ORDER BY date DESC, time DESC")
    fun getReadingsByPatient(pid: String): Flow<List<BloodPressureEntity>>

    /** Returns readings whose date falls within [fromDate, toDate] inclusive. */
    @Query("""
        SELECT * FROM blood_pressure_readings
        WHERE patientId = :pid
        AND isDeleted = 0
        AND date >= :fromDate
        AND date <= :toDate
        ORDER BY date DESC, time DESC
    """)
    fun getReadingsByPatientInRange(pid: String, fromDate: Long, toDate: Long): Flow<List<BloodPressureEntity>>

    @Query("SELECT * FROM blood_pressure_readings WHERE patientId = :pid AND isDeleted = 0 ORDER BY date DESC LIMIT 1")
    suspend fun getLatestReading(pid: String): BloodPressureEntity?

    /** Latest reading within the case date range. */
    @Query("""
        SELECT * FROM blood_pressure_readings
        WHERE patientId = :pid
        AND isDeleted = 0
        AND date >= :fromDate
        AND date <= :toDate
        ORDER BY date DESC, time DESC
        LIMIT 1
    """)
    suspend fun getLatestReadingInRange(pid: String, fromDate: Long, toDate: Long): BloodPressureEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(r: BloodPressureEntity)

    @Query("SELECT * FROM blood_pressure_readings WHERE lastModifiedAt > :since")
    suspend fun getModifiedSince(since: Long): List<BloodPressureEntity>
}

// ── Body temperature ──────────────────────────────────────────────────────────

@Dao
interface BodyTemperatureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(r: BodyTemperatureEntity)

    @Delete
    suspend fun delete(r: BodyTemperatureEntity)

    @Query("SELECT * FROM body_temperature_readings WHERE patientId = :pid AND isDeleted = 0 ORDER BY date DESC, time DESC")
    fun getReadingsByPatient(pid: String): Flow<List<BodyTemperatureEntity>>

    /** Returns readings within [fromDate, toDate] inclusive. */
    @Query("""
        SELECT * FROM body_temperature_readings
        WHERE patientId = :pid
        AND isDeleted = 0
        AND date >= :fromDate
        AND date <= :toDate
        ORDER BY date DESC, time DESC
    """)
    fun getReadingsByPatientInRange(pid: String, fromDate: Long, toDate: Long): Flow<List<BodyTemperatureEntity>>

    @Query("SELECT * FROM body_temperature_readings WHERE patientId = :pid AND isDeleted = 0 ORDER BY date DESC LIMIT 1")
    suspend fun getLatestReading(pid: String): BodyTemperatureEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(r: BodyTemperatureEntity)

    @Query("SELECT * FROM body_temperature_readings WHERE lastModifiedAt > :since")
    suspend fun getModifiedSince(since: Long): List<BodyTemperatureEntity>
}

// ── Symptoms ──────────────────────────────────────────────────────────────────

@Dao
interface SymptomDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(s: SymptomEntity)

    @Update
    suspend fun update(s: SymptomEntity)

    @Delete
    suspend fun delete(s: SymptomEntity)

    @Query("SELECT * FROM symptoms WHERE patientId = :pid AND isDeleted = 0 ORDER BY date DESC, time DESC")
    fun getSymptomsByPatient(pid: String): Flow<List<SymptomEntity>>

    /** Returns symptoms within [fromDate, toDate] inclusive. */
    @Query("""
        SELECT * FROM symptoms
        WHERE patientId = :pid
        AND isDeleted = 0
        AND date >= :fromDate
        AND date <= :toDate
        ORDER BY date DESC, time DESC
    """)
    fun getSymptomsByPatientInRange(pid: String, fromDate: Long, toDate: Long): Flow<List<SymptomEntity>>

    @Query("SELECT * FROM symptoms WHERE lastModifiedAt > :since")
    suspend fun getModifiedSince(since: Long): List<SymptomEntity>
}

// ── Inhaler log ───────────────────────────────────────────────────────────────

@Dao
interface InhalerLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(l: InhalerLogEntity)

    @Query("SELECT * FROM inhaler_logs WHERE patientId = :pid AND isDeleted = 0 ORDER BY date DESC")
    fun getLogsByPatient(pid: String): Flow<List<InhalerLogEntity>>

    @Query("SELECT COUNT(*) FROM inhaler_logs WHERE patientId = :pid AND isDeleted = 0 AND date BETWEEN :start AND :end")
    suspend fun getUsageCount(pid: String, start: Long, end: Long): Int
}

// ── Lab tests ─────────────────────────────────────────────────────────────────

@Dao
interface LabTestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(t: LabTestEntity)

    @Query("SELECT * FROM lab_tests WHERE patientId = :pid AND isDeleted = 0 ORDER BY testDate DESC")
    fun getTestsByPatient(pid: String): Flow<List<LabTestEntity>>
}

// ── Doctor notes ──────────────────────────────────────────────────────────────

@Dao
interface DoctorNoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(n: DoctorNoteEntity)

    @Query("SELECT * FROM doctor_notes WHERE patientId = :pid AND isDeleted = 0 ORDER BY visitDate DESC")
    fun getNotesByPatient(pid: String): Flow<List<DoctorNoteEntity>>
}

// ── Medication schedules ──────────────────────────────────────────────────────

@Dao
interface MedicationScheduleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(s: MedicationScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(schedules: List<MedicationScheduleEntity>)

    @Update
    suspend fun update(s: MedicationScheduleEntity)

    @Query("SELECT * FROM medication_schedules WHERE medicationId = :medId AND isDeleted = 0 ORDER BY scheduledTime")
    fun getSchedulesForMedication(medId: String): Flow<List<MedicationScheduleEntity>>

    @Query("SELECT * FROM medication_schedules WHERE medicationId = :medId AND isDeleted = 0 ORDER BY scheduledTime")
    suspend fun getSchedulesForMedicationOnce(medId: String): List<MedicationScheduleEntity>

    @Query("SELECT * FROM medication_schedules WHERE isDeleted = 0")
    suspend fun getAllSchedules(): List<MedicationScheduleEntity>

    @Query("UPDATE medication_schedules SET isDeleted = 1, lastModifiedAt = :ts WHERE medicationId = :medId")
    suspend fun softDeleteForMedication(medId: String, ts: Long)

    @Query("SELECT * FROM medication_schedules WHERE lastModifiedAt > :since")
    suspend fun getModifiedSince(since: Long): List<MedicationScheduleEntity>
}

// ── Lab Reports ───────────────────────────────────────────────────────────────

@Dao
interface LabReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(r: LabReportEntity): Long

    @Update
    suspend fun update(r: LabReportEntity)

    @Query("SELECT * FROM lab_reports WHERE patientId = :pid AND isDeleted = 0 ORDER BY reportDate DESC, capturedAt DESC")
    fun getReportsByPatient(pid: String): Flow<List<LabReportEntity>>

    @Query("""
        SELECT * FROM lab_reports
        WHERE patientId = :pid AND isDeleted = 0
        AND reportDate >= :fromDate AND reportDate <= :toDate
        ORDER BY reportDate DESC, capturedAt DESC
    """)
    fun getReportsByPatientInRange(pid: String, fromDate: Long, toDate: Long): Flow<List<LabReportEntity>>

    @Query("SELECT * FROM lab_reports WHERE id = :id AND isDeleted = 0 LIMIT 1")
    suspend fun getReportById(id: String): LabReportEntity?

    @Query("UPDATE lab_reports SET isDeleted = 1, lastModifiedAt = :ts WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long)

    @Query("SELECT * FROM lab_reports WHERE lastModifiedAt > :since")
    suspend fun getModifiedSince(since: Long): List<LabReportEntity>
}

// ── Lab Report Items ──────────────────────────────────────────────────────────

@Dao
interface LabReportItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<LabReportItemEntity>)

    @Query("SELECT * FROM lab_report_items WHERE reportId = :reportId ORDER BY id")
    fun getItemsForReport(reportId: String): Flow<List<LabReportItemEntity>>

    @Query("SELECT * FROM lab_report_items WHERE reportId = :reportId ORDER BY id")
    suspend fun getItemsForReportOnce(reportId: String): List<LabReportItemEntity>

    @Query("DELETE FROM lab_report_items WHERE reportId = :reportId")
    suspend fun deleteItemsForReport(reportId: String)
}