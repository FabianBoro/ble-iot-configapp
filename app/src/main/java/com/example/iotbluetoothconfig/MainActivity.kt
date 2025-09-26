package com.example.iotbluetoothconfig

import android.bluetooth.BluetoothAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.iotbluetoothconfig.data.BluetoothSPPManager
import com.example.iotbluetoothconfig.ui.theme.IoTBluetoothConfigTheme
import com.example.iotbluetoothconfig.ui.view.*
import com.example.iotbluetoothconfig.viewmodel.BluetoothViewModel
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            IoTBluetoothConfigTheme {
                val navController = rememberNavController()
                MainScreen(navController)
            }
        }
    }
}

@RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
@Composable
fun MainScreen(navController: NavHostController) {
    val items = listOf(
        BottomNavItem("Scan", "scan"),
        BottomNavItem("Config", "config/{address}"),
        BottomNavItem("GATT", "gatt"),
        BottomNavItem("Door lock", "doorlock") // ✅ ubah route ke "doorlock"
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val currentRoute = currentRoute(navController)
                items.forEach { item ->
                    NavigationBarItem(
                        label = { Text(item.label) },
                        selected = currentRoute?.startsWith(item.route.removeSuffix("/{address}")) == true,
                        onClick = {
                            if (item.route.contains("{address}")) {
                                navController.navigate("scan")
                            } else {
                                navController.navigate(item.route)
                            }
                        },
                        icon = {}
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "scan",
            modifier = androidx.compose.ui.Modifier.padding(innerPadding)
        ) {
            // 🔍 Halaman Scan
            composable("scan") {
                ScanScreen(navController = navController)
            }

            // 📡 Halaman GATT dari bottomnav (default)
            composable("gatt") {
                Text("Belum ada perangkat GATT dipilih")
            }

            // 📡 Halaman GATT detail
            composable("gatt/{address}") { backStackEntry ->
                val address = backStackEntry.arguments?.getString("address") ?: ""
                GattScreen(deviceAddress = address)
            }

            // 🔌 Halaman Config (SPP)
            composable("config/{address}") { backStackEntry ->
                val address = backStackEntry.arguments?.getString("address") ?: ""
                val bluetoothViewModel: BluetoothViewModel = viewModel()
                val sppManager = remember { BluetoothSPPManager() }
                val device = BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(address)

                LaunchedEffect(device) {
                    device?.let {
                        sppManager.connect(it) { message ->
                            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                            bluetoothViewModel.appendLog("[$timestamp] $message")
                        }
                    }
                }

                ConfigPagerScreen(
                    deviceAddress = address,
                    bluetoothViewModel = bluetoothViewModel,
                    onSendConfig = { message ->
                        sppManager.send(message)
                        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                        bluetoothViewModel.appendLog("[$timestamp] Kirim: $message")
                    }
                )
            }

            // 🚪 ✅ Halaman Door Lock
            composable("doorlock") {
                DoorLockScreen()
            }

            // 📄 Halaman kosong (dummy)
            composable("empty") { EmptyScreen() }
        }
    }
}

data class BottomNavItem(val label: String, val route: String)

@Composable
fun currentRoute(navController: NavHostController): String? {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    return navBackStackEntry?.destination?.route
}
