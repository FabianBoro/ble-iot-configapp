@file:Suppress("DEPRECATION")

package com.example.iotbluetoothconfig.ui.view

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.iotbluetoothconfig.data.BluetoothSPPManager
import com.example.iotbluetoothconfig.viewmodel.BluetoothViewModel

@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    deviceAddress: String?,
    sppManager: BluetoothSPPManager = BluetoothSPPManager(),
    viewModel: BluetoothViewModel, // 🔹 inject ViewModel
    onSendConfig: (String) -> Unit = {}
) {
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var ipAddress by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }

    val adapter = BluetoothAdapter.getDefaultAdapter()
    val device: BluetoothDevice? = remember(deviceAddress) {
        deviceAddress?.let { addr -> adapter?.getRemoteDevice(addr) }
    }

    val deviceName = device?.name ?: "Unknown"
    val deviceAddr = device?.address ?: "Unknown"

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
            // 🔹 tampilkan nama + address
            Text("Device: $deviceName ($deviceAddr)")

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
                    if (device != null) {
                        sppManager.connect(device) { response ->
                            // simpan log ke ViewModel
                            viewModel.appendLog("Respon: $response")
                        }
                        sppManager.send(configString)
                        viewModel.appendLog("📤 Kirim ke $deviceAddr: $configString")
                        onSendConfig(configString)
                        viewModel.appendLog("📤ESP32 Reboot")
                        // TODO: Untuk RAK4631 BLE → pakai GATT + writeCharacteristic
                    } else {
                        viewModel.appendLog("❌ Device tidak ditemukan!")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Kirim")
            }
        }
    }
}

