package com.example.iotbluetoothconfig

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.iotbluetoothconfig.ui.theme.IoTBluetoothConfigTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bluetoothManager = BluetoothSPPManager(this)

        setContent {
            IoTBluetoothConfigTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "deviceList"
                    ) {
                        composable("deviceList") {
                            DeviceListScreen(
                                bluetoothManager = bluetoothManager,
                                onDeviceSelected = { deviceAddress ->
                                    navController.navigate("config/$deviceAddress")
                                }
                            )
                        }
                        composable(
                            "config/{deviceAddress}",
                            arguments = listOf(navArgument("deviceAddress") {
                                type = NavType.StringType
                            })
                        ) { backStackEntry ->
                            val deviceAddress =
                                backStackEntry.arguments?.getString("deviceAddress") ?: ""
                            ConfigScreen(
                                bluetoothManager = bluetoothManager,
                                deviceAddress = deviceAddress,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
