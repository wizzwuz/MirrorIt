package com.wizzwuz.mirrorit.network

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import android.view.Surface
import com.wizzwuz.mirrorit.model.ControlPacket
import com.wizzwuz.mirrorit.model.FrameHeader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer

class ClientStreamer(
    private val hostIp: String,
    private val hostPort: Int = 45556,
    private val surface: Surface
) {
    private val TAG = "ClientStreamer"

    enum class ConnectionStatus {
        DISCONNECTED,
        CONNECTING,
        STREAMING,
        ERROR
    }

    private val _status = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps.asStateFlow()

    private val _latencyMs = MutableStateFlow(0L)
    val latencyMs: StateFlow<Long> = _latencyMs.asStateFlow()

    private var socket: Socket? = null
    private var dataInputStream: DataInputStream? = null
    private var dataOutputStream: DataOutputStream? = null
    private var decoder: MediaCodec? = null
    private var streamJob: Job? = null
    private var heartbeatJob: Job? = null

    @Volatile
    private var isDecoderReady = false

    private var hostWidth = 1080
    private var hostHeight = 1920

    fun start() {
        stop()
        _status.value = ConnectionStatus.CONNECTING
        streamJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                // Resolve target IP
                var targetIp = hostIp.trim()
                if (targetIp.isBlank() || targetIp == "127.0.0.1" || targetIp == "0.0.0.0") {
                    targetIp = "192.168.43.1"
                }

                // Connect with up to 3 fast retries (useful if host is just launching)
                var connectedSocket: Socket? = null
                var lastEx: Exception? = null
                val maxRetries = 3

                for (attempt in 1..maxRetries) {
                    if (!isActive) break
                    try {
                        Log.i(TAG, "Connecting to host $targetIp:$hostPort (attempt $attempt/$maxRetries)...")
                        val s = Socket()
                        s.tcpNoDelay = true
                        s.connect(InetSocketAddress(targetIp, hostPort), 3500)
                        connectedSocket = s
                        break
                    } catch (e: Exception) {
                        lastEx = e
                        Log.w(TAG, "Connection attempt $attempt failed: ${e.message}")
                        if (attempt < maxRetries && isActive) {
                            delay(700)
                        }
                    }
                }

                val s = connectedSocket ?: throw (lastEx ?: java.net.ConnectException("Failed to connect to $targetIp:$hostPort"))
                socket = s
                dataInputStream = DataInputStream(BufferedInputStream(s.getInputStream(), 128 * 1024))
                dataOutputStream = DataOutputStream(s.getOutputStream())

                // Send initial ping to confirm two-way communication
                ControlPacket.write(dataOutputStream!!, ControlPacket.Ping)

                // Start decoder
                initDecoder(hostWidth, hostHeight, null)

                _status.value = ConnectionStatus.STREAMING
                Log.i(TAG, "Connected to host $targetIp:$hostPort, stream loop active")

                // Start heartbeat coroutine to keep socket alive
                heartbeatJob = launch(Dispatchers.IO) {
                    while (isActive && !s.isClosed) {
                        delay(2000)
                        synchronized(this@ClientStreamer) {
                            try {
                                dataOutputStream?.let { out ->
                                    ControlPacket.write(out, ControlPacket.Ping)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }

                var frameCounter = 0
                var lastFpsTime = System.currentTimeMillis()
                val buffer = ByteArray(512 * 1024)

                while (isActive && !s.isClosed) {
                    val header = FrameHeader.read(dataInputStream!!) ?: break
                    val payloadLen = header.payloadLength
                    if (payloadLen <= 0 || payloadLen > buffer.size) {
                        Log.w(TAG, "Skipping invalid payload length: $payloadLen")
                        continue
                    }

                    dataInputStream!!.readFully(buffer, 0, payloadLen)

                    val now = System.currentTimeMillis()
                    val frameTimeMs = header.timestampUs / 1000
                    if (frameTimeMs > 0 && now >= frameTimeMs) {
                        _latencyMs.value = (now - frameTimeMs).coerceIn(1, 999)
                    }

                    when (header.type) {
                        FrameHeader.TYPE_METADATA -> {
                            if (payloadLen >= 8) {
                                val bb = ByteBuffer.wrap(buffer, 0, payloadLen)
                                hostWidth = bb.int
                                hostHeight = bb.int
                                Log.i(TAG, "Host screen metadata received: ${hostWidth}x${hostHeight}")
                            }
                        }
                        FrameHeader.TYPE_CONFIG -> {
                            // Received SPS/PPS from Host; reconfigure decoder with exact parameters
                            val csd = ByteArray(payloadLen)
                            System.arraycopy(buffer, 0, csd, 0, payloadLen)
                            initDecoder(hostWidth, hostHeight, csd)
                        }
                        else -> {
                            feedDecoder(header.type, header.timestampUs, buffer, payloadLen)
                        }
                    }

                    frameCounter++
                    if (now - lastFpsTime >= 1000) {
                        _fps.value = frameCounter
                        frameCounter = 0
                        lastFpsTime = now
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Streamer connection exception: ${e.message}", e)
                _status.value = ConnectionStatus.ERROR
            } finally {
                if (_status.value == ConnectionStatus.STREAMING) {
                    _status.value = ConnectionStatus.ERROR
                }
                cleanup()
            }
        }
    }

    private fun initDecoder(width: Int, height: Int, csd0: ByteArray?) {
        try {
            decoder?.stop()
            decoder?.release()
        } catch (_: Exception) {}
        decoder = null
        isDecoderReady = false

        try {
            if (!surface.isValid) {
                Log.w(TAG, "Surface is not valid for decoder configuration")
                return
            }

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                if (csd0 != null) {
                    setByteBuffer("csd-0", ByteBuffer.wrap(csd0))
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                    } catch (_: Exception) {}
                }
            }

            val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(format, surface, null, 0)
            codec.start()
            decoder = codec
            isDecoderReady = true
            Log.i(TAG, "MediaCodec decoder successfully initialized (${width}x${height}, csd-0: ${csd0 != null})")
        } catch (e: Exception) {
            Log.e(TAG, "Decoder init error: ${e.message}", e)
        }
    }

    private fun feedDecoder(type: Byte, timestampUs: Long, data: ByteArray, length: Int) {
        if (!isDecoderReady) {
            initDecoder(1280, 720, null)
        }
        val codec = decoder ?: return

        try {
            val inIndex = codec.dequeueInputBuffer(10_000)
            if (inIndex >= 0) {
                val inBuffer: ByteBuffer? = codec.getInputBuffer(inIndex)
                inBuffer?.clear()
                inBuffer?.put(data, 0, length)

                val flags = when (type) {
                    FrameHeader.TYPE_CONFIG -> MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                    FrameHeader.TYPE_KEY_FRAME -> MediaCodec.BUFFER_FLAG_KEY_FRAME
                    else -> 0
                }
                codec.queueInputBuffer(inIndex, 0, length, timestampUs, flags)
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var outIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            while (outIndex >= 0) {
                codec.releaseOutputBuffer(outIndex, true)
                outIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Decoder feed warning: ${e.message}")
        }
    }

    fun sendTouch(action: Int, pointerId: Int, normX: Float, normY: Float) {
        CoroutineScope(Dispatchers.IO).launch {
            synchronized(this@ClientStreamer) {
                val out = dataOutputStream ?: return@launch
                try {
                    ControlPacket.write(out, ControlPacket.Touch(action, pointerId, normX, normY))
                } catch (e: Exception) {
                    Log.w(TAG, "Error sending touch: ${e.message}")
                }
            }
        }
    }

    fun sendKey(keyCode: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            synchronized(this@ClientStreamer) {
                val out = dataOutputStream ?: return@launch
                try {
                    ControlPacket.write(out, ControlPacket.Key(keyCode))
                } catch (e: Exception) {
                    Log.w(TAG, "Error sending key: ${e.message}")
                }
            }
        }
    }

    fun sendText(text: String) {
        CoroutineScope(Dispatchers.IO).launch {
            synchronized(this@ClientStreamer) {
                val out = dataOutputStream ?: return@launch
                try {
                    ControlPacket.write(out, ControlPacket.Text(text))
                } catch (e: Exception) {
                    Log.w(TAG, "Error sending text: ${e.message}")
                }
            }
        }
    }

    private fun cleanup() {
        heartbeatJob?.cancel()
        heartbeatJob = null

        try {
            decoder?.stop()
            decoder?.release()
        } catch (_: Exception) {}
        decoder = null
        isDecoderReady = false

        try {
            dataInputStream?.close()
            dataOutputStream?.close()
            socket?.close()
        } catch (_: Exception) {}
        dataInputStream = null
        dataOutputStream = null
        socket = null

        if (_status.value != ConnectionStatus.ERROR) {
            _status.value = ConnectionStatus.DISCONNECTED
        }
    }

    fun stop() {
        streamJob?.cancel()
        streamJob = null
        cleanup()
    }
}
