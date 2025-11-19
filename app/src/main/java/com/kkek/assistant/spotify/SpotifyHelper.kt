package com.kkek.assistant.spotify

import android.content.Context
import android.util.Log
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

class SpotifyHelper {
    @Suppress("unused", "RedundantQualifierName")
    companion object {
        const val REDIRECT_URI: String = "assistant://callback"
        const val CLIENT_ID: String = "a73b2c8f6ae64bbeb12c53d2d995974d"
        private const val TAG = "SpotifyHelper"

        private val appRemoteRef = AtomicReference<SpotifyAppRemote?>(null)

        fun playPause(context: Context) {
            connectAppRemote(context) { appRemote ->
                appRemote.playerApi.playerState.setResultCallback { playerState ->
                    if (playerState.isPaused) {
                        appRemote.playerApi.resume()
                    } else {
                        appRemote.playerApi.pause()
                    }
                }
            }
        }

        fun next(context: Context) {
            connectAppRemote(context) { it.playerApi.skipNext() }
        }

        fun previous(context: Context) {
            connectAppRemote(context) { it.playerApi.skipPrevious() }
        }

        fun seekForward(context: Context) {
            connectAppRemote(context) { appRemote ->
                appRemote.playerApi.playerState.setResultCallback { playerState ->
                    appRemote.playerApi.seekTo(playerState.playbackPosition + 15000)
                }
            }
        }

        fun seekBackward(context: Context) {
            connectAppRemote(context) { appRemote ->
                appRemote.playerApi.playerState.setResultCallback { playerState ->
                    appRemote.playerApi.seekTo(playerState.playbackPosition - 15000)
                }
            }
        }

        fun disconnect() {
            appRemoteRef.get()?.let {
                SpotifyAppRemote.disconnect(it)
                appRemoteRef.set(null)
            }
        }

        private fun connectAppRemote(context: Context, onConnected: ((SpotifyAppRemote) -> Unit)? = null) {
            if (appRemoteRef.get()?.isConnected == true) {
                onConnected?.invoke(appRemoteRef.get()!!)
                return
            }

            GlobalScope.launch {
//                val credentials = authManager.getCredentials()
//                val accessToken = credentials?.accessToken

//                if (accessToken == null) {
//                    Log.e(TAG, "Access token not found in Firestore.")
//                    return@launch
//                }

                val connectionParams = ConnectionParams.Builder(CLIENT_ID)
                    .setRedirectUri(REDIRECT_URI)
                    .showAuthView(true)
                    .build()


                withContext(Dispatchers.Main) {
                    SpotifyAppRemote.connect(context, connectionParams, object : Connector.ConnectionListener {
                        override fun onConnected(spotifyAppRemote: SpotifyAppRemote) {
                            appRemoteRef.set(spotifyAppRemote)
                            Log.d(TAG, "Spotify App Remote connected")
                            onConnected?.invoke(spotifyAppRemote)
                        }

                        override fun onFailure(throwable: Throwable) {
                            Log.e(TAG, "Spotify App Remote connection failed", throwable)
                        }
                    })
                }
            }
        }
    }
}
