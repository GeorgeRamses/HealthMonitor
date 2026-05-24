package com.healthmonitor.app.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.healthmonitor.app.data.local.HealthMonitorDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reschedules all active medication alarms after device reboot.
 *
 * Note: MainActivity also schedules alarms on first launch, but the two
 * paths use setExactAndAllowWhileIdle with FLAG_UPDATE_CURRENT so
 * a duplicate call simply overwrites the existing alarm — no double-fire.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "received: $action")
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val db   = HealthMonitorDatabase.getDatabase(appContext)
                val meds = db.medicationDao().getAllMedicationsOnce()   // only isActive = 1
                var scheduled = 0
                meds.forEach { med ->
                    parseMedicationTimes(med.scheduledTimes).forEach { time ->
                        AlarmScheduler.schedule(appContext, med.name, med.id, time, dosageLabel(med.dosage, med.unit))
                        scheduled++
                    }
                }
                Log.i(TAG, "rescheduled $scheduled alarms for ${meds.size} medications after boot")
            }.onFailure { e ->
                Log.e(TAG, "failed to reschedule alarms after boot: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}

private fun dosageLabel(dosage: String, unit: String): String =
    listOf(dosage.trim(), unit.trim()).filter { it.isNotBlank() }.joinToString(" ")
