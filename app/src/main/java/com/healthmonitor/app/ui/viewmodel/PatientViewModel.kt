package com.healthmonitor.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthmonitor.app.data.local.entities.PatientEntity
import com.healthmonitor.app.data.repository.HealthMonitorRepository
import com.healthmonitor.app.util.ActiveCaseManager
import com.healthmonitor.app.util.ActivePatientManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PatientViewModel @Inject constructor(
    application: Application,
    private val repository: HealthMonitorRepository
) : AndroidViewModel(application) {

    fun getAllPatients(): Flow<List<PatientEntity>> = repository.getAllPatients()

    /**
     * Reactive StateFlow of the currently-selected patient id.
     * Composables that collect this will recompose whenever the selection changes.
     */
    val activePatientIdFlow: StateFlow<String?> = ActivePatientManager.activePatientIdFlow

    fun setActivePatientId(id: String) {
        val ctx = getApplication<Application>().applicationContext
        ActivePatientManager.setActivePatientId(ctx, id)
        ActiveCaseManager.clearActiveCase(ctx)
    }

    fun addPatient(name: String, age: Int, gender: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val patient = PatientEntity(name = name, age = age, gender = gender)
            repository.insertPatient(patient)
            // Automatically select the new patient
            setActivePatientId(patient.id)
        }
    }

    fun deletePatient(patient: PatientEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updatePatient(
                patient.copy(isDeleted = true, lastModifiedAt = System.currentTimeMillis())
            )
            // Clear the active patient if it was deleted
            if (ActivePatientManager.getActivePatientId() == patient.id) {
                ActivePatientManager.clearActivePatient(getApplication<Application>().applicationContext)
            }
        }
    }
}