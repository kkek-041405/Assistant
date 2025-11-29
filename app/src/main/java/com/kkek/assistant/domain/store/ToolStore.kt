package com.kkek.assistant.domain.store

import com.kkek.assistant.domain.model.AiTool
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A Hilt-powered store for managing all available AiTools.
 * It receives a set of all tool implementations provided via Hilt multibindings.
 */
@Singleton
class ToolStore @Inject constructor(
    // Hilt will inject the set of all AiTool bindings provided in ToolModule
    private val tools: Set<@JvmSuppressWildcards AiTool>
) {

    private val toolMap: Map<String, AiTool> by lazy {
        tools.associateBy { it.name }
    }

    /**
     * Retrieves a specific tool by its unique name.
     *
     * @param name The name of the tool to find.
     * @return The [AiTool] if found, otherwise null.
     */
    fun getTool(name: String): AiTool? {
        return toolMap[name]
    }

    /**
     * Retrieves a list of all available tools.
     *
     * @return A list of all registered [AiTool] implementations.
     */
    fun getAllTools(): List<AiTool> {
        return tools.toList()
    }
}
