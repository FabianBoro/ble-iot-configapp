package com.example.iotbluetoothconfig

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DeviceListScreen(
    bluetoothManager: BluetoothSPPManager,
    onDeviceSelected: (String) -> Unit // kirim String address, bukan BluetoothDevice
) {
    val bondedDevices by remember { mutableStateOf(bluetoothManager.getBondedDevices()) }
    var isConnecting by remember { mutableStateOf(false) }
    var connectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Pilih Perangkat Bluetooth", style = MaterialTheme.typography.titleLarge)

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn {
            items(bondedDevices.size) { index ->
                val device = bondedDevices[index]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                        .clickable {
                            isConnecting = true
                            bluetoothManager.connect(device) { success ->
                                isConnecting = false
                                if (success) {
                                    connectedDevice = device
                                    onDeviceSelected(device.address) // kirim String address
                                }
                            }
                        }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(device.name ?: "Unknown Device")
                        Text(device.address, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (isConnecting) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
            Text("Sedang mencoba connect...")
        }

        connectedDevice?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Terhubung ke: ${it.name} (${it.address})")
        }
    }
}