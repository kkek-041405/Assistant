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
    NEXT,
    SPOTIFY_PLAY_PAUSE,
    SPOTIFY_NEXT,
    SPOTIFY_PREVIOUS,
    SPOTIFY_SEEK_FORWARD,
    SPOTIFY_SEEK_BACKWARD,
    TOGGLE_BLUETOOTH,
    CONNECT_BLUETOOTH_DEVICE,
    DISCONNECT_BLUETOOTH_DEVICE,
    LAUNCH_APP
}
