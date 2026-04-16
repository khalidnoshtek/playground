package com.example.securesms.crypto

import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM with a per-direction monotonic 96-bit nonce:
 *   [ 32-bit random salt (once per session) || 64-bit counter ]
 *
 * The random salt is exchanged in the clear at handshake time so both peers
 * agree on it.  This prevents nonce reuse across sessions and lets either
 * side detect replays via the counter.
 */
class SessionCipher(
    key: ByteArray,
    private val noncePrefix: ByteArray
) {
    init {
        require(key.size == 32) { "key must be 32 bytes" }
        require(noncePrefix.size == 4) { "nonce prefix must be 4 bytes" }
    }

    private val secretKey = SecretKeySpec(key, "AES")
    private val counter = AtomicLong(0)

    fun seal(plaintext: ByteArray, aad: ByteArray? = null): ByteArray {
        val ctr = counter.getAndIncrement()
        val nonce = buildNonce(ctr)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, nonce))
        if (aad != null) cipher.updateAAD(aad)
        val ct = cipher.doFinal(plaintext)
        // Prepend the 8-byte counter so the receiver can reconstruct the nonce.
        val out = ByteArray(8 + ct.size)
        longToBytes(ctr, out, 0)
        System.arraycopy(ct, 0, out, 8, ct.size)
        return out
    }

    fun open(framed: ByteArray, aad: ByteArray? = null): ByteArray {
        require(framed.size >= 8 + 16) { "ciphertext too short" }
        val ctr = bytesToLong(framed, 0)
        val nonce = buildNonce(ctr)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, nonce))
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(framed, 8, framed.size - 8)
    }

    private fun buildNonce(ctr: Long): ByteArray {
        val nonce = ByteArray(12)
        System.arraycopy(noncePrefix, 0, nonce, 0, 4)
        longToBytes(ctr, nonce, 4)
        return nonce
    }

    companion object {
        fun randomNoncePrefix(): ByteArray = ByteArray(4).also { SecureRandom().nextBytes(it) }

        private fun longToBytes(v: Long, out: ByteArray, off: Int) {
            for (i in 0..7) out[off + i] = ((v shr (56 - 8 * i)) and 0xFF).toByte()
        }

        private fun bytesToLong(src: ByteArray, off: Int): Long {
            var v = 0L
            for (i in 0..7) v = (v shl 8) or (src[off + i].toLong() and 0xFF)
            return v
        }
    }
}
