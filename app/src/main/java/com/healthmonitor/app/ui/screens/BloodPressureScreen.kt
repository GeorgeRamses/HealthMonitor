package com.healthmonitor.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.healthmonitor.app.data.local.entities.BloodPressureEntity
import com.healthmonitor.app.ui.design.*
import com.healthmonitor.app.ui.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BloodPressureScreen(
    navController: NavHostController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    var systolic         by remember { mutableStateOf("") }
    var diastolic        by remember { mutableStateOf("") }
    var pulse            by remember { mutableStateOf("") }
    var oxygenSaturation by remember { mutableStateOf("") }
    var notes            by remember { mutableStateOf("") }
    var showSuccess      by remember { mutableStateOf(false) }

    val currentPatient by viewModel.currentPatient.collectAsState()
    val readings       by viewModel.bloodPressureReadings.collectAsState()

    val sys    = systolic.toIntOrNull()
    val dia    = diastolic.toIntOrNull()
    val canSave = sys != null && dia != null && currentPatient != null

    val bpStatus = if (sys != null && dia != null) {
        when {
            sys < 120 && dia < 80 -> Triple("طبيعي", HMColor.GreenBright, HMColor.GreenBg)
            sys < 140 && dia < 90 -> Triple("مرتفع قليلاً", HMColor.AmberBright, HMColor.AmberBg)
            else -> Triple("مرتفع", HMColor.RedBright, HMColor.RedBg)
        }
    } else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Screen header ────────────────────────────────────────────────
        ScreenHeader(
            title    = "قياس ضغط الدم",
            subtitle = "سجّل القراءة الجديدة",
            onBack   = { navController.popBackStack() }
        )

        Column(modifier = Modifier.padding(horizontal = HMSpacing.lg)) {

            // ── Main input card ──────────────────────────────────────────
            HMCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "القراءة",
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color      = HMColor.TextSecondary,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(HMSpacing.md))

                Row(horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)) {
                    HMTextField(
                        value         = systolic,
                        onValueChange = { systolic = it.filter(Char::isDigit) },
                        label         = "الانقباضي *",
                        trailingText  = "mmHg",
                        keyboardType  = KeyboardType.Number,
                        modifier      = Modifier.weight(1f)
                    )
                    HMTextField(
                        value         = diastolic,
                        onValueChange = { diastolic = it.filter(Char::isDigit) },
                        label         = "الانبساطي *",
                        trailingText  = "mmHg",
                        keyboardType  = KeyboardType.Number,
                        modifier      = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(HMSpacing.sm))

                Row(horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)) {
                    HMTextField(
                        value         = pulse,
                        onValueChange = { pulse = it.filter(Char::isDigit) },
                        label         = "النبض",
                        trailingText  = "bpm",
                        keyboardType  = KeyboardType.Number,
                        leadingIcon   = Icons.Outlined.Favorite,
                        modifier      = Modifier.weight(1f)
                    )
                    HMTextField(
                        value         = oxygenSaturation,
                        onValueChange = { oxygenSaturation = it.filter(Char::isDigit) },
                        label         = "الأكسجين",
                        trailingText  = "%",
                        keyboardType  = KeyboardType.Number,
                        modifier      = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(HMSpacing.sm))

                HMTextField(
                    value         = notes,
                    onValueChange = { notes = it },
                    label         = "ملاحظات (اختياري)",
                    singleLine    = false,
                    minLines      = 2,
                    maxLines      = 3
                )
            }

            // ── BP status badge ──────────────────────────────────────────
            AnimatedVisibility(
                visible = bpStatus != null,
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically()
            ) {
                bpStatus?.let { (label, color, bg) ->
                    Spacer(Modifier.height(HMSpacing.sm))
                    HMCard(
                        modifier        = Modifier.fillMaxWidth(),
                        backgroundColor = bg,
                        borderColor     = color.copy(alpha = 0.3f)
                    ) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("الحالة", fontSize = 11.sp, color = HMColor.TextSecondary)
                                Spacer(Modifier.height(2.dp))
                                Row(
                                    verticalAlignment     = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        "${systolic}/${diastolic}",
                                        fontSize   = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color      = HMColor.TextPrimary
                                    )
                                    Text("mmHg", fontSize = 12.sp, color = HMColor.TextSecondary, modifier = Modifier.padding(bottom = 3.dp))
                                }
                            }
                            HMBadge(
                                text            = label,
                                color           = color,
                                backgroundColor = color.copy(alpha = 0.15f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(HMSpacing.md))

            HMPrimaryButton(
                text       = "حفظ القراءة",
                onClick    = {
                    viewModel.recordBloodPressure(
                        systolic         = sys ?: return@HMPrimaryButton,
                        diastolic        = dia ?: return@HMPrimaryButton,
                        pulse            = pulse.toIntOrNull(),
                        oxygenSaturation = oxygenSaturation.toIntOrNull(),
                        notes            = notes.ifBlank { null }
                    )
                    systolic = ""; diastolic = ""; pulse = ""
                    oxygenSaturation = ""; notes = ""
                    showSuccess = true
                },
                enabled    = canSave,
                leadingIcon = Icons.Filled.Save,
                modifier   = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(HMSpacing.xl))

            // ── History ──────────────────────────────────────────────────
            HMSectionHeader("سجل القراءات")
            Spacer(Modifier.height(HMSpacing.sm))

            if (readings.isEmpty()) {
                HMEmptyState(emoji = "🫀", title = "لا توجد قراءات", subtitle = "أضف أول قراءة أعلاه")
            } else {
                readings.forEach { r ->
                    BPReadingCard(r)
                    Spacer(Modifier.height(HMSpacing.sm))
                }
            }

            Spacer(Modifier.height(HMSpacing.xxxl))
        }
    }

    if (showSuccess) {
        HMSuccessDialog(
            title    = "تم الحفظ",
            message  = "تم حفظ قراءة ضغط الدم بنجاح",
            onDismiss = { showSuccess = false }
        )
    }
}

@Composable
private fun BPReadingCard(r: BloodPressureEntity) {
    val (statusLabel, statusColor) = when {
        r.systolic < 120 && r.diastolic < 80 -> "طبيعي" to HMColor.GreenBright
        r.systolic < 140 && r.diastolic < 90 -> "مرتفع قليلاً" to HMColor.AmberBright
        else -> "مرتفع" to HMColor.RedBright
    }

    HMCard(
        modifier    = Modifier.fillMaxWidth(),
        borderColor = statusColor.copy(alpha = 0.25f)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${r.systolic}/${r.diastolic}",
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color      = statusColor
                    )
                    Text("mmHg", fontSize = 11.sp, color = HMColor.TextSecondary, modifier = Modifier.padding(bottom = 2.dp))
                }
                Text(formatBPTs(r.time), fontSize = 11.sp, color = HMColor.TextDisabled)
            }
            Column(horizontalAlignment = Alignment.End) {
                HMBadge(text = statusLabel, color = statusColor, backgroundColor = statusColor.copy(alpha = 0.1f))
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(HMSpacing.md)) {
                    r.pulse?.let { Text("💓 $it bpm", fontSize = 11.sp, color = HMColor.TextSecondary) }
                    r.oxygenSaturation?.let { Text("🫁 $it%", fontSize = 11.sp, color = HMColor.TextSecondary) }
                }
            }
        }
        if (!r.notes.isNullOrBlank()) {
            Spacer(Modifier.height(HMSpacing.sm))
            HMDivider()
            Spacer(Modifier.height(HMSpacing.sm))
            Text(r.notes, fontSize = 12.sp, color = HMColor.TextSecondary)
        }
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(HMSpacing.lg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = HMColor.TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = HMColor.TextSecondary)
        }
        IconButton(onClick = onBack) {
            Icon(Icons.Default.Close, "رجوع", tint = HMColor.TextSecondary)
        }
    }
}

private fun formatBPTs(ts: Long): String =
    SimpleDateFormat("d MMM yyyy  hh:mm a", Locale.getDefault()).format(Date(ts))