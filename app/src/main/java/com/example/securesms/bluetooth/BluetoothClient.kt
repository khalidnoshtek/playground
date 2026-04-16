package com.example.securesms.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.IOException

@SuppressLint("MissingPermission")
object BluetoothClient {

    /**
     * Connect to an already-bonded device and return a secure RFCOMM socket.
     * Throws if the device is not paired at the OS level.
     */
    @Throws(IOException::class)
    fun connect(adapter: BluetoothAdapter, device: BluetoothDevice): BluetoothSocket {
        // Cancel discovery because it slows connections considerably.
        if (adapter.isDiscovering) adapter.cancelDiscovery()
        val socket = device.createRfcommSocketToServiceRecord(SECURE_SMS_UUID)
        socket.connect()
        return socket
    }
}
