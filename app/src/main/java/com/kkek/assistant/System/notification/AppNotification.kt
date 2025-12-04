package com.kkek.assistant.System.notification

data class AppNotification(
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val otpCode: String? = null
)
