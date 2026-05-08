package com.healthmonitor.app.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.healthmonitor.app.MainActivity
import java.util.Calendar

/**
 * Central scheduler for all medication alarms.
 *
 * All existing call-sites in DashboardViewModel, CaseViewModel,
 * MedicationReminderViewModel, BootReceiver, and MainActivity keep working
 * unchanged because the public API (schedule / cancel / cancelAll / reschedule)
 * is identical to the original.
 *
 * New additions:
 *  - scheduleSnooze()  → one-shot alarm N minutes from now
 *  - Passes dosage string through to the receiver so the alarm UI can show it
 *  - Uses setAlarmClock() which is the highest-priority alarm type on all
 *    Android versions and shows the clock icon in the status bar
 */
object AlarmScheduler {

    private const val TAG = "AlarmScheduler"

    // ── Public API (unchanged signatures — drop-in replacement) ───────────────

    /**
     * Schedule a daily exact alarm for a single medication dose.
     *
     * @param context Application context
     * @param name    Medication name displayed in the alarm UI and notification
     * @param medId   Stable UUID of the medication (used as alarm identity)
     * @param time    "HH:mm" 24-hour string, e.g. "08:00"
     * @param dosage  Optional dosage label shown on the alarm screen (e.g. "5 mg")
     */
    fun schedule(
        context: Context,
        name: String,
        medId: String,
        time: String,
        dosage: String = ""
    ) {
        val (hour, minute) = parseTime(time) ?: run {
            Log.w(TAG, "invalid time '$time' for $name — skipping")
            return
        }

        val triggerMillis = nextOccurrenceMillis(hour, minute)
        val pending = buildPendingIntent(context, name, medId, time, dosage)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                // Fallback: setAlarmClock is user-visible (clock icon in status bar) and
                // is granted even without USE_EXACT_ALARM on most devices.
                val showIntent = PendingIntent.getActivity(
                    context,
                    alarmRequestCode(medId, time) + 1,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerMillis, showIntent),
                    pending
                )
                Log.i(TAG, "setAlarmClock (no-exact-perm fallback) for $name at $time")
            } else {
                // Best-effort exact alarm that fires even in Doze mode
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pending
                )
                Log.i(TAG, "setAlarmClock for $name at $time → trigger=$triggerMillis")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException — falling back to inexact: ${e.message}")
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
        } catch (e: Exception) {
            Log.e(TAG, "schedule failed for $name: ${e.message}")
        }
    }

    /** Cancel the daily alarm for a specific medication+time slot. */
    fun cancel(context: Context, name: String, medId: String, time: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = buildPendingIntent(context, name, medId, time, "")
        alarmManager.cancel(pi)
        Log.i(TAG, "cancelled alarm for $name at $time")
    }

    /** Cancel every scheduled alarm for a medication (all its time slots). */
    fun cancelAll(context: Context, name: String, medId: String, times: List<String>) {
        times.forEach { cancel(context, name, medId, it) }
    }

    /**
     * Cancel old alarms and schedule new ones.
     * Called when a medication's name or scheduled times change.
     */
    fun reschedule(
        context: Context,
        oldName: String,
        newName: String,
        medId: String,
        oldTimes: List<String>,
        newTimes: List<String>,
        dosage: String = ""
    ) {
        cancelAll(context, oldName, medId, oldTimes)
        newTimes.forEach { schedule(context, newName, medId, it, dosage) }
    }

    // ── Snooze (new) ──────────────────────────────────────────────────────────

    /**
     * Schedule a one-shot snooze alarm [snoozeMinutes] minutes from now.
     * The alarm fires the same MedicationAlarmReceiver with EXTRA_IS_SNOOZE = true
     * so that the already-taken check is bypassed.
     */
    fun scheduleSnooze(
        context: Context,
        medicationId: String,
        medicationName: String,
        dosage: String,
        scheduledTime: String,
        snoozeMinutes: Int
    ) {
        val triggerMillis = System.currentTimeMillis() + snoozeMinutes * 60_000L
        val uniqueCode = ("snz_$medicationId$scheduledTime").hashCode() and Int.MAX_VALUE

        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            putExtra(MedicationAlarmReceiver.EXTRA_MEDICATION_ID, medicationId)
            putExtra(MedicationAlarmReceiver.EXTRA_MEDICATION_NAME, medicationName)
            putExtra(MedicationAlarmReceiver.EXTRA_DOSAGE, dosage)
            putExtra(MedicationAlarmReceiver.EXTRA_SCHEDULED_TIME, scheduledTime)
            putExtra(MedicationAlarmReceiver.EXTRA_IS_SNOOZE, true)
        }

        val pi = PendingIntent.getBroadcast(
            context,
            uniqueCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )


        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
            }
            Log.i(TAG, "snooze alarm set for $medicationName in $snoozeMinutes min")
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
            Log.w(TAG, "snooze SecurityException — inexact fallback: ${e.message}")
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun buildPendingIntent(
        context: Context,
        name: String,
        medId: String,
        time: String,
        dosage: String
    ): PendingIntent {
        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            putExtra(MedicationAlarmReceiver.EXTRA_MEDICATION_ID, medId)
            putExtra(MedicationAlarmReceiver.EXTRA_MEDICATION_NAME, name)
            putExtra(MedicationAlarmReceiver.EXTRA_DOSAGE, dosage)
            putExtra(MedicationAlarmReceiver.EXTRA_SCHEDULED_TIME, time)
            putExtra(MedicationAlarmReceiver.EXTRA_IS_SNOOZE, false)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmRequestCode(medId, time),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Returns the next occurrence (in millis) of [hour]:[minute] local time.
     * If that time has already passed today, schedules for tomorrow.
     */
    private fun nextOccurrenceMillis(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun parseTime(time: String): Pair<Int, Int>? {
        val parts = time.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h to m
    }

    /** Unique PendingIntent request code per medication+time (daily alarms). */
    private fun alarmRequestCode(medId: String, time: String): Int =
        com.healthmonitor.app.util.alarmRequestCode(medId, time)  // delegates to Medicationutils.kt

    /** Separate namespace for snooze PendingIntents so they never collide with daily alarms. */
    private fun snoozeRequestCode(medId: String, time: String): Int =
        ("snz_$medId$time").hashCode() and Int.MAX_VALUE
}