package com.healthmonitor.app.util

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.healthmonitor.app.data.local.HealthMonitorDatabase
import com.healthmonitor.app.data.local.entities.MedicationLogEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Full-screen alarm activity that appears OVER the lock screen.
 *
 * ── FIX SUMMARY ──────────────────────────────────────────────────────────────
 *
 * 1. REMOVED duplicate acquireWakeLock() call in onCreate().
 *    The original code called it twice (before window flags AND after them).
 *    The second call was a bug — it tried to re-acquire an already-held lock
 *    which on some Samsung/Xiaomi devices causes the second acquire to block
 *    briefly and delay the window flags from taking effect before super.onCreate().
 *
 * 2. REMOVED FLAG_ACTIVITY_NO_USER_ACTION from createIntent().
 *    This flag tells Android "don't count this as a user interaction", which
 *    suppresses the screen-wakeup side-effects on Pixel and OnePlus devices.
 *    The net result was the screen stayed off even when the alarm fired.
 *
 * 3. ADDED android:showWhenLocked + android:turnScreenOn in the manifest
 *    (see AndroidManifest.xml). The programmatic setShowWhenLocked() calls are
 *    kept here as well — both are required because some OEMs (MIUI, ColorOS)
 *    only respect one or the other.
 *
 * 4. ADDED canUseFullScreenIntent() fallback for Android 14+.
 *    On API 34+, USE_FULL_SCREEN_INTENT requires explicit user grant via
 *    Settings. If the permission is missing the notification never promotes to
 *    full-screen. We now detect this and call startActivity() directly as a
 *    fallback so the alarm still appears when the screen is already on.
 *
 * ── HOW LOCK SCREEN DISPLAY WORKS ───────────────────────────────────────────
 *
 *  A) INSECURE lock (swipe only):
 *     FLAG_DISMISS_KEYGUARD dismisses it and shows the activity directly.
 *
 *  B) SECURE lock (PIN / pattern / fingerprint):
 *     FLAG_DISMISS_KEYGUARD has no effect. setShowWhenLocked(true) renders
 *     the activity ON TOP of the lock screen — the user sees and interacts
 *     with the alarm without unlocking. This matches Clock app behavior.
 */
@AndroidEntryPoint
class MedicationAlarmActivity : ComponentActivity() {

    @Inject
    lateinit var database: HealthMonitorDatabase

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // Reactive state — updated by onNewIntent so UI refreshes without recreation
    private val medicationIdState   = mutableStateOf("")
    private val medicationNameState = mutableStateOf("الدواء")
    private val dosageState         = mutableStateOf("")
    private val scheduledTimeState  = mutableStateOf("")

    // ── onCreate ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {

        // ── 1. Acquire WakeLock ONCE before super.onCreate() ──────────────────
        // ACQUIRE_CAUSES_WAKEUP physically turns the screen on.
        // Must be called BEFORE window flags and super.onCreate() on Samsung/Xiaomi/OPPO.
        // FIX: Only called once here. The duplicate call that was below window.addFlags()
        // has been removed — it caused a race on some devices.
        acquireWakeLock()

        // ── 2. Window flags before super.onCreate() ───────────────────────────
        // FLAG_SHOW_WHEN_LOCKED  → draw window over keyguard (deprecated API 27 fallback)
        // FLAG_TURN_SCREEN_ON    → turn display on (deprecated API 27 fallback)
        // FLAG_KEEP_SCREEN_ON    → keep display on while visible
        // FLAG_DISMISS_KEYGUARD  → dismiss INSECURE keyguard only (swipe-to-unlock)
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED  or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON    or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON    or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        super.onCreate(savedInstanceState)

        // ── 3. Modern API equivalents (API 27+) ───────────────────────────────
        // setShowWhenLocked(true) — KEY call for rendering over SECURE lock screens
        // (PIN/pattern/fingerprint) without requiring authentication first.
        // setTurnScreenOn(true)  — modern equivalent of FLAG_TURN_SCREEN_ON.
        // Both the manifest attributes AND these calls are needed for full OEM coverage.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        // ── 4. Read intent extras and start alarm ─────────────────────────────
        initAlarm(intent)

        // ── 5. Draw UI ────────────────────────────────────────────────────────
        setContent {
            AlarmScreen(
                medicationNameState = medicationNameState,
                dosageState         = dosageState,
                scheduledTimeState  = scheduledTimeState,
                snoozeMinutes       = SNOOZE_MINUTES,
                onTaken = {
                    lifecycleScope.launch {
                        markAsTaken(medicationIdState.value, scheduledTimeState.value)
                        stopAlarm()
                        finish()
                    }
                },
                onSnooze = {
                    AlarmScheduler.scheduleSnooze(
                        context        = this@MedicationAlarmActivity,
                        medicationId   = medicationIdState.value,
                        medicationName = medicationNameState.value,
                        dosage         = dosageState.value,
                        scheduledTime  = scheduledTimeState.value,
                        snoozeMinutes  = SNOOZE_MINUTES
                    )
                    stopAlarm()
                    finish()
                },
                onDismiss = {
                    stopAlarm()
                    finish()
                }
            )
        }
    }

    // ── onNewIntent ───────────────────────────────────────────────────────────
    // Fired when launchMode=singleInstance and the activity is already running.
    // E.g. the user taps the heads-up banner while the screen is on.

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        initAlarm(intent)
    }

    // ── initAlarm ─────────────────────────────────────────────────────────────

    private fun initAlarm(intent: Intent) {
        medicationIdState.value   = intent.getStringExtra(MedicationAlarmReceiver.EXTRA_MEDICATION_ID)   ?: ""
        medicationNameState.value = intent.getStringExtra(MedicationAlarmReceiver.EXTRA_MEDICATION_NAME) ?: "الدواء"
        dosageState.value         = intent.getStringExtra(MedicationAlarmReceiver.EXTRA_DOSAGE)          ?: ""
        scheduledTimeState.value  = intent.getStringExtra(MedicationAlarmReceiver.EXTRA_SCHEDULED_TIME)  ?: ""
        stopSound()
        startAlarmSound()
        startVibration()
    }

    // ── WakeLock ──────────────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == true) return   // guard against accidental double-acquire
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            wakeLock = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK        or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or   // physically wakes the display
                        PowerManager.ON_AFTER_RELEASE,
                "HealthMonitor:MedicationAlarm"
            ).also { it.acquire(3 * 60 * 1000L) }       // 3-minute ceiling
        } catch (e: Exception) {
            android.util.Log.e(TAG, "WakeLock acquire failed: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            wakeLock = null
        } catch (e: Exception) {
            android.util.Log.e(TAG, "WakeLock release failed: ${e.message}")
        }
    }

    // ── Sound ─────────────────────────────────────────────────────────────────

    private fun startAlarmSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@MedicationAlarmActivity, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "MediaPlayer failed: ${e.message}")
        }
    }

    private fun stopSound() {
        runCatching { mediaPlayer?.stop(); mediaPlayer?.release() }
        mediaPlayer = null
    }

    // ── Vibration ─────────────────────────────────────────────────────────────

    private fun startVibration() {
        val pattern = longArrayOf(0, 600, 300, 600, 300)
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Vibrator failed: ${e.message}")
        }
    }

    // ── Stop all ──────────────────────────────────────────────────────────────

    private fun stopAlarm() {
        stopSound()
        vibrator?.cancel()
        vibrator = null
        releaseWakeLock()

        val notificationId = (medicationIdState.value + scheduledTimeState.value).hashCode()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(notificationId)
    }

    // ── Mark dose taken ───────────────────────────────────────────────────────

    private suspend fun markAsTaken(medicationId: String, scheduledTime: String) {
        if (medicationId.isBlank()) return
        withContext(Dispatchers.IO) {
            val today = startOfDayMillis()
            val dao   = database.medicationLogDao()
            val log   = dao.getLogForDose(medicationId, today, scheduledTime)
            if (log != null) {
                dao.update(log.copy(taken = true, lastModifiedAt = System.currentTimeMillis()))
            } else {
                val patientId = database.medicationDao()
                    .getMedicationById(medicationId)?.patientId ?: ""
                dao.insert(
                    MedicationLogEntity(
                        medicationId  = medicationId,
                        patientId     = patientId,
                        date          = today,
                        scheduledTime = scheduledTime,
                        time          = System.currentTimeMillis(),
                        taken         = true
                    )
                )
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Block back — user must explicitly tap Taken / Snooze / Dismiss
    }

    // ── Intent factory ────────────────────────────────────────────────────────

    companion object {
        private const val TAG    = "MedicationAlarmActivity"
        const val SNOOZE_MINUTES = 10

        fun createIntent(
            context: Context,
            medicationId: String,
            medicationName: String,
            dosage: String,
            scheduledTime: String
        ) = Intent(context, MedicationAlarmActivity::class.java).apply {
            // FIX: Removed FLAG_ACTIVITY_NO_USER_ACTION — this flag suppresses the
            // screen-wakeup side-effects on Pixel and OnePlus devices, causing the
            // screen to stay off even though the alarm fired correctly.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MedicationAlarmReceiver.EXTRA_MEDICATION_ID,   medicationId)
            putExtra(MedicationAlarmReceiver.EXTRA_MEDICATION_NAME, medicationName)
            putExtra(MedicationAlarmReceiver.EXTRA_DOSAGE,          dosage)
            putExtra(MedicationAlarmReceiver.EXTRA_SCHEDULED_TIME,  scheduledTime)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Alarm Screen UI
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AlarmScreen(
    medicationNameState: MutableState<String>,
    dosageState: MutableState<String>,
    scheduledTimeState: MutableState<String>,
    snoozeMinutes: Int,
    onTaken: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    val medicationName by medicationNameState
    val dosage         by dosageState
    val scheduledTime  by scheduledTimeState

    // Auto-snooze after 2 minutes if the user ignores the alarm
    LaunchedEffect(Unit) {
        delay(120_000L.milliseconds)
        onSnooze()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.12f,
        animationSpec = infiniteRepeatable(
            animation  = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A1628), Color(0xFF0D2137), Color(0xFF0A1628))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .scale(scale)
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1565C0))
                    .border(3.dp, Color(0xFF42A5F5), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("💊", fontSize = 52.sp)
            }

            Text(
                "وقت الدواء",
                color         = Color(0xFF90CAF9),
                fontSize      = 16.sp,
                fontWeight    = FontWeight.Medium,
                letterSpacing = 2.sp
            )

            Text(
                format12Hour(scheduledTime),
                color      = Color(0xFF42A5F5),
                fontSize   = 36.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                medicationName,
                color      = Color.White,
                fontSize   = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
                lineHeight = 34.sp
            )

            if (dosage.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF1565C0).copy(alpha = 0.6f)
                ) {
                    Text(
                        "الجرعة: $dosage",
                        color    = Color(0xFF90CAF9),
                        fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick  = onTaken,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape  = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
            ) {
                Text(
                    "✓   تم أخذ الجرعة",
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color(0xFF69F0AE)
                )
            }

            OutlinedButton(
                onClick  = onSnooze,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape  = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFB74D))
            ) {
                Text(
                    "⏰   تأجيل $snoozeMinutes دقائق",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            TextButton(onClick = onDismiss) {
                Text("تجاهل", color = Color(0xFF546E7A), fontSize = 14.sp)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}