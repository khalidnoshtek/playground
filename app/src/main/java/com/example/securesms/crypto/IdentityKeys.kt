package com.example.securesms.crypto

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Long-term ECDSA/ECDH identity key pair, persisted in EncryptedSharedPreferences.
 * Curve: secp256r1 (P-256). Same key is used for ECDSA signatures and as
 * identity pin; ephemeral ECDH keys are created per session.
 */
class IdentityKeys private constructor(
    val keyPair: KeyPair,
    val publicKeyEncoded: ByteArray,
    val publicFingerprint: String
) {
    companion object {
        private const val PREFS = "secure_sms_trust"
        private const val KEY_PRIV = "identity_priv"
        private const val KEY_PUB = "identity_pub"
        private const val CURVE = "secp256r1"

        fun loadOrCreate(ctx: Context): IdentityKeys {
            val prefs = prefs(ctx)
            val privB64 = prefs.getString(KEY_PRIV, null)
            val pubB64 = prefs.getString(KEY_PUB, null)
            val kp = if (privB64 != null && pubB64 != null) {
                val priv = decodePrivate(Base64.decode(privB64, Base64.NO_WRAP))
                val pub = decodePublic(Base64.decode(pubB64, Base64.NO_WRAP))
                KeyPair(pub, priv)
            } else {
                val g = KeyPairGenerator.getInstance("EC")
                g.initialize(ECGenParameterSpec(CURVE))
                val created = g.generateKeyPair()
                prefs.edit()
                    .putString(KEY_PRIV, Base64.encodeToString(created.private.encoded, Base64.NO_WRAP))
                    .putString(KEY_PUB, Base64.encodeToString(created.public.encoded, Base64.NO_WRAP))
                    .apply()
                created
            }
            val encoded = kp.public.encoded
            return IdentityKeys(kp, encoded, fingerprint(encoded))
        }

        fun prefs(ctx: Context) = EncryptedSharedPreferences.create(
            ctx,
            PREFS,
            MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        fun decodePublic(x509: ByteArray): PublicKey =
            KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(x509))

        fun decodePrivate(pkcs8: ByteArray): PrivateKey =
            KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(pkcs8))

        /** Short, user-readable fingerprint of a public key. */
        fun fingerprint(encoded: ByteArray): String {
            val hash = MessageDigest.getInstance("SHA-256").digest(encoded)
            return hash.take(8).joinToString(":") { "%02X".format(it) }
        }
    }
}
