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
import java.util.UUID

class BluetoothService: Service() {
    private var bluetoothSocket: BluetoothSocket? = null
    private var job: Job? = null
    private val TAG = "BluetoothService"
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BluetoothService created")
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand bluetooth service is running and listening for connections.....")

        val macAddress =intent?.getStringExtra("MAC_ADDRESS")
        if (macAddress == null) {
            Log.e(TAG, "cant retrieve mac address from intent")
            sendBroadcast(Intent("com.example.miniivi_dantruong.BLUETOOTH_CONNECTION_ERROR"))
            return START_NOT_STICKY
        }

        Log.d(TAG, "mac address: $macAddress")

        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled")
            sendBroadcast(Intent("com.example.miniivi_dantruong.BLUETOOTH_CONNECTION_ERROR"))
            return START_NOT_STICKY
        }

        val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(macAddress)
        Log.d(TAG, "attempting to connect to device: ${device.name} (${device.address})")

        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Connecting to device: ${device.name} (${device.address})")
                 bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
                Log.d(TAG, "cancel scanning")
                bluetoothAdapter.cancelDiscovery()
                bluetoothSocket?.connect()
                Log.d(TAG, "Connected to device: ${device.name} (${device.address})")
                sendBroadcast(Intent("com.example.miniivi_dantruong.BLUETOOTH_CONNECTED"))
            }catch (e: Exception) {
                Log.e(TAG, "Error connecting to device: ${e.message}")
                sendBroadcast(Intent("com.example.miniivi_dantruong.BLUETOOTH_CONNECTION_ERROR"))
                try {
                    bluetoothSocket?.close()
                } catch (closeException: Exception) {
                    Log.e(TAG, "Error closing socket: ${closeException.message}")
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "BluetoothService destroyed")
    }

}