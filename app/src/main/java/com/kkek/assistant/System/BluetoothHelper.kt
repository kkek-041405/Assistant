package com.kkek.assistant.System

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object BluetoothHelper {

    private var context: Context? = null
    private val bluetoothManager: BluetoothManager? by lazy { context?.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager }
    private val bluetoothAdapter: BluetoothAdapter? by lazy { bluetoothManager?.adapter }
    private var bluetoothSocket: BluetoothSocket? = null

    // Standard SerialPortService ID
    private val myUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun init(context: Context) {
        if (this.context == null) {
            this.context = context.applicationContext
        }
    }

    private fun hasPermission(permission: String): Boolean {
        val currentContext = context ?: return false
        return ContextCompat.checkSelfPermission(currentContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun enableBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            return
        }
        if (bluetoothAdapter?.isEnabled == false) {
            bluetoothAdapter?.enable()
        }
    }

    fun disableBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            return
        }
        if (bluetoothAdapter?.isEnabled == true) {
            bluetoothAdapter?.disable()
        }
    }

    fun isBluetoothEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            return false
        }
        return bluetoothAdapter?.isEnabled == true
    }

    @Suppress("MissingPermission")
    fun isConnected(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            return false
        }

        val a2dpConnected = bluetoothAdapter?.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
        val headsetConnected = bluetoothAdapter?.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED

        return a2dpConnected || headsetConnected
    }

    fun getPairedDevices(): List<BluetoothDeviceData> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            return emptyList()
        }
        @Suppress("MissingPermission") // Already checked above
        return bluetoothAdapter?.bondedDevices?.map { device ->
            BluetoothDeviceData(device.name, device.address)
        } ?: emptyList()
    }

    fun connectToDevice(deviceData: BluetoothDeviceData): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT) || !hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                return false
            }
        }
        if (bluetoothAdapter == null) return false

        try {
            @Suppress("MissingPermission") // Already checked above
            val device: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(deviceData.address)
            if (device != null) {
                @Suppress("MissingPermission") // Already checked above
                bluetoothSocket = device.createRfcommSocketToServiceRecord(myUUID)
                @Suppress("MissingPermission") // Already checked above
                bluetoothAdapter?.cancelDiscovery()
                Log.d("BluetoothHelper", "Connecting to device...")
                @Suppress("MissingPermission") // Already checked above
                bluetoothSocket?.connect()
                Log.d("BluetoothHelper", "Device connected.")
                return true
            }
        } catch (e: IOException) {
            Log.e("BluetoothHelper", "Could not connect to device", e)
            try {
                bluetoothSocket?.close()
            } catch (e2: IOException) {
                Log.e("BluetoothHelper", "Could not close socket after connection error", e2)
            }
            return false
        }
        return false
    }

    suspend fun disconnect() {
        val currentContext = context ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            return
        }

        suspendCoroutine<Unit> { continuation ->
            val profilesToClose = listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)
            val profileCounter = java.util.concurrent.atomic.AtomicInteger(profilesToClose.size)

            val profileListener = object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    @Suppress("MissingPermission")
                    val connectedDevices = proxy.connectedDevices
                    if (connectedDevices.isNotEmpty()) {
                        for (device in connectedDevices) {
                            try {
                                Log.d("BluetoothHelper", "Forcing system disconnect from ${device.name} via profile $profile")
                                val disconnectMethod = proxy.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
                                disconnectMethod.invoke(proxy, device)
                            } catch (e: Exception) {
                                Log.e("BluetoothHelper", "Failed to reflectively disconnect from ${device.name}", e)
                            }
                        }
                    } else {
                        Log.d("BluetoothHelper", "No devices connected to profile $profile")
                    }
                    bluetoothAdapter?.closeProfileProxy(profile, proxy)

                    if (profileCounter.decrementAndGet() == 0) {
                        continuation.resume(Unit)
                    }
                }

                override fun onServiceDisconnected(profile: Int) {
                    if (profileCounter.decrementAndGet() == 0) {
                        continuation.resume(Unit)
                    }
                }
            }

            Log.d("BluetoothHelper", "Attempting to close profile proxies for A2DP and HEADSET")
            profilesToClose.forEach { profileId ->
                if (bluetoothAdapter?.getProfileProxy(currentContext, profileListener, profileId) == false) {
                    if (profileCounter.decrementAndGet() == 0) {
                        continuation.resume(Unit)
                    }
                }
            }
        }

        // Finally, always close our own socket to clean up app resources.
        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            // This is expected if the socket is already closed.
        }
        bluetoothSocket = null
        Log.d("BluetoothHelper", "Suspend disconnect has finished.")
    }
}

data class BluetoothDeviceData(val name: String?, val address: String)
