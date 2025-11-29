package com.kkek.assistant.states

import com.kkek.assistant.data.Contact
import com.kkek.assistant.model.Kind
import com.kkek.assistant.model.ListItem
import com.kkek.assistant.model.ToolAction

object ContactsState {
    fun build(contacts: List<Contact>): List<ListItem> {
        return contacts.map { contact ->
            ListItem(
                kind = Kind.CONTACT,
                text = "${contact.name} ${contact.phone}",
                name = contact.name,
                phoneNumber = contact.phone,
                longNext = listOf(ToolAction("call_contact", mapOf("contactName" to contact.name))), // Requires a new 'call_contact' tool
                shortNext = listOf(ToolAction("next_item")),
                shortPrevious = listOf(ToolAction("previous_item")),
                longPrevious = listOf(ToolAction("show_default_list"))

            )
        }
    }
}