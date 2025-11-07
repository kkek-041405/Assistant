package com.kkek.assistant.spotify

import android.content.Context
import android.util.Log
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.PlayerState
import java.util.concurrent.atomic.AtomicReference
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import android.app.Activity

class SpotifyHelper {
    @Suppress("unused", "RedundantQualifierName")
    companion object {
        // Request code will be used to verify if result comes from the login activity. Can be set to any integer.
        const val REQUEST_CODE: Int = 1337
        // Updated to use the required redirect URI
        const val REDIRECT_URI: String = "assistant://callback"
        const val CLIENT_ID: String = "a73b2c8f6ae64bbeb12c53d2d995974d"

        /**
         * Start the Spotify authorization flow.
         * Use the system browser to avoid platform-specific WebView hangs (some OEM ROMs/WebView
         * implementations cause the SDK's internal WebView to hang). Opening the external browser
         * should reliably redirect back to the app via assistant://callback.
         */
        fun startLogin(context: Context) {
            try {
                // Prefer the Spotify Auth SDK token flow when an Activity is available so the SDK
                // can return an access token directly via onActivityResult (implicit grant).
                val activityToUse: Activity? = if (context is Activity) context else currentActivityRef.get()
                if (activityToUse != null) {
                    val builder = AuthorizationRequest.Builder(
                        CLIENT_ID,
                        AuthorizationResponse.Type.TOKEN,
                        REDIRECT_URI
                    )
                    // Include streaming and app-remote-control scope for App Remote and playback control
                    builder.setScopes(arrayOf("streaming", "app-remote-control"))
                    val request = builder.build()
                    Log.d("SpotifyHelper", "startLogin: opening SDK login activity for auth (via Activity)")
                    AuthorizationClient.openLoginActivity(activityToUse, REQUEST_CODE, request)
                    return
                }

                // No Activity available now — queue a pending SDK login. When an Activity is
                // registered via setActivity(...) the SDK login will be started automatically.
                pendingLogin.set(true)
                Log.d("SpotifyHelper", "No Activity available; queued SDK login request (will start when Activity registers)")
                return
            } catch (e: Exception) {
                // Fallback: try the SDK's built-in login activity (may use WebView internally)
                Log.w("SpotifyHelper", "Failed to open browser for Spotify auth; falling back to SDK login activity", e)
                try {
                    // Try opening SDK login as a last resort (use token flow)
                    val builder = AuthorizationRequest.Builder(
                        CLIENT_ID,
                        AuthorizationResponse.Type.TOKEN,
                        REDIRECT_URI
                    )
                    builder.setScopes(arrayOf("streaming", "app-remote-control"))
                    val request = builder.build()
                    val act: Activity? = if (context is Activity) context else currentActivityRef.get()
                    if (act != null) {
                        AuthorizationClient.openLoginActivity(act, REQUEST_CODE, request)
                    } else {
                        pendingLogin.set(true)
                        Log.w("SpotifyHelper", "No Activity available for fallback; queued SDK login")
                    }
                } catch (inner: Exception) {
                    Log.e("SpotifyHelper", "Fallback to SDK login activity failed", inner)
                }
            }
        }

        // Note: this helper now uses only the Spotify Auth SDK (implicit/token flow) and
        // App Remote for playback. Token exchange and refresh via direct web API calls
        // have been removed to avoid embedding CLIENT_SECRET and in-app token exchange.

        // Simple playback control helpers that call Spotify App Remote. Web API auth/exchange removed.
        fun playPause(context: Context) {
            // Require an Activity (either provided or registered) for App Remote playback control
            val activity = if (context is Activity) context else currentActivityRef.get()
            if (activity == null) {
                Log.w("SpotifyHelper", "playPause: no Activity available for App Remote; playback unavailable")
                return
            }

            connectAppRemote(activity) { remote ->
                try {
                    // Toggle play/pause by checking player state
                    remote.playerApi.playerState.setResultCallback { state: PlayerState ->
                        val isPaused = state.isPaused
                        if (isPaused) {
                            remote.playerApi.resume()
                            Log.d("SpotifyHelper", "Resumed via AppRemote")
                        } else {
                            remote.playerApi.pause()
                            Log.d("SpotifyHelper", "Paused via AppRemote")
                        }
                    }
                } catch (e: Exception) {
                    Log.w("SpotifyHelper", "AppRemote playPause failed", e)
                }
            }
        }

        fun next(context: Context) {
            val activity = if (context is Activity) context else currentActivityRef.get()
            if (activity == null) {
                Log.w("SpotifyHelper", "next: no Activity available for App Remote; playback unavailable")
                return
            }

            connectAppRemote(activity) { remote ->
                try {
                    remote.playerApi.skipNext()
                    Log.d("SpotifyHelper", "Skipped to next via AppRemote")
                } catch (e: Exception) {
                    Log.w("SpotifyHelper", "AppRemote skipNext failed", e)
                }
            }
        }

        fun previous(context: Context) {
            val activity = if (context is Activity) context else currentActivityRef.get()
            if (activity == null) {
                Log.w("SpotifyHelper", "previous: no Activity available for App Remote; playback unavailable")
                return
            }

            connectAppRemote(activity) { remote ->
                try {
                    remote.playerApi.skipPrevious()
                    Log.d("SpotifyHelper", "Skipped to previous via AppRemote")
                } catch (e: Exception) {
                    Log.w("SpotifyHelper", "AppRemote skipPrevious failed", e)
                }
            }
        }

        // App Remote connection reference
        private val appRemoteRef = AtomicReference<SpotifyAppRemote?>(null)
        // Hold a reference to the current Activity (set from MainActivity lifecycle)
        private val currentActivityRef = AtomicReference<Activity?>(null)

        // Pending login flag — if true, setActivity will kick off the SDK login flow.
        private val pendingLogin = java.util.concurrent.atomic.AtomicBoolean(false)

        // Called by an Activity to register itself as the current foreground activity used for App Remote
        fun setActivity(activity: Activity?) {
            currentActivityRef.set(activity)

            // If there was a pending login request and an Activity is now available, start the SDK login
            if (activity != null && pendingLogin.get()) {
                pendingLogin.set(false) // Clear the flag
                try {
                    startLogin(activity)
                } catch (e: Exception) {
                    Log.w("SpotifyHelper", "Failed to start pending SDK login", e)
                }
            }
        }

        // Public wrapper to allow Activities to request an App Remote connection.
        fun connect(activity: Activity) {
            connectAppRemote(activity, null)
        }

        private fun connectAppRemote(context: Context, onConnected: ((SpotifyAppRemote) -> Unit)? = null) {
            // Prefer an Activity context. If the provided context is not an Activity, try the registered Activity reference.
            val activityToUse: Activity? = if (context is Activity) context else currentActivityRef.get()
            if (activityToUse == null) {
                Log.w("SpotifyHelper", "connectAppRemote requires an Activity context (none available); falling back to Web API")
                return
            }

            Log.d("SpotifyHelper", "Connecting to Spotify AppRemote...")

            val existing = appRemoteRef.get()
            if (existing != null && existing.isConnected) {
                Log.d("SpotifyHelper", "AppRemote already connected")
                onConnected?.invoke(existing)
                return
            }

            // Do not request the App Remote auth view here — we handle auth via the system browser
            // to ensure redirects go through our assistant://callback handler. Requesting the SDK's
            // auth view can cause redirect handling issues on some devices/ROMs.
            val connectionParams = ConnectionParams.Builder(CLIENT_ID)
                .setRedirectUri(REDIRECT_URI)
                .showAuthView(false)
                .build()

            Log.d("SpotifyHelper", "Connecting with params: $connectionParams")
            SpotifyAppRemote.connect(activityToUse, connectionParams, object : Connector.ConnectionListener {
                override fun onConnected(spotifyAppRemote: SpotifyAppRemote) {
                    Log.d("SpotifyHelper", "AppRemote connected")
                    appRemoteRef.set(spotifyAppRemote)
                    onConnected?.invoke(spotifyAppRemote)
                }

                override fun onFailure(throwable: Throwable) {
                    Log.w("SpotifyHelper", "Failed to connect AppRemote", throwable)
                }
            })
        }

        // Connect but allow the Spotify app to show its auth UI if necessary. Use this when you
        // just completed an implicit token flow and want the App Remote to finish authorization.
        fun connectWithAuth(activity: Activity) {
            val existing = appRemoteRef.get()
            if (existing != null && existing.isConnected) return

            val connectionParams = ConnectionParams.Builder(CLIENT_ID)
                .setRedirectUri(REDIRECT_URI)
                .showAuthView(true)
                .build()

            SpotifyAppRemote.connect(activity, connectionParams, object : Connector.ConnectionListener {
                override fun onConnected(spotifyAppRemote: SpotifyAppRemote) {
                    Log.d("SpotifyHelper", "AppRemote connected (with auth)")
                    appRemoteRef.set(spotifyAppRemote)
                }

                override fun onFailure(throwable: Throwable) {
                    Log.w("SpotifyHelper", "Failed to connect AppRemote with auth", throwable)
                }
            })
        }

        fun disconnectAppRemote() {
            try {
                appRemoteRef.getAndSet(null)?.let { remote ->
                    try { SpotifyAppRemote.disconnect(remote) } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }
}
