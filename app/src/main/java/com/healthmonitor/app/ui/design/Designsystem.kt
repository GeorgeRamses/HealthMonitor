package com.healthmonitor.app.ui.design

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
// DESIGN TOKENS — single source of truth for the entire app
// ─────────────────────────────────────────────────────────────────────────────

object HMColor {
    // ── Backgrounds ──────────────────────────────────────────────────────────
    val BgBase        = Color(0xFF080C0A)   // true near-black with a green tint
    val BgSurface     = Color(0xFF111814)   // cards and panels
    val BgElevated    = Color(0xFF1A2420)   // modals, dialogs
    val BgOverlay     = Color(0xFF222E28)   // hover states, chip backgrounds

    // ── Green accent (primary) ────────────────────────────────────────────────
    val GreenBright   = Color(0xFF00E676)   // high-impact CTA
    val GreenPrimary  = Color(0xFF43A047)   // standard primary
    val GreenMuted    = Color(0xFF2E7D32)   // subtle/secondary usage
    val GreenDim      = Color(0xFF1B5E20)   // very muted, disabled states
    val GreenGlow     = Color(0x2200E676)   // glow shadow (alpha)
    val GreenBg       = Color(0xFF0E2318)   // success card background
    val GreenBorder   = Color(0xFF2A4D30)   // success card border

    // ── Amber (warning) ───────────────────────────────────────────────────────
    val AmberBright   = Color(0xFFFFCA28)
    val AmberPrimary  = Color(0xFFFFB300)
    val AmberDim      = Color(0xFFFF8F00)
    val AmberBg       = Color(0xFF231900)
    val AmberBorder   = Color(0xFF3D2E00)

    // ── Red (danger) ──────────────────────────────────────────────────────────
    val RedBright     = Color(0xFFEF5350)
    val RedPrimary    = Color(0xFFE53935)
    val RedDim        = Color(0xFFB71C1C)
    val RedBg         = Color(0xFF200F0F)
    val RedBorder     = Color(0xFF3D1515)

    // ── Blue (info) ───────────────────────────────────────────────────────────
    val BlueBright    = Color(0xFF42A5F5)
    val BluePrimary   = Color(0xFF1E88E5)
    val BlueDim       = Color(0xFF1565C0)
    val BlueBg        = Color(0xFF0D1621)
    val BlueBorder    = Color(0xFF1A2D42)

    // ── Cyan (oxygen / saturation) ────────────────────────────────────────────
    val CyanBright    = Color(0xFF26C6DA)
    val CyanPrimary   = Color(0xFF00ACC1)

    // ── Text ──────────────────────────────────────────────────────────────────
    val TextPrimary   = Color(0xFFECF0ED)   // near-white, slight green warmth
    val TextSecondary = Color(0xFF8EA899)   // muted label text
    val TextDisabled  = Color(0xFF4A5E54)   // disabled / placeholders
    val TextInverse   = Color(0xFF080C0A)   // text on bright backgrounds

    // ── Borders ───────────────────────────────────────────────────────────────
    val BorderSubtle  = Color(0xFF1E2D24)   // hairline between sections
    val BorderDefault = Color(0xFF2A3D30)   // standard card border
    val BorderStrong  = Color(0xFF3A5440)   // emphasis border
}

object HMRadius {
    val xs  = 6.dp
    val sm  = 10.dp
    val md  = 14.dp
    val lg  = 20.dp
    val xl  = 28.dp
    val full = 999.dp
}

object HMSpacing {
    val xs  = 4.dp
    val sm  = 8.dp
    val md  = 12.dp
    val lg  = 16.dp
    val xl  = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

object HMElevation {
    val none   = 0.dp
    val low    = 1.dp
    val medium = 3.dp
    val high   = 8.dp
}

// ─────────────────────────────────────────────────────────────────────────────
// REUSABLE COMPOSABLES
// ─────────────────────────────────────────────────────────────────────────────

/** Surface card with consistent styling */
@Composable
fun HMCard(
    modifier: Modifier = Modifier,
    borderColor: Color = HMColor.BorderDefault,
    backgroundColor: Color = HMColor.BgSurface,
    cornerRadius: Dp = HMRadius.md,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(cornerRadius))
            .padding(HMSpacing.lg),
        content = content
    )
}

/** Pill/badge label */
@Composable
fun HMBadge(
    text: String,
    color: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(HMRadius.full))
            .background(backgroundColor)
            .padding(horizontal = HMSpacing.md, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp
        )
    }
}

/** Animated press-scale button wrapper */
@Composable
fun HMPressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "press_scale"
    )
    Box(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        content = content
    )
}

/** Section header with optional trailing content */
@Composable
fun HMSectionHeader(
    title: String,
    color: Color = HMColor.TextSecondary,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = HMSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = 1.2.sp
        )
        trailing?.invoke()
    }
}

/** Divider matching app style */
@Composable
fun HMDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(HMColor.BorderSubtle)
    )
}

/** Avatar/initials circle */
@Composable
fun HMAvatar(
    initials: String,
    size: Dp = 40.dp,
    backgroundColor: Color = HMColor.GreenBg,
    textColor: Color = HMColor.GreenBright
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.dp, HMColor.GreenBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials.take(2).uppercase(),
            color = textColor,
            fontSize = (size.value * 0.35f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Metric stat item — label + large value */
@Composable
fun HMStatItem(
    value: String,
    label: String,
    valueColor: Color = HMColor.TextPrimary,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            lineHeight = 28.sp
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = HMColor.TextSecondary,
            lineHeight = 14.sp
        )
    }
}

/** Horizontal info row: label on left, value on right */
@Composable
fun HMInfoRow(
    label: String,
    value: String,
    valueColor: Color = HMColor.TextPrimary,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = HMColor.TextSecondary)
        Text(value, fontSize = 13.sp, color = valueColor, fontWeight = FontWeight.Medium)
    }
}

/** Animated progress bar */
@Composable
fun HMProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = HMColor.BgOverlay,
    progressColor: Color = HMColor.GreenPrimary,
    height: Dp = 6.dp,
    cornerRadius: Dp = HMRadius.full
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "progress"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .fillMaxHeight()
                .clip(RoundedCornerShape(cornerRadius))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            progressColor.copy(alpha = 0.85f),
                            progressColor
                        )
                    )
                )
        )
    }
}

/** Status dot indicator */
@Composable
fun HMStatusDot(color: Color, size: Dp = 8.dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

/** Empty state placeholder */
@Composable
fun HMEmptyState(
    emoji: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = HMSpacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 40.sp)
        Spacer(Modifier.height(HMSpacing.md))
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = HMColor.TextPrimary
        )
        Spacer(Modifier.height(HMSpacing.xs))
        Text(
            subtitle,
            fontSize = 13.sp,
            color = HMColor.TextSecondary
        )
    }
}