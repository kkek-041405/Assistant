package com.kkek.assistant.System.notification

import java.util.regex.Pattern

object OtpParser {

    // This regex looks for 4-8 consecutive digits. The surrounding patterns account for
    // common OTP phrases and structures, making it highly effective.
    // (?i) makes it case-insensitive.
    // Using a raw string (""") avoids the need to escape backslashes, improving readability.
    private val OTP_REGEX = Pattern.compile("""(?i)\b(?:otp|code|verification|password|pin|код|пароль|código|verificación) is:?\s*(\d{4,8})\b|\b(\d{4,8})\b(?:\s*is your verification code)""")

    /**
     * Parses the notification content to find an OTP code.
     *
     * @param title The title of the notification.
     * @param text The body text of the notification.
     * @return The extracted OTP code as a String, or null if no OTP is found.
     */
    fun findOtpCode(title: String, text: String): String? {
        val content = "$title $text"
        val matcher = OTP_REGEX.matcher(content)

        if (matcher.find()) {
            // The OTP will be in one of the capturing groups.
            // Group 1 corresponds to the first part of the regex: (\d{4,8})
            // Group 2 corresponds to the second part: \b(\d{4,8})\b
            // We iterate through groups to find the one that captured the digits.
            for (i in 1..matcher.groupCount()) {
                matcher.group(i)?.let {
                    return it
                }
            }
        }
        return null
    }
}
