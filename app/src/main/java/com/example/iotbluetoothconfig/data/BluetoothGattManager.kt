package com.example.iotbluetoothconfig.data

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import java.util.*


class BluetoothGattManager(
    private val context: Context
) {@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private var bluetoothGatt: BluetoothGatt? = null

    // Listener untuk event callback ke ViewModel / UI
    var onConnectionStateChange: ((connected: Boolean) -> Unit)? = null
    var onServicesDiscovered: ((services: List<BluetoothGattService>) -> Unit)? = null
    var onCharacteristicChanged: ((uuid: UUID, value: ByteArray) -> Unit)? = null
    var onCharacteristicRead: ((uuid: UUID, value: ByteArray) -> Unit)? = null
    var onCharacteristicWrite: ((uuid: UUID, success: Boolean) -> Unit)? = null

    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("GattManager", "Connected to GATT server")
                onConnectionStateChange?.invoke(true)
                gatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("GattManager", "Disconnected from GATT server")
                onConnectionStateChange?.invoke(false)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val services = gatt?.services ?: emptyList()
                onServicesDiscovered?.invoke(services)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            characteristic?.let {
                onCharacteristicChanged?.invoke(it.uuid, it.value)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
                onCharacteristicRead?.invoke(characteristic.uuid, characteristic.value)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            val success = status == BluetoothGatt.GATT_SUCCESS
            onCharacteristicWrite?.invoke(characteristic?.uuid ?: UUID(0,0), success)
        }
    }
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: BluetoothDevice) {
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun readCharacteristic(characteristic: BluetoothGattCharacteristic) {
        bluetoothGatt?.readCharacteristic(characteristic)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        characteristic.value = value
        val ok = bluetoothGatt?.writeCharacteristic(characteristic) ?: false
        Log.d("GattManager", "Write ${characteristic.uuid} value=${value.joinToString(" ") { "%02X".format(it) }} (ok=$ok)")
    }
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun enableNotifications(characteristic: BluetoothGattCharacteristic) {
        bluetoothGatt?.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb") // Client Characteristic Config
        )
        descriptor?.let {
            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            bluetoothGatt?.writeDescriptor(it)
        }
    }
}
