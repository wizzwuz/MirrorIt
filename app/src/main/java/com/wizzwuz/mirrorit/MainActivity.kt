package com.wizzwuz.mirrorit

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.wizzwuz.mirrorit.network.DiscoveryBeacon
import com.wizzwuz.mirrorit.network.NetworkUtils
import com.wizzwuz.mirrorit.service.ScreenCaptureService
import com.wizzwuz.mirrorit.shizuku.ShizukuManager
import com.wizzwuz.mirrorit.ui.screens.MainTabScreen
import com.wizzwuz.mirrorit.ui.theme.MirrorItTheme

class MainActivity : ComponentActivity() {

    private lateinit var mediaProjectionManager: MediaProjectionManager

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Continue
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // Initialize Shizuku
        ShizukuManager.init()

        // Start local discovery listener
        DiscoveryBeacon.startListening()

        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MirrorItTheme {
                val interfaces = NetworkUtils.getLocalIpAddresses(this)
                MainTabScreen(
                    interfaces = interfaces,
                    onStartStream = {
                        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
                        mediaProjectionLauncher.launch(captureIntent)
                    },
                    onStopStream = {
                        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
                            action = ScreenCaptureService.ACTION_STOP
                        }
                        startService(stopIntent)
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        DiscoveryBeacon.stopListening()
        ShizukuManager.destroy()
    }
}
