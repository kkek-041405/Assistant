package com.kkek.assistant.domain.model

/**
 * Represents the result of a tool execution.
 * This sealed class encapsulates success and failure states,
 * providing a robust way to handle outcomes.
 */
sealed class ToolResult {

    /**
     * Represents a successful tool execution.
     *
     * @property data A map of key-value pairs representing the output of the tool.
     *                This provides a flexible way for tools to return data.
     */
    data class Success(val data: Map<String, Any> = emptyMap()) : ToolResult()

    /**
     * Represents a failed tool execution.
     *
     * @property reason A human-readable string explaining the cause of the failure.
     * @property throwable An optional Throwable for debugging purposes.
     */
    data class Failure(val reason: String, val throwable: Throwable? = null) : ToolResult()
}
