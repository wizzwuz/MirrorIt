package com.wizzwuz.mirrorit

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class MirrorItApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_SCREEN_CAPTURE,
                "Screen Mirroring Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows notification while screen mirroring and remote control are active."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID_SCREEN_CAPTURE = "mirrorit_screen_capture_channel"
        lateinit var instance: MirrorItApp
            private set
    }
}
