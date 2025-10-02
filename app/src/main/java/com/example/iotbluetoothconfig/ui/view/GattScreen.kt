package com.example.iotbluetoothconfig.ui.view

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattService
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.iotbluetoothconfig.viewmodel.GattViewModel
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

val CONFIG_SERVICE_UUID: UUID = UUID.fromString("e54b0001-67f5-479e-8711-b3b99198ce6c")

// UUID characteristic (contoh dari firmware RAK4631)
val DEV_EUI_CHAR_UUID = UUID.fromString("e54b0002-67f5-479e-8711-b3b99198ce6c")
val APP_EUI_CHAR_UUID = UUID.fromString("e54b0003-67f5-479e-8711-b3b99198ce6c")
val APP_KEY_CHAR_UUID = UUID.fromString("e54b0004-67f5-479e-8711-b3b99198ce6c")
val INTERVAL_CHAR_UUID = UUID.fromString("e54b0005-67f5-479e-8711-b3b99198ce6c")
val CLASS_CHAR_UUID = UUID.fromString("e54b0006-67f5-479e-8711-b3b99198ce6c")
val CMD_CHAR_UUID = UUID.fromString("e54b0007-67f5-479e-8711-b3b99198ce6c")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
fun GattScreen(
    deviceAddress: String,
    gattViewModel: GattViewModel = viewModel()
) {
    val services by gattViewModel.services.collectAsState()
    val connectedAddress by gattViewModel.connectedDeviceAddress.collectAsState()

    // ambil device dari adapter
    val device: BluetoothDevice? = remember {
        BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(deviceAddress)
    }

    // connect otomatis
    LaunchedEffect(deviceAddress) {
        device?.let { gattViewModel.connect(it) }
    }

    val hasConfigService = services.any { it.uuid == CONFIG_SERVICE_UUID }
    var selectedTab by remember { mutableStateOf(0) }

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
            TabRow(selectedTabIndex = selectedTab) {
                if (hasConfigService) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("RAK4631 Config") }
                    )
                }
                Tab(
                    selected = if (hasConfigService) selectedTab == 1 else selectedTab == 0,
                    onClick = { selectedTab = if (hasConfigService) 1 else 0 },
                    text = { Text("GATT Services") }
                )
            }

            when (selectedTab) {
                0 -> if (hasConfigService) {
                    Rak4631ConfigUI(gattViewModel)
                } else {
                    GattServicesUI(services, gattViewModel)
                }
                1 -> if (hasConfigService) {
                    GattServicesUI(services, gattViewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
fun Rak4631ConfigUI(gattViewModel: GattViewModel) {
    val configValues by gattViewModel.configValues.collectAsState()

    val scope = rememberCoroutineScope()

    // ambil value dari ViewModel
    var devEui by remember(configValues) {
        mutableStateOf(configValues[GattViewModel.DEV_EUI_CHAR_UUID] ?: "")
    }
    var appEui by remember(configValues) {
        mutableStateOf(configValues[GattViewModel.APP_EUI_CHAR_UUID] ?: "")
    }
    var appKey by remember(configValues) {
        mutableStateOf(configValues[GattViewModel.APP_KEY_CHAR_UUID] ?: "")
    }
    var interval by remember(configValues) {
        mutableStateOf(configValues[GattViewModel.INTERVAL_CHAR_UUID] ?: "")
    }
    var devClass by remember(configValues) {
        mutableStateOf(configValues[GattViewModel.CLASS_CHAR_UUID] ?: "Class A")
    }

    // editable state
    var editDevEui by remember { mutableStateOf(false) }
    var editAppEui by remember { mutableStateOf(false) }
    var editAppKey by remember { mutableStateOf(false) }
    var editInterval by remember { mutableStateOf(false) }
    var editClass by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        ConfigRow(
            label = "DevEUI",
            value = devEui,
            editable = editDevEui,
            onEditToggle = { editDevEui = !editDevEui },
            onValueChange = { devEui = it }
        )

        ConfigRow(
            label = "AppEUI",
            value = appEui,
            editable = editAppEui,
            onEditToggle = { editAppEui = !editAppEui },
            onValueChange = { appEui = it }
        )

        ConfigRow(
            label = "AppKey",
            value = appKey,
            editable = editAppKey,
            onEditToggle = { editAppKey = !editAppKey },
            onValueChange = { appKey = it }
        )

        ConfigRow(
            label = "Interval (ms)",
            value = interval,
            editable = editInterval,
            keyboardType = KeyboardType.Number,
            onEditToggle = { editInterval = !editInterval },
            onValueChange = { interval = it }
        )

        // Device Class Dropdown
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Device Class", style = MaterialTheme.typography.bodyLarge)
            if (editClass) {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    Button(onClick = { expanded = true }) {
                        Text(devClass)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        listOf("Class A", "Class B", "Class C").forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    devClass = option
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            } else {
                Text(devClass)
            }
            IconButton(onClick = { editClass = !editClass }) {
                Icon(Icons.Default.Edit, contentDescription = "Ubah")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Tombol Simpan
        Button(
            onClick = {
                scope.launch {
                    gattViewModel.writeCharacteristic(
                        GattViewModel.CONFIG_SERVICE_UUID,
                        GattViewModel.DEV_EUI_CHAR_UUID,
                        devEui.replace("-", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    )
                    gattViewModel.writeCharacteristic(
                        GattViewModel.CONFIG_SERVICE_UUID,
                        GattViewModel.APP_EUI_CHAR_UUID,
                        appEui.replace("-", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    )
                    gattViewModel.writeCharacteristic(
                        GattViewModel.CONFIG_SERVICE_UUID,
                        GattViewModel.APP_KEY_CHAR_UUID,
                        appKey.replace("-", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    )
                    interval.toIntOrNull()?.let {
                        val bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(it).array()
                        gattViewModel.writeCharacteristic(
                            GattViewModel.CONFIG_SERVICE_UUID,
                            GattViewModel.INTERVAL_CHAR_UUID,
                            bytes
                        )
                    }
                    val classVal = when (devClass) {
                        "Class A" -> 0x00
                        "Class B" -> 0x01
                        "Class C" -> 0x02
                        else -> 0x00
                    }
                    gattViewModel.writeCharacteristic(
                        GattViewModel.CONFIG_SERVICE_UUID,
                        GattViewModel.CLASS_CHAR_UUID,
                        byteArrayOf(classVal.toByte())
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Simpan Konfigurasi")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tombol Restart
        Button(
            onClick = {
                scope.launch {
                    gattViewModel.writeCharacteristic(
                        GattViewModel.CONFIG_SERVICE_UUID,
                        GattViewModel.RESTART_CHAR_UUID,
                        byteArrayOf(0x01)
                    )
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Restart Device", color = Color.White)
        }
    }
}

@Composable
private fun ConfigRow(
    label: String,
    value: String,
    editable: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    onEditToggle: () -> Unit,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (editable) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF59D)) // highlight kuning saat edit
                )
            } else {
                Text(value.ifBlank { "-" })
            }
        }
        IconButton(onClick = onEditToggle) {
            Icon(Icons.Default.Edit, contentDescription = "Ubah")
        }
    }
}

@Composable
@androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
fun GattServicesUI(
    services: List<BluetoothGattService>,
    gattViewModel: GattViewModel
) {
    val configValues by gattViewModel.configValues.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        services.forEach { service ->
            item {
                Text(
                    "Service: ${service.uuid}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            items(service.characteristics) { char ->
                val value = configValues[char.uuid] ?: ""

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, bottom = 8.dp)
                ) {
                    Text(
                        text = "Char: ${char.uuid}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (value.isNotBlank()) {
                        Text(
                            text = "Value: $value",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                gattViewModel.readCharacteristic(service.uuid, char.uuid)
                            },
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text("Read")
                        }
                        Button(
                            onClick = {
                                gattViewModel.enableNotifications(service.uuid, char.uuid)
                            },
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text("Notify")
                        }
                        Button(
                            onClick = {
                                // contoh dummy: tulis byte 0x01
                                gattViewModel.writeCharacteristic(
                                    service.uuid,
                                    char.uuid,
                                    byteArrayOf(0x01)
                                )
                            }
                        ) {
                            Text("Write")
                        }
                    }
                }
            }
        }
    }
}

