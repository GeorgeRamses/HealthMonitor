package com.healthmonitor.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.healthmonitor.app.data.local.entities.MedicationEntity
import com.healthmonitor.app.data.local.entities.MedicationLogEntity
import com.healthmonitor.app.data.model.FrequencyType
import com.healthmonitor.app.ui.design.*
import com.healthmonitor.app.ui.viewmodel.AiToolsViewModel
import com.healthmonitor.app.ui.viewmodel.DashboardViewModel
import com.healthmonitor.app.util.ActiveCaseManager
import com.healthmonitor.app.util.MedicationInventoryMode
import com.healthmonitor.app.util.MedicationInventoryStatus
import com.healthmonitor.app.util.calculateMedicationInventoryStatus
import com.healthmonitor.app.util.format12Hour
import com.healthmonitor.app.data.model.DosageFormType
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────────────────────
// Data model — one item PER MEDICATION (not per dose-time slot)
// All scheduled times are bundled inside; each time has its own taken flag.
// ─────────────────────────────────────────────────────────────────────────────

private data class MedItem(
    val medication: MedicationEntity,
    // Parallel lists: times[i] ↔ logs[i]
    val times: List<String>,
    val logs: List<MedicationLogEntity?>
) {
    // A medication is "fully taken" only when every scheduled time is taken
    val allTaken: Boolean get() = times.isNotEmpty() && logs.all { it?.taken == true }

    // "Partially taken" — at least one dose is taken but not all
    val partiallyTaken: Boolean get() = logs.any { it?.taken == true } && !allTaken
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationsScreen(
    navController: NavHostController,
    viewModel: DashboardViewModel = hiltViewModel(),
    aiViewModel: AiToolsViewModel = hiltViewModel()
) {
    val medications by viewModel.allMedications.collectAsState()
    val medicationLogs by viewModel.medicationLogs.collectAsState()
    val medicationHistoryLogs by viewModel.medicationHistoryLogs.collectAsState()
    val scheduleMap by viewModel.scheduleMap.collectAsState()
    val selectedDate by viewModel.selectedMedicationDate.collectAsState()
    val activeCaseId by ActiveCaseManager.activeCaseIdFlow.collectAsState()
    val currentPatient by viewModel.currentPatient.collectAsState()
    val medicineInfoState by aiViewModel.medicineInfoState.collectAsState()

    // ── Search / filter ───────────────────────────────────────────────────────
    var searchQuery by remember { mutableStateOf("") }

    // ── Dialog / overlay state ────────────────────────────────────────────────
    var showAddDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<MedicationEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<MedicationEntity?>(null) }
    var refillTarget by remember { mutableStateOf<MedicationEntity?>(null) }
    var showNoCaseDialog by remember { mutableStateOf(false) }
    var showNoPatientDialog by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var scannedName by remember { mutableStateOf("") }
    var medicineInfoTarget by remember { mutableStateOf<String?>(null) }

    val activeMedications = viewModel.todayMedications.collectAsState().value
    val inactiveMedications = medications.filterNot { it.isActive }

    // ── Build grouped MedItems (one card per medication) ──────────────────────
    fun buildMedItems(meds: List<MedicationEntity>): List<MedItem> =
        meds.map { med ->
            val times = scheduleMap[med.id]?.ifEmpty { listOf("") } ?: listOf("")
            val logs = times.map { t ->
                medicationLogs.firstOrNull { it.medicationId == med.id && it.scheduledTime == t }
            }
            MedItem(medication = med, times = times, logs = logs)
        }.sortedWith(
            compareBy(
                { it.times.minOfOrNull { t -> normalizeTime(t) } ?: "99:99" },
                { it.medication.name }
            ))

    val allItems = buildMedItems(activeMedications)
    val takenItems = allItems.filter { it.allTaken }
    val remainItems = allItems.filterNot { it.allTaken }

    // Apply search filter
    val query = searchQuery.trim()
    fun List<MedItem>.filtered() = if (query.isBlank()) this
    else filter { it.medication.name.contains(query, ignoreCase = true) }

    val filteredRemain = remainItems.filtered()
    val filteredTaken = takenItems.filtered()
    val filteredInactive = inactiveMedications
        .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }

    val totalDoses = allItems.sumOf { it.times.size }
    val takenDoses = allItems.sumOf { item -> item.logs.count { it?.taken == true } }
    val progress = if (totalDoses == 0) 0f else takenDoses.toFloat() / totalDoses

    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    var needsOemPermission by remember {
        mutableStateOf(AlarmPermissionHelper.needsOemPermission(context))
    }

    // Reset stale "ignored" flag from old app versions so returning OEM users
    // see the warning card again after an update.
    LaunchedEffect(Unit) {
        AlarmPermissionHelper.resetIgnoredFlag(context)
        needsOemPermission = AlarmPermissionHelper.needsOemPermission(context)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted; if (granted) showScanner = true }

    // Update warning card visibility when dialog is dismissed
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                needsOemPermission = AlarmPermissionHelper.needsOemPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(medicineInfoTarget) {
        medicineInfoTarget?.let { aiViewModel.fetchMedicineInfo(it) }
    }

    Scaffold(
        containerColor = HMColor.BgBase,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    when {
                        currentPatient == null -> showNoPatientDialog = true
                        activeCaseId == null -> showNoCaseDialog = true
                        else -> showAddDialog = true
                    }
                },
                containerColor = if (activeCaseId != null) HMColor.GreenBright else HMColor.TextDisabled,
                contentColor = HMColor.TextInverse,
                shape = RoundedCornerShape(HMRadius.sm)
            ) { Icon(Icons.Default.Add, "إضافة دواء") }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HMSpacing.lg)
        ) {
            Spacer(Modifier.height(HMSpacing.lg))
            if (needsOemPermission) {
                PermissionWarningCard(
                    onAction = { viewModel.showOemDialog = true },
                    onGranted = {
                        AlarmPermissionHelper.markOemPermissionGranted(context)
                        needsOemPermission = false
                    }  // hide immediately
                )
            }
            Text("الأدوية", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = HMColor.TextPrimary)
            Spacer(Modifier.height(HMSpacing.lg))

            if (activeCaseId == null) {
                NoCaseBanner()
                Spacer(Modifier.height(HMSpacing.md))
            }

            // ── Progress card ─────────────────────────────────────────────────
            DailyProgressCard(taken = takenDoses, total = totalDoses, progress = progress)
            Spacer(Modifier.height(HMSpacing.sm))

            // ── Date navigator ────────────────────────────────────────────────
            MedicationDateNavigator(
                dateMillis = selectedDate,
                onPrevious = { viewModel.moveSelectedMedicationDate(-1) },
                onNext = { viewModel.moveSelectedMedicationDate(1) }
            )
            Spacer(Modifier.height(HMSpacing.md))

            // ── Search bar ────────────────────────────────────────────────────
            HMTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = "بحث في الأدوية",
                placeholder = "اكتب اسم الدواء...",
                leadingIcon = Icons.Outlined.Search,
                trailingText = if (searchQuery.isNotBlank()) null else null,
                singleLine = true
            )
            // Clear button
            AnimatedVisibility(visible = searchQuery.isNotBlank()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { searchQuery = "" }) {
                        Text("مسح البحث", fontSize = 11.sp, color = HMColor.TextSecondary)
                    }
                }
            }
            Spacer(Modifier.height(HMSpacing.md))

            // ── Remaining ─────────────────────────────────────────────────────
            if (filteredRemain.isNotEmpty()) {
                HMSectionHeader(
                    title = "متبقي اليوم (${filteredRemain.size})",
                    color = HMColor.AmberBright
                )
                Spacer(Modifier.height(HMSpacing.sm))
                filteredRemain.forEach { item ->
                    MedCard(
                        item = item,
                        historyLogs = medicationHistoryLogs,
                        onToggleDose = { medId, time, taken ->
                            viewModel.setMedicationDoseTaken(medId, time, taken)
                        },
                        onEdit = { editTarget = item.medication },
                        onDelete = { deleteTarget = item.medication },
                        onReactivate = { viewModel.toggleMedicationActive(item.medication) },
                        onShowInfo = { medicineInfoTarget = item.medication.name },
                        onRefill = { refillTarget = item.medication }
                            .takeIf { item.medication.inventoryMode == MedicationInventoryMode.STOCK }
                            ?: {}
                    )
                    Spacer(Modifier.height(HMSpacing.sm))
                }
            }

            if (filteredRemain.isNotEmpty() && filteredTaken.isNotEmpty())
                Spacer(Modifier.height(HMSpacing.sm))

            // ── Taken ─────────────────────────────────────────────────────────
            if (filteredTaken.isNotEmpty()) {
                HMSectionHeader(
                    title = "أُخذت اليوم (${filteredTaken.size})",
                    color = HMColor.GreenBright
                )
                Spacer(Modifier.height(HMSpacing.sm))
                filteredTaken.forEach { item ->
                    MedCard(
                        item = item,
                        historyLogs = medicationHistoryLogs,
                        onToggleDose = { medId, time, taken ->
                            viewModel.setMedicationDoseTaken(medId, time, taken)
                        },
                        onEdit = { editTarget = item.medication },
                        onDelete = { deleteTarget = item.medication },
                        onReactivate = { viewModel.toggleMedicationActive(item.medication) },
                        onShowInfo = { medicineInfoTarget = item.medication.name },
                        onRefill = { refillTarget = item.medication }
                            .takeIf { item.medication.inventoryMode == MedicationInventoryMode.STOCK }
                            ?: {}
                    )
                    Spacer(Modifier.height(HMSpacing.sm))
                }
            }

            if (filteredRemain.isEmpty() && filteredTaken.isEmpty() && query.isBlank()) {
                HMEmptyState(emoji = "💊", title = "لا توجد أدوية", subtitle = "اضغط + لإضافة دواء جديد")
            }
            if (filteredRemain.isEmpty() && filteredTaken.isEmpty() && query.isNotBlank()) {
                HMEmptyState(emoji = "🔍", title = "لا نتائج", subtitle = "لا يوجد دواء باسم \"$query\"")
            }

            // ── Inactive ──────────────────────────────────────────────────────
            if (filteredInactive.isNotEmpty()) {
                Spacer(Modifier.height(HMSpacing.lg))
                HMSectionHeader(
                    title = "أدوية موقوفة (${filteredInactive.size})",
                    color = HMColor.TextDisabled
                )
                Spacer(Modifier.height(HMSpacing.sm))
                filteredInactive.forEach { med ->
                    val times = scheduleMap[med.id]?.ifEmpty { listOf("") } ?: listOf("")
                    MedCard(
                        item = MedItem(
                            medication = med,
                            times = times,
                            logs = times.map { null }
                        ),
                        historyLogs = medicationHistoryLogs,
                        onToggleDose = { _, _, _ -> },
                        onEdit = { editTarget = med },
                        onDelete = { deleteTarget = med },
                        onReactivate = { viewModel.toggleMedicationActive(med) },
                        onShowInfo = { medicineInfoTarget = med.name },
                        onRefill = {},
                        dimmed = true
                    )
                    Spacer(Modifier.height(HMSpacing.sm))
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }

    // ── Permission dialogs ────────────────────────────────────────────────────
    HMAlarmPermissionDialogs(
        showA14Dialog = viewModel.showA14Dialog,
        showOemDialog = viewModel.showOemDialog,
        onDismissA14 = { viewModel.showA14Dialog = false },
        onDismissOem = { viewModel.showOemDialog = false },
        onOemGranted = {                                    // ← new
            viewModel.showOemDialog = false
            needsOemPermission = false
        }
    )

    // ── Medicine info bottom sheet ─────────────────────────────────────────────
    medicineInfoTarget?.let { name ->
        MedicineInfoBottomSheet(
            medicineName = name,
            state = medicineInfoState,
            onDismiss = { medicineInfoTarget = null; aiViewModel.clearMedicineInfo() }
        )
    }

    // ── Refill dialog (STOCK mode) ─────────────────────────────────────────────
    refillTarget?.let { med ->
        RefillDialog(
            medication = med,
            onDismiss = { refillTarget = null },
            onConfirm = { newQty ->
                viewModel.refillMedication(med, newQty)
                refillTarget = null
            }
        )
    }

    // ── Add dialog ────────────────────────────────────────────────────────────
    if (showAddDialog) {
        MedEditorDialog(
            title = "إضافة دواء",
            initial = null,
            initTimes = emptyList(),
            initialName = scannedName,
            onScanRequest = {
                if (hasCameraPermission) showScanner = true
                else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onDismiss = { showAddDialog = false; scannedName = "" },
            onSave = { name, dosage, dosageFormKey, unit, freq, times, notes, days, mode, totalQty, currentQty, qtyPerDose ->
                viewModel.addMedicationWithPermissionCheck(
                    name,
                    dosage,
                    dosageFormKey,
                    unit,
                    freq,
                    times,
                    notes,
                    days,
                    mode,
                    totalQty,
                    currentQty,
                    qtyPerDose
                )
                showAddDialog = false; scannedName = ""
            }
        )
    }

    if (showNoCaseDialog) {
        AlertDialog(
            onDismissRequest = { showNoCaseDialog = false },
            title = { Text("لم تُحدَّد حالة") },
            text = { Text("يجب اختيار حالة نشطة من القائمة العلوية قبل إضافة دواء.") },
            confirmButton = { Button(onClick = { showNoCaseDialog = false }) { Text("حسناً") } }
        )
    }
    if (showNoPatientDialog) {
        AlertDialog(
            onDismissRequest = { showNoPatientDialog = false },
            title = { Text("لا يوجد مريض") },
            text = { Text("يرجى اختيار مريض أولاً من القائمة العلوية.") },
            confirmButton = { Button(onClick = { showNoPatientDialog = false }) { Text("حسناً") } }
        )
    }

    if (showScanner) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showScanner = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                decorFitsSystemWindows = false
            )
        ) {
            MedicineScannerScreen(
                onNameExtracted = { extracted ->
                    scannedName = extracted; showScanner = false
                    if (!showAddDialog && editTarget == null) showAddDialog = true
                },
                onDismiss = { showScanner = false }
            )
        }
    }

    editTarget?.let { med ->
        MedEditorDialog(
            title = "تعديل الدواء",
            initial = med,
            initTimes = scheduleMap[med.id] ?: viewModel.parseScheduledTimes(med.scheduledTimes),
            initialName = scannedName.ifBlank { med.name },
            onScanRequest = {
                if (hasCameraPermission) showScanner = true
                else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onDismiss = { editTarget = null; scannedName = "" },
            onSave = { name, dosage, dosageFormKey, unit, freq, times, notes, days, mode, totalQty, currentQty, qtyPerDose ->
                viewModel.updateMedication(
                    med,
                    name,
                    dosage,
                    dosageFormKey,
                    unit,
                    freq,
                    times,
                    notes,
                    days,
                    mode,
                    totalQty,
                    currentQty,
                    qtyPerDose
                )
                editTarget = null; scannedName = ""
            }
        )
    }

    deleteTarget?.let { med ->
        HMDialog(
            onDismiss = { deleteTarget = null },
            title = med.name,
            confirmText = "حذف نهائياً",
            onConfirm = { viewModel.deleteMedication(med); deleteTarget = null },
            dismissText = "إلغاء",
            confirmColor = HMColor.RedBright,
            confirmContentColor = Color.White
        ) {
            Text(
                "هل تريد حذف هذا الدواء نهائياً أم إيقافه مؤقتاً؟",
                fontSize = 13.sp, color = HMColor.TextSecondary
            )
            Spacer(Modifier.height(4.dp))
            HMSecondaryButton(
                text = if (med.isActive) "إيقاف مؤقت" else "إعادة تفعيل",
                onClick = { viewModel.toggleMedicationActive(med); deleteTarget = null },
                color = HMColor.AmberBright,
                leadingIcon = if (med.isActive) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Medicine Info Bottom Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicineInfoBottomSheet(
    medicineName: String,
    state: com.healthmonitor.app.ui.viewmodel.MedicineInfoUiState,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HMColor.BgElevated,
        contentColor = HMColor.TextPrimary,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = HMSpacing.md)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(HMRadius.full))
                    .background(HMColor.BorderDefault)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HMSpacing.lg)
                .padding(bottom = HMSpacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(HMSpacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
                ) {
                    Text("💊", fontSize = 22.sp)
                    Column {
                        Text(medicineName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = HMColor.TextPrimary)
                        Text("معلومات الدواء", fontSize = 11.sp, color = HMColor.GreenBright.copy(alpha = 0.7f))
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, "إغلاق", tint = HMColor.TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
            HMDivider()
            if (state.isLoading) AiLoadingCard(message = "يجمع الذكاء الاصطناعي معلومات $medicineName...")
            state.error?.let { AiErrorCard(it) }
            state.result?.let { MedicineInfoResultCard(medicineName = medicineName, result = it) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Refill Dialog — for STOCK mode medications
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RefillDialog(
    medication: MedicationEntity,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var newQtyText by remember {
        mutableStateOf(medication.totalQuantity?.let {
            if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
        } ?: "")
    }
    val newQty = newQtyText.toDoubleOrNull()
    val isValid = newQty != null && newQty > 0.0

    HMDialog(
        onDismiss = onDismiss,
        title = "تجديد مخزون ${medication.name}",
        confirmText = "تجديد",
        onConfirm = { if (isValid) onConfirm(newQty!!) },
        confirmEnabled = isValid,
        dismissText = "إلغاء",
        confirmColor = HMColor.BlueBright
    ) {
        Text(
            "أدخل الكمية الجديدة في العبوة (وحدة / حبة / مل ...)",
            fontSize = 12.sp,
            color = HMColor.TextSecondary
        )
        Spacer(Modifier.height(HMSpacing.sm))
        HMTextField(
            value = newQtyText,
            onValueChange = { newQtyText = it.filterDecimalLocal() },
            label = "الكمية الجديدة *",
            placeholder = medication.totalQuantity?.toString() ?: "30",
            keyboardType = KeyboardType.Decimal,
            leadingIcon = Icons.Outlined.Inventory2
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MedCard — ONE card per medication, all scheduled times grouped inside
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MedCard(
    item: MedItem,
    historyLogs: List<MedicationLogEntity>,
    onToggleDose: (String, String, Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReactivate: () -> Unit,
    onShowInfo: () -> Unit,
    onRefill: () -> Unit,
    dimmed: Boolean = false
) {
    val med = item.medication
    val alpha = if (dimmed) 0.45f else 1f
    val medLogs = historyLogs.filter { it.medicationId == med.id }
    val status = calculateMedicationInventoryStatus(
        medication = med,
        allLogsForMedication = medLogs,
        scheduledDoseCount = item.times.size.coerceAtLeast(1)
    )

    val bgColor = when {
        dimmed -> HMColor.BgBase
        item.allTaken -> HMColor.GreenBg
        else -> HMColor.BgSurface
    }
    val borderColor = when {
        dimmed -> HMColor.BorderSubtle
        item.allTaken -> HMColor.GreenBorder
        item.partiallyTaken -> HMColor.AmberBorder
        else -> HMColor.BorderDefault
    }

    HMCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = bgColor,
        borderColor = borderColor
    ) {
        // ── Header row: name + badge + actions ────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Name + dosage badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        med.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HMColor.TextPrimary.copy(alpha = alpha)
                    )
                    HMBadge(
                        text = "${med.dosage} ${med.unit}",
                        color = HMColor.GreenBright.copy(alpha = alpha),
                        backgroundColor = HMColor.GreenBright.copy(alpha = 0.1f * alpha)
                    )
                    // Mode badge
                    ModeBadge(mode = med.inventoryMode, alpha = alpha)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    FrequencyType.label(med.frequency),
                    fontSize = 11.sp,
                    color = HMColor.TextDisabled.copy(alpha = alpha)
                )
            }

            // Actions column
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                IconButton(onClick = onShowInfo, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Info, "معلومات الدواء",
                        tint = HMColor.GreenBright.copy(alpha = alpha), modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Edit, "تعديل",
                        tint = HMColor.BlueBright.copy(alpha = alpha), modifier = Modifier.size(16.dp)
                    )
                }
                if (dimmed) {
                    IconButton(onClick = onReactivate, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.PlayCircle, "إعادة تفعيل",
                            tint = HMColor.GreenBright, modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Outlined.Delete, "حذف",
                            tint = HMColor.RedBright.copy(alpha = alpha), modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        if (dimmed) {
            Spacer(Modifier.height(HMSpacing.sm))
            HMPrimaryButton(
                text = "إعادة استخدام الدواء",
                onClick = onReactivate,
                leadingIcon = Icons.Default.PlayCircle,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ── Scheduled dose times — one row per time with individual checkbox ───
        if (!dimmed && item.times.isNotEmpty()) {
            Spacer(Modifier.height(HMSpacing.sm))
            HMDivider()
            Spacer(Modifier.height(HMSpacing.sm))
            item.times.forEachIndexed { idx, time ->
                val log = item.logs.getOrNull(idx)
                val isTaken = log?.taken == true
                DoseTimeRow(
                    time = time,
                    isTaken = isTaken,
                    alpha = alpha,
                    onToggle = { onToggleDose(med.id, time, !isTaken) }
                )
                if (idx < item.times.lastIndex)
                    Spacer(Modifier.height(4.dp))
            }
        }

        // ── Inventory status section ───────────────────────────────────────────
        Spacer(Modifier.height(HMSpacing.sm))
        HMDivider()
        Spacer(Modifier.height(HMSpacing.sm))
        InventoryStatusSection(
            status = status,
            med = med,
            alpha = alpha,
            onRefill = onRefill
        )

        // Notes
        if (!med.notes.isNullOrBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(med.notes, fontSize = 11.sp, color = HMColor.TextDisabled.copy(alpha = alpha))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DoseTimeRow — one checkbox row per scheduled time
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DoseTimeRow(
    time: String,
    isTaken: Boolean,
    alpha: Float,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HMRadius.xs))
            .background(
                if (isTaken) HMColor.GreenBright.copy(alpha = 0.07f)
                else HMColor.BgOverlay.copy(alpha = 0.5f)
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = HMSpacing.sm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
    ) {
        Checkbox(
            checked = isTaken,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = HMColor.GreenBright,
                uncheckedColor = HMColor.TextSecondary,
                checkmarkColor = HMColor.TextInverse
            ),
            modifier = Modifier.size(24.dp)
        )
        Icon(
            Icons.Outlined.Schedule, null,
            tint = if (isTaken) HMColor.GreenBright else HMColor.TextSecondary.copy(alpha = alpha),
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = if (time.isBlank()) "أي وقت" else format12Hour(time),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (isTaken) HMColor.GreenBright else HMColor.TextPrimary.copy(alpha = alpha),
            modifier = Modifier.weight(1f)
        )
        if (isTaken) {
            Text("✓ تم", fontSize = 10.sp, color = HMColor.GreenBright, fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// InventoryStatusSection — mode-aware status display
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InventoryStatusSection(
    status: MedicationInventoryStatus,
    med: MedicationEntity,
    alpha: Float,
    onRefill: () -> Unit
) {
    when (med.inventoryMode) {

        // ── CHRONIC: show adherence streak ────────────────────────────────────
        MedicationInventoryMode.CHRONIC -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("🔥", fontSize = 14.sp)
                    Column {
                        Text(
                            "سلسلة الانتظام",
                            fontSize = 10.sp,
                            color = HMColor.TextDisabled.copy(alpha = alpha)
                        )
                        Text(
                            "${status.adherenceStreakDays} يوم متواصل",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                status.adherenceStreakDays >= 30 -> HMColor.GreenBright
                                status.adherenceStreakDays >= 7 -> HMColor.AmberBright
                                else -> HMColor.TextSecondary
                            }.copy(alpha = alpha)
                        )
                    }
                }
                status.lastTakenAt?.let {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("آخر جرعة", fontSize = 10.sp, color = HMColor.TextDisabled.copy(alpha = alpha))
                        Text(formatTimestamp(it), fontSize = 10.sp, color = HMColor.TextDisabled.copy(alpha = alpha))
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                "إجمالي الجرعات المأخوذة: ${status.totalTakenDoses}",
                fontSize = 10.sp,
                color = HMColor.TextDisabled.copy(alpha = alpha)
            )
        }

        // ── STOCK: quantity bar + low-stock warning + refill button ───────────
        MedicationInventoryMode.STOCK -> {
            // Low stock warning banner
            if (status.isLowStock) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(HMRadius.xs))
                        .background(HMColor.AmberBright.copy(alpha = 0.1f))
                        .padding(horizontal = HMSpacing.sm, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.WarningAmber, null,
                        tint = HMColor.AmberBright, modifier = Modifier.size(12.dp)
                    )
                    Text(
                        "مخزون منخفض — ${status.remainingDays ?: 0} يوم متبق تقريباً",
                        fontSize = 10.sp, color = HMColor.AmberBright, fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            // Progress bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                HMProgressBar(
                    progress = status.progress,
                    height = 4.dp,
                    progressColor = if (status.isLowStock) HMColor.AmberBright else HMColor.BlueBright,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${status.remainingDoses ?: 0} جرعة",
                    fontSize = 10.sp,
                    color = HMColor.TextDisabled.copy(alpha = alpha)
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    status.lastTakenAt?.let {
                        Text(
                            "آخر جرعة: ${formatTimestamp(it)}",
                            fontSize = 10.sp,
                            color = HMColor.TextDisabled.copy(alpha = alpha)
                        )
                    }
                    status.remainingDays?.let {
                        Text(
                            "يكفي لـ $it يوم تقريباً",
                            fontSize = 10.sp,
                            color = HMColor.TextDisabled.copy(alpha = alpha)
                        )
                    }
                }
                // Refill button
                HMPressable(onClick = onRefill) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(HMRadius.xs))
                            .background(HMColor.BlueBright.copy(alpha = 0.12f))
                            .border(1.dp, HMColor.BlueBright.copy(alpha = 0.35f), RoundedCornerShape(HMRadius.xs))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Inventory2, null,
                                tint = HMColor.BlueBright, modifier = Modifier.size(11.dp)
                            )
                            Text(
                                "تجديد",
                                fontSize = 11.sp,
                                color = HMColor.BlueBright,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // ── COURSE (default): days progress bar ───────────────────────────────
        else -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                HMProgressBar(
                    progress = status.progress,
                    height = 4.dp,
                    progressColor = HMColor.BlueBright.copy(alpha = alpha),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${status.remainingDays ?: 0} يوم",
                    fontSize = 10.sp, color = HMColor.TextDisabled.copy(alpha = alpha)
                )
                Text(
                    "${status.remainingDoses ?: 0} جرعة",
                    fontSize = 10.sp, color = HMColor.TextDisabled.copy(alpha = alpha)
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                status.lastTakenAt?.let {
                    Text(
                        "آخر جرعة: ${formatTimestamp(it)}",
                        fontSize = 10.sp,
                        color = HMColor.TextDisabled.copy(alpha = alpha)
                    )
                }
                status.estimatedCompletionDate?.let {
                    Text("ينتهي: ${formatDate(it)}", fontSize = 10.sp, color = HMColor.TextDisabled.copy(alpha = alpha))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mode badge
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ModeBadge(mode: String, alpha: Float) {
    val (label, color) = when (mode) {
        MedicationInventoryMode.CHRONIC -> "علاج مستمر" to HMColor.CyanBright
        MedicationInventoryMode.STOCK -> "مخزون" to HMColor.BlueBright
        else -> return
    }
    HMBadge(
        text = label,
        color = color.copy(alpha = alpha),
        backgroundColor = color.copy(alpha = 0.1f * alpha)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared card composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NoCaseBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HMRadius.md))
            .background(
                Brush.horizontalGradient(
                    listOf(HMColor.AmberBright.copy(alpha = 0.18f), HMColor.AmberBright.copy(alpha = 0.08f))
                )
            )
            .border(1.dp, HMColor.AmberBright.copy(alpha = 0.35f), RoundedCornerShape(HMRadius.md))
            .padding(horizontal = HMSpacing.md, vertical = HMSpacing.sm)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
        ) {
            Icon(Icons.Default.FolderOpen, null, tint = HMColor.AmberBright, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "لم تُحدَّد حالة نشطة",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HMColor.AmberBright
                )
                Text(
                    "اختر حالة من القائمة العلوية لإضافة أو متابعة الأدوية.",
                    fontSize = 11.sp, color = HMColor.AmberBright.copy(alpha = 0.75f), lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun DailyProgressCard(taken: Int, total: Int, progress: Float) {
    HMCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (taken == total && total > 0) HMColor.GreenBorder else HMColor.BorderDefault,
        backgroundColor = if (taken == total && total > 0) HMColor.GreenBg else HMColor.BgSurface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("متابعة اليوم", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HMColor.TextPrimary)
            Text(
                "$taken / $total جرعة", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = if (taken == total && total > 0) HMColor.GreenBright else HMColor.AmberBright
            )
        }
        Spacer(Modifier.height(HMSpacing.sm))
        HMProgressBar(
            progress = progress,
            progressColor = if (taken == total && total > 0) HMColor.GreenBright else HMColor.AmberBright
        )
        if (taken == total && total > 0) {
            Spacer(Modifier.height(HMSpacing.sm))
            Text("🎉 أحسنت! أخذت جميع الجرعات اليوم.", fontSize = 12.sp, color = HMColor.GreenBright)
        }
    }
}

@Composable
private fun MedicationDateNavigator(dateMillis: Long, onPrevious: () -> Unit, onNext: () -> Unit) {
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
                    formatDate(dateMillis),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HMColor.TextPrimary
                )
                Text("سجل الجرعات", fontSize = 10.sp, color = HMColor.TextDisabled)
            }
            IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, "اليوم التالي", tint = HMColor.TextSecondary)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MedEditorDialog — 3-mode aware
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MedEditorDialog(
    title: String,
    initial: MedicationEntity?,
    initTimes: List<String>,
    initialName: String = "",
    onScanRequest: () -> Unit = {},
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, List<String>, String?, Int, String, Double?, Double?, Double) -> Unit
) {
    // ── State ─────────────────────────────────────────────────────────────────
    var name by remember { mutableStateOf(initialName.ifBlank { initial?.name ?: "" }) }
    var dosage by remember { mutableStateOf(initial?.dosage ?: "") }
    // شكل الدواء — بنخمنه من الـ unit المحفوظة لو دواء موجود
    var selectedForm by remember {
        mutableStateOf(
            DosageFormType.fromKey(initial?.dosageFormKey)
                ?: DosageFormType.guessFromUnit(initial?.unit ?: "mg")
        )
    }
    var unit by remember {
        mutableStateOf(initial?.unit ?: DosageFormType.TABLET.defaultUnit)
    }
    var frequency by remember { mutableStateOf(initial?.frequency ?: FrequencyType.ONCE_DAILY) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var durationDays by remember { mutableStateOf((initial?.durationDays ?: 7).toString()) }
    var inventoryMode by remember { mutableStateOf(initial?.inventoryMode ?: MedicationInventoryMode.COURSE) }
    var totalQuantity by remember { mutableStateOf(initial?.totalQuantity?.formatQuantityLocal() ?: "") }
    var currentQuantity by remember { mutableStateOf(initial?.currentQuantity?.formatQuantityLocal() ?: totalQuantity) }
    var quantityPerDose by remember { mutableStateOf(initial?.quantityPerDose?.formatQuantityLocal() ?: "1") }
    var times by remember { mutableStateOf(initTimes.toMutableList()) }
    var showPicker by remember { mutableStateOf(false) }
    var showSuggestion by remember { mutableStateOf(false) }
    var suggestedTimes by remember { mutableStateOf<List<String>>(emptyList()) }
    val timeState = rememberTimePickerState(8, 0, false)

    // ── Validation ────────────────────────────────────────────────────────────
    val stockValid = inventoryMode != MedicationInventoryMode.STOCK ||
            ((currentQuantity.toDoubleOrNull() ?: -1.0) >= 0.0 &&
                    (quantityPerDose.toDoubleOrNull() ?: 0.0) > 0.0)
    val courseValid = inventoryMode != MedicationInventoryMode.COURSE ||
            (durationDays.toIntOrNull() ?: 0) > 0
    val isValid = name.isNotBlank() && dosage.isNotBlank() &&
            (frequency == FrequencyType.AS_NEEDED || times.isNotEmpty()) &&
            stockValid && courseValid

    LaunchedEffect(initialName) { if (initialName.isNotBlank()) name = initialName }

    // ── Time picker dialog ─────────────────────────────────────────────────────
    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            containerColor = HMColor.BgElevated,
            title = { Text("اختر الوقت", color = HMColor.TextPrimary) },
            text = {
                TimePicker(
                    state = timeState,
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
                HMPressable(onClick = {
                    val t = "%02d:%02d".format(timeState.hour, timeState.minute)
                    if (t !in times) {
                        times = (times + t).toMutableList()
                        // Show suggestion dialog if:
                        // 1. User has selected a frequency > 1
                        // 2. This is the first time being added
                        // 3. There aren't enough times yet
                        val freqCount = getFrequencyCount(frequency)
                        if (times.size == 1 && freqCount > 1) {
                            val suggested = calculateSuggestedTimes(t, frequency)
                            if (suggested.isNotEmpty()) {
                                suggestedTimes = suggested
                                showSuggestion = true
                            }
                        }
                    }
                    showPicker = false
                }) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(HMRadius.sm))
                            .background(HMColor.GreenBright)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "تأكيد",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HMColor.TextInverse
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("إلغاء", color = HMColor.TextSecondary)
                }
            },
            shape = RoundedCornerShape(HMRadius.lg)
        )
    }

    // ── Suggestion dialog ──────────────────────────────────────────────────────
    if (showSuggestion && suggestedTimes.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showSuggestion = false },
            containerColor = HMColor.BgElevated,
            title = {
                Text("اقتراح المواعيد", color = HMColor.TextPrimary, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(HMSpacing.md)) {
                    val freqLabel = when (frequency) {
                        FrequencyType.TWICE_DAILY -> "مرتين يومياً"
                        FrequencyType.THREE_TIMES_DAILY -> "3 مرات يومياً"
                        FrequencyType.FOUR_TIMES_DAILY -> "4 مرات يومياً"
                        FrequencyType.EVERY_8_HOURS -> "كل 8 ساعات"
                        FrequencyType.EVERY_12_HOURS -> "كل 12 ساعة"
                        else -> "مرات متعددة يومياً"
                    }
                    Text(
                        "بناءً على اختيارك $freqLabel، يقترح البرنامج المواعيد التالية:",
                        fontSize = 13.sp,
                        color = HMColor.TextSecondary,
                        lineHeight = 18.sp
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(HMSpacing.sm)) {
                        suggestedTimes.forEach { suggestedTime ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(HMRadius.xs))
                                    .background(HMColor.GreenBright.copy(alpha = 0.08f))
                                    .border(
                                        1.dp,
                                        HMColor.GreenBright.copy(alpha = 0.3f),
                                        RoundedCornerShape(HMRadius.xs)
                                    )
                                    .padding(HMSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
                            ) {
                                Icon(
                                    Icons.Outlined.Schedule,
                                    null,
                                    tint = HMColor.GreenBright,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    format12Hour(suggestedTime),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = HMColor.GreenBright
                                )
                            }
                        }
                    }
                    Text(
                        "يمكنك قبول هذه المواعيد أو إدخالها يدويًا.",
                        fontSize = 12.sp,
                        color = HMColor.TextDisabled
                    )
                }
            },
            confirmButton = {
                HMPressable(onClick = {
                    // Add all suggested times
                    times = (times + suggestedTimes).distinct().toMutableList()
                    showSuggestion = false
                    suggestedTimes = emptyList()
                }) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(HMRadius.sm))
                            .background(HMColor.GreenBright)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "قبول المواعيد",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HMColor.TextInverse
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSuggestion = false
                    suggestedTimes = emptyList()
                }) {
                    Text("الإدخال اليدوي", color = HMColor.TextSecondary)
                }
            },
            shape = RoundedCornerShape(HMRadius.lg)
        )
    }

    // ── Main dialog ───────────────────────────────────────────────────────────
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HMColor.BgElevated,
        titleContentColor = HMColor.TextPrimary,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        shape = RoundedCornerShape(HMRadius.lg),
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(HMSpacing.md)
            ) {
                // ── اسم الدواء + كاميرا ────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
                ) {
                    HMTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "اسم الدواء *",
                        placeholder = "مثال: Norvasc",
                        leadingIcon = Icons.Outlined.LocalPharmacy,
                        modifier = Modifier.weight(1f)
                    )
                    HMPressable(onClick = onScanRequest, modifier = Modifier.padding(top = 6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(HMRadius.sm))
                                .background(HMColor.GreenBright.copy(alpha = 0.12f))
                                .border(1.dp, HMColor.GreenBorder, RoundedCornerShape(HMRadius.sm)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                null,
                                tint = HMColor.GreenBright,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // ── شكل الدواء + الوحدة + الكمية (الجديد) ─────────────────────
                DosageInputSection(
                    dosage = dosage,
                    onDosageChange = { dosage = it.filterDecimalLocal() },
                    selectedForm = selectedForm,
                    onFormChange = { form ->
                        selectedForm = form
                        unit = form.defaultUnit
                    },
                    selectedUnit = unit,
                    onUnitChange = { unit = it }
                )

                // ── نوع المتابعة ────────────────────────────────────────────────
                HMSectionHeader("نوع المتابعة")
                InventoryModeSelector(selected = inventoryMode, onSelect = { inventoryMode = it })

                // ── حقول خاصة بكل mode ─────────────────────────────────────────
                AnimatedVisibility(visible = inventoryMode == MedicationInventoryMode.COURSE) {
                    HMTextField(
                        value = durationDays,
                        onValueChange = { durationDays = it.filter(Char::isDigit) },
                        label = "مدة العلاج (أيام) *",
                        placeholder = "7",
                        keyboardType = KeyboardType.Number,
                        leadingIcon = Icons.Outlined.DateRange
                    )
                }

                AnimatedVisibility(visible = inventoryMode == MedicationInventoryMode.STOCK) {
                    Column(verticalArrangement = Arrangement.spacedBy(HMSpacing.sm)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)) {
                            HMTextField(
                                value = currentQuantity,
                                onValueChange = { currentQuantity = it.filterDecimalLocal() },
                                label = "المخزون الحالي",
                                placeholder = "30",
                                keyboardType = KeyboardType.Decimal,
                                modifier = Modifier.weight(1f)
                            )
                            HMTextField(
                                value = quantityPerDose,
                                onValueChange = { quantityPerDose = it.filterDecimalLocal() },
                                label = "لكل جرعة",
                                placeholder = "1",
                                keyboardType = KeyboardType.Decimal,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        HMTextField(
                            value = totalQuantity,
                            onValueChange = { totalQuantity = it.filterDecimalLocal() },
                            label = "إجمالي العبوة (اختياري)",
                            placeholder = "30",
                            keyboardType = KeyboardType.Decimal
                        )
                    }
                }

                AnimatedVisibility(visible = inventoryMode == MedicationInventoryMode.CHRONIC) {
                    HMCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = HMColor.CyanBright.copy(alpha = 0.3f),
                        backgroundColor = HMColor.BgOverlay
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
                        ) {
                            Text("♾️", fontSize = 18.sp)
                            Column {
                                Text(
                                    "علاج مستمر",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = HMColor.CyanBright
                                )
                                Text(
                                    "لا نهاية محددة — يستمر حتى يوقفه الطبيب أو المريض",
                                    fontSize = 11.sp,
                                    color = HMColor.TextSecondary,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                // ── التكرار ────────────────────────────────────────────────────
                HMSectionHeader("التكرار")
                FrequencySelector(selected = frequency, onSelect = { frequency = it })

                // ── مواعيد الجرعات ─────────────────────────────────────────────
                HMSectionHeader("مواعيد الجرعات *")
                if (times.isEmpty()) {
                    Text("لم يُضف أي وقت بعد.", fontSize = 12.sp, color = HMColor.TextDisabled)
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        times.forEach { t ->
                            TimeChip(
                                time = t,
                                onRemove = {
                                    times = times.toMutableList().also { m -> m.remove(t) }
                                }
                            )
                        }
                    }
                }
                HMSecondaryButton(
                    text = "إضافة وقت",
                    onClick = { showPicker = true },
                    leadingIcon = Icons.Outlined.Schedule,
                    modifier = Modifier.fillMaxWidth()
                )

                // ── ملاحظات ────────────────────────────────────────────────────
                HMTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "ملاحظات (اختياري)",
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            HMPressable(
                onClick = {
                    if (isValid) onSave(
                        name.trim(),
                        dosage.trim(),
                        selectedForm.key,
                        unit.trim(),
                        frequency,
                        times.toList(),
                        notes.trim().ifBlank { null },
                        if (inventoryMode == MedicationInventoryMode.CHRONIC) Int.MAX_VALUE
                        else durationDays.toIntOrNull() ?: 7,
                        inventoryMode,
                        totalQuantity.toDoubleOrNull(),
                        currentQuantity.toDoubleOrNull(),
                        quantityPerDose.toDoubleOrNull() ?: 1.0
                    )
                },
                enabled = isValid
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(HMRadius.sm))
                        .background(if (isValid) HMColor.GreenBright else HMColor.BgOverlay)
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        "حفظ",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = if (isValid) HMColor.TextInverse else HMColor.TextDisabled
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
// TimeChip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TimeChip(time: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(HMRadius.full))
            .background(HMColor.GreenBright.copy(alpha = 0.12f))
            .border(1.dp, HMColor.GreenBorder, RoundedCornerShape(HMRadius.full))
            .padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(format12Hour(time), fontSize = 12.sp, color = HMColor.GreenBright, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(4.dp))
        HMPressable(onClick = onRemove) {
            Icon(
                Icons.Default.Close,
                "حذف",
                tint = HMColor.GreenBright.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FrequencySelector
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FrequencySelector(selected: String, onSelect: (String) -> Unit) {
    val options = listOf(
        FrequencyType.ONCE_DAILY to "مرة", FrequencyType.TWICE_DAILY to "مرتين",
        FrequencyType.THREE_TIMES_DAILY to "3×", FrequencyType.FOUR_TIMES_DAILY to "4×",
        FrequencyType.AS_NEEDED to "عند الحاجة"
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
        options.forEach { (freq, label) ->
            val isSel = selected == freq
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(HMRadius.xs))
                    .background(if (isSel) HMColor.GreenBright.copy(alpha = 0.15f) else Color.Transparent)
                    .then(
                        if (isSel) Modifier.border(
                            1.dp,
                            HMColor.GreenBorder,
                            RoundedCornerShape(HMRadius.xs)
                        ) else Modifier
                    )
                    .clickable { onSelect(freq) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label, fontSize = 11.sp,
                    color = if (isSel) HMColor.GreenBright else HMColor.TextSecondary,
                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// InventoryModeSelector — 3 modes
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InventoryModeSelector(selected: String, onSelect: (String) -> Unit) {
    val options = listOf(
        MedicationInventoryMode.COURSE to Triple("📅", "كورس", HMColor.GreenBright),
        MedicationInventoryMode.STOCK to Triple("📦", "مخزون", HMColor.BlueBright),
        MedicationInventoryMode.CHRONIC to Triple("♾️", "علاج مستمر", HMColor.CyanBright)
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (mode, triple) ->
            val (emoji, label, color) = triple
            val isSel = selected == mode
            HMPressable(onClick = { onSelect(mode) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(HMRadius.sm))
                        .background(if (isSel) color.copy(alpha = 0.1f) else HMColor.BgOverlay)
                        .border(
                            1.dp,
                            if (isSel) color.copy(alpha = 0.4f) else HMColor.BorderSubtle,
                            RoundedCornerShape(HMRadius.sm)
                        )
                        .padding(horizontal = HMSpacing.md, vertical = HMSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
                ) {
                    Text(emoji, fontSize = 16.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = if (isSel) color else HMColor.TextPrimary
                        )
                        Text(
                            MedicationInventoryMode.description(mode), fontSize = 10.sp,
                            color = HMColor.TextSecondary, lineHeight = 14.sp
                        )
                    }
                    if (isSel) {
                        Icon(Icons.Default.CheckCircle, null, tint = color, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun normalizeTime(v: String): String = v.ifBlank { "99:99" }
private fun formatDate(ts: Long): String = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ts))
private fun formatTimestamp(ts: Long): String = SimpleDateFormat("d MMM  hh:mm a", Locale.getDefault()).format(Date(ts))
private fun String.filterDecimalLocal(): String =
    filterIndexed { i, c -> c.isDigit() || (c == '.' && indexOf('.') == i) }

private fun Double.formatQuantityLocal(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()

/**
 * Get the number of times per day for a frequency type.
 */
private fun getFrequencyCount(frequency: String): Int = when (frequency) {
    FrequencyType.ONCE_DAILY -> 1
    FrequencyType.TWICE_DAILY -> 2
    FrequencyType.THREE_TIMES_DAILY -> 3
    FrequencyType.FOUR_TIMES_DAILY -> 4
    FrequencyType.EVERY_8_HOURS -> 3  // 24 / 8 = 3
    FrequencyType.EVERY_12_HOURS -> 2  // 24 / 12 = 2
    else -> 1  // AS_NEEDED, WEEKLY, BIWEEKLY, etc.
}

/**
 * Calculate suggested times based on the first time and frequency.
 * Returns a list of times (HH:mm format) in 24-hour time.
 */
private fun calculateSuggestedTimes(firstTime: String, frequency: String): List<String> {
    val count = getFrequencyCount(frequency)
    if (count <= 1) return emptyList()

    val parts = firstTime.split(":").map { it.toIntOrNull() ?: 0 }
    val startHour = parts.getOrNull(0) ?: 0
    val startMinute = parts.getOrNull(1) ?: 0

    val intervalHours = 24.0 / count
    val suggested = mutableListOf(firstTime)

    repeat(count - 1) { i ->
        // Calculate total minutes from the start time
        val totalMinutes = (startHour * 60 + startMinute + (intervalHours * 60 * (i + 1)).toInt()) % (24 * 60)
        val hour = (totalMinutes / 60) % 24
        val minute = totalMinutes % 60
        suggested.add("%02d:%02d".format(hour, minute))
    }

    return suggested.distinct()
}