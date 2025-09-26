package com.example.iotbluetoothconfig.ui.view

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.iotbluetoothconfig.viewmodel.GattViewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
fun GattScreen(
    deviceAddress: String,
    gattViewModel: GattViewModel = viewModel()
) {
    val logs by gattViewModel.logs.collectAsState()
    val services by gattViewModel.services.collectAsState()
    val connectedAddress by gattViewModel.connectedDeviceAddress.collectAsState()

    // ambil device dari adapter
    val device: BluetoothDevice? = remember {
        BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(deviceAddress)
    }

    // connect otomatis saat pertama kali masuk
    LaunchedEffect(deviceAddress) {
        device?.let { gattViewModel.connect(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (connectedAddress != null) {
                        Text("GATT: $connectedAddress")
                    } else {
                        Text("GATT: tidak terhubung")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Services & characteristics
            Text(
                "Services (${services.size}):",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(8.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                services.forEach { service ->
                    item {
                        Text(
                            "Service: ${friendlyName(service.uuid)} (${service.uuid})",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    service.characteristics.forEach { char ->
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Char: ${friendlyName(char.uuid)} (${char.uuid})",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Row {
                                    Button(
                                        onClick =  {
                                            gattViewModel.readCharacteristic(
                                                service.uuid,
                                                char.uuid
                                            )
                                        },
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) { Text("Read") }
                                    Button(
                                        onClick = {
                                            gattViewModel.enableNotifications(
                                                service.uuid,
                                                char.uuid
                                            )
                                        }
                                    ) { Text("Notify") }
                                }
                            }
                        }
                    }
                }
            }

            Divider()

            // Log output
            Text(
                "Logs:",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(8.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                logs.forEach { log ->
                    Text(log, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * Fungsi untuk mengubah UUID standar menjadi nama lebih ramah
 */
fun friendlyName(uuid: UUID): String = when (uuid.toString().lowercase()) {
    "0000180a-0000-1000-8000-00805f9b34fb" -> "Device Information Service"
    "0000180f-0000-1000-8000-00805f9b34fb" -> "Battery Service"
    "0000180d-0000-1000-8000-00805f9b34fb" -> "Heart Rate Service"

    "00002a00-0000-1000-8000-00805f9b34fb" -> "Device Name"
    "00002a29-0000-1000-8000-00805f9b34fb" -> "Manufacturer Name"
    "00002a19-0000-1000-8000-00805f9b34fb" -> "Battery Level"
    "00002a37-0000-1000-8000-00805f9b34fb" -> "Heart Rate Measurement"

    else -> "Unknown"
}
