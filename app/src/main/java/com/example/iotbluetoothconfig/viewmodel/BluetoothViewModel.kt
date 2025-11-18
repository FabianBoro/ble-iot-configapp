package com.example.iotbluetoothconfig.viewmodel

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
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

    companion object {
        // batas maksimum device yang disimpan untuk menghindari scan tak berujung
        const val MAX_SCAN_DEVICES = 8
    }

    // State daftar device hasil scan
    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDevice>> = _devices

    // State log monitor (Serial Monitor)
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    // State apakah sedang scanning (BLE atau Classic)
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    // ---------- BLE Scan Callback ----------
    private val bleCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { addDevice(it) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { it.device?.let { d -> addDevice(d) } }
        }

        override fun onScanFailed(errorCode: Int) {
            appendLog("❌ BLE scan failed, code=$errorCode")
            viewModelScope.launch {
                _isScanning.value = false
            }
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
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED
        ) {
            appendLog("⚠️ BLUETOOTH_SCAN belum diberikan - scan dibatalkan")
            return
        }

        if (_isScanning.value) {
            appendLog("⚠️ Start scan dibatalkan — sudah dalam keadaan scanning")
            return
        }

        viewModelScope.launch {
            try {
                // reset device list saat memulai scan baru
                _devices.value = emptyList()

                // Cancel classic discovery jika sedang berjalan
                try {
                    if (bluetoothAdapter?.isDiscovering == true) {
                        bluetoothAdapter.cancelDiscovery()
                        appendLog("ℹ️ cancelDiscovery dipanggil sebelum startScan")
                    }
                } catch (se: SecurityException) {
                    appendLog("⚠️ cancelDiscovery gagal: ${se.message}")
                } catch (e: Exception) {
                    appendLog("⚠️ cancelDiscovery error: ${e.message}")
                }

                // Mulai discovery klasik (optional)
                try {
                    val startedClassic = bluetoothAdapter?.startDiscovery() ?: false
                    if (startedClassic) {
                        appendLog("📡 Classic discovery dimulai")
                    } else {
                        appendLog("ℹ️ Classic discovery tidak dimulai (mungkin tidak didukung atau sudah berjalan)")
                    }
                } catch (se: SecurityException) {
                    appendLog("⚠️ startDiscovery gagal: ${se.message}")
                } catch (e: Exception) {
                    appendLog("⚠️ startDiscovery error: ${e.message}")
                }

                // Mulai BLE scan
                try {
                    bleScanner?.startScan(bleCallback)
                    _isScanning.value = true
                    appendLog("📡 BLE scan dimulai")
                } catch (se: SecurityException) {
                    appendLog("⚠️ BLE startScan gagal: ${se.message}")
                    _isScanning.value = false
                } catch (e: Exception) {
                    appendLog("❌ BLE startScan error: ${e.message}")
                    _isScanning.value = false
                }
            } catch (e: Exception) {
                appendLog("❌ startScan error: ${e.message}")
                _isScanning.value = false
            }
        }
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        viewModelScope.launch {
            if (!_isScanning.value && (bluetoothAdapter?.isDiscovering != true)) {
                appendLog("ℹ️ stopScan dipanggil tetapi tidak ada scan aktif")
            }

            try {
                try {
                    if (bluetoothAdapter?.isDiscovering == true) {
                        bluetoothAdapter.cancelDiscovery()
                        appendLog("⏹️ Classic discovery dihentikan")
                    } else {
                        appendLog("ℹ️ Tidak ada classic discovery aktif")
                    }
                } catch (se: SecurityException) {
                    appendLog("⚠️ cancelDiscovery gagal di stopScan: ${se.message}")
                } catch (e: Exception) {
                    appendLog("⚠️ cancelDiscovery error: ${e.message}")
                }

                try {
                    bleScanner?.stopScan(bleCallback)
                    appendLog("⏹️ BLE scan dihentikan")
                } catch (se: SecurityException) {
                    appendLog("⚠️ BLE stopScan gagal: ${se.message}")
                } catch (e: Exception) {
                    appendLog("⚠️ BLE stopScan error: ${e.message}")
                }

            } catch (e: Exception) {
                appendLog("❌ stopScan error: ${e.message}")
            } finally {
                _isScanning.value = false
            }
        }
    }

    /**
     * Tambah device ke list bila belum ada dan belum mencapai MAX_SCAN_DEVICES.
     * Jika mencapai batas, stopScan() dipanggil otomatis untuk menghemat resource.
     */
    private fun addDevice(device: BluetoothDevice) {
        viewModelScope.launch {
            val current = _devices.value

            // jangan tambahkan duplikat
            if (current.any { it.address == device.address }) return@launch

            // jika sudah mencapai batas, ignore dan hentikan scan
            if (current.size >= MAX_SCAN_DEVICES) {
                appendLog("⚠️ Limit perangkat ter-scan (${MAX_SCAN_DEVICES}) tercapai — mengabaikan ${device.address}")
                // stop scan agar tidak terus menerima event
                try {
                    stopScan()
                } catch (e: Exception) {
                    appendLog("⚠️ Gagal stopScan setelah limit tercapai: ${e.message}")
                }
                return@launch
            }

            // tambahkan device baru
            _devices.value = current + device
            appendLog("➕ Device ditemukan: ${device.name ?: "(unknown)"} ${device.address} (total=${_devices.value.size})")

            // cek lagi: bila setelah penambahan tepat mencapai limit, hentikan scan
            if (_devices.value.size >= MAX_SCAN_DEVICES) {
                appendLog("ℹ️ Mencapai batas ${MAX_SCAN_DEVICES} perangkat — melakukan stopScan otomatis")
                try {
                    stopScan()
                } catch (e: Exception) {
                    appendLog("⚠️ Gagal stopScan setelah mencapai batas: ${e.message}")
                }
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

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    override fun onCleared() {
        super.onCleared()
        try {
            stopScan()
        } catch (e: Exception) {
            appendLog("⚠️ error saat onCleared stopScan: ${e.message}")
        }

        try {
            context.unregisterReceiver(classicReceiver)
        } catch (e: Exception) {
            // ignore
        }
    }
}
