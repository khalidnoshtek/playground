package com.example.securesms.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.securesms.SecureSmsApp
import com.example.securesms.bluetooth.BluetoothServer
import com.example.securesms.bluetooth.HostSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun HostScreen(adapter: BluetoothAdapter, onBack: () -> Unit) {
    val ctx = LocalContext.current.applicationContext as SecureSmsApp
    val server = remember { BluetoothServer(adapter) }
    var currentSession by remember { mutableStateOf<HostSession?>(null) }
    var listenError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                server.start()
                while (true) {
                    val socket = server.accept()
                    val session = HostSession(
                        socket, ctx.identity, ctx.trustedDevices, ctx.smsRepository
                    )
                    session.start()
                    currentSession = session
                }
            } catch (t: Throwable) {
                listenError = t.message
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            currentSession?.close()
            server.close()
        }
    }

    val session = currentSession
    val state = session?.state?.collectAsState()?.value

    Scaffold(topBar = { TopAppBar(title = { Text("Host") }) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Status", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    if (listenError != null) {
                        Text("Failed to start server: $listenError")
                    } else when (state) {
                        null -> Row(text = "Waiting for a client to connect…", spinner = true)
                        is HostSession.State.Handshaking -> Row(text = "Handshaking…", spinner = true)
                        is HostSession.State.AwaitingPairing -> Row(text = "Waiting for pairing confirmation")
                        is HostSession.State.Active -> Text("Connected to ${state.peerName} (${state.peerAddress})")
                        is HostSession.State.Failed -> Text("Failed: ${state.reason}")
                        HostSession.State.Closed -> Row(text = "Disconnected. Waiting for next client…", spinner = true)
                    }
                }
            }
            if (state is HostSession.State.AwaitingPairing) {
                PairingDialog(
                    code = state.code,
                    peerName = state.peerName,
                    fingerprint = state.peerFingerprint,
                    onConfirm = state.onConfirm,
                    onReject = state.onReject
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { currentSession?.close(); onBack() }) { Text("Stop hosting") }
        }
    }
}

@Composable
private fun Row(text: String, spinner: Boolean = false) {
    Box(Modifier.fillMaxWidth()) {
        Column {
            Text(text)
            if (spinner) {
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator()
            }
        }
    }
}
