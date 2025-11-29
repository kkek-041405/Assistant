package com.kkek.assistant.states

import com.kkek.assistant.model.Kind
import com.kkek.assistant.model.ListItem
import com.kkek.assistant.model.ToolAction

object SpotifyState {
    fun build(isConnected: Boolean): List<ListItem> = listOf(
        ListItem(
            kind = Kind.SIMPLE,
            text = "Play/Pause",
            shortNext = listOf(ToolAction("spotify", mapOf("action" to "next"))),
            shortPrevious = listOf(ToolAction("spotify", mapOf("action" to "previous"))),
            longNext = listOf(ToolAction("spotify", mapOf("action" to "seek_forward"))),
            longPrevious = listOf(ToolAction("spotify", mapOf("action" to "seek_backward"))),
            doubleNext = listOf(ToolAction("spotify", mapOf("action" to "play_pause"))),
            doublePrevious = listOf(ToolAction("show_default_list"))
        ),
        ListItem(
            kind = Kind.TOGGLE,
            text = if (isConnected) "Connected" else "Disconnected",
            isOn = isConnected
        )
    )
}