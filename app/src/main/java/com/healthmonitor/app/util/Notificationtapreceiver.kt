package com.healthmonitor.app.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.healthmonitor.app.MainActivity

/**
 * Fired when the user taps a medication alarm notification.
 *
 * Responsibility:
 *   1. Cancel the notification immediately (removes it from the shade).
 *   2. Launch MainActivity so the app opens.
 *
 * Why a BroadcastReceiver instead of a direct Activity PendingIntent:
 *   setOngoing(true) notifications cannot be dismissed by swiping, and
 *   setAutoCancel(true/false) only controls swipe-to-dismiss, NOT tap-to-open.
 *   When the contentIntent is a direct Activity PendingIntent Android opens the
 *   app but does NOT cancel an ongoing notification. By routing through this
 *   receiver we can cancel the notification programmatically before handing off
 *   to the activity — giving the user a clean, no-lingering-notification experience.
 */
class NotificationTapReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        // Cancel the notification first
        if (notificationId != -1) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(notificationId)
            Log.i(TAG, "notification $notificationId cancelled on tap")
        }

        // Launch the main app
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        // On Android 10+ we need to wrap in a PendingIntent and send it
        // to get the foreground-launch exemption from the notification tap
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pi = PendingIntent.getActivity(
                context,
                notificationId,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                pi.send()
            } catch (e: PendingIntent.CanceledException) {
                Log.e(TAG, "PendingIntent cancelled: ${e.message}")
            }
        } else {
            context.startActivity(appIntent)
        }
    }

    companion object {
        private const val TAG = "NotificationTapReceiver"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

    }
}