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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.healthmonitor.app.data.ai.MedicineInfoResult
import com.healthmonitor.app.ui.design.*
import com.healthmonitor.app.ui.viewmodel.AiToolsViewModel

// ─────────────────────────────────────────────────────────────────────────────
// AI Tools Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AiToolsScreen(
    navController: NavHostController,
    viewModel: AiToolsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val medicineState by viewModel.medicineInfoState.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val reportImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bitmap = uri.decodeBitmap(context)
        if (bitmap == null) viewModel.reportImageLoadFailed()
        else viewModel.summarizeReportImage(bitmap, uri.lastPathSegment)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HMColor.BgBase)
            .verticalScroll(scrollState)
            .padding(HMSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(HMSpacing.lg)
    ) {
        // ── Header ────────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(HMRadius.sm))
                    .background(HMColor.BlueBright.copy(alpha = 0.12f))
                    .border(1.dp, HMColor.BlueBorder, RoundedCornerShape(HMRadius.sm)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    null,
                    tint = HMColor.BlueBright,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    "المساعد الطبي",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = HMColor.TextPrimary
                )
                Text(
                    "مدعوم بالذكاء الاصطناعي",
                    fontSize = 11.sp,
                    color = HMColor.BlueBright.copy(alpha = 0.7f)
                )
            }
        }

        // ── Tab selector ──────────────────────────────────────────────────
        var selectedTab by remember { mutableIntStateOf(0) }
        val tabs = listOf("تحليل التقارير", "معلومات الدواء")
        HMSegmentedSelector(
            options = tabs,
            selectedOption = tabs[selectedTab],
            onSelect = { selectedTab = tabs.indexOf(it) },
            accentColors = listOf(HMColor.BlueBright, HMColor.GreenBright)
        )

        // ── Tab content ───────────────────────────────────────────────────
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                fadeIn(tween(200)) togetherWith fadeOut(tween(200))
            },
            label = "tab_content"
        ) { tab ->
            when (tab) {
                0 -> ReportAnalysisSection(
                    state = state,
                    onTextChange = viewModel::updateReportText,
                    onPickImage = {
                        reportImagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onSummarize = viewModel::summarizeReport,
                    onClear = viewModel::clear
                )

                1 -> MedicineInfoSection(
                    state = medicineState,
                    onSearch = viewModel::fetchMedicineInfo,
                    onClear = viewModel::clearMedicineInfo
                )
            }
        }

        Spacer(Modifier.height(HMSpacing.xxxl))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Report Analysis Section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReportAnalysisSection(
    state: com.healthmonitor.app.ui.viewmodel.AiToolsUiState,
    onTextChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onSummarize: () -> Unit,
    onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(HMSpacing.md)) {

        // Input card
        HMCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = HMColor.BlueBorder,
            backgroundColor = HMColor.BlueBg
        ) {
            HMSectionHeader("تحليل التقارير الطبية", color = HMColor.BlueBright)
            Spacer(Modifier.height(HMSpacing.sm))
            Text(
                "الصق نتائج التحاليل أو ارفع صورة التقرير وسيقوم الذكاء الاصطناعي بشرحها بالعربية.",
                fontSize = 12.sp,
                color = HMColor.TextSecondary,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(HMSpacing.md))

            HMTextField(
                value = state.reportText,
                onValueChange = onTextChange,
                label = "نص التقرير أو التحاليل",
                placeholder = "الصق نتائج التحاليل هنا...",
                leadingIcon = Icons.Outlined.Description,
                keyboardType = KeyboardType.Text,
                singleLine = false,
                minLines = 6,
                maxLines = 12
            )

            Spacer(Modifier.height(HMSpacing.sm))

            // Image picker button
            HMSecondaryButton(
                text = if (state.isLoading && state.reportText.isBlank()) "جاري تحليل الصورة..." else "📷  رفع صورة تقرير",
                onClick = onPickImage,
                enabled = !state.isLoading,
                color = HMColor.BlueBright,
                modifier = Modifier.fillMaxWidth()
            )

            state.selectedImageName?.let { name ->
                Spacer(Modifier.height(HMSpacing.xs))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HMSpacing.xs)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        null,
                        tint = HMColor.GreenBright,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        "الصورة المختارة: $name",
                        color = HMColor.TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(HMSpacing.md))

            HMPrimaryButton(
                text = if (state.isLoading) "جارٍ التحليل..." else "تحليل وشرح بالعربية ✦",
                onClick = onSummarize,
                enabled = state.reportText.isNotBlank() && !state.isLoading,
                leadingIcon = Icons.Default.AutoAwesome,
                color = HMColor.BlueBright
            )

            AnimatedVisibility(visible = state.reportText.isNotBlank() && !state.isLoading) {
                Column {
                    Spacer(Modifier.height(HMSpacing.sm))
                    HMSecondaryButton(
                        text = "مسح النص والنتيجة",
                        onClick = onClear,
                        leadingIcon = Icons.Outlined.Delete,
                        color = HMColor.TextSecondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Loading indicator
        AnimatedVisibility(visible = state.isLoading) {
            AiLoadingCard(message = "يقرأ الذكاء الاصطناعي التقرير ويُحللّه...")
        }

        // Error
        state.error?.let { AiErrorCard(it) }

        // Result
        AnimatedVisibility(
            visible = state.summary.isNotBlank(),
            enter = fadeIn() + expandVertically()
        ) {
            AiResultCard(
                title = "نتيجة التحليل",
                content = state.summary,
                accentColor = HMColor.BlueBright
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Medicine Info Section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MedicineInfoSection(
    state: com.healthmonitor.app.ui.viewmodel.MedicineInfoUiState,
    onSearch: (String) -> Unit,
    onClear: () -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(HMSpacing.md)) {
        // Input card
        HMCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = HMColor.GreenBorder,
            backgroundColor = HMColor.GreenBg
        ) {
            HMSectionHeader("معلومات الدواء", color = HMColor.GreenBright)
            Spacer(Modifier.height(HMSpacing.sm))
            Text(
                "اكتب اسم الدواء او التقطه بالكاميرا وسيقدم الذكاء الاصطناعي معلومات شاملة عنه بالعربية.",
                fontSize = 12.sp,
                color = HMColor.TextSecondary,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(HMSpacing.md))

            Row(verticalAlignment = Alignment.CenterVertically) {
                HMTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = "اسم الدواء",
                    placeholder = "مثال: Aspirin، Metformin...",
                    leadingIcon = Icons.Outlined.LocalPharmacy,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(HMSpacing.sm))
                IconButton(
                    onClick = { showScanner = true },
                    enabled = !state.isLoading
                ) {
                    Icon(
                        Icons.Outlined.CameraAlt, contentDescription = "مسح اسم الدواء",
                        tint = HMColor.GreenBright,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.height(HMSpacing.md))

            HMPrimaryButton(
                text = if (state.isLoading) "جارٍ البحث..." else "الحصول على معلومات الدواء ✦",
                onClick = { onSearch(searchText.trim()) },
                enabled = searchText.isNotBlank() && !state.isLoading,
                leadingIcon = Icons.Default.AutoAwesome,
                color = HMColor.GreenBright
            )

            AnimatedVisibility(visible = state.result != null && !state.isLoading) {
                Column {
                    Spacer(Modifier.height(HMSpacing.sm))
                    HMSecondaryButton(
                        text = "بحث جديد",
                        onClick = {
                            searchText = ""
                            onClear()
                        },
                        leadingIcon = Icons.Outlined.Refresh,
                        color = HMColor.TextSecondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Loading
        AnimatedVisibility(visible = state.isLoading) {
            AiLoadingCard(message = "يبحث الذكاء الاصطناعي عن معلومات الدواء...")
        }

        // Error
        state.error?.let { AiErrorCard(it) }

        // Results
        AnimatedVisibility(
            visible = state.result != null,
            enter = fadeIn() + expandVertically()
        ) {
            state.result?.let { result ->
                MedicineInfoResultCard(
                    medicineName = state.medicineName,
                    result = result
                )
            }
        }
    }

    if (showScanner) {
        // تغليف شاشة المسح بـ Dialog لضمان ظهورها كطبقة فوقية بملء الشاشة
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showScanner = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                decorFitsSystemWindows = false
            )
        ) {
            MedicineScannerScreen(
                onNameExtracted = { name ->
                    searchText = name
                    showScanner = false
                },
                onDismiss = { showScanner = false }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Medicine Info Result Card — structured display
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MedicineInfoResultCard(
    medicineName: String,
    result: MedicineInfoResult,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(HMSpacing.sm)
    ) {
        // Header
        HMCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = HMColor.GreenBorder,
            backgroundColor = HMColor.GreenBg
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(HMRadius.sm))
                        .background(HMColor.GreenBright.copy(alpha = 0.15f))
                        .border(1.dp, HMColor.GreenBorder, RoundedCornerShape(HMRadius.sm)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("💊", fontSize = 20.sp)
                }
                Column {
                    Text(
                        medicineName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = HMColor.GreenBright
                    )
                    result.arabicName?.let {
                        Text(it, fontSize = 12.sp, color = HMColor.TextSecondary)
                    }
                    result.category?.let {
                        HMBadge(
                            text = it,
                            color = HMColor.GreenBright,
                            backgroundColor = HMColor.GreenBright.copy(alpha = 0.12f)
                        )
                    }
                }
            }
        }

        // Uses
        result.primaryUses?.let {
            MedicineInfoRow(
                icon = Icons.Outlined.MedicalServices,
                title = "الاستخدامات الرئيسية",
                content = it,
                accentColor = HMColor.BlueBright,
                bgColor = HMColor.BlueBg,
                borderColor = HMColor.BlueBorder
            )
        }

        // Side effects
        result.commonSideEffects?.let {
            MedicineInfoRow(
                icon = Icons.Outlined.Info,
                title = "الأعراض الجانبية الشائعة",
                content = it,
                accentColor = HMColor.AmberBright,
                bgColor = HMColor.AmberBg,
                borderColor = HMColor.AmberBorder
            )
        }

        // Warnings
        result.importantWarnings?.let {
            MedicineInfoRow(
                icon = Icons.Default.WarningAmber,
                title = "تحذيرات مهمة",
                content = it,
                accentColor = HMColor.RedBright,
                bgColor = HMColor.RedBg,
                borderColor = HMColor.RedBorder
            )
        }

        // Instructions
        result.generalInstructions?.let {
            MedicineInfoRow(
                icon = Icons.Outlined.Checklist,
                title = "تعليمات الاستخدام العامة",
                content = it,
                accentColor = HMColor.GreenBright,
                bgColor = HMColor.GreenBg,
                borderColor = HMColor.GreenBorder
            )
        }

        // Disclaimer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(HMRadius.sm))
                .background(HMColor.BgOverlay)
                .border(1.dp, HMColor.BorderSubtle, RoundedCornerShape(HMRadius.sm))
                .padding(HMSpacing.md)
        ) {
            Text(
                result.disclaimer,
                fontSize = 11.sp,
                color = HMColor.TextSecondary,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MedicineInfoRow(
    icon: ImageVector,
    title: String,
    content: String,
    accentColor: Color,
    bgColor: Color,
    borderColor: Color
) {
    HMCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = bgColor,
        borderColor = borderColor
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(HMRadius.xs))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    letterSpacing = 0.3.sp
                )
                Spacer(Modifier.height(HMSpacing.xs))
                FormattedAiText(
                    content = content,
                    baseFontSize = 13f,
                    baseColor = HMColor.TextPrimary
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared UI components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AiLoadingCard(message: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "ai_loading")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "loading_alpha"
    )

    HMCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = HMColor.BlueBorder,
        backgroundColor = HMColor.BlueBg
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HMSpacing.md)
        ) {
            CircularProgressIndicator(
                color = HMColor.BlueBright.copy(alpha = alpha),
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    "جارٍ التحليل...",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HMColor.BlueBright.copy(alpha = alpha)
                )
                Text(
                    message,
                    fontSize = 11.sp,
                    color = HMColor.TextSecondary
                )
            }
        }
    }
}

@Composable
fun AiErrorCard(error: String) {
    HMCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = HMColor.RedBorder,
        backgroundColor = HMColor.RedBg
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm)
        ) {
            Icon(
                Icons.Default.WarningAmber,
                null,
                tint = HMColor.RedBright,
                modifier = Modifier
                    .size(16.dp)
                    .padding(top = 1.dp)
            )
            Column {
                Text(
                    "حدث خطأ",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = HMColor.RedBright
                )
                Spacer(Modifier.height(2.dp))
                Text(error, color = HMColor.RedBright.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
fun AiResultCard(
    title: String,
    content: String,
    accentColor: Color = HMColor.GreenBright
) {
    HMCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = accentColor.copy(alpha = 0.35f),
        backgroundColor = accentColor.copy(alpha = 0.04f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HMSpacing.sm),
            modifier = Modifier.padding(bottom = HMSpacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(HMRadius.xs))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    null,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
        HMDivider()
        Spacer(Modifier.height(HMSpacing.md))
        FormattedAiText(content)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FormattedAiText — RTL-aware markdown renderer for AI responses
// Handles: **bold**, numbered lines (١. / 1.), bullet lines (- / •), plain text
// Each line is forced RTL so mixed Arabic+English stays in the right order
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FormattedAiText(
    content: String,
    baseFontSize: Float = 14f,
    baseColor: Color = HMColor.TextPrimary
) {
    // Normalise line endings then split
    val lines = content
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .split("\n")

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        lines.forEach { rawLine ->
            val line = rawLine.trim()

            // True blank line → small gap, never skip real content
            if (line.isEmpty()) {
                Spacer(Modifier.height(2.dp))
                return@forEach
            }

            // ── Heading: ## or # ─────────────────────────────────────────
            if (line.startsWith("#")) {
                val headText = line.trimStart('#').trim().stripInlineBold()
                Text(
                    text = headText,
                    fontSize = (baseFontSize + 3).sp,
                    fontWeight = FontWeight.Bold,
                    color = baseColor,
                    lineHeight = (baseFontSize + 12).sp,
                    modifier = Modifier.fillMaxWidth(),
                    style = LocalTextStyle.current.copy(
                        textDirection = TextDirection.Rtl,
                        textAlign = TextAlign.Right
                    )
                )
                return@forEach
            }

            // ── Bullet: lines that start with - • * ──────────────────────
            val bulletPrefix = listOf("- ", "* ", "• ", "– ", "— ")
                .firstOrNull { line.startsWith(it) }
            if (bulletPrefix != null) {
                val body = line.removePrefix(bulletPrefix).trim()
                BulletRow(body, baseFontSize, baseColor)
                return@forEach
            }

            // ── Numbered: 1. or ١. ───────────────────────────────────────
            val numMatch = Regex("^([\\d١٢٣٤٥٦٧٨٩٠]{1,3}[.)،]\\s?)(.*)$").find(line)
            if (numMatch != null) {
                val num = numMatch.groupValues[1].trimEnd('.', ')', '،', ' ')
                val body = numMatch.groupValues[2].trim()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(HMRadius.full))
                            .background(baseColor.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            num,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = baseColor.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        text = body.parseInlineBold(baseColor),
                        fontSize = baseFontSize.sp,
                        color = baseColor,
                        lineHeight = (baseFontSize + 8).sp,
                        modifier = Modifier.weight(1f),
                        style = LocalTextStyle.current.copy(
                            textDirection = TextDirection.Rtl,
                            textAlign = TextAlign.Right
                        )
                    )
                }
                return@forEach
            }

            // ── Everything else: plain paragraph ─────────────────────────
            // This is the fallback — ALWAYS renders, never drops content
            Text(
                text = line.parseInlineBold(baseColor),
                fontSize = baseFontSize.sp,
                color = baseColor,
                lineHeight = (baseFontSize + 8).sp,
                modifier = Modifier.fillMaxWidth(),
                style = LocalTextStyle.current.copy(
                    textDirection = TextDirection.Rtl,
                    textAlign = TextAlign.Right
                )
            )
        }
    }
}

@Composable
private fun BulletRow(body: String, baseFontSize: Float, baseColor: Color) {
    val dotColor = when {
        body.contains("⚠") -> HMColor.AmberBright
        body.contains("✓") -> HMColor.GreenBright
        else -> baseColor.copy(alpha = 0.45f)
    }
    // RTL layout: text on the right, bullet dot on the left visually
    // We use Rtl arrangement so the dot appears after the text end (right side)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Text fills remaining space, aligned right
        Text(
            text = body.parseInlineBold(baseColor),
            fontSize = baseFontSize.sp,
            color = baseColor,
            lineHeight = (baseFontSize + 8).sp,
            modifier = Modifier.weight(1f),
            style = LocalTextStyle.current.copy(
                textDirection = TextDirection.Rtl,
                textAlign = TextAlign.Right
            )
        )
        // Dot on the left (visually on the right in RTL reading)
        Text(
            "•",
            fontSize = (baseFontSize + 2).sp,
            color = dotColor,
            lineHeight = (baseFontSize + 8).sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/** Strip **bold** markers and return plain string */
private fun String.stripInlineBold(): String =
    replace(Regex("\\*{1,2}(.+?)\\*{1,2}"), "$1")

/** Parse **bold** markers into AnnotatedString with bold spans.
 *  Falls back to plain text if parsing produces an empty result. */
private fun String.parseInlineBold(baseColor: Color): AnnotatedString {
    // Fast path: no bold markers at all
    if (!contains("**")) return AnnotatedString(this)

    val result = buildAnnotatedString {
        var i = 0
        while (i < this@parseInlineBold.length) {
            val openIdx = this@parseInlineBold.indexOf("**", i)
            if (openIdx == -1) {
                // No opening marker left — append remainder as plain
                append(this@parseInlineBold.substring(i))
                break
            }
            // Append plain text before opening **
            if (openIdx > i) {
                append(this@parseInlineBold.substring(i, openIdx))
            }
            // Search for closing ** strictly AFTER the opening pair ends
            val searchFrom = openIdx + 2
            val closeIdx = this@parseInlineBold.indexOf("**", searchFrom)
            if (closeIdx == -1 || closeIdx == openIdx) {
                // No valid closing — treat from openIdx as plain text
                append(this@parseInlineBold.substring(openIdx))
                break
            }
            val boldText = this@parseInlineBold.substring(openIdx + 2, closeIdx)
            if (boldText.isNotEmpty()) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                    append(boldText)
                }
            }
            i = closeIdx + 2
        }
    }

    // Safety: if parsing produced nothing, return the original string as-is
    return if (result.text.isBlank()) AnnotatedString(this) else result
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun Uri.decodeBitmap(context: Context): Bitmap? =
    runCatching {
        context.contentResolver.openInputStream(this)?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()