package com.healthmonitor.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.healthmonitor.app.data.local.HealthMonitorDatabase
import com.healthmonitor.app.util.ActiveCaseManager
import com.healthmonitor.app.util.ActivePatientManager
import com.healthmonitor.app.util.AlarmVerificationWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class HealthMonitorApplication : Application(), Configuration.Provider {

    // Hilt-aware WorkManager factory — required when using @HiltWorker
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    // WorkManager configuration — must provide this when using Hilt workers
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

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

        // Start the periodic alarm verification heartbeat.
        // Safe to call every launch — WorkManager deduplicates via a unique name.
        AlarmVerificationWorker.enqueue(this)
    }

    private suspend fun validateRestoredSelections() {
        val db = HealthMonitorDatabase.getDatabase(this)

        val patientId = ActivePatientManager.getActivePatientId()
        if (patientId != null) {
            val patient = db.patientDao().getPatient(patientId)
            if (patient == null || patient.isDeleted) {
                ActivePatientManager.clearActivePatient(this)
            }
        }

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