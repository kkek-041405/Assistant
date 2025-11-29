package com.kkek.assistant.repository

import android.util.Log
import com.google.firebase.database.ktx.database
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.kkek.assistant.System.notification.AppNotification
import com.kkek.assistant.data.CallDetails
import com.kkek.assistant.data.Contact
import kotlinx.coroutines.tasks.await

object FirebaseRepository {

    private val RealTimeDB = Firebase.database.reference
    private val FirestoreDB = Firebase.firestore

    fun uploadNotification(notification: AppNotification) {
        FirestoreDB.collection("notifications").add(notification)
            .addOnSuccessListener { documentReference ->
                Log.d("FirebaseRepository", "Notification uploaded successfully with ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseRepository", "Failed to upload notification.", e)
            }
    }

    fun uploadCallDetails(callDetails: CallDetails) {
        RealTimeDB.child("calls").push().setValue(callDetails)
            .addOnSuccessListener {
                Log.d("FirebaseRepository", "Call details uploaded successfully.")
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseRepository", "Failed to upload call details.", e)
            }
    }

    suspend fun getContacts(): List<Contact> {
        Log.d("FirebaseDB", "Getting contacts from Firestore")
        val contacts = mutableListOf<Contact>()

        try {
            // Fetch documents from the "contacts" collection in Firestore
            val querySnapshot = FirestoreDB.collection("contacts").get().await()

            // Loop through each document in the result
            for (document in querySnapshot.documents) {
                // Use .getString() for safer type casting and provide a default value
                val name = document.getString("name") ?: ""
                val phone = document.getString("phone") ?: ""

                if (name.isNotEmpty() && phone.isNotEmpty()) {
                    contacts.add(Contact(name, phone))
                }
            }
            Log.d("FirebaseDB", "Fetched contacts from Firestore: $contacts")

        } catch (e: Exception) {
            Log.e("FirebaseDB", "Failed to get contacts from Firestore", e)
        }

        Log.d("FirebaseDB", "Returning contacts: $contacts")
        return contacts
    }
}
