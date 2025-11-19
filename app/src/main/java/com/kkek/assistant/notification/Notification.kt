package com.kkek.assistant.notification

data class Notification(
    val id: String,
    val packageName: String,
    val title: String,
    val text: String,
    val timestamp: Long
)