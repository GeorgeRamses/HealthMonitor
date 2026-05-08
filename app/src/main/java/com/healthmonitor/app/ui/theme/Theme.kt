package com.healthmonitor.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.healthmonitor.app.ui.design.HMColor

// ─────────────────────────────────────────────────────────────────────────────
// Material 3 color scheme — mapped from HMColor tokens
// ─────────────────────────────────────────────────────────────────────────────

private val HMDarkColorScheme = darkColorScheme(
    // ── Core ─────────────────────────────────────────────────────────────────
    primary              = HMColor.GreenBright,
    onPrimary            = HMColor.TextInverse,
    primaryContainer     = HMColor.GreenDim,
    onPrimaryContainer   = HMColor.GreenBright,

    secondary            = HMColor.AmberPrimary,
    onSecondary          = HMColor.TextInverse,
    secondaryContainer   = HMColor.AmberBg,
    onSecondaryContainer = HMColor.AmberBright,

    tertiary             = HMColor.BluePrimary,
    onTertiary           = Color.White,
    tertiaryContainer    = HMColor.BlueBg,
    onTertiaryContainer  = HMColor.BlueBright,

    error                = HMColor.RedBright,
    onError              = Color.White,
    errorContainer       = HMColor.RedBg,
    onErrorContainer     = HMColor.RedBright,

    // ── Surfaces ──────────────────────────────────────────────────────────────
    background           = HMColor.BgBase,
    onBackground         = HMColor.TextPrimary,
    surface              = HMColor.BgSurface,
    onSurface            = HMColor.TextPrimary,
    surfaceVariant       = HMColor.BgElevated,
    onSurfaceVariant     = HMColor.TextSecondary,
    surfaceTint          = HMColor.GreenPrimary,

    // ── Outlines ──────────────────────────────────────────────────────────────
    outline              = HMColor.BorderDefault,
    outlineVariant       = HMColor.BorderSubtle,

    // ── Inverse ───────────────────────────────────────────────────────────────
    inverseSurface       = HMColor.TextPrimary,
    inverseOnSurface     = HMColor.BgBase,
    inversePrimary       = HMColor.GreenMuted,

    scrim                = Color(0xCC000000)
)

// ─────────────────────────────────────────────────────────────────────────────
// Typography — clean, hierarchical type scale optimised for Arabic + Latin
// ─────────────────────────────────────────────────────────────────────────────

private val HMTypography = Typography(
    // ── Display ──────────────────────────────────────────────────────────────
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize   = 44.sp,
        lineHeight = 50.sp,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize   = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.3).sp
    ),
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize   = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.2).sp
    ),

    // ── Headline ──────────────────────────────────────────────────────────────
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize   = 26.sp,
        lineHeight = 34.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize   = 22.sp,
        lineHeight = 30.sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize   = 18.sp,
        lineHeight = 26.sp
    ),

    // ── Title ─────────────────────────────────────────────────────────────────
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize   = 16.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),

    // ── Body ──────────────────────────────────────────────────────────────────
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize   = 15.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize   = 13.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize   = 11.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.1.sp
    ),

    // ── Label ─────────────────────────────────────────────────────────────────
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
)

// ─────────────────────────────────────────────────────────────────────────────
// Theme entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HealthMonitorTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = HMDarkColorScheme,
        typography  = HMTypography,
        content     = content
    )
}