package com.kkek.assistant.domain.usecase

import android.content.Context
import com.kkek.assistant.domain.model.AiTool
import com.kkek.assistant.domain.model.ToolResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException

class ToolExecutor(private val context: Context) {

    /**
     * Executes a given tool with the provided parameters.
     *
     * @param tool The [AiTool] to execute.
     * @param params A map of parameter names to values.
     * @param scope The coroutine scope for the execution.
     * @return A [ToolResult] indicating the outcome.
     */
    suspend fun execute(tool: AiTool, params: Map<String, Any>, scope: CoroutineScope): ToolResult {
        return try {
            val validationResult = validateParameters(tool, params)
            if (validationResult is ToolResult.Failure) {
                return validationResult
            }

            tool.execute(context, params, scope)
        } catch (e: CancellationException) {
            ToolResult.Failure("Tool execution was cancelled.")
        } catch (e: Exception) {
            ToolResult.Failure("An unexpected error occurred during tool execution: ${e.message}", e)
        }
    }

    /**
     * Validates the provided parameters against the tool's definition.
     *
     * @param tool The [AiTool] whose parameters are to be validated.
     * @param params The parameters to validate.
     * @return A [ToolResult.Success] if validation passes, otherwise a [ToolResult.Failure].
     */
    private fun validateParameters(tool: AiTool, params: Map<String, Any>): ToolResult {
        for (paramDef in tool.parameters) {
            if (paramDef.isRequired && !params.containsKey(paramDef.name)) {
                return ToolResult.Failure("Missing required parameter: ${paramDef.name}")
            }
            // Here, you could add more sophisticated type checking based on `paramDef.type`
        }
        return ToolResult.Success()
    }
}
