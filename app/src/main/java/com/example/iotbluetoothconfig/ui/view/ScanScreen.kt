package com.example.iotbluetoothconfig.ui.view

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.iotbluetoothconfig.viewmodel.BluetoothViewModel

// Nama target tetap ada (opsional)
private const val TARGET_BLE_NAME = "RAK4631"

@Composable
fun ScanScreen(navController: NavHostController, viewModel: BluetoothViewModel = viewModel()) {
    val context = LocalContext.current
    val devices by viewModel.devices.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val scanGranted = perms[Manifest.permission.BLUETOOTH_SCAN] == true
        val connectGranted = perms[Manifest.permission.BLUETOOTH_CONNECT] == true
        if (scanGranted) {
            viewModel.startScan()
        } else {
            viewModel.appendLog("⚠️ Izin BLUETOOTH_SCAN ditolak")
        }
        if (!connectGranted) {
            viewModel.appendLog("⚠️ Izin BLUETOOTH_CONNECT belum diberikan (beberapa operasi GATT/SPP akan dibatasi)")
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(12.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            val reachedLimit = devices.size >= BluetoothViewModel.MAX_SCAN_DEVICES

            Button(
                onClick = {
                    val hasScan = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    val hasConnect = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

                    if (hasScan) {
                        if (!isScanning && !reachedLimit) {
                            viewModel.startScan()
                        } else if (reachedLimit) {
                            viewModel.appendLog("⚠️ Tidak bisa start: telah mencapai limit ${BluetoothViewModel.MAX_SCAN_DEVICES} perangkat")
                        } else {
                            viewModel.appendLog("ℹ️ Sudah dalam keadaan scanning")
                        }
                    } else {
                        permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
                    }
                },
                enabled = !reachedLimit // disable button jika cap tercapai
            ) {
                Text(if (isScanning) "Scanning..." else "Start Scan")
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(onClick = { viewModel.stopScan() }) {
                Text("Stop Scan")
            }

            Spacer(modifier = Modifier.width(8.dp))

            // tampilkan counter perangkat
            Text(text = "(${devices.size}/${BluetoothViewModel.MAX_SCAN_DEVICES})", modifier = Modifier.align(Alignment.CenterVertically))

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = {
                viewModel.clearLogs()
            }) {
                Icon(Icons.Default.Refresh, contentDescription = "Clear logs")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(text = "Perangkat Ditemukan:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (devices.isEmpty()) {
            Text("Belum ada perangkat.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(devices) { device ->
                    DeviceRow(device = device, onClick = {
                        handleDeviceClick(device, navController, viewModel)
                    })
                    Divider()
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(text = "Logs:", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(6.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            items(logs) { log ->
                Text(text = log, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun handleDeviceClick(device: BluetoothDevice, navController: NavHostController, viewModel: BluetoothViewModel) {
    val name = device.name ?: ""
    if (name.contains(TARGET_BLE_NAME, ignoreCase = true)) {
        viewModel.appendLog("ℹ️ Device name matches $TARGET_BLE_NAME — menggunakan GATT untuk ${device.address}")
        navController.navigate("gatt/${device.address}")
        return
    }

    when (device.type) {
        BluetoothDevice.DEVICE_TYPE_CLASSIC -> {
            viewModel.appendLog("ℹ️ Device jenis CLASSIC — menggunakan SPP untuk ${device.address}")
            navController.navigate("config/${device.address}")
        }
        BluetoothDevice.DEVICE_TYPE_LE -> {
            viewModel.appendLog("ℹ️ Device jenis BLE — menggunakan GATT untuk ${device.address}")
            navController.navigate("gatt/${device.address}")
        }
        BluetoothDevice.DEVICE_TYPE_DUAL -> {
            viewModel.appendLog("ℹ️ Device DUAL — default menggunakan SPP untuk ${device.address} (override: ubah kebijakan)")
            navController.navigate("config/${device.address}")
        }
        else -> {
            viewModel.appendLog("⚠️ Tipe device tidak dikenal — fallback ke SPP untuk ${device.address}")
            navController.navigate("config/${device.address}")
        }
    }
}

@Composable
fun DeviceRow(device: BluetoothDevice, onClick: () -> Unit) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(6.dp)
        .clickable { onClick() }) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(text = device.name ?: "(Unknown)", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = device.address ?: "", style = MaterialTheme.typography.bodySmall)
        }
    }
}
