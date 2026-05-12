package com.healthmonitor.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.healthmonitor.app.data.local.entities.CaseEntity
import com.healthmonitor.app.ui.design.*
import com.healthmonitor.app.ui.viewmodel.CaseViewModel
import com.healthmonitor.app.ui.viewmodel.PatientViewModel

@Composable
fun CasesScreen(
    navController: NavHostController,
    caseViewModel: CaseViewModel       = hiltViewModel(),
    patientViewModel: PatientViewModel = hiltViewModel()
) {
    val resolvedPatientId by patientViewModel.activePatientIdFlow.collectAsState()

    val cases by if (resolvedPatientId != null) {
        caseViewModel.getCasesForPatient(resolvedPatientId!!).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList()) }
    }

    var showAdd      by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<CaseEntity?>(null) }
    var closeTarget  by remember { mutableStateOf<CaseEntity?>(null) }

    Scaffold(
        containerColor = HMColor.BgBase,
        floatingActionButton = {
            FloatingActionButton(
                onClick        = { showAdd = true },
                containerColor = if (resolvedPatientId != null) HMColor.GreenBright else HMColor.TextDisabled,
                contentColor   = HMColor.TextInverse,
                shape          = RoundedCornerShape(HMRadius.sm)
            ) {
                Icon(Icons.Default.Add, "إضافة حالة")
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
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(HMRadius.sm))
                            .background(HMColor.CyanBright.copy(alpha = 0.12f))
                            .border(1.dp, HMColor.CyanBright.copy(alpha = 0.3f), RoundedCornerShape(HMRadius.sm)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.FolderOpen, null,
                            tint     = HMColor.CyanBright,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            "الحالات",
                            fontSize   = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color      = HMColor.TextPrimary
                        )
                        Text(
                            "${cases.size} حالة مسجلة",
                            fontSize = 11.sp,
                            color    = HMColor.TextSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(HMSpacing.lg))

            // ── No patient selected ───────────────────────────────────────
            if (resolvedPatientId == null) {
                HMCard(
                    modifier        = Modifier.fillMaxWidth(),
                    borderColor     = HMColor.AmberBorder,
                    backgroundColor = HMColor.AmberBg
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
                    ) {
                        Icon(
                            Icons.Outlined.FolderOpen, null,
                            tint     = HMColor.AmberBright,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "يرجى اختيار مريض أولاً من القائمة العلوية.",
                            fontSize = 13.sp,
                            color    = HMColor.AmberBright
                        )
                    }
                }
                return@Column
            }

            // ── Empty state ───────────────────────────────────────────────
            if (cases.isEmpty()) {
                Spacer(Modifier.height(HMSpacing.xxxl))
                HMEmptyState(
                    emoji    = "📁",
                    title    = "لا توجد حالات",
                    subtitle = "اضغط + لإضافة أول حالة للمريض"
                )
                return@Column
            }

            // ── Cases list ────────────────────────────────────────────────
            LazyColumn(verticalArrangement = Arrangement.spacedBy(HMSpacing.sm)) {
                val open   = cases.filter { !it.isClosed }
                val closed = cases.filter { it.isClosed }

                if (open.isNotEmpty()) {
                    item {
                        HMSectionHeader(
                            title = "مفتوحة (${open.size})",
                            color = HMColor.GreenBright
                        )
                        Spacer(Modifier.height(HMSpacing.xs))
                    }
                    items(open, key = { it.id }) { c ->
                        CaseCard(
                            c        = c,
                            onOpen   = { caseViewModel.setActiveCase(c.id) },
                            onClose  = { closeTarget = c },
                            onDelete = { deleteTarget = c }
                        )
                    }
                }

                if (closed.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(HMSpacing.md))
                        HMSectionHeader(
                            title = "مغلقة (${closed.size})",
                            color = HMColor.TextDisabled
                        )
                        Spacer(Modifier.height(HMSpacing.xs))
                    }
                    items(closed, key = { it.id }) { c ->
                        CaseCard(
                            c        = c,
                            onOpen   = {},
                            onClose  = {},
                            onDelete = { deleteTarget = c }
                        )
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    // ── Add dialog ────────────────────────────────────────────────────────
    if (showAdd) {
        AddCaseDialog(
            onDismiss = { showAdd = false },
            onSave    = { title, doctor, notes ->
                caseViewModel.addCase(resolvedPatientId!!, title, doctor, notes)
                showAdd = false
            }
        )
    }

    // ── Close confirmation ────────────────────────────────────────────────
    closeTarget?.let { c ->
        HMDialog(
            onDismiss           = { closeTarget = null },
            title               = "إغلاق الحالة",
            confirmText         = "إغلاق",
            onConfirm           = { caseViewModel.closeCase(c); closeTarget = null },
            dismissText         = "إلغاء",
            confirmColor        = HMColor.AmberBright,
            confirmContentColor = HMColor.TextInverse
        ) {
            Text(
                "سيتم إيقاف جميع أدوية الحالة «${c.title}» وإغلاقها. هل تريد المتابعة؟",
                fontSize = 13.sp,
                color    = HMColor.TextSecondary,
                lineHeight = 20.sp
            )
        }
    }

    // ── Delete confirmation ───────────────────────────────────────────────
    deleteTarget?.let { c ->
        HMDialog(
            onDismiss           = { deleteTarget = null },
            title               = "حذف الحالة",
            confirmText         = "حذف",
            onConfirm           = { caseViewModel.deleteCase(c); deleteTarget = null },
            dismissText         = "إلغاء",
            confirmColor        = HMColor.RedBright,
            confirmContentColor = Color.White
        ) {
            Text(
                "هل تريد حذف الحالة «${c.title}»؟ سيتم حذف جميع الأدوية المرتبطة بها.",
                fontSize = 13.sp,
                color    = HMColor.TextSecondary,
                lineHeight = 20.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Case card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CaseCard(
    c: CaseEntity,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onDelete: () -> Unit
) {
    val accentColor = if (c.isClosed) HMColor.TextDisabled else HMColor.GreenBright
    val borderColor = if (c.isClosed) HMColor.BorderSubtle else HMColor.GreenBorder
    val bgColor     = if (c.isClosed) HMColor.BgBase       else HMColor.BgSurface

    HMCard(
        modifier        = Modifier.fillMaxWidth(),
        backgroundColor = bgColor,
        borderColor     = borderColor
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.Top
        ) {
            // ── Info column ───────────────────────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
                ) {
                    Text(
                        c.title,
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = if (c.isClosed) HMColor.TextSecondary else HMColor.TextPrimary
                    )
                    HMBadge(
                        text            = if (c.isClosed) "مغلقة" else "مفتوحة",
                        color           = accentColor,
                        backgroundColor = accentColor.copy(alpha = 0.12f)
                    )
                }
                if (!c.doctorName.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "د. ${c.doctorName}",
                        fontSize = 12.sp,
                        color    = HMColor.TextSecondary
                    )
                }
                if (!c.notes.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        c.notes,
                        fontSize   = 11.sp,
                        color      = HMColor.TextDisabled,
                        lineHeight = 16.sp
                    )
                }
            }

            // ── Actions column ────────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Delete — always visible
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Delete, "حذف",
                        tint     = HMColor.RedBright.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // ── Action buttons — only for open cases ──────────────────────────
        if (!c.isClosed) {
            Spacer(Modifier.height(HMSpacing.sm))
            HMDivider()
            Spacer(Modifier.height(HMSpacing.sm))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
            ) {
                HMPrimaryButton(
                    text     = "تفعيل",
                    onClick  = onOpen,
                    color    = HMColor.GreenBright,
                    modifier = Modifier.weight(1f)
                )
                HMSecondaryButton(
                    text     = "إغلاق",
                    onClick  = onClose,
                    color    = HMColor.AmberBright,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Add case dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AddCaseDialog(
    onDismiss: () -> Unit,
    onSave: (String, String?, String?) -> Unit
) {
    var title  by remember { mutableStateOf("") }
    var doctor by remember { mutableStateOf("") }
    var notes  by remember { mutableStateOf("") }

    HMDialog(
        onDismiss      = onDismiss,
        title          = "إضافة حالة جديدة",
        confirmText    = "حفظ",
        onConfirm      = {
            if (title.isNotBlank()) {
                onSave(title.trim(), doctor.ifBlank { null }, notes.ifBlank { null })
            }
        },
        confirmEnabled = title.isNotBlank(),
        dismissText    = "إلغاء"
    ) {
        HMTextField(
            value         = title,
            onValueChange = { title = it },
            label         = "عنوان الحالة *",
            placeholder   = "مثال: متابعة ضغط الدم",
            singleLine    = true
        )
        HMTextField(
            value         = doctor,
            onValueChange = { doctor = it },
            label         = "اسم الطبيب (اختياري)",
            singleLine    = true
        )
        HMTextField(
            value         = notes,
            onValueChange = { notes = it },
            label         = "ملاحظات (اختياري)",
            singleLine    = false,
            minLines      = 2,
            maxLines      = 4
        )
    }
}