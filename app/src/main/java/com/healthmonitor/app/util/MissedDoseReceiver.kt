package com.healthmonitor.app.util

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
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fires exactly FOLLOW_UP_DELAY_MINUTES after a scheduled dose time.
 * Checks the DB — if the dose still hasn't been marked taken, posts a
 * gentle "did you take your medication?" notification.
 *
 * Scheduled by [MedicationAlarmReceiver] every time a dose alarm fires.
 * Cancelled automatically if the user marks the dose taken from the
 * alarm screen (because the check in doWork will find taken=true and exit).
 */
class MissedDoseReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MissedDoseEntryPoint {
        fun database(): HealthMonitorDatabase
    }

    override fun onReceive(context: Context, intent: Intent) {
        val medicationId   = intent.getStringExtra(EXTRA_MEDICATION_ID)   ?: return
        val medicationName = intent.getStringExtra(EXTRA_MEDICATION_NAME) ?: return
        val scheduledTime  = intent.getStringExtra(EXTRA_SCHEDULED_TIME)  ?: return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val db = EntryPointAccessors
                    .fromApplication(context.applicationContext, MissedDoseEntryPoint::class.java)
                    .database()

                val today  = startOfDayMillis()
                val log    = db.medicationLogDao().getLogForDose(medicationId, today, scheduledTime)
                val taken  = log?.taken == true

                if (!taken) {
                    Log.i(TAG, "dose not taken for $medicationName at $scheduledTime — posting follow-up")
                    postMissedDoseNotification(context, medicationId, medicationName, scheduledTime)
                } else {
                    Log.i(TAG, "dose already taken for $medicationName at $scheduledTime — no follow-up needed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "missed dose check failed: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postMissedDoseNotification(
        context: Context,
        medicationId: String,
        medicationName: String,
        scheduledTime: String
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Reuse the existing alarm channel — it's already IMPORTANCE_HIGH
        val channel = NotificationChannel(
            CHANNEL_ID,
            "تنبيهات الأدوية",
            NotificationManager.IMPORTANCE_DEFAULT   // softer than the alarm notification
        ).apply {
            description = "تذكيرات الجرعات الفائتة"
        }
        manager.createNotificationChannel(channel)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val tapPi = PendingIntent.getActivity(
            context,
            (medicationId + scheduledTime + "missed").hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeLabel = format12Hour(scheduledTime)
        val body      = "لم تأخذ $medicationName بعد — كان موعدها $timeLabel"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("⏰ جرعة لم تُؤخذ بعد")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(tapPi)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

        val notificationId = (medicationId + scheduledTime + "missed").hashCode()
        manager.notify(notificationId, notification)
    }

    companion object {
        private const val TAG                  = "MissedDoseReceiver"
        private const val CHANNEL_ID           = "medication_reminders_v4"
        const val EXTRA_MEDICATION_ID          = "medication_id"
        const val EXTRA_MEDICATION_NAME        = "medication_name"
        const val EXTRA_SCHEDULED_TIME         = "scheduled_time"

        /** How long after the scheduled time we check whether the dose was taken */
        const val FOLLOW_UP_DELAY_MINUTES      = 60

        /**
         * Schedules a one-shot follow-up check [FOLLOW_UP_DELAY_MINUTES] after
         * the dose was due. Called from [MedicationAlarmReceiver] after it fires.
         */
        fun schedule(
            context: Context,
            medicationId: String,
            medicationName: String,
            scheduledTime: String
        ) {
            val triggerMillis = System.currentTimeMillis() +
                    FOLLOW_UP_DELAY_MINUTES * 60_000L

            val intent = Intent(context, MissedDoseReceiver::class.java).apply {
                putExtra(EXTRA_MEDICATION_ID,   medicationId)
                putExtra(EXTRA_MEDICATION_NAME, medicationName)
                putExtra(EXTRA_SCHEDULED_TIME,  scheduledTime)
            }

            val pi = PendingIntent.getBroadcast(
                context,
                (medicationId + scheduledTime + "missed").hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as
                    android.app.AlarmManager

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    !alarmManager.canScheduleExactAlarms()
                ) {
                    alarmManager.setAndAllowWhileIdle(
                        android.app.AlarmManager.RTC_WAKEUP, triggerMillis, pi
                    )
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        android.app.AlarmManager.RTC_WAKEUP, triggerMillis, pi
                    )
                }
                Log.i(TAG, "follow-up check scheduled for $medicationName in $FOLLOW_UP_DELAY_MINUTES min")
            } catch (e: Exception) {
                Log.w(TAG, "could not schedule follow-up: ${e.message}")
            }
        }
    }
}