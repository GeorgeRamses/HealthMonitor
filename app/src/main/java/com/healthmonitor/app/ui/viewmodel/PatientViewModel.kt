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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PatientViewModel @Inject constructor(
    application: Application,
    private val repository: HealthMonitorRepository
) : AndroidViewModel(application) {

    fun getAllPatients(): Flow<List<PatientEntity>> = repository.getAllPatients()

    // Reactive StateFlow of the currently-selected patient id
    val activePatientIdFlow: StateFlow<String?> = ActivePatientManager.activePatientIdFlow

    // Single patient loaded for profile / edit screen
    private val _selectedPatient = MutableStateFlow<PatientEntity?>(null)
    val selectedPatient: StateFlow<PatientEntity?> = _selectedPatient.asStateFlow()

    fun loadPatient(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _selectedPatient.value = repository.getPatientById(id)
        }
    }

    fun setActivePatientId(id: String) {
        val ctx = getApplication<Application>().applicationContext
        ActivePatientManager.setActivePatientId(ctx, id)
        ActiveCaseManager.clearActiveCase(ctx)
    }

    fun addPatient(name: String, age: Int, gender: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val patient = PatientEntity(name = name, age = age, gender = gender)
            repository.insertPatient(patient)
            setActivePatientId(patient.id)
        }
    }

    /**
     * Updates all editable fields on an existing patient.
     * Preserves id, createdAt, and isDeleted — only touches user-visible data.
     */
    fun updatePatient(
        patient: PatientEntity,
        name: String,
        age: Int,
        gender: String,
        bloodType: String?,
        medicalConditions: String,
        emergencyContact: String?,
        emergencyPhone: String?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = patient.copy(
                name              = name.trim(),
                age               = age,
                gender            = gender,
                bloodType         = bloodType?.trim()?.ifBlank { null },
                medicalConditions = medicalConditions.trim(),
                emergencyContact  = emergencyContact?.trim()?.ifBlank { null },
                emergencyPhone    = emergencyPhone?.trim()?.ifBlank { null },
                lastModifiedAt    = System.currentTimeMillis()
            )
            repository.updatePatient(updated)
            // Refresh selected patient if it's the one being edited
            if (_selectedPatient.value?.id == patient.id) {
                _selectedPatient.value = updated
            }
        }
    }

    fun deletePatient(patient: PatientEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updatePatient(
                patient.copy(isDeleted = true, lastModifiedAt = System.currentTimeMillis())
            )
            if (ActivePatientManager.getActivePatientId() == patient.id) {
                ActivePatientManager.clearActivePatient(getApplication<Application>().applicationContext)
            }
        }
    }
}