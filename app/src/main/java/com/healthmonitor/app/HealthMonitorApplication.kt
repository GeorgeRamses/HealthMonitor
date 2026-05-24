package com.healthmonitor.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.healthmonitor.app.data.local.DatabaseKeyManager
import com.healthmonitor.app.data.local.DatabaseMigrationHelper
import com.healthmonitor.app.data.local.HealthMonitorDatabase
import com.healthmonitor.app.util.ActiveCaseManager
import com.healthmonitor.app.util.ActivePatientManager
import com.healthmonitor.app.util.AlarmVerificationWorker
import com.healthmonitor.app.util.ConsentManager
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
        // Initialize Crashlytics — disabled in debug builds to avoid noise
        if (ConsentManager.isConsentGranted(this)) {
            FirebaseCrashlytics.getInstance()
                .setCrashlyticsCollectionEnabled(true)
        }

        ActivePatientManager.init(this)
        ActiveCaseManager.init(this)

        CoroutineScope(Dispatchers.IO).launch {
            // Run migration FIRST — before any DB query touches the file
            val passphrase = DatabaseKeyManager.getOrCreateKey(applicationContext)
            DatabaseMigrationHelper.migrateToEncryptedIfNeeded(applicationContext, passphrase)

            // Then validate selections — this triggers the first DB access
            validateRestoredSelections()
        }
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