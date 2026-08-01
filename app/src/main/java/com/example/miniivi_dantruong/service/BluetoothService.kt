package com.example.miniivi_dantruong.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class BluetoothService: Service() {
    private val TAG = "BluetoothService"

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BluetoothService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand bluetooth service is running and listening for connections.....")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "BluetoothService destroyed")
    }
}