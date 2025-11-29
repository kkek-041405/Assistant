package com.kkek.assistant.data

import kotlinx.serialization.Serializable

@Serializable
data class Contact(
    val name: String,
    val phone: String
)
