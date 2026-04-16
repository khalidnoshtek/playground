package com.example.securesms.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.util.Log
import com.example.securesms.crypto.IdentityKeys
import com.example.securesms.crypto.TrustedDeviceStore
import com.example.securesms.protocol.Conversation
import com.example.securesms.protocol.Envelope
import com.example.securesms.protocol.MsgType
import com.example.securesms.protocol.SmsMessage
import com.example.securesms.protocol.parseConversations
import com.example.securesms.protocol.parseMessages
import com.example.securesms.protocol.parseNewMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Client-side session: connects to a bonded host, drives the handshake, and
 * exposes a small request/response API plus a live flow of inbound messages.
 */
@SuppressLint("MissingPermission")
class ClientSession(
    private val adapter: BluetoothAdapter,
    private val device: BluetoothDevice,
    private val identity: IdentityKeys,
    private val trusted: TrustedDeviceStore
) {
    sealed interface State {
        data object Connecting : State
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

    private val _state = MutableStateFlow<State>(State.Connecting)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _messagesFor = MutableStateFlow<Pair<Long, List<SmsMessage>>?>(null)
    val messagesFor: StateFlow<Pair<Long, List<SmsMessage>>?> = _messagesFor.asStateFlow()

    private val _events = MutableSharedFlow<SmsMessage>(extraBufferCapacity = 32)
    val events: SharedFlow<SmsMessage> = _events.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val outbox = Channel<JSONObject>(Channel.UNLIMITED)
    private lateinit var channel: SecureChannel

    fun start() {
        scope.launch {
            try {
                val socket = BluetoothClient.connect(adapter, device)
                _state.value = State.Handshaking
                val peerAddress = device.address
                val peerName = device.name ?: peerAddress
                channel = SecureChannel(
                    input = socket.inputStream,
                    output = socket.outputStream,
                    role = Role.CLIENT,
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
                val final = _state.first {
                    it is State.Active || it is State.Closed || it is State.Failed
                }
                if (final !is State.Active) return@launch

                scope.launch { readerLoop() }
                scope.launch { writerLoop() }
                requestConversations()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "client session error", t)
                _state.value = State.Failed(t.message ?: t::class.java.simpleName)
                close()
            }
        }
    }

    fun requestConversations() { outbox.trySend(Envelope.request(MsgType.LIST_CONVERSATIONS)) }

    fun requestMessages(threadId: Long) {
        outbox.trySend(
            Envelope.request(MsgType.LIST_MESSAGES, JSONObject().put("threadId", threadId))
        )
    }

    fun close() {
        scope.cancel()
        _state.value = State.Closed
    }

    private suspend fun readerLoop() {
        while (true) {
            val msg = channel.receive()
            when (msg.getString("type")) {
                MsgType.CONVERSATIONS -> _conversations.value = msg.parseConversations()
                MsgType.MESSAGES -> _messagesFor.value = msg.parseMessages()
                MsgType.NEW_MESSAGE -> _events.tryEmit(msg.parseNewMessage())
                MsgType.ERROR, MsgType.PONG -> {}
            }
        }
    }

    private suspend fun writerLoop() {
        for (req in outbox) channel.send(req)
    }

    companion object { private const val TAG = "ClientSession" }
}
