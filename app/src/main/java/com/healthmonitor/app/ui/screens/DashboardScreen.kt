package com.healthmonitor.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.healthmonitor.app.data.local.entities.SymptomEntity
import com.healthmonitor.app.ui.design.*
import com.healthmonitor.app.ui.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

@Composable
fun DashboardScreen(
    navController: NavHostController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val currentPatient      by viewModel.currentPatient.collectAsState()
    val todayMedications    by viewModel.todayMedications.collectAsState()
    val medicationLogs      by viewModel.medicationLogs.collectAsState()
    val medicationAdherence by viewModel.medicationAdherence.collectAsState()
    val todayDoseCount      by viewModel.todayDoseCount.collectAsState()
    val lastBloodPressure   by viewModel.lastBloodPressure.collectAsState()
    val lastBodyTemperature by viewModel.lastBodyTemperature.collectAsState()
    val recentSymptoms      by viewModel.recentSymptoms.collectAsState()

    val takenCount     = medicationLogs.count { it.taken }
    val remainingCount = (todayDoseCount - takenCount).coerceAtLeast(0)
    val patientName    = currentPatient?.name?.takeIf { it.isNotBlank() } ?: "—"
    val todayStr       = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE، d MMMM yyyy"))

    val bpSys = lastBloodPressure?.systolic?.toString() ?: "--"
    val bpDia = lastBloodPressure?.diastolic?.toString() ?: "--"
    val pulse = lastBloodPressure?.pulse?.toString() ?: "--"
    val spo2  = lastBloodPressure?.oxygenSaturation?.toString() ?: "--"
    val bpStatus = lastBloodPressure?.let {
        when {
            it.systolic < 120 && it.diastolic < 80 -> "طبيعي" to HMColor.GreenBright
            it.systolic < 140 && it.diastolic < 90 -> "مرتفع قليلاً" to HMColor.AmberBright
            else -> "مرتفع" to HMColor.RedBright
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HMColor.BgBase)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header ────────────────────────────────────────────────────────
        DashboardHeader(
            patientName = patientName,
            dateStr     = todayStr,
            adherence   = medicationAdherence
        )

        Column(modifier = Modifier.padding(horizontal = HMSpacing.lg)) {

            // ── Quick stat strip ──────────────────────────────────────────
            StatStrip(
                takenCount     = takenCount,
                totalDoses     = todayDoseCount,
                remainingCount = remainingCount,
                symptomsCount  = recentSymptoms.size
            )

            Spacer(Modifier.height(HMSpacing.lg))

            // ── Blood pressure card ───────────────────────────────────────
            BPCard(
                systolic   = bpSys,
                diastolic  = bpDia,
                pulse      = pulse,
                spo2       = spo2,
                status     = bpStatus,
                lastUpdated = lastBloodPressure?.time?.let { formatTs(it) } ?: "لا توجد قراءة",
                onNavigate = { navController.navigate("health") }
            )

            Spacer(Modifier.height(HMSpacing.md))

            // ── Body temperature card ─────────────────────────────────────
            TemperatureCard(
                lastReading = lastBodyTemperature,
                onNavigate  = { navController.navigate("health") }
            )

            Spacer(Modifier.height(HMSpacing.md))

            // ── Medication progress card ──────────────────────────────────
            MedProgressCard(
                taken      = takenCount,
                total      = todayDoseCount,
                remaining  = remainingCount,
                medicCount = todayMedications.size,
                onNavigate = { navController.navigate("medications") }
            )

            Spacer(Modifier.height(HMSpacing.md))

            // ── Recent symptoms ───────────────────────────────────────────
            LatestSymptomCard(
                symptoms   = recentSymptoms,
                onNavigate = { navController.navigate("health") }
            )

            Spacer(Modifier.height(HMSpacing.xl))

            // ── Quick actions ─────────────────────────────────────────────
            HMSectionHeader("إجراءات سريعة")
            Spacer(Modifier.height(HMSpacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
            ) {
                QuickAction(
                    icon     = Icons.Outlined.Favorite,
                    label    = "قياس الضغط",
                    color    = HMColor.GreenBright,
                    modifier = Modifier.weight(1f),
                    onClick  = { navController.navigate("health") }
                )
                QuickAction(
                    icon    = Icons.Outlined.LocalPharmacy,
                    label   = "متابعة الأدوية",
                    color   = HMColor.BlueBright,
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate("medications") }
                )
                QuickAction(
                    icon    = Icons.Outlined.HealthAndSafety,
                    label   = "تسجيل عرض",
                    color   = HMColor.AmberBright,
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate("health") }
                )
            }

            Spacer(Modifier.height(HMSpacing.xxxl))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DashboardHeader(
    patientName: String,
    dateStr: String,
    adherence: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(HMColor.BgSurface, HMColor.BgBase)
                )
            )
            .padding(
                start  = HMSpacing.lg,
                end    = HMSpacing.lg,
                top    = HMSpacing.xl,
                bottom = HMSpacing.lg
            )
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text       = patientName,
                    fontSize   = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color      = HMColor.TextPrimary,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text     = dateStr,
                    fontSize = 11.sp,
                    color    = HMColor.TextSecondary
                )
            }
            // Adherence ring
            AdherenceRing(adherence)
        }
    }
}

@Composable
private fun TemperatureCard(
    lastReading: com.healthmonitor.app.data.local.entities.BodyTemperatureEntity?,
    onNavigate: () -> Unit
) {
    val temp        = lastReading?.temperature
    val displayTemp = temp?.let { "%.1f °C".format(it) } ?: "--"
    val (statusLabel, statusColor) = when {
        temp == null         -> "لا توجد قراءة" to HMColor.TextDisabled
        temp < 36.0f         -> "أقل من الطبيعي" to HMColor.BlueBright
        temp <= 37.5f        -> "طبيعي"          to HMColor.GreenBright
        temp <= 38.5f        -> "حمى خفيفة"      to HMColor.AmberBright
        else                 -> "حمى"             to HMColor.RedBright
    }

    HMCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Thermostat, null,
                    tint     = HMColor.CyanBright,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "درجة الحرارة",
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = HMColor.TextSecondary,
                    letterSpacing = 0.8.sp
                )
            }
            if (temp != null) {
                HMBadge(
                    text            = statusLabel,
                    color           = statusColor,
                    backgroundColor = statusColor.copy(alpha = 0.12f)
                )
            }
        }

        Spacer(Modifier.height(HMSpacing.sm))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.Bottom
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    displayTemp,
                    fontSize   = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color      = statusColor,
                    lineHeight = 32.sp
                )
            }
            HMPressable(onClick = onNavigate) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(HMRadius.sm))
                        .background(HMColor.CyanBright)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("تسجيل", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = HMColor.TextInverse)
                }
            }
        }

        lastReading?.let {
            Spacer(Modifier.height(HMSpacing.sm))
            Text(
                "آخر تحديث: ${formatTs(it.time)}",
                fontSize = 10.sp,
                color    = HMColor.TextDisabled
            )
        }
    }
}

@Composable
private fun AdherenceRing(adherence: Int) {
    val ringColor = when {
        adherence >= 80 -> HMColor.GreenBright
        adherence >= 50 -> HMColor.AmberBright
        else            -> HMColor.RedBright
    }
    Box(
        modifier          = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(ringColor.copy(alpha = 0.1f))
            .border(2.dp, ringColor.copy(alpha = 0.4f), CircleShape),
        contentAlignment  = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$adherence",
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                color      = ringColor,
                lineHeight = 18.sp
            )
            Text(
                "%",
                fontSize = 9.sp,
                color    = ringColor.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun StatStrip(
    takenCount: Int,
    totalDoses: Int,
    remainingCount: Int,
    symptomsCount: Int
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
    ) {
        StatChip(
            label    = "أُخذت",
            value    = "$takenCount/$totalDoses",
            color    = HMColor.GreenBright,
            modifier = Modifier.weight(1f)
        )
        StatChip(
            label    = "متبقي",
            value    = "$remainingCount",
            color    = HMColor.AmberBright,
            modifier = Modifier.weight(1f)
        )
        StatChip(
            label    = "أعراض",
            value    = "$symptomsCount",
            color    = HMColor.BlueBright,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(HMRadius.sm))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(HMRadius.sm))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value,
            fontSize   = 18.sp,
            fontWeight = FontWeight.Bold,
            color      = color
        )
        Text(
            label,
            fontSize = 10.sp,
            color    = HMColor.TextSecondary,
            letterSpacing = 0.3.sp
        )
    }
}

@Composable
private fun BPCard(
    systolic: String,
    diastolic: String,
    pulse: String,
    spo2: String,
    status: Pair<String, Color>?,
    lastUpdated: String,
    onNavigate: () -> Unit
) {
    HMCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Favorite,
                    null,
                    tint     = HMColor.RedBright,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "ضغط الدم",
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = HMColor.TextSecondary,
                    letterSpacing = 0.8.sp
                )
            }
            status?.let { (label, color) ->
                HMBadge(
                    text            = label,
                    color           = color,
                    backgroundColor = color.copy(alpha = 0.12f)
                )
            }
        }

        Spacer(Modifier.height(HMSpacing.md))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.Bottom
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$systolic/$diastolic",
                    fontSize   = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color      = HMColor.TextPrimary,
                    lineHeight = 38.sp
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "mmHg",
                    fontSize     = 13.sp,
                    color        = HMColor.TextSecondary,
                    modifier     = Modifier.padding(bottom = 4.dp)
                )
            }
            HMPressable(onClick = onNavigate) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(HMRadius.sm))
                        .background(HMColor.GreenBright)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("تسجيل", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = HMColor.TextInverse)
                }
            }
        }

        Spacer(Modifier.height(HMSpacing.md))
        HMDivider()
        Spacer(Modifier.height(HMSpacing.md))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
        ) {
            BPMetric("النبض", pulse, "bpm", HMColor.BlueBright, Modifier.weight(1f))
            BPMetric("الأكسجين", spo2, "%", HMColor.CyanBright, Modifier.weight(1f))
        }

        Spacer(Modifier.height(HMSpacing.sm))
        Text(
            "آخر تحديث: $lastUpdated",
            fontSize = 10.sp,
            color    = HMColor.TextDisabled
        )
    }
}

@Composable
private fun BPMetric(
    label: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier          = modifier
            .clip(RoundedCornerShape(HMRadius.xs))
            .background(color.copy(alpha = 0.07f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column {
            Text(label, fontSize = 10.sp, color = HMColor.TextSecondary)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
                Spacer(Modifier.width(3.dp))
                Text(unit, fontSize = 10.sp, color = HMColor.TextSecondary, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
    }
}

@Composable
private fun MedProgressCard(
    taken: Int,
    total: Int,
    remaining: Int,
    medicCount: Int,
    onNavigate: () -> Unit
) {
    HMCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                "أدوية اليوم",
                fontSize   = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color      = HMColor.TextSecondary,
                letterSpacing = 0.8.sp
            )
            Text(
                "$taken من $total",
                fontSize   = 13.sp,
                fontWeight = FontWeight.Bold,
                color      = if (taken == total && total > 0) HMColor.GreenBright else HMColor.AmberBright
            )
        }

        Spacer(Modifier.height(HMSpacing.md))

        HMProgressBar(
            progress      = if (total > 0) taken.toFloat() / total else 0f,
            height        = 8.dp,
            progressColor = if (taken == total && total > 0) HMColor.GreenBright else HMColor.AmberBright
        )

        Spacer(Modifier.height(HMSpacing.md))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(HMSpacing.md)
        ) {
            MedStat("مأخوذة", "$taken", HMColor.GreenBright, Modifier.weight(1f))
            MedStat("متبقية", "$remaining", HMColor.AmberBright, Modifier.weight(1f))
            MedStat("أنواع", "$medicCount", HMColor.BlueBright, Modifier.weight(1f))
        }

        Spacer(Modifier.height(HMSpacing.md))
        HMDivider()
        Spacer(Modifier.height(HMSpacing.md))

        HMPressable(onClick = onNavigate, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("فتح قائمة الأدوية", fontSize = 13.sp, color = HMColor.BlueBright, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = HMColor.BlueBright, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun MedStat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 10.sp, color = HMColor.TextSecondary)
    }
}

@Composable
private fun LatestSymptomCard(
    symptoms: List<SymptomEntity>,
    onNavigate: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val latest   = symptoms.firstOrNull()

    HMCard(modifier = Modifier.fillMaxWidth()) {
        // ── Header row (always visible, tappable to expand) ───────────────
        HMPressable(
            onClick  = { if (symptoms.size > 1) expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.HealthAndSafety, null,
                        tint     = HMColor.AmberBright,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "آخر الأعراض",
                        fontSize      = 12.sp,
                        fontWeight    = FontWeight.SemiBold,
                        color         = HMColor.TextSecondary,
                        letterSpacing = 0.8.sp
                    )
                    if (symptoms.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        HMBadge(
                            text            = "${symptoms.size}",
                            color           = HMColor.AmberBright,
                            backgroundColor = HMColor.AmberBright.copy(alpha = 0.12f)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HMPressable(onClick = onNavigate) {
                        Text("عرض الكل", fontSize = 11.sp, color = HMColor.AmberBright)
                    }
                    if (symptoms.size > 1) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expanded) "طيّ" else "توسيع",
                            tint     = HMColor.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // ── Collapsed: show only the most recent symptom ──────────────────
        if (symptoms.isEmpty()) {
            Spacer(Modifier.height(HMSpacing.md))
            Text("لا توجد أعراض مسجلة.", fontSize = 13.sp, color = HMColor.TextSecondary)
        } else {
            latest?.let { symptom ->
                val severityColor = severityColor(symptom.severity)
                Spacer(Modifier.height(HMSpacing.sm))
                HMDivider()
                Spacer(Modifier.height(HMSpacing.sm))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            symptom.symptomType,
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = HMColor.TextPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(formatTs(symptom.time), fontSize = 11.sp, color = HMColor.TextDisabled)
                    }
                    HMBadge(
                        text            = symptom.severity,
                        color           = severityColor,
                        backgroundColor = severityColor.copy(alpha = 0.12f)
                    )
                }
            }
        }

        // ── Expanded: show remaining symptoms ─────────────────────────────
        AnimatedVisibility(
            visible = expanded,
            enter   = fadeIn() + expandVertically(),
            exit    = fadeOut() + shrinkVertically()
        ) {
            Column {
                symptoms.drop(1).forEach { symptom ->
                    val severityColor = severityColor(symptom.severity)
                    Spacer(Modifier.height(HMSpacing.sm))
                    HMDivider()
                    Spacer(Modifier.height(HMSpacing.sm))
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                symptom.symptomType,
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color      = HMColor.TextPrimary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(formatTs(symptom.time), fontSize = 11.sp, color = HMColor.TextDisabled)
                        }
                        HMBadge(
                            text            = symptom.severity,
                            color           = severityColor,
                            backgroundColor = severityColor.copy(alpha = 0.12f)
                        )
                    }
                }
            }
        }
    }
}

private fun severityColor(severity: String): Color = when (severity) {
    "خفيف"  -> HMColor.GreenBright
    "متوسط" -> HMColor.AmberBright
    else    -> HMColor.RedBright
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    HMPressable(onClick = onClick, modifier = modifier) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(HMRadius.sm))
                .background(color.copy(alpha = 0.08f))
                .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(HMRadius.sm))
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            Text(
                label,
                fontSize  = 10.sp,
                color     = HMColor.TextSecondary,
                maxLines  = 2,
                lineHeight = 13.sp,
                letterSpacing = 0.2.sp
            )
        }
    }
}

private fun formatTs(ts: Long): String =
    SimpleDateFormat("d MMM  hh:mm a", Locale.getDefault()).format(Date(ts))