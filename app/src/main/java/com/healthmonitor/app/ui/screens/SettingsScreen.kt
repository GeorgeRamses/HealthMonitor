package com.healthmonitor.app.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.healthmonitor.app.ui.design.*
import com.healthmonitor.app.ui.viewmodel.DashboardViewModel
import com.healthmonitor.app.ui.viewmodel.ExportViewModel
import com.healthmonitor.app.ui.viewmodel.PatientViewModel
import com.healthmonitor.app.util.AlarmScheduler
import com.healthmonitor.app.util.parseMedicationTimes

// ─────────────────────────────────────────────────────────────────────────────
// Alarm preference helpers
// ─────────────────────────────────────────────────────────────────────────────

private const val PREFS              = "health_monitor_prefs"
private const val KEY_ALARMS_ENABLED = "medication_alarms_enabled"

fun isMedicationAlarmsEnabled(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_ALARMS_ENABLED, true)

fun setMedicationAlarmsEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_ALARMS_ENABLED, enabled).apply()
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(
    navController: NavHostController,
    patientViewModel: PatientViewModel    = hiltViewModel(),
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
    exportViewModel: ExportViewModel      = hiltViewModel()
) {
    val context         = LocalContext.current
    val activePatientId by patientViewModel.activePatientIdFlow.collectAsState()
    val patients        by patientViewModel.getAllPatients().collectAsState(initial = emptyList())
    val activePatient   = patients.find { it.id == activePatientId }
    val exportState     by exportViewModel.uiState.collectAsState()

    var alarmsEnabled by remember { mutableStateOf(isMedicationAlarmsEnabled(context)) }

    // Clear export state when screen is entered
    LaunchedEffect(Unit) { exportViewModel.clearState() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HMColor.BgBase)
            .verticalScroll(rememberScrollState())
            .padding(HMSpacing.lg)
    ) {

        // ── Title ─────────────────────────────────────────────────────────
        Text(
            "الإعدادات",
            fontSize   = 24.sp,
            fontWeight = FontWeight.Bold,
            color      = HMColor.TextPrimary,
            modifier   = Modifier.padding(bottom = HMSpacing.xl)
        )

        // ── Active patient ────────────────────────────────────────────────
        SettingsSectionHeader(title = "المريض النشط", icon = Icons.Outlined.Person)
        Spacer(Modifier.height(HMSpacing.sm))

        if (activePatient != null) {
            SettingsInfoCard(activePatient.name, "الاسم", Icons.Default.Person)
            if (activePatient.age > 0)
                SettingsInfoCard("${activePatient.age} سنة", "العمر", Icons.Default.Cake)
            if (activePatient.gender.isNotBlank())
                SettingsInfoCard(activePatient.gender, "الجنس", Icons.Outlined.Wc)
            if (activePatient.medicalConditions.isNotBlank())
                SettingsInfoCard(activePatient.medicalConditions, "الحالات الطبية", Icons.Default.MedicalServices)
        } else {
            HMCard(modifier = Modifier.fillMaxWidth(), borderColor = HMColor.AmberBorder, backgroundColor = HMColor.AmberBg) {
                Text("لم يتم تحديد مريض نشط", fontSize = 13.sp, color = HMColor.AmberBright)
            }
        }

        Spacer(Modifier.height(HMSpacing.sm))
        HMSecondaryButton(
            text        = "إدارة المرضى",
            onClick     = { navController.navigate("patients") },
            leadingIcon = Icons.Default.Group,
            modifier    = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(HMSpacing.xl))

        // ── Alarms ────────────────────────────────────────────────────────
        SettingsSectionHeader(title = "التنبيهات والمنبهات", icon = Icons.Outlined.Notifications)
        Spacer(Modifier.height(HMSpacing.sm))

        HMCard(
            modifier        = Modifier.fillMaxWidth(),
            borderColor     = if (alarmsEnabled) HMColor.GreenBorder else HMColor.BorderDefault,
            backgroundColor = if (alarmsEnabled) HMColor.GreenBg else HMColor.BgSurface
        ) {
            HMToggleRow(
                title           = "منبهات الأدوية",
                subtitle        = if (alarmsEnabled)
                    "✓ ستتلقى تنبيهاً عند حلول موعد كل جرعة"
                else
                    "⚠️ المنبهات معطلة — لن تتلقى أي تذكير",
                checked         = alarmsEnabled,
                accentColor     = HMColor.GreenBright,
                onCheckedChange = { enabled ->
                    alarmsEnabled = enabled
                    setMedicationAlarmsEnabled(context, enabled)
                    applyAlarmState(context = context, enabled = enabled, viewModel = dashboardViewModel)
                }
            )
            AnimatedVisibility(
                visible = !alarmsEnabled,
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(Modifier.height(HMSpacing.sm))
                    HMDivider()
                    Spacer(Modifier.height(HMSpacing.sm))
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
                    ) {
                        Icon(Icons.Default.WarningAmber, null, tint = HMColor.AmberBright, modifier = Modifier.size(14.dp))
                        Text(
                            "يُنصح بإبقاء المنبهات مفعّلة لضمان انتظام الجرعات",
                            fontSize = 11.sp, color = HMColor.AmberBright, lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(HMSpacing.xl))

        // ── Data Export ───────────────────────────────────────────────────
        SettingsSectionHeader(title = "تصدير البيانات", icon = Icons.Outlined.Share)
        Spacer(Modifier.height(HMSpacing.sm))

        HMCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "تصدير التقرير الطبي الشامل",
                fontSize   = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color      = HMColor.TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "يشمل التقرير: بيانات المريض، الأدوية، قراءات ضغط الدم، الأعراض، والتقارير المخبرية.",
                fontSize   = 12.sp,
                color      = HMColor.TextSecondary,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(HMSpacing.md))

            // Error state
            exportState.error?.let { err ->
                AiErrorCard(err)
                Spacer(Modifier.height(HMSpacing.sm))
            }

            // Success state
            AnimatedVisibility(visible = exportState.success) {
                Column {
                    HMCard(
                        modifier        = Modifier.fillMaxWidth(),
                        borderColor     = HMColor.GreenBorder,
                        backgroundColor = HMColor.GreenBg
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = HMColor.GreenBright, modifier = Modifier.size(16.dp))
                            Text("تم إنشاء التقرير — سيفتح مربع المشاركة الآن.", fontSize = 12.sp, color = HMColor.GreenBright)
                        }
                    }
                    Spacer(Modifier.height(HMSpacing.sm))
                }
            }

            HMPrimaryButton(
                text    = if (exportState.isLoading) "جارٍ إنشاء التقرير..." else "تصدير ومشاركة التقرير",
                onClick = { exportViewModel.exportAndShare() },
                enabled = !exportState.isLoading && activePatient != null,
                leadingIcon = Icons.Outlined.Share,
                color   = HMColor.GreenBright,
                modifier = Modifier.fillMaxWidth()
            )

            if (activePatient == null) {
                Spacer(Modifier.height(HMSpacing.xs))
                Text(
                    "يرجى تحديد مريض أولاً لتفعيل التصدير.",
                    fontSize = 11.sp,
                    color    = HMColor.TextDisabled
                )
            }
        }

        Spacer(Modifier.height(HMSpacing.xl))

        // ── Medication history ─────────────────────────────────────────────
        SettingsSectionHeader(title = "السجلات", icon = Icons.Outlined.History)
        Spacer(Modifier.height(HMSpacing.sm))

        HMSecondaryButton(
            text        = "سجل تاريخ الجرعات",
            onClick     = { navController.navigate("medication_history") },
            leadingIcon = Icons.Default.History,
            color       = HMColor.BlueBright,
            modifier    = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(HMSpacing.xl))

        // ── About ─────────────────────────────────────────────────────────
        SettingsSectionHeader(title = "حول التطبيق", icon = Icons.Outlined.Info)
        Spacer(Modifier.height(HMSpacing.sm))
        SettingsInfoCard("v5.5.3", "الإصدار", Icons.Default.Info)
        SettingsInfoCard("SQLite — بدون إنترنت", "قاعدة البيانات", Icons.Default.Storage)
        SettingsInfoCard("Gemini AI", "محرك الذكاء الاصطناعي", Icons.Default.AutoAwesome)

        Spacer(Modifier.height(HMSpacing.xxxl))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Cancel / reschedule all alarms
// ─────────────────────────────────────────────────────────────────────────────

private fun applyAlarmState(context: Context, enabled: Boolean, viewModel: DashboardViewModel) {
    val meds = viewModel.allMedications.value.filter { it.isActive && !it.isDeleted }
    if (enabled) {
        meds.forEach { med ->
            parseMedicationTimes(med.scheduledTimes).forEach { time ->
                AlarmScheduler.schedule(context, med.name, med.id, time)
            }
        }
    } else {
        meds.forEach { med ->
            AlarmScheduler.cancelAll(context, med.name, med.id, parseMedicationTimes(med.scheduledTimes))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm),
        modifier              = Modifier.padding(bottom = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(HMRadius.xs))
                .background(HMColor.GreenBright.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = HMColor.GreenBright, modifier = Modifier.size(13.dp))
        }
        Text(
            title.uppercase(),
            fontSize      = 10.sp,
            fontWeight    = FontWeight.Bold,
            color         = HMColor.TextSecondary,
            letterSpacing = 1.2.sp
        )
    }
}

@Composable
private fun SettingsInfoCard(value: String, label: String, icon: ImageVector) {
    HMCard(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HMSpacing.md)
        ) {
            Icon(icon, null, tint = HMColor.GreenBright, modifier = Modifier.size(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 10.sp, color = HMColor.TextSecondary, letterSpacing = 0.5.sp)
                Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = HMColor.TextPrimary)
            }
        }
    }
}