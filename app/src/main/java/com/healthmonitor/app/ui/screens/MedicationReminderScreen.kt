package com.healthmonitor.app.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import com.healthmonitor.app.data.local.entities.MedicationEntity
import com.healthmonitor.app.ui.design.*
import com.healthmonitor.app.ui.viewmodel.DashboardViewModel
import com.healthmonitor.app.ui.viewmodel.MedicationReminderViewModel
import com.healthmonitor.app.util.ActiveCaseManager
import com.healthmonitor.app.util.ActivePatientManager
import com.healthmonitor.app.util.format12Hour
import org.json.JSONArray
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationReminderScreen(
    navController: NavHostController,
    reminderViewModel: MedicationReminderViewModel = hiltViewModel(),
    dashboardViewModel: DashboardViewModel = hiltViewModel()
) {
    var medicationName by remember { mutableStateOf("") }
    val timePickerState = rememberTimePickerState(initialHour = 8, initialMinute = 0, is24Hour = false)
    var showTimePicker by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activeCaseId = ActiveCaseManager.getActiveCaseId()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // ── Permission state ──────────────────────────────────────────────────
    val notifEnabled = remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val exactAlarmOk = remember { mutableStateOf(reminderViewModel.canScheduleExactAlarms()) }
    var needsOemPermission by remember {
        mutableStateOf(AlarmPermissionHelper.needsOemPermission(context))
    }

    // Reset stale "ignored" flag from old app versions so returning OEM users
    // see the warning card again after an update.
    LaunchedEffect(Unit) {
        AlarmPermissionHelper.resetIgnoredFlag(context)
        needsOemPermission = AlarmPermissionHelper.needsOemPermission(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifEnabled.value = NotificationManagerCompat.from(context).areNotificationsEnabled()
                exactAlarmOk.value = reminderViewModel.canScheduleExactAlarms()
                needsOemPermission = AlarmPermissionHelper.needsOemPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifEnabled.value = granted
        if (!granted) Toast.makeText(
            context, "الإذن مرفوض — فعّل الإشعارات من إعدادات التطبيق", Toast.LENGTH_LONG
        ).show()
    }

    // ── OEM / A14 permission dialogs ──────────────────────────────────────
    HMAlarmPermissionDialogs(
        showA14Dialog = reminderViewModel.showA14Dialog,
        showOemDialog = reminderViewModel.showOemDialog,
        onDismissA14 = { reminderViewModel.showA14Dialog = false },
        onDismissOem = { reminderViewModel.showOemDialog = false },
        onOemGranted = {                           // ← add this
            reminderViewModel.showOemDialog = false
            needsOemPermission = false
        }
    )

    // ── Time picker dialog ────────────────────────────────────────────────
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = HMColor.BgElevated,
            title = { Text("اختر وقت الجرعة", color = HMColor.TextPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                TimePicker(
                    state = timePickerState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = HMColor.BgOverlay,
                        clockDialSelectedContentColor = HMColor.TextInverse,
                        clockDialUnselectedContentColor = HMColor.TextSecondary,
                        selectorColor = HMColor.GreenBright,
                        containerColor = HMColor.BgElevated,
                        periodSelectorBorderColor = HMColor.GreenBright,
                        periodSelectorSelectedContainerColor = HMColor.GreenBright,
                        periodSelectorUnselectedContainerColor = HMColor.BgOverlay,
                        periodSelectorSelectedContentColor = HMColor.TextInverse,
                        periodSelectorUnselectedContentColor = HMColor.TextSecondary,
                        timeSelectorSelectedContainerColor = HMColor.GreenBright.copy(alpha = 0.2f),
                        timeSelectorUnselectedContainerColor = HMColor.BgOverlay,
                        timeSelectorSelectedContentColor = HMColor.GreenBright,
                        timeSelectorUnselectedContentColor = HMColor.TextPrimary
                    )
                )
            },
            confirmButton = {
                HMPressable(onClick = { showTimePicker = false }) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(HMRadius.sm))
                            .background(HMColor.GreenBright)
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text("تأكيد", fontWeight = FontWeight.SemiBold, color = HMColor.TextInverse)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("إلغاء", color = HMColor.TextSecondary)
                }
            },
            shape = RoundedCornerShape(HMRadius.lg)
        )
    }

    // ── Success dialog ────────────────────────────────────────────────────
    if (showSuccess) {
        HMSuccessDialog(
            title = "تم الحفظ",
            message = "تم إضافة الدواء وجدولة المنبه بنجاح",
            onDismiss = { showSuccess = false }
        )
    }

    // ── Main UI ───────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HMColor.BgBase)
            .verticalScroll(rememberScrollState())
            .padding(HMSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(HMSpacing.md)
    ) {
        Spacer(Modifier.height(HMSpacing.sm))

        // ── Screen title ──────────────────────────────────────────────────
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
                    Icons.Default.Alarm, null,
                    tint = HMColor.GreenBright,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    "إضافة منبه دواء",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = HMColor.TextPrimary
                )
                Text(
                    "تذكير سريع بجرعة واحدة",
                    fontSize = 11.sp,
                    color = HMColor.TextSecondary
                )
            }
        }

        // ── Permission banners ────────────────────────────────────────────
        AnimatedVisibility(visible = !notifEnabled.value) {
            HMPermissionBanner(
                title = "الإشعارات معطلة",
                subtitle = "يجب تمكين الإشعارات لتلقي تذكيرات الأدوية",
                buttonText = "تمكين",
                accentColor = HMColor.RedBright,
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                        )
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !exactAlarmOk.value
        ) {
            HMPermissionBanner(
                title = "المنبهات الدقيقة معطلة",
                subtitle = "مطلوب لضمان دقة التذكيرات",
                buttonText = "اطلب الإذن",
                accentColor = HMColor.AmberBright,
                onClick = {
                    try {
                        context.startActivity(
                            Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARMS")
                        )

                    } catch (_: Exception) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = "package:${context.packageName}".toUri()
                            }
                        )
                    }
                }
            )
        }

        AnimatedVisibility(visible = needsOemPermission) {
            PermissionWarningCard(
                onAction = { reminderViewModel.showOemDialog = true },
                onGranted = {
                    AlarmPermissionHelper.markOemPermissionGranted(context)
                    needsOemPermission = false
                }
            )
        }

        // ── Input card ────────────────────────────────────────────────────
        HMCard(modifier = Modifier.fillMaxWidth()) {
            HMSectionHeader("بيانات الدواء")
            Spacer(Modifier.height(HMSpacing.sm))

            HMTextField(
                value = medicationName,
                onValueChange = { medicationName = it },
                label = "اسم الدواء *",
                placeholder = "مثال: Eliquis",
                leadingIcon = Icons.Outlined.LocalPharmacy,
                singleLine = true
            )
        }

        // ── Time card ─────────────────────────────────────────────────────
        HMCard(modifier = Modifier.fillMaxWidth()) {
            HMSectionHeader("وقت الجرعة")
            Spacer(Modifier.height(HMSpacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
                ) {
                    Icon(
                        Icons.Outlined.Schedule, null,
                        tint = HMColor.GreenBright,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        format12Hour(timePickerState.hour, timePickerState.minute),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = HMColor.GreenBright
                    )
                }
                HMSecondaryButton(
                    text = "تغيير",
                    onClick = { showTimePicker = true },
                    leadingIcon = Icons.Outlined.Alarm,
                    color = HMColor.GreenBright
                )
            }
        }

        // ── Save button ───────────────────────────────────────────────────
        HMPrimaryButton(
            text = "حفظ المنبه",
            onClick = {
                val timeString = "%02d:%02d".format(timePickerState.hour, timePickerState.minute)
                val patientId = ActivePatientManager.getActivePatientId()

                if (patientId == null) {
                    Toast.makeText(context, "يرجى اختيار مريض أولاً", Toast.LENGTH_SHORT).show()
                    return@HMPrimaryButton
                }
                if (activeCaseId == null) {
                    Toast.makeText(context, "يرجى اختيار حالة أولاً", Toast.LENGTH_SHORT).show()
                    return@HMPrimaryButton
                }
                val med = MedicationEntity(
                    patientId = patientId,
                    caseId = activeCaseId,
                    name = medicationName.trim(),
                    dosage = "",
                    frequency = "once_daily",
                    timesPerDay = 1,
                    scheduledTimes = JSONArray(listOf(timeString)).toString(),
                    startDate = System.currentTimeMillis()
                )
                reminderViewModel.saveWithPermissionCheck(med)
                medicationName = ""
                showSuccess = true
            },
            enabled = medicationName.isNotBlank(),
            leadingIcon = Icons.Default.Save,
            modifier = Modifier.fillMaxWidth()
        )

        // ── Info note ─────────────────────────────────────────────────────
        HMCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = HMColor.BlueBorder,
            backgroundColor = HMColor.BlueBg
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
            ) {
                Icon(
                    Icons.Filled.NotificationsOff, null,
                    tint = HMColor.BlueBright,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "هذه الشاشة تضيف منبهاً سريعاً. لإدارة أدوية كاملة مع جرعات ومواعيد متعددة، استخدم شاشة الأدوية.",
                    fontSize = 12.sp,
                    color = HMColor.BlueBright.copy(alpha = 0.85f),
                    lineHeight = 18.sp
                )
            }
        }
        Spacer(Modifier.height(HMSpacing.xxxl))
    }
}