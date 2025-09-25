package com.example.iotbluetoothconfig.ui.view

import android.bluetooth.BluetoothDevice
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import com.example.iotbluetoothconfig.viewmodel.BluetoothViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ScanScreen(
    navController: NavHostController,
    viewModel: BluetoothViewModel = viewModel()
) {
    // ambil state daftar device dari ViewModel
    val devices by viewModel.devices.collectAsState()

    DeviceListScreen(
        devices = devices,
        onScanClick = { viewModel.startScan() },
        onDeviceClick = { device ->
            // lakukan pairing via reflection
            try {
                val method = device.javaClass.getMethod("createBond")
                method.invoke(device)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // pindah ke halaman config sambil bawa address device
            navController.navigate("config/${device.address}")
        }
    )
}
