package com.wizzwuz.mirrorit.ui.screens

import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.wizzwuz.mirrorit.model.ControlPacket
import com.wizzwuz.mirrorit.network.ClientStreamer
import com.wizzwuz.mirrorit.ui.components.ErgonomicControlBar
import com.wizzwuz.mirrorit.ui.theme.DarkBackground
import com.wizzwuz.mirrorit.ui.theme.DarkSurface
import com.wizzwuz.mirrorit.ui.theme.NeonCyan
import com.wizzwuz.mirrorit.ui.theme.TextPrimary

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RemoteViewerScreen(
    hostIp: String,
    hostPort: Int,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    var streamer by remember { mutableStateOf<ClientStreamer?>(null) }
    val status by streamer?.status?.collectAsState() ?: remember { mutableStateOf(ClientStreamer.ConnectionStatus.CONNECTING) }
    val fps by streamer?.fps?.collectAsState() ?: remember { mutableStateOf(0) }
    val latencyMs by streamer?.latencyMs?.collectAsState() ?: remember { mutableStateOf(0L) }

    var showKeyboardDialog by remember { mutableStateOf(false) }
    var remoteText by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video SurfaceView
        AndroidView(
            factory = { context ->
                SurfaceView(context).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            val client = ClientStreamer(hostIp, hostPort, holder.surface)
                            client.start()
                            streamer = client
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            streamer?.stop()
                            streamer = null
                        }
                    })
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInteropFilter { motionEvent ->
                    val viewWidth = 1080f // Fallback standard
                    val viewHeight = 1920f
                    val normX = (motionEvent.x / (motionEvent.device?.getMotionRange(MotionEvent.AXIS_X)?.range ?: 1080f))
                        .coerceIn(0f, 1f)
                    val normY = (motionEvent.y / (motionEvent.device?.getMotionRange(MotionEvent.AXIS_Y)?.range ?: 1920f))
                        .coerceIn(0f, 1f)

                    val action = when (motionEvent.actionMasked) {
                        MotionEvent.ACTION_DOWN -> ControlPacket.Touch.ACTION_DOWN
                        MotionEvent.ACTION_MOVE -> ControlPacket.Touch.ACTION_MOVE
                        MotionEvent.ACTION_UP -> ControlPacket.Touch.ACTION_UP
                        MotionEvent.ACTION_CANCEL -> ControlPacket.Touch.ACTION_CANCEL
                        else -> -1
                    }

                    if (action != -1) {
                        streamer?.sendTouch(action, motionEvent.getPointerId(0), normX, normY)
                    }
                    true
                }
        )

        // Connecting Indicator
        if (status == ClientStreamer.ConnectionStatus.CONNECTING) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = NeonCyan)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Connecting to $hostIp:$hostPort...",
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Error State
        if (status == ClientStreamer.ConnectionStatus.ERROR) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Connection Lost or Refused",
                        color = Color.Red,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurface)
                    ) {
                        Text("Return to Menu", color = TextPrimary)
                    }
                }
            }
        }

        // Floating Ergonomic Overlay Bar
        ErgonomicControlBar(
            fps = fps,
            latencyMs = latencyMs,
            onSendKey = { keyCode -> streamer?.sendKey(keyCode) },
            onOpenKeyboard = { showKeyboardDialog = true },
            onDisconnect = {
                streamer?.stop()
                onDisconnect()
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Remote Keyboard Input Modal
        if (showKeyboardDialog) {
            AlertDialog(
                onDismissRequest = { showKeyboardDialog = false },
                title = { Text("Remote Text Input", color = TextPrimary) },
                text = {
                    Column {
                        Text(
                            text = "Type text to send directly to the controlled Android device:",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = remoteText,
                            onValueChange = { remoteText = it },
                            placeholder = { Text("Enter text to type...") },
                            singleLine = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (remoteText.isNotEmpty()) {
                                streamer?.sendText(remoteText)
                                remoteText = ""
                            }
                            showKeyboardDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black)
                    ) {
                        Text("Send Text")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showKeyboardDialog = false }) {
                        Text("Cancel", color = TextPrimary)
                    }
                },
                containerColor = DarkSurface,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}
