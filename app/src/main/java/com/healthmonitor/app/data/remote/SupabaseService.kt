package com.healthmonitor.app.data.remote

import com.healthmonitor.app.data.model.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseService @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    // --- Patients ---
    suspend fun getPatients(since: Long): List<Patient> = 
        supabaseClient.from("patients").select {
            filter {
                Patient::lastModifiedAt gt since
            }
        }.decodeList<Patient>()

    suspend fun upsertPatients(patients: List<Patient>) {
        supabaseClient.from("patients").upsert(patients)
    }

    // --- Medical Cases ---
    suspend fun getMedicalCases(since: Long): List<MedicalCase> =
        supabaseClient.from("medical_cases").select {
            filter {
                MedicalCase::lastModifiedAt gt since
            }
        }.decodeList<MedicalCase>()

    suspend fun upsertMedicalCases(cases: List<MedicalCase>) {
        supabaseClient.from("medical_cases").upsert(cases)
    }

    // --- Medications ---
    suspend fun getMedications(since: Long): List<Medication> =
        supabaseClient.from("medications").select {
            filter {
                Medication::lastModifiedAt gt since
            }
        }.decodeList<Medication>()

    suspend fun upsertMedications(meds: List<Medication>) {
        supabaseClient.from("medications").upsert(meds)
    }

    // --- Medication Schedules ---
    suspend fun getMedicationSchedules(since: Long): List<MedicationSchedule> =
        supabaseClient.from("medication_schedules").select {
            filter {
                MedicationSchedule::lastModifiedAt gt since
            }
        }.decodeList<MedicationSchedule>()

    suspend fun upsertMedicationSchedules(schedules: List<MedicationSchedule>) {
        supabaseClient.from("medication_schedules").upsert(schedules)
    }

    // --- Medication Logs ---
    suspend fun getMedicationLogs(since: Long): List<MedicationLog> =
        supabaseClient.from("medication_logs").select {
            filter {
                MedicationLog::lastModifiedAt gt since
            }
        }.decodeList<MedicationLog>()

    suspend fun upsertMedicationLogs(logs: List<MedicationLog>) {
        supabaseClient.from("medication_logs").upsert(logs)
    }

    // --- Blood Pressure Readings ---
    suspend fun getBloodPressureReadings(since: Long): List<BloodPressureReading> =
        supabaseClient.from("blood_pressure_readings").select {
            filter {
                BloodPressureReading::lastModifiedAt gt since
            }
        }.decodeList<BloodPressureReading>()

    suspend fun upsertBloodPressureReadings(readings: List<BloodPressureReading>) {
        supabaseClient.from("blood_pressure_readings").upsert(readings)
    }

    // --- Symptoms ---
    suspend fun getSymptoms(since: Long): List<Symptom> =
        supabaseClient.from("symptoms").select {
            filter {
                Symptom::lastModifiedAt gt since
            }
        }.decodeList<Symptom>()

    suspend fun upsertSymptoms(symptoms: List<Symptom>) {
        supabaseClient.from("symptoms").upsert(symptoms)
    }
}

