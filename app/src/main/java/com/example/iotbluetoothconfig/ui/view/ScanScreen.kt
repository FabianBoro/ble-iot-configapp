package com.example.iotbluetoothconfig.ui.view

import android.bluetooth.BluetoothDevice
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.iotbluetoothconfig.viewmodel.BluetoothViewModel

@Composable
fun ScanScreen(
    navController: NavHostController,
    viewModel: BluetoothViewModel = viewModel()
) {
    // Ambil state daftar device dari ViewModel
    val devices by viewModel.devices.collectAsState()

    DeviceListScreen(
        devices = devices,
        onScanClick = { viewModel.startScan() },
        onDeviceClick = { device ->
            // Pairing (jika belum bonded)
            try {
                val method = device.javaClass.getMethod("createBond")
                method.invoke(device)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Navigasi ke halaman config sambil bawa alamat MAC
            navController.navigate("config/${device.address}")
        }
    )
}
