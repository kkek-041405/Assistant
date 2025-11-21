package com.kkek.assistant.System.notification

data class Notification(
    val id: String,
    val packageName: String,
    val title: String,
    val text: String,
    val timestamp: Long
)