package com.example.securesms.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.example.securesms.crypto.IdentityKeys
import com.example.securesms.crypto.TrustedDeviceStore
import com.example.securesms.protocol.Envelope
import com.example.securesms.protocol.MsgType
import com.example.securesms.protocol.SmsMessage
import com.example.securesms.sms.SmsReceiver
import com.example.securesms.sms.SmsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Handles a single connected client: performs the handshake, surfaces the
 * pairing code to the UI, and then services SMS read requests while the
 * connection is alive.
 */
@SuppressLint("MissingPermission")
class HostSession(
    private val socket: BluetoothSocket,
    private val identity: IdentityKeys,
    private val trusted: TrustedDeviceStore,
    private val repository: SmsRepository
) {
    sealed interface State {
        data object Handshaking : State
        data class AwaitingPairing(
            val code: String,
            val peerFingerprint: String,
            val peerAddress: String,
            val peerName: String,
            val alreadyTrusted: Boolean,
            val onConfirm: () -> Unit,
            val onReject: () -> Unit
        ) : State
        data class Active(val peerAddress: String, val peerName: String) : State
        data class Failed(val reason: String) : State
        data object Closed : State
    }

    private val _state = MutableStateFlow<State>(State.Handshaking)
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var channel: SecureChannel

    fun start() {
        scope.launch {
            try {
                val peerAddress = socket.remoteDevice.address
                val peerName = socket.remoteDevice.name ?: peerAddress
                channel = SecureChannel(
                    input = socket.inputStream,
                    output = socket.outputStream,
                    role = Role.HOST,
                    identity = identity,
                    trustedDevices = trusted,
                    peerAddressId = peerAddress,
                    peerLabel = peerName
                )
                val hs = channel.performHandshake()
                if (hs.peerAlreadyTrusted) {
                    _state.value = State.Active(peerAddress, peerName)
                } else {
                    _state.value = State.AwaitingPairing(
                        code = hs.pairingCode,
                        peerFingerprint = hs.peerFingerprint,
                        peerAddress = peerAddress,
                        peerName = peerName,
                        alreadyTrusted = false,
                        onConfirm = {
                            hs.confirmPairing()
                            _state.value = State.Active(peerAddress, peerName)
                        },
                        onReject = { close() }
                    )
                }
                // Wait for pairing to complete before serving.
                val proceed = awaitActive()
                if (!proceed) return@launch
                // Forward future incoming SMS to the peer.
                scope.launch { forwardIncomingSms() }
                serveRequests()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "host session error", t)
                _state.value = State.Failed(t.message ?: t::class.java.simpleName)
                close()
            }
        }
    }

    /** Returns true if the session advanced to Active. */
    private suspend fun awaitActive(): Boolean {
        val final = _state.first { it is State.Active || it is State.Closed || it is State.Failed }
        return final is State.Active
    }

    private suspend fun forwardIncomingSms() {
        SmsReceiver.events.collect { msg ->
            try { channel.send(Envelope.newMessage(msg)) } catch (_: Throwable) {}
        }
    }

    private suspend fun serveRequests() {
        while (true) {
            val req = channel.receive()
            val type = req.getString("type")
            val payload = req.optJSONObject("payload") ?: JSONObject()
            when (type) {
                MsgType.LIST_CONVERSATIONS -> {
                    val list = repository.listConversations()
                    channel.send(Envelope.conversationsResponse(list))
                }
                MsgType.LIST_MESSAGES -> {
                    val threadId = payload.getLong("threadId")
                    channel.send(Envelope.messagesResponse(threadId, repository.listMessages(threadId)))
                }
                MsgType.PING -> channel.send(Envelope.request(MsgType.PONG))
                else -> channel.send(Envelope.error("unknown_type:$type"))
            }
        }
    }

    fun close() {
        scope.cancel()
        runCatching { socket.close() }
        _state.value = State.Closed
    }

    companion object { private const val TAG = "HostSession" }
}
