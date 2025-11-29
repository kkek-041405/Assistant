package com.kkek.assistant.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class Command {
    object ExecutePrimaryAction : Command()
}

object CommandQueue {
    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 1)
    val commands = _commands.asSharedFlow()

    fun sendCommand(command: Command) {
        _commands.tryEmit(command)
    }
}
