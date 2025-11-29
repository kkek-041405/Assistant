package com.kkek.assistant.states

import com.kkek.assistant.model.Kind
import com.kkek.assistant.model.ListItem
import com.kkek.assistant.model.ToolAction

object InCallState {
    fun build(): List<ListItem> = listOf(
        ListItem(
            kind = Kind.SIMPLE,
            text = "End Call",
            longNext = listOf(ToolAction("end_call")),
            shortNext = listOf(ToolAction("next_item")),
            shortPrevious = listOf(ToolAction("previous_item")),
            longPrevious = listOf(ToolAction("show_default_list"))
        )
    )
}