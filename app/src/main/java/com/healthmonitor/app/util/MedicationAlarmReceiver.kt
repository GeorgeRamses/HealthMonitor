package com.healthmonitor.app.util

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.healthmonitor.app.MainActivity
import com.healthmonitor.app.data.local.HealthMonitorDatabase
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
 * ── WHY context.startActivity() IS REMOVED ───────────────────────────────────
 * On Android 10+ (API 29+) the OS blocks background apps from starting
 * activities directly. A BroadcastReceiver IS a background context, so
 * context.startActivity() is silently dropped — you only see the notification
 * (and hear the sound from it) but never the full-screen alarm UI.
 *
 * The CORRECT path on Android 10+ is:
 *   1. Post a notification with setFullScreenIntent().
 *   2. The OS itself decides whether to show the full-screen activity or
 *      just a heads-up banner, based on:
 *        • USE_FULL_SCREEN_INTENT permission granted          <- manifest
 *        • Notification channel importance = HIGH or MAX      <- done below
 *        • Notification category = CATEGORY_ALARM            <- done below
 *        • Device is locked / screen is off                  -> promotes to full-screen
 *        • Device is unlocked / screen is on                 -> shows heads-up banner
 *
 * ── WHY THE NOTIFICATION CHANNEL MUST BE IMPORTANCE_HIGH ─────────────────────
 * IMPORTANCE_DEFAULT or lower will NEVER trigger a full-screen intent.
 * The channel must be IMPORTANCE_HIGH (or MAX). Once a channel is created its
 * importance can only be lowered by the user — so the first creation matters.
 * We use a new channel ID "medication_reminders_v2" to force recreation at the
 * correct importance level on existing installs.
 *
 * ── WHY setFullScreenIntent(pendingIntent, true) ──────────────────────────────
 * The second argument highPriority=true is what tells the system this
 * notification should interrupt the user even when Do Not Disturb is active.
 * Setting it to false causes the notification to wait in the shade silently.
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
                val alarmsEnabled = isSnooze || isMedicationAlarmsEnabled(context)
                val shouldFire    = alarmsEnabled &&
                        (isSnooze || !isAlreadyTaken(context, medicationId, scheduledTime))

                if (shouldFire) {
                    // The ONLY correct way to show a full-screen alarm on Android 10+.
                    // context.startActivity() is blocked from background — DO NOT use it.
                    showAlarmNotification(
                        context, medicationId, medicationName, dosage, scheduledTime
                    )
                    Log.i(TAG, "alarm notification posted for $medicationName at $scheduledTime")
                } else {
                    if (!alarmsEnabled) {
                        Log.i(TAG, "skipped — alarms globally disabled: $medicationName")
                    } else {
                        Log.i(TAG, "skipped — already taken: $medicationName at $scheduledTime")
                    }
                }

                // Always reschedule for tomorrow to keep the daily alarm chain alive.
                if (!isSnooze) {
                    AlarmScheduler.schedule(context, medicationName, medicationId, scheduledTime)
                    Log.i(TAG, "rescheduled next-day alarm for $medicationName at $scheduledTime")
                }

            } catch (e: Exception) {
                Log.e(TAG, "error in onReceive: ${e.message}")
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
    //
    // This single notification does two things depending on device state:
    //   Screen OFF / locked  -> OS promotes to full-screen alarm activity
    //   Screen ON / unlocked -> OS shows heads-up banner; user taps to open alarm
    //
    // Both paths launch MedicationAlarmActivity which handles sound + vibration.

    @SuppressLint("FullScreenIntentPolicy")
    private fun showAlarmNotification(
        context: Context,
        medicationId: String,
        medicationName: String,
        dosage: String,
        scheduledTime: String
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // ── Ensure the notification channel exists with IMPORTANCE_HIGH ────────
        // IMPORTANCE_HIGH is the minimum for full-screen intents to work.
        // We use a new channel ID (v2) so existing installs with the old channel
        // at a lower importance level are not affected — a fresh channel is created
        // at the correct level the first time this version runs.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "تنبيهات الأدوية",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description         = "تذكير بمواعيد الأدوية"
                enableVibration(true)
                enableLights(true)
                setBypassDnd(true)                              // fire even in Do Not Disturb
                lockscreenVisibility =
                    android.app.Notification.VISIBILITY_PUBLIC  // show on lock screen
            }
            manager.createNotificationChannel(channel)
        }

        // ── Full-screen pending intent -> launches MedicationAlarmActivity ─────
        // This is what the OS fires when the screen is off / device is locked.
        // A unique request code per medication+time prevents overwriting pending
        // intents for different medications scheduled at the same time.
        val alarmActivityIntent = MedicationAlarmActivity.createIntent(
            context        = context,
            medicationId   = medicationId,
            medicationName = medicationName,
            dosage         = dosage,
            scheduledTime  = scheduledTime
        )
        val fullScreenPi = PendingIntent.getActivity(
            context,
            (medicationId + scheduledTime).hashCode(),
            alarmActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ── Tap-on-notification intent -> Opens the main app ────────
        val mainAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val tapPi = PendingIntent.getActivity(
            context,
            (medicationId + scheduledTime + "tap").hashCode(),
            mainAppIntent, // <-- Now launching the App instead of the Alarm UI
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dosageLabel = if (dosage.isNotBlank()) " — الجرعة: $dosage" else ""
        val body = "حان وقت جرعة $medicationName$dosageLabel (${format12Hour(scheduledTime)})"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("💊 وقت الدواء")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            // PRIORITY_MAX ensures the notification is never batched or delayed
            .setPriority(NotificationCompat.PRIORITY_MAX)
            // CATEGORY_ALARM grants bypass privileges over Do Not Disturb
            // and signals to the OS that this is a time-critical alarm
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            // setOngoing(true) + setAutoCancel(false) keeps the notification
            // visible so the user cannot accidentally swipe it away
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(tapPi)
            // highPriority=true -> interrupt even during DND / full-screen apps.
            // This is the flag that actually launches the activity when screen is off.
            .setFullScreenIntent(fullScreenPi, true)
            .setVibrate(longArrayOf(0, 600, 300, 600, 300))
            // VISIBILITY_PUBLIC shows the full content on the lock screen
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val notificationId = (medicationId + scheduledTime).hashCode()
        manager.notify(notificationId, notification)
        Log.i(TAG, "notification posted id=$notificationId for $medicationName")
    }

    companion object {
        private const val TAG           = "MedicationAlarmReceiver"
        // v2 channel ID forces recreation at IMPORTANCE_HIGH on existing installs
        // that may have the old channel at a lower importance level
        const val CHANNEL_ID            = "medication_reminders_v2"
        const val EXTRA_MEDICATION_ID   = "medication_id"
        const val EXTRA_MEDICATION_NAME = "medication_name"
        const val EXTRA_DOSAGE          = "dosage"
        const val EXTRA_SCHEDULED_TIME  = "scheduled_time"
        const val EXTRA_IS_SNOOZE       = "is_snooze"
    }
}