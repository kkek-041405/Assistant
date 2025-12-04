package com.kkek.assistant.data

import kotlinx.serialization.Serializable

@Serializable
data class CallDetails(
    val timestamp: Long,
    val phoneNumber: String?,
    val type: String, // e.g., INCOMING, OUTGOING, MISSED
    val duration: Int // in seconds
)
