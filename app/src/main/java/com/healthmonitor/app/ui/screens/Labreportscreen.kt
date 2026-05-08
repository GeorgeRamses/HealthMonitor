package com.healthmonitor.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.healthmonitor.app.data.local.entities.LabReportItemEntity
import com.healthmonitor.app.ui.design.*
import com.healthmonitor.app.ui.viewmodel.LabReportViewModel
import com.healthmonitor.app.ui.viewmodel.LabReportWithItems
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────────────────────
// Lab Report Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LabReportScreen(
    viewModel: LabReportViewModel = hiltViewModel()
) {
    val uiState          by viewModel.uiState.collectAsState()
    val reportsWithItems by viewModel.reportsWithItems.collectAsState()
    val context          = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bmp = uri.decodeBitmapLabReport(context)
        if (bmp != null) viewModel.scanAndSaveLabReport(bmp)
        else viewModel.clearState()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HMSpacing.lg)
    ) {
        Spacer(Modifier.height(HMSpacing.md))

        // ── Upload card ───────────────────────────────────────────────────
        HMCard(
            modifier        = Modifier.fillMaxWidth(),
            borderColor     = HMColor.BlueBorder,
            backgroundColor = HMColor.BlueBg
        ) {
            HMSectionHeader("استيراد تقرير مخبري أو تصويري", color = HMColor.BlueBright)
            Spacer(Modifier.height(HMSpacing.sm))
            Text(
                "ارفع صورة التقرير — سيستخرج الذكاء الاصطناعي جميع القيم والقياسات مع شرح مبسط لكل فحص.",
                fontSize   = 12.sp,
                color      = HMColor.TextSecondary,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(HMSpacing.md))
            HMPrimaryButton(
                text        = if (uiState.isLoading) "جارٍ المعالجة..." else "📷  رفع صورة التقرير",
                onClick     = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                enabled     = !uiState.isLoading,
                leadingIcon = Icons.Outlined.FileUpload,
                color       = HMColor.BlueBright,
                modifier    = Modifier.fillMaxWidth()
            )
        }

        // ── Loading ───────────────────────────────────────────────────────
        AnimatedVisibility(visible = uiState.isLoading) {
            Column {
                Spacer(Modifier.height(HMSpacing.md))
                LabScanLoadingCard()
            }
        }

        // ── Error ─────────────────────────────────────────────────────────
        uiState.error?.let { err ->
            Spacer(Modifier.height(HMSpacing.md))
            AiErrorCard(err)
        }

        // ── Success banner ────────────────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.successMessage != null,
            enter   = fadeIn() + expandVertically(),
            exit    = fadeOut() + shrinkVertically()
        ) {
            uiState.successMessage?.let { msg ->
                Spacer(Modifier.height(HMSpacing.md))
                HMCard(
                    modifier        = Modifier.fillMaxWidth(),
                    borderColor     = HMColor.GreenBorder,
                    backgroundColor = HMColor.GreenBg
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
                    ) {
                        Icon(Icons.Default.CheckCircle, null,
                            tint = HMColor.GreenBright, modifier = Modifier.size(18.dp))
                        Text(msg, fontSize = 13.sp, color = HMColor.GreenBright,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ── History ───────────────────────────────────────────────────────
        Spacer(Modifier.height(HMSpacing.xl))
        HMSectionHeader(
            title = "سجل التقارير (${reportsWithItems.size})",
            color = HMColor.BlueBright
        )
        Spacer(Modifier.height(HMSpacing.sm))

        if (reportsWithItems.isEmpty() && !uiState.isLoading) {
            HMEmptyState(
                emoji    = "🔬",
                title    = "لا توجد تقارير",
                subtitle = "ارفع صورة تقرير للبدء"
            )
        } else {
            reportsWithItems.forEach { rwi ->
                LabReportCard(
                    rwi      = rwi,
                    onDelete = { viewModel.deleteReport(rwi.report.id) }
                )
                Spacer(Modifier.height(HMSpacing.md))
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Individual report card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LabReportCard(rwi: LabReportWithItems, onDelete: () -> Unit) {
    var expanded       by remember { mutableStateOf(false) }
    var showDeleteDial by remember { mutableStateOf(false) }

    val report   = rwi.report
    val items    = rwi.items
    val abnormal = items.count { it.status != "Normal" }

    val accent = when {
        abnormal == 0              -> HMColor.GreenBright
        abnormal <= items.size / 2 -> HMColor.AmberBright
        else                       -> HMColor.RedBright
    }
    val accentBg     = when {
        abnormal == 0              -> HMColor.GreenBg
        abnormal <= items.size / 2 -> HMColor.AmberBg
        else                       -> HMColor.RedBg
    }
    val accentBorder = when {
        abnormal == 0              -> HMColor.GreenBorder
        abnormal <= items.size / 2 -> HMColor.AmberBorder
        else                       -> HMColor.RedBorder
    }

    HMCard(
        modifier        = Modifier.fillMaxWidth(),
        backgroundColor = HMColor.BgSurface,
        borderColor     = accent.copy(alpha = 0.3f)
    ) {
        // ── Tappable header ───────────────────────────────────────────────
        HMPressable(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    modifier              = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(HMRadius.sm))
                            .background(accentBg)
                            .border(1.dp, accentBorder, RoundedCornerShape(HMRadius.sm)),
                        contentAlignment = Alignment.Center
                    ) { Text("🔬", fontSize = 18.sp) }

                    Column {
                        Text(
                            report.reportName,
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color      = HMColor.TextPrimary
                        )
                        Text(
                            formatLabDate(report.reportDate),
                            fontSize = 11.sp,
                            color    = HMColor.TextDisabled
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showDeleteDial = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Delete, "حذف",
                            tint = HMColor.RedBright.copy(alpha = 0.55f),
                            modifier = Modifier.size(16.dp))
                    }
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = HMColor.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Summary badges — always visible
        Spacer(Modifier.height(HMSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)) {
            HMBadge("${items.size} معلمة", HMColor.BlueBright, HMColor.BlueBright.copy(alpha = 0.1f))
            if (abnormal > 0) {
                HMBadge("$abnormal خارج المعدل", accent, accent.copy(alpha = 0.12f))
            } else {
                HMBadge("جميع النتائج طبيعية ✓", HMColor.GreenBright, HMColor.GreenBright.copy(alpha = 0.1f))
            }
        }

        // ── Expandable rows ───────────────────────────────────────────────
        AnimatedVisibility(
            visible = expanded,
            enter   = fadeIn() + expandVertically(),
            exit    = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(Modifier.height(HMSpacing.md))
                HMDivider()
                Spacer(Modifier.height(HMSpacing.sm))

                // Column header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                ) {
                    Text("الفحص", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = HMColor.TextSecondary, modifier = Modifier.weight(2.5f))
                    Text("القيمة", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = HMColor.TextSecondary, modifier = Modifier.weight(1.5f),
                        textAlign = TextAlign.Center)
                    Text("المدى الطبيعي", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = HMColor.TextSecondary, modifier = Modifier.weight(1.5f),
                        textAlign = TextAlign.End)
                }
                HMDivider()
                Spacer(Modifier.height(4.dp))

                items.forEachIndexed { idx, item ->
                    LabItemRow(item = item)
                    if (idx < items.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(HMColor.BorderSubtle)
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDial) {
        HMDialog(
            onDismiss           = { showDeleteDial = false },
            title               = "حذف التقرير",
            confirmText         = "حذف",
            onConfirm           = { onDelete(); showDeleteDial = false },
            dismissText         = "إلغاء",
            confirmColor        = HMColor.RedBright,
            confirmContentColor = Color.White
        ) {
            Text("هل تريد حذف تقرير «${report.reportName}»؟",
                fontSize = 13.sp, color = HMColor.TextSecondary)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Single result row — term name + Arabic description + full value + range
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LabItemRow(item: LabReportItemEntity) {
    val statusColor = when (item.status) {
        "High"   -> HMColor.RedBright
        "Low"    -> HMColor.BlueBright
        else     -> HMColor.GreenBright
    }
    val statusIcon = when (item.status) {
        "High"   -> "⬆"
        "Low"    -> "⬇"
        else     -> "✓"
    }
    val rowBg = when (item.status) {
        "High"   -> HMColor.RedBright.copy(alpha = 0.04f)
        "Low"    -> HMColor.BlueBright.copy(alpha = 0.04f)
        else     -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // ── Left: test name + plain Arabic description ─────────────────────
        Column(modifier = Modifier.weight(2.5f)) {
            Text(
                text       = item.testItem,
                fontSize   = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color      = HMColor.TextPrimary,
                lineHeight = 16.sp
            )
            // Plain-Arabic description provided by the AI
            if (!item.simpleDescription.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text       = item.simpleDescription,
                    fontSize   = 10.sp,
                    color      = HMColor.TextSecondary,
                    lineHeight = 14.sp
                )
            }
        }

        // ── Centre: numeric value + unit + status pill ────────────────────
        Column(
            modifier            = Modifier.weight(1.5f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text       = item.result,
                fontSize   = 13.sp,
                fontWeight = FontWeight.Bold,
                color      = statusColor,
                textAlign  = TextAlign.Center
            )
            if (!item.unit.isNullOrBlank()) {
                Text(
                    text      = item.unit,
                    fontSize  = 10.sp,
                    color     = HMColor.TextDisabled,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(HMRadius.full))
                    .background(statusColor.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text       = "$statusIcon ${item.status}",
                    fontSize   = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color      = statusColor
                )
            }
        }

        // ── Right: reference range ────────────────────────────────────────
        Text(
            text      = item.referenceRange ?: "—",
            fontSize  = 10.sp,
            color     = HMColor.TextDisabled,
            modifier  = Modifier.weight(1.5f),
            textAlign = TextAlign.End,
            lineHeight = 14.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Loading card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LabScanLoadingCard() {
    val inf = rememberInfiniteTransition(label = "lab_loading")
    val alpha by inf.animateFloat(
        initialValue  = 0.4f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "alpha"
    )
    HMCard(
        modifier        = Modifier.fillMaxWidth(),
        borderColor     = HMColor.BlueBorder,
        backgroundColor = HMColor.BlueBg
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HMSpacing.md)
        ) {
            CircularProgressIndicator(
                color       = HMColor.BlueBright.copy(alpha = alpha),
                strokeWidth = 2.dp,
                modifier    = Modifier.size(20.dp)
            )
            Column {
                Text(
                    "يحلّل الذكاء الاصطناعي التقرير...",
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = HMColor.BlueBright.copy(alpha = alpha)
                )
                Text(
                    "يستخرج القيم والقياسات الفعلية مع شرح مبسط لكل فحص",
                    fontSize = 11.sp,
                    color    = HMColor.TextSecondary
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun formatLabDate(ts: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ts))

private fun Uri.decodeBitmapLabReport(context: Context): Bitmap? =
    runCatching {
        context.contentResolver.openInputStream(this)?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()