package com.example.iotbluetoothconfig.ui.view

import android.bluetooth.BluetoothDevice
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.iotbluetoothconfig.viewmodel.BluetoothViewModel

@Composable
fun ScanScreen(
    navController: NavHostController,
    viewModel: BluetoothViewModel = viewModel()
) {
    // ambil state daftar device dari ViewModel
    val devices by viewModel.devices.collectAsState()

    // 🔹 otomatis stop scan saat keluar halaman
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopScan()
        }
    }

    DeviceListScreen(
        devices = devices,
        onScanClick = { viewModel.startScan() },
        onDeviceClick = { device ->
            // ✅ hentikan scan ketika user klik device
            viewModel.stopScan()

            // pairing via reflection (opsional)
            try {
                val method = device.javaClass.getMethod("createBond")
                method.invoke(device)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // arahkan ke halaman sesuai tipe device
            when (device.type) {
                BluetoothDevice.DEVICE_TYPE_CLASSIC -> {
                    navController.navigate("config/${device.address}") // SPP
                }
                BluetoothDevice.DEVICE_TYPE_LE -> {
                    navController.navigate("gatt/${device.address}") // GATT
                }
                BluetoothDevice.DEVICE_TYPE_DUAL -> {
                    // default ke SPP (atau tampilkan dialog kalau mau user pilih)
                    navController.navigate("config/${device.address}")
                }
                else -> {
                    navController.navigate("config/${device.address}")
                }
            }
        }
    )
}
