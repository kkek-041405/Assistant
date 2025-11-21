package com.kkek.assistant

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.kkek.assistant.helpers.AppLauncher
import com.kkek.assistant.music.SpotifyHelper
import com.kkek.assistant.System.BluetoothHelper
import com.kkek.assistant.System.TTSHelper
import com.kkek.assistant.model.Actions
import com.kkek.assistant.model.Kind
import com.kkek.assistant.model.ListItem
import com.kkek.assistant.modules.Phone
import kotlinx.coroutines.launch

class ActionExecutor(private val viewModel: MainViewModel, private val application: Application) {
    internal val ttsHelper = TTSHelper(application)
    internal val bluetoothHelper = BluetoothHelper(application)
    private val appLauncher = AppLauncher(application)

    fun execute(selectedItem: ListItem, actionIds: List<Actions>) {
        if (actionIds.isEmpty()) {
            viewModel.setUserMessage("No action assigned")
            return
        }

        actionIds.forEach { actionId ->
            when (actionId) {
                Actions.TELL_TIME -> viewModel.speakStatus()
                Actions.SUMMARIZE_NOTIFICATIONS -> {
                    ttsHelper.speak("Notification summary not available")
                    viewModel.setUserMessage("Summarize notifications (not implemented)")
                }
                Actions.ANSWER_CALL -> {
                    Phone.answerCall()
                    viewModel.currentList = viewModel.callItems
                    viewModel.selectedIndex = 0
                    viewModel.inSublist = true
                    viewModel.setUserMessage("Call answered")
                }
                Actions.REJECT_CALL -> {
                    Phone.rejectCall()
                    viewModel.currentList = viewModel.defaultItems
                    viewModel.selectedIndex = 0
                    viewModel.inSublist = false
                    viewModel.setUserMessage("Call rejected")
                }
                Actions.HANGUP -> {
                    Phone.endCall()
                    viewModel.currentList = viewModel.defaultItems
                    viewModel.selectedIndex = 0
                    viewModel.inSublist = false
                    viewModel.setUserMessage("Call ended")
                }
                Actions.TOGGLE_SPEAKER -> {
                    Phone.toggleSpeaker()
                    viewModel.setUserMessage("Toggled speaker")
                }
                Actions.TOGGLE_MUTE -> {
                    Phone.toggleMute()
                    viewModel.setUserMessage("Toggled mute")
                }
                Actions.CALL_CONTACT -> {
                    val phoneNumber = selectedItem.phoneNumber
                    if (phoneNumber != null) {
                        Phone.placeCall(application, phoneNumber)
                        viewModel.setUserMessage("Calling ${selectedItem.name ?: phoneNumber}")
                        viewModel.currentList = viewModel.callItems
                        viewModel.selectedIndex = 0
                        viewModel.inSublist = true
                    } else {
                        viewModel.setUserMessage("No phone number for contact")
                    }
                }
                Actions.OPEN_SUBLIST -> {
                    when (selectedItem.text) {
                        "Spotify" -> {
                            viewModel.currentList = viewModel.getSpotifySublist()
                            viewModel.selectedIndex = 0
                            viewModel.inSublist = true
                            viewModel.setUserMessage("Opened Spotify controls")
                        }
                        "Make call" -> {
                            viewModel.viewModelScope.launch {
                                viewModel.setUserMessage("Fetching contacts...")
                                viewModel.currentList = viewModel.getContacts()
                                viewModel.selectedIndex = 0
                                viewModel.inSublist = true
                                viewModel.setUserMessage("Opened contacts")
                            }
                        }
                        "Bluetooth" -> {
                            viewModel.currentList = viewModel.getBluetoothSublist()
                            viewModel.selectedIndex = 0
                            viewModel.inSublist = true
                            viewModel.setUserMessage("Opened Bluetooth menu")
                        }
                        else -> {
                            selectedItem.sublist?.let {
                                viewModel.currentList = it
                                viewModel.selectedIndex = 0
                                viewModel.inSublist = true
                                viewModel.setUserMessage("Opened sublist: ${selectedItem.text ?: "Unknown"}")
                            } ?: viewModel.setUserMessage("No sublist available for this item")
                        }
                    }
                }
                Actions.CLOSE_SUBLIST -> {
                    if (viewModel.currentList != viewModel.defaultItems) {
                        viewModel.currentList = viewModel.defaultItems
                        viewModel.selectedIndex = 0
                        viewModel.inSublist = false
                        viewModel.setUserMessage("")
                    }
                }
                Actions.READ_SELECTION -> {
                    selectedItem.let {
                        val textToSpeak = when (it.kind) {
                            Kind.SIMPLE -> it.text
                            Kind.TOGGLE -> it.text
                            Kind.CONTACT -> it.name
                            Kind.SUBLIST -> it.text
                        }
                        ttsHelper.speak(textToSpeak ?: "Unknown item")
                        viewModel.setUserMessage("Spoken: ${textToSpeak ?: "Unknown item"}")
                    }
                }
                Actions.PREVIOUS -> {
                    if (viewModel.currentList.isNotEmpty()) {
                        viewModel.selectedIndex = if (viewModel.selectedIndex > 0) viewModel.selectedIndex - 1 else viewModel.selectedIndex
                    }
                }
                Actions.NEXT -> {
                    if (viewModel.currentList.isNotEmpty()) {
                        viewModel.selectedIndex = if (viewModel.selectedIndex < viewModel.currentList.size - 1) viewModel.selectedIndex + 1 else viewModel.selectedIndex
                    }
                }
                Actions.SPOTIFY_PLAY_PAUSE -> {
                    try {
                        SpotifyHelper.playPause(application)
                        viewModel.setUserMessage("Toggled Play/Pause")
                    } catch (e: Exception) {
                        Log.w(viewModel.TAG, "Failed to toggle play/pause", e)
                        viewModel.setUserMessage("Play/Pause failed")
                    }
                }
                Actions.SPOTIFY_NEXT -> {
                    try {
                        SpotifyHelper.next(application)
                        viewModel.setUserMessage("Skipping to next track")
                    } catch (e: Exception) {
                        Log.w(viewModel.TAG, "Failed to skip to next", e)
                        viewModel.setUserMessage("Skip next failed")
                    }
                }
                Actions.SPOTIFY_PREVIOUS -> {
                    try {
                        SpotifyHelper.previous(application)
                        viewModel.setUserMessage("Skipping to previous track")
                    } catch (e: Exception) {
                        Log.w(viewModel.TAG, "Failed to skip to previous", e)
                        viewModel.setUserMessage("Skip previous failed")
                    }
                }
                Actions.SPOTIFY_SEEK_FORWARD -> {
                    try {
                        SpotifyHelper.seekForward(application)
                        viewModel.setUserMessage("Seeked forward")
                    } catch (e: Exception) {
                        Log.w(viewModel.TAG, "Failed to seek forward", e)
                        viewModel.setUserMessage("Seek forward failed")
                    }
                }
                Actions.SPOTIFY_SEEK_BACKWARD -> {
                    try {
                        SpotifyHelper.seekBackward(application)
                        viewModel.setUserMessage("Seeked backward")
                    } catch (e: Exception) {
                        Log.w(viewModel.TAG, "Failed to seek backward", e)
                        viewModel.setUserMessage("Seek backward failed")
                    }
                }
                Actions.TOGGLE_BLUETOOTH -> {
                    if (bluetoothHelper.isBluetoothEnabled()) {
                        bluetoothHelper.disableBluetooth()
                        viewModel.setUserMessage("Bluetooth disabled")
                    } else {
                        bluetoothHelper.enableBluetooth()
                        viewModel.setUserMessage("Bluetooth enabled")
                    }
                    viewModel.currentList = viewModel.getBluetoothSublist()
                }
                Actions.CONNECT_BLUETOOTH_DEVICE -> {
                    val deviceName = selectedItem.text
                    val device = bluetoothHelper.getPairedDevices().find { it.name == deviceName || it.address == deviceName }
                    if (device != null) {
                        if (bluetoothHelper.connectToDevice(device)) {
                            viewModel.setUserMessage("Connecting to ${device.name}")
                        } else {
                            viewModel.setUserMessage("Failed to connect to ${device.name}")
                        }
                    }
                }
                Actions.DISCONNECT_BLUETOOTH_DEVICE -> {
                    viewModel.viewModelScope.launch {
                        try {
                            SpotifyHelper.playPause(application) // Stop the audio first
                            viewModel.setUserMessage("Paused music.")
                        } catch (e: Exception) {
                            Log.w(viewModel.TAG, "Failed to pause Spotify", e)
                        }
                        bluetoothHelper.disconnect() // Wait for disconnection to complete
                        viewModel.setUserMessage("Disconnected from Bluetooth device")
                        viewModel.currentList = viewModel.getBluetoothSublist()
                    }
                }
                Actions.LAUNCH_APP -> {
                    selectedItem.packageName?.let { appLauncher.launchApp(it) }
                }
            }
        }
    }
}
