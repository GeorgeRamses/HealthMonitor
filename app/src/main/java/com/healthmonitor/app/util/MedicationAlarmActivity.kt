package com.healthmonitor.app.util

import android.app.KeyguardManager
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

/**
 * Full-screen alarm activity that appears OVER the lock screen.
 *
 * ── HOW LOCK SCREEN DISPLAY WORKS ───────────────────────────────────────────
 *
 * There are two distinct lock screen scenarios:
 *
 *  A) INSECURE lock (swipe only, no PIN/pattern/fingerprint):
 *     FLAG_DISMISS_KEYGUARD dismisses it entirely and shows the activity.
 *
 *  B) SECURE lock (PIN / pattern / fingerprint / face):
 *     FLAG_DISMISS_KEYGUARD does NOTHING — the system refuses to bypass
 *     security without user authentication.
 *     The correct solution is setShowWhenLocked(true) which renders the
 *     activity ON TOP OF the lock screen without dismissing it. The user
 *     sees the alarm, taps "Taken", and the device stays locked. This is
 *     exactly how the Android Clock alarm and Google Calendar reminders work.
 *
 * ── WHY requestDismissKeyguard() IS NOT USED ─────────────────────────────────
 *     requestDismissKeyguard() asks the system to fully unlock the device.
 *     On a secure lock screen it shows the PIN/pattern/face prompt first —
 *     which means the user must authenticate before they even see the alarm.
 *     That is the opposite of what we want. We want the alarm visible
 *     immediately, and the device can stay locked.
 *
 * ── WHAT setShowWhenLocked(true) DOES ────────────────────────────────────────
 *     API 27+ (Android 8.1+): The activity window is drawn over the keyguard.
 *     The lock screen is still active behind it — but the alarm is fully
 *     interactive on top. The user can tap Taken/Snooze/Dismiss without
 *     unlocking. After they dismiss the alarm the lock screen reappears.
 *     This is the correct, secure, user-friendly behavior.
 *
 * ── PRE-API-27 FALLBACK ───────────────────────────────────────────────────────
 *     FLAG_SHOW_WHEN_LOCKED does the same thing via the window flags API.
 *     Both are set here for maximum compatibility.
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

        // ── 1. Acquire WakeLock before super.onCreate() ───────────────────────
        // ACQUIRE_CAUSES_WAKEUP physically turns the screen on.
        // Must be called BEFORE super.onCreate() on Samsung/Xiaomi/OPPO.
        acquireWakeLock()

        // ── 2. Window flags before super.onCreate() ───────────────────────────
        // FLAG_SHOW_WHEN_LOCKED  → draw this window over the keyguard (all APIs)
        // FLAG_TURN_SCREEN_ON    → turn the display on when this activity starts
        // FLAG_KEEP_SCREEN_ON    → keep the display on while activity is visible
        // FLAG_DISMISS_KEYGUARD  → dismiss INSECURE keyguard (swipe-only locks)
        //                          Has no effect on secure (PIN/pattern) locks —
        //                          setShowWhenLocked handles those instead.
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED  or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON    or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON    or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        acquireWakeLock()

        super.onCreate(savedInstanceState)

        // ── 3. Modern API equivalents (API 27+) ───────────────────────────────
        // setShowWhenLocked(true) is the KEY call that makes the activity appear
        // over a secure (PIN/pattern/fingerprint) lock screen without requiring
        // the user to authenticate first. This is what was missing before.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)   // SHOW OVER lock screen (not bypass it)
            setTurnScreenOn(true)     // Turn display on
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
    // E.g. the user taps the heads-up banner while screen is on and alarm is open.

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
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            wakeLock = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK        or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or   // ← physically wakes the display
                        PowerManager.ON_AFTER_RELEASE,
                "HealthMonitor:MedicationAlarm"
            ).also { it.acquire(3 * 60 * 1000L) }       // 3 min ceiling
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
            vibrator?.let { v ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(pattern, 0)
                }
            }
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
            flags = Intent.FLAG_ACTIVITY_NEW_TASK       or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION or
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

    // Auto-snooze after 2 minutes if ignored
    LaunchedEffect(Unit) {
        delay(120_000L)
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
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape    = RoundedCornerShape(18.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
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
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape    = RoundedCornerShape(18.dp),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFB74D))
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