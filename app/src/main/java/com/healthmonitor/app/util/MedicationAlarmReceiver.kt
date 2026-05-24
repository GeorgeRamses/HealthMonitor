package com.healthmonitor.app.util

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.healthmonitor.app.MainActivity
import com.healthmonitor.app.data.local.HealthMonitorDatabase
import com.healthmonitor.app.data.local.entities.MedicationEntity
import com.healthmonitor.app.ui.screens.isMedicationAlarmsEnabled
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that fires when AlarmManager triggers a medication alarm.
 *
 * Key design decisions:
 *
 * • Channel IMPORTANCE_MAX — alarm-clock level, bypasses battery throttling.
 * • setOngoing(true) — notification cannot be swiped away; user must respond.
 * • contentIntent → NotificationTapReceiver — cancels the notification first,
 *   then opens the app. This is the fix for notifications that persist after tap.
 * • Full-screen intent → MedicationAlarmActivity — shown when screen is locked.
 * • Android 14+ fallback — direct startActivity() if USE_FULL_SCREEN_INTENT
 *   permission is not granted.
 */
class MedicationAlarmReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AlarmEntryPoint {
        fun database(): HealthMonitorDatabase
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "onReceive — action=${intent.action}")

        val medicationId   = intent.getStringExtra(EXTRA_MEDICATION_ID)   ?: ""
        val medicationName = intent.getStringExtra(EXTRA_MEDICATION_NAME) ?: "الدواء"
        val dosage         = intent.getStringExtra(EXTRA_DOSAGE)          ?: ""
        val scheduledTime  = intent.getStringExtra(EXTRA_SCHEDULED_TIME)  ?: ""
        val isSnooze       = intent.getBooleanExtra(EXTRA_IS_SNOOZE, false)

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val currentMedication = getCurrentMedicationForAlarm(context, medicationId, scheduledTime)
                if (!isSnooze && currentMedication == null) {
                    AlarmScheduler.cancel(context, medicationName, medicationId, scheduledTime)
                    Log.i(TAG, "skipped stale alarm for $medicationName at $scheduledTime")
                    return@launch
                }

                val activeName = currentMedication?.name ?: medicationName
                val activeDosage = currentMedication?.let { dosageLabel(it) } ?: dosage
                val alarmsEnabled = isSnooze || isMedicationAlarmsEnabled(context)
                val alreadyTaken  = !isSnooze && isAlreadyTaken(context, medicationId, scheduledTime)
                val shouldFire    = alarmsEnabled && !alreadyTaken

                if (!isSnooze && shouldFire) {
                    AlarmScheduler.schedule(context, activeName, medicationId, scheduledTime, activeDosage)
                    Log.i(TAG, "next-day alarm rescheduled for $activeName at $scheduledTime")
                }

                if (shouldFire) {
                    showAlarmNotification(
                        context, medicationId, activeName, activeDosage, scheduledTime
                    )
                    Log.i(TAG, "alarm notification posted for $activeName at $scheduledTime")

                    if (!isSnooze) {
                        MissedDoseReceiver.schedule(context, medicationId, activeName, scheduledTime)
                    }
                } else {
                    when {
                        !alarmsEnabled -> Log.i(TAG, "skipped — alarms globally disabled: $activeName")
                        true -> Log.i(TAG, "skipped — already taken: $activeName at $scheduledTime")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "error in onReceive coroutine: ${e.message}")
                runCatching {
                    showAlarmNotification(
                        context, medicationId, medicationName, dosage, scheduledTime
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    // ── DB check ──────────────────────────────────────────────────────────────

    private suspend fun getCurrentMedicationForAlarm(
        context: Context,
        medicationId: String,
        scheduledTime: String
    ): MedicationEntity? {
        if (medicationId.isBlank() || scheduledTime.isBlank()) return null
        return try {
            val db = EntryPointAccessors
                .fromApplication(context.applicationContext, AlarmEntryPoint::class.java)
                .database()
            val medication = db.medicationDao().getMedicationById(medicationId)
                ?.takeIf { it.isActive && !it.isDeleted }
                ?: return null
            val currentTimes = db.medicationScheduleDao()
                .getSchedulesForMedicationOnce(medicationId)
                .map { it.scheduledTime }
                .ifEmpty { parseMedicationTimes(medication.scheduledTimes) }
            medication.takeIf { scheduledTime in currentTimes }
        } catch (e: Exception) {
            Log.e(TAG, "current alarm validation failed: ${e.message}")
            null
        }
    }

    private suspend fun isAlreadyTaken(
        context: Context,
        medicationId: String,
        scheduledTime: String
    ): Boolean {
        if (medicationId.isBlank()) return false
        return try {
            val db = EntryPointAccessors
                .fromApplication(context.applicationContext, AlarmEntryPoint::class.java)
                .database()
            val today = startOfDayMillis()
            db.medicationLogDao().getLogForDose(medicationId, today, scheduledTime)?.taken == true
        } catch (e: Exception) {
            Log.e(TAG, "DB check failed: ${e.message}")
            false
        }
    }

    // ── Alarm notification ────────────────────────────────────────────────────

    @SuppressLint("FullScreenIntentPolicy")
    @Suppress("WrongConstant")
    private fun showAlarmNotification(
        context: Context,
        medicationId: String,
        medicationName: String,
        dosage: String,
        scheduledTime: String
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Ensure channel exists at IMPORTANCE_MAX
        val channel = NotificationChannel(
            CHANNEL_ID,
            "تنبيهات الأدوية",
            NotificationManager.IMPORTANCE_MAX
        ).apply {
            description         = "تذكير بمواعيد الأدوية"
            enableVibration(true)
            enableLights(true)
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)

        val notificationId = (medicationId + scheduledTime).hashCode()

        // ── Full-screen pending intent → MedicationAlarmActivity ──────────────
        // Fires when the screen is OFF / device is locked.
        val alarmActivityIntent = MedicationAlarmActivity.createIntent(
            context        = context,
            medicationId   = medicationId,
            medicationName = medicationName,
            dosage         = dosage,
            scheduledTime  = scheduledTime
        )
        val fullScreenPi = PendingIntent.getActivity(
            context,
            notificationId,
            alarmActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ── Tap (contentIntent) → NotificationTapReceiver ────────────────────
        // FIX: Routes through NotificationTapReceiver which:
        //   1. Cancels the notification (removes it from the shade immediately).
        //   2. Launches MainActivity.
        // Previously used a direct Activity PendingIntent which opened the app
        // but left the ongoing notification visible — because Android does not
        // auto-cancel ongoing notifications even when their contentIntent fires.
        val tapIntent = Intent(context, NotificationTapReceiver::class.java).apply {
            putExtra(NotificationTapReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val tapPi = PendingIntent.getBroadcast(
            context,
            notificationId + 9000,  // different request code from fullScreenPi
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dosageLabel = if (dosage.isNotBlank()) " — الجرعة: $dosage" else ""
        val body = "حان وقت جرعة $medicationName$dosageLabel (${format12Hour(scheduledTime)})"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("💊 وقت الدواء")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)           // cannot be swiped away; canceled explicitly
            .setAutoCancel(true)       // dismissed automatically when tapped
            .setContentIntent(tapPi)   // FIX: now cancels the notification on tap
            .setFullScreenIntent(fullScreenPi, true)
            .setVibrate(longArrayOf(0, 600, 300, 600, 300))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        manager.notify(notificationId, notification)
        Log.i(TAG, "notification posted id=$notificationId for $medicationName")

        // Full-screen notification promotion can be downgraded by device policy.
        // Try a direct alarm activity launch as well; if Android blocks it, the
        // alarm notification remains available as the fallback.
        Log.w(TAG, "attempting direct alarm activity launch")
        try {
            context.startActivity(
                alarmActivityIntent.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "direct startActivity() fallback failed: ${e.message}")
        }
    }

    private fun dosageLabel(medication: MedicationEntity): String =
        listOf(medication.dosage.trim(), medication.unit.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")

    companion object {
        private const val TAG           = "MedicationAlarmReceiver"
        const val CHANNEL_ID            = "medication_reminders_v4"
        const val EXTRA_MEDICATION_ID   = "medication_id"
        const val EXTRA_MEDICATION_NAME = "medication_name"
        const val EXTRA_DOSAGE          = "dosage"
        const val EXTRA_SCHEDULED_TIME  = "scheduled_time"
        const val EXTRA_IS_SNOOZE       = "is_snooze"
    }
}
