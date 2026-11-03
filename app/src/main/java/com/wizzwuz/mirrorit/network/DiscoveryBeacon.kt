package com.wizzwuz.mirrorit.network

import android.os.Build
import android.util.Log
import com.wizzwuz.mirrorit.model.DeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

object DiscoveryBeacon {
    private const val TAG = "DiscoveryBeacon"
    const val DISCOVERY_PORT = 45555

    private var broadcastJob: Job? = null
    private var listenerJob: Job? = null

    private val _discoveredDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val discoveredDevices: StateFlow<List<DeviceInfo>> = _discoveredDevices.asStateFlow()

    fun startBroadcasting(
        deviceName: String,
        hostIp: String,
        port: Int,
        width: Int,
        height: Int,
        hasShizuku: Boolean
    ) {
        stopBroadcasting()
        broadcastJob = CoroutineScope(Dispatchers.IO).launch {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.broadcast = true

                while (isActive) {
                    try {
                        val json = JSONObject().apply {
                            put("name", deviceName)
                            put("ip", hostIp)
                            put("port", port)
                            put("width", width)
                            put("height", height)
                            put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
                            put("shizuku", hasShizuku)
                        }
                        val data = json.toString().toByteArray(Charsets.UTF_8)
                        val broadcastAddresses = NetworkUtils.getAllBroadcastAddresses()
                        for (broadcastAddr in broadcastAddresses) {
                            try {
                                val packet = DatagramPacket(data, data.size, broadcastAddr, DISCOVERY_PORT)
                                socket.send(packet)
                            } catch (_: Exception) {}
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Beacon send error: ${e.message}")
                    }
                    delay(1500)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Broadcasting failed", e)
            } finally {
                socket?.close()
            }
        }
    }

    fun stopBroadcasting() {
        broadcastJob?.cancel()
        broadcastJob = null
    }

    fun startListening() {
        if (listenerJob != null && listenerJob?.isActive == true) return
        listenerJob = CoroutineScope(Dispatchers.IO).launch {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(DISCOVERY_PORT))
                    broadcast = true
                }

                // Cleanup coroutine for stale devices
                launch {
                    while (isActive) {
                        delay(3000)
                        val now = System.currentTimeMillis()
                        _discoveredDevices.value = _discoveredDevices.value.filter {
                            now - it.lastSeenTimestamp < 6000
                        }
                    }
                }

                val buffer = ByteArray(2048)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isActive) {
                    try {
                        socket.receive(packet)
                        val message = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                        val json = JSONObject(message)

                        // Priority: use the packet's sender IP as it is the proven routable IP across this network
                        val packetSenderIp = packet.address?.hostAddress
                        val resolvedIp = if (!packetSenderIp.isNullOrBlank() && packetSenderIp != "0.0.0.0" && packetSenderIp != "127.0.0.1") {
                            packetSenderIp
                        } else {
                            val jsonIp = json.optString("ip", "")
                            if (jsonIp.isNotBlank() && jsonIp != "127.0.0.1") jsonIp else "192.168.43.1"
                        }

                        val device = DeviceInfo(
                            deviceName = json.optString("name", "Android Device"),
                            ipAddress = resolvedIp,
                            port = json.optInt("port", 45556),
                            screenWidth = json.optInt("width", 1080),
                            screenHeight = json.optInt("height", 1920),
                            osVersion = json.optString("model", "Android"),
                            hasShizuku = json.optBoolean("shizuku", false),
                            lastSeenTimestamp = System.currentTimeMillis()
                        )

                        val current = _discoveredDevices.value.toMutableList()
                        val index = current.indexOfFirst { it.ipAddress == device.ipAddress && it.port == device.port }
                        if (index >= 0) {
                            current[index] = device
                        } else {
                            current.add(device)
                        }
                        _discoveredDevices.value = current
                    } catch (e: Exception) {
                        if (!isActive) break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Listener socket failed", e)
            } finally {
                socket?.close()
            }
        }
    }

    fun stopListening() {
        listenerJob?.cancel()
        listenerJob = null
        _discoveredDevices.value = emptyList()
    }
}
