package com.wizzwuz.mirrorit.network

import android.util.Log
import com.wizzwuz.mirrorit.accessibility.MirrorAccessibilityService
import com.wizzwuz.mirrorit.model.ControlPacket
import com.wizzwuz.mirrorit.model.FrameHeader
import com.wizzwuz.mirrorit.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import kotlin.math.abs

class HostServer(
    private val port: Int = 45556,
    private val hostScreenWidth: Int = 1080,
    private val hostScreenHeight: Int = 1920
) {
    private val TAG = "HostServer"

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var dataOutputStream: DataOutputStream? = null
    private var dataInputStream: DataInputStream? = null
    private var serverJob: Job? = null
    private var clientReaderJob: Job? = null

    var onClientConnectedListener: (() -> Unit)? = null

    // Cache latest SPS/PPS config frame so newly connected clients can decode immediately
    @Volatile
    private var cachedConfigData: ByteArray? = null

    private val _isClientConnected = MutableStateFlow(false)
    val isClientConnected: StateFlow<Boolean> = _isClientConnected.asStateFlow()

    private val _connectedClientIp = MutableStateFlow<String?>(null)
    val connectedClientIp: StateFlow<String?> = _connectedClientIp.asStateFlow()

    // Touch gesture tracking for tap vs swipe
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downTimeMs = 0L

    fun start() {
        stop()
        serverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                Log.i(TAG, "Host server listening on port $port")

                while (isActive) {
                    try {
                        val socket = serverSocket?.accept() ?: break
                        socket.tcpNoDelay = true
                        Log.i(TAG, "Client connected from ${socket.remoteSocketAddress}")

                        synchronized(this@HostServer) {
                            try {
                                clientSocket?.close()
                            } catch (_: Exception) {}

                            clientSocket = socket
                            dataOutputStream = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), 128 * 1024))
                            dataInputStream = DataInputStream(socket.getInputStream())
                            _isClientConnected.value = true
                            _connectedClientIp.value = socket.inetAddress.hostAddress

                            // Immediately send cached SPS/PPS codec configuration if available
                            val config = cachedConfigData
                            if (config != null) {
                                try {
                                    FrameHeader.write(dataOutputStream!!, FrameHeader.TYPE_CONFIG, 0L, config.size)
                                    dataOutputStream!!.write(config)
                                    dataOutputStream!!.flush()
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to send initial SPS/PPS config to client: ${e.message}")
                                }
                            }
                        }

                        // Notify listener to request an immediate keyframe from MediaCodec
                        onClientConnectedListener?.invoke()

                        // Spawn client reader coroutine so accept loop continues listening
                        clientReaderJob?.cancel()
                        clientReaderJob = launch(Dispatchers.IO) {
                            readControlPackets(socket)
                        }

                    } catch (e: SocketException) {
                        if (!isActive) break
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception in client accept loop", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server socket error on port $port", e)
            }
        }
    }

    private fun readControlPackets(socket: Socket) {
        val input = dataInputStream ?: return
        try {
            while (!socket.isClosed && socket.isConnected) {
                val packet = ControlPacket.read(input)
                if (packet == null) {
                    // Stream closed
                    break
                }
                when (packet) {
                    is ControlPacket.Ping -> {
                        // Keep-alive heartbeat, no-op
                    }
                    is ControlPacket.Touch -> handleTouchEvent(packet)
                    is ControlPacket.Key -> handleKeyEvent(packet)
                    is ControlPacket.Text -> handleTextEvent(packet)
                }
            }
        } catch (_: EOFException) {
            Log.i(TAG, "Client disconnected normally")
        } catch (e: Exception) {
            Log.w(TAG, "Control packet read ended: ${e.message}")
        } finally {
            onClientDisconnected()
        }
    }

    private fun handleTouchEvent(touch: ControlPacket.Touch) {
        val targetX = (touch.normalizedX * hostScreenWidth).coerceIn(0f, hostScreenWidth.toFloat())
        val targetY = (touch.normalizedY * hostScreenHeight).coerceIn(0f, hostScreenHeight.toFloat())

        when (touch.action) {
            ControlPacket.Touch.ACTION_DOWN -> {
                downX = targetX
                downY = targetY
                lastX = targetX
                lastY = targetY
                downTimeMs = System.currentTimeMillis()
            }
            ControlPacket.Touch.ACTION_MOVE -> {
                lastX = targetX
                lastY = targetY
            }
            ControlPacket.Touch.ACTION_UP -> {
                val dx = abs(lastX - downX)
                val dy = abs(lastY - downY)
                val elapsed = (System.currentTimeMillis() - downTimeMs).coerceAtLeast(50)

                val isTap = dx < 25 && dy < 25 && elapsed < 400
                if (ShizukuManager.isGranted()) {
                    if (isTap) {
                        ShizukuManager.injectTap(downX.toInt(), downY.toInt())
                    } else {
                        ShizukuManager.injectSwipe(
                            downX.toInt(),
                            downY.toInt(),
                            lastX.toInt(),
                            lastY.toInt(),
                            elapsed.toInt().coerceIn(100, 800)
                        )
                    }
                } else if (MirrorAccessibilityService.isRunning) {
                    val service = MirrorAccessibilityService.instance
                    if (isTap) {
                        service?.dispatchClick(downX, downY)
                    } else {
                        service?.dispatchSwipe(downX, downY, lastX, lastY, elapsed)
                    }
                }
            }
            ControlPacket.Touch.ACTION_CANCEL -> {
                // Cancelled
            }
        }
    }

    private fun handleKeyEvent(key: ControlPacket.Key) {
        if (ShizukuManager.isGranted()) {
            ShizukuManager.injectKey(key.keyCode)
        } else if (MirrorAccessibilityService.isRunning) {
            MirrorAccessibilityService.instance?.dispatchKey(key.keyCode)
        }
    }

    private fun handleTextEvent(text: ControlPacket.Text) {
        if (ShizukuManager.isGranted()) {
            ShizukuManager.injectText(text.content)
        }
    }

    fun sendVideoFrame(type: Byte, timestampUs: Long, data: ByteArray, offset: Int, length: Int) {
        // Cache SPS/PPS configuration frame
        if (type == FrameHeader.TYPE_CONFIG) {
            val copy = ByteArray(length)
            System.arraycopy(data, offset, copy, 0, length)
            cachedConfigData = copy
        }

        synchronized(this) {
            val out = dataOutputStream ?: return
            try {
                FrameHeader.write(out, type, timestampUs, length)
                out.write(data, offset, length)
                out.flush()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send video frame: ${e.message}")
                onClientDisconnected()
            }
        }
    }

    private fun onClientDisconnected() {
        synchronized(this) {
            try {
                dataOutputStream?.close()
            } catch (_: Exception) {}
            try {
                dataInputStream?.close()
            } catch (_: Exception) {}
            try {
                clientSocket?.close()
            } catch (_: Exception) {}
            dataOutputStream = null
            dataInputStream = null
            clientSocket = null
            _isClientConnected.value = false
            _connectedClientIp.value = null
        }
    }

    fun stop() {
        clientReaderJob?.cancel()
        clientReaderJob = null
        serverJob?.cancel()
        serverJob = null
        onClientDisconnected()
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }
}
