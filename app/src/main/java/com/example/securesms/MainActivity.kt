package com.example.securesms

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.securesms.ui.ClientScreen
import com.example.securesms.ui.HostScreen
import com.example.securesms.ui.RoleSelectionScreen
import com.example.securesms.ui.Screen
import com.example.securesms.ui.theme.SecureSmsTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SecureSmsTheme { AppRoot() } }
    }

    @Composable
    private fun AppRoot() {
        var screen by remember { mutableStateOf<Screen>(Screen.Roles) }
        val adapter = remember { bluetoothAdapter() }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (adapter == null || !adapter.isEnabled) {
                BluetoothUnavailable()
            } else {
                PermissionGate(screen) { granted ->
                    if (granted) {
                        when (screen) {
                            Screen.Roles -> RoleSelectionScreen(
                                onHost = { screen = Screen.Host },
                                onClient = { screen = Screen.Client }
                            )
                            Screen.Host -> HostScreen(adapter) { screen = Screen.Roles }
                            Screen.Client -> ClientScreen(adapter) { screen = Screen.Roles }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun BluetoothUnavailable() {
        Column(Modifier.padding(24.dp)) {
            Text("Bluetooth is off or unavailable.", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("Enable Bluetooth in system settings and restart the app.")
        }
    }

    @Composable
    private fun PermissionGate(screen: Screen, content: @Composable (Boolean) -> Unit) {
        val required = remember(screen) { requiredPermissions(screen) }
        var granted by remember { mutableStateOf(required.all { has(it) }) }
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result -> granted = required.all { result[it] == true || has(it) } }

        if (!granted) {
            Column(Modifier.padding(24.dp)) {
                Text("This app needs the following permissions:", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                required.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(12.dp))
                Button(onClick = { launcher.launch(required) }) { Text("Grant permissions") }
            }
        } else {
            content(true)
        }
    }

    private fun requiredPermissions(screen: Screen): Array<String> {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list += Manifest.permission.BLUETOOTH_CONNECT
            list += Manifest.permission.BLUETOOTH_SCAN
        } else {
            list += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        if (screen == Screen.Host) {
            list += Manifest.permission.READ_SMS
            list += Manifest.permission.RECEIVE_SMS
            list += Manifest.permission.READ_CONTACTS
        }
        return list.toTypedArray()
    }

    private fun has(perm: String) =
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    private fun bluetoothAdapter(): BluetoothAdapter? {
        val mgr = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return mgr?.adapter
    }
}
