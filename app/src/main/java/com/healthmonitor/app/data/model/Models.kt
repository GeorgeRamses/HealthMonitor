package com.healthmonitor.app.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// Every domain object uses a String UUID primary key so identities are stable
// across devices before they ever reach Supabase.
//
// lastModifiedAt (epoch-millis) drives "Win-Latest" conflict resolution:
//   if remote.lastModifiedAt > local.lastModifiedAt → apply remote version
//   else                                            → push local version
//
// isDeleted implements soft-delete: a deleted record is never removed from
// the DB; it is propagated to peers with isDeleted=true and filtered out
// of all UI queries.
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class Patient(
    val id: String                  = UUID.randomUUID().toString(),
    val name: String,
    val age: Int,
    val gender: String,
    val bloodType: String?          = null,
    val medicalConditions: String   = "",
    val emergencyContact: String?   = null,
    val emergencyPhone: String?     = null,
    val createdAt: Long             = System.currentTimeMillis(),
    val lastModifiedAt: Long        = System.currentTimeMillis(),
    val isDeleted: Boolean          = false
)

@Serializable
data class MedicalCase(
    val id: String                  = UUID.randomUUID().toString(),
    val patientId: String,
    val title: String,
    val doctorName: String?         = null,
    val notes: String?              = null,
    /** "OPEN" | "CLOSED" */
    val status: String              = CaseStatus.OPEN,
    val createdAt: Long             = System.currentTimeMillis(),
    val lastModifiedAt: Long        = System.currentTimeMillis(),
    val isDeleted: Boolean          = false
)

object CaseStatus {
    const val OPEN   = "OPEN"
    const val CLOSED = "CLOSED"
}

@Serializable
data class Medication(
    val id: String                  = UUID.randomUUID().toString(),
    val patientId: String,
    val caseId: String?             = null,
    val name: String,
    val dosage: String,
    val unit: String                = "mg",
    val frequency: String           = FrequencyType.ONCE_DAILY,
    val notes: String?              = null,
    val durationDays: Int           = 7,  // Duration in days
    val isActive: Boolean           = true,
    val startDate: Long             = System.currentTimeMillis(),
    val endDate: Long?              = null,
    val createdAt: Long             = System.currentTimeMillis(),
    val lastModifiedAt: Long        = System.currentTimeMillis(),
    val isDeleted: Boolean          = false
)

object FrequencyType {
    const val ONCE_DAILY        = "once_daily"
    const val TWICE_DAILY       = "twice_daily"
    const val THREE_TIMES_DAILY = "three_times_daily"
    const val FOUR_TIMES_DAILY  = "four_times_daily"
    const val EVERY_8_HOURS     = "every_8_hours"
    const val EVERY_12_HOURS    = "every_12_hours"
    const val AS_NEEDED         = "as_needed"
    const val WEEKLY            = "weekly"
    const val BIWEEKLY          = "biweekly"

    /** Human-readable Arabic label for each frequency. */
    fun label(key: String): String = when (key) {
        ONCE_DAILY        -> "مرة يومياً"
        TWICE_DAILY       -> "مرتين يومياً"
        THREE_TIMES_DAILY -> "ثلاث مرات يومياً"
        FOUR_TIMES_DAILY  -> "أربع مرات يومياً"
        EVERY_8_HOURS     -> "كل 8 ساعات"
        EVERY_12_HOURS    -> "كل 12 ساعة"
        AS_NEEDED         -> "عند الحاجة"
        WEEKLY            -> "أسبوعياً"
        BIWEEKLY          -> "كل أسبوعين"
        else              -> key
    }

    val all = listOf(
        ONCE_DAILY, TWICE_DAILY, THREE_TIMES_DAILY, FOUR_TIMES_DAILY,
        EVERY_8_HOURS, EVERY_12_HOURS, AS_NEEDED, WEEKLY, BIWEEKLY
    )
}

/**
 * Each row = one scheduled dose time for a medication.
 * Splitting schedules into their own table allows per-dose alarm tracking
 * and clean many-medication:many-times queries.
 */
@Serializable
data class MedicationSchedule(
    val id: String                  = UUID.randomUUID().toString(),
    val medicationId: String,
    /** "HH:mm" 24-hour for storage; UI always renders in 12-hour AM/PM */
    val scheduledTime: String,
    val createdAt: Long             = System.currentTimeMillis(),
    val lastModifiedAt: Long        = System.currentTimeMillis(),
    val isDeleted: Boolean          = false
)

@Serializable
data class MedicationLog(
    val id: String                  = UUID.randomUUID().toString(),
    val medicationId: String,
    val scheduleId: String,
    val patientId: String,
    /** Start-of-day epoch millis (timezone-local). */
    val date: Long,
    val scheduledTime: String,
    val takenAt: Long?              = null,
    val taken: Boolean              = false,
    val notes: String?              = null,
    val createdAt: Long             = System.currentTimeMillis(),
    val lastModifiedAt: Long        = System.currentTimeMillis(),
    val isDeleted: Boolean          = false
)

@Serializable
data class BloodPressureReading(
    val id: String                  = UUID.randomUUID().toString(),
    val patientId: String,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?                 = null,
    val oxygenSaturation: Int?      = null,
    val recordedAt: Long            = System.currentTimeMillis(),
    val notes: String?              = null,
    val createdAt: Long             = System.currentTimeMillis(),
    val lastModifiedAt: Long        = System.currentTimeMillis(),
    val isDeleted: Boolean          = false
)

@Serializable
data class Symptom(
    val id: String                  = UUID.randomUUID().toString(),
    val patientId: String,
    val symptomType: String,
    val severity: String,
    val recordedAt: Long            = System.currentTimeMillis(),
    val inhalerUsed: Boolean        = false,
    val improvementAfterInhaler: Boolean? = null,
    val notes: String?              = null,
    val createdAt: Long             = System.currentTimeMillis(),
    val lastModifiedAt: Long        = System.currentTimeMillis(),
    val isDeleted: Boolean          = false
)

// ── Lightweight projection used for dashboard / list views ───────────────────

data class MedicationWithSchedules(
    val medication: Medication,
    val schedules: List<MedicationSchedule>
)

data class CaseWithMedications(
    val case: MedicalCase,
    val medications: List<MedicationWithSchedules>
)

// SyncState lives in com.healthmonitor.app.shared.sync.SyncState