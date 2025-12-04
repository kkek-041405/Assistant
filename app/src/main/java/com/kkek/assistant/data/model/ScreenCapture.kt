package com.kkek.assistant.data.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Represents a single screen content capture.
 *
 * @property timestamp The server-side timestamp of when the capture was recorded. Null until the server sets it.
 * @property sourceApp The package name of the foreground application (e.g., "com.android.chrome").
 * @property content The aggregated text content extracted from the screen.
 */
data class ScreenCapture(
    @ServerTimestamp val timestamp: Date? = null,
    val sourceApp: String = "",
    val content: String = ""
)
