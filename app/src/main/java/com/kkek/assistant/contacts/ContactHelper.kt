package com.kkek.assistant.contacts

import com.kkek.assistant.model.ListItem

/**
 * ContactHelper: returns app-provided contacts instead of querying device contacts.
 * Accepts an optional list of custom contacts; defaults to a small sample list.
 */
class ContactHelper(private val customContacts: List<ListItem.ContactItem> = DEFAULT_CONTACTS) {
    fun fetchContacts(): List<ListItem.ContactItem> {
        // Return a defensive copy
        return customContacts.toList()
    }

    companion object {
        private val DEFAULT_CONTACTS = listOf(
            ListItem.ContactItem("Dad", "+919391632589"),
            ListItem.ContactItem("MoM", "+916301638687"),
            ListItem.ContactItem("Charlie", "+10000000003")
        )
    }
}
