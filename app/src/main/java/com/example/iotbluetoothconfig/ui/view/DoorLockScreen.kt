package com.example.iotbluetoothconfig.ui.view

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.iotbluetoothconfig.viewmodel.DoorLockViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoorLockScreen(
    viewModel: DoorLockViewModel = viewModel()
) {
    val isConnected by viewModel.isConnected.collectAsState()
    val networkReady by viewModel.networkReady.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val publishStatus by viewModel.publishStatus.collectAsState()

    var selectedDoor by remember { mutableStateOf(1) }
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Cek network saat pertama kali masuk
    LaunchedEffect(Unit) {
        viewModel.checkNetworkReady()
    }

    // 🔔 Tampilkan Toast jika ada status publish terbaru
    LaunchedEffect(publishStatus) {
        publishStatus?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Door Lock Controller") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF96A78D),
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Status koneksi
            Text(
                text = when {
                    !networkReady -> "❌ Broker tidak terjangkau. Pastikan Wi-Fi atau VPN aktif."
                    isConnected -> "✅ Terhubung ke MQTT Broker"
                    else -> "⚠️ Belum terhubung ke MQTT Broker"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (isConnected) Color(0xFF4CAF50) else Color.Red,
                modifier = Modifier.padding(8.dp),
                textAlign = TextAlign.Center
            )

            // Tombol Connect / Disconnect
            Button(
                onClick = {
                    if (isConnected) viewModel.disconnectMqtt()
                    else viewModel.connectMqtt()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(if (isConnected) "Disconnect MQTT" else "Connect MQTT")
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Dropdown pilih pintu
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Pilih Pintu: $selectedDoor")
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    (1..3).forEach { door ->
                        DropdownMenuItem(
                            text = { Text("Pintu $door") },
                            onClick = {
                                selectedDoor = door
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Tombol buka pintu (lingkaran besar)
            Button(
                onClick = {
                    if (isConnected) {
                        viewModel.openDoor(selectedDoor)
                    }
                },
                enabled = isConnected && networkReady,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) Color(0xFF4CAF50) else Color.Gray
                ),
                modifier = Modifier
                    .size(160.dp)
                    .padding(16.dp)
            ) {
                Text(
                    text = "BUKA",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Divider(thickness = 1.dp, color = Color.Gray)

            Spacer(modifier = Modifier.height(16.dp))

            // Log aktivitas
            Text(
                "📜 Log Aktivitas:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .background(Color(0xFFF0F0F0))
                    .padding(8.dp)
            ) {
                logs.forEach { log ->
                    Text(log, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
