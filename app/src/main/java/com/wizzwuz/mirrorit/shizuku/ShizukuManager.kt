package com.wizzwuz.mirrorit.shizuku

import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicBoolean

object ShizukuManager {
    private const val TAG = "ShizukuManager"
    const val REQUEST_CODE_SHIZUKU = 5123

    enum class State {
        DEAD,
        WAITING_PERMISSION,
        GRANTED,
        UNSUPPORTED
    }

    private val _shizukuState = MutableStateFlow(State.DEAD)
    val shizukuState: StateFlow<State> = _shizukuState.asStateFlow()

    private val isShellInitialized = AtomicBoolean(false)
    private var shellProcess: Process? = null
    private var shellWriter: BufferedWriter? = null

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.i(TAG, "Shizuku binder received, version: ${try { Shizuku.getVersion() } catch (e: Exception) { -1 }}")
        checkStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "Shizuku binder dead")
        _shizukuState.value = State.DEAD
        destroyShell()
    }

    private val requestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE_SHIZUKU) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Shizuku permission granted by user")
                    _shizukuState.value = State.GRANTED
                    initShell()
                } else {
                    Log.w(TAG, "Shizuku permission denied by user")
                    _shizukuState.value = State.WAITING_PERMISSION
                }
            }
        }

    fun init() {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
            checkStatus()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize Shizuku", t)
            _shizukuState.value = State.UNSUPPORTED
        }
    }

    fun checkStatus() {
        try {
            if (!Shizuku.pingBinder()) {
                _shizukuState.value = State.DEAD
                return
            }

            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                _shizukuState.value = State.GRANTED
                initShell()
            } else {
                _shizukuState.value = State.WAITING_PERMISSION
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Error checking Shizuku status", t)
            _shizukuState.value = State.DEAD
        }
    }

    fun requestPermission() {
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to request Shizuku permission", t)
        }
    }

    fun isGranted(): Boolean = _shizukuState.value == State.GRANTED

    @Synchronized
    private fun initShell() {
        if (isShellInitialized.get()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val proc = Shizuku.newProcess(arrayOf("sh"), null, null)
                shellProcess = proc
                shellWriter = BufferedWriter(OutputStreamWriter(proc.outputStream))
                isShellInitialized.set(true)
                Log.i(TAG, "Interactive Shizuku shell process started successfully")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to start Shizuku shell process", t)
                isShellInitialized.set(false)
            }
        }
    }

    @Synchronized
    private fun destroyShell() {
        isShellInitialized.set(false)
        try {
            shellWriter?.write("exit\n")
            shellWriter?.flush()
            shellWriter?.close()
        } catch (_: Exception) {}
        shellWriter = null
        try {
            shellProcess?.destroy()
        } catch (_: Exception) {}
        shellProcess = null
    }

    fun sendCommand(cmd: String) {
        CoroutineScope(Dispatchers.IO).launch {
            synchronized(this@ShizukuManager) {
                try {
                    val writer = shellWriter
                    if (writer != null && isShellInitialized.get()) {
                        writer.write(cmd)
                        writer.write("\n")
                        writer.flush()
                    } else if (isGranted()) {
                        initShell()
                        shellWriter?.write(cmd)
                        shellWriter?.write("\n")
                        shellWriter?.flush()
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Error sending command through Shizuku: $cmd", t)
                    destroyShell()
                }
            }
        }
    }

    fun injectTap(x: Int, y: Int) {
        sendCommand("input tap $x $y")
    }

    fun injectSwipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 150) {
        sendCommand("input swipe $x1 $y1 $x2 $y2 $durationMs")
    }

    fun injectKey(keyCode: Int) {
        sendCommand("input keyevent $keyCode")
    }

    fun injectText(text: String) {
        // Escape special shell characters for input text
        val safeText = text.replace(" ", "%s").replace("\"", "\\\"").replace("'", "\\'")
        sendCommand("input text \"$safeText\"")
    }

    fun destroy() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
            destroyShell()
        } catch (_: Exception) {}
    }
}
