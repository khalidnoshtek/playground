package com.example.securesms.crypto

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

/**
 * Elliptic-curve Diffie-Hellman (secp256r1 / P-256) key exchange.
 *
 * Public keys are serialized as X.509 SubjectPublicKeyInfo so the format is
 * portable between Android versions without depending on BouncyCastle.
 */
object KeyExchange {
    private const val CURVE = "secp256r1"
    private const val ALGO = "EC"

    fun generateEphemeralKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance(ALGO)
        kpg.initialize(ECGenParameterSpec(CURVE))
        return kpg.generateKeyPair()
    }

    fun encodePublicKey(pk: PublicKey): ByteArray = pk.encoded

    fun decodePublicKey(bytes: ByteArray): ECPublicKey {
        val spec = X509EncodedKeySpec(bytes)
        val kf = KeyFactory.getInstance(ALGO)
        return kf.generatePublic(spec) as ECPublicKey
    }

    /**
     * Derive the raw shared secret via ECDH.  Caller is expected to run this
     * through a KDF (see [SessionKeys]) before using it for encryption.
     */
    fun deriveSharedSecret(localPrivate: java.security.PrivateKey, remotePublic: PublicKey): ByteArray {
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(localPrivate)
        ka.doPhase(remotePublic, true)
        return ka.generateSecret()
    }
}
