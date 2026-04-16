package com.example.securesms.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun RoleSelectionScreen(onHost: () -> Unit, onClient: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("SecureSms Link", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Choose this device's role.  The host reads messages from its SIM; the client views them over an encrypted Bluetooth link.",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onHost, modifier = Modifier.fillMaxWidth(0.8f)) {
            Text("Host — this phone has the SIM")
        }
        OutlinedButton(onClick = onClient, modifier = Modifier.fillMaxWidth(0.8f)) {
            Text("Client — this phone reads messages")
        }
        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Read-only & consent-based", fontWeight = FontWeight.SemiBold)
                Text(
                    "• The client cannot send or delete SMS.\n" +
                    "• Both phones must already be Bluetooth-paired.\n" +
                    "• First connection requires confirming a 6-digit code.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
