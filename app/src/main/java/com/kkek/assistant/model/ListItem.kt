package com.kkek.assistant.model

/**
 * Model classes for items shown in the UI list.
 * Responsibility: simple, immutable data holders used by the UI.
 */
sealed class ListItem {
    data class SimpleItem(val text: String) : ListItem()
    data class SublistItem(val text: String, val sublist: List<ListItem>) : ListItem()
    data class ToggleItem(val text: String, val isOn: Boolean = false) : ListItem()
    data class ContactItem(val name: String, val phoneNumber: String) : ListItem()
}

