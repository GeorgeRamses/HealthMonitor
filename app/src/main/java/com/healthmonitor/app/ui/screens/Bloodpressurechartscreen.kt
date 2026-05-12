package com.healthmonitor.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthmonitor.app.data.local.entities.BloodPressureEntity
import com.healthmonitor.app.ui.design.*
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────────────────────
// Blood Pressure Chart
//
// Rendered entirely with Compose Canvas — no external chart library needed.
// Shows systolic (red) and diastolic (blue) lines with animated draw-in.
// Called as a tab inside HealthScreen.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BloodPressureChartContent(readings: List<BloodPressureEntity>) {
    // Show up to 14 most recent readings, oldest first so line reads left→right
    val chartReadings = remember(readings) {
        readings.take(14).reversed()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HMSpacing.lg)
    ) {
        Spacer(Modifier.height(HMSpacing.md))

        if (chartReadings.size < 2) {
            HMEmptyState(
                emoji    = "📈",
                title    = "بيانات غير كافية",
                subtitle = "أضف قراءتين على الأقل لعرض الرسم البياني"
            )
            return@Column
        }

        // ── Chart card ────────────────────────────────────────────────────
        HMCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm),
                modifier              = Modifier.padding(bottom = HMSpacing.md)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(HMRadius.xs))
                        .background(HMColor.RedBright.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.ShowChart, null,
                        tint     = HMColor.RedBright,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    "اتجاه ضغط الدم",
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color      = HMColor.TextPrimary
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "آخر ${chartReadings.size} قراءة",
                    fontSize = 11.sp,
                    color    = HMColor.TextSecondary
                )
            }

            // ── Legend ────────────────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(HMSpacing.lg),
                modifier              = Modifier.padding(bottom = HMSpacing.sm)
            ) {
                LegendItem("انقباضي", HMColor.RedBright)
                LegendItem("انبساطي", HMColor.BlueBright)
            }

            // ── Canvas chart ──────────────────────────────────────────────
            val animProgress by animateFloatAsState(
                targetValue  = 1f,
                animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                label        = "chart_anim"
            )

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                drawBpChart(chartReadings, animProgress)
            }

            // ── X-axis labels ─────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val step = if (chartReadings.size <= 7) 1 else 2
                chartReadings.filterIndexed { i, _ -> i % step == 0 }.forEach { r ->
                    Text(
                        formatChartDate(r.date),
                        fontSize = 9.sp,
                        color    = HMColor.TextDisabled
                    )
                }
            }
        }

        Spacer(Modifier.height(HMSpacing.md))

        // ── Stats summary card ────────────────────────────────────────────
        if (chartReadings.isNotEmpty()) {
            BpSummaryCard(chartReadings)
        }

        Spacer(Modifier.height(HMSpacing.lg))

        // ── Reading list ──────────────────────────────────────────────────
        HMSectionHeader("جميع القراءات (${readings.size})", color = HMColor.RedBright)
        Spacer(Modifier.height(HMSpacing.sm))

        readings.forEach { r ->
            BpChartReadingRow(r)
            Spacer(Modifier.height(HMSpacing.xs))
        }

        Spacer(Modifier.height(80.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Canvas drawing
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawBpChart(
    readings: List<BloodPressureEntity>,
    progress: Float
) {
    if (readings.size < 2) return

    val paddingH  = 32.dp.toPx()
    val paddingV  = 16.dp.toPx()
    val chartW    = size.width - paddingH * 2
    val chartH    = size.height - paddingV * 2

    // Value range: systolic drives the ceiling, diastolic drives the floor
    val allSys = readings.map { it.systolic }
    val allDia = readings.map { it.diastolic }
    val minVal  = (allDia.min() - 10).coerceAtLeast(40)
    val maxVal  = (allSys.max() + 10).coerceAtMost(200)
    val range   = (maxVal - minVal).toFloat().coerceAtLeast(1f)

    fun xOf(index: Int) = paddingH + index * (chartW / (readings.size - 1))
    fun yOf(value: Int) = paddingV + chartH * (1f - (value - minVal) / range)

    // ── Grid lines ──────────────────────────────────────────────────────
    val gridValues = listOf(80, 90, 120, 140).filter { it in minVal..maxVal }
    gridValues.forEach { v ->
        val y = yOf(v)
        drawLine(
            color       = Color(0xFF1E2D24),
            start       = Offset(paddingH, y),
            end         = Offset(size.width - paddingH, y),
            strokeWidth = 1.dp.toPx()
        )
    }

    // ── Normal range band (80–120 systolic reference) ────────────────────
    val bandTop = yOf(120).coerceIn(paddingV, paddingV + chartH)
    val bandBot = yOf(80).coerceIn(paddingV, paddingV + chartH)
    drawRect(
        color   = Color(0xFF0E2318),
        topLeft = Offset(paddingH, bandTop),
        size    = androidx.compose.ui.geometry.Size(chartW, bandBot - bandTop)
    )

    // ── Draw a line with progress-aware clipping ──────────────────────────
    fun drawAnimatedLine(
        values: List<Int>,
        color: Color,
        strokeWidth: Float = 2.5.dp.toPx()
    ) {
        val totalPoints = values.size
        val visibleUpTo = (progress * (totalPoints - 1)).toInt().coerceIn(0, totalPoints - 1)
        val fraction    = (progress * (totalPoints - 1)) - visibleUpTo

        val path = Path()
        values.forEachIndexed { i, v ->
            val x = xOf(i)
            val y = yOf(v)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Dots
        for (i in 0..visibleUpTo) {
            val x = if (i == visibleUpTo && i < totalPoints - 1) {
                xOf(i) + fraction * (xOf(i + 1) - xOf(i))
            } else xOf(i)
            val y = if (i == visibleUpTo && i < totalPoints - 1) {
                yOf(values[i]) + fraction * (yOf(values[i + 1]) - yOf(values[i]))
            } else yOf(values[i])
            drawCircle(color = color, radius = 4.dp.toPx(), center = Offset(x, y))
            drawCircle(color = Color.Black, radius = 2.dp.toPx(), center = Offset(x, y))
        }
    }

    drawAnimatedLine(readings.map { it.systolic }, Color(0xFFEF5350)) // systolic red
    drawAnimatedLine(readings.map { it.diastolic }, Color(0xFF42A5F5)) // diastolic blue
}

// ─────────────────────────────────────────────────────────────────────────────
// Summary stats card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BpSummaryCard(readings: List<BloodPressureEntity>) {
    val avgSys = readings.map { it.systolic }.average().toInt()
    val avgDia = readings.map { it.diastolic }.average().toInt()
    val maxSys = readings.maxOf { it.systolic }
    val minSys = readings.minOf { it.systolic }

    val avgStatus = when {
        avgSys < 120 && avgDia < 80 -> "طبيعي"    to HMColor.GreenBright
        avgSys < 140 && avgDia < 90 -> "مرتفع قليلاً" to HMColor.AmberBright
        else                         -> "مرتفع"       to HMColor.RedBright
    }

    HMCard(modifier = Modifier.fillMaxWidth()) {
        HMSectionHeader("ملخص الفترة", color = HMColor.TextSecondary)
        Spacer(Modifier.height(HMSpacing.sm))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
        ) {
            BpStatBox("المتوسط",  "$avgSys/$avgDia", avgStatus.second, Modifier.weight(1f))
            BpStatBox("أعلى قراءة", "$maxSys",     HMColor.RedBright,  Modifier.weight(1f))
            BpStatBox("أقل قراءة", "$minSys",      HMColor.BlueBright, Modifier.weight(1f))
        }
        Spacer(Modifier.height(HMSpacing.sm))
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("الحالة العامة:", fontSize = 12.sp, color = HMColor.TextSecondary)
            HMBadge(
                text            = avgStatus.first,
                color           = avgStatus.second,
                backgroundColor = avgStatus.second.copy(alpha = 0.12f)
            )
        }
    }
}

@Composable
private fun BpStatBox(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier            = modifier
            .clip(RoundedCornerShape(HMRadius.xs))
            .background(color.copy(alpha = 0.07f))
            .padding(HMSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 10.sp, color = HMColor.TextSecondary)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Compact reading row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BpChartReadingRow(r: BloodPressureEntity) {
    val (statusLabel, statusColor) = when {
        r.systolic < 120 && r.diastolic < 80 -> "طبيعي" to HMColor.GreenBright
        r.systolic < 140 && r.diastolic < 90 -> "مرتفع قليلاً" to HMColor.AmberBright
        else                                   -> "مرتفع" to HMColor.RedBright
    }
    HMCard(
        modifier    = Modifier.fillMaxWidth(),
        borderColor = statusColor.copy(alpha = 0.2f)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment     = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "${r.systolic}/${r.diastolic}",
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color      = statusColor
                    )
                    Text(
                        "mmHg",
                        fontSize = 10.sp,
                        color    = HMColor.TextSecondary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                Text(
                    formatChartDateTime(r.time),
                    fontSize = 10.sp,
                    color    = HMColor.TextDisabled
                )
            }
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
            ) {
                r.pulse?.let {
                    Text("💓 $it", fontSize = 11.sp, color = HMColor.TextSecondary)
                }
                HMBadge(
                    text            = statusLabel,
                    color           = statusColor,
                    backgroundColor = statusColor.copy(alpha = 0.1f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Legend item
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 16.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Text(label, fontSize = 11.sp, color = HMColor.TextSecondary)
    }
}

private fun formatChartDate(ts: Long): String =
    SimpleDateFormat("d/M", Locale.getDefault()).format(Date(ts))

private fun formatChartDateTime(ts: Long): String =
    SimpleDateFormat("d MMM  hh:mm a", Locale.getDefault()).format(Date(ts))