package com.healthmonitor.app.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.content.edit

/**
 * Singleton that tracks the currently-selected patient and exposes it as a
 * [StateFlow] so that Compose UI recomposes whenever the selection changes.
 *
 * SharedPreferences is still used for persistence across process restarts.
 * [init] must be called once from [com.healthmonitor.app.HealthMonitorApplication]
 * before any ViewModel or Composable tries to read the state.
 */
object ActivePatientManager {

    private const val PREFS      = "health_monitor_prefs"
    private const val KEY_PATIENT = "active_patient_id_v2"

    private val _activePatientId = MutableStateFlow<String?>(null)
    val activePatientIdFlow: StateFlow<String?> = _activePatientId.asStateFlow()

    /** Call once from Application.onCreate() to restore persisted value. */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _activePatientId.value = prefs.getString(KEY_PATIENT, null)
    }

    fun getActivePatientId(): String? = _activePatientId.value

    fun setActivePatientId(context: Context, id: String) {
        _activePatientId.value = id
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putString(KEY_PATIENT, id) }
    }

    fun clearActivePatient(context: Context) {
        _activePatientId.value = null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { remove(KEY_PATIENT) }
    }
}

/**
 * Same reactive wrapper for the active case.
 */
object ActiveCaseManager {

    private const val PREFS    = "health_monitor_prefs"
    private const val KEY_CASE = "active_case_id_v2"

    private val _activeCaseId = MutableStateFlow<String?>(null)
    val activeCaseIdFlow: StateFlow<String?> = _activeCaseId.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _activeCaseId.value = prefs.getString(KEY_CASE, null)
    }

    fun getActiveCaseId(): String? = _activeCaseId.value

    fun setActiveCaseId(context: Context, id: String) {
        _activeCaseId.value = id
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putString(KEY_CASE, id) }
    }

    fun clearActiveCase(context: Context) {
        _activeCaseId.value = null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { remove(KEY_CASE) }
    }
}