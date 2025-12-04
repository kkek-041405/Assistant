package com.kkek.assistant.System.notification

import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.kkek.assistant.data.AssistantRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NotificationListener : NotificationListenerService() {

    @Inject
    lateinit var repository: AssistantRepository

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        sbn?.let {
            val packageName = it.packageName
            val notification = it.notification
            val title = notification.extras.getString("android.title", "")
            val text = notification.extras.getString("android.text", "")
            val appName = getAppName(packageName)

            if (!title.isNullOrBlank() || !text.isNullOrBlank()) { // Changed to OR to catch notifications with only one field
                // Call the parser to check for an OTP
                val otpCode = OtpParser.findOtpCode(title, text)

                val appNotification = AppNotification(
                    appName = appName,
                    title = title,
                    text = text,
                    timestamp = System.currentTimeMillis(),
                    otpCode = otpCode // Set the extracted OTP code here
                )

                // Now your repository can decide what to do if otpCode is not null
                repository.handleNewNotification(appNotification)
            }
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = applicationContext.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // We can handle notification removal here if needed in the future
    }
}
