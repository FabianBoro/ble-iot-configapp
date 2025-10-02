package com.example.iotbluetoothconfig.viewmodel

import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BluetoothViewModel(app: Application) : AndroidViewModel(app) {


    private val context = app.applicationContext
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val bleScanner = bluetoothAdapter?.bluetoothLeScanner

    // ✅ State daftar device hasil scan
    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDevice>> = _devices

    // ✅ State log monitor (Serial Monitor)
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    // ---------- BLE Scan Callback ----------
    private val bleCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { addDevice(it) }
        }
    }

    // ---------- Classic Bluetooth Receiver ----------
    private val classicReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (BluetoothDevice.ACTION_FOUND == intent?.action) {
                val device: BluetoothDevice? =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                device?.let { addDevice(it) }
            }
        }
    }

    init {
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(classicReceiver, filter)
    }

    // ---------- Scan ----------
    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        viewModelScope.launch  {
            _devices.value = emptyList()
            bluetoothAdapter?.cancelDiscovery()
            bluetoothAdapter?.startDiscovery()
            bleScanner?.startScan(bleCallback)
        }
    }
    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        bluetoothAdapter?.cancelDiscovery()
        bleScanner?.stopScan(bleCallback)
    }

    private fun addDevice(device: BluetoothDevice) {
        viewModelScope.launch {
            val current = _devices.value
            if (current.none { it.address == device.address }) {
                _devices.value = current + device
            }
        }
    }

    // ---------- Logs ----------
    fun appendLog(message: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        viewModelScope.launch {
            val updated = _logs.value.toMutableList()
            updated.add("[$ts] $message")
            _logs.value = updated
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    override fun onCleared() {
        super.onCleared()
        stopScan()
        context.unregisterReceiver(classicReceiver)
    }
}
