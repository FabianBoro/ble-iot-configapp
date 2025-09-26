package com.example.iotbluetoothconfig.viewmodel

import android.app.Application
import android.net.wifi.WifiManager
import android.text.format.Formatter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.*
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*

class DoorLockViewModel(app: Application) : AndroidViewModel(app) {

    // MQTT configuration
    private val brokerHost = "172.15.5.53"
    private val brokerPort = 1883
    private val brokerUrl = "tcp://$brokerHost:$brokerPort"

    private var mqttClient: MqttClient? = null

    // State management
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private val _networkReady = MutableStateFlow(false)
    val networkReady: StateFlow<Boolean> = _networkReady

    private val _publishStatus = MutableStateFlow<String?>(null)
    val publishStatus: StateFlow<String?> = _publishStatus

    // ========================
    // 1️⃣  Check Network (SSID + Ping)
    // ========================
    fun checkNetworkReady() {
        viewModelScope.launch(Dispatchers.IO) {
            val reachable = pingBroker(brokerHost, brokerPort)
            _networkReady.value = reachable
            appendLog(if (reachable) "✅ Broker MQTT terjangkau." else "❌ Broker tidak dapat diakses.")
        }
    }

    private fun pingBroker(host: String, port: Int, timeout: Int = 1500): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeout)
                true
            }
        } catch (e: Exception) {
            appendLog("Ping gagal: ${e.message}")
            false
        }
    }

    // ========================
    // 2️⃣  MQTT Connection
    // ========================
    fun connectMqtt() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (mqttClient == null) {
                    mqttClient = MqttClient(
                        brokerUrl,
                        MqttClient.generateClientId(),
                        null
                    )
                }

                val options = MqttConnectOptions().apply {
                    isCleanSession = true
                    connectionTimeout = 5
                    keepAliveInterval = 20
                }

                mqttClient?.connect(options)
                _isConnected.value = mqttClient?.isConnected == true
                appendLog("🔌 MQTT connected ke $brokerUrl")
            } catch (e: Exception) {
                appendLog("❌ MQTT connection error: ${e.message}")
                _isConnected.value = false
            }
        }
    }

    fun disconnectMqtt() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                mqttClient?.disconnect()
                _isConnected.value = false
                appendLog("🔌 MQTT disconnected")
            } catch (e: Exception) {
                appendLog("❌ MQTT disconnect error: ${e.message}")
            }
        }
    }

    // ========================
    // 3️⃣  Publish Command (Open Door)
    // ========================
    fun openDoor(doorNumber: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val devEui = when (doorNumber) {
                1 -> "5c7e78a5caf6dd22"
                2 -> "5c7e78a5caf6dd23"
                3 -> "5c7e78a5caf6dd24"
                else -> return@launch
            }

            val topic =
                "application/f3f6e948-fc0e-46fe-9629-abaac8a8cf69/device/$devEui/command/down"
            val base64Data = when (doorNumber) {
                1 -> "UDE6T1BFTg=="
                2 -> "UDI6T1BFTg=="
                3 -> "UDM6T1BFTg=="
                else -> ""
            }

            val payload = """
            {
              "confirmed": false,
              "fPort": 1,
              "data": "$base64Data",
              "devEui": "$devEui"
            }
        """.trimIndent()

            try {
                if (mqttClient?.isConnected != true) {
                    appendLog("⚠️ MQTT belum terkoneksi!")
                    _publishStatus.value = "Gagal: MQTT belum terkoneksi"
                    return@launch
                }

                val message = MqttMessage(payload.toByteArray()).apply {
                    qos = 1
                }
                mqttClient?.publish(topic, message)
                appendLog("📤 Publish ke $topic: $payload")
                _publishStatus.value = "✅ Pintu $doorNumber berhasil dibuka"
            } catch (e: Exception) {
                appendLog("❌ Gagal kirim downlink: ${e.message}")
                _publishStatus.value = "❌ Gagal kirim downlink"
            }
        }
    }


    // ========================
    // 🪵 Logger
    // ========================
    private fun appendLog(message: String) {
        viewModelScope.launch(Dispatchers.Main) {
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            _logs.value = _logs.value + "[$time] $message"
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnectMqtt()
    }
}
