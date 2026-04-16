package com.example.securesms.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64

/**
 * Stores identity fingerprints of devices the user has approved during
 * numeric-comparison pairing.  Using [EncryptedSharedPreferences] means the
 * fingerprints are encrypted at rest with a key managed by the Android
 * Keystore.
 *
 * NOTE: This only stores *fingerprints*, not private keys — the current
 * implementation uses fresh ephemeral ECDH keys per session and re-runs
 * numeric comparison only if the fingerprint is unknown.
 */
class TrustedDeviceStore private constructor(private val prefs: SharedPreferences) {

    data class TrustedDevice(val id: String, val label: String, val fingerprintHex: String)

    fun isTrusted(id: String, fingerprintHex: String): Boolean =
        prefs.getString(keyFor(id), null) == fingerprintHex

    fun trust(id: String, label: String, fingerprintHex: String) {
        prefs.edit()
            .putString(keyFor(id), fingerprintHex)
            .putString(labelKeyFor(id), label)
            .apply()
    }

    fun forget(id: String) {
        prefs.edit().remove(keyFor(id)).remove(labelKeyFor(id)).apply()
    }

    fun list(): List<TrustedDevice> {
        val out = mutableListOf<TrustedDevice>()
        for ((k, v) in prefs.all) {
            if (k.startsWith(FP_PREFIX) && v is String) {
                val id = k.removePrefix(FP_PREFIX)
                val label = prefs.getString(labelKeyFor(id), id) ?: id
                out += TrustedDevice(id, label, v)
            }
        }
        return out
    }

    private fun keyFor(id: String) = FP_PREFIX + id
    private fun labelKeyFor(id: String) = LABEL_PREFIX + id

    companion object {
        private const val FP_PREFIX = "fp:"
        private const val LABEL_PREFIX = "label:"

        @Volatile private var instance: TrustedDeviceStore? = null

        fun get(context: Context): TrustedDeviceStore = instance ?: synchronized(this) {
            instance ?: TrustedDeviceStore(IdentityKeys.prefs(context.applicationContext))
                .also { instance = it }
        }

        fun fingerprintHex(bytes: ByteArray): String {
            val sb = StringBuilder(bytes.size * 2)
            for (b in bytes) sb.append("%02x".format(b))
            return sb.toString()
        }

        fun toB64(bytes: ByteArray): String =
            Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
    }
}
