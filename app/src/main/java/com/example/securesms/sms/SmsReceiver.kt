package com.example.securesms.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.securesms.protocol.SmsMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Listens for incoming SMS and emits them on [events] so the host session can
 * forward new messages to a connected client.  The receiver is registered in
 * the manifest so it fires even if the app UI has been backgrounded, but the
 * forwarding only happens when a live session has subscribed.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val now = System.currentTimeMillis()
        messages.forEach { sms ->
            val wrapped = SmsMessage(
                id = now + sms.hashCode(),
                threadId = -1L, // unknown until inserted by system
                address = sms.originatingAddress ?: "",
                body = sms.messageBody ?: "",
                timestamp = sms.timestampMillis.takeIf { it > 0 } ?: now,
                inbound = true
            )
            _events.tryEmit(wrapped)
        }
    }

    companion object {
        private val _events = MutableSharedFlow<SmsMessage>(
            replay = 0, extraBufferCapacity = 32
        )
        val events: SharedFlow<SmsMessage> = _events.asSharedFlow()
    }
}
