package com.example.securesms.crypto

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HKDF-SHA256 derivation of per-direction keys and a short authentication
 * string used for numeric-comparison pairing.
 *
 * Binding the transcript (both ephemeral public keys, in canonical order)
 * into the salt prevents a MITM from splicing their own keys in without
 * changing the displayed pairing code.
 */
object SessionKeys {
    private const val KEY_LEN = 32 // AES-256

    data class Derived(
        val hostToClient: ByteArray,
        val clientToHost: ByteArray,
        /** 6-digit code for user confirmation on both devices. */
        val pairingCode: String,
        /** Long-term identifier for the remote (first 16 bytes of SHA-256(transcript)). */
        val sessionId: ByteArray
    )

    fun derive(sharedSecret: ByteArray, hostPub: ByteArray, clientPub: ByteArray): Derived {
        val transcript = MessageDigest.getInstance("SHA-256").run {
            update(hostPub)
            update(clientPub)
            digest()
        }
        val prk = hkdfExtract(salt = transcript, ikm = sharedSecret)
        val h2c = hkdfExpand(prk, "SecureSms/host->client".toByteArray(), KEY_LEN)
        val c2h = hkdfExpand(prk, "SecureSms/client->host".toByteArray(), KEY_LEN)
        val authBytes = hkdfExpand(prk, "SecureSms/auth".toByteArray(), 4)
        val code = (((authBytes[0].toInt() and 0xFF) shl 24) or
                ((authBytes[1].toInt() and 0xFF) shl 16) or
                ((authBytes[2].toInt() and 0xFF) shl 8) or
                (authBytes[3].toInt() and 0xFF)).let {
            val positive = it and 0x7FFFFFFF
            "%06d".format(positive % 1_000_000)
        }
        return Derived(
            hostToClient = h2c,
            clientToHost = c2h,
            pairingCode = code,
            sessionId = transcript.copyOf(16)
        )
    }

    private fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        return mac.doFinal(ikm)
    }

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, outLen: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val hashLen = mac.macLength
        val n = (outLen + hashLen - 1) / hashLen
        require(n <= 255) { "HKDF output too large" }
        var t = ByteArray(0)
        val out = ByteArray(outLen)
        var pos = 0
        for (i in 1..n) {
            mac.reset()
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info)
            mac.update(i.toByte())
            t = mac.doFinal()
            val copy = minOf(hashLen, outLen - pos)
            System.arraycopy(t, 0, out, pos, copy)
            pos += copy
        }
        return out
    }
}
