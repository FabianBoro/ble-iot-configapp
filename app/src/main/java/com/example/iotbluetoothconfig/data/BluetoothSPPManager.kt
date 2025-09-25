package com.example.iotbluetoothconfig.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.concurrent.thread

class BluetoothSPPManager {
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    private val SPP_UUID: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    fun isBluetoothAvailable(): Boolean = adapter != null

    fun connect(device: BluetoothDevice, onLog: (String) -> Unit) {
        thread {
            try {
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket?.connect()
                input = socket?.inputStream
                output = socket?.outputStream

                val buffer = ByteArray(1024)
                while (true) {
                    val bytes = input?.read(buffer) ?: break
                    if (bytes > 0) {
                        val message = String(buffer, 0, bytes)
                        onLog(message) // kirim ke ViewModel
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onLog("Error: ${e.message}")
            }
        }
    }

    fun send(message: String) {
        thread {
            try {
                output?.write(message.toByteArray())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun close() {
        try {
            input?.close()
            output?.close()
            socket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
