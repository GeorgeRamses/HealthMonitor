package com.healthmonitor.app.util

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.healthmonitor.app.data.local.HealthMonitorDatabase
import com.healthmonitor.app.ui.screens.isMedicationAlarmsEnabled
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager job that runs every 6 hours.
 *
 * Problem it solves:
 *   Xiaomi, OPPO, Vivo, Huawei and other OEMs aggressively kill AlarmManager
 *   entries when the app is swiped from recents or when battery optimisation
 *   kicks in. The BootReceiver covers device restarts, but mid-session kills
 *   are invisible to the app. This worker silently re-schedules any alarms that
 *   may have been dropped — call it a "heartbeat" for the alarm system.
 *
 * Why WorkManager and not a repeating AlarmManager?
 *   WorkManager is Doze-aware and guaranteed to run even on restricted devices.
 *   It uses JobScheduler on API 23+ which the OS respects even under battery
 *   optimisation. An AlarmManager-based repeating job would itself be a victim
 *   of the very problem we're trying to solve.
 */
@HiltWorker
class AlarmVerificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "alarm verification started")

        if (!isMedicationAlarmsEnabled(applicationContext)) {
            Log.i(TAG, "alarms globally disabled — skipping verification")
            return Result.success()
        }

        return try {
            val db   = HealthMonitorDatabase.getDatabase(applicationContext)
            val meds = db.medicationDao().getAllMedicationsOnce()  // isActive = 1 only

            var rescheduled = 0
            meds.forEach { med ->
                parseMedicationTimes(med.scheduledTimes).forEach { time ->
                    AlarmScheduler.schedule(applicationContext, med.name, med.id, time, dosageLabel(med.dosage, med.unit))
                    rescheduled++
                }
            }

            Log.i(TAG, "verification complete — re-scheduled $rescheduled alarms for ${meds.size} medications")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "verification failed: ${e.message}")
            // Retry up to WorkManager's built-in back-off policy
            Result.retry()
        }
    }

    private fun dosageLabel(dosage: String, unit: String): String =
        listOf(dosage.trim(), unit.trim()).filter { it.isNotBlank() }.joinToString(" ")

    companion object {
        private const val TAG         = "AlarmVerificationWorker"
        private const val WORK_NAME   = "alarm_verification_periodic"

        /**
         * Enqueues the periodic job. Safe to call multiple times — WorkManager
         * will deduplicate using [WORK_NAME] and [ExistingPeriodicWorkPolicy.KEEP]
         * so existing schedules are never disrupted.
         *
         * Call this once from [com.healthmonitor.app.HealthMonitorApplication.onCreate].
         */
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<AlarmVerificationWorker>(
                repeatInterval    = 6,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(
                    Constraints.Builder()
                        // Only run when the device is not actively low on battery
                        .setRequiresBatteryNotLow(false)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

            Log.i(TAG, "periodic alarm verification enqueued (every 6 hours)")
        }
    }
}
