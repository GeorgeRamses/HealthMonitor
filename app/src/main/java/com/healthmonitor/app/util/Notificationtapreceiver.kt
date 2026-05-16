package com.healthmonitor.app.util

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

        // Cancel the notification first so it disappears immediately on tap
        if (notificationId != -1) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(notificationId)
            Log.i(TAG, "notification $notificationId cancelled on tap")
        }

        // Launch the main app
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(appIntent)
    }

    companion object {
        private const val TAG = "NotificationTapReceiver"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        /**
         * Build the PendingIntent used as the notification's contentIntent.
         * Pass the same [notificationId] used in manager.notify() so the
         * receiver knows exactly which notification to cancel.
         */
        fun buildPendingIntent(context: Context, notificationId: Int): android.app.PendingIntent {
            val intent = Intent(context, NotificationTapReceiver::class.java).apply {
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            return android.app.PendingIntent.getBroadcast(
                context,
                (notificationId.toString() + "tap").hashCode(),
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}