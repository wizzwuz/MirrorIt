package com.wizzwuz.mirrorit.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.wizzwuz.mirrorit.model.ControlPacket

class MirrorAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    fun dispatchClick(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(x, y)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
                .build()
            dispatchGesture(gesture, null, null)
        }
    }

    fun dispatchSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 200) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build()
            dispatchGesture(gesture, null, null)
        }
    }

    fun dispatchKey(keyCode: Int): Boolean {
        return when (keyCode) {
            ControlPacket.Key.KEY_BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            ControlPacket.Key.KEY_HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            ControlPacket.Key.KEY_RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            ControlPacket.Key.KEY_POWER -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                } else false
            }
            else -> false
        }
    }

    companion object {
        var instance: MirrorAccessibilityService? = null
            private set

        val isRunning: Boolean
            get() = instance != null
    }
}
