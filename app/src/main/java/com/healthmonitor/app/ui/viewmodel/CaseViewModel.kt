package com.healthmonitor.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthmonitor.app.data.local.entities.CaseEntity
import com.healthmonitor.app.data.repository.HealthMonitorRepository
import com.healthmonitor.app.util.ActiveCaseManager
import com.healthmonitor.app.util.AlarmScheduler
import com.healthmonitor.app.util.parseMedicationTimes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CaseViewModel @Inject constructor(
    application: Application,
    private val repository: HealthMonitorRepository
) : AndroidViewModel(application) {

    fun getCasesForPatient(patientId: String): Flow<List<CaseEntity>> =
        repository.getCasesByPatient(patientId)

    /**
     * @param patientId Must be the resolved patient id.
     */
    fun addCase(patientId: String, title: String, doctor: String?, notes: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertCase(
                CaseEntity(
                    patientId  = patientId,
                    doctorName = doctor,
                    title      = title,
                    notes      = notes
                )
            )
        }
    }

    fun closeCase(c: CaseEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            repository.updateCase(c.copy(isClosed = true, closedAt = now, lastModifiedAt = now))
            // Deactivate every medication belonging to this case and cancel alarms
            try {
                val ctx  = getApplication<Application>().applicationContext
                val meds = repository.getMedicationsByPatientOnce(c.patientId)
                meds.filter { it.caseId == c.id }.forEach { med ->
                    repository.updateMedication(med.copy(isActive = false, lastModifiedAt = System.currentTimeMillis()))
                    AlarmScheduler.cancelAll(
                        ctx,
                        med.name,
                        med.id, // String ID -> Int for AlarmManager
                        parseMedicationTimes(med.scheduledTimes)
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("CaseViewModel", "closeCase alarm cleanup failed: ${e.message}")
            }
        }
    }

    fun deleteCase(c: CaseEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateCase(c.copy(isDeleted = true, lastModifiedAt = System.currentTimeMillis()))
            // Deactivate and soft-delete all medications belonging to this case and cancel alarms
            try {
                val ctx  = getApplication<Application>().applicationContext
                val meds = repository.getMedicationsByPatientOnce(c.patientId)
                meds.filter { it.caseId == c.id }.forEach { med ->
                    repository.updateMedication(med.copy(isDeleted = true, isActive = false, lastModifiedAt = System.currentTimeMillis()))
                    AlarmScheduler.cancelAll(
                        ctx,
                        med.name,
                        med.id,
                        parseMedicationTimes(med.scheduledTimes)
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("CaseViewModel", "deleteCase alarm cleanup failed: ${e.message}")
            }
            // Clear the active case if it was deleted
            if (ActiveCaseManager.getActiveCaseId() == c.id) {
                ActiveCaseManager.clearActiveCase(getApplication<Application>().applicationContext)
            }
        }
    }

    fun setActiveCase(caseId: String) {
        val ctx = getApplication<Application>().applicationContext
        ActiveCaseManager.setActiveCaseId(ctx, caseId)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val case = repository.getCaseById(caseId) ?: return@launch
                val meds = repository.getMedicationsByPatientOnce(case.patientId)
                meds.filter { it.caseId == caseId && it.isActive }.forEach { med ->
                    parseMedicationTimes(med.scheduledTimes).forEach { time ->
                        AlarmScheduler.schedule(ctx, med.name, med.id, time)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CaseViewModel", "setActiveCase alarm schedule failed: ${e.message}")
            }
        }
    }
}