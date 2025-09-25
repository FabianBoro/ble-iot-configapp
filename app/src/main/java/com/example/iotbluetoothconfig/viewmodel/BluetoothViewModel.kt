package com.example.iotbluetoothconfig.viewmodel

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
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

class BluetoothViewModel(app: Application) : AndroidViewModel(app) {

    private val context = app.applicationContext
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val bleScanner = bluetoothAdapter?.bluetoothLeScanner

    // State untuk daftar device yang discan
    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDevice>> = _devices

    // BLE Callback
    private val bleCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { addDevice(it) }
        }
    }

    // Classic Bluetooth Receiver
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
        // register receiver untuk scan Classic
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(classicReceiver, filter)
    }

    fun startScan() {
        viewModelScope.launch {
            _devices.value = emptyList()
            bluetoothAdapter?.cancelDiscovery()
            bluetoothAdapter?.startDiscovery()
            bleScanner?.startScan(bleCallback)
        }
    }

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

    override fun onCleared() {
        super.onCleared()
        stopScan()
        context.unregisterReceiver(classicReceiver)
    }
}
