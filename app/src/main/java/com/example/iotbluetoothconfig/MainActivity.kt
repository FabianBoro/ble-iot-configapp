package com.example.iotbluetoothconfig

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat

class MainActivity : ComponentActivity() {
    private val sppManager = BluetoothSPPManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Runtime permission untuk Android 12+
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            // bisa dicek kalau perlu
        }
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        )

        setContent {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return@setContent
            }
            AppUI(sppManager)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
@Composable
fun AppUI(sppManager: BluetoothSPPManager) {
    var log by remember { mutableStateOf("Belum ada log") }
    var inputText by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(false) }
    var connectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }

    val bondedDevices = sppManager.getBondedDevices()?.toList() ?: emptyList()

    Scaffold(
        topBar = { TopAppBar(title = { Text("SPP ESP32") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Status koneksi
            Text(
                text = if (connected && connectedDevice != null) {
                    "Terhubung ke: ${connectedDevice?.name} (${connectedDevice?.address})"
                } else {
                    "Belum terhubung ke perangkat"
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(8.dp)
            )

            // List perangkat (scrollable + tinggi terbatas)
            Text("Pilih device yang sudah dipair:", modifier = Modifier.padding(8.dp))
            Box(
                modifier = Modifier
                    .weight(1f) // sisakan ruang untuk bagian bawah
                    .padding(horizontal = 8.dp)
            ) {
                LazyColumn {
                    items(bondedDevices.size) { index ->
                        val device = bondedDevices[index]
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    connectedDevice = device
                                    sppManager.connect(device) { message ->
                                        log = "Dari ESP32: $message"
                                    }
                                    connected = true
                                },
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Text(
                                text = "${device.name ?: "Unknown"} (${device.address})",
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            // Bagian input + kirim data
            if (connected) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        label = { Text("Pesan ke ESP32") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            sppManager.send(inputText)
                            log = "Kirim: $inputText"
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Kirim")
                    }
                }
            }

            // Log area
            Text("Log:", modifier = Modifier.padding(8.dp))
            Text(
                log,
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
            )
        }
    }
}
