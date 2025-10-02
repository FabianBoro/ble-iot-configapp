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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.LinkedList
import java.util.Queue

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

    // Config values untuk UI
    private val _configValues =
        MutableStateFlow<Map<UUID, String>>(emptyMap())
    val configValues: StateFlow<Map<UUID, String>> = _configValues

    // antrian read
    private val readQueue: Queue<BluetoothGattCharacteristic> = LinkedList()

    // client config descriptor UUID (CCC)
    private val CCC_DESCRIPTOR_UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Public API: aktifkan notification (set + write CCC descriptor)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun enableNotifications(serviceUuid: UUID, charUuid: UUID) {
        val service = bluetoothGatt?.getService(serviceUuid)
        val characteristic = service?.getCharacteristic(charUuid)
        if (characteristic != null) {
            val setOk =
                bluetoothGatt?.setCharacteristicNotification(characteristic, true) ?: false
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

    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(
            gatt: BluetoothGatt?,
            status: Int,
            newState: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                appendLog("onConnectionStateChange error status=$status")
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                appendLog("Terhubung ke ${gatt?.device?.address}, discover service...")
                _connectedDeviceAddress.value = gatt?.device?.address
                gatt?.requestMtu(517)
                gatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                appendLog("Terputus dari perangkat GATT (status=$status)")
                _connectedDeviceAddress.value = null
                _services.value = emptyList()
                _configValues.value = emptyMap()
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                appendLog("Services ditemukan: ${gatt.services.size}")
                _services.value = gatt.services

                // baca config service characteristic satu-satu via queue
                gatt.getService(CONFIG_SERVICE_UUID)?.let { svc ->
                    listOf(
                        DEV_EUI_CHAR_UUID,
                        APP_EUI_CHAR_UUID,
                        APP_KEY_CHAR_UUID,
                        INTERVAL_CHAR_UUID,
                        CLASS_CHAR_UUID
                    ).forEach { uuid ->
                        svc.getCharacteristic(uuid)?.let { enqueueRead(it, gatt) }
                    }
                }
            } else {
                appendLog("Service discovery gagal, status=$status")
            }
        }


        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
                val value = characteristic.value ?: byteArrayOf()
                val pretty = when (characteristic.uuid) {
                    DEV_EUI_CHAR_UUID, APP_EUI_CHAR_UUID, APP_KEY_CHAR_UUID ->
                        value.toHexString("-")
                    INTERVAL_CHAR_UUID ->
                        ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).int.toString()
                    CLASS_CHAR_UUID -> when (value.firstOrNull()?.toInt()) {
                        0 -> "Class A"
                        1 -> "Class B"
                        2 -> "Class C"
                        else -> "Unknown"
                    }
                    else -> value.toHexOrAscii()
                }
                updateCharValue(characteristic.uuid, pretty)
                appendLog("Read ${characteristic.uuid}: $pretty")

                handleNextRead(gatt)
            } else {
                appendLog("Read gagal status=$status")
                handleNextRead(gatt)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            val value = characteristic?.value?.toHexOrAscii()
            appendLog("Notify ${characteristic?.uuid}: $value")
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            appendLog("onDescriptorWrite ${descriptor?.uuid} status=$status")
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            appendLog(
                "onCharacteristicWrite ${characteristic?.uuid} " +
                        "status=$status value=${characteristic?.value?.toHexOrAscii()}"
            )
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            appendLog("MTU berubah: $mtu (status=$status)")
        }
    }

    // --- Public API ---
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

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun readCharacteristic(serviceUuid: UUID, charUuid: UUID) {
        val service = bluetoothGatt?.getService(serviceUuid)
        val characteristic = service?.getCharacteristic(charUuid)
        if (characteristic != null) {
            enqueueRead(characteristic, bluetoothGatt!!)
            appendLog("Request read $charUuid")
        } else {
            appendLog("Characteristic $charUuid tidak ditemukan")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun writeCharacteristic(serviceUuid: UUID, charUuid: UUID, value: ByteArray) {
        val service = bluetoothGatt?.getService(serviceUuid)
        val characteristic = service?.getCharacteristic(charUuid)
        if (characteristic != null) {
            characteristic.value = value
            bluetoothGatt?.writeCharacteristic(characteristic)
            appendLog("Request write $charUuid value=${value.toHexOrAscii()}")
        } else {
            appendLog("Characteristic $charUuid tidak ditemukan")
        }
    }

    // --- Queue Helpers ---
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun enqueueRead(char: BluetoothGattCharacteristic, gatt: BluetoothGatt) {
        readQueue.add(char)
        if (readQueue.size == 1) {
            gatt.readCharacteristic(char)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun handleNextRead(gatt: BluetoothGatt?) {
        if (readQueue.isNotEmpty()) {
            readQueue.poll()
            readQueue.peek()?.let { gatt?.readCharacteristic(it) }
        }
    }

    private fun updateCharValue(uuid: UUID, value: String) {
        _configValues.value = _configValues.value.toMutableMap().apply {
            put(uuid, value)
        }
    }

    private fun appendLog(msg: String) {
        viewModelScope.launch {
            val timestamp =
                java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            _logs.value = _logs.value + "[$timestamp] $msg"
        }
    }

    companion object {
        // Config service + characteristic UUIDs
        val CONFIG_SERVICE_UUID: UUID =
            UUID.fromString("e54b0001-67f5-479e-8711-b3b99198ce6c")
        val DEV_EUI_CHAR_UUID: UUID =
            UUID.fromString("e54b0002-67f5-479e-8711-b3b99198ce6c")
        val APP_EUI_CHAR_UUID: UUID =
            UUID.fromString("e54b0003-67f5-479e-8711-b3b99198ce6c")
        val APP_KEY_CHAR_UUID: UUID =
            UUID.fromString("e54b0004-67f5-479e-8711-b3b99198ce6c")
        val INTERVAL_CHAR_UUID: UUID =
            UUID.fromString("e54b0005-67f5-479e-8711-b3b99198ce6c")
        val CLASS_CHAR_UUID: UUID =
            UUID.fromString("e54b0006-67f5-479e-8711-b3b99198ce6c")
        val RESTART_CHAR_UUID: UUID =
            UUID.fromString("e54b0007-67f5-479e-8711-b3b99198ce6c")

        // UUID standar
        const val SERVICE_DEVICE_INFORMATION =
            "0000180a-0000-1000-8000-00805f9b34fb"
        const val CHAR_DEVICE_NAME = "00002a00-0000-1000-8000-00805f9b34fb"
        const val CHAR_MANUFACTURER_NAME = "00002a29-0000-1000-8000-00805f9b34fb"

        const val SERVICE_BATTERY = "0000180f-0000-1000-8000-00805f9b34fb"
        const val CHAR_BATTERY_LEVEL = "00002a19-0000-1000-8000-00805f9b34fb"

        const val SERVICE_HEART_RATE = "0000180d-0000-1000-8000-00805f9b34fb"
        const val CHAR_HEART_RATE_MEASUREMENT =
            "00002a37-0000-1000-8000-00805f9b34fb"
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}

// === Helpers ===
private fun ByteArray.toHexOrAscii(): String {
    return if (all { it in 0x20..0x7E }) {
        String(this)
    } else {
        joinToString(" ") { "%02X".format(it) }
    }
}

private fun ByteArray.toHexString(separator: String = ""): String {
    return joinToString(separator) { "%02X".format(it) }
}
