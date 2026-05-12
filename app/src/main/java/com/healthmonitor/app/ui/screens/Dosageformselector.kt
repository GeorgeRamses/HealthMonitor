package com.healthmonitor.app.ui.screens

// ─────────────────────────────────────────────────────────────────────────────
// DosageFormSelector + UnitSelector
//
// ملف منفصل يحتوي على:
//   1. DosageFormSelector  → grid بكل أشكال الدواء (قرص، حقنة، بخاخ...)
//   2. UnitSelector        → row بالوحدات المناسبة للشكل المختار
//   3. DosageInputSection  → القسم الكامل يجمع الاثنين مع حقل الكمية
//
// الاستخدام داخل MedEditorDialog:
//   DosageInputSection(
//       dosage = dosage, onDosageChange = { dosage = it },
//       selectedForm = selectedForm, onFormChange = { selectedForm = it; unit = it.defaultUnit },
//       selectedUnit = unit, onUnitChange = { unit = it }
//   )
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthmonitor.app.data.model.DosageForm
import com.healthmonitor.app.data.model.DosageFormType
import com.healthmonitor.app.ui.design.*

// ─────────────────────────────────────────────────────────────────────────────
// DosageInputSection — القسم الكامل (شكل + وحدة + كمية)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DosageInputSection(
    dosage: String,
    onDosageChange: (String) -> Unit,
    selectedForm: DosageForm,
    onFormChange: (DosageForm) -> Unit,
    selectedUnit: String,
    onUnitChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(HMSpacing.sm)
    ) {
        // ── عنوان ─────────────────────────────────────────────────────────────
        HMSectionHeader("شكل الدواء والجرعة")

        // ── Grid شكل الدواء ──────────────────────────────────────────────────
        DosageFormGrid(
            selectedForm = selectedForm,
            onFormChange = { form ->
                onFormChange(form)
                // غيّر الوحدة تلقائياً للـ default عند تغيير الشكل
                onUnitChange(form.defaultUnit)
            }
        )

        // ── الوحدات المناسبة للشكل ───────────────────────────────────────────
        AnimatedVisibility(
            visible = true,
            enter   = fadeIn(tween(200)) + expandVertically(),
            exit    = fadeOut(tween(200)) + shrinkVertically()
        ) {
            UnitSelector(
                units        = selectedForm.units,
                selectedUnit = selectedUnit,
                onUnitChange = onUnitChange
            )
        }

        // ── حقل الكمية ───────────────────────────────────────────────────────
        HMTextField(
            value         = dosage,
            onValueChange = onDosageChange,
            label         = "الكمية / الجرعة *",
            placeholder   = dosagePlaceholder(selectedForm),
            trailingText  = selectedUnit,
            keyboardType  = KeyboardType.Decimal
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DosageFormGrid — شبكة 4 أعمدة لأشكال الدواء
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DosageFormGrid(
    selectedForm: DosageForm,
    onFormChange: (DosageForm) -> Unit
) {
    // نقسم الـ list على صفوف من 4
    val rows = DosageFormType.all.chunked(4)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { rowForms ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowForms.forEach { form ->
                    DosageFormChip(
                        form       = form,
                        isSelected = form.key == selectedForm.key,
                        onClick    = { onFormChange(form) },
                        modifier   = Modifier.weight(1f)
                    )
                }
                // padding لو الصف مش مكتمل
                repeat(4 - rowForms.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DosageFormChip — كارت واحد بالـ emoji والاسم
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DosageFormChip(
    form: DosageForm,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor     = if (isSelected) HMColor.GreenBright.copy(alpha = 0.14f) else HMColor.BgOverlay
    val borderColor = if (isSelected) HMColor.GreenBright.copy(alpha = 0.5f) else HMColor.BorderSubtle
    val textColor   = if (isSelected) HMColor.GreenBright else HMColor.TextSecondary

    HMPressable(onClick = onClick, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(HMRadius.sm))
                .background(bgColor)
                .border(1.dp, borderColor, RoundedCornerShape(HMRadius.sm))
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text     = form.emoji,
                fontSize = 20.sp
            )
            Text(
                text       = form.label,
                fontSize   = 9.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color      = textColor,
                maxLines   = 1,
                lineHeight = 11.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UnitSelector — LazyRow بالوحدات
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UnitSelector(
    units: List<String>,
    selectedUnit: String,
    onUnitChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "الوحدة",
            fontSize      = 10.sp,
            fontWeight    = FontWeight.Bold,
            color         = HMColor.TextSecondary,
            letterSpacing = 1.sp
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(units) { unitOption ->
                val isSelected  = unitOption == selectedUnit
                val bgColor     = if (isSelected) HMColor.GreenBright.copy(alpha = 0.14f) else HMColor.BgOverlay
                val borderColor = if (isSelected) HMColor.GreenBright.copy(alpha = 0.5f) else HMColor.BorderSubtle
                val textColor   = if (isSelected) HMColor.GreenBright else HMColor.TextSecondary

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(HMRadius.full))
                        .background(bgColor)
                        .border(1.dp, borderColor, RoundedCornerShape(HMRadius.full))
                        .clickable { onUnitChange(unitOption) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text       = unitOption,
                        fontSize   = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color      = textColor
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper — placeholder مناسب لكل شكل
// ─────────────────────────────────────────────────────────────────────────────

private fun dosagePlaceholder(form: DosageForm): String = when (form.key) {
    "tablet"      -> "مثال: 500"
    "capsule"     -> "مثال: 100"
    "injection"   -> "مثال: 2"
    "inhaler"     -> "مثال: 2"
    "drops"       -> "مثال: 3"
    "syrup"       -> "مثال: 10"
    "ointment"    -> "مثال: 1"
    "patch"       -> "مثال: 1"
    "suppository" -> "مثال: 1"
    "powder"      -> "مثال: 1"
    "sublingual"  -> "مثال: 0.5"
    "insulin"     -> "مثال: 10"
    else          -> "مثال: 5"
}