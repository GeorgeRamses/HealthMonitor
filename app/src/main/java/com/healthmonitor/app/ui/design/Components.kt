package com.healthmonitor.app.ui.design

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri

// ─────────────────────────────────────────────────────────────────────────────
// HM TEXT FIELD — replaces all raw OutlinedTextField usages
// Features: focus ring animation, error state, leading/trailing icons
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HMTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    trailingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorMessage: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = when {
            isError -> HMColor.RedBright
            isFocused -> HMColor.GreenBright
            else -> HMColor.BorderDefault
        },
        animationSpec = tween(150),
        label = "border_color"
    )

    val labelColor by animateColorAsState(
        targetValue = when {
            isError -> HMColor.RedBright
            isFocused -> HMColor.GreenBright
            else -> HMColor.TextSecondary
        },
        animationSpec = tween(150),
        label = "label_color"
    )

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, fontSize = 12.sp) },
            placeholder = if (placeholder.isNotBlank()) {
                { Text(placeholder, color = HMColor.TextDisabled, fontSize = 13.sp) }
            } else null,
            leadingIcon = leadingIcon?.let {
                {
                    Icon(
                        it,
                        null,
                        tint = if (isFocused) HMColor.GreenBright else HMColor.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            trailingIcon = trailingText?.let {
                { Text(it, color = HMColor.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp)) }
            },
            isError = isError,
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            shape = RoundedCornerShape(HMRadius.sm),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = HMColor.TextPrimary,
                unfocusedTextColor = HMColor.TextPrimary,
                disabledTextColor = HMColor.TextDisabled,
                focusedBorderColor = borderColor,
                unfocusedBorderColor = HMColor.BorderDefault,
                errorBorderColor = HMColor.RedBright,
                focusedLabelColor = labelColor,
                unfocusedLabelColor = HMColor.TextSecondary,
                errorLabelColor = HMColor.RedBright,
                focusedContainerColor = HMColor.BgSurface,
                unfocusedContainerColor = HMColor.BgSurface,
                cursorColor = HMColor.GreenBright,
                focusedTrailingIconColor = HMColor.TextSecondary,
                unfocusedTrailingIconColor = HMColor.TextSecondary,
                focusedLeadingIconColor = if (isFocused) HMColor.GreenBright else HMColor.TextSecondary,
                unfocusedLeadingIconColor = HMColor.TextSecondary
            )
        )
        AnimatedVisibility(
            visible = isError && !errorMessage.isNullOrBlank(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Text(
                text = errorMessage ?: "",
                color = HMColor.RedBright,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 12.dp, top = 3.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HM PRIMARY BUTTON — animated press, gradient tint
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HMPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    color: Color = HMColor.GreenBright,
    contentColor: Color = HMColor.TextInverse
) {
    HMPressable(onClick = onClick, enabled = enabled, modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(HMRadius.sm))
                .background(
                    if (enabled)
                        Brush.horizontalGradient(
                            listOf(color.copy(alpha = 0.85f), color, color.copy(alpha = 0.9f))
                        )
                    else
                        Brush.horizontalGradient(listOf(HMColor.BgOverlay, HMColor.BgOverlay))
                )
                .border(
                    width = 1.dp,
                    color = if (enabled) color.copy(alpha = 0.5f) else HMColor.BorderSubtle,
                    shape = RoundedCornerShape(HMRadius.sm)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                leadingIcon?.let {
                    Icon(
                        it, null,
                        tint = if (enabled) contentColor else HMColor.TextDisabled,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (enabled) contentColor else HMColor.TextDisabled,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

/** Secondary outlined button */
@Composable
fun HMSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    color: Color = HMColor.GreenBright
) {
    HMPressable(onClick = onClick, enabled = enabled, modifier = modifier) {
        Box(
            modifier = Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(HMRadius.sm))
                .background(color.copy(alpha = if (enabled) 0.08f else 0f))
                .border(
                    1.dp,
                    if (enabled) color.copy(alpha = 0.4f) else HMColor.BorderSubtle,
                    RoundedCornerShape(HMRadius.sm)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                leadingIcon?.let {
                    Icon(it, null, tint = if (enabled) color else HMColor.TextDisabled, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = if (enabled) color else HMColor.TextDisabled
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SEGMENTED SELECTOR — replaces chip rows for severity / gender / options
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HMSegmentedSelector(
    options: List<String>,
    selectedOption: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    accentColors: List<Color>? = null // per-option color overrides
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HMRadius.sm))
            .background(HMColor.BgOverlay)
            .border(1.dp, HMColor.BorderSubtle, RoundedCornerShape(HMRadius.sm))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { idx, option ->
            val isSelected = option == selectedOption
            val accent = accentColors?.getOrNull(idx) ?: HMColor.GreenBright
            val bgAnim by animateColorAsState(
                targetValue = if (isSelected) accent.copy(alpha = 0.15f) else Color.Transparent,
                animationSpec = tween(200),
                label = "seg_bg"
            )
            val textAnim by animateColorAsState(
                targetValue = if (isSelected) accent else HMColor.TextSecondary,
                animationSpec = tween(200),
                label = "seg_text"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(HMRadius.xs))
                    .background(bgAnim)
                    .then(
                        if (isSelected) Modifier.border(
                            1.dp,
                            accent.copy(alpha = 0.35f),
                            RoundedCornerShape(HMRadius.xs)
                        )
                        else Modifier
                    )
                    .clickable { onSelect(option) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    color = textAnim,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HM DIALOG — base styled dialog wrapper
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HMDialog(
    onDismiss: () -> Unit,
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    dismissText: String = "إلغاء",
    confirmColor: Color = HMColor.GreenBright,
    confirmContentColor: Color = HMColor.TextInverse,
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HMColor.BgElevated,
        titleContentColor = HMColor.TextPrimary,
        textContentColor = HMColor.TextPrimary,
        title = {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                color = HMColor.TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        },
        confirmButton = {
            HMPressable(
                onClick = onConfirm,
                enabled = confirmEnabled
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(HMRadius.sm))
                        .background(if (confirmEnabled) confirmColor else HMColor.BgOverlay)
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        confirmText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = if (confirmEnabled) confirmContentColor else HMColor.TextDisabled
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText, color = HMColor.TextSecondary, fontSize = 13.sp)
            }
        },
        shape = RoundedCornerShape(HMRadius.lg)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// PERMISSION BANNER — replaces the inline banners in MedicationReminderScreen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HMPermissionBanner(
    title: String,
    subtitle: String,
    buttonText: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HMRadius.sm))
            .background(accentColor.copy(alpha = 0.08f))
            .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(HMRadius.sm))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HMColor.TextPrimary)
            Text(subtitle, fontSize = 11.sp, color = HMColor.TextSecondary, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(12.dp))
        HMPressable(onClick = onClick) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(HMRadius.xs))
                    .background(accentColor)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(buttonText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = HMColor.TextInverse)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SUCCESS BOTTOM SHEET — replaces AlertDialog success popups
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HMSuccessDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HMColor.BgElevated,
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(HMRadius.full))
                    .background(HMColor.GreenBg)
                    .border(1.dp, HMColor.GreenBorder, RoundedCornerShape(HMRadius.full)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    null,
                    tint = HMColor.GreenBright,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(title, fontWeight = FontWeight.SemiBold, color = HMColor.TextPrimary, textAlign = TextAlign.Center)
        },
        text = {
            Text(
                message,
                color = HMColor.TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            HMPrimaryButton(text = "موافق", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        },
        shape = RoundedCornerShape(HMRadius.lg)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// TOGGLE ROW — nicer switch row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HMToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color = HMColor.GreenBright,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = HMColor.TextPrimary, fontWeight = FontWeight.Medium)
            subtitle?.let {
                Text(it, fontSize = 11.sp, color = HMColor.TextSecondary, modifier = Modifier.padding(top = 1.dp))
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = HMColor.TextInverse,
                checkedTrackColor = accentColor,
                checkedBorderColor = accentColor,
                uncheckedThumbColor = HMColor.TextSecondary,
                uncheckedTrackColor = HMColor.BgOverlay,
                uncheckedBorderColor = HMColor.BorderDefault
            )
        )
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// ALARM PERMISSION UTILS
// ─────────────────────────────────────────────────────────────────────────────

object AlarmPermissionHelper {

    private const val TAG = "AlarmPermissionHelper"

    fun needsFullScreenIntentPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            return !nm.canUseFullScreenIntent()
        }
        return false
    }

    /**
     * Returns true if the device is from an OEM known to require manual
     * lock-screen permission. Checks both MANUFACTURER and BRAND so that
     * sub-brands (Redmi, POCO, Honor) are caught even when MANUFACTURER
     * reports the parent company name (e.g. "Xiaomi").
     * Logs device info so we can verify in Logcat.
     */
    fun isRestrictedOEM(): Boolean {
        val oemKeywords = listOf(
            "xiaomi", "redmi", "poco",
            "oppo", "vivo", "realme", "oneplus",
            "huawei", "honor",
            "samsung"
        )
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand        = Build.BRAND.lowercase()
        val result = oemKeywords.any { manufacturer.contains(it) || brand.contains(it) }
        android.util.Log.d(TAG,
            "isRestrictedOEM=$result | MANUFACTURER=${Build.MANUFACTURER} | BRAND=${Build.BRAND} | MODEL=${Build.MODEL}"
        )
        return result
    }

    /**
     * Returns true when the OEM warning card should be shown.
     * Logs every pref value so we can trace the exact reason it returns false.
     */
    fun needsOemPermission(context: Context): Boolean {
        val isOem   = isRestrictedOEM()
        if (!isOem) {
            android.util.Log.d(TAG, "needsOemPermission=false — not a restricted OEM")
            return false
        }
        val prefs   = context.getSharedPreferences("hm_permissions", Context.MODE_PRIVATE)
        val ignored = prefs.getBoolean("oem_warning_ignored", false)
        val granted = prefs.getBoolean("oem_permission_granted", false)
        val result  = !ignored && !granted
        android.util.Log.d(TAG,
            "needsOemPermission=$result | ignored=$ignored | granted=$granted"
        )
        return result
    }

    /**
     * Called once per screen open via LaunchedEffect(Unit).
     * Only resets "oem_warning_ignored" so a user who tapped "ignore" still
     * sees the card again on next visit.
     * NEVER touches "oem_permission_granted" — once the user confirms they
     * granted the permission, we must not clear that flag.
     */
    fun resetIgnoredFlag(context: Context) {
        android.util.Log.d(TAG, "resetIgnoredFlag — clearing oem_warning_ignored only")
        context.getSharedPreferences("hm_permissions", Context.MODE_PRIVATE)
            .edit {
                putBoolean("oem_warning_ignored", false)
                remove("oem_warning_seen")
            }
    }

    fun markOemPermissionGranted(context: Context) {
        android.util.Log.d(TAG, "markOemPermissionGranted")
        context.getSharedPreferences("hm_permissions", Context.MODE_PRIVATE)
            .edit { putBoolean("oem_permission_granted", true) }
    }

    fun markOemWarningIgnored(context: Context) {
        android.util.Log.d(TAG, "markOemWarningIgnored")
        context.getSharedPreferences("hm_permissions", Context.MODE_PRIVATE)
            .edit { putBoolean("oem_warning_ignored", true) }
    }

    fun clearOemWarningIgnored(context: Context) {
        android.util.Log.d(TAG, "clearOemWarningIgnored")
        context.getSharedPreferences("hm_permissions", Context.MODE_PRIVATE)
            .edit { putBoolean("oem_warning_ignored", false) }
    }

    fun openFullScreenIntentSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = "package:${context.packageName}".toUri()
            }
            context.startActivity(intent)
        }
    }

    fun openAppInfoSettings(context: Context) {
        // Opens the App Info screen. The user must manually click "Other Permissions".
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
        }
        context.startActivity(intent)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PERMISSION DIALOGS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HMAlarmPermissionDialogs(
    showA14Dialog: Boolean,
    showOemDialog: Boolean,
    onDismissA14: () -> Unit,
    onDismissOem: () -> Unit,
    onOemGranted: () -> Unit = onDismissOem
) {
    val context = LocalContext.current

    // Android 14+ System Dialog
    if (showA14Dialog) {
        AlertDialog(
            onDismissRequest = onDismissA14,
            shape = RoundedCornerShape(24.dp),
            containerColor = Color(0xFF1A1C1E),
            title = {
                Text("صلاحية التنبيه", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "لتفعيل شاشة المنبه عند قفل الهاتف، يرجى السماح بإشعارات ملء الشاشة.",
                    color = Color.LightGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        AlarmPermissionHelper.openFullScreenIntentSettings(context)
                        onDismissOem()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
                ) {
                    Text("الذهاب للإعدادات")
                }
            }
        )
    }

    // Xiaomi/Oppo/OEM Specific Dialog for "Show on Lock Screen"
    if (showOemDialog) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = onDismissOem,
            shape = RoundedCornerShape(24.dp),
            containerColor = Color(0xFF1A1C1E),
            title = {
                Text("إعدادات شاشة القفل", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "لضمان ظهور المنبه والهاتف مغلق، يرجى فتح الإعدادات ثم الدخول إلى:\n\n" +
                            "«أذونات أخرى» (Other Permissions)\n" +
                            "ثم تفعيل «العرض في شاشة القفل» (Show on Lock screen).",
                    color = Color.LightGray,
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                // Primary — open settings
                Button(
                    onClick = {
                        AlarmPermissionHelper.clearOemWarningIgnored(context)
                        AlarmPermissionHelper.openAppInfoSettings(context)
                        onDismissOem()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
                ) {
                    Text("فتح إعدادات التطبيق")
                }
            },
            dismissButton = {
                Row {

                    // Secondary — user already set the permission
                    OutlinedButton(
                        onClick = {
                            AlarmPermissionHelper.markOemPermissionGranted(context)
                            onOemGranted()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF42A5F5))
                    ) {
                        Text("✓ فعّلتها بالفعل")
                    }
                    TextButton(
                        onClick = {
                            AlarmPermissionHelper.markOemWarningIgnored(context)
                            onDismissOem()
                        }
                    ) {
                        Text("تجاهل نهائياً", color = Color.Gray)
                    }
                }

            }
        )
    }
}


@Composable
fun PermissionWarningCard(onAction: () -> Unit, onGranted: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF331010)),
        border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "تنبيه: المنبه قد لا يعمل",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    "اضغط هنا لضبط إعدادات شاشة القفل",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Opens the dialog / settings
                    TextButton(onClick = onAction) {
                        Text("فتح الإعدادات", color = Color.Red, fontSize = 12.sp)
                    }
                    // User confirms they already granted it
                    TextButton(onClick = {
                        onGranted()
                    }) {
                        Text("✓ فعّلتها", color = Color(0xFF42A5F5), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}