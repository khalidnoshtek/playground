package com.example.securesms.bluetooth

import android.util.Base64
import com.example.securesms.crypto.IdentityKeys
import com.example.securesms.crypto.KeyExchange
import com.example.securesms.crypto.SessionCipher
import com.example.securesms.crypto.SessionKeys
import com.example.securesms.crypto.TrustedDeviceStore
import com.example.securesms.protocol.Framing
import org.json.JSONObject
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.Signature

enum class Role { HOST, CLIENT }

/**
 * Outcome of the handshake.  The caller must display [pairingCode] to the user
 * and call [confirmPairing] once the user verifies that both phones show the
 * same six digits.  If [peerAlreadyTrusted] is true the code has still been
 * computed (so callers may display it for reassurance) but user confirmation
 * is not strictly required; callers may auto-confirm.
 */
data class HandshakeResult(
    val pairingCode: String,
    val peerAlreadyTrusted: Boolean,
    val peerFingerprint: String,
    val peerAddressId: String,
    private val onConfirm: () -> Unit
) {
    fun confirmPairing() = onConfirm()
}

/**
 * End-to-end encrypted JSON channel on top of an arbitrary byte-stream.
 *
 * Security properties (assuming peers are honestly implemented):
 *  - Confidentiality + integrity: AES-256-GCM with unique 96-bit nonces.
 *  - Forward secrecy: fresh ephemeral ECDH key pair per session.
 *  - Peer authentication: ECDSA signature over the full transcript, verified
 *    against a pinned identity fingerprint stored in an encrypted preference.
 *  - First-use pairing: user must confirm a 6-digit SAS on both devices
 *    before the transcript is accepted and the peer fingerprint pinned.
 */
class SecureChannel(
    private val input: InputStream,
    private val output: OutputStream,
    private val role: Role,
    private val identity: IdentityKeys,
    private val trustedDevices: TrustedDeviceStore,
    private val peerAddressId: String,
    private val peerLabel: String
) : Closeable {

    private var cipherSend: SessionCipher? = null
    private var cipherRecv: SessionCipher? = null
    @Volatile private var confirmed = false

    fun performHandshake(): HandshakeResult {
        val ephemKeys = KeyExchange.generateEphemeralKeyPair()
        val noncePrefixLocal = SessionCipher.randomNoncePrefix()

        val helloLocal = JSONObject().apply {
            put("role", role.name)
            put("ephem", Base64.encodeToString(KeyExchange.encodePublicKey(ephemKeys.public), Base64.NO_WRAP))
            put("identity", Base64.encodeToString(identity.publicKeyEncoded, Base64.NO_WRAP))
            put("nonce", Base64.encodeToString(noncePrefixLocal, Base64.NO_WRAP))
        }
        Framing.writeFrame(output, helloLocal.toString().toByteArray(Charsets.UTF_8))

        val helloPeerBytes = Framing.readFrame(input)
        val helloPeer = JSONObject(String(helloPeerBytes, Charsets.UTF_8))
        val peerRole = Role.valueOf(helloPeer.getString("role"))
        check(peerRole != role) { "both peers announced the same role" }

        val peerEphem = KeyExchange.decodePublicKey(Base64.decode(helloPeer.getString("ephem"), Base64.NO_WRAP))
        val peerIdentityBytes = Base64.decode(helloPeer.getString("identity"), Base64.NO_WRAP)
        val peerIdentity = IdentityKeys.decodePublic(peerIdentityBytes)
        val noncePrefixPeer = Base64.decode(helloPeer.getString("nonce"), Base64.NO_WRAP)

        val shared = KeyExchange.deriveSharedSecret(ephemKeys.private, peerEphem)

        // Canonical ordering: host's bytes first, then client's.
        val hostEphem: ByteArray; val clientEphem: ByteArray
        val hostId: ByteArray;    val clientId: ByteArray
        val hostNonce: ByteArray; val clientNonce: ByteArray
        if (role == Role.HOST) {
            hostEphem = KeyExchange.encodePublicKey(ephemKeys.public)
            clientEphem = KeyExchange.encodePublicKey(peerEphem)
            hostId = identity.publicKeyEncoded
            clientId = peerIdentityBytes
            hostNonce = noncePrefixLocal
            clientNonce = noncePrefixPeer
        } else {
            clientEphem = KeyExchange.encodePublicKey(ephemKeys.public)
            hostEphem = KeyExchange.encodePublicKey(peerEphem)
            clientId = identity.publicKeyEncoded
            hostId = peerIdentityBytes
            clientNonce = noncePrefixLocal
            hostNonce = noncePrefixPeer
        }
        val derived = SessionKeys.derive(shared, hostEphem, clientEphem)

        // Transcript bound into signatures pins every byte of the handshake.
        val transcript = MessageDigest.getInstance("SHA-256").run {
            update("SecureSms/v1".toByteArray())
            update(hostEphem); update(hostId); update(hostNonce)
            update(clientEphem); update(clientId); update(clientNonce)
            digest()
        }

        // Exchange signatures so each side authenticates the transcript with
        // its long-term identity key.  A MITM who swaps ephemerals cannot
        // forge these without the corresponding private key.
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(identity.keyPair.private)
        sig.update(transcript)
        val signed = sig.sign()
        Framing.writeFrame(output, JSONObject()
            .put("sig", Base64.encodeToString(signed, Base64.NO_WRAP))
            .toString().toByteArray(Charsets.UTF_8))

        val peerSigBytes = Framing.readFrame(input)
        val peerSig = Base64.decode(JSONObject(String(peerSigBytes, Charsets.UTF_8))
            .getString("sig"), Base64.NO_WRAP)
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(peerIdentity)
        verifier.update(transcript)
        check(verifier.verify(peerSig)) { "peer signature verification failed" }

        val peerFingerprint = TrustedDeviceStore.fingerprintHex(
            MessageDigest.getInstance("SHA-256").digest(peerIdentityBytes)
        )

        val isReconnection = trustedDevices.isTrusted(peerAddressId, peerFingerprint)

        // Set up send/receive ciphers.  Directional keying + distinct nonce
        // prefixes ensure nonces are never reused even if a peer is lazy.
        val sendKey = if (role == Role.HOST) derived.hostToClient else derived.clientToHost
        val recvKey = if (role == Role.HOST) derived.clientToHost else derived.hostToClient
        val sendNonce = if (role == Role.HOST) hostNonce else clientNonce
        val recvNonce = if (role == Role.HOST) clientNonce else hostNonce
        cipherSend = SessionCipher(sendKey, sendNonce)
        cipherRecv = SessionCipher(recvKey, recvNonce)

        return HandshakeResult(
            pairingCode = derived.pairingCode,
            peerAlreadyTrusted = isReconnection,
            peerFingerprint = peerFingerprint,
            peerAddressId = peerAddressId,
            onConfirm = {
                trustedDevices.trust(peerAddressId, peerLabel, peerFingerprint)
                confirmed = true
            }
        ).also { if (isReconnection) confirmed = true }
    }

    fun send(obj: JSONObject) {
        val c = cipherSend ?: error("handshake not performed")
        check(confirmed) { "pairing not confirmed" }
        val pt = obj.toString().toByteArray(Charsets.UTF_8)
        Framing.writeFrame(output, c.seal(pt))
    }

    fun receive(): JSONObject {
        val c = cipherRecv ?: error("handshake not performed")
        check(confirmed) { "pairing not confirmed" }
        val pt = c.open(Framing.readFrame(input))
        return JSONObject(String(pt, Charsets.UTF_8))
    }

    override fun close() {
        runCatching { input.close() }
        runCatching { output.close() }
    }
}
