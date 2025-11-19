package com.kkek.assistant.convex

import android.util.Log
import androidx.lifecycle.Lifecycle
import com.kkek.assistant.telecom.Contact
import dev.convex.android.ConvexClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

object ConvexDB {
    private const val CONVEX_URL = "https://strong-dogfish-175.convex.cloud"



    val client = ConvexClient(CONVEX_URL)

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())

    private val applicationScope = CoroutineScope(SupervisorJob())


    val contact: StateFlow<List<Contact>> = _contacts.asStateFlow()

    fun startContactsListener() {
        Log.d("ConvexDB", "Starting contacts listener")
        applicationScope.launch {
            client.subscribe<List<Contact>>("contacts:get").collect { result ->
                result.onSuccess { receivedContacts ->
                    _contacts.value = receivedContacts
                    Log.d("ConvexDB", "Fetched contacts: $receivedContacts")
                }.onFailure {
                    Log.d("ConvexDB", "Error fetching contacts: ${it.message}")
                }
            }
        }
    }
}