package com.example.miniivi_dantruong.ui.bluetooth

sealed class BluetoothStatus {
    object Disconnected : BluetoothStatus()
    object Connecting : BluetoothStatus()
    object Connected : BluetoothStatus()
}