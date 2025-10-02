package com.example.iotbluetoothconfig.ui.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.iotbluetoothconfig.viewmodel.BluetoothViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
@androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
fun ConfigPagerScreen(
    deviceAddress: String,
    bluetoothViewModel: BluetoothViewModel,
    onSendConfig: (String) -> Unit
) {
    var showMonitor by remember { mutableStateOf(false) }
    val logs by bluetoothViewModel.logs.collectAsState()

    Box(Modifier.fillMaxSize()) {
        // layar utama Config
        ConfigScreen(
            deviceAddress = deviceAddress,
            viewModel = bluetoothViewModel
        )

        // tombol untuk buka drawer kanan
        FloatingActionButton(
            onClick = { showMonitor = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = Color(0xFF96A78D)
        ) {
            Text("Log", color = Color.White)
        }

        // overlay hitam ketika monitor aktif
        if (showMonitor) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showMonitor = false }
            )
        }

        // drawer kanan
        AnimatedVisibility(
            visible = showMonitor,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(300)
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(300)
            ),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            SerialMonitorContent(
                logs = logs,
                onClose = { showMonitor = false }
            )
        }
    }
}

@Composable
fun SerialMonitorContent(
    logs: List<String>,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(320.dp),
        color = Color(0xFF1E1E1E),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // header monitor
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Serial Monitor",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                TextButton(onClick = onClose) {
                    Text("Close", color = Color.Red)
                }
            }

            Divider(color = Color.Gray)

            // isi log
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp)
            ) {
                items(logs) { log ->
                    val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        .format(Date())
                    Text(
                        "[$timestamp] $log",
                        color = Color.Green,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
