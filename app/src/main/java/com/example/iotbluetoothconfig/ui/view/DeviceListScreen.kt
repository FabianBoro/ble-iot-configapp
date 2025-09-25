package com.example.iotbluetoothconfig.ui.view

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    devices: List<BluetoothDevice>,
    onScanClick: () -> Unit,
    onDeviceClick: (BluetoothDevice) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pilih Perangkat Bluetooth") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF96A78D),
                    titleContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = onScanClick) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Scan",
                            tint = Color.White
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (devices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Belum ada perangkat ditemukan")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(devices) { device ->
                    DeviceItem(device, onClick = { onDeviceClick(device) })
                }
            }
        }
    }
}

@Composable
fun DeviceItem(device: BluetoothDevice, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Text(
            text = device.name ?: "Unknown Device",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = device.address,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
