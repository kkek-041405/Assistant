package com.kkek.assistant.spotify

import android.content.Context

class TokenManager(private val context: Context) {
    companion object {
        private const val PREFS_FILE = "spotify_tokens"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
    }

    private val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun saveTokens(accessToken: String?, refreshToken: String?, expiresInSeconds: Int?) {
        val editor = prefs.edit()
        if (accessToken != null) editor.putString(KEY_ACCESS, accessToken) else editor.remove(KEY_ACCESS)
        if (refreshToken != null) editor.putString(KEY_REFRESH, refreshToken) else editor.remove(KEY_REFRESH)
        if (expiresInSeconds != null) {
            val expiresAt = System.currentTimeMillis() / 1000 + expiresInSeconds
            editor.putLong(KEY_EXPIRES_AT, expiresAt)
        } else {
            editor.remove(KEY_EXPIRES_AT)
        }
        editor.apply()
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS, null)
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH, null)
    fun getExpiresAt(): Long = prefs.getLong(KEY_EXPIRES_AT, 0L)
    fun clear() {
        prefs.edit().clear().apply()
    }

    /**
     * Returns true if the access token is missing or will expire within the next `leewaySec` seconds.
     */
    fun needsRefresh(leewaySec: Long = 60): Boolean {
        val access = getAccessToken()
        if (access.isNullOrEmpty()) return true
        val expiresAt = getExpiresAt()
        if (expiresAt <= 0L) return true
        val now = System.currentTimeMillis() / 1000
        return expiresAt - now <= leewaySec
    }
}
