package com.healthmonitor.app.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.healthmonitor.app.ui.viewmodel.DashboardViewModel
import com.healthmonitor.app.ui.viewmodel.MedicationReminderViewModel
import com.healthmonitor.app.util.ActiveCaseManager
import com.healthmonitor.app.util.ActivePatientManager
import com.healthmonitor.app.util.format12Hour
import org.json.JSONArray

import androidx.compose.material3.rememberTimePickerState
import com.healthmonitor.app.ui.design.HMAlarmPermissionDialogs

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

    // ── Permission / capability state ────────────────────────────────────

    val notifEnabledState = remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val exactAlarmState = remember { mutableStateOf(reminderViewModel.canScheduleExactAlarms()) }

    // Re-check after the user returns from Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifEnabledState.value = NotificationManagerCompat.from(context).areNotificationsEnabled()
                exactAlarmState.value = reminderViewModel.canScheduleExactAlarms()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifEnabledState.value = granted
        if (!granted) Toast.makeText(
            context, "الإذن مرفوض — فعّل الإشعارات من إعدادات التطبيق", Toast.LENGTH_LONG
        ).show()
    }

    // ── Time picker ──────────────────────────────────────────────────────
    HMAlarmPermissionDialogs(
        showA14Dialog = reminderViewModel.showA14Dialog,
        showOemDialog = reminderViewModel.showOemDialog,
        onDismissA14 = { reminderViewModel.showA14Dialog = false },
        onDismissOem = { reminderViewModel.showOemDialog = false }
    )
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("تم") }
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }

    // ── UI ───────────────────────────────────────────────────────────────

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Notifications banner
        if (!notifEnabledState.value) {
            PermissionBanner(
                title = "الإشعارات معطلة",
                subtitle = "يجب تمكين الإشعارات لتلقي تذكيرات الأدوية",
                btnText = "تمكين",
                btnColor = Color(0xFF4CAF50),
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        })
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Exact-alarm banner
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !exactAlarmState.value) {
            PermissionBanner(
                title = "المنبهات الدقيقة معطلة",
                subtitle = "مطلوب إذن جدولة المنبهات لضمان دقة التذكيرات",
                btnText = "اطلب الإذن",
                btnColor = Color(0xFFFFA000),
                onClick = {
                    try {
                        context.startActivity(
                            Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARMS")
                        )
                    } catch (_: Exception) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                        )
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Text(
            "إضافة منبه دواء",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE8E8E8)
            ),
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = medicationName,
            onValueChange = { medicationName = it },
            label = { Text("اسم الدواء") },
            placeholder = { Text("مثال: ELiquis", color = Color(0xFF555555)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color(0xFFE8E8E8),
                unfocusedTextColor = Color(0xFF888888),
                focusedBorderColor = Color(0xFF4CAF50),
                unfocusedBorderColor = Color(0xFF2A2A2A),
                focusedLabelColor = Color(0xFF4CAF50),
                unfocusedLabelColor = Color(0xFF666666),
                cursorColor = Color(0xFF4CAF50)
            ),
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Time picker card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
            border = BorderStroke(1.dp, Color(0xFF2A2A2A))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "وقت الجرعة",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF888888))
                    )
                    Text(
                        format12Hour(timePickerState.hour, timePickerState.minute),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Button(
                    onClick = { showTimePicker = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Alarm, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تغيير")
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                val timeString = "%02d:%02d".format(timePickerState.hour, timePickerState.minute)
                // Resolve patient / case ids reactively
                val patientId = ActivePatientManager.getActivePatientId()

                if (patientId == null) {
                    Toast.makeText(context, "يرجى اختيار مريض أولاً", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (activeCaseId == null) {
                    Toast.makeText(context, "يرجى اختيار حالة أولاً", Toast.LENGTH_SHORT).show()
                    return@Button
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
                // Persist medication to DB AND then schedule the alarm
//                reminderViewModel.saveMedicationAndSchedule(med)
                reminderViewModel.saveWithPermissionCheck(med)
                medicationName = ""
                showSuccess = true
            },
            enabled = medicationName.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50),
                disabledContainerColor = Color(0xFF2A2A2A)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Save, null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("حفظ المنبه", fontWeight = FontWeight.Bold)
        }
    }

    if (showSuccess) {
        AlertDialog(
            onDismissRequest = { showSuccess = false },
            icon = {
                Icon(
                    Icons.Default.CheckCircle, null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(48.dp)
                )
            },
            title = { Text("تم الحفظ") },
            text = { Text("تم إضافة الدواء وجدولة المنبه بنجاح") },
            confirmButton = {
                Button(
                    onClick = { showSuccess = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) { Text("موافق") }
            },
            containerColor = Color(0xFF1A1A1A),
            textContentColor = Color(0xFFE8E8E8),
            titleContentColor = Color(0xFFE8E8E8)
        )
    }
}

@Composable
private fun PermissionBanner(
    title: String,
    subtitle: String,
    btnText: String,
    btnColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1A1A)),
        border = BorderStroke(1.dp, btnColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = Color(0xFFE8E8E8),
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFBBBBBB)))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                shape = RoundedCornerShape(8.dp)
            ) { Text(btnText) }
        }
    }
}