package com.kkek.assistant.domain.model

import android.content.Context
import kotlinx.coroutines.CoroutineScope

/**
 * Defines the contract for an AI tool.
 */
interface AiTool {
    val name: String
    val description: String

    /**
     * Defines the parameters that this tool accepts.
     */
    val parameters: List<ToolParameter>

    /**
     * Executes the tool's logic.
     *
     * @param context The Android context.
     * @param params A map of parameter names to values. The framework will validate
     *               these against the `parameters` definition before execution.
     * @param scope The coroutine scope to support cancellation.
     * @return A [ToolResult] indicating the outcome.
     */
    suspend fun execute(
        context: Context,
        params: Map<String, Any>,
        scope: CoroutineScope
    ): ToolResult
}

/**
 * Describes a single parameter for an [AiTool].
 *
 * @property name The name of the parameter.
 * @property type The expected data type of the parameter (e.g., "String", "Int", "File").
 * @property description A description of what the parameter is for.
 * @property isRequired Whether the parameter must be provided for the tool to execute.
 */
data class ToolParameter(
    val name: String,
    val type: String,
    val description: String,
    val isRequired: Boolean
)
