package com.healthmonitor.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.healthmonitor.app.data.local.entities.MedicationEntity
import com.healthmonitor.app.data.local.entities.MedicationLogEntity
import com.healthmonitor.app.data.model.FrequencyType
import com.healthmonitor.app.ui.design.*
import com.healthmonitor.app.ui.viewmodel.DashboardViewModel
import com.healthmonitor.app.util.addDays
import com.healthmonitor.app.util.format12Hour
import com.healthmonitor.app.util.startOfDayMillis
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

// ─────────────────────────────────────────────────────────────────────────────
// Medication History Screen
//
// Shows a scrollable per-day log of ALL medication doses (taken + missed)
// across the entire case window. Date navigator lets the user step day by day.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MedicationHistoryScreen(
    navController: NavHostController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val allMedications    by viewModel.allMedications.collectAsState()
    val historyLogs       by viewModel.medicationHistoryLogs.collectAsState()
    val scheduleMap       by viewModel.scheduleMap.collectAsState()
    val selectedDate      by viewModel.selectedMedicationDate.collectAsState()

    // ── Stats for selected day ────────────────────────────────────────────
    val logsForDay = remember(historyLogs, selectedDate) {
        historyLogs.filter { it.date == selectedDate }
    }

    // Build rows: one row per (medication × scheduledTime) pair
    data class DoseRow(
        val medication: MedicationEntity,
        val scheduledTime: String,
        val log: MedicationLogEntity?
    )

    val doseRows = remember(allMedications, scheduleMap, logsForDay) {
        allMedications.flatMap { med ->
            val times = scheduleMap[med.id]?.ifEmpty { listOf("") } ?: listOf("")
            times.map { time ->
                DoseRow(
                    medication    = med,
                    scheduledTime = time,
                    log           = logsForDay.firstOrNull {
                        it.medicationId == med.id && it.scheduledTime == time
                    }
                )
            }
        }.sortedWith(compareBy(
            { it.scheduledTime.ifBlank { "99:99" } },
            { it.medication.name }
        ))
    }

    val takenCount  = doseRows.count { it.log?.taken == true }
    val totalCount  = doseRows.size
    val missedCount = doseRows.count { it.log == null || it.log.taken == false }
    val progress    = if (totalCount > 0) takenCount.toFloat() / totalCount else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HMColor.BgBase)
    ) {
        // ── Header ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(HMColor.BgSurface, HMColor.BgBase)
                    )
                )
                .padding(HMSpacing.lg)
        ) {
            Column {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(HMRadius.sm))
                                .background(HMColor.BlueBright.copy(alpha = 0.12f))
                                .border(1.dp, HMColor.BlueBorder, RoundedCornerShape(HMRadius.sm)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.History, null,
                                tint     = HMColor.BlueBright,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                "سجل الجرعات",
                                fontSize   = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color      = HMColor.TextPrimary
                            )
                            Text(
                                "تاريخ تناول الأدوية",
                                fontSize = 11.sp,
                                color    = HMColor.TextSecondary
                            )
                        }
                    }
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, "رجوع",
                            tint = HMColor.TextSecondary
                        )
                    }
                }

                Spacer(Modifier.height(HMSpacing.md))

                // ── Date navigator ────────────────────────────────────────
                HistoryDateNavigator(
                    dateMillis = selectedDate,
                    onPrevious = { viewModel.moveSelectedMedicationDate(-1) },
                    onNext     = { viewModel.moveSelectedMedicationDate(1) }
                )

                Spacer(Modifier.height(HMSpacing.md))

                // ── Day summary strip ─────────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
                ) {
                    DaySummaryChip(
                        label    = "المطلوب",
                        value    = "$totalCount",
                        color    = HMColor.TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    DaySummaryChip(
                        label    = "أُخذت",
                        value    = "$takenCount",
                        color    = HMColor.GreenBright,
                        modifier = Modifier.weight(1f)
                    )
                    DaySummaryChip(
                        label    = "فائتة",
                        value    = "$missedCount",
                        color    = if (missedCount > 0) HMColor.RedBright else HMColor.TextDisabled,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(HMSpacing.sm))

                // ── Progress bar ──────────────────────────────────────────
                HMProgressBar(
                    progress      = progress,
                    progressColor = when {
                        progress >= 1f  -> HMColor.GreenBright
                        progress >= 0.5f -> HMColor.AmberBright
                        else            -> HMColor.RedBright
                    }
                )
            }
        }

        // ── Dose list ─────────────────────────────────────────────────────
        if (doseRows.isEmpty()) {
            Spacer(Modifier.height(HMSpacing.xxxl))
            HMEmptyState(
                emoji    = "💊",
                title    = "لا توجد أدوية",
                subtitle = "لا توجد أدوية مسجلة في هذه الحالة"
            )
        } else {
            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(HMSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(HMSpacing.sm)
            ) {
                items(doseRows, key = { "${it.medication.id}_${it.scheduledTime}" }) { row ->
                    DoseHistoryCard(row.medication, row.scheduledTime, row.log)
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dose history card — one card per medication × time slot
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DoseHistoryCard(
    medication: MedicationEntity,
    scheduledTime: String,
    log: MedicationLogEntity?
) {
    val taken = log?.taken == true

    val (statusIcon, statusLabel, statusColor, bgColor, borderColor) = when {
        taken -> DoseStatus(
            icon        = Icons.Default.CheckCircle,
            label       = "أُخذت",
            color       = HMColor.GreenBright,
            bg          = HMColor.GreenBg,
            border      = HMColor.GreenBorder
        )
        log != null && !taken -> DoseStatus(
            icon        = Icons.Outlined.Cancel,
            label       = "فائتة",
            color       = HMColor.RedBright,
            bg          = HMColor.RedBg,
            border      = HMColor.RedBorder
        )
        else -> DoseStatus(
            icon        = Icons.Outlined.Schedule,
            label       = "لم تُسجَّل",
            color       = HMColor.TextDisabled,
            bg          = HMColor.BgSurface,
            border      = HMColor.BorderSubtle
        )
    }

    HMCard(
        modifier        = Modifier.fillMaxWidth(),
        backgroundColor = bgColor,
        borderColor     = borderColor
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // ── Medication info ───────────────────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
                ) {
                    Text(
                        medication.name,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = HMColor.TextPrimary
                    )
                    HMBadge(
                        text            = "${medication.dosage} ${medication.unit}",
                        color           = HMColor.GreenBright,
                        backgroundColor = HMColor.GreenBright.copy(alpha = 0.1f)
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Outlined.Schedule, null,
                        tint     = HMColor.TextDisabled,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        if (scheduledTime.isBlank()) "أي وقت" else format12Hour(scheduledTime),
                        fontSize = 11.sp,
                        color    = HMColor.TextDisabled
                    )
                    if (FrequencyType.label(medication.frequency).isNotBlank()) {
                        Text("·", fontSize = 11.sp, color = HMColor.TextDisabled)
                        Text(
                            FrequencyType.label(medication.frequency),
                            fontSize = 11.sp,
                            color    = HMColor.TextDisabled
                        )
                    }
                }
                // Show actual taken time if available
                if (taken && log?.time != null && log.time > 0L) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "أُخذت الساعة ${formatLogTime(log.time)}",
                        fontSize = 10.sp,
                        color    = HMColor.GreenBright.copy(alpha = 0.8f)
                    )
                }
            }

            // ── Status badge ──────────────────────────────────────────────
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    statusIcon, null,
                    tint     = statusColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    statusLabel,
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = statusColor
                )
            }
        }

        // Notes
        if (!log?.notes.isNullOrBlank()) {
            Spacer(Modifier.height(HMSpacing.sm))
            HMDivider()
            Spacer(Modifier.height(HMSpacing.sm))
            Text(log!!.notes!!, fontSize = 11.sp, color = HMColor.TextSecondary)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Date navigator
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HistoryDateNavigator(
    dateMillis: Long,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val isToday = dateMillis == startOfDayMillis(LocalDate.now(), ZoneId.systemDefault())

    HMCard(
        modifier        = Modifier.fillMaxWidth(),
        backgroundColor = HMColor.BgSurface,
        borderColor     = HMColor.BorderSubtle
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, "اليوم السابق",
                    tint = HMColor.TextSecondary
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (isToday) "اليوم" else formatNavDate(dateMillis),
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (isToday) HMColor.GreenBright else HMColor.TextPrimary
                )
                Text(
                    formatNavDate(dateMillis),
                    fontSize = 10.sp,
                    color    = HMColor.TextDisabled
                )
            }
            IconButton(
                onClick  = onNext,
                enabled  = !isToday,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward, "اليوم التالي",
                    tint = if (isToday) HMColor.TextDisabled else HMColor.TextSecondary
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Day summary chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DaySummaryChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier            = modifier
            .clip(RoundedCornerShape(HMRadius.sm))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(HMRadius.sm))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 10.sp, color = HMColor.TextSecondary)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private data class DoseStatus(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val color: Color,
    val bg: Color,
    val border: Color
)

// Destructuring operator
private operator fun DoseStatus.component1() = icon
private operator fun DoseStatus.component2() = label
private operator fun DoseStatus.component3() = color
private operator fun DoseStatus.component4() = bg
private operator fun DoseStatus.component5() = border

private fun formatNavDate(ts: Long): String =
    SimpleDateFormat("EEEE، d MMM yyyy", Locale.getDefault()).format(Date(ts))

private fun formatLogTime(ts: Long): String =
    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(ts))