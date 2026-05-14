package com.healthmonitor.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.healthmonitor.app.data.local.entities.PatientEntity
import com.healthmonitor.app.ui.design.*
import com.healthmonitor.app.ui.viewmodel.PatientViewModel

@Composable
fun PatientsScreen(
    navController: NavHostController,
    viewModel: PatientViewModel = hiltViewModel()
) {
    val patients        by viewModel.getAllPatients().collectAsState(initial = emptyList())
    val activePatientId by viewModel.activePatientIdFlow.collectAsState()
    var deleteTarget    by remember { mutableStateOf<PatientEntity?>(null) }
    var showAdd         by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = HMColor.BgBase,
        floatingActionButton = {
            FloatingActionButton(
                onClick        = { showAdd = true },
                containerColor = HMColor.GreenBright,
                contentColor   = HMColor.TextInverse,
                shape          = RoundedCornerShape(HMRadius.sm)
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة مريض")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(HMColor.BgBase)
                .padding(padding)
                .padding(horizontal = HMSpacing.lg)
        ) {
            Spacer(Modifier.height(HMSpacing.lg))

            // ── Header ────────────────────────────────────────────────────
            Row(
                verticalAlignment     = Alignment.CenterVertically,
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
                        Icons.Default.Group, null,
                        tint     = HMColor.GreenBright,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        "المرضى",
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color      = HMColor.TextPrimary
                    )
                    Text(
                        "${patients.size} مريض مسجل",
                        fontSize = 11.sp,
                        color    = HMColor.TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(HMSpacing.lg))

            // ── Empty state ───────────────────────────────────────────────
            if (patients.isEmpty()) {
                Spacer(Modifier.height(HMSpacing.xxxl))
                HMEmptyState(
                    emoji    = "👤",
                    title    = "لا يوجد مرضى",
                    subtitle = "اضغط + لإضافة أول مريض"
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(HMSpacing.sm)) {
                    items(patients, key = { it.id }) { patient ->
                        PatientCard(
                            patient   = patient,
                            isActive  = patient.id == activePatientId,
                            onSelect  = { viewModel.setActivePatientId(patient.id) },
                            onEdit    = { navController.navigate("patient_profile/${patient.id}") },
                            onDelete  = { deleteTarget = patient }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    // ── Add dialog ────────────────────────────────────────────────────────
    if (showAdd) {
        AddPatientDialog(
            onDismiss = { showAdd = false },
            onSave    = { name, age, gender ->
                viewModel.addPatient(name, age.toIntOrNull() ?: 0, gender)
                showAdd = false
            }
        )
    }

    // ── Delete confirmation ───────────────────────────────────────────────
    deleteTarget?.let { patient ->
        HMDialog(
            onDismiss           = { deleteTarget = null },
            title               = "حذف المريض",
            confirmText         = "حذف",
            onConfirm           = {
                viewModel.deletePatient(patient)
                deleteTarget = null
            },
            dismissText         = "إلغاء",
            confirmColor        = HMColor.RedBright,
            confirmContentColor = Color.White
        ) {
            Text(
                "هل تريد حذف المريض «${patient.name}»؟ سيتم حذف جميع بياناته.",
                fontSize   = 13.sp,
                color      = HMColor.TextSecondary,
                lineHeight = 20.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Patient card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PatientCard(
    patient: PatientEntity,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val accentColor by animateColorAsState(
        targetValue   = if (isActive) HMColor.GreenBright else HMColor.BorderDefault,
        animationSpec = tween(200),
        label         = "accent"
    )
    val bgColor = if (isActive) HMColor.GreenBg else HMColor.BgSurface

    HMCard(
        modifier        = Modifier.fillMaxWidth(),
        backgroundColor = bgColor,
        borderColor     = accentColor
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // ── Avatar + info ─────────────────────────────────────────────
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HMSpacing.md),
                modifier              = Modifier.weight(1f)
            ) {
                HMAvatar(
                    initials         = patient.name.take(2),
                    size             = 44.dp,
                    backgroundColor  = if (isActive) HMColor.GreenBg else HMColor.BgOverlay,
                    textColor        = if (isActive) HMColor.GreenBright else HMColor.TextSecondary
                )
                Column {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            patient.name,
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = if (isActive) HMColor.GreenBright else HMColor.TextPrimary
                        )
                        if (isActive) {
                            Icon(
                                Icons.Default.CheckCircle, null,
                                tint     = HMColor.GreenBright,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
                    ) {
                        if (patient.age > 0) {
                            Text(
                                "${patient.age} سنة",
                                fontSize = 12.sp,
                                color    = HMColor.TextSecondary
                            )
                        }
                        if (patient.gender.isNotBlank()) {
                            Text(
                                "·",
                                fontSize = 12.sp,
                                color    = HMColor.TextDisabled
                            )
                            Text(
                                patient.gender,
                                fontSize = 12.sp,
                                color    = HMColor.TextSecondary
                            )
                        }
                    }
                    if (patient.medicalConditions.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            patient.medicalConditions,
                            fontSize   = 11.sp,
                            color      = HMColor.TextDisabled,
                            maxLines   = 1
                        )
                    }
                }
            }

            // ── Actions ───────────────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!isActive) {
                    HMPressable(onClick = onSelect) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(HMRadius.xs))
                                .background(HMColor.GreenBright.copy(alpha = 0.12f))
                                .border(1.dp, HMColor.GreenBorder, RoundedCornerShape(HMRadius.xs))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "تحديد",
                                fontSize   = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color      = HMColor.GreenBright
                            )
                        }
                    }
                } else {
                    HMBadge(
                        text            = "نشط",
                        color           = HMColor.GreenBright,
                        backgroundColor = HMColor.GreenBright.copy(alpha = 0.12f)
                    )
                }
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Outlined.Edit, "تعديل",
                            tint     = HMColor.BlueBright.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Outlined.Delete, "حذف",
                            tint     = HMColor.RedBright.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Add patient dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AddPatientDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name   by remember { mutableStateOf("") }
    var age    by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("ذكر") }

    HMDialog(
        onDismiss      = onDismiss,
        title          = "مريض جديد",
        confirmText    = "حفظ",
        onConfirm      = {
            if (name.isNotBlank()) onSave(name.trim(), age.trim(), gender)
        },
        confirmEnabled = name.isNotBlank(),
        dismissText    = "إلغاء"
    ) {
        HMTextField(
            value         = name,
            onValueChange = { name = it },
            label         = "الاسم *",
            placeholder   = "اسم المريض",
            leadingIcon   = Icons.Outlined.Person,
            singleLine    = true
        )
        HMTextField(
            value         = age,
            onValueChange = { age = it.filter(Char::isDigit) },
            label         = "العمر",
            placeholder   = "مثال: 45",
            keyboardType  = KeyboardType.Number,
            singleLine    = true
        )
        Column(verticalArrangement = Arrangement.spacedBy(HMSpacing.sm)) {
            Text(
                "الجنس",
                fontSize      = 11.sp,
                fontWeight    = FontWeight.Bold,
                color         = HMColor.TextSecondary,
                letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified
            )
            HMSegmentedSelector(
                options        = listOf("ذكر", "أنثى"),
                selectedOption = gender,
                onSelect       = { gender = it },
                accentColors   = listOf(HMColor.BlueBright, HMColor.GreenBright)
            )
        }
    }
}