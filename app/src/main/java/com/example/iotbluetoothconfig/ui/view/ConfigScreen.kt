package com.example.iotbluetoothconfig.ui.view

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.iotbluetoothconfig.data.BluetoothSPPManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    deviceAddress: String?,
    sppManager: BluetoothSPPManager = BluetoothSPPManager() // inject manager
) {
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var ipAddress by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var log by remember { mutableStateOf("Belum ada aksi") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Konfigurasi Perangkat") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF96A78D),
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Device: ${deviceAddress ?: "Unknown"}")

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = ssid,
                onValueChange = { ssid = it },
                label = { Text("SSID") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = ipAddress,
                onValueChange = { ipAddress = it },
                label = { Text("IP Address") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text("Port") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val configString = "$ssid,$password,$ipAddress,$port"

                    if (deviceAddress != null) {
                        // Cari device berdasarkan address
                        val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                        val device: BluetoothDevice? = adapter?.getRemoteDevice(deviceAddress)

                        if (device != null) {
                            // Untuk ESP32 (SPP)
                            sppManager.connect(device) { response ->
                                log = "Respon: $response"
                            }
                            sppManager.send(configString)
                            log = "Kirim ke $deviceAddress: $configString"

                            // TODO: Untuk RAK4631 BLE → pakai GATT + writeCharacteristic
                            // (nanti kita buat BluetoothGattManager terpisah)
                        } else {
                            log = "Device tidak ditemukan!"
                        }
                    } else {
                        log = "Alamat device tidak valid!"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Kirim")
            }

            Spacer(Modifier.height(16.dp))

            Text("Log: $log", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
