package com.healthmonitor.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthmonitor.app.data.local.entities.*
import com.healthmonitor.app.data.repository.HealthMonitorRepository
import com.healthmonitor.app.util.ActiveCaseManager
import com.healthmonitor.app.util.ActivePatientManager
import com.healthmonitor.app.util.AlarmScheduler
import com.healthmonitor.app.util.MedicationInventoryMode
import com.healthmonitor.app.util.addDays
import com.healthmonitor.app.util.isMedicationScheduledOnDate
import com.healthmonitor.app.util.parseMedicationTimes
import com.healthmonitor.app.util.startOfDayMillis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    application: Application,
    private val repository: HealthMonitorRepository
) : AndroidViewModel(application) {

    // ── Patient ──────────────────────────────────────────────────────────

    private val _currentPatient = MutableStateFlow<PatientEntity?>(null)
    val currentPatient: StateFlow<PatientEntity?> = _currentPatient.asStateFlow()

    // ── Case ─────────────────────────────────────────────────────────────

    private val _currentCaseId = MutableStateFlow<String?>(null)
    val currentCaseId: StateFlow<String?> = _currentCaseId.asStateFlow()

    // ── Medications ──────────────────────────────────────────────────────

    private val _todayMedications = MutableStateFlow<List<MedicationEntity>>(emptyList())
    val todayMedications: StateFlow<List<MedicationEntity>> = _todayMedications.asStateFlow()

    private val _allMedications = MutableStateFlow<List<MedicationEntity>>(emptyList())
    val allMedications: StateFlow<List<MedicationEntity>> = _allMedications.asStateFlow()

    private val _medicationLogs = MutableStateFlow<List<MedicationLogEntity>>(emptyList())
    val medicationLogs: StateFlow<List<MedicationLogEntity>> = _medicationLogs.asStateFlow()

    private val _medicationHistoryLogs = MutableStateFlow<List<MedicationLogEntity>>(emptyList())
    val medicationHistoryLogs: StateFlow<List<MedicationLogEntity>> = _medicationHistoryLogs.asStateFlow()

    private val _selectedMedicationDate = MutableStateFlow(getTodayMillis())
    val selectedMedicationDate: StateFlow<Long> = _selectedMedicationDate.asStateFlow()

    // medicationId → list of scheduled times (from medication_schedules table)
    private val _scheduleMap = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val scheduleMap: StateFlow<Map<String, List<String>>> = _scheduleMap.asStateFlow()

    // ── Blood pressure ───────────────────────────────────────────────────

    private val _lastBloodPressure = MutableStateFlow<BloodPressureEntity?>(null)
    val lastBloodPressure: StateFlow<BloodPressureEntity?> = _lastBloodPressure.asStateFlow()

    private val _bloodPressureReadings = MutableStateFlow<List<BloodPressureEntity>>(emptyList())
    val bloodPressureReadings: StateFlow<List<BloodPressureEntity>> = _bloodPressureReadings.asStateFlow()

    // ── Body temperature ─────────────────────────────────────────────────

    private val _lastBodyTemperature = MutableStateFlow<BodyTemperatureEntity?>(null)
    val lastBodyTemperature: StateFlow<BodyTemperatureEntity?> = _lastBodyTemperature.asStateFlow()

    private val _bodyTemperatureReadings = MutableStateFlow<List<BodyTemperatureEntity>>(emptyList())
    val bodyTemperatureReadings: StateFlow<List<BodyTemperatureEntity>> = _bodyTemperatureReadings.asStateFlow()

    // ── Case date window (drives BP/symptom/temperature filtering) ────────
    private val _caseFromDate = MutableStateFlow<Long?>(null)
    private val _caseToDate   = MutableStateFlow<Long?>(null)

    // ── Symptoms ─────────────────────────────────────────────────────────

    private val _recentSymptoms = MutableStateFlow<List<SymptomEntity>>(emptyList())
    val recentSymptoms: StateFlow<List<SymptomEntity>> = _recentSymptoms.asStateFlow()

    private val _allSymptoms = MutableStateFlow<List<SymptomEntity>>(emptyList())
    val allSymptoms: StateFlow<List<SymptomEntity>> = _allSymptoms.asStateFlow()

    private val _todaySymptoms = MutableStateFlow<List<SymptomEntity>>(emptyList())
    val todaySymptoms: StateFlow<List<SymptomEntity>> = _todaySymptoms.asStateFlow()

    private val _selectedHealthDate = MutableStateFlow(getTodayMillis())
    val selectedHealthDate: StateFlow<Long> = _selectedHealthDate.asStateFlow()

    private val _symptomTypes = MutableStateFlow(defaultSymptomTypes())
    val symptomTypes: StateFlow<List<String>> = _symptomTypes.asStateFlow()

    // ── Stats ────────────────────────────────────────────────────────────

    private val _medicationAdherence = MutableStateFlow(0)
    val medicationAdherence: StateFlow<Int> = _medicationAdherence.asStateFlow()

    private val _todayDoseCount = MutableStateFlow(0)
    val todayDoseCount: StateFlow<Int> = _todayDoseCount.asStateFlow()

    private var patientDataJob: Job? = null
    private var medicationCollectionJob: Job? = null
    private var medicationLogCollectionJob: Job? = null
    private var medicationHistoryLogCollectionJob: Job? = null
    private var bloodPressureCollectionJob: Job? = null
    private var bodyTemperatureCollectionJob: Job? = null
    private var symptomCollectionJob: Job? = null

    init {
        observeActivePatient()
        observeActiveCase()
    }

    // ── Patient observation ───────────────────────────────────────────────

    private fun observeActivePatient() {
        viewModelScope.launch {
            ActivePatientManager.activePatientIdFlow.collect { patientId ->
                if (patientId == null) return@collect
                val previousId = _currentPatient.value?.id
                val patient = repository.getPatientById(patientId)
                _currentPatient.value = patient
                if (previousId != patientId && patient != null) {
                    loadRelatedData(patient.id)
                }
            }
        }
    }

    // ── Case observation ──────────────────────────────────────────────────

    private fun observeActiveCase() {
        viewModelScope.launch {
            ActiveCaseManager.activeCaseIdFlow.collect { caseId ->
                _currentCaseId.value = caseId
                val patientId = _currentPatient.value?.id
                    ?: ActivePatientManager.activePatientIdFlow.value
                    ?: return@collect

                if (caseId != null) {
                    val caseEntity = repository.getCaseById(caseId)
                    if (caseEntity != null) {
                        _caseFromDate.value = startOfDayMillis(
                            java.time.Instant.ofEpochMilli(caseEntity.createdAt)
                                .atZone(ZoneId.systemDefault()).toLocalDate(),
                            ZoneId.systemDefault()
                        )
                        _caseToDate.value = caseEntity.closedAt
                            ?: (getTodayMillis() + 86_399_999L)
                    }
                } else {
                    _caseFromDate.value = null
                    _caseToDate.value   = null
                }

                collectMedications(patientId)
                collectMedicationLogs(patientId)
                collectBloodPressureInCaseRange(patientId)
                collectSymptomsInCaseRange(patientId)
                collectBodyTemperatureInCaseRange(patientId)
            }
        }
    }

    // ── Related data ──────────────────────────────────────────────────────

    private fun loadRelatedData(patientId: String) {
        collectMedications(patientId)
        collectMedicationLogs(patientId)
        collectBloodPressureInCaseRange(patientId)
        collectSymptomsInCaseRange(patientId)
        collectBodyTemperatureInCaseRange(patientId)
    }

    private fun refreshMedicationData() {
        val patientId = _currentPatient.value?.id ?: return
        collectMedications(patientId)
        collectMedicationLogs(patientId)
    }

    private fun collectMedications(patientId: String) {
        medicationCollectionJob?.cancel()
        val caseId = _currentCaseId.value

        if (caseId == null) {
            _allMedications.value    = emptyList()
            _todayMedications.value  = emptyList()
            _scheduleMap.value       = emptyMap()
            _medicationLogs.value    = emptyList()
            calculateAdherence()
            return
        }

        medicationCollectionJob = viewModelScope.launch {
            repository.getAllMedicationsByPatientAndCase(patientId, caseId)
                .collect { meds ->
                    _allMedications.value = meds
                    _todayMedications.value = meds.filter {
                        it.isActive && isMedicationScheduledOnDate(it, _selectedMedicationDate.value)
                    }
                    loadScheduleMap(meds.map { it.id })
                    collectMedicationHistoryLogs(meds.map { it.id })
                    calculateAdherence()
                }
        }
    }

    private fun loadScheduleMap(medIds: List<String>) {
        viewModelScope.launch {
            val map = mutableMapOf<String, List<String>>()
            medIds.forEach { medId ->
                map[medId] = repository.getSchedulesForMedicationOnce(medId).map { it.scheduledTime }
            }
            _scheduleMap.value = map
            calculateAdherence()
        }
    }

    private fun collectMedicationLogs(patientId: String) {
        medicationLogCollectionJob?.cancel()
        val caseId = _currentCaseId.value
        if (caseId == null) {
            _medicationLogs.value = emptyList()
            calculateAdherence()
            return
        }
        medicationLogCollectionJob = viewModelScope.launch {
            repository.getLogsForDate(patientId, caseId, _selectedMedicationDate.value).collect { logs ->
                _medicationLogs.value = logs
                calculateAdherence()
            }
        }
    }

    private fun collectMedicationHistoryLogs(medicationIds: List<String>) {
        medicationHistoryLogCollectionJob?.cancel()
        if (medicationIds.isEmpty()) {
            _medicationHistoryLogs.value = emptyList()
            return
        }
        medicationHistoryLogCollectionJob = viewModelScope.launch {
            repository.getLogsForMedications(medicationIds).collect { logs ->
                _medicationHistoryLogs.value = logs
            }
        }
    }

    private fun collectBloodPressureInCaseRange(patientId: String) {
        bloodPressureCollectionJob?.cancel()
        val from = _caseFromDate.value
        val to   = _caseToDate.value
        bloodPressureCollectionJob = viewModelScope.launch {
            if (from != null && to != null) {
                repository.getBloodPressureReadingsInRange(patientId, from, to).collect { readings ->
                    _bloodPressureReadings.value = readings
                    _lastBloodPressure.value     = readings.firstOrNull()
                }
            } else {
                _bloodPressureReadings.value = emptyList()
                _lastBloodPressure.value     = null
            }
        }
    }

    private fun collectSymptomsInCaseRange(patientId: String) {
        symptomCollectionJob?.cancel()
        val from = _caseFromDate.value
        val to   = _caseToDate.value
        symptomCollectionJob = viewModelScope.launch {
            if (from != null && to != null) {
                repository.getSymptomsByPatientInRange(patientId, from, to).collect { symptoms ->
                    _allSymptoms.value    = symptoms
                    _recentSymptoms.value = symptoms.take(10)
                    _todaySymptoms.value  = symptoms.filter { it.date == getTodayMillis() }
                    _symptomTypes.value   = (defaultSymptomTypes() + symptoms.map { it.symptomType })
                        .distinct().sorted()
                }
            } else {
                _allSymptoms.value    = emptyList()
                _recentSymptoms.value = emptyList()
                _todaySymptoms.value  = emptyList()
                _symptomTypes.value   = defaultSymptomTypes()
            }
        }
    }

    private fun collectBodyTemperatureInCaseRange(patientId: String) {
        bodyTemperatureCollectionJob?.cancel()
        val from = _caseFromDate.value
        val to   = _caseToDate.value
        bodyTemperatureCollectionJob = viewModelScope.launch {
            if (from != null && to != null) {
                repository.getBodyTemperatureReadingsInRange(patientId, from, to).collect { readings ->
                    _bodyTemperatureReadings.value = readings
                    _lastBodyTemperature.value     = readings.firstOrNull()
                }
            } else {
                _bodyTemperatureReadings.value = emptyList()
                _lastBodyTemperature.value     = null
            }
        }
    }

    fun moveSelectedHealthDate(days: Int) {
        _selectedHealthDate.value = addDays(_selectedHealthDate.value, days)
    }

    // ── Medication CRUD ───────────────────────────────────────────────────

    fun addMedication(
        name: String,
        dosage: String,
        unit: String,
        frequency: String,
        scheduledTimes: List<String>,
        notes: String?,
        durationDays: Int = 7,
        inventoryMode: String = MedicationInventoryMode.COURSE,
        totalQuantity: Double? = null,
        currentQuantity: Double? = totalQuantity,
        quantityPerDose: Double = 1.0
    ) {
        viewModelScope.launch {
            val patientId = _currentPatient.value?.id ?: return@launch
            val caseId    = _currentCaseId.value ?: return@launch
            val ctx       = getApplication<Application>().applicationContext
            val entity = MedicationEntity(
                patientId       = patientId,
                caseId          = caseId,
                name            = name,
                dosage          = dosage,
                unit            = unit,
                frequency       = frequency,
                timesPerDay     = scheduledTimes.size.coerceAtLeast(1),
                scheduledTimes  = JSONArray(scheduledTimes).toString(),
                inventoryMode   = inventoryMode,
                durationDays    = durationDays,
                totalQuantity   = totalQuantity,
                currentQuantity = currentQuantity,
                quantityPerDose = quantityPerDose,
                startDate       = getTodayMillis(),
                notes           = notes?.takeIf { it.isNotBlank() },
                isActive        = true
            )
            repository.insertMedication(entity)
            val schedules = scheduledTimes.map { time ->
                MedicationScheduleEntity(medicationId = entity.id, scheduledTime = time)
            }
            repository.insertSchedules(schedules)
            // Only schedule alarms if global alarm setting is on
            if (com.healthmonitor.app.ui.screens.isMedicationAlarmsEnabled(ctx)) {
                scheduledTimes.forEach { time ->
                    AlarmScheduler.schedule(ctx, name, entity.id, time)
                }
            }
            refreshMedicationData()
        }
    }

    fun updateMedication(
        medication: MedicationEntity,
        name: String,
        dosage: String,
        unit: String,
        frequency: String,
        scheduledTimes: List<String>,
        notes: String?,
        durationDays: Int = medication.durationDays,
        inventoryMode: String = medication.inventoryMode,
        totalQuantity: Double? = medication.totalQuantity,
        currentQuantity: Double? = medication.currentQuantity,
        quantityPerDose: Double = medication.quantityPerDose
    ) {
        viewModelScope.launch {
            val ctx      = getApplication<Application>().applicationContext
            val oldTimes = getScheduledTimesFor(medication.id)
            val updated  = medication.copy(
                name            = name,
                dosage          = dosage,
                unit            = unit,
                frequency       = frequency,
                timesPerDay     = scheduledTimes.size.coerceAtLeast(1),
                scheduledTimes  = JSONArray(scheduledTimes).toString(),
                inventoryMode   = inventoryMode,
                durationDays    = durationDays,
                totalQuantity   = totalQuantity,
                currentQuantity = currentQuantity,
                quantityPerDose = quantityPerDose,
                notes           = notes?.takeIf { it.isNotBlank() },
                lastModifiedAt  = System.currentTimeMillis()
            )
            repository.updateMedication(updated)
            repository.softDeleteSchedulesForMedication(medication.id)
            val schedules = scheduledTimes.map { time ->
                MedicationScheduleEntity(medicationId = medication.id, scheduledTime = time)
            }
            repository.insertSchedules(schedules)
            // Always cancel old alarms first, then reschedule only if setting is on
            AlarmScheduler.cancelAll(ctx, medication.name, medication.id, oldTimes)
            if (com.healthmonitor.app.ui.screens.isMedicationAlarmsEnabled(ctx)) {
                scheduledTimes.forEach { time ->
                    AlarmScheduler.schedule(ctx, name, medication.id, time)
                }
            }
            refreshMedicationData()
        }
    }

    fun toggleMedicationActive(medication: MedicationEntity) {
        viewModelScope.launch {
            val ctx     = getApplication<Application>().applicationContext
            val updated = medication.copy(
                isActive       = !medication.isActive,
                lastModifiedAt = System.currentTimeMillis()
            )
            repository.updateMedication(updated)
            val times = getScheduledTimesFor(medication.id)
            if (updated.isActive && com.healthmonitor.app.ui.screens.isMedicationAlarmsEnabled(ctx)) {
                times.forEach { AlarmScheduler.schedule(ctx, updated.name, updated.id, it) }
            } else {
                AlarmScheduler.cancelAll(ctx, medication.name, medication.id, times)
            }
            refreshMedicationData()
        }
    }

    fun deleteMedication(medication: MedicationEntity) {
        viewModelScope.launch {
            val ctx   = getApplication<Application>().applicationContext
            val times = getScheduledTimesFor(medication.id)
            AlarmScheduler.cancelAll(ctx, medication.name, medication.id, times)
            repository.softDeleteSchedulesForMedication(medication.id)
            repository.updateMedication(
                medication.copy(isDeleted = true, lastModifiedAt = System.currentTimeMillis())
            )
            refreshMedicationData()
        }
    }

    /**
     * Refill a STOCK-mode medication: reset currentQuantity to [newQuantity]
     * and update totalQuantity if the new value is larger than the old total.
     * Called from the "تجديد" button on the medication card.
     */
    fun refillMedication(medication: MedicationEntity, newQuantity: Double) {
        viewModelScope.launch {
            val newTotal = medication.totalQuantity?.let { maxOf(it, newQuantity) } ?: newQuantity
            repository.updateMedication(
                medication.copy(
                    currentQuantity = newQuantity,
                    totalQuantity   = newTotal,
                    lastModifiedAt  = System.currentTimeMillis()
                )
            )
            refreshMedicationData()
        }
    }

    // ── Medication log ────────────────────────────────────────────────────

    fun recordMedicationTaken(medicationId: String, date: Long, time: Long) {
        setMedicationDoseTaken(medicationId, "", true, date, time)
    }

    fun setMedicationDoseTaken(
        medicationId: String,
        scheduledTime: String,
        taken: Boolean,
        date: Long = getTodayMillis(),
        actualTime: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            val patientId   = _currentPatient.value?.id ?: return@launch
            val existingLog = repository.getMedicationLogForDose(medicationId, date, scheduledTime)
            val medication  = repository.getMedicationById(medicationId)
            when {
                existingLog != null -> {
                    repository.updateMedicationLog(
                        existingLog.copy(
                            time           = actualTime,
                            taken          = taken,
                            lastModifiedAt = System.currentTimeMillis()
                        )
                    )
                    if (existingLog.taken != taken) {
                        adjustMedicationStock(medication, decrement = taken)
                    }
                }
                taken -> {
                    repository.insertMedicationLog(
                        MedicationLogEntity(
                            medicationId  = medicationId,
                            patientId     = patientId,
                            date          = date,
                            scheduledTime = scheduledTime,
                            time          = actualTime,
                            taken         = true
                        )
                    )
                    adjustMedicationStock(medication, decrement = true)
                }
            }
        }
    }

    private suspend fun adjustMedicationStock(medication: MedicationEntity?, decrement: Boolean) {
        if (medication == null || medication.inventoryMode != MedicationInventoryMode.STOCK) return
        val current = medication.currentQuantity ?: medication.totalQuantity ?: return
        val delta   = medication.quantityPerDose.takeIf { it > 0.0 } ?: 1.0
        val updatedQuantity = if (decrement) {
            (current - delta).coerceAtLeast(0.0)
        } else {
            val restored = current + delta
            medication.totalQuantity?.let { restored.coerceAtMost(it) } ?: restored
        }
        repository.updateMedication(
            medication.copy(
                currentQuantity = updatedQuantity,
                lastModifiedAt  = System.currentTimeMillis()
            )
        )
    }

    // ── Blood pressure ────────────────────────────────────────────────────

    fun recordBloodPressure(
        systolic: Int,
        diastolic: Int,
        pulse: Int?,
        oxygenSaturation: Int?,
        notes: String?
    ) {
        viewModelScope.launch {
            val patientId = _currentPatient.value?.id ?: return@launch
            repository.insertBloodPressure(
                BloodPressureEntity(
                    patientId        = patientId,
                    systolic         = systolic,
                    diastolic        = diastolic,
                    pulse            = pulse,
                    oxygenSaturation = oxygenSaturation,
                    date             = getTodayMillis(),
                    time             = System.currentTimeMillis(),
                    notes            = notes
                )
            )
        }
    }

    fun deleteBloodPressure(reading: BloodPressureEntity) {
        viewModelScope.launch { repository.deleteBloodPressure(reading) }
    }

    // ── Body temperature ──────────────────────────────────────────────────

    fun recordBodyTemperature(temperature: Float, site: String = "oral", notes: String?) {
        viewModelScope.launch {
            val patientId = _currentPatient.value?.id ?: return@launch
            val now = System.currentTimeMillis()
            repository.insertBodyTemperature(
                BodyTemperatureEntity(
                    patientId   = patientId,
                    temperature = temperature,
                    site        = site,
                    date        = getTodayMillis(),
                    time        = now,
                    notes       = notes?.takeIf { it.isNotBlank() }
                )
            )
        }
    }

    fun deleteBodyTemperature(reading: BodyTemperatureEntity) {
        viewModelScope.launch { repository.deleteBodyTemperature(reading) }
    }

    // ── Symptoms ──────────────────────────────────────────────────────────

    fun recordSymptom(
        symptomType: String,
        severity: String,
        notes: String?,
        inhalerUsed: Boolean,
        improvementAfterInhaler: Boolean?
    ) {
        viewModelScope.launch {
            val patientId = _currentPatient.value?.id ?: return@launch
            val now = System.currentTimeMillis()
            repository.insertSymptom(
                SymptomEntity(
                    patientId               = patientId,
                    date                    = getTodayMillis(),
                    time                    = now,
                    symptomType             = symptomType,
                    severity                = severity,
                    notes                   = notes?.takeIf { it.isNotBlank() },
                    inhalerUsed             = inhalerUsed,
                    improvementAfterInhaler = improvementAfterInhaler
                )
            )
        }
    }

    fun deleteSymptom(symptom: SymptomEntity) {
        viewModelScope.launch {
            repository.updateSymptom(
                symptom.copy(isDeleted = true, lastModifiedAt = System.currentTimeMillis())
            )
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun calculateAdherence() {
        val schedMap   = _scheduleMap.value
        val totalDoses = _todayMedications.value.sumOf { med ->
            schedMap[med.id]?.size?.coerceAtLeast(1) ?: 1
        }
        val takenDoses = _medicationLogs.value.count { it.taken }
        _todayDoseCount.value    = totalDoses
        _medicationAdherence.value = if (totalDoses > 0) (takenDoses * 100) / totalDoses else 0
    }

    fun getScheduledTimesFor(medId: String): List<String> =
        _scheduleMap.value[medId] ?: emptyList()

    fun parseScheduledTimes(rawValue: String): List<String> = parseMedicationTimes(rawValue)

    fun setSelectedMedicationDate(dateMillis: Long) {
        _selectedMedicationDate.value = dateMillis
        val patientId = _currentPatient.value?.id ?: return
        _todayMedications.value = _allMedications.value.filter {
            it.isActive && isMedicationScheduledOnDate(it, dateMillis)
        }
        collectMedicationLogs(patientId)
        calculateAdherence()
    }

    fun moveSelectedMedicationDate(days: Int) {
        setSelectedMedicationDate(addDays(_selectedMedicationDate.value, days))
    }

    private fun getTodayMillis(): Long =
        startOfDayMillis(LocalDate.now(), ZoneId.systemDefault())

    private fun defaultSymptomTypes() = listOf(
        "كحة نفس", "صفير / تزييق", "ألم في الصدر", "إرهاق", "سعال"
    )
}