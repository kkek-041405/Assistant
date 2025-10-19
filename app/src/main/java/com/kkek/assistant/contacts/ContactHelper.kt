package com.kkek.assistant.contacts

import com.kkek.assistant.model.Kind
import com.kkek.assistant.model.ListItem

/**
 * ContactHelper: returns app-provided contacts instead of querying device contacts.
 * Accepts an optional list of custom contacts; defaults to a small sample list.
 */
class ContactHelper(private val customContacts: List<ListItem> = DEFAULT_CONTACTS) {
    fun fetchContacts(): List<ListItem> {
        // Return a defensive copy
        return customContacts.toList()
    }

    companion object {
        private val DEFAULT_CONTACTS = listOf(
            ListItem(kind = Kind.CONTACT,"Dad", "+919391632589"),
            ListItem(kind = Kind.CONTACT,"MoM", "+916301638687"),
            ListItem(kind = Kind.CONTACT,"Charlie", "+10000000003")
        )
    }
}
