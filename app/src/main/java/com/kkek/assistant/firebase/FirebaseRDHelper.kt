package com.kkek.assistant.firebase

import android.util.Log
import com.google.firebase.database.ktx.database
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.kkek.assistant.notification.Notification
import com.kkek.assistant.telecom.Contact
import kotlinx.coroutines.tasks.await

object FirebaseRDHelper {

    private val RealTimeDB = Firebase.database.reference
    private val FirestoreDB = Firebase.firestore

    fun uploadNotification(notification: Notification) {
        RealTimeDB.child("notifications").child(notification.id).setValue(notification)
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
