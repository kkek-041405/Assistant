package com.kkek.assistant.data.repository

import com.kkek.assistant.data.model.Contact
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    fun getContacts(): Flow<List<Contact>>
    suspend fun refreshContacts()
}
