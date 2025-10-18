package com.kkek.assistant.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * NotificationHelper (notification subpackage): encapsulates notification logic.
 * Responsibility: create channels and post full-screen notifications. Keeps
 * notification concerns separate from UI and telecom logic (SRP).
 */
class NotificationHelper(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "TestChannel"
        const val NOTIFICATION_ID = 2
    }

    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Test Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }


}
