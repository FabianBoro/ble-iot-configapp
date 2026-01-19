package com.example.iotbluetoothconfig.ui.view

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.iotbluetoothconfig.viewmodel.GattViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
fun GattScreen(
    deviceAddress: String,
    gattViewModel: GattViewModel = viewModel()
) {
    val config by gattViewModel.configValues.collectAsState()
    val connected by gattViewModel.connected.collectAsState()

    val device: BluetoothDevice? = remember {
        BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(deviceAddress)
    }

    // auto connect
    LaunchedEffect(deviceAddress) {
        device?.let { gattViewModel.connect(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (connected) "RAK4631 LoRaWAN Info"
                        else "GATT not connected"
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            /* ===============================
             *  LoRaWAN Info Card
             * =============================== */
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    InfoRow("DEV EUI", config.devEui)
                    Divider()
                    InfoRow("APP EUI", config.appEui)
                    Divider()
                    InfoRow("APP KEY", config.appKey)
                    Divider()
                    InfoRow("Interval", config.interval)
                    Divider()
                    InfoRow("Class", config.deviceClass)
                }
            }

            /* ===============================
             *  Restart Button
             * =============================== */
            Button(
                onClick = { gattViewModel.restartDevice() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Restart Device", color = Color.White)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value.ifBlank { "-" }, style = MaterialTheme.typography.bodyMedium)
    }
}
