package com.kkek.assistant.tools

import android.content.Context
import com.kkek.assistant.domain.model.AiTool
import com.kkek.assistant.domain.model.ToolParameter
import com.kkek.assistant.domain.model.ToolResult
import com.kkek.assistant.music.SpotifyHelper
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

class SpotifyTool @Inject constructor(private val spotifyHelper: SpotifyHelper) : AiTool {
    override val name = "spotify"
    override val description = "Controls Spotify playback."

    override val parameters = listOf(
        ToolParameter("action", "String", "The playback action to perform (e.g., 'play_pause', 'next').", isRequired = true)
    )

    override suspend fun execute(context: Context, params: Map<String, Any>, scope: CoroutineScope): ToolResult {
        return when (val action = params["action"] as? String) {
            "play_pause" -> {
                spotifyHelper.playPause()
                ToolResult.Success(mapOf("status" to "Playback toggled."))
            }
            "next" -> {
                spotifyHelper.next()
                ToolResult.Success(mapOf("status" to "Skipped to next track."))
            }
            "previous" -> {
                spotifyHelper.previous()
                ToolResult.Success(mapOf("status" to "Skipped to previous track."))
            }
            "seek_forward" -> {
                spotifyHelper.seekForward()
                ToolResult.Success(mapOf("status" to "Seeked forward."))
            }
            "seek_backward" -> {
                spotifyHelper.seekBackward()
                ToolResult.Success(mapOf("status" to "Seeked backward."))
            }
            else -> ToolResult.Failure("Invalid or missing action: $action")
        }
    }
}
