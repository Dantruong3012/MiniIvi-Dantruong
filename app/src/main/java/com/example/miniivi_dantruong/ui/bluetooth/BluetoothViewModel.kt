package com.example.miniivi_dantruong.ui.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import com.example.miniivi_dantruong.service.BluetoothService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class BluetoothViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel(), DefaultLifecycleObserver {

    private val TAG = "BluetoothViewModel"

    private val _bluetoothStatus = MutableStateFlow<BluetoothStatus>(BluetoothStatus.Disconnected)
    val bluetoothStatus = _bluetoothStatus.asStateFlow()

    private val _receivedMessages = MutableStateFlow<List<String>>(emptyList())
    val receivedMessages = _receivedMessages.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val scannedDevices = _scannedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Broadcast received action: ${intent?.action}")
            when (intent?.action) {
                BluetoothService.BROADCAST_CONNECTED -> {
                    _bluetoothStatus.value = BluetoothStatus.Connected
                }
                BluetoothService.BROADCAST_CONNECTION_ERROR -> {
                    _bluetoothStatus.value = BluetoothStatus.Disconnected
                }
                BluetoothService.BROADCAST_DISCONNECTED -> {
                    _bluetoothStatus.value = BluetoothStatus.Disconnected
                }
                BluetoothService.BROADCAST_MESSAGE_RECEIVED -> {
                    val message = intent.getStringExtra(BluetoothService.EXTRA_MESSAGE_DATA)
                    if (message != null) {
                        _receivedMessages.value = _receivedMessages.value + message
                    }
                }
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null) {
                        // Avoid duplicates in scanned devices list
                        if (!_scannedDevices.value.any { it.address == device.address }) {
                            _scannedDevices.value = _scannedDevices.value + device
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    _isScanning.value = true
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isScanning.value = false
                }
            }
        }
    }

    fun connectDevice(macAddress: String) {
        Log.d(TAG, "Connecting to device: $macAddress")
        stopScan() // Stop scanning prior to connecting
        _bluetoothStatus.value = BluetoothStatus.Connecting
        val intent = Intent(context, BluetoothService::class.java).apply {
            action = BluetoothService.ACTION_CONNECT
            putExtra(BluetoothService.EXTRA_MAC_ADDRESS, macAddress)
        }
        context.startService(intent)
    }

    fun disconnectDevice() {
        Log.d(TAG, "Disconnecting device")
        val intent = Intent(context, BluetoothService::class.java).apply {
            action = BluetoothService.ACTION_DISCONNECT
        }
        context.startService(intent)
    }

    fun sendMessage(message: String) {
        Log.d(TAG, "Sending message: $message")
        val intent = Intent(context, BluetoothService::class.java).apply {
            action = BluetoothService.ACTION_SEND
            putExtra(BluetoothService.EXTRA_MESSAGE_DATA, message)
        }
        context.startService(intent)
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        Log.d(TAG, "startScan triggered")
        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager?.adapter ?: return
        
        if (!bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth adapter not enabled, cannot scan")
            return
        }

        _scannedDevices.value = emptyList()
        
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }
        bluetoothAdapter.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        Log.d(TAG, "stopScan triggered")
        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager?.adapter ?: return
        
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }
        _isScanning.value = false
    }

    fun clearMessages() {
        _receivedMessages.value = emptyList()
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Log.d(TAG, "onStart: Registering bluetooth receiver")
        val filter = IntentFilter().apply {
            addAction(BluetoothService.BROADCAST_CONNECTED)
            addAction(BluetoothService.BROADCAST_CONNECTION_ERROR)
            addAction(BluetoothService.BROADCAST_DISCONNECTED)
            addAction(BluetoothService.BROADCAST_MESSAGE_RECEIVED)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(bluetoothReceiver, filter)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        Log.d(TAG, "onStop: Unregistering bluetooth receiver")
        stopScan() // Stop active scans to avoid leaks
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver: ${e.message}")
        }
    }
}