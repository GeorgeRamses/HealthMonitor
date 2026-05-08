package com.healthmonitor.app.shared.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.healthmonitor.app.data.local.entities.*
import com.healthmonitor.app.data.model.*
import com.healthmonitor.app.data.remote.SupabaseService
import com.healthmonitor.app.data.repository.HealthMonitorRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncDataStore by preferencesDataStore(name = "sync_prefs")
private val LAST_SYNC_KEY = longPreferencesKey("last_sync_timestamp")

sealed interface SyncState {
    object Idle : SyncState
    object Syncing : SyncState
    data class Success(val lastSyncAt: Long) : SyncState
    data class Error(val message: String) : SyncState
}

/**
 * Bidirectional sync between local Room DB and Supabase.
 *
 * Strategy: "Win-Latest" — for each record pair (local vs. remote) whichever
 * has the greater lastModifiedAt is authoritative and is written to both stores.
 *
 * Soft-deleted records (isDeleted = true) are synced like any other record;
 * UI queries already filter them out via WHERE isDeleted = 0.
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: HealthMonitorRepository,
    private val supabaseService: SupabaseService
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state

    fun syncNow() {
        if (_state.value is SyncState.Syncing) return
        scope.launch {
            _state.value = SyncState.Syncing
            try {
                val since = readLastSyncTimestamp()
                val now = System.currentTimeMillis()

                syncPatients(since)
                syncCases(since)
                syncMedications(since)
                syncMedicationSchedules(since)
                syncMedicationLogs(since)
                syncBloodPressureReadings(since)
                syncSymptoms(since)

                writeLastSyncTimestamp(now)
                _state.value = SyncState.Success(now)
            } catch (ex: Exception) {
                _state.value = SyncState.Error(ex.message ?: "unknown sync error")
            }
        }
    }

    // ── Per-entity sync ───────────────────────────────────────────────────

    private suspend fun syncPatients(since: Long) {
        val remote = supabaseService.getPatients(since)
        val local = repository.getPatientsModifiedSince(since)

        // Merge remote → local (Win-Latest)
        remote.forEach { remotePatient ->
            val existing = repository.getPatientById(remotePatient.id)
            if (existing == null || remotePatient.lastModifiedAt > existing.lastModifiedAt) {
                repository.upsertPatient(remotePatient.toEntity())
            }
        }

        // Push local → remote (records newer than remote counterparts)
        val toUpload = local.filter { localEntity ->
            val remoteRecord = remote.firstOrNull { it.id == localEntity.id }
            remoteRecord == null || localEntity.lastModifiedAt > remoteRecord.lastModifiedAt
        }
        if (toUpload.isNotEmpty()) {
            supabaseService.upsertPatients(toUpload.map { it.toModel() })
        }
    }

    private suspend fun syncCases(since: Long) {
        val remote = supabaseService.getMedicalCases(since)
        val local = repository.getCasesModifiedSince(since)

        remote.forEach { remoteCase ->
            val existing = repository.getCaseById(remoteCase.id)
            if (existing == null || remoteCase.lastModifiedAt > existing.lastModifiedAt) {
                repository.upsertCase(remoteCase.toEntity())
            }
        }

        val toUpload = local.filter { localEntity ->
            val remoteRecord = remote.firstOrNull { it.id == localEntity.id }
            remoteRecord == null || localEntity.lastModifiedAt > remoteRecord.lastModifiedAt
        }
        if (toUpload.isNotEmpty()) {
            supabaseService.upsertMedicalCases(toUpload.map { it.toModel() })
        }
    }

    private suspend fun syncMedications(since: Long) {
        val remote = supabaseService.getMedications(since)
        val local = repository.getMedicationsModifiedSince(since)
        val remoteById = remote.associateBy { it.id }
        val allLocalMeds = repository.getAllActiveMedicationsOnce().associateBy { it.id }

        remote.forEach { remoteMed ->
            val existing = allLocalMeds[remoteMed.id]
            if (existing == null || remoteMed.lastModifiedAt > existing.lastModifiedAt) {
                repository.upsertMedication(remoteMed.toEntity())
            }
        }

        val toUpload = local.filter { localEntity ->
            val remoteRecord = remoteById[localEntity.id]
            remoteRecord == null || localEntity.lastModifiedAt > remoteRecord.lastModifiedAt
        }
        if (toUpload.isNotEmpty()) supabaseService.upsertMedications(toUpload.map { it.toModel() })
    }

    private suspend fun syncMedicationSchedules(since: Long) {
        val remote = supabaseService.getMedicationSchedules(since)
        val local = repository.getSchedulesModifiedSince(since)
        val remoteById = remote.associateBy { it.id }
        val allLocalScheds = repository.getAllSchedules().associateBy { it.id }

        remote.forEach { remoteSched ->
            val existing = allLocalScheds[remoteSched.id]
            if (existing == null || remoteSched.lastModifiedAt > existing.lastModifiedAt) {
                repository.upsertSchedule(remoteSched.toEntity())
            }
        }

        val toUpload = local.filter { localEntity ->
            val remoteRecord = remoteById[localEntity.id]
            remoteRecord == null || localEntity.lastModifiedAt > remoteRecord.lastModifiedAt
        }
        if (toUpload.isNotEmpty()) supabaseService.upsertMedicationSchedules(toUpload.map { it.toModel() })
    }

    private suspend fun syncMedicationLogs(since: Long) {
        val remote = supabaseService.getMedicationLogs(since)
        val local = repository.getLogsModifiedSince(since)

        remote.forEach { remoteLog ->
            val existingLocal = repository.getMedicationLogForDose(
                remoteLog.medicationId, remoteLog.date, remoteLog.scheduledTime
            )
            if (existingLocal == null || remoteLog.lastModifiedAt > existingLocal.lastModifiedAt) {
                repository.upsertMedicationLog(remoteLog.toEntity())
            }
        }

        val toUpload = local.filter { localEntity ->
            val remoteRecord = remote.firstOrNull { it.id == localEntity.id }
            remoteRecord == null || localEntity.lastModifiedAt > remoteRecord.lastModifiedAt
        }
        if (toUpload.isNotEmpty()) {
            supabaseService.upsertMedicationLogs(toUpload.map { it.toModel() })
        }
    }

    private suspend fun syncBloodPressureReadings(since: Long) {
        val remote = supabaseService.getBloodPressureReadings(since)
        val local = repository.getBpReadingsModifiedSince(since)

        remote.forEach { remoteReading ->
            val existing = local.firstOrNull { it.id == remoteReading.id }
            if (existing == null || remoteReading.lastModifiedAt > existing.lastModifiedAt) {
                repository.upsertBloodPressure(remoteReading.toEntity())
            }
        }

        val toUpload = local.filter { localEntity ->
            val remoteRecord = remote.firstOrNull { it.id == localEntity.id }
            remoteRecord == null || localEntity.lastModifiedAt > remoteRecord.lastModifiedAt
        }
        if (toUpload.isNotEmpty()) {
            supabaseService.upsertBloodPressureReadings(toUpload.map { it.toModel() })
        }
    }

    private suspend fun syncSymptoms(since: Long) {
        val remote = supabaseService.getSymptoms(since)
        val local = repository.getSymptomsModifiedSince(since)

        remote.forEach { remoteSymptom ->
            val existing = local.firstOrNull { it.id == remoteSymptom.id }
            if (existing == null || remoteSymptom.lastModifiedAt > existing.lastModifiedAt) {
                repository.upsertSymptom(remoteSymptom.toEntity())
            }
        }

        val toUpload = local.filter { localEntity ->
            val remoteRecord = remote.firstOrNull { it.id == localEntity.id }
            remoteRecord == null || localEntity.lastModifiedAt > remoteRecord.lastModifiedAt
        }
        if (toUpload.isNotEmpty()) {
            supabaseService.upsertSymptoms(toUpload.map { it.toModel() })
        }
    }

    // ── DataStore persistence ─────────────────────────────────────────────

    private suspend fun readLastSyncTimestamp(): Long =
        context.syncDataStore.data.first()[LAST_SYNC_KEY] ?: 0L

    private suspend fun writeLastSyncTimestamp(ts: Long) {
        context.syncDataStore.edit { it[LAST_SYNC_KEY] = ts }
    }
}

// ── Domain ↔ Entity mappers ───────────────────────────────────────────────────

private fun Patient.toEntity() = PatientEntity(
    id = id,
    name = name,
    age = age,
    gender = gender,
    bloodType = bloodType,
    medicalConditions = medicalConditions,
    emergencyContact = emergencyContact,
    emergencyPhone = emergencyPhone,
    createdAt = createdAt,
    lastModifiedAt = lastModifiedAt,
    isDeleted = isDeleted
)

private fun PatientEntity.toModel() = Patient(
    id = id,
    name = name,
    age = age,
    gender = gender,
    bloodType = bloodType,
    medicalConditions = medicalConditions,
    emergencyContact = emergencyContact,
    emergencyPhone = emergencyPhone,
    createdAt = createdAt,
    lastModifiedAt = lastModifiedAt,
    isDeleted = isDeleted
)

private fun MedicalCase.toEntity() = CaseEntity(
    id = id,
    patientId = patientId,
    title = title,
    doctorName = doctorName,
    notes = notes,
    isClosed = status == CaseStatus.CLOSED,
    createdAt = createdAt,
    lastModifiedAt = lastModifiedAt,
    isDeleted = isDeleted
)

private fun CaseEntity.toModel() = MedicalCase(
    id = id,
    patientId = patientId,
    title = title,
    doctorName = doctorName,
    notes = notes,
    status = if (isClosed) CaseStatus.CLOSED else CaseStatus.OPEN,
    createdAt = createdAt,
    lastModifiedAt = lastModifiedAt,
    isDeleted = isDeleted
)

// AFTER — durationDays is now preserved
private fun Medication.toEntity() = MedicationEntity(
    id = id,
    patientId = patientId,
    caseId = caseId,
    name = name,
    dosage = dosage,
    unit = unit,
    frequency = frequency,
    timesPerDay = 1,
    scheduledTimes = "[]",
    durationDays = durationDays,
    startDate = startDate,
    endDate = endDate,
    notes = notes,
    isActive = isActive,
    createdAt = createdAt,
    lastModifiedAt = lastModifiedAt,
    isDeleted = isDeleted
)

private fun MedicationEntity.toModel() = Medication(
    id = id,
    patientId = patientId,
    caseId = caseId,
    name = name,
    dosage = dosage,
    unit = unit,
    frequency = frequency,
    durationDays = durationDays,   // ← ADD THIS LINE
    notes = notes,
    isActive = isActive,
    startDate = startDate,
    endDate = endDate,
    createdAt = createdAt,
    lastModifiedAt = lastModifiedAt,
    isDeleted = isDeleted
)

private fun MedicationSchedule.toEntity() = MedicationScheduleEntity(
    id = id,
    medicationId = medicationId,
    scheduledTime = scheduledTime,
    createdAt = createdAt,
    lastModifiedAt = lastModifiedAt,
    isDeleted = isDeleted
)

private fun MedicationScheduleEntity.toModel() = MedicationSchedule(
    id = id,
    medicationId = medicationId,
    scheduledTime = scheduledTime,
    createdAt = createdAt,
    lastModifiedAt = lastModifiedAt,
    isDeleted = isDeleted
)

private fun MedicationLog.toEntity() = MedicationLogEntity(
    id = id,
    medicationId = medicationId,
    patientId = patientId,
    date = date,
    scheduledTime = scheduledTime,
    time = takenAt ?: 0L,
    taken = taken,
    notes = notes,
    createdAt = createdAt,
    lastModifiedAt = lastModifiedAt,
    isDeleted = isDeleted
)

private fun MedicationLogEntity.toModel() = MedicationLog(
    id = id,
    medicationId = medicationId,
    scheduleId = "",
    patientId = patientId,
    date = date,
    scheduledTime = scheduledTime,
    takenAt = time.takeIf { it > 0L },
    taken = taken,
    notes = notes,
    createdAt = createdAt,
    lastModifiedAt = lastModifiedAt,
    isDeleted = isDeleted
)

private fun BloodPressureReading.toEntity() = BloodPressureEntity(
    id = id,
    patientId = patientId,
    systolic = systolic,
    diastolic = diastolic,
    pulse = pulse,
    oxygenSaturation = oxygenSaturation,
    date = recordedAt,
    time = recordedAt,
    notes = notes,
    createdAt = createdAt,
    lastModifiedAt = lastModifiedAt,
    isDeleted = isDeleted
)

private fun BloodPressureEntity.toModel() = BloodPressureReading(
    id = id,
    patientId = patientId,
    systolic = systolic,
    diastolic = diastolic,
    pulse = pulse,
    oxygenSaturation = oxygenSaturation,
    recordedAt = time,
    notes = notes,
    createdAt = createdAt,
    lastModifiedAt = lastModifiedAt,
    isDeleted = isDeleted
)

private fun Symptom.toEntity() = SymptomEntity(
    id = id,
    patientId = patientId,
    date = recordedAt,
    time = recordedAt,
    symptomType = symptomType,
    severity = severity,
    notes = notes,
    inhalerUsed = inhalerUsed,
    improvementAfterInhaler = improvementAfterInhaler,
    createdAt = createdAt,
    lastModifiedAt = lastModifiedAt,
    isDeleted = isDeleted
)

private fun SymptomEntity.toModel() = Symptom(
    id = id,
    patientId = patientId,
    symptomType = symptomType,
    severity = severity,
    recordedAt = time,
    inhalerUsed = inhalerUsed,
    improvementAfterInhaler = improvementAfterInhaler,
    notes = notes,
    createdAt = createdAt,
    lastModifiedAt = lastModifiedAt,
    isDeleted = isDeleted
)
