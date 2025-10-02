package com.example.iotbluetoothconfig.ui.view

import android.Manifest
import androidx.annotation.RequiresPermission
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.iotbluetoothconfig.viewmodel.GattViewModel

@Composable
@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
fun GattPagerScreen(
    deviceAddress: String,
    gattViewModel: GattViewModel = viewModel()
) {
    var showMonitor by remember { mutableStateOf(false) }
    val logs by gattViewModel.logs.collectAsState()

    Box(Modifier.fillMaxSize()) {
        // Halaman utama dengan TabRow
        GattScreen(deviceAddress = deviceAddress, gattViewModel = gattViewModel)

        // Tombol buka monitor
        FloatingActionButton(
            onClick = { showMonitor = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Text("Log", color = Color.White)
        }

        // Overlay hitam
        if (showMonitor) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showMonitor = false }
            )
        }

        // Drawer kanan
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
            GattMonitorContent(
                logs = logs,
                onClose = { showMonitor = false }
            )
        }
    }
}

@Composable
fun GattMonitorContent(
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
            // header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("GATT Monitor", style = MaterialTheme.typography.titleMedium, color = Color.White)
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
                    Text(
                        log,
                        color = Color.Green,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
