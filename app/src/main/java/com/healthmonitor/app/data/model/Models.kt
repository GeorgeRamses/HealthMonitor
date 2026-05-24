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


// ─────────────────────────────────────────────────────────────────────────────
// DosageFormType — شكل الدواء + الوحدات المناسبة له
//
// كل شكل صيدلاني له:
//   • emoji       → أيقونة بصرية
//   • label       → الاسم بالعربي
//   • units       → قائمة الوحدات الممكنة مرتبة من الأكثر شيوعاً
//   • defaultUnit → الوحدة الافتراضية اللي بتظهر أول ما يختار الشكل
// ─────────────────────────────────────────────────────────────────────────────

data class DosageForm(
    val key: String,
    val emoji: String,
    val label: String,
    val units: List<String>,
    val defaultUnit: String
)

object DosageFormType {

    // ── الأشكال الصيدلانية الكاملة ────────────────────────────────────────────

    val TABLET = DosageForm(
        key         = "tablet",
        emoji       = "🔴",
        label       = "قرص",
        units       = listOf("mg", "mcg", "g", "حبة"),
        defaultUnit = "mg"
    )

    val CAPSULE = DosageForm(
        key         = "capsule",
        emoji       = "💊",
        label       = "كبسولة",
        units       = listOf("mg", "mcg", "g", "حبة"),
        defaultUnit = "mg"
    )

    val INJECTION = DosageForm(
        key         = "injection",
        emoji       = "💉",
        label       = "حقنة",
        units       = listOf("ml", "mg", "mcg", "IU", "وحدة دولية"),
        defaultUnit = "ml"
    )

    val INHALER = DosageForm(
        key         = "inhaler",
        emoji       = "🫁",
        label       = "بخاخ / رذاذ",
        units       = listOf("جرعة", "نفخة", "mcg", "mg"),
        defaultUnit = "جرعة"
    )

    val DROPS = DosageForm(
        key         = "drops",
        emoji       = "💧",
        label       = "قطرة",
        units       = listOf("قطرة", "ml"),
        defaultUnit = "قطرة"
    )

    val SYRUP = DosageForm(
        key         = "syrup",
        emoji       = "🥤",
        label       = "شراب / معلق",
        units       = listOf("ml", "ملعقة صغيرة (5ml)", "ملعقة كبيرة (15ml)"),
        defaultUnit = "ml"
    )

    val OINTMENT = DosageForm(
        key         = "ointment",
        emoji       = "🧴",
        label       = "مرهم / كريم / جيل",
        units       = listOf("سم", "g", "mg"),
        defaultUnit = "سم"
    )

    val PATCH = DosageForm(
        key         = "patch",
        emoji       = "🩹",
        label       = "لصقة جلدية",
        units       = listOf("لصقة", "mg"),
        defaultUnit = "لصقة"
    )

    val SUPPOSITORY = DosageForm(
        key         = "suppository",
        emoji       = "🫙",
        label       = "تحميلة",
        units       = listOf("تحميلة", "mg"),
        defaultUnit = "تحميلة"
    )

    val POWDER = DosageForm(
        key         = "powder",
        emoji       = "🧂",
        label       = "مسحوق / كيس",
        units       = listOf("كيس", "g", "mg"),
        defaultUnit = "كيس"
    )

    val SUBLINGUAL = DosageForm(
        key         = "sublingual",
        emoji       = "👅",
        label       = "تحت اللسان",
        units       = listOf("mg", "mcg", "حبة"),
        defaultUnit = "mg"
    )

    val INSULIN = DosageForm(
        key         = "insulin",
        emoji       = "🩸",
        label       = "إنسولين",
        units       = listOf("وحدة دولية", "IU", "ml"),
        defaultUnit = "وحدة دولية"
    )

    val OTHER = DosageForm(
        key         = "other",
        emoji       = "💬",
        label       = "أخرى",
        units       = listOf("mg", "ml", "mcg", "g", "IU", "حبة", "جرعة", "وحدة"),
        defaultUnit = "mg"
    )

    // ── القائمة الكاملة — الترتيب هو ترتيب الظهور في الـ UI ─────────────────

    val all: List<DosageForm> = listOf(
        TABLET,
        CAPSULE,
        INJECTION,
        INHALER,
        DROPS,
        SYRUP,
        OINTMENT,
        PATCH,
        SUPPOSITORY,
        POWDER,
        SUBLINGUAL,
        INSULIN,
        OTHER
    )

    // ── Helper: ارجع الـ DosageForm من الـ unit المحفوظ ──────────────────────
    // مفيد لما بنفتح الـ dialog على دواء موجود — نحاول نخمن الشكل من الوحدة

    fun guessFromUnit(unit: String): DosageForm {
        val lower = unit.lowercase().trim()
        return when {
            lower == "قطرة" || lower == "قطرات"                 -> DROPS
            lower == "جرعة" || lower == "نفخة"                  -> INHALER
            lower == "لصقة"                                      -> PATCH
            lower == "تحميلة"                                    -> SUPPOSITORY
            lower == "كيس"                                       -> POWDER
            lower == "سم"                                        -> OINTMENT
            lower == "ml" && unit != "ml"                        -> SYRUP
            lower.contains("iu") || lower.contains("وحدة دولية") -> INSULIN
            lower == "ml"                                        -> INJECTION
            lower == "mcg" && unit.length < 5                   -> TABLET
            lower == "mg" || lower == "g" || lower == "حبة"     -> TABLET
            else                                                 -> OTHER
        }
    }

    fun fromKey(key: String?): DosageForm? =
        all.firstOrNull { it.key == key?.trim() }
}
