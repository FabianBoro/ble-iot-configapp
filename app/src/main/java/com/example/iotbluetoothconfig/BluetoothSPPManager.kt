package com.example.iotbluetoothconfig

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.*
import java.io.IOException
import java.io.OutputStream
import java.util.*

class BluetoothSPPManager(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    private val sppUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun getBondedDevices(): List<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    fun connect(device: BluetoothDevice, onResult: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN) {
            try {
                val tmpSocket = device.createRfcommSocketToServiceRecord(sppUUID)
                bluetoothAdapter?.cancelDiscovery()
                tmpSocket.connect()
                socket = tmpSocket
                outputStream = tmpSocket.outputStream
                withContext(Dispatchers.Main) {
                    onResult(true)
                }
            } catch (e: IOException) {
                Log.e("BluetoothSPPManager", "Connection failed", e)
                withContext(Dispatchers.Main) {
                    onResult(false)
                }
            }
        }
    }

    fun sendData(data: String) {
        try {
            outputStream?.write(data.toByteArray())
        } catch (e: IOException) {
            Log.e("BluetoothSPPManager", "Error sending data", e)
        }
    }

    fun disconnect() {
        try {
            socket?.close()
        } catch (e: IOException) {
            Log.e("BluetoothSPPManager", "Error closing socket", e)
        }
    }
}
