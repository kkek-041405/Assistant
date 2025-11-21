package com.kkek.assistant.model

/**
 * Model classes for items shown in the UI list.
 * Responsibility: simple, immutable data holders used by the UI.
 */

enum class Kind { SIMPLE, SUBLIST, TOGGLE, CONTACT }

data class ListItem(
    val kind: Kind = Kind.SIMPLE,
    // Common display text (used for SIMPLE, SUBLIST, TOGGLE)
    val text: String? = null,
    // Contact-specific fields
    val name: String? = null,
    val phoneNumber: String? = null,
    // Sublist (children)
    val sublist: List<ListItem>? = null,
    // Toggle state
    val isOn: Boolean = false,
    // App to launch
    val packageName: String? = null,

    // Directional actions for simple items (Next / Previous)
    val shortNext: List<Actions> = emptyList(),
    val shortPrevious: List<Actions> = emptyList(),
    val longNext: List<Actions> = emptyList(),
    val longPrevious: List<Actions> = emptyList(),
    val doubleNext: List<Actions> = emptyList(),
    val doublePrevious: List<Actions> = emptyList()
)
