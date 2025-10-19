package com.kkek.assistant.model

/**
 * Central registry of available actions
 * ListItems reference actions by ID so actions are defined in one place.
 */
enum class Actions() {
    TELL_TIME,
    SUMMARIZE_NOTIFICATIONS,
    ANSWER_CALL,
    REJECT_CALL,
    HANGUP,
    TOGGLE_SPEAKER,
    TOGGLE_MUTE,
    CALL_CONTACT,
    READ_SELECTION,
    OPEN_SUBLIST,
    CLOSE_SUBLIST,
    PREVIOUS,
    NEXT
}
