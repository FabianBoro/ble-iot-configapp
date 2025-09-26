package com.example.iotbluetoothconfig.viewmodel

import android.Manifest
import android.app.Application
import android.bluetooth.*
import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*

class GattViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Context = app.applicationContext
    private var bluetoothGatt: BluetoothGatt? = null

    // alamat device yang sedang terhubung (nullable)
    private val _connectedDeviceAddress = MutableStateFlow<String?>(null)
    val connectedDeviceAddress: StateFlow<String?> = _connectedDeviceAddress

    // State log untuk monitor
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    // State service/characteristic hasil discovery
    private val _services = MutableStateFlow<List<BluetoothGattService>>(emptyList())
    val services: StateFlow<List<BluetoothGattService>> = _services

    // client config descriptor UUID (CCC)
    private val CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                appendLog("onConnectionStateChange error status=$status")
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                appendLog("Terhubung ke ${gatt?.device?.address}, discover service...")
                _connectedDeviceAddress.value = gatt?.device?.address
                // request MTU (opsional)
                gatt?.requestMtu(517)
                gatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                appendLog("Terputus dari perangkat GATT (status=$status)")
                _connectedDeviceAddress.value = null
                _services.value = emptyList()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                appendLog("Services ditemukan: ${gatt.services.size}")
                _services.value = gatt.services

                // auto: baca Device Name & Manufacturer (jika ada)
                readCharacteristicIfExists(
                    gatt,
                    UUID.fromString(SERVICE_DEVICE_INFORMATION),
                    UUID.fromString(CHAR_DEVICE_NAME)
                )
                readCharacteristicIfExists(
                    gatt,
                    UUID.fromString(SERVICE_DEVICE_INFORMATION),
                    UUID.fromString(CHAR_MANUFACTURER_NAME)
                )

                // auto: aktifkan notify untuk heart rate & battery kalau ada
                enableNotificationsIfExists(
                    gatt,
                    UUID.fromString(SERVICE_HEART_RATE),
                    UUID.fromString(CHAR_HEART_RATE_MEASUREMENT)
                )
                enableNotificationsIfExists(
                    gatt,
                    UUID.fromString(SERVICE_BATTERY),
                    UUID.fromString(CHAR_BATTERY_LEVEL)
                )

                // start periodic battery read (loop yang berhenti saat disconnect)
                startPeriodicBatteryRead()
            } else {
                appendLog("Service discovery gagal, status=$status")
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
                val value = characteristic.value?.decodeToString()
                appendLog("Read ${characteristic.uuid}: $value")
            } else {
                appendLog("Read gagal status=$status")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            val value = characteristic?.value?.decodeToString()
            appendLog("Notify ${characteristic?.uuid}: $value")
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            super.onDescriptorWrite(gatt, descriptor, status)
            appendLog("onDescriptorWrite ${descriptor?.uuid} status=$status")
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            appendLog("onCharacteristicWrite ${characteristic?.uuid} status=$status")
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            appendLog("MTU berubah: $mtu (status=$status)")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: BluetoothDevice) {
        bluetoothGatt?.close()
        appendLog("Menghubungkan ke ${device.address}...")
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
        _connectedDeviceAddress.value = device.address
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        appendLog("Koneksi GATT ditutup")
        _connectedDeviceAddress.value = null
    }

    // Public API: baca characteristic tertentu
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun readCharacteristic(serviceUuid: UUID, charUuid: UUID) {
        val service = bluetoothGatt?.getService(serviceUuid)
        val characteristic = service?.getCharacteristic(charUuid)
        if (characteristic != null) {
            val ok = bluetoothGatt?.readCharacteristic(characteristic)
            appendLog("Request read $charUuid (ok=$ok)")
        } else {
            appendLog("Characteristic $charUuid tidak ditemukan")
        }
    }

    // Public API: aktifkan notification (set + write CCC descriptor)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun enableNotifications(serviceUuid: UUID, charUuid: UUID) {
        val service = bluetoothGatt?.getService(serviceUuid)
        val characteristic = service?.getCharacteristic(charUuid)
        if (characteristic != null) {
            val setOk = bluetoothGatt?.setCharacteristicNotification(characteristic, true) ?: false
            appendLog("setCharacteristicNotification $charUuid => $setOk")
            val ccc = characteristic.getDescriptor(CCC_DESCRIPTOR_UUID)
            if (ccc != null) {
                ccc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                val writeOk = bluetoothGatt?.writeDescriptor(ccc)
                appendLog("writeDescriptor CCC for $charUuid (ok=$writeOk)")
            } else {
                appendLog("Descriptor CCC tidak ditemukan untuk $charUuid")
            }
        } else {
            appendLog("Characteristic $charUuid tidak ditemukan")
        }
    }

    // --- helper internal dipakai otomatis pada discovery ---
    private fun readCharacteristicIfExists(gatt: BluetoothGatt, serviceUuid: UUID, charUuid: UUID) {
        val service = gatt.getService(serviceUuid) ?: return
        val char = service.getCharacteristic(charUuid) ?: return
        gatt.readCharacteristic(char)
    }

    private fun enableNotificationsIfExists(gatt: BluetoothGatt, serviceUuid: UUID, charUuid: UUID) {
        val service = gatt.getService(serviceUuid) ?: return
        val char = service.getCharacteristic(charUuid) ?: return
        gatt.setCharacteristicNotification(char, true)
        val ccc = char.getDescriptor(CCC_DESCRIPTOR_UUID)
        if (ccc != null) {
            ccc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(ccc)
        }
    }

    // loop baca battery setiap 20 detik, berhenti kalau bluetoothGatt == null
    private fun startPeriodicBatteryRead() {
        viewModelScope.launch {
            while (bluetoothGatt != null) {
                try {
                    delay(20_000)
                    readCharacteristicIfExists(
                        bluetoothGatt ?: return@launch,
                        UUID.fromString(SERVICE_BATTERY),
                        UUID.fromString(CHAR_BATTERY_LEVEL)
                    )
                } catch (t: Throwable) {
                    appendLog("Periodic read error: ${t.message}")
                }
            }
        }
    }

    private fun appendLog(msg: String) {
        viewModelScope.launch {
            val timestamp =
                java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            _logs.value = _logs.value + "[$timestamp] $msg"
        }
    }

    // UUID standar
    companion object {
        const val SERVICE_DEVICE_INFORMATION = "0000180a-0000-1000-8000-00805f9b34fb"
        const val CHAR_DEVICE_NAME = "00002a00-0000-1000-8000-00805f9b34fb"
        const val CHAR_MANUFACTURER_NAME = "00002a29-0000-1000-8000-00805f9b34fb"

        const val SERVICE_BATTERY = "0000180f-0000-1000-8000-00805f9b34fb"
        const val CHAR_BATTERY_LEVEL = "00002a19-0000-1000-8000-00805f9b34fb"

        const val SERVICE_HEART_RATE = "0000180d-0000-1000-8000-00805f9b34fb"
        const val CHAR_HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb"
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}
