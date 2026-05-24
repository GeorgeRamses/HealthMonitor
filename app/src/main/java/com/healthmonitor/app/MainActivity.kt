package com.healthmonitor.app

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.healthmonitor.app.data.local.HealthMonitorDatabase
import com.healthmonitor.app.ui.HealthMonitorApp
import com.healthmonitor.app.ui.theme.HealthMonitorTheme
import com.healthmonitor.app.util.ActivePatientManager
import com.healthmonitor.app.util.AlarmScheduler
import com.healthmonitor.app.util.parseMedicationTimes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) scheduleAllAlarms()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            HealthMonitorTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = Color(0xFF0F0F0F)
                ) {
                    HealthMonitorApp(context = this@MainActivity)
                }
            }
        }

        requestPermissionsAndSchedule()
    }

    // ── Permission flow ───────────────────────────────────────────────────

    private fun requestPermissionsAndSchedule() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = "package:$packageName".toUri()
                })
                return
            }
        }

        scheduleAllAlarms()
    }

    /**
     * Schedules alarms for every active medication belonging to the currently
     * active patient.  Falls back to the first patient in the database when no
     * patient has been explicitly selected yet.
     */
    private fun scheduleAllAlarms() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = HealthMonitorDatabase.getDatabase(applicationContext)

            // Prefer the persisted active-patient id; otherwise take the first patient
            val patientId = ActivePatientManager.getActivePatientId()
                .takeIf { !it.isNullOrBlank() }
                ?: db.patientDao().getFirstPatientOnce()?.id
                ?: return@launch

            val meds = db.medicationDao().getMedicationsByPatientOnce(patientId)
            meds.forEach { med ->
                parseMedicationTimes(med.scheduledTimes).forEach { time ->
                    AlarmScheduler.schedule(applicationContext, med.name, med.id, time, dosageLabel(med.dosage, med.unit))
                }
            }
        }
    }

    private fun dosageLabel(dosage: String, unit: String): String =
        listOf(dosage.trim(), unit.trim()).filter { it.isNotBlank() }.joinToString(" ")
}
