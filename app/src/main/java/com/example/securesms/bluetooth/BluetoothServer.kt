package com.example.securesms.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import java.io.Closeable
import java.io.IOException

/**
 * Blocking RFCOMM server.  Callers should drive it from a background
 * coroutine/dispatcher because [accept] parks the current thread.
 *
 * We require an *authenticated and encrypted* Bluetooth link via
 * `listenUsingRfcommWithServiceRecord`, so the underlying devices must be
 * OS-level paired (bonded) before our handshake runs on top.
 */
@SuppressLint("MissingPermission")
class BluetoothServer(private val adapter: BluetoothAdapter) : Closeable {

    private var serverSocket: BluetoothServerSocket? = null

    @Throws(IOException::class)
    fun start() {
        serverSocket = adapter.listenUsingRfcommWithServiceRecord(SDP_NAME, SECURE_SMS_UUID)
    }

    @Throws(IOException::class)
    fun accept(): BluetoothSocket {
        val s = serverSocket ?: error("server not started")
        return s.accept()
    }

    override fun close() {
        runCatching { serverSocket?.close() }
        serverSocket = null
    }
}
