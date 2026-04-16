package com.example.securesms.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun PairingDialog(
    code: String,
    peerName: String,
    fingerprint: String,
    onConfirm: () -> Unit,
    onReject: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onReject,
        title = { Text("Confirm pairing") },
        text = {
            Column {
                Text("Make sure the same 6-digit code appears on “$peerName”.")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = code.chunked(3).joinToString(" "),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Peer identity fingerprint:\n$fingerprint",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Codes match, trust") }
        },
        dismissButton = {
            TextButton(onClick = onReject) { Text("Codes differ, abort") }
        }
    )
}
