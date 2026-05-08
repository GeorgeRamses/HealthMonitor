package com.healthmonitor.app.ui.screens

import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.healthmonitor.app.ui.design.HMColor
import com.healthmonitor.app.ui.design.HMPressable
import com.healthmonitor.app.ui.design.HMRadius
import com.healthmonitor.app.ui.design.HMSpacing
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

private const val TAG = "MedicineScanner"

private const val FRAME_WIDTH_FRACTION = 0.85f
private const val FRAME_ASPECT_RATIO = 3.5f

// How often we allow a new OCR pass while scanning (ms)
private const val SCAN_INTERVAL_MS = 400L

// How many consecutive frames must agree on the same top result before we lock in
private const val STABLE_FRAMES_NEEDED = 2

// Module-level var used only for the animated sweep line (non-Compose, purely visual)
private var lastFrameHeightPx = 0f

@Composable
fun MedicineScannerScreen(
    onNameExtracted: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // ── State ─────────────────────────────────────────────────────────────────
    var scanState by remember { mutableStateOf<ScanState>(ScanState.Scanning) }
    var confirmedName by remember { mutableStateOf<String?>(null) }

    var frameOffset by remember { mutableStateOf(IntOffset.Zero) }
    var frameSize by remember { mutableStateOf(IntSize.Zero) }
    var screenSize by remember { mutableStateOf(IntSize.Zero) }

    // ── OCR engines ───────────────────────────────────────────────────────────
    val latinRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val analysisExec = remember { Executors.newSingleThreadExecutor() }

    // Thread-safe flags
    val isBusy = remember { AtomicBoolean(false) }
    val lastScan = remember { AtomicLong(0L) }
    val isPaused = remember { AtomicBoolean(false) }

    // Stability tracking (only written on main thread)
    var stableCount by remember { mutableIntStateOf(0) }
    var lastTopResult by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose { latinRecognizer.close(); analysisExec.shutdown() }
    }

    // ── ImageAnalysis use-case (created once) ─────────────────────────────────
    val imageAnalyzer = remember {
        ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(analysisExec) { proxy ->
                    val now = System.currentTimeMillis()

                    if (isPaused.get() || isBusy.get() || now - lastScan.get() < SCAN_INTERVAL_MS) {
                        proxy.close(); return@setAnalyzer
                    }

                    val fOffset = frameOffset
                    val fSize = frameSize
                    val sSize = screenSize

                    if (fSize == IntSize.Zero || sSize == IntSize.Zero) {
                        proxy.close(); return@setAnalyzer
                    }

                    isBusy.set(true)
                    lastScan.set(now)

                    runOcr(proxy, latinRecognizer, fOffset, fSize, sSize) { candidates ->
                        Handler(Looper.getMainLooper()).post {
                            isBusy.set(false)

                            if (candidates.isEmpty()) {
                                stableCount = 0; lastTopResult = ""; scanState = ScanState.Scanning
                                return@post
                            }

                            val top = candidates.first()
                            if (top == lastTopResult) stableCount++
                            else {
                                stableCount = 1; lastTopResult = top
                            }

                            scanState = ScanState.LivePreview(top)

                            if (stableCount >= STABLE_FRAMES_NEEDED) {
                                isPaused.set(true)
                                scanState = ScanState.Results(candidates)
                            }
                        }
                    }
                }
            }
    }

    // ── Root Box ──────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onGloballyPositioned { screenSize = it.size }
    ) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                // Use FILL_CENTER to ensure the camera fills the screen accurately
                previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
                ProcessCameraProvider.getInstance(ctx).addListener({
                    val provider = ProcessCameraProvider.getInstance(ctx).get()
                    val preview = Preview.Builder().build()
                        .also { it.surfaceProvider = previewView.surfaceProvider }
                    runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer
                        )
                    }.onFailure { e -> Log.e(TAG, "Camera bind failed: ${e.message}") }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = HMSpacing.lg, vertical = HMSpacing.md)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "مسح اسم الدواء", fontSize = 18.sp,
                            fontWeight = FontWeight.Bold, color = Color.White
                        )
                        Text(
                            when (scanState) {
                                is ScanState.Scanning -> "وجّه الكاميرا نحو اسم الدواء"
                                is ScanState.LivePreview -> "جارٍ التحقق…"
                                is ScanState.Results -> "اختر الاسم الصحيح"
                            },
                            fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close, "إغلاق",
                            tint = Color.White, modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                DimOverlay(FRAME_WIDTH_FRACTION, FRAME_ASPECT_RATIO)
                ScanFrame(
                    widthFraction = FRAME_WIDTH_FRACTION,
                    aspectRatio = FRAME_ASPECT_RATIO,
                    isScanning = scanState !is ScanState.Results,
                    onPositioned = { off, sz ->
                        frameOffset = off
                        frameSize = sz
                        lastFrameHeightPx = sz.height.toFloat()
                    }
                )
            }

            BottomPanel(
                scanState = scanState,
                onPick = { confirmedName = it },
                onRetry = {
                    stableCount = 0; lastTopResult = ""
                    isPaused.set(false)
                    scanState = ScanState.Scanning
                }
            )
        }
    }

    confirmedName?.let { picked ->
        AlertDialog(
            onDismissRequest = { confirmedName = null },
            containerColor = HMColor.BgElevated,
            title = {
                Text(
                    "تأكيد اسم الدواء",
                    fontWeight = FontWeight.SemiBold, color = HMColor.TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(HMSpacing.sm)) {
                    Text(
                        "هل تريد استخدام هذا النص كاسم الدواء؟",
                        fontSize = 13.sp, color = HMColor.TextSecondary
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(HMRadius.sm))
                            .background(HMColor.GreenBg)
                            .border(1.dp, HMColor.GreenBorder, RoundedCornerShape(HMRadius.sm))
                            .padding(HMSpacing.md)
                    ) {
                        Text(
                            picked, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = HMColor.GreenBright
                        )
                    }
                }
            },
            confirmButton = {
                HMPressable(onClick = { onNameExtracted(picked); confirmedName = null }) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(HMRadius.sm))
                            .background(HMColor.GreenBright)
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "استخدام", fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp, color = HMColor.TextInverse
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmedName = null }) {
                    Text("إلغاء", color = HMColor.TextSecondary)
                }
            },
            shape = RoundedCornerShape(HMRadius.lg)
        )
    }
}

// ── Sealed state ──────────────────────────────────────────────────────────────

private sealed interface ScanState {
    data object Scanning : ScanState
    data class LivePreview(val topCandidate: String) : ScanState
    data class Results(val lines: List<String>) : ScanState
}

// ── OCR ───────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalGetImage::class)
private fun runOcr(
    proxy: ImageProxy,
    latinRecognizer: TextRecognizer,
    frameOffset: IntOffset,
    frameSize: IntSize,
    screenSize: IntSize,
    onResult: (List<String>) -> Unit,
) {
    val mediaImage = proxy.image
    if (mediaImage == null) {
        proxy.close(); return
    }

    try {
        val rotation = proxy.imageInfo.rotationDegrees
        // Process the FULL image natively (much faster, zero bitmap allocations)
        val input = InputImage.fromMediaImage(mediaImage, rotation)

        // Calculate the camera feed dimensions matching the rotated orientation
        val camW = if (rotation == 90 || rotation == 270) proxy.height else proxy.width
        val camH = if (rotation == 90 || rotation == 270) proxy.width else proxy.height

        // Figure out how much the camera image was scaled to FILL the screen
        val scale = maxOf(
            screenSize.width.toFloat() / camW.coerceAtLeast(1),
            screenSize.height.toFloat() / camH.coerceAtLeast(1)
        )

        // Calculate how much of the camera feed is hidden off-screen (due to FILL_CENTER)
        val dx = (camW * scale - screenSize.width) / 2f
        val dy = (camH * scale - screenSize.height) / 2f

        // Mathematically map the visual UI frame to the original camera coordinates
        val frameLeft = (frameOffset.x + dx) / scale
        val frameTop = (frameOffset.y + dy) / scale
        val frameRight = frameLeft + (frameSize.width / scale)
        val frameBottom = frameTop + (frameSize.height / scale)

        // Give a tiny 5% padding so text touching the border isn't aggressively rejected
        val padX = (frameRight - frameLeft) * 0.05f
        val padY = (frameBottom - frameTop) * 0.05f

        val frameRect = RectF(
            frameLeft - padX,
            frameTop - padY,
            frameRight + padX,
            frameBottom + padY
        )

        latinRecognizer.process(input)
            .addOnSuccessListener { latinResult ->
                onResult(extractMedicationNames(latinResult, frameRect))
                proxy.close()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "OCR failed: ${e.message}")
                onResult(emptyList())
                proxy.close()
            }

    } catch (e: Exception) {
        Log.e(TAG, "runOcr error: ${e.message}")
        proxy.close()
        onResult(emptyList())
    }
}

private fun extractMedicationNames(latinResult: Text?, frameRect: RectF): List<String> {
    data class Span(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val height get() = bottom - top
        val width get() = right - left
        val midY get() = (top + bottom) / 2f
    }

    val allBlocks = (latinResult?.textBlocks ?: emptyList())

    val allSpans = allBlocks.flatMap { block ->
        val blockLines = block.lines.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null

            // CRITICAL CHECK: Completely ignore text whose center point is NOT inside our frame
            if (!frameRect.contains(box.exactCenterX(), box.exactCenterY())) {
                return@mapNotNull null
            }

            val raw = line.text.trim()
            if (raw.isEmpty()) return@mapNotNull null
            Span(raw, box.left, box.top, box.right, box.bottom)
        }.sortedBy { it.left }

        val groups = mutableListOf<MutableList<Span>>()
        for (span in blockLines) {
            val added = groups.any { group ->
                val avgH = group.map { it.height }.average()
                group.any { existing ->
                    // Relaxed band matching to 50% to handle slight curves or angled text
                    abs(existing.midY - span.midY) < avgH * 0.50f
                }.also { fits ->
                    if (fits) group.add(span)
                }
            }
            if (!added) groups.add(mutableListOf(span))
        }

        groups.mapNotNull { group ->
            if (group.isEmpty()) return@mapNotNull null
            val sorted = group.sortedBy { it.left }
            Span(
                text = sorted.joinToString(" ") { it.text },
                left = sorted.minOf { it.left },
                top = sorted.minOf { it.top },
                right = sorted.maxOf { it.right },
                bottom = sorted.maxOf { it.bottom },
            )
        }
    }

    val sorted = allSpans.sortedBy { it.left }.toMutableList()
    var i = 0
    while (i < sorted.size - 1) {
        val a = sorted[i]
        val b = sorted[i + 1]
        val avgCharW = if (a.text.isNotEmpty()) a.width.toFloat() / a.text.length else 20f
        val gap = (b.left - a.right).toFloat()

        // Relaxed height/band limits slightly to merge syllables like "Bus" and "copan"
        val sameBand = abs(a.midY - b.midY) < minOf(a.height, b.height) * 0.50f
        val similarHeight = abs(a.height - b.height).toFloat() < maxOf(a.height, b.height) * 0.35f

        // Increased allowed gap from 2.0x to 3.5x to account for wide letter spacing (kerning)
        if (sameBand && similarHeight && gap < avgCharW * 3.5f) {
            // If the gap is very tiny (less than 1 char width), join without a space
            val joinText = if (gap < avgCharW * 1.0f) "${a.text}${b.text}" else "${a.text} ${b.text}"
            sorted[i] = Span(
                text = joinText,
                left = minOf(a.left, b.left),
                top = minOf(a.top, b.top),
                right = maxOf(a.right, b.right),
                bottom = maxOf(a.bottom, b.bottom),
            )
            sorted.removeAt(i + 1)
        } else {
            i++
        }
    }

    data class Candidate(val text: String, val score: Float)

    return sorted
        .asSequence()
        .mapNotNull { span ->
            val raw = span.text.trim()

            if (raw.length < 3) return@mapNotNull null // Names are rarely < 3 chars
            if (isNoisyPattern(raw)) return@mapNotNull null

            // Score primarily heavily weighted by text physical HEIGHT (medicine names are the largest text)
            val heightScore = span.height.toFloat()

            // Penalize very long text (often descriptions, not names)
            val lengthPenalty = if (raw.length > 20) (raw.length - 20) * 2f else 0f

            // Penalize numbers (we prefer purely alphabetical words as the primary name)
            val digitPenalty = if (raw.any { it.isDigit() }) span.height * 0.4f else 0f

            val score = heightScore - lengthPenalty - digitPenalty

            if (score > 0f) {
                Candidate(cleanMedicationName(raw), score)
            } else {
                null
            }
        }
        .filter { it.text.isNotBlank() && it.text.length >= 3 }
        .sortedByDescending { it.score }
        .distinctBy { it.text }
        .take(6)
        .map { it.text }
        .toList()
        .also { results ->
            if (results.isNotEmpty()) {
                Log.d(TAG, "Detected candidates (${results.size}): $results")
            }
        }
}

private fun cleanMedicationName(text: String): String {
    // Strips out registered trademark symbols, etc.
    val cleaned = text
        .replace(Regex("[®™©]"), "")
        .replace(Regex("[^\\p{L}\\p{N}\\s\\-&]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
    val hasArabic = cleaned.any { it.code in 0x0600..0x06FF }
    return if (hasArabic) cleaned else cleaned
}

/** Robust noise exclusion heuristics */
private fun isNoisyPattern(text: String): Boolean {
    val lower = text.lowercase().trim()

    // 1. Exact match generic terms (Stop-words)
    val exactGenerics = setOf(
        "tablet", "tablets", "capsule", "capsules", "caplets",
        "syrup", "suspension", "drops", "ointment", "cream", "gel",
        "mg", "ml", "gm", "mcg", "g", "iu", "dose", "dosage"
    )
    if (exactGenerics.contains(lower)) return true

    // 2. Contains codes, expiry dates, or batch numbers
    if (lower.startsWith("exp") || lower.startsWith("lot") || lower.startsWith("bn")) return true
    if (text.matches(Regex(".*\\d{2}/\\d{2,4}.*"))) return true // dates like 10/24 or 10/2024
    if (text.matches(Regex(".*[A-Z]\\d{1,2}[A-Z].*")) && text.length < 8) return true // A1B

    // 3. Structural checks
    val letterCount = text.count { it.isLetter() }

    // Most medicine names have a dominant amount of letters
    if (letterCount < text.length / 3) return true

    // Batch numbers often consist of consonants/numbers without vowels
    val hasVowels = text.matches(Regex(".*[AEIOUYaeiouy].*")) || text.any { it.code in 0x0600..0x06FF }
    if (!hasVowels && letterCount > 0) return true

    // Text full of scattered punctuation
    if (text.count { !it.isLetterOrDigit() && !it.isWhitespace() } > text.length / 4) return true

    return false
}

// ── Composable components ─────────────────────────────────────────────────────

@Composable
private fun BottomPanel(scanState: ScanState, onPick: (String) -> Unit, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.82f))
            .padding(HMSpacing.lg)
    ) {
        when (scanState) {
            is ScanState.Scanning -> ScanningHint()
            is ScanState.LivePreview -> LivePreviewChip(scanState.topCandidate)
            is ScanState.Results -> ResultList(scanState.lines, onPick, onRetry)
        }
    }
}

@Composable
private fun ScanningHint() {
    val inf = rememberInfiniteTransition(label = "pulse")
    val alpha by inf.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "a"
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HMSpacing.sm)
    ) {
        Icon(
            Icons.Default.CameraAlt, null,
            tint = HMColor.GreenBright.copy(alpha = alpha), modifier = Modifier.size(28.dp)
        )
        Text(
            "وجّه الإطار على اسم الدواء وانتظر لحظة…",
            color = Color.White.copy(alpha = alpha), fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LivePreviewChip(text: String) {
    val inf = rememberInfiniteTransition(label = "blink")
    val alpha by inf.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "b"
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HMSpacing.sm)
    ) {
        Text("جارٍ التحقق…", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(HMRadius.sm))
                .background(HMColor.GreenBg)
                .border(1.dp, HMColor.GreenBorder.copy(alpha = alpha), RoundedCornerShape(HMRadius.sm))
                .padding(HMSpacing.md),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                color = HMColor.GreenBright.copy(alpha = alpha)
            )
        }
    }
}

@Composable
private fun ResultList(lines: List<String>, onPick: (String) -> Unit, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "اختر اسم الدواء:", color = Color.White,
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = onRetry, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Refresh, "مسح مجدداً",
                    tint = HMColor.GreenBright, modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.height(HMSpacing.sm))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.heightIn(max = 200.dp)
        ) {
            items(lines) { line ->
                DetectedTextChip(text = line, onClick = { onPick(line) })
            }
        }
    }
}

@Composable
private fun DimOverlay(frameFraction: Float, frameAspect: Float) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val fw = maxWidth * frameFraction
        val fh = fw / frameAspect
        val sg = (maxWidth - fw) / 2
        val tg = (maxHeight - fh) / 2
        val dim = Color.Black.copy(alpha = 0.55f)
        Box(
            Modifier
                .fillMaxWidth()
                .height(tg)
                .background(dim)
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(tg)
                .align(Alignment.BottomCenter)
                .background(dim)
        )
        Box(
            Modifier
                .width(sg)
                .fillMaxHeight()
                .align(Alignment.CenterStart)
                .background(dim)
        )
        Box(
            Modifier
                .width(sg)
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
                .background(dim)
        )
    }
}

@Composable
private fun ScanFrame(
    widthFraction: Float,
    aspectRatio: Float,
    isScanning: Boolean,
    onPositioned: (offset: IntOffset, size: IntSize) -> Unit,
) {
    val density = LocalDensity.current

    val inf = rememberInfiniteTransition(label = "frame")
    val glow by inf.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "g"
    )
    val sweep by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "sweep"
    )
    val borderColor = if (isScanning) HMColor.GreenBright.copy(alpha = glow) else HMColor.GreenBright

    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .aspectRatio(aspectRatio)
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                lastFrameHeightPx = coords.size.height.toFloat()
                onPositioned(IntOffset(pos.x.toInt(), pos.y.toInt()), coords.size)
            }
            .border(1.5.dp, borderColor, RoundedCornerShape(HMRadius.sm))
    ) {
        listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd)
            .forEach { alignment ->
                Box(
                    Modifier
                        .size(14.dp)
                        .align(alignment)
                        .background(borderColor)
                )
            }

        if (isScanning) {
            val sweepOffsetDp = with(density) { (sweep * lastFrameHeightPx).toDp() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .offset(y = sweepOffsetDp)
                    .background(HMColor.GreenBright.copy(alpha = 0.65f))
            )
        }

        Text(
            text = if (isScanning) "اسم الدواء هنا" else "✓ تم",
            fontSize = 10.sp,
            color = borderColor.copy(alpha = 0.75f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 3.dp)
        )
    }
}

@Composable
private fun DetectedTextChip(text: String, onClick: () -> Unit) {
    HMPressable(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(HMRadius.sm))
                .background(HMColor.BgOverlay.copy(alpha = 0.92f))
                .border(1.dp, HMColor.BorderDefault, RoundedCornerShape(HMRadius.sm))
                .padding(horizontal = HMSpacing.md, vertical = HMSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text, color = HMColor.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(HMSpacing.sm))
            Icon(
                Icons.Default.CheckCircle, "اختيار",
                tint = HMColor.GreenBright, modifier = Modifier.size(16.dp)
            )
        }
    }
}