package com.wizzwuz.mirrorit.network

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import com.wizzwuz.mirrorit.model.ControlPacket
import com.wizzwuz.mirrorit.model.FrameHeader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    fun start() {
        stop()
        _status.value = ConnectionStatus.CONNECTING
        streamJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val s = Socket()
                s.tcpNoDelay = true
                s.connect(InetSocketAddress(hostIp, hostPort), 5000)
                socket = s
                dataInputStream = DataInputStream(BufferedInputStream(s.getInputStream(), 128 * 1024))
                dataOutputStream = DataOutputStream(s.getOutputStream())

                initDecoder()
                _status.value = ConnectionStatus.STREAMING
                Log.i(TAG, "Connected to host $hostIp:$hostPort, decoding started")

                var frameCounter = 0
                var lastFpsTime = System.currentTimeMillis()

                val buffer = ByteArray(512 * 1024)

                while (isActive && !s.isClosed) {
                    val header = FrameHeader.read(dataInputStream!!) ?: break
                    val payloadLen = header.payloadLength
                    if (payloadLen <= 0 || payloadLen > buffer.size) {
                        Log.w(TAG, "Invalid payload length: $payloadLen")
                        break
                    }

                    dataInputStream!!.readFully(buffer, 0, payloadLen)

                    val now = System.currentTimeMillis()
                    val frameTimeMs = header.timestampUs / 1000
                    if (frameTimeMs > 0 && now >= frameTimeMs) {
                        _latencyMs.value = now - frameTimeMs
                    }

                    feedDecoder(header.type, header.timestampUs, buffer, payloadLen)

                    frameCounter++
                    if (now - lastFpsTime >= 1000) {
                        _fps.value = frameCounter
                        frameCounter = 0
                        lastFpsTime = now
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Streamer exception", e)
                _status.value = ConnectionStatus.ERROR
            } finally {
                cleanup()
            }
        }
    }

    private fun initDecoder() {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
        }
        val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, surface, null, 0)
        codec.start()
        decoder = codec
    }

    private fun feedDecoder(type: Byte, timestampUs: Long, data: ByteArray, length: Int) {
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
            Log.w(TAG, "Decoder feed error: ${e.message}")
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
        try {
            decoder?.stop()
            decoder?.release()
        } catch (_: Exception) {}
        decoder = null

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
