package com.kkek.assistant.tools

import android.content.Context
import com.kkek.assistant.System.TTSHelper
import com.kkek.assistant.domain.model.AiTool
import com.kkek.assistant.domain.model.ToolParameter
import com.kkek.assistant.domain.model.ToolResult
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

class TtsTool @Inject constructor(private val ttsHelper: TTSHelper) : AiTool {
    override val name = "tts"
    override val description = "Synthesizes speech from text."

    override val parameters = listOf(
        ToolParameter("text", "String", "The text to be converted to speech.", isRequired = true)
    )

    override suspend fun execute(context: Context, params: Map<String, Any>, scope: CoroutineScope): ToolResult {
        val text = params["text"] as? String
            ?: return ToolResult.Failure("Text to speak was not provided.")

        ttsHelper.speak(text)
        return ToolResult.Success(mapOf("status" to "Speech synthesis initiated."))
    }
}
