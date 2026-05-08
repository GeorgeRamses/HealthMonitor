package com.healthmonitor.app.util

import com.healthmonitor.app.data.local.entities.MedicationEntity
import com.healthmonitor.app.data.local.entities.MedicationLogEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.floor

// ─────────────────────────────────────────────────────────────────────────────
// Inventory modes — 3 modes covering every real-world prescription scenario
//
//  COURSE  → Fixed duration (e.g. antibiotics for 7 days).
//            Progress bar = days elapsed / total days.
//            Alarm stops automatically after end date.
//
//  STOCK   → Quantity-based (e.g. eye drops, insulin vials, inhalers).
//            Progress bar = quantity consumed / total quantity.
//            Shows "Refill" button when low. Alarm keeps firing after stock
//            runs out (user might forget to refill, not to take it).
//
//  CHRONIC → No end date, no quantity limit (e.g. hypertension, diabetes).
//            No progress bar — replaced by an adherence streak counter.
//            Alarm runs indefinitely until user manually deactivates.
// ─────────────────────────────────────────────────────────────────────────────

object MedicationInventoryMode {
    const val COURSE  = "course"
    const val STOCK   = "stock"
    const val CHRONIC = "chronic"

    fun label(mode: String): String = when (mode) {
        COURSE  -> "كورس محدد"
        STOCK   -> "مخزون"
        CHRONIC -> "مزمن / مستمر"
        else    -> mode
    }

    fun description(mode: String): String = when (mode) {
        COURSE  -> "مدة محددة (أيام) — مثال: مضاد حيوي"
        STOCK   -> "يتناقص بالجرعات — مثال: قطرة عين، إنسولين"
        CHRONIC -> "بلا توقف — مثال: ضغط الدم، السكر"
        else    -> ""
    }
}

data class MedicationInventoryStatus(
    val mode: String = MedicationInventoryMode.COURSE,
    val lastTakenAt: Long?,
    // COURSE / STOCK
    val remainingDays: Int?,
    val remainingDoses: Int?,
    val estimatedCompletionDate: Long?,
    val progress: Float,
    // CHRONIC
    val adherenceStreakDays: Int = 0,
    val totalTakenDoses: Int = 0,
    // STOCK low-stock warning
    val isLowStock: Boolean = false,
    val lowStockThresholdDoses: Int = 7
)

fun calculateMedicationInventoryStatus(
    medication: MedicationEntity,
    allLogsForMedication: List<MedicationLogEntity>,
    scheduledDoseCount: Int,
    todayMillis: Long = startOfDayMillis()
): MedicationInventoryStatus {

    val dailyDoseCount = scheduledDoseCount
        .coerceAtLeast(medication.timesPerDay)
        .coerceAtLeast(1)

    val takenLogs  = allLogsForMedication.filter { it.taken && !it.isDeleted }
    val lastTakenAt = takenLogs.maxOfOrNull { it.time }
    val takenDoses  = takenLogs.size

    return when (medication.inventoryMode) {

        // ── STOCK ─────────────────────────────────────────────────────────────
        MedicationInventoryMode.STOCK -> {
            val currentQuantity = medication.currentQuantity ?: medication.totalQuantity ?: 0.0
            val quantityPerDose = medication.quantityPerDose.takeIf { it > 0.0 } ?: 1.0
            val remainingDoses  = floor(currentQuantity / quantityPerDose).toInt().coerceAtLeast(0)
            val dailyQuantity   = dailyDoseCount * quantityPerDose
            val remainingDays   = if (dailyQuantity > 0.0)
                ceil(currentQuantity / dailyQuantity).toInt() else null
            val endDate         = remainingDays?.let { addDays(todayMillis, (it - 1).coerceAtLeast(0)) }
            val total           = medication.totalQuantity?.takeIf { it > 0.0 }
            val progress        = if (total != null)
                ((total - currentQuantity).toFloat() / total.toFloat()).coerceIn(0f, 1f)
            else 0f
            // Warn when fewer than 7 days of stock remain
            val isLow = remainingDays != null && remainingDays <= 7

            MedicationInventoryStatus(
                mode                    = MedicationInventoryMode.STOCK,
                lastTakenAt             = lastTakenAt,
                remainingDays           = remainingDays,
                remainingDoses          = remainingDoses,
                estimatedCompletionDate = endDate,
                progress                = progress,
                isLowStock              = isLow,
                totalTakenDoses         = takenDoses
            )
        }

        // ── CHRONIC ───────────────────────────────────────────────────────────
        MedicationInventoryMode.CHRONIC -> {
            // Build a set of days (epoch-day numbers) on which the user took ≥1 dose
            val takenDays = takenLogs
                .map { toLocalDate(it.date).toEpochDay() }
                .toSortedSet()

            // Count consecutive days ending today
            val todayEpochDay = toLocalDate(todayMillis).toEpochDay()
            var streak = 0
            var checkDay = todayEpochDay
            while (takenDays.contains(checkDay)) {
                streak++
                checkDay--
            }

            MedicationInventoryStatus(
                mode                    = MedicationInventoryMode.CHRONIC,
                lastTakenAt             = lastTakenAt,
                remainingDays           = null,
                remainingDoses          = null,
                estimatedCompletionDate = null,
                progress                = 0f,   // No progress bar for chronic
                adherenceStreakDays      = streak,
                totalTakenDoses         = takenDoses
            )
        }

        // ── COURSE (default) ──────────────────────────────────────────────────
        else -> {
            val elapsedDays    = daysBetween(medication.startDate, todayMillis).coerceAtLeast(0)
            val remainingDays  = (medication.durationDays - elapsedDays).coerceAtLeast(0)
            val totalDoses     = (medication.durationDays * dailyDoseCount).coerceAtLeast(0)
            val remainingDoses = (totalDoses - takenDoses).coerceAtLeast(0)
            val completionDate = medication.endDate
                ?: addDays(medication.startDate, (medication.durationDays - 1).coerceAtLeast(0))
            val progress = if (medication.durationDays > 0)
                (elapsedDays.toFloat() / medication.durationDays.toFloat()).coerceIn(0f, 1f)
            else 0f

            MedicationInventoryStatus(
                mode                    = MedicationInventoryMode.COURSE,
                lastTakenAt             = lastTakenAt,
                remainingDays           = remainingDays,
                remainingDoses          = remainingDoses,
                estimatedCompletionDate = completionDate,
                progress                = progress,
                totalTakenDoses         = takenDoses
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Scheduling logic — CHRONIC meds are always scheduled while isActive = true
// ─────────────────────────────────────────────────────────────────────────────

fun isMedicationScheduledOnDate(medication: MedicationEntity, dateMillis: Long): Boolean {
    if (medication.isDeleted) return false
    // Chronic meds run indefinitely — only the isActive flag controls them
    if (medication.inventoryMode == MedicationInventoryMode.CHRONIC) return medication.isActive
    val start    = toLocalDate(medication.startDate)
    val selected = toLocalDate(dateMillis)
    val end      = medication.endDate?.let(::toLocalDate)
        ?: start.plusDays((medication.durationDays - 1).coerceAtLeast(0).toLong())
    return !selected.isBefore(start) && !selected.isAfter(end)
}

// ─────────────────────────────────────────────────────────────────────────────
// Date helpers
// ─────────────────────────────────────────────────────────────────────────────

fun startOfDayMillis(date: LocalDate = LocalDate.now(), zoneId: ZoneId = ZoneId.systemDefault()): Long =
    date.atStartOfDay(zoneId).toInstant().toEpochMilli()

fun addDays(epochMillis: Long, days: Int, zoneId: ZoneId = ZoneId.systemDefault()): Long =
    toLocalDate(epochMillis, zoneId).plusDays(days.toLong()).atStartOfDay(zoneId).toInstant().toEpochMilli()

private fun daysBetween(startMillis: Long, endMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Int =
    ChronoUnit.DAYS.between(toLocalDate(startMillis, zoneId), toLocalDate(endMillis, zoneId)).toInt()

private fun toLocalDate(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()