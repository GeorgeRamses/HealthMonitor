package com.healthmonitor.app.ui.screens

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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.healthmonitor.app.data.local.entities.SymptomEntity
import com.healthmonitor.app.ui.design.*
import com.healthmonitor.app.ui.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SymptomsScreen(
    navController: NavHostController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val symptomTypes  by viewModel.symptomTypes.collectAsState()
    val todaySymptoms by viewModel.todaySymptoms.collectAsState()

    var selectedSymptom  by remember { mutableStateOf<String?>(null) }
    var customSymptom    by remember { mutableStateOf("") }
    var selectedSeverity by remember { mutableStateOf<String?>(null) }
    var inhalerUsed      by remember { mutableStateOf(false) }
    var inhalerHelped    by remember { mutableStateOf<Boolean?>(null) }
    var notes            by remember { mutableStateOf("") }
    var showSuccess      by remember { mutableStateOf(false) }
    var deleteTarget     by remember { mutableStateOf<SymptomEntity?>(null) }

    val symptomToSave = customSymptom.trim().ifBlank { selectedSymptom.orEmpty() }
    val canSave       = symptomToSave.isNotBlank() && selectedSeverity != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(HMSpacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("تسجيل الأعراض", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = HMColor.TextPrimary)
                Text("اختر أو اكتب عرضاً جديداً", fontSize = 12.sp, color = HMColor.TextSecondary)
            }
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.Close, "رجوع", tint = HMColor.TextSecondary)
            }
        }

        Column(modifier = Modifier.padding(horizontal = HMSpacing.lg)) {

            // ── Symptom picker card ───────────────────────────────────────
            HMCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "نوع العرض",
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color      = HMColor.TextSecondary,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(HMSpacing.md))

                // Quick-select chips
                FlowRow(
                    horizontalGap = 6.dp,
                    verticalGap   = 6.dp
                ) {
                    symptomTypes.forEach { symptom ->
                        val isSelected = selectedSymptom == symptom && customSymptom.isBlank()
                        SymptomChip(
                            text       = symptom,
                            isSelected = isSelected,
                            onClick    = {
                                selectedSymptom = symptom
                                customSymptom   = ""
                            }
                        )
                    }
                }

                Spacer(Modifier.height(HMSpacing.md))
                HMDivider()
                Spacer(Modifier.height(HMSpacing.md))

                HMTextField(
                    value         = customSymptom,
                    onValueChange = {
                        customSymptom   = it
                        if (it.isNotBlank()) selectedSymptom = null
                    },
                    label         = "أو اكتب عرضاً جديداً",
                    placeholder   = "مثال: دوخة، صداع، غثيان...",
                    leadingIcon   = Icons.Outlined.Edit
                )
            }

            // ── Severity ─────────────────────────────────────────────────
            AnimatedVisibility(
                visible = symptomToSave.isNotBlank(),
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(Modifier.height(HMSpacing.md))
                    HMCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "شدة العرض",
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color      = HMColor.TextSecondary,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(HMSpacing.md))
                        HMSegmentedSelector(
                            options       = listOf("خفيف", "متوسط", "شديد"),
                            selectedOption = selectedSeverity,
                            onSelect      = { selectedSeverity = it },
                            accentColors  = listOf(HMColor.GreenBright, HMColor.AmberBright, HMColor.RedBright)
                        )
                    }
                }
            }

            Spacer(Modifier.height(HMSpacing.md))

            // ── Inhaler toggle ────────────────────────────────────────────
            HMCard(modifier = Modifier.fillMaxWidth()) {
                HMToggleRow(
                    title    = "استخدام البخاخ",
                    subtitle = "هل استخدمت البخاخ مع هذا العرض؟",
                    checked  = inhalerUsed,
                    onCheckedChange = {
                        inhalerUsed = it
                        inhalerHelped = null
                    }
                )
                AnimatedVisibility(visible = inhalerUsed) {
                    Column {
                        Spacer(Modifier.height(HMSpacing.md))
                        HMDivider()
                        Spacer(Modifier.height(HMSpacing.md))
                        Text("هل تحسّن الوضع بعد البخاخ؟", fontSize = 13.sp, color = HMColor.TextPrimary)
                        Spacer(Modifier.height(HMSpacing.sm))
                        HMSegmentedSelector(
                            options        = listOf("نعم", "لا"),
                            selectedOption = when (inhalerHelped) { true -> "نعم"; false -> "لا"; else -> null },
                            onSelect       = { inhalerHelped = it == "نعم" },
                            accentColors   = listOf(HMColor.GreenBright, HMColor.RedBright)
                        )
                    }
                }
            }

            Spacer(Modifier.height(HMSpacing.md))

            HMTextField(
                value         = notes,
                onValueChange = { notes = it },
                label         = "ملاحظات إضافية (اختياري)",
                singleLine    = false,
                minLines      = 2,
                maxLines      = 4
            )

            Spacer(Modifier.height(HMSpacing.md))

            HMPrimaryButton(
                text        = "حفظ التقرير",
                onClick     = {
                    viewModel.recordSymptom(
                        symptomType             = symptomToSave,
                        severity                = selectedSeverity ?: return@HMPrimaryButton,
                        notes                   = notes.ifBlank { null },
                        inhalerUsed             = inhalerUsed,
                        improvementAfterInhaler = inhalerHelped
                    )
                    selectedSymptom = null; customSymptom = ""; selectedSeverity = null
                    inhalerUsed = false; inhalerHelped = null; notes = ""
                    showSuccess = true
                },
                enabled     = canSave,
                leadingIcon = Icons.Filled.Save,
                modifier    = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(HMSpacing.xl))

            // ── Today's symptoms ──────────────────────────────────────────
            HMSectionHeader("أعراض اليوم")
            Spacer(Modifier.height(HMSpacing.sm))

            if (todaySymptoms.isEmpty()) {
                HMEmptyState(emoji = "✅", title = "لا أعراض اليوم", subtitle = "رائع! لا توجد أعراض مسجلة اليوم")
            } else {
                todaySymptoms.sortedByDescending { it.time }.forEach { s ->
                    SymptomCard(symptom = s, onDelete = { deleteTarget = s })
                    Spacer(Modifier.height(HMSpacing.sm))
                }
            }

            Spacer(Modifier.height(HMSpacing.xxxl))
        }
    }

    if (showSuccess) {
        HMSuccessDialog("تم الحفظ", "تم تسجيل العرض بنجاح", onDismiss = { showSuccess = false })
    }

    deleteTarget?.let { s ->
        HMDialog(
            onDismiss    = { deleteTarget = null },
            title        = "حذف السجل",
            confirmText  = "حذف",
            onConfirm    = { viewModel.deleteSymptom(s); deleteTarget = null },
            dismissText  = "إلغاء",
            confirmColor = HMColor.RedBright,
            confirmContentColor = androidx.compose.ui.graphics.Color.White
        ) {
            Text(
                "هل تريد حذف سجل «${s.symptomType}» من أعراض اليوم؟",
                fontSize = 13.sp,
                color = HMColor.TextSecondary
            )
        }
    }
}

@Composable
private fun SymptomChip(text: String, isSelected: Boolean, onClick: () -> Unit) {
    HMPressable(onClick = onClick) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(HMRadius.full))
                .background(
                    if (isSelected) HMColor.GreenBright.copy(alpha = 0.15f)
                    else HMColor.BgOverlay
                )
                .border(
                    1.dp,
                    if (isSelected) HMColor.GreenBorder else HMColor.BorderSubtle,
                    RoundedCornerShape(HMRadius.full)
                )
                .padding(horizontal = HMSpacing.md, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                fontSize   = 12.sp,
                color      = if (isSelected) HMColor.GreenBright else HMColor.TextSecondary,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun SymptomCard(symptom: SymptomEntity, onDelete: () -> Unit) {
    val severityColor = when (symptom.severity) {
        "خفيف"  -> HMColor.GreenBright
        "متوسط" -> HMColor.AmberBright
        else    -> HMColor.RedBright
    }
    HMCard(
        modifier    = Modifier.fillMaxWidth(),
        borderColor = severityColor.copy(alpha = 0.25f)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        symptom.symptomType,
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = HMColor.TextPrimary
                    )
                    HMBadge(
                        text            = symptom.severity,
                        color           = severityColor,
                        backgroundColor = severityColor.copy(alpha = 0.12f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    formatSymptomTime(symptom.time),
                    fontSize = 11.sp,
                    color    = HMColor.TextDisabled
                )
                if (symptom.inhalerUsed) {
                    Spacer(Modifier.height(4.dp))
                    val inhalerNote = when (symptom.improvementAfterInhaler) {
                        true  -> "البخاخ ✓ تحسّن"
                        false -> "البخاخ ✓ بدون تحسّن"
                        else  -> "استُخدم البخاخ"
                    }
                    Text(inhalerNote, fontSize = 11.sp, color = HMColor.TextSecondary)
                }
                if (!symptom.notes.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(symptom.notes, fontSize = 11.sp, color = HMColor.TextSecondary)
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Delete, "حذف", tint = HMColor.RedBright.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

private fun formatSymptomTime(ts: Long): String =
    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(ts))


@Composable
fun FlowRow(
    horizontalGap: Dp = 8.dp,
    verticalGap: Dp = 8.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(
        content  = content,
        modifier = modifier
    ) { measurables, constraints ->
        val hGap = horizontalGap.roundToPx()
        val vGap = verticalGap.roundToPx()
        val rows  = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var rowPlaceables = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var rowWidth  = 0
        var maxHeight = 0

        measurables.forEach { m ->
            val p = m.measure(constraints.copy(minWidth = 0))
            if (rowWidth + p.width + (if (rowPlaceables.isEmpty()) 0 else hGap) > constraints.maxWidth) {
                rows.add(rowPlaceables)
                rowPlaceables = mutableListOf()
                rowWidth = 0
            }
            rowPlaceables.add(p)
            rowWidth += p.width + (if (rowPlaceables.size > 1) hGap else 0)
            maxHeight = maxOf(maxHeight, p.height)
        }
        if (rowPlaceables.isNotEmpty()) rows.add(rowPlaceables)

        val totalHeight = rows.size * maxHeight + (rows.size - 1).coerceAtLeast(0) * vGap
        layout(constraints.maxWidth, totalHeight) {
            var y = 0
            rows.forEach { row ->
                var x = 0
                row.forEach { p ->
                    p.placeRelative(x, y)
                    x += p.width + hGap
                }
                y += maxHeight + vGap
            }
        }
    }
}