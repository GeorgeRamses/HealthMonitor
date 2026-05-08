package com.healthmonitor.app.ui.viewmodel

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthmonitor.app.data.local.entities.MedicationEntity
import com.healthmonitor.app.data.repository.HealthMonitorRepository
import com.healthmonitor.app.ui.design.AlarmPermissionHelper
import com.healthmonitor.app.util.AlarmScheduler
import com.healthmonitor.app.util.parseMedicationTimes
import dagger.hilt.android.internal.Contexts.getApplication
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MedicationReminderViewModel @Inject constructor(
    application: Application,
    private val repository: HealthMonitorRepository
) : AndroidViewModel(application) {
    var showA14Dialog by mutableStateOf(false)
    var showOemDialog by mutableStateOf(false)
    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = getApplication<Application>()
            .getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    fun scheduleAllMedicationAlarms(patientId: String) {
        viewModelScope.launch {
            val ctx  = getApplication<Application>().applicationContext
            val meds = repository.getMedicationsByPatientOnce(patientId)
            meds.forEach { med -> scheduleForMedication(ctx, med) }
        }
    }

    fun saveMedicationAndSchedule(med: MedicationEntity) {
        viewModelScope.launch {
            val ctx = getApplication<Application>().applicationContext
            repository.insertMedication(med)
            // med.id is already a UUID set before insertion
            parseMedicationTimes(med.scheduledTimes).forEach { time ->
                AlarmScheduler.schedule(ctx, med.name, med.id, time)
            }
        }
    }

    fun saveWithPermissionCheck(med: MedicationEntity) {
        val context = getApplication<Application>().applicationContext

        if (AlarmPermissionHelper.needsFullScreenIntentPermission(context)) {
            showA14Dialog = true
        } else {
            // إذا كان الهاتف شاومي أو ما شابه، نظهر التنبيه ولكن نكمل الحفظ
            if (AlarmPermissionHelper.isRestrictedOEM()) {
                showOemDialog = true
            }
            saveMedicationAndSchedule(med)
        }
    }
}

    private fun scheduleForMedication(context: Context, med: MedicationEntity) {
        val times = parseMedicationTimes(med.scheduledTimes)
        if (!med.isActive) {
            AlarmScheduler.cancelAll(context, med.name, med.id, times)
            return
        }
        times.forEach { AlarmScheduler.schedule(context, med.name, med.id, it) }
    }

