package com.healthmonitor.app.util

import org.json.JSONArray
import java.util.Calendar

/**
 * Single source of truth for parsing a medication's scheduledTimes JSON/CSV string.
 * Replaces 5 identical private copies spread across ViewModels and Receivers.
 */
fun parseMedicationTimes(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    return try {
        val json = JSONArray(raw)
        buildList { for (i in 0 until json.length()) add(json.getString(i)) }
    } catch (_: Exception) {
        raw.split(",", "-").map { it.trim() }.filter { it.isNotBlank() }
    }
}

/**
 * Generates a unique, collision-safe PendingIntent request code for a
 * medication alarm.  Uses the medication's database id (stable, unique)
 * XOR'd with the time-of-day encoded as HHMM to avoid clashes when two
 * different medications share the same scheduled time.
 *
 *   medId=1  time=08:00 → requestCode = 1_000_000 + 800  = 1_000_800
 *   medId=2  time=08:00 → requestCode = 2_000_000 + 800  = 2_000_800
 *   medId=1  time=20:00 → requestCode = 1_000_000 + 2000 = 1_002_000
 *
 * The old formula (medId*1000 + totalMinutes) collided: medId=2,time=08:20
 * gave 2500 and medId=25,time=00:00 also gave 25000 — but more dangerously
 * medId=1,time=10:00 (600 min) == medId=600,time=00:00 (0 min).
 */
fun alarmRequestCode(medId: String, time: String): Int {
    val parts = time.split(":")
    val hhmm = (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 100 +
            (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    // medId is a UUID string, so we use its hashCode as a stable Int
    return medId.hashCode() + hhmm
}

/** Returns midnight (00:00:00.000) of today in local time as epoch-millis. */
fun startOfDayMillis(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun format12Hour(hour: Int, minute: Int): String {
    val amPm = if (hour < 12) "AM" else "PM"
    val h = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "%d:%02d %s".format(h, minute, amPm)
}

/** Converts "HH:mm" 24-hour string to "h:mm AM/PM" Arabic-friendly string. */
fun format12Hour(time24: String): String {
    val parts = time24.split(":").mapNotNull { it.toIntOrNull() }
    if (parts.size != 2) return time24
    val h = parts[0];
    val m = parts[1]
    val period = if (h < 12) "صباحاً" else "مساءً"
    val h12 = when {
        h == 0 -> 12; h > 12 -> h - 12; else -> h
    }
    return "%d:%02d %s".format(h12, m, period)
}