package com.kkek.assistant.states

import com.kkek.assistant.model.Kind
import com.kkek.assistant.model.ListItem
import com.kkek.assistant.model.ToolAction

object DefaultState {
    fun build(): List<ListItem> = listOf(
        ListItem(kind = Kind.SIMPLE, text = "Tell time", longNext = listOf(ToolAction("status")),
            shortNext = listOf(ToolAction("next_item")),
            shortPrevious = listOf(ToolAction("previous_item")),
            longPrevious = listOf(ToolAction("show_default_list"))),
        ListItem(kind = Kind.SUBLIST, text = "Make call", longNext = listOf(ToolAction("show_contacts_list")),
            shortNext = listOf(ToolAction("next_item")),
            shortPrevious = listOf(ToolAction("previous_item")),
            longPrevious = listOf(ToolAction("show_default_list"))),
        ListItem(kind = Kind.SUBLIST, text = "Spotify", longNext = listOf(ToolAction("show_spotify_list")),
            shortNext = listOf(ToolAction("next_item")),
            shortPrevious = listOf(ToolAction("previous_item")),
            longPrevious = listOf(ToolAction("show_default_list"))),
        ListItem(kind = Kind.SUBLIST, text = "Apps", longNext = listOf(ToolAction("show_apps_list")),
            shortNext = listOf(ToolAction("next_item")),
            shortPrevious = listOf(ToolAction("previous_item")),
            longPrevious = listOf(ToolAction("show_default_list"))),
        ListItem(kind = Kind.SIMPLE, text = "Summarize Notifications", longNext = listOf(ToolAction("notification", mapOf("action" to "summarize"))),
            shortNext = listOf(ToolAction("next_item")),
            shortPrevious = listOf(ToolAction("previous_item")),
            longPrevious = listOf(ToolAction("show_default_list"))),
        ListItem(kind = Kind.SIMPLE, text = "Start Bubble Service", longNext = listOf(ToolAction("start_bubble_service")),
            shortNext = listOf(ToolAction("next_item")),
            shortPrevious = listOf(ToolAction("previous_item")),
            longPrevious = listOf(ToolAction("show_default_list"))),
        )
}