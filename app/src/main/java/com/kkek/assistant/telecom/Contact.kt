package com.kkek.assistant.telecom

import kotlinx.serialization.Serializable

@Serializable
data class Contact(
    val name: String,
    val phone: String
)