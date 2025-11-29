package com.kkek.assistant.model

/**
 * Model classes for items shown in the UI list.
 * Responsibility: simple, immutable data holders used by the UI.
 */

enum class Kind { SIMPLE, SUBLIST, TOGGLE, CONTACT }

/**
 * Represents a deferred tool execution, specifying which tool to run and with what parameters.
 * This is a pure data holder, created by the ViewModel and interpreted by the UI layer to trigger actions.
 */
data class ToolAction(
    val toolId: String,
    val params: Map<String, Any> = emptyMap()
)

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

    // Directional actions that map to tool executions
    val shortNext: List<ToolAction> = emptyList(),
    val shortPrevious: List<ToolAction> = emptyList(),
    val longNext: List<ToolAction> = emptyList(),
    val longPrevious: List<ToolAction> = emptyList(),
    val doubleNext: List<ToolAction> = emptyList(),
    val doublePrevious: List<ToolAction> = emptyList(),
)
