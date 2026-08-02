package com.example.miniivi_dantruong.service

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
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
    private var serverSocket: BluetoothServerSocket? = null
    private var connectionJob: Job? = null
    private var serverJob: Job? = null
    private var readerJob: Job? = null
    private val TAG = "BT_SERVICE"
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    private val SERVICE_NAME = "MiniIviBluetoothService"

    companion object {
        const val ACTION_CONNECT = "ACTION_CONNECT"
        const val ACTION_DISCONNECT = "ACTION_DISCONNECT"
        const val ACTION_SEND = "ACTION_SEND"
        const val ACTION_START_SERVER = "ACTION_START_SERVER"
        const val ACTION_STOP_SERVER = "ACTION_STOP_SERVER"

        const val EXTRA_MAC_ADDRESS = "MAC_ADDRESS"
        const val EXTRA_MESSAGE_DATA = "MESSAGE_DATA"

        const val BROADCAST_CONNECTED = "BLUETOOTH_CONNECTED"
        const val BROADCAST_CONNECTION_ERROR = "BLUETOOTH_CONNECTION_ERROR"
        const val BROADCAST_DISCONNECTED = "BLUETOOTH_DISCONNECTED"
        const val BROADCAST_MESSAGE_RECEIVED = "BLUETOOTH_MESSAGE_RECEIVED"
        const val BROADCAST_SERVER_LISTENING = "BLUETOOTH_SERVER_LISTENING"
    }

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "=== BluetoothService CREATED ===")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: "(null)"
        Log.d(TAG, ">>> onStartCommand | action=$action")

        when (intent?.action) {
            ACTION_CONNECT -> {
                val macAddress = intent.getStringExtra(EXTRA_MAC_ADDRESS)
                if (macAddress == null) {
                    Log.e(TAG, "[CLIENT] Cannot retrieve MAC address from intent!")
                    sendBroadcast(Intent(BROADCAST_CONNECTION_ERROR))
                } else {
                    connectToDevice(macAddress)
                }
            }
            ACTION_DISCONNECT -> disconnect()
            ACTION_SEND -> {
                val message = intent.getStringExtra(EXTRA_MESSAGE_DATA)
                if (message != null) sendMessage(message)
                else Log.w(TAG, "[SEND] Message data is null, nothing sent")
            }
            ACTION_START_SERVER -> startServer()
            ACTION_STOP_SERVER -> stopServer()
            else -> {
                val macAddress = intent?.getStringExtra("MAC_ADDRESS")
                if (macAddress != null) {
                    Log.w(TAG, "[COMPAT] No action set but MAC_ADDRESS found — falling back to connectToDevice()")
                    connectToDevice(macAddress)
                } else {
                    Log.d(TAG, "Service kept alive in idle state (no action)")
                }
            }
        }

        return START_STICKY
    }


    // CLIENT MODE

    @SuppressLint("MissingPermission")
    private fun connectToDevice(macAddress: String) {
        Log.i(TAG, "--- [CLIENT] Initiating connection to: $macAddress ---")
        stopServer()
        disconnect()

        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null) {
            Log.e(TAG, "[CLIENT] BluetoothAdapter is NULL — device may not support Bluetooth")
            sendBroadcast(Intent(BROADCAST_CONNECTION_ERROR))
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            Log.e(TAG, "[CLIENT] Bluetooth is DISABLED on this device")
            sendBroadcast(Intent(BROADCAST_CONNECTION_ERROR))
            return
        }

        val device = try {
            bluetoothAdapter.getRemoteDevice(macAddress)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "[CLIENT] Invalid MAC address format: $macAddress")
            sendBroadcast(Intent(BROADCAST_CONNECTION_ERROR))
            return
        }

        Log.d(TAG, "[CLIENT] Target device: name=${device.name}, address=${device.address}, bondState=${bondStateString(device.bondState)}")

        connectionJob = CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "[CLIENT] Starting connection coroutine on IO thread")
            try {
                Log.d(TAG, "[CLIENT] Creating RFCOMM socket with UUID: $MY_UUID")
                bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
                Log.d(TAG, "[CLIENT] Cancelling discovery before connect()")
                bluetoothAdapter.cancelDiscovery()
                Log.d(TAG, "[CLIENT] Calling socket.connect()... (blocking)")
                bluetoothSocket?.connect()
                Log.i(TAG, "[CLIENT] SUCCESS: CONNECTED (standard UUID) to: ${device.name} (${device.address})")
                sendBroadcast(Intent(BROADCAST_CONNECTED))
                startReadingData()
            } catch (e: Exception) {
                Log.e(TAG, "[CLIENT] FAILED: Standard connection FAILED: ${e.message}")
                Log.d(TAG, "[CLIENT] Trying Reflection fallback on channel 1...")
                try {
                    val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    bluetoothSocket = method.invoke(device, 1) as BluetoothSocket
                    bluetoothAdapter.cancelDiscovery()
                    Log.d(TAG, "[CLIENT] Calling socket.connect() via fallback... (blocking)")
                    bluetoothSocket?.connect()
                    Log.i(TAG, "[CLIENT] SUCCESS: CONNECTED (reflection fallback) to: ${device.name} (${device.address})")
                    sendBroadcast(Intent(BROADCAST_CONNECTED))
                    startReadingData()
                } catch (fallbackException: Exception) {
                    Log.e(TAG, "[CLIENT] FAILED: Fallback connection ALSO FAILED: ${fallbackException.message}")
                    Log.e(TAG, "[CLIENT] Root cause: Make sure the remote device is running in Server/Listen mode")
                    sendBroadcast(Intent(BROADCAST_CONNECTION_ERROR))
                    cleanConnectionResources()
                }
            }
        }
    }

    private fun bondStateString(bondState: Int) = when (bondState) {
        10 -> "BOND_NONE (not paired)"
        11 -> "BOND_BONDING (pairing in progress)"
        12 -> "BOND_BONDED (paired)"
        else -> "UNKNOWN ($bondState)"
    }


    // SERVER MODE

    @SuppressLint("MissingPermission")
    private fun startServer() {
        if (serverJob?.isActive == true) {
            Log.w(TAG, "[SERVER] Already listening, ignoring startServer() call")
            return
        }

        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "[SERVER] Bluetooth is not enabled — cannot start server")
            return
        }

        Log.i(TAG, "--- [SERVER] Starting server socket (UUID: $MY_UUID) ---")

        serverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, MY_UUID)
                Log.i(TAG, "[SERVER] LISTENING: Waiting for an incoming connection")
                sendBroadcast(Intent(BROADCAST_SERVER_LISTENING))

                // accept() blocks the thread until a client connects or exception is thrown
                val socket = serverSocket?.accept()

                if (socket != null) {
                    val remote = socket.remoteDevice
                    Log.i(TAG, "[SERVER] SUCCESS: Accepted connection from: name=${remote.name}, address=${remote.address}")
                    serverSocket?.close()
                    serverSocket = null
                    Log.d(TAG, "[SERVER] Server socket closed (only accepting 1 connection at a time)")
                    bluetoothSocket = socket
                    sendBroadcast(Intent(BROADCAST_CONNECTED))
                    startReadingData()
                } else {
                    Log.w(TAG, "[SERVER] accept() returned null socket — connection may have been cancelled")
                }
            } catch (e: Exception) {
                if (serverJob?.isCancelled == true) {
                    Log.d(TAG, "[SERVER] Server socket closed intentionally (stopServer called)")
                } else {
                    Log.e(TAG, "[SERVER] ❌ Server error: ${e.message}")
                }
            }
        }
    }

    private fun stopServer() {
        if (serverJob?.isActive == true) {
            Log.d(TAG, "[SERVER] Stopping server socket...")
        }
        serverJob?.cancel()
        try {
            serverSocket?.close()
            Log.d(TAG, "[SERVER] Server socket closed")
        } catch (e: Exception) {
            Log.e(TAG, "[SERVER] Error closing server socket: ${e.message}")
        } finally {
            serverSocket = null
        }
    }


    @SuppressLint("MissingPermission")
    private fun startReadingData() {
        val socketInfo = bluetoothSocket?.remoteDevice?.let { "${it.name} (${it.address})" } ?: "unknown"
        Log.i(TAG, "[READ] Starting data reader loop for: $socketInfo")

        readerJob = CoroutineScope(Dispatchers.IO).launch {
            val inputStream: InputStream? = bluetoothSocket?.inputStream
            val buffer = ByteArray(1024)
            var bytesRead: Int
            var totalBytesRead = 0

            if (inputStream == null) {
                Log.e(TAG, "[READ] InputStream is NULL — cannot start reading")
                return@launch
            }

            Log.d(TAG, "[READ] InputStream ready. Waiting for data...")

            while (true) {
                try {
                    bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) {
                        Log.w(TAG, "[READ] Stream returned -1 — remote device disconnected cleanly")
                        sendBroadcast(Intent(BROADCAST_DISCONNECTED))
                        cleanConnectionResources()
                        break
                    }
                    totalBytesRead += bytesRead
                    val message = String(buffer, 0, bytesRead)
                    Log.d(TAG, "[READ] Received ${bytesRead}B (total=${totalBytesRead}B): \"$message\"")
                    val intent = Intent(BROADCAST_MESSAGE_RECEIVED).apply {
                        putExtra(EXTRA_MESSAGE_DATA, message)
                    }
                    sendBroadcast(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "[READ]  Read error (connection likely dropped): ${e.message}")
                    sendBroadcast(Intent(BROADCAST_DISCONNECTED))
                    cleanConnectionResources()
                    break
                }
            }
            Log.i(TAG, "[READ] Reader loop exited for: $socketInfo")
        }
    }

    private fun sendMessage(message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val outputStream: OutputStream? = bluetoothSocket?.outputStream
            if (outputStream == null) {
                Log.e(TAG, "[SEND] FAILED: Cannot send — socket not connected or outputStream is null")
                return@launch
            }
            try {
                val bytes = message.toByteArray()
                outputStream.write(bytes)
                Log.d(TAG, "[SEND] SUCCESS: Sent ${bytes.size}B: \"$message\"")
            } catch (e: Exception) {
                Log.e(TAG, "[SEND] FAILED: Send failed: ${e.message}")
            }
        }
    }

    // CLEANUP
    private fun disconnect() {
        val wasConnected = bluetoothSocket != null
        if (wasConnected) {
            Log.i(TAG, "--- [DISCONNECT] Closing active connection ---")
        }
        connectionJob?.cancel()
        readerJob?.cancel()
        cleanConnectionResources()
    }

    private fun cleanConnectionResources() {
        try {
            bluetoothSocket?.close()
            Log.d(TAG, "[CLEANUP] BluetoothSocket closed")
        } catch (closeException: Exception) {
            Log.e(TAG, "[CLEANUP] Error closing socket: ${closeException.message}")
        } finally {
            bluetoothSocket = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "=== BluetoothService DESTROYED ===")
        stopServer()
        disconnect()
    }
}