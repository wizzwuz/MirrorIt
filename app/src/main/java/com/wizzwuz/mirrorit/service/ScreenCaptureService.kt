package com.wizzwuz.mirrorit.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.wizzwuz.mirrorit.MainActivity
import com.wizzwuz.mirrorit.MirrorItApp
import com.wizzwuz.mirrorit.R
import com.wizzwuz.mirrorit.model.FrameHeader
import com.wizzwuz.mirrorit.model.StreamConfig
import com.wizzwuz.mirrorit.network.DiscoveryBeacon
import com.wizzwuz.mirrorit.network.HostServer
import com.wizzwuz.mirrorit.network.NetworkUtils
import com.wizzwuz.mirrorit.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

class ScreenCaptureService : Service() {
    private val TAG = "ScreenCaptureService"

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var encoder: MediaCodec? = null
    private var encoderSurface: Surface? = null
    private var encodeJob: Job? = null
    private var hostServer: HostServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

        if (resultCode != 0 && resultData != null) {
            startForegroundServiceNotification()
            startScreenCapture(resultCode, resultData)
        } else {
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startForegroundServiceNotification() {
        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            2,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, MirrorItApp.CHANNEL_ID_SCREEN_CAPTURE)
            .setContentTitle("Screen Mirroring Active")
            .setContentText("Broadcasting screen and listening for remote control input.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Mirroring", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startScreenCapture(resultCode: Int, resultData: Intent) {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, resultData)

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val densityDpi = metrics.densityDpi

        // Align dimensions to 16
        val targetWidth = (currentConfig.width / 16) * 16
        val targetHeight = (currentConfig.height / 16) * 16

        // Initialize Host TCP Server
        val server = HostServer(
            port = 45556,
            hostScreenWidth = screenWidth,
            hostScreenHeight = screenHeight
        )
        server.start()
        hostServer = server

        // Start Discovery Beacon
        val primaryIp = NetworkUtils.getPrimaryIpAddress(this)
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
        DiscoveryBeacon.startBroadcasting(
            deviceName = deviceName,
            hostIp = primaryIp,
            port = 45556,
            width = screenWidth,
            height = screenHeight,
            hasShizuku = ShizukuManager.isGranted()
        )

        initEncoder(targetWidth, targetHeight, densityDpi)
        _isRunning.value = true
    }

    private fun initEncoder(width: Int, height: Int, densityDpi: Int) {
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, currentConfig.bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, currentConfig.frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, currentConfig.iFrameIntervalSeconds)
                // Low latency flags
                setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                }
            }

            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = codec.createInputSurface()
            codec.start()

            encoder = codec
            encoderSurface = inputSurface

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "MirrorIt-Capture",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface,
                null,
                null
            )

            startEncodingLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize encoder", e)
            stopSelf()
        }
    }

    private fun startEncodingLoop() {
        encodeJob = CoroutineScope(Dispatchers.IO).launch {
            val codec = encoder ?: return@launch
            val bufferInfo = MediaCodec.BufferInfo()
            val chunkBuffer = ByteArray(512 * 1024)

            try {
                while (isActive) {
                    val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                    if (outIndex >= 0) {
                        val outBuffer: ByteBuffer? = codec.getOutputBuffer(outIndex)
                        if (outBuffer != null && bufferInfo.size > 0) {
                            outBuffer.position(bufferInfo.offset)
                            outBuffer.limit(bufferInfo.offset + bufferInfo.size)

                            val type = when {
                                (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0 -> FrameHeader.TYPE_CONFIG
                                (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0 -> FrameHeader.TYPE_KEY_FRAME
                                else -> FrameHeader.TYPE_DELTA_FRAME
                            }

                            val size = bufferInfo.size
                            if (size <= chunkBuffer.size) {
                                outBuffer.get(chunkBuffer, 0, size)
                                hostServer?.sendVideoFrame(type, bufferInfo.presentationTimeUs, chunkBuffer, 0, size)
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Encoding loop exception", e)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        encodeJob?.cancel()
        encodeJob = null

        DiscoveryBeacon.stopBroadcasting()
        hostServer?.stop()
        hostServer = null

        try {
            encoder?.stop()
            encoder?.release()
        } catch (_: Exception) {}
        encoder = null

        encoderSurface?.release()
        encoderSurface = null

        virtualDisplay?.release()
        virtualDisplay = null

        mediaProjection?.stop()
        mediaProjection = null

        _isRunning.value = false
        Log.i(TAG, "ScreenCaptureService destroyed")
    }

    companion object {
        const val NOTIFICATION_ID = 8801
        const val ACTION_STOP = "com.wizzwuz.mirrorit.ACTION_STOP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        var currentConfig: StreamConfig = StreamConfig.PRESET_HD

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }
}
