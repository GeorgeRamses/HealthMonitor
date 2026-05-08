package com.healthmonitor.app

import android.app.Application
import com.healthmonitor.app.data.local.HealthMonitorDatabase
import com.healthmonitor.app.util.ActiveCaseManager
import com.healthmonitor.app.util.ActivePatientManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class HealthMonitorApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Restore persisted selections into StateFlows immediately
        // so ViewModels and Composables read the correct value on first access.
        ActivePatientManager.init(this)
        ActiveCaseManager.init(this)

        // Validate restored IDs against the DB in the background.
        // If a patient or case was deleted since last session, clear the stale ID.
        CoroutineScope(Dispatchers.IO).launch {
            validateRestoredSelections()
        }
    }

    private suspend fun validateRestoredSelections() {
        val db = HealthMonitorDatabase.getDatabase(this)

        // Validate active patient
        val patientId = ActivePatientManager.getActivePatientId()
        if (patientId != null) {
            val patient = db.patientDao().getPatient(patientId)
            if (patient == null || patient.isDeleted) {
                ActivePatientManager.clearActivePatient(this)
            }
        }

        // Validate active case — clear if deleted, closed, or
        // does not belong to the current active patient
        val caseId = ActiveCaseManager.getActiveCaseId()
        if (caseId != null) {
            val case = db.caseDao().getCaseById(caseId)
            val currentPatientId = ActivePatientManager.getActivePatientId()
            if (case == null
                || case.isDeleted
                || case.isClosed
                || case.patientId != currentPatientId
            ) {
                ActiveCaseManager.clearActiveCase(this)
            }
        }
    }
}