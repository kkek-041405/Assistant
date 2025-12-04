package com.kkek.assistant.data.model

data class CallState(
    val state: State,
    val phoneNumber: String? = null
) {
    enum class State {
        IDLE,
        RINGING,
        OFFHOOK
    }
}
