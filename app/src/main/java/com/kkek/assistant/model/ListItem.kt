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

    // Directional actions for simple items (Next / Previous)
    val shortNext: Actions? = null,
    val shortPrevious: Actions? = null,
    val longNext: Actions? = null,
    val longPrevious: Actions? = null,
    val doubleNext: Actions? = null,
    val doublePrevious: Actions? = null
)
