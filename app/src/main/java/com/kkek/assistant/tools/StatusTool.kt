package com.kkek.assistant.tools

import android.content.Context
import com.kkek.assistant.data.AssistantRepository
import com.kkek.assistant.domain.model.AiTool
import com.kkek.assistant.domain.model.ToolParameter
import com.kkek.assistant.domain.model.ToolResult
import kotlinx.coroutines.CoroutineScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class StatusTool @Inject constructor(private val repository: AssistantRepository) : AiTool {
    override val name = "status"
    override val description = "Gets the current time and battery status."

    override val parameters: List<ToolParameter> = emptyList()

    override suspend fun execute(context: Context, params: Map<String, Any>, scope: CoroutineScope): ToolResult {
        val sdf = SimpleDateFormat("h:mm a", Locale.US)
        val currentTime = sdf.format(Date())

        val currentBattery = repository.updateBatteryPercentage()

        val text = if (currentBattery != -1) {
            "It's $currentTime with $currentBattery percent"
        } else {
            "It's $currentTime. Battery percentage unknown"
        }

        return ToolResult.Success(mapOf("speechOutput" to text))
    }
}
