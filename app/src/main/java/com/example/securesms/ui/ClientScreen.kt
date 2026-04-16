package com.example.securesms.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.securesms.SecureSmsApp
import com.example.securesms.bluetooth.ClientSession
import com.example.securesms.protocol.Conversation
import com.example.securesms.protocol.SmsMessage
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun ClientScreen(adapter: BluetoothAdapter, onBack: () -> Unit) {
    val ctx = LocalContext.current.applicationContext as SecureSmsApp
    val bonded = remember { adapter.bondedDevices.toList() }
    var session by remember { mutableStateOf<ClientSession?>(null) }
    var selectedThread by remember { mutableStateOf<Long?>(null) }

    DisposableEffect(session) {
        onDispose { session?.close() }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Client") }) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (session == null) {
                Text("Bonded Bluetooth devices", style = MaterialTheme.typography.titleMedium)
                if (bonded.isEmpty()) {
                    Text("No paired devices found. Pair your host phone in system Bluetooth settings first.")
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(bonded) { device ->
                        DeviceRow(device) {
                            val s = ClientSession(adapter, device, ctx.identity, ctx.trustedDevices)
                            s.start()
                            session = s
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onBack) { Text("Back") }
            } else {
                SessionContent(
                    session = session!!,
                    selectedThread = selectedThread,
                    onOpenThread = { selectedThread = it; session!!.requestMessages(it) },
                    onCloseThread = { selectedThread = null },
                    onDisconnect = {
                        session!!.close()
                        session = null
                        selectedThread = null
                    }
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(device: BluetoothDevice, onConnect: () -> Unit) {
    @SuppressLint("MissingPermission") val name = device.name ?: device.address
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onConnect() }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold)
                Text(device.address, style = MaterialTheme.typography.labelSmall)
            }
            Button(onClick = onConnect) { Text("Connect") }
        }
    }
}

@Composable
private fun SessionContent(
    session: ClientSession,
    selectedThread: Long?,
    onOpenThread: (Long) -> Unit,
    onCloseThread: () -> Unit,
    onDisconnect: () -> Unit
) {
    val state by session.state.collectAsState()
    val conversations by session.conversations.collectAsState()
    val messagesFor by session.messagesFor.collectAsState()

    // Live new-message toast via events flow
    LaunchedEffect(session) {
        session.events.collect { _ -> session.requestConversations() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Session", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                when (val s = state) {
                    ClientSession.State.Connecting, ClientSession.State.Handshaking -> {
                        CircularProgressIndicator()
                        Text("Connecting…")
                    }
                    is ClientSession.State.AwaitingPairing -> PairingDialog(
                        code = s.code,
                        peerName = s.peerName,
                        fingerprint = s.peerFingerprint,
                        onConfirm = s.onConfirm,
                        onReject = s.onReject
                    )
                    is ClientSession.State.Active -> Text("Connected to ${s.peerName}")
                    is ClientSession.State.Failed -> Text("Failed: ${s.reason}")
                    ClientSession.State.Closed -> Text("Disconnected")
                }
            }
        }

        if (selectedThread == null) {
            Button(onClick = { session.requestConversations() }) { Text("Refresh") }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(conversations) { conv -> ConversationRow(conv) { onOpenThread(conv.threadId) } }
            }
        } else {
            OutlinedButton(onClick = onCloseThread) { Text("← All conversations") }
            val pair = messagesFor
            if (pair == null || pair.first != selectedThread) {
                CircularProgressIndicator()
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(pair.second) { m -> MessageBubble(m) }
                }
            }
        }

        Divider()
        Button(onClick = onDisconnect) { Text("Disconnect") }
    }
}

@Composable
private fun ConversationRow(conv: Conversation, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(Modifier.padding(12.dp)) {
            Text(conv.displayName ?: conv.address, fontWeight = FontWeight.SemiBold)
            Text(conv.snippet, maxLines = 2, style = MaterialTheme.typography.bodySmall)
            Text(formatTs(conv.lastTimestamp), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MessageBubble(msg: SmsMessage) {
    val bg = if (msg.inbound) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    val align = if (msg.inbound) Alignment.Start else Alignment.End
    Column(Modifier.fillMaxWidth(), horizontalAlignment = align) {
        Surface(color = bg, shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(8.dp)) {
                Text(msg.body)
                Text(formatTs(msg.timestamp), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}

private fun formatTs(ms: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ms))
