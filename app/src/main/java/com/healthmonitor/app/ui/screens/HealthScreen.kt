package com.healthmonitor.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.healthmonitor.app.data.local.entities.BloodPressureEntity
import com.healthmonitor.app.data.local.entities.SymptomEntity
import com.healthmonitor.app.ui.design.*
import com.healthmonitor.app.ui.viewmodel.DashboardViewModel
import com.healthmonitor.app.ui.viewmodel.LabReportViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HealthScreen(
    navController: NavHostController,
    viewModel: DashboardViewModel = hiltViewModel(),
    labReportViewModel: LabReportViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val selectedDate by viewModel.selectedHealthDate.collectAsState()
    var showAddBpDialog       by remember { mutableStateOf(false) }
    var showAddSymptomDialog   by remember { mutableStateOf(false) }
    var showAddTempDialog      by remember { mutableStateOf(false) }
    var showVitalsPicker       by remember { mutableStateOf(false) }   // picker for tab 0

    // Tab 0 = Vital Signs (BP + Temp), 1 = Symptoms, 2 = Lab Reports, 3 = Chart
    val fabColor = when (selectedTab) {
        0 -> HMColor.GreenBright
        1 -> HMColor.AmberBright
        else -> HMColor.BlueBright
    }
    val fabLabel = when (selectedTab) {
        0 -> "تسجيل قياس"
        1 -> "تسجيل عرض"
        else -> "رفع تقرير"
    }

    Scaffold(
        containerColor = HMColor.BgBase,
        floatingActionButton = {
            // No FAB on lab reports (tab 2) or chart (tab 3)
            if (selectedTab == 2 || selectedTab == 3) {
                return@Scaffold
            }
            FloatingActionButton(
                onClick = {
                    when (selectedTab) {
                        0 -> showVitalsPicker = true   // show BP vs Temp picker
                        1 -> showAddSymptomDialog = true
                    }
                },
                containerColor = fabColor,
                contentColor = HMColor.TextInverse,
                shape = RoundedCornerShape(HMRadius.sm)
            ) {
                Icon(Icons.Default.Add, fabLabel)
            }
        }
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(HMColor.BgBase)
                .padding(bottom = scaffoldPadding.calculateBottomPadding())
        ) {
            // ── Gradient header ───────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(HMColor.BgSurface, HMColor.BgBase)))
                    .padding(horizontal = HMSpacing.lg, vertical = HMSpacing.lg)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(HMRadius.sm))
                                .background(HMColor.GreenBright.copy(alpha = 0.12f))
                                .border(1.dp, HMColor.GreenBorder, RoundedCornerShape(HMRadius.sm)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.HealthAndSafety, null,
                                tint = HMColor.GreenBright,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                "الصحة",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = HMColor.TextPrimary
                            )
                            Text(
                                "العلامات الحيوية والأعراض",
                                fontSize = 11.sp,
                                color = HMColor.TextSecondary
                            )
                        }
                    }
                    // Date navigator is hidden on chart tab (tab 3)
                    if (selectedTab != 3) {
                        Spacer(Modifier.height(HMSpacing.md))
                        HealthDateNavigator(
                            dateMillis = selectedDate,
                            onPrevious = { viewModel.moveSelectedHealthDate(-1) },
                            onNext = { viewModel.moveSelectedHealthDate(1) }
                        )
                    }
                    Spacer(Modifier.height(HMSpacing.md))
                    HealthTabSelector(selectedTab = selectedTab, onSelect = { selectedTab = it })
                }
            }

            // ── Tab content ───────────────────────────────────────────────
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "health_tab",
                modifier = Modifier.fillMaxSize()
            ) { tab ->
                when (tab) {
                    0 -> VitalSignsContent(viewModel = viewModel, selectedDate = selectedDate)
                    1 -> SymptomsContent(viewModel = viewModel, selectedDate = selectedDate)
                    2 -> LabReportScreen(viewModel = labReportViewModel)
                    else -> {
                        val readings by viewModel.bloodPressureReadings.collectAsState()
                        BloodPressureChartContent(readings = readings)
                    }
                }
            }
        }
    }

    // ── Vitals type picker ────────────────────────────────────────────────
    if (showVitalsPicker) {
        VitalsPickerDialog(
            onBp   = { showVitalsPicker = false; showAddBpDialog = true },
            onTemp = { showVitalsPicker = false; showAddTempDialog = true },
            onDismiss = { showVitalsPicker = false }
        )
    }

    // ── Dialogs ───────────────────────────────────────────────────────────
    if (showAddBpDialog) {
        AddBloodPressureDialog(
            viewModel = viewModel,
            onDismiss = { showAddBpDialog = false }
        )
    }

    if (showAddSymptomDialog) {
        AddSymptomDialog(
            viewModel = viewModel,
            onDismiss = { showAddSymptomDialog = false }
        )
    }

    if (showAddTempDialog) {
        AddBodyTemperatureDialog(
            viewModel = viewModel,
            onDismiss = { showAddTempDialog = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab selector
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HealthTabSelector(selectedTab: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf(
        "العلامات الحيوية" to Icons.Outlined.MonitorHeart,
        "الأعراض"          to Icons.Outlined.HealthAndSafety,
        "تقارير"           to Icons.Outlined.Science,
        "رسم بياني"        to Icons.AutoMirrored.Outlined.ShowChart
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HMRadius.sm))
            .background(HMColor.BgOverlay)
            .border(1.dp, HMColor.BorderSubtle, RoundedCornerShape(HMRadius.sm))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        tabs.forEachIndexed { index, (label, icon) ->
            val isSelected = selectedTab == index
            val accentColor = when (index) {
                0    -> HMColor.GreenBright
                1    -> HMColor.AmberBright
                2    -> HMColor.BlueBright
                else -> HMColor.CyanBright
            }
            HMPressable(onClick = { onSelect(index) }, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(HMRadius.xs))
                        .background(if (isSelected) accentColor.copy(alpha = 0.14f) else Color.Transparent)
                        .then(
                            if (isSelected) Modifier.border(
                                1.dp,
                                accentColor.copy(alpha = 0.35f),
                                RoundedCornerShape(HMRadius.xs)
                            ) else Modifier
                        )
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            icon, null,
                            tint     = if (isSelected) accentColor else HMColor.TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            label,
                            fontSize   = 9.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color      = if (isSelected) accentColor else HMColor.TextSecondary
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Vital Signs — merged BP + Temperature tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VitalSignsContent(viewModel: DashboardViewModel, selectedDate: Long) {
    val allBpReadings   by viewModel.bloodPressureReadings.collectAsState()
    val allTempReadings by viewModel.bodyTemperatureReadings.collectAsState()

    val bpReadings   = remember(allBpReadings, selectedDate)   { allBpReadings.filter   { it.date == selectedDate } }
    val tempReadings = remember(allTempReadings, selectedDate) { allTempReadings.filter { it.date == selectedDate } }

    var deleteBpTarget   by remember { mutableStateOf<BloodPressureEntity?>(null) }
    var deleteTempTarget by remember { mutableStateOf<com.healthmonitor.app.data.local.entities.BodyTemperatureEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HMSpacing.lg)
    ) {
        Spacer(Modifier.height(HMSpacing.md))

        // ── Blood pressure section ────────────────────────────────────────
        HMSectionHeader("ضغط الدم", color = HMColor.RedBright)
        Spacer(Modifier.height(HMSpacing.sm))

        if (bpReadings.isEmpty()) {
            HMEmptyState(emoji = "🫀", title = "لا توجد قراءات", subtitle = "اضغط + لإضافة قراءة")
        } else {
            bpReadings.forEach { r ->
                HealthBPReadingCard(r, onDelete = { deleteBpTarget = r })
                Spacer(Modifier.height(HMSpacing.sm))
            }
        }

        Spacer(Modifier.height(HMSpacing.lg))

        // ── Temperature section ───────────────────────────────────────────
        HMSectionHeader("درجة الحرارة", color = HMColor.CyanBright)
        Spacer(Modifier.height(HMSpacing.sm))

        if (tempReadings.isEmpty()) {
            HMEmptyState(emoji = "🌡️", title = "لا توجد قراءات", subtitle = "اضغط + لإضافة قراءة")
        } else {
            tempReadings.forEach { r ->
                BodyTemperatureCard(r, onDelete = { deleteTempTarget = r })
                Spacer(Modifier.height(HMSpacing.sm))
            }
        }

        Spacer(Modifier.height(80.dp))
    }

    // ── Delete BP ─────────────────────────────────────────────────────────
    deleteBpTarget?.let { r ->
        HMDialog(
            onDismiss           = { deleteBpTarget = null },
            title               = "حذف القراءة",
            confirmText         = "حذف",
            onConfirm           = { viewModel.deleteBloodPressure(r); deleteBpTarget = null },
            dismissText         = "إلغاء",
            confirmColor        = HMColor.RedBright,
            confirmContentColor = Color.White
        ) {
            Text("هل تريد حذف قراءة ضغط الدم هذه؟", fontSize = 13.sp, color = HMColor.TextSecondary)
        }
    }

    // ── Delete temperature ────────────────────────────────────────────────
    deleteTempTarget?.let { r ->
        HMDialog(
            onDismiss           = { deleteTempTarget = null },
            title               = "حذف القراءة",
            confirmText         = "حذف",
            onConfirm           = { viewModel.deleteBodyTemperature(r); deleteTempTarget = null },
            dismissText         = "إلغاء",
            confirmColor        = HMColor.RedBright,
            confirmContentColor = Color.White
        ) {
            Text("هل تريد حذف قراءة درجة الحرارة هذه؟", fontSize = 13.sp, color = HMColor.TextSecondary)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Vitals picker dialog — lets user choose BP or Temperature when tapping FAB
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VitalsPickerDialog(
    onBp: () -> Unit,
    onTemp: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HMColor.BgElevated,
        shape            = RoundedCornerShape(HMRadius.lg),
        title = {
            Text(
                "إضافة قياس",
                fontWeight = FontWeight.SemiBold,
                color      = HMColor.TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HMSpacing.sm)) {
                // BP option
                HMPressable(onClick = onBp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(HMRadius.sm))
                            .background(HMColor.RedBright.copy(alpha = 0.08f))
                            .border(1.dp, HMColor.RedBright.copy(alpha = 0.3f), RoundedCornerShape(HMRadius.sm))
                            .padding(HMSpacing.md),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(HMSpacing.md)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(HMRadius.sm))
                                .background(HMColor.RedBright.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) { Text("🫀", fontSize = 20.sp) }
                        Column {
                            Text(
                                "ضغط الدم",
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color      = HMColor.TextPrimary
                            )
                            Text(
                                "تسجيل قراءة ضغط الدم والنبض والأكسجين",
                                fontSize = 11.sp,
                                color    = HMColor.TextSecondary
                            )
                        }
                    }
                }
                // Temperature option
                HMPressable(onClick = onTemp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(HMRadius.sm))
                            .background(HMColor.CyanBright.copy(alpha = 0.08f))
                            .border(1.dp, HMColor.CyanBright.copy(alpha = 0.3f), RoundedCornerShape(HMRadius.sm))
                            .padding(HMSpacing.md),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(HMSpacing.md)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(HMRadius.sm))
                                .background(HMColor.CyanBright.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) { Text("🌡️", fontSize = 20.sp) }
                        Column {
                            Text(
                                "درجة الحرارة",
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color      = HMColor.TextPrimary
                            )
                            Text(
                                "تسجيل درجة حرارة الجسم",
                                fontSize = 11.sp,
                                color    = HMColor.TextSecondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = HMColor.TextSecondary)
            }
        }
    )
}

@Composable
private fun HealthBPReadingCard(r: BloodPressureEntity, onDelete: () -> Unit = {}) {
    val (statusLabel, statusColor) = when {
        r.systolic < 120 && r.diastolic < 80 -> "طبيعي" to HMColor.GreenBright
        r.systolic < 140 && r.diastolic < 90 -> "مرتفع قليلاً" to HMColor.AmberBright
        else -> "مرتفع" to HMColor.RedBright
    }
    HMCard(modifier = Modifier.fillMaxWidth(), borderColor = statusColor.copy(alpha = 0.25f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "${r.systolic}/${r.diastolic}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    Text(
                        "mmHg",
                        fontSize = 11.sp,
                        color = HMColor.TextSecondary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                Text(formatHealthTs(r.time), fontSize = 11.sp, color = HMColor.TextDisabled)
            }
            Column(horizontalAlignment = Alignment.End) {
                HMBadge(text = statusLabel, color = statusColor, backgroundColor = statusColor.copy(alpha = 0.1f))
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
                ) {
                    r.pulse?.let { Text("💓 $it bpm", fontSize = 11.sp, color = HMColor.TextSecondary) }
                    r.oxygenSaturation?.let { Text("🫁 $it%", fontSize = 11.sp, color = HMColor.TextSecondary) }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Outlined.Delete, "حذف",
                            tint     = HMColor.RedBright.copy(alpha = 0.6f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
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

// ─────────────────────────────────────────────────────────────────────────────
// Symptoms — display only
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SymptomsContent(viewModel: DashboardViewModel, selectedDate: Long) {
    val allSymptoms = viewModel.allSymptoms.collectAsState().value
    val filteredSymptoms = remember(allSymptoms, selectedDate) {
        allSymptoms.filter { it.date == selectedDate }.sortedByDescending { it.time }
    }
    var deleteTarget by remember { mutableStateOf<SymptomEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HMSpacing.lg)
    ) {
        Spacer(Modifier.height(HMSpacing.md))
        HMSectionHeader(
            title = "الأعراض (${filteredSymptoms.size})",
            color = HMColor.AmberBright
        )
        Spacer(Modifier.height(HMSpacing.sm))

        if (filteredSymptoms.isEmpty()) {
            HMEmptyState(emoji = "✅", title = "لا أعراض", subtitle = "اضغط + لتسجيل عرض جديد")
        } else {
            filteredSymptoms.forEach { s ->
                HealthSymptomCard(symptom = s, onDelete = { deleteTarget = s })
                Spacer(Modifier.height(HMSpacing.sm))
            }
        }
        Spacer(Modifier.height(80.dp))
    }

    deleteTarget?.let { s ->
        HMDialog(
            onDismiss = { deleteTarget = null },
            title = "حذف السجل",
            confirmText = "حذف",
            onConfirm = { viewModel.deleteSymptom(s); deleteTarget = null },
            dismissText = "إلغاء",
            confirmColor = HMColor.RedBright,
            confirmContentColor = Color.White
        ) {
            Text("هل تريد حذف سجل «${s.symptomType}»؟", fontSize = 13.sp, color = HMColor.TextSecondary)
        }
    }
}

@Composable
private fun HealthSymptomCard(symptom: SymptomEntity, onDelete: () -> Unit) {
    val severityColor = healthSeverityColor(symptom.severity)
    HMCard(modifier = Modifier.fillMaxWidth(), borderColor = severityColor.copy(alpha = 0.25f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        symptom.symptomType,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HMColor.TextPrimary
                    )
                    HMBadge(
                        text = symptom.severity,
                        color = severityColor,
                        backgroundColor = severityColor.copy(alpha = 0.12f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(formatHealthSymptomTime(symptom.time), fontSize = 11.sp, color = HMColor.TextDisabled)
                if (symptom.inhalerUsed) {
                    Spacer(Modifier.height(4.dp))
                    val note = when (symptom.improvementAfterInhaler) {
                        true -> "البخاخ ✓ تحسّن"
                        false -> "البخاخ ✓ بدون تحسّن"
                        else -> "استُخدم البخاخ"
                    }
                    Text(note, fontSize = 11.sp, color = HMColor.TextSecondary)
                }
                if (!symptom.notes.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(symptom.notes, fontSize = 11.sp, color = HMColor.TextSecondary)
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Delete,
                    "حذف",
                    tint = HMColor.RedBright.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Add Blood Pressure Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AddBloodPressureDialog(viewModel: DashboardViewModel, onDismiss: () -> Unit) {
    val currentPatient by viewModel.currentPatient.collectAsState()

    var systolic by remember { mutableStateOf("") }
    var diastolic by remember { mutableStateOf("") }
    var pulse by remember { mutableStateOf("") }
    var oxygenSaturation by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var showSuccess by remember { mutableStateOf(false) }

    val sys = systolic.toIntOrNull()
    val dia = diastolic.toIntOrNull()
    val canSave = sys != null && dia != null && currentPatient != null

    val bpStatus = if (sys != null && dia != null) {
        when {
            sys < 120 && dia < 80 -> Triple("طبيعي", HMColor.GreenBright, HMColor.GreenBg)
            sys < 140 && dia < 90 -> Triple("مرتفع قليلاً", HMColor.AmberBright, HMColor.AmberBg)
            else -> Triple("مرتفع", HMColor.RedBright, HMColor.RedBg)
        }
    } else null

    if (showSuccess) {
        HMSuccessDialog(
            title = "تم الحفظ",
            message = "تم حفظ قراءة ضغط الدم بنجاح",
            onDismiss = { showSuccess = false; onDismiss() }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HMColor.BgElevated,
        titleContentColor = HMColor.TextPrimary,
        shape = RoundedCornerShape(HMRadius.lg),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(HMRadius.xs))
                        .background(HMColor.RedBright.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Favorite, null, tint = HMColor.RedBright, modifier = Modifier.size(14.dp))
                }
                Text("قراءة جديدة", fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(HMSpacing.sm)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)) {
                    HMTextField(
                        value = systolic,
                        onValueChange = { systolic = it.filter(Char::isDigit) },
                        label = "الانقباضي *",
                        trailingText = "mmHg",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f)
                    )
                    HMTextField(
                        value = diastolic,
                        onValueChange = { diastolic = it.filter(Char::isDigit) },
                        label = "الانبساطي *",
                        trailingText = "mmHg",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)) {
                    HMTextField(
                        value = pulse,
                        onValueChange = { pulse = it.filter(Char::isDigit) },
                        label = "النبض",
                        trailingText = "bpm",
                        keyboardType = KeyboardType.Number,
                        leadingIcon = Icons.Outlined.Favorite,
                        modifier = Modifier.weight(1f)
                    )
                    HMTextField(
                        value = oxygenSaturation,
                        onValueChange = { oxygenSaturation = it.filter(Char::isDigit) },
                        label = "الأكسجين",
                        trailingText = "%",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f)
                    )
                }
                HMTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "ملاحظات (اختياري)",
                    singleLine = false,
                    minLines = 2,
                    maxLines = 3
                )
                // live status preview
                AnimatedVisibility(
                    visible = bpStatus != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    bpStatus?.let { (label, color, bg) ->
                        HMCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = bg,
                            borderColor = color.copy(alpha = 0.3f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${systolic}/${diastolic} mmHg",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = color
                                )
                                HMBadge(text = label, color = color, backgroundColor = color.copy(alpha = 0.15f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            HMPressable(onClick = {
                if (canSave) {
                    viewModel.recordBloodPressure(
                        systolic = sys ?: return@HMPressable,
                        diastolic = dia ?: return@HMPressable,
                        pulse = pulse.toIntOrNull(),
                        oxygenSaturation = oxygenSaturation.toIntOrNull(),
                        notes = notes.ifBlank { null }
                    )
                    showSuccess = true
                }
            }, enabled = canSave) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(HMRadius.sm))
                        .background(if (canSave) HMColor.RedBright else HMColor.BgOverlay)
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        "حفظ",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = if (canSave) HMColor.TextInverse else HMColor.TextDisabled
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = HMColor.TextSecondary, fontSize = 13.sp)
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Add Symptom Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AddSymptomDialog(viewModel: DashboardViewModel, onDismiss: () -> Unit) {
    val symptomTypes by viewModel.symptomTypes.collectAsState()

    var selectedSymptom by remember { mutableStateOf<String?>(null) }
    var customSymptom by remember { mutableStateOf("") }
    var selectedSeverity by remember { mutableStateOf<String?>(null) }
    var inhalerUsed by remember { mutableStateOf(false) }
    var inhalerHelped by remember { mutableStateOf<Boolean?>(null) }
    var notes by remember { mutableStateOf("") }
    var showSuccess by remember { mutableStateOf(false) }

    val symptomToSave = customSymptom.trim().ifBlank { selectedSymptom.orEmpty() }
    val canSave = symptomToSave.isNotBlank() && selectedSeverity != null

    if (showSuccess) {
        HMSuccessDialog(
            title = "تم الحفظ",
            message = "تم تسجيل العرض بنجاح",
            onDismiss = { showSuccess = false; onDismiss() }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HMColor.BgElevated,
        titleContentColor = HMColor.TextPrimary,
        shape = RoundedCornerShape(HMRadius.lg),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(HMRadius.xs))
                        .background(HMColor.AmberBright.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.HealthAndSafety,
                        null,
                        tint = HMColor.AmberBright,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text("تسجيل عرض جديد", fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(HMSpacing.md)
            ) {
                // ── Symptom chip picker ───────────────────────────────────
                HMSectionHeader("نوع العرض")
                FlowRow(horizontalGap = 6.dp, verticalGap = 6.dp) {
                    symptomTypes.forEach { symptom ->
                        val isSelected = selectedSymptom == symptom && customSymptom.isBlank()
                        HealthSymptomChip(
                            text = symptom,
                            isSelected = isSelected,
                            onClick = { selectedSymptom = symptom; customSymptom = "" }
                        )
                    }
                }
                HMTextField(
                    value = customSymptom,
                    onValueChange = { customSymptom = it; if (it.isNotBlank()) selectedSymptom = null },
                    label = "أو اكتب عرضاً جديداً",
                    placeholder = "مثال: دوخة، صداع...",
                    leadingIcon = Icons.Outlined.Edit
                )

                // ── Severity ─────────────────────────────────────────────
                AnimatedVisibility(
                    visible = symptomToSave.isNotBlank(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(HMSpacing.sm)) {
                        HMSectionHeader("شدة العرض")
                        HMSegmentedSelector(
                            options = listOf("خفيف", "متوسط", "شديد"),
                            selectedOption = selectedSeverity,
                            onSelect = { selectedSeverity = it },
                            accentColors = listOf(HMColor.GreenBright, HMColor.AmberBright, HMColor.RedBright)
                        )
                    }
                }

                // ── Inhaler ───────────────────────────────────────────────
                HMCard(modifier = Modifier.fillMaxWidth()) {
                    HMToggleRow(
                        title = "استخدام البخاخ",
                        subtitle = "هل استخدمت البخاخ مع هذا العرض؟",
                        checked = inhalerUsed,
                        onCheckedChange = { inhalerUsed = it; inhalerHelped = null }
                    )
                    AnimatedVisibility(visible = inhalerUsed) {
                        Column {
                            Spacer(Modifier.height(HMSpacing.md))
                            HMDivider()
                            Spacer(Modifier.height(HMSpacing.md))
                            Text("هل تحسّن الوضع بعد البخاخ؟", fontSize = 13.sp, color = HMColor.TextPrimary)
                            Spacer(Modifier.height(HMSpacing.sm))
                            HMSegmentedSelector(
                                options = listOf("نعم", "لا"),
                                selectedOption = when (inhalerHelped) {
                                    true -> "نعم"; false -> "لا"; else -> null
                                },
                                onSelect = { inhalerHelped = it == "نعم" },
                                accentColors = listOf(HMColor.GreenBright, HMColor.RedBright)
                            )
                        }
                    }
                }

                HMTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "ملاحظات إضافية (اختياري)",
                    singleLine = false,
                    minLines = 2,
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            HMPressable(onClick = {
                if (canSave) {
                    viewModel.recordSymptom(
                        symptomType = symptomToSave,
                        severity = selectedSeverity ?: return@HMPressable,
                        notes = notes.ifBlank { null },
                        inhalerUsed = inhalerUsed,
                        improvementAfterInhaler = inhalerHelped
                    )
                    showSuccess = true
                }
            }, enabled = canSave) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(HMRadius.sm))
                        .background(if (canSave) HMColor.AmberBright else HMColor.BgOverlay)
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        "حفظ",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = if (canSave) HMColor.TextInverse else HMColor.TextDisabled
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = HMColor.TextSecondary, fontSize = 13.sp)
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HealthSymptomChip(text: String, isSelected: Boolean, onClick: () -> Unit) {
    HMPressable(onClick = onClick) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(HMRadius.full))
                .background(
                    if (isSelected) HMColor.AmberBright.copy(alpha = 0.14f)
                    else HMColor.BgOverlay
                )
                .border(
                    1.dp,
                    if (isSelected) HMColor.AmberBright.copy(alpha = 0.4f) else HMColor.BorderSubtle,
                    RoundedCornerShape(HMRadius.full)
                )
                .padding(horizontal = HMSpacing.md, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                fontSize = 12.sp,
                color = if (isSelected) HMColor.AmberBright else HMColor.TextSecondary,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Date navigator
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HealthDateNavigator(dateMillis: Long, onPrevious: () -> Unit, onNext: () -> Unit) {
    HMCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = HMColor.BgSurface,
        borderColor = HMColor.BorderSubtle
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "اليوم السابق", tint = HMColor.TextSecondary)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatHealthDate(dateMillis),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HMColor.TextPrimary
                )
                Text("تصفح التاريخ", fontSize = 10.sp, color = HMColor.TextDisabled)
            }
            IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, "اليوم التالي", tint = HMColor.TextSecondary)
            }
        }
    }
}


@Composable
private fun BodyTemperatureCard(
    r: com.healthmonitor.app.data.local.entities.BodyTemperatureEntity,
    onDelete: () -> Unit
) {
    val (statusLabel, statusColor) = when {
        r.temperature < 36.0f -> "أقل من الطبيعي" to HMColor.BlueBright
        r.temperature <= 37.5f -> "طبيعي" to HMColor.GreenBright
        r.temperature <= 38.5f -> "حمى خفيفة" to HMColor.AmberBright
        else -> "حمى" to HMColor.RedBright
    }
    val siteLabel = when (r.site) {
        "oral" -> "فموي"
        "axillary" -> "إبطي"
        "rectal" -> "شرجي"
        "tympanic" -> "أذني"
        "forehead" -> "جبهي"
        else -> r.site
    }

    HMCard(modifier = Modifier.fillMaxWidth(), borderColor = statusColor.copy(alpha = 0.25f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "%.1f".format(r.temperature),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    Text(
                        "°C",
                        fontSize = 13.sp,
                        color = HMColor.TextSecondary,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
                Text(formatHealthTs(r.time), fontSize = 11.sp, color = HMColor.TextDisabled)
                Text("موضع القياس: $siteLabel", fontSize = 11.sp, color = HMColor.TextSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                HMBadge(text = statusLabel, color = statusColor, backgroundColor = statusColor.copy(alpha = 0.1f))
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Delete,
                        "حذف",
                        tint = HMColor.RedBright.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
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

// ─────────────────────────────────────────────────────────────────────────────
// Add Body Temperature Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AddBodyTemperatureDialog(viewModel: DashboardViewModel, onDismiss: () -> Unit) {
    var tempText by remember { mutableStateOf("") }
    var selectedSite by remember { mutableStateOf("oral") }
    var notes by remember { mutableStateOf("") }
    var showSuccess by remember { mutableStateOf(false) }

    val temp = tempText.toFloatOrNull()
    val canSave = temp != null && temp in 30f..45f

    val sites = listOf(
        "oral" to "فموي",
        "axillary" to "إبطي",
        "tympanic" to "أذني",
        "forehead" to "جبهي",
        "rectal" to "شرجي"
    )

    val tempStatus = temp?.let {
        when {
            it < 36.0f -> Triple("أقل من الطبيعي", HMColor.BlueBright, HMColor.BlueBg)
            it <= 37.5f -> Triple("طبيعي", HMColor.GreenBright, HMColor.GreenBg)
            it <= 38.5f -> Triple("حمى خفيفة", HMColor.AmberBright, HMColor.AmberBg)
            else -> Triple("حمى", HMColor.RedBright, HMColor.RedBg)
        }
    }

    if (showSuccess) {
        HMSuccessDialog(
            title = "تم الحفظ",
            message = "تم تسجيل درجة الحرارة بنجاح",
            onDismiss = { showSuccess = false; onDismiss() }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HMColor.BgElevated,
        titleContentColor = HMColor.TextPrimary,
        shape = RoundedCornerShape(HMRadius.lg),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(HMRadius.xs))
                        .background(HMColor.CyanBright.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Thermostat, null, tint = HMColor.CyanBright, modifier = Modifier.size(14.dp))
                }
                Text("قراءة حرارة جديدة", fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(HMSpacing.md)
            ) {
                HMTextField(
                    value = tempText,
                    onValueChange = { tempText = it.filterDecimalTemp() },
                    label = "درجة الحرارة *",
                    trailingText = "°C",
                    keyboardType = KeyboardType.Decimal,
                    leadingIcon = Icons.Outlined.Thermostat,
                    isError = tempText.isNotBlank() && temp == null
                )

                // Live status
                AnimatedVisibility(
                    visible = tempStatus != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    tempStatus?.let { (label, color, bg) ->
                        HMCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = bg,
                            borderColor = color.copy(alpha = 0.3f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${"%.1f".format(temp)} °C",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = color
                                )
                                HMBadge(text = label, color = color, backgroundColor = color.copy(alpha = 0.15f))
                            }
                        }
                    }
                }

                // Site selector
                Text(
                    "موضع القياس", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = HMColor.TextSecondary, letterSpacing = 1.sp
                )
                HMSegmentedSelector(
                    options = sites.map { it.second },
                    selectedOption = sites.firstOrNull { it.first == selectedSite }?.second,
                    onSelect = { label -> selectedSite = sites.first { it.second == label }.first },
                    accentColors = List(sites.size) { HMColor.CyanBright }
                )

                HMTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "ملاحظات (اختياري)",
                    singleLine = false,
                    minLines = 2,
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            HMPressable(onClick = {
                if (canSave) {
                    viewModel.recordBodyTemperature(
                        temperature = temp!!,
                        site = selectedSite,
                        notes = notes.ifBlank { null }
                    )
                    showSuccess = true
                }
            }, enabled = canSave) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(HMRadius.sm))
                        .background(if (canSave) HMColor.CyanBright else HMColor.BgOverlay)
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        "حفظ",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = if (canSave) HMColor.TextInverse else HMColor.TextDisabled
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = HMColor.TextSecondary, fontSize = 13.sp)
            }
        }
    )
}

private fun String.filterDecimalTemp(): String =
    filterIndexed { index, c -> c.isDigit() || (c == '.' && indexOf('.') == index) }


private fun healthSeverityColor(severity: String): Color = when (severity) {
    "خفيف" -> HMColor.GreenBright
    "متوسط" -> HMColor.AmberBright
    else -> HMColor.RedBright
}

private fun formatHealthDate(ts: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ts))

private fun formatHealthTs(ts: Long): String =
    SimpleDateFormat("d MMM yyyy  hh:mm a", Locale.getDefault()).format(Date(ts))

private fun formatHealthSymptomTime(ts: Long): String =
    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(ts))