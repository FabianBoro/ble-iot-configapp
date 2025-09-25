package com.example.iotbluetoothconfig

import android.bluetooth.BluetoothAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.iotbluetoothconfig.data.BluetoothSPPManager
import com.example.iotbluetoothconfig.ui.theme.IoTBluetoothConfigTheme
import com.example.iotbluetoothconfig.ui.view.ConfigPagerScreen
import com.example.iotbluetoothconfig.ui.view.ConfigScreen
import com.example.iotbluetoothconfig.ui.view.EmptyScreen
import com.example.iotbluetoothconfig.ui.view.ScanScreen
import com.example.iotbluetoothconfig.viewmodel.BluetoothViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

@Composable
fun MainScreen(navController: NavHostController) {
    val items = listOf(
        BottomNavItem("Scan", "scan"),
        BottomNavItem("Config", "config/{address}"),
        BottomNavItem("Other", "empty"),
        BottomNavItem("Door lock", "empty")
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
                            // kalau route ada parameter (config/{address}), arahkan ke scan dulu
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
            // Halaman Scan
            composable("scan") {
                ScanScreen(navController = navController)
            }

            // Halaman Config, dengan argumen address
            composable("config/{address}") { backStackEntry ->
                val address = backStackEntry.arguments?.getString("address") ?: ""
                val bluetoothViewModel: BluetoothViewModel = viewModel()
                val sppManager = remember { BluetoothSPPManager() }
                // Ambil device dari BluetoothAdapter
                val device = BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(address)

                // Jalankan koneksi saat masuk halaman Config
                LaunchedEffect(device) {
                    device?.let {
                        sppManager.connect(it) { message ->
                            // ✅ Tambahkan timestamp di Android side
                            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                            bluetoothViewModel.appendLog("[$timestamp] $message")
                        }
                    }
                }

                ConfigPagerScreen(
                    deviceAddress = address,
                    bluetoothViewModel = bluetoothViewModel,
                    onSendConfig = { message ->
                        sppManager.send(message) // ✅ kirim ke ESP32
                        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                        bluetoothViewModel.appendLog("[$timestamp] Kirim: $message")
                    }
                )
            }


            // Halaman kosong
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
