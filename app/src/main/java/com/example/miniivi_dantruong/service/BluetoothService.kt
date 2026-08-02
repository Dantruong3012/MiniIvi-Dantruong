package com.example.miniivi_dantruong.service

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothService : Service() {
    private var bluetoothSocket: BluetoothSocket? = null
    private var connectionJob: Job? = null
    private var readerJob: Job? = null
    private val TAG = "BluetoothService"
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    companion object {
        const val ACTION_CONNECT = "ACTION_CONNECT"
        const val ACTION_DISCONNECT = "ACTION_DISCONNECT"
        const val ACTION_SEND = "ACTION_SEND"

        const val EXTRA_MAC_ADDRESS = "MAC_ADDRESS"
        const val EXTRA_MESSAGE_DATA = "MESSAGE_DATA"

        const val BROADCAST_CONNECTED = "BLUETOOTH_CONNECTED"
        const val BROADCAST_CONNECTION_ERROR = "BLUETOOTH_CONNECTION_ERROR"
        const val BROADCAST_DISCONNECTED = "BLUETOOTH_DISCONNECTED"
        const val BROADCAST_MESSAGE_RECEIVED = "BLUETOOTH_MESSAGE_RECEIVED"
    }

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BluetoothService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand bluetooth service action: ${intent?.action}")

        when (intent?.action) {
            ACTION_CONNECT -> {
                val macAddress = intent.getStringExtra(EXTRA_MAC_ADDRESS)
                if (macAddress == null) {
                    Log.e(TAG, "Cannot retrieve mac address from intent")
                    sendBroadcast(Intent(BROADCAST_CONNECTION_ERROR))
                } else {
                    connectToDevice(macAddress)
                }
            }
            ACTION_DISCONNECT -> {
                disconnect()
            }
            ACTION_SEND -> {
                val message = intent.getStringExtra(EXTRA_MESSAGE_DATA)
                if (message != null) {
                    sendMessage(message)
                }
            }
            else -> {
                // Fallback for legacy behavior where action is null but MAC_ADDRESS is passed directly
                val macAddress = intent?.getStringExtra("MAC_ADDRESS")
                if (macAddress != null) {
                    connectToDevice(macAddress)
                } else {
                    Log.d(TAG, "No action or MAC address provided; keeping service alive in idle state")
                }
            }
        }

        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(macAddress: String) {
        // Cancel any active connections first
        disconnect()

        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled")
            sendBroadcast(Intent(BROADCAST_CONNECTION_ERROR))
            return
        }

        val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(macAddress)
        Log.d(TAG, "attempting to connect to device: ${device.name} (${device.address})")

        connectionJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Connecting to device: ${device.name} (${device.address})")
                bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
                Log.d(TAG, "cancel scanning")
                bluetoothAdapter.cancelDiscovery()
                bluetoothSocket?.connect()
                Log.d(TAG, "Connected to device: ${device.name} (${device.address})")
                sendBroadcast(Intent(BROADCAST_CONNECTED))

                // Start listening for incoming data
                startReadingData()
            } catch (e: Exception) {
                Log.e(TAG, "Standard connection failed: ${e.message}. Trying fallback...")
                try {
                    // Fallback using reflection to create RFCOMM socket on port 1
                    val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    bluetoothSocket = method.invoke(device, 1) as BluetoothSocket
                    bluetoothAdapter.cancelDiscovery()
                    bluetoothSocket?.connect()
                    Log.d(TAG, "Connected via fallback to device: ${device.name}")
                    sendBroadcast(Intent(BROADCAST_CONNECTED))

                    // Start listening for incoming data
                    startReadingData()
                } catch (fallbackException: Exception) {
                    Log.e(TAG, "Fallback connection also failed: ${fallbackException.message}")
                    sendBroadcast(Intent(BROADCAST_CONNECTION_ERROR))
                    cleanConnectionResources()
                }
            }
        }
    }

    private fun startReadingData() {
        readerJob = CoroutineScope(Dispatchers.IO).launch {
            val inputStream: InputStream? = bluetoothSocket?.inputStream
            val buffer = ByteArray(1024)
            var bytes: Int

            while (true) {
                try {
                    bytes = inputStream?.read(buffer) ?: -1
                    if (bytes == -1) {
                        Log.w(TAG, "Connection lost: Input stream reached end of file")
                        sendBroadcast(Intent(BROADCAST_DISCONNECTED))
                        cleanConnectionResources()
                        break
                    }
                    val message = String(buffer, 0, bytes)
                    Log.d(TAG, "Received message: $message")
                    val intent = Intent(BROADCAST_MESSAGE_RECEIVED).apply {
                        putExtra(EXTRA_MESSAGE_DATA, message)
                    }
                    sendBroadcast(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading from input stream: ${e.message}")
                    sendBroadcast(Intent(BROADCAST_DISCONNECTED))
                    cleanConnectionResources()
                    break
                }
            }
        }
    }

    private fun sendMessage(message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val outputStream: OutputStream? = bluetoothSocket?.outputStream
            if (outputStream == null) {
                Log.e(TAG, "Cannot send message: socket is not connected or outputStream is null")
                return@launch
            }
            try {
                outputStream.write(message.toByteArray())
                Log.d(TAG, "Sent message: $message")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message: ${e.message}")
            }
        }
    }

    private fun disconnect() {
        Log.d(TAG, "Disconnecting from device...")
        connectionJob?.cancel()
        readerJob?.cancel()
        cleanConnectionResources()
    }

    private fun cleanConnectionResources() {
        try {
            bluetoothSocket?.close()
        } catch (closeException: Exception) {
            Log.e(TAG, "Error closing socket: ${closeException.message}")
        } finally {
            bluetoothSocket = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
        Log.d(TAG, "BluetoothService destroyed")
    }
}