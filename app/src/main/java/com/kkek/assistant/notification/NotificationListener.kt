package com.kkek.assistant.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.kkek.assistant.firebase.FirebaseRDHelper

class NotificationListener : NotificationListenerService() {



    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = Notification(
            id = sbn.key,
            packageName = sbn.packageName,
            title = sbn.notification.extras.getString("android.title") ?: "",
            text = sbn.notification.extras.getString("android.text") ?: "",
            timestamp = sbn.postTime
        )
        FirebaseRDHelper.uploadNotification(notification)
    }
}
