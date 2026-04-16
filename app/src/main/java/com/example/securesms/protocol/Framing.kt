package com.example.securesms.protocol

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * Length-prefixed binary framing.  Each frame: [uint32 BE length][bytes payload].
 * Max frame size is bounded to avoid memory exhaustion attacks.
 */
object Framing {
    private const val MAX_FRAME = 1 shl 20 // 1 MiB

    fun writeFrame(out: OutputStream, data: ByteArray) {
        require(data.size <= MAX_FRAME) { "frame too large" }
        val dos = if (out is DataOutputStream) out else DataOutputStream(out)
        dos.writeInt(data.size)
        dos.write(data)
        dos.flush()
    }

    fun readFrame(input: InputStream): ByteArray {
        val dis = if (input is DataInputStream) input else DataInputStream(input)
        val len = try { dis.readInt() } catch (e: EOFException) { throw PeerClosed() }
        if (len < 0 || len > MAX_FRAME) throw IllegalStateException("invalid frame length $len")
        val buf = ByteArray(len)
        dis.readFully(buf)
        return buf
    }
}

class PeerClosed : RuntimeException("peer closed connection")
