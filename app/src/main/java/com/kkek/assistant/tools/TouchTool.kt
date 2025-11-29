package com.kkek.assistant.tools

import android.content.Context
import com.kkek.assistant.System.touch.AccessibilityHelper
import com.kkek.assistant.domain.model.AiTool
import com.kkek.assistant.domain.model.ToolParameter
import com.kkek.assistant.domain.model.ToolResult
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

class TouchTool @Inject constructor(private val accessibilityHelper: AccessibilityHelper) : AiTool {
    override val name = "touch"
    override val description = "Simulates a touch gesture at a given coordinate."

    override val parameters = listOf(
        ToolParameter("x", "Int", "The x-coordinate of the touch point.", isRequired = true),
        ToolParameter("y", "Int", "The y-coordinate of the touch point.", isRequired = true)
    )

    override suspend fun execute(context: Context, params: Map<String, Any>, scope: CoroutineScope): ToolResult {
        val x = params["x"] as? Int
            ?: return ToolResult.Failure("X-coordinate was not provided or is not an integer.")
        val y = params["y"] as? Int
            ?: return ToolResult.Failure("Y-coordinate was not provided or is not an integer.")

        accessibilityHelper.click(x, y)
        return ToolResult.Success(mapOf("status" to "Touch event dispatched at ($x, $y)."))
    }
}
