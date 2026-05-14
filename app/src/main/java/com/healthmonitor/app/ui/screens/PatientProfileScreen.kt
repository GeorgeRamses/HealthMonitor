package com.healthmonitor.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.healthmonitor.app.data.local.entities.PatientEntity
import com.healthmonitor.app.ui.design.*
import com.healthmonitor.app.ui.viewmodel.PatientViewModel
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────────────────────
// Patient Profile Screen
//
// Shown when the user taps the edit icon on a patient card.
// Displays all patient fields in a clean read-only view, with an Edit button
// that switches to an inline edit mode for all fields.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PatientProfileScreen(
    patientId: String,
    navController: NavHostController,
    viewModel: PatientViewModel = hiltViewModel()
) {
    // Load the patient when the screen opens
    LaunchedEffect(patientId) { viewModel.loadPatient(patientId) }

    val patient by viewModel.selectedPatient.collectAsState()

    if (patient == null) {
        // Loading state
        Box(
            modifier         = Modifier.fillMaxSize().background(HMColor.BgBase),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = HMColor.GreenBright)
        }
        return
    }

    PatientProfileContent(
        patient      = patient!!,
        navController = navController,
        onSave       = { name, age, gender, bloodType, conditions, contact, phone ->
            viewModel.updatePatient(
                patient          = patient!!,
                name             = name,
                age              = age,
                gender           = gender,
                bloodType        = bloodType,
                medicalConditions = conditions,
                emergencyContact = contact,
                emergencyPhone   = phone
            )
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Profile content — handles view / edit toggle
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PatientProfileContent(
    patient: PatientEntity,
    navController: NavHostController,
    onSave: (String, Int, String, String?, String, String?, String?) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }

    // Edit form state — initialised from the current patient
    var name             by remember(patient) { mutableStateOf(patient.name) }
    var age              by remember(patient) { mutableStateOf(patient.age.takeIf { it > 0 }?.toString() ?: "") }
    var gender           by remember(patient) { mutableStateOf(patient.gender.ifBlank { "ذكر" }) }
    var bloodType        by remember(patient) { mutableStateOf(patient.bloodType ?: "") }
    var conditions       by remember(patient) { mutableStateOf(patient.medicalConditions) }
    var emergencyContact by remember(patient) { mutableStateOf(patient.emergencyContact ?: "") }
    var emergencyPhone   by remember(patient) { mutableStateOf(patient.emergencyPhone ?: "") }

    val canSave = name.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HMColor.BgBase)
    ) {
        // ── Header ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(HMColor.BgSurface, HMColor.BgBase)))
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
                        horizontalArrangement = Arrangement.spacedBy(HMSpacing.md)
                    ) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack, "رجوع",
                                tint = HMColor.TextSecondary
                            )
                        }
                        Column {
                            Text(
                                if (isEditing) "تعديل الملف الطبي" else "الملف الطبي",
                                fontSize   = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color      = HMColor.TextPrimary
                            )
                            Text(
                                patient.name,
                                fontSize = 11.sp,
                                color    = HMColor.TextSecondary
                            )
                        }
                    }
                    // Edit / Cancel toggle button
                    HMPressable(onClick = {
                        if (isEditing) {
                            // Cancel — reset fields
                            name             = patient.name
                            age              = patient.age.takeIf { it > 0 }?.toString() ?: ""
                            gender           = patient.gender.ifBlank { "ذكر" }
                            bloodType        = patient.bloodType ?: ""
                            conditions       = patient.medicalConditions
                            emergencyContact = patient.emergencyContact ?: ""
                            emergencyPhone   = patient.emergencyPhone ?: ""
                        }
                        isEditing = !isEditing
                    }) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(HMRadius.sm))
                                .background(
                                    if (isEditing) HMColor.RedBright.copy(alpha = 0.1f)
                                    else HMColor.GreenBright.copy(alpha = 0.1f)
                                )
                                .border(
                                    1.dp,
                                    if (isEditing) HMColor.RedBright.copy(alpha = 0.4f)
                                    else HMColor.GreenBorder,
                                    RoundedCornerShape(HMRadius.sm)
                                )
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    if (isEditing) Icons.Default.Close else Icons.Outlined.Edit,
                                    null,
                                    tint     = if (isEditing) HMColor.RedBright else HMColor.GreenBright,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    if (isEditing) "إلغاء" else "تعديل",
                                    fontSize   = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = if (isEditing) HMColor.RedBright else HMColor.GreenBright
                                )
                            }
                        }
                    }
                }

                // Avatar row
                Spacer(Modifier.height(HMSpacing.lg))
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HMSpacing.md)
                ) {
                    HMAvatar(
                        initials        = patient.name.take(2),
                        size            = 64.dp,
                        backgroundColor = HMColor.GreenBg,
                        textColor       = HMColor.GreenBright
                    )
                    Column {
                        Text(
                            patient.name,
                            fontSize   = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color      = HMColor.TextPrimary
                        )
                        Text(
                            "مريض منذ ${formatDate(patient.createdAt)}",
                            fontSize = 11.sp,
                            color    = HMColor.TextSecondary
                        )
                    }
                }
            }
        }

        // ── Scrollable body ───────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(HMSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(HMSpacing.md)
        ) {
            // Success banner
            AnimatedVisibility(
                visible = showSuccess,
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically()
            ) {
                HMCard(
                    modifier        = Modifier.fillMaxWidth(),
                    borderColor     = HMColor.GreenBorder,
                    backgroundColor = HMColor.GreenBg
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle, null,
                            tint     = HMColor.GreenBright,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "تم حفظ الملف الطبي بنجاح",
                            fontSize   = 13.sp,
                            color      = HMColor.GreenBright,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            AnimatedContent(
                targetState = isEditing,
                transitionSpec = {
                    fadeIn(androidx.compose.animation.core.tween(200)) togetherWith
                            fadeOut(androidx.compose.animation.core.tween(150))
                },
                label = "profile_mode"
            ) { editing ->
                if (editing) {
                    // ── Edit mode ─────────────────────────────────────────
                    EditForm(
                        name             = name,
                        age              = age,
                        gender           = gender,
                        bloodType        = bloodType,
                        conditions       = conditions,
                        emergencyContact = emergencyContact,
                        emergencyPhone   = emergencyPhone,
                        onNameChange     = { name = it },
                        onAgeChange      = { age = it.filter(Char::isDigit) },
                        onGenderChange   = { gender = it },
                        onBloodTypeChange = { bloodType = it },
                        onConditionsChange = { conditions = it },
                        onContactChange  = { emergencyContact = it },
                        onPhoneChange    = { emergencyPhone = it }
                    )
                } else {
                    // ── View mode ─────────────────────────────────────────
                    ViewProfile(patient = patient)
                }
            }

            // Save button — only in edit mode
            AnimatedVisibility(
                visible = isEditing,
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically()
            ) {
                HMPrimaryButton(
                    text        = "حفظ التغييرات",
                    onClick     = {
                        onSave(
                            name,
                            age.toIntOrNull() ?: 0,
                            gender,
                            bloodType.ifBlank { null },
                            conditions,
                            emergencyContact.ifBlank { null },
                            emergencyPhone.ifBlank { null }
                        )
                        isEditing   = false
                        showSuccess = true
                    },
                    enabled     = canSave,
                    leadingIcon = Icons.Default.Save,
                    modifier    = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(HMSpacing.xxxl))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// View mode — read-only info cards
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ViewProfile(patient: PatientEntity) {
    Column(verticalArrangement = Arrangement.spacedBy(HMSpacing.md)) {

        // ── Personal info ─────────────────────────────────────────────────
        ProfileSection(title = "البيانات الشخصية", icon = Icons.Outlined.Person) {
            ProfileInfoRow("الاسم",    patient.name,              Icons.Outlined.Person)
            if (patient.age > 0)
                ProfileInfoRow("العمر", "${patient.age} سنة",    Icons.Outlined.Cake)
            if (patient.gender.isNotBlank())
                ProfileInfoRow("الجنس", patient.gender,           Icons.Outlined.Wc)
            if (!patient.bloodType.isNullOrBlank())
                ProfileInfoRow("فصيلة الدم", patient.bloodType!!, Icons.Outlined.Bloodtype)
        }

        // ── Medical conditions ────────────────────────────────────────────
        if (patient.medicalConditions.isNotBlank()) {
            ProfileSection(title = "الحالات الطبية", icon = Icons.Outlined.MedicalServices) {
                Text(
                    patient.medicalConditions,
                    fontSize   = 13.sp,
                    color      = HMColor.TextPrimary,
                    lineHeight = 20.sp
                )
            }
        } else {
            ProfileSection(title = "الحالات الطبية", icon = Icons.Outlined.MedicalServices) {
                Text(
                    "لم تُسجَّل حالات طبية",
                    fontSize = 13.sp,
                    color    = HMColor.TextDisabled
                )
            }
        }

        // ── Emergency contact ─────────────────────────────────────────────
        if (!patient.emergencyContact.isNullOrBlank() || !patient.emergencyPhone.isNullOrBlank()) {
            ProfileSection(title = "جهة الطوارئ", icon = Icons.Outlined.Emergency) {
                patient.emergencyContact?.let {
                    ProfileInfoRow("الاسم", it, Icons.Outlined.Person)
                }
                patient.emergencyPhone?.let {
                    ProfileInfoRow("الهاتف", it, Icons.Outlined.Phone)
                }
            }
        } else {
            ProfileSection(title = "جهة الطوارئ", icon = Icons.Outlined.Emergency) {
                Text(
                    "لم تُسجَّل جهة طوارئ",
                    fontSize = 13.sp,
                    color    = HMColor.TextDisabled
                )
            }
        }

        // ── Meta ──────────────────────────────────────────────────────────
        ProfileSection(title = "معلومات السجل", icon = Icons.Outlined.Info) {
            ProfileInfoRow("تاريخ الإنشاء",     formatDate(patient.createdAt),      Icons.Outlined.CalendarToday)
            ProfileInfoRow("آخر تعديل",          formatDate(patient.lastModifiedAt), Icons.Outlined.Update)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Edit mode — form fields
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditForm(
    name: String,             onNameChange: (String) -> Unit,
    age: String,              onAgeChange: (String) -> Unit,
    gender: String,           onGenderChange: (String) -> Unit,
    bloodType: String,        onBloodTypeChange: (String) -> Unit,
    conditions: String,       onConditionsChange: (String) -> Unit,
    emergencyContact: String, onContactChange: (String) -> Unit,
    emergencyPhone: String,   onPhoneChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(HMSpacing.md)) {

        // ── Personal info ─────────────────────────────────────────────────
        ProfileSection(title = "البيانات الشخصية", icon = Icons.Outlined.Person) {
            HMTextField(
                value         = name,
                onValueChange = onNameChange,
                label         = "الاسم *",
                placeholder   = "اسم المريض الكامل",
                leadingIcon   = Icons.Outlined.Person,
                isError       = name.isBlank()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)) {
                HMTextField(
                    value         = age,
                    onValueChange = onAgeChange,
                    label         = "العمر",
                    placeholder   = "مثال: 45",
                    keyboardType  = KeyboardType.Number,
                    modifier      = Modifier.weight(1f)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "الجنس",
                        fontSize      = 11.sp,
                        fontWeight    = FontWeight.Bold,
                        color         = HMColor.TextSecondary,
                        modifier      = Modifier.padding(bottom = HMSpacing.xs)
                    )
                    HMSegmentedSelector(
                        options        = listOf("ذكر", "أنثى"),
                        selectedOption = gender,
                        onSelect       = onGenderChange,
                        accentColors   = listOf(HMColor.BlueBright, HMColor.GreenBright)
                    )
                }
            }

            // Blood type selector
            Column {
                Text(
                    "فصيلة الدم",
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color      = HMColor.TextSecondary,
                    modifier   = Modifier.padding(bottom = HMSpacing.xs)
                )
                val bloodTypes = listOf("A+", "A−", "B+", "B−", "AB+", "AB−", "O+", "O−")
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(HMSpacing.xs)
                ) {
                    bloodTypes.forEach { bt ->
                        item(key = bt) {
                            val isSelected = bloodType == bt
                            HMPressable(onClick = { onBloodTypeChange(if (isSelected) "" else bt) }) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(HMRadius.full))
                                        .background(
                                            if (isSelected) HMColor.RedBright.copy(alpha = 0.15f)
                                            else HMColor.BgOverlay
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) HMColor.RedBright.copy(alpha = 0.5f)
                                            else HMColor.BorderSubtle,
                                            RoundedCornerShape(HMRadius.full)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        bt,
                                        fontSize   = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color      = if (isSelected) HMColor.RedBright else HMColor.TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Medical conditions ────────────────────────────────────────────
        ProfileSection(title = "الحالات الطبية", icon = Icons.Outlined.MedicalServices) {
            HMTextField(
                value         = conditions,
                onValueChange = onConditionsChange,
                label         = "الحالات الطبية (اختياري)",
                placeholder   = "مثال: ضغط الدم، السكري، الربو...",
                singleLine    = false,
                minLines      = 3,
                maxLines      = 5
            )
        }

        // ── Emergency contact ─────────────────────────────────────────────
        ProfileSection(title = "جهة الطوارئ", icon = Icons.Outlined.Emergency) {
            HMTextField(
                value         = emergencyContact,
                onValueChange = onContactChange,
                label         = "اسم جهة الاتصال (اختياري)",
                placeholder   = "مثال: محمد أحمد",
                leadingIcon   = Icons.Outlined.Person
            )
            HMTextField(
                value         = emergencyPhone,
                onValueChange = onPhoneChange,
                label         = "رقم الهاتف (اختياري)",
                placeholder   = "مثال: 0501234567",
                keyboardType  = KeyboardType.Phone,
                leadingIcon   = Icons.Outlined.Phone
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
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
                    .background(HMColor.GreenBright.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = HMColor.GreenBright, modifier = Modifier.size(14.dp))
            }
            Text(
                title,
                fontSize      = 11.sp,
                fontWeight    = FontWeight.Bold,
                color         = HMColor.TextSecondary,
                letterSpacing = 0.8.sp
            )
        }
        HMDivider()
        Spacer(Modifier.height(HMSpacing.md))
        Column(
            verticalArrangement = Arrangement.spacedBy(HMSpacing.sm),
            content             = content
        )
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String, icon: ImageVector) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm),
        modifier              = Modifier.fillMaxWidth()
    ) {
        Icon(
            icon, null,
            tint     = HMColor.TextDisabled,
            modifier = Modifier.size(14.dp)
        )
        Text(
            "$label: ",
            fontSize = 12.sp,
            color    = HMColor.TextSecondary
        )
        Text(
            value,
            fontSize   = 13.sp,
            fontWeight = FontWeight.Medium,
            color      = HMColor.TextPrimary
        )
    }
}

private fun formatDate(ts: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ts))