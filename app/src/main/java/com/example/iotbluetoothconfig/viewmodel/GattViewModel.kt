package com.example.iotbluetoothconfig.viewmodel

import android.Manifest
import android.app.Application
import android.bluetooth.*
import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class GattViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Context = app.applicationContext
    private var bluetoothGatt: BluetoothGatt? = null

    /* ===============================
     *  UUID
     * =============================== */

    private val CONFIG_SERVICE_UUID =
        UUID.fromString("e54b0001-67f5-479e-8711-b3b99198ce6c")

    private val DEV_EUI_CHAR_UUID =
        UUID.fromString("e54b0002-67f5-479e-8711-b3b99198ce6c")

    private val APP_EUI_CHAR_UUID =
        UUID.fromString("e54b0003-67f5-479e-8711-b3b99198ce6c")

    private val APP_KEY_CHAR_UUID =
        UUID.fromString("e54b0004-67f5-479e-8711-b3b99198ce6c")

    private val INTERVAL_CHAR_UUID =
        UUID.fromString("e54b0005-67f5-479e-8711-b3b99198ce6c")

    private val CLASS_CHAR_UUID =
        UUID.fromString("e54b0006-67f5-479e-8711-b3b99198ce6c")

    private val RESTART_CHAR_UUID =
        UUID.fromString("e54b00FF-67f5-479e-8711-b3b99198ce6c") // ⚠️ sesuaikan

    /* ===============================
     *  State
     * =============================== */

    data class ConfigValues(
        val devEui: String = "-",
        val appEui: String = "-",
        val appKey: String = "-",
        val interval: String = "-",
        val deviceClass: String = "-"
    )

    private val _configValues = MutableStateFlow(ConfigValues())
    val configValues: StateFlow<ConfigValues> = _configValues

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    /* ===============================
     *  GATT Callback
     * =============================== */

    private val gattCallback = object : BluetoothGattCallback() {

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                appendLog("GATT connected, discovering services…")
                _connected.value = true
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                appendLog("GATT disconnected (status=$status)")
                _connected.value = false
                bluetoothGatt?.close()
                bluetoothGatt = null
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                appendLog("Service discovery failed (status=$status)")
                return
            }

            val service = gatt.getService(CONFIG_SERVICE_UUID)
            if (service == null) {
                appendLog("Config service not found")
                return
            }

            appendLog("Config service found, reading values")

            readChar(service, DEV_EUI_CHAR_UUID)
            readChar(service, APP_EUI_CHAR_UUID)
            readChar(service, APP_KEY_CHAR_UUID)
            readChar(service, INTERVAL_CHAR_UUID)
            readChar(service, CLASS_CHAR_UUID)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                appendLog("Read failed: ${characteristic.uuid}")
                return
            }

            val value = characteristic.value ?: return

            _configValues.value = when (characteristic.uuid) {
                DEV_EUI_CHAR_UUID ->
                    _configValues.value.copy(devEui = value.toHex())

                APP_EUI_CHAR_UUID ->
                    _configValues.value.copy(appEui = value.toHex())

                APP_KEY_CHAR_UUID ->
                    _configValues.value.copy(appKey = value.toHex())

                INTERVAL_CHAR_UUID ->
                    _configValues.value.copy(interval = value.toHex())

                CLASS_CHAR_UUID ->
                    _configValues.value.copy(deviceClass = value.toHex())

                else -> _configValues.value
            }

            appendLog("Read ${characteristic.uuid}")
        }
    }

    /* ===============================
     *  Public API
     * =============================== */

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: BluetoothDevice) {
        appendLog("Connecting to ${device.address}")
        bluetoothGatt?.close()
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun restartDevice() {
        val service = bluetoothGatt?.getService(CONFIG_SERVICE_UUID)
        val char = service?.getCharacteristic(RESTART_CHAR_UUID)

        if (char == null) {
            appendLog("Restart characteristic not found")
            return
        }

        char.value = byteArrayOf(0x01)
        val success = bluetoothGatt?.writeCharacteristic(char) ?: false
        appendLog("Restart command sent: $success")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connected.value = false
        appendLog("GATT closed")
    }

    /* ===============================
     *  Helpers
     * =============================== */

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun readChar(service: BluetoothGattService, uuid: UUID) {
        val char = service.getCharacteristic(uuid)
        if (char != null) {
            bluetoothGatt?.readCharacteristic(char)
        } else {
            appendLog("Characteristic not found: $uuid")
        }
    }

    private fun appendLog(msg: String) {
        viewModelScope.launch {
            val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            _logs.value = _logs.value + "[$ts] $msg"
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}

/* ===============================
 *  ByteArray Helper
 * =============================== */
private fun ByteArray.toHex(): String =
    joinToString("") { "%02X".format(it) }
