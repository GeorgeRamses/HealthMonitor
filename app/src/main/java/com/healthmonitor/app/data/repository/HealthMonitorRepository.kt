package com.healthmonitor.app.data.repository

import com.healthmonitor.app.data.local.HealthMonitorDatabase
import com.healthmonitor.app.data.local.entities.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthMonitorRepository @Inject constructor(
    private val db: HealthMonitorDatabase
) {
    // Patient
    suspend fun insertPatient(p: PatientEntity) = db.patientDao().insert(p)
    suspend fun updatePatient(p: PatientEntity) = db.patientDao().update(p)
    fun getFirstPatient(): Flow<PatientEntity?> = db.patientDao().getFirstPatient()
    fun getAllPatients(): Flow<List<PatientEntity>> = db.patientDao().getAllPatients()
    suspend fun getPatientById(id: String): PatientEntity? = db.patientDao().getPatient(id)
    suspend fun getFirstPatientOnce(): PatientEntity? = db.patientDao().getFirstPatientOnce()

    // Medications
    suspend fun insertMedication(m: MedicationEntity) = db.medicationDao().insert(m)
    suspend fun updateMedication(m: MedicationEntity) = db.medicationDao().update(m)
    suspend fun deleteMedication(m: MedicationEntity) = db.medicationDao().delete(m)
    fun getAllMedicationsByPatientAndCase(pid: String, cid: String) =
        db.medicationDao().getAllMedicationsByPatientAndCase(pid, cid)

    suspend fun getMedicationsByPatientOnce(pid: String) = db.medicationDao().getMedicationsByPatientOnce(pid)
    suspend fun getMedicationById(id: String) = db.medicationDao().getMedicationById(id)

    suspend fun getAllActiveMedicationsOnce() = db.medicationDao().getAllMedicationsOnce()

    // Medication Schedules
    suspend fun insertSchedule(s: MedicationScheduleEntity) = db.medicationScheduleDao().insert(s)
    suspend fun insertSchedules(schedules: List<MedicationScheduleEntity>) =
        db.medicationScheduleDao().insertAll(schedules)

    suspend fun updateSchedule(s: MedicationScheduleEntity) = db.medicationScheduleDao().update(s)
    fun getSchedulesForMedication(medId: String) = db.medicationScheduleDao().getSchedulesForMedication(medId)
    suspend fun getSchedulesForMedicationOnce(medId: String) =
        db.medicationScheduleDao().getSchedulesForMedicationOnce(medId)

    suspend fun getAllSchedules() = db.medicationScheduleDao().getAllSchedules()
    suspend fun softDeleteSchedulesForMedication(medId: String) =
        db.medicationScheduleDao().softDeleteForMedication(medId, System.currentTimeMillis())

    suspend fun getSchedulesModifiedSince(since: Long) = db.medicationScheduleDao().getModifiedSince(since)

    // Cases
    suspend fun insertCase(c: CaseEntity) = db.caseDao().insert(c)
    suspend fun updateCase(c: CaseEntity) = db.caseDao().update(c)
    fun getCasesByPatient(pid: String) = db.caseDao().getCasesByPatient(pid)
    suspend fun getCaseById(id: String) = db.caseDao().getCaseById(id)

    // Medication Logs
    suspend fun insertMedicationLog(l: MedicationLogEntity) = db.medicationLogDao().insert(l)
    suspend fun updateMedicationLog(l: MedicationLogEntity) = db.medicationLogDao().update(l)
    suspend fun getMedicationLogForDose(medId: String, date: Long, scheduledTime: String) =
        db.medicationLogDao().getLogForDose(medId, date, scheduledTime)


    fun getLogsForDate(pid: String, caseId: String, date: Long) =
        db.medicationLogDao().getLogsForDate(pid, caseId, date)

    suspend fun getTakenCountForDate(pid: String, caseId: String, date: Long) =
        db.medicationLogDao().getTakenCount(pid, caseId, date)

    fun getLogsForMedications(medicationIds: List<String>, date: Long) =
        db.medicationLogDao().getLogsForMedications(medicationIds, date)

    fun getLogsForMedications(medicationIds: List<String>) =
        db.medicationLogDao().getLogsForMedications(medicationIds)

    fun getLastTakenLog(medicationId: String) = db.medicationLogDao().getLastTakenLog(medicationId)
    fun getTakenDoseCount(medicationId: String) = db.medicationLogDao().getTakenDoseCount(medicationId)

    // Blood Pressure
    suspend fun insertBloodPressure(r: BloodPressureEntity) = db.bloodPressureDao().insert(r)
    suspend fun deleteBloodPressure(r: BloodPressureEntity) = db.bloodPressureDao().delete(r)
    fun getBloodPressureReadings(pid: String) = db.bloodPressureDao().getReadingsByPatient(pid)
    fun getBloodPressureReadingsInRange(pid: String, fromDate: Long, toDate: Long) =
        db.bloodPressureDao().getReadingsByPatientInRange(pid, fromDate, toDate)

    suspend fun getLatestBloodPressure(pid: String) = db.bloodPressureDao().getLatestReading(pid)
    suspend fun getLatestBloodPressureInRange(pid: String, fromDate: Long, toDate: Long) =
        db.bloodPressureDao().getLatestReadingInRange(pid, fromDate, toDate)

    // Body Temperature
    suspend fun insertBodyTemperature(r: BodyTemperatureEntity) = db.bodyTemperatureDao().insert(r)
    suspend fun deleteBodyTemperature(r: BodyTemperatureEntity) = db.bodyTemperatureDao().delete(r)
    fun getBodyTemperatureReadings(pid: String) = db.bodyTemperatureDao().getReadingsByPatient(pid)
    fun getBodyTemperatureReadingsInRange(pid: String, fromDate: Long, toDate: Long) =
        db.bodyTemperatureDao().getReadingsByPatientInRange(pid, fromDate, toDate)

    suspend fun getLatestBodyTemperature(pid: String) = db.bodyTemperatureDao().getLatestReading(pid)
    suspend fun upsertBodyTemperature(r: BodyTemperatureEntity) = db.bodyTemperatureDao().upsert(r)

    // Sync helpers — return records modified after a given timestamp
    suspend fun getPatientsModifiedSince(since: Long) = db.patientDao().getModifiedSince(since)
    suspend fun getCasesModifiedSince(since: Long) = db.caseDao().getModifiedSince(since)
    suspend fun getMedicationsModifiedSince(since: Long) = db.medicationDao().getModifiedSince(since)
    suspend fun getLogsModifiedSince(since: Long) = db.medicationLogDao().getModifiedSince(since)
    suspend fun getBpReadingsModifiedSince(since: Long) = db.bloodPressureDao().getModifiedSince(since)
    suspend fun getSymptomsModifiedSince(since: Long) = db.symptomDao().getModifiedSince(since)

    // Upsert helpers used when merging remote records into local DB
    suspend fun upsertPatient(p: PatientEntity) = db.patientDao().insert(p)
    suspend fun upsertCase(c: CaseEntity) = db.caseDao().insert(c)
    suspend fun upsertMedication(m: MedicationEntity) = db.medicationDao().insert(m)
    suspend fun upsertSchedule(s: MedicationScheduleEntity) = db.medicationScheduleDao().insert(s)
    suspend fun upsertMedicationLog(l: MedicationLogEntity) = db.medicationLogDao().insert(l)
    suspend fun upsertBloodPressure(r: BloodPressureEntity) = db.bloodPressureDao().upsert(r)
    suspend fun upsertSymptom(s: SymptomEntity) = db.symptomDao().insert(s)

    // Symptoms
    suspend fun insertSymptom(s: SymptomEntity) = db.symptomDao().insert(s)
    suspend fun updateSymptom(s: SymptomEntity) = db.symptomDao().update(s)
    suspend fun deleteSymptom(s: SymptomEntity) = db.symptomDao().delete(s)
    fun getSymptomsByPatient(pid: String) = db.symptomDao().getSymptomsByPatient(pid)
    fun getSymptomsByPatientInRange(pid: String, fromDate: Long, toDate: Long) =
        db.symptomDao().getSymptomsByPatientInRange(pid, fromDate, toDate)

    // Inhaler
    suspend fun insertInhalerLog(l: InhalerLogEntity) = db.inhalerLogDao().insert(l)
    fun getInhalerLogs(pid: String) = db.inhalerLogDao().getLogsByPatient(pid)
    suspend fun getInhalerUsageCount(pid: String, start: Long, end: Long) =
        db.inhalerLogDao().getUsageCount(pid, start, end)

    // Lab Tests
    suspend fun insertLabTest(t: LabTestEntity) = db.labTestDao().insert(t)
    fun getLabTests(pid: String) = db.labTestDao().getTestsByPatient(pid)

    // Doctor Notes
    suspend fun insertDoctorNote(n: DoctorNoteEntity) = db.doctorNoteDao().insert(n)
    fun getDoctorNotes(pid: String) = db.doctorNoteDao().getNotesByPatient(pid)


    // Lab Reports
    suspend fun insertLabReport(r: LabReportEntity) = db.labReportDao().insert(r)
    suspend fun updateLabReport(r: LabReportEntity) = db.labReportDao().update(r)
    suspend fun softDeleteLabReport(id: String) =
        db.labReportDao().softDelete(id, System.currentTimeMillis())

    fun getLabReportsByPatient(pid: String) = db.labReportDao().getReportsByPatient(pid)
    fun getLabReportsByPatientInRange(pid: String, fromDate: Long, toDate: Long) =
        db.labReportDao().getReportsByPatientInRange(pid, fromDate, toDate)

    suspend fun getLabReportById(id: String) = db.labReportDao().getReportById(id)

    // Lab Report Items
    suspend fun insertLabReportItems(items: List<LabReportItemEntity>) =
        db.labReportItemDao().insertAll(items)

    fun getItemsForReport(reportId: String) = db.labReportItemDao().getItemsForReport(reportId)
    suspend fun getItemsForReportOnce(reportId: String) =
        db.labReportItemDao().getItemsForReportOnce(reportId)

    suspend fun deleteItemsForReport(reportId: String) =
        db.labReportItemDao().deleteItemsForReport(reportId)
}

