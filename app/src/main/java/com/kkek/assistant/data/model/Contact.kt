package com.kkek.assistant.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.OptIn
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(InternalSerializationApi::class)
@Entity(tableName = "contacts")
@Serializable
data class Contact(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val phoneNumber: String
)
