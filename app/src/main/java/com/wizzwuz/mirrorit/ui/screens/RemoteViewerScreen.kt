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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.wizzwuz.mirrorit.model.ControlPacket
import com.wizzwuz.mirrorit.network.ClientStreamer
import com.wizzwuz.mirrorit.ui.components.ErgonomicControlBar
import com.wizzwuz.mirrorit.ui.theme.DarkSurface
import com.wizzwuz.mirrorit.ui.theme.NeonCyan
import com.wizzwuz.mirrorit.ui.theme.TextPrimary

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
        // Video SurfaceView with Touch listener
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

                    setOnTouchListener { v, motionEvent ->
                        val w = v.width.toFloat().coerceAtLeast(1f)
                        val h = v.height.toFloat().coerceAtLeast(1f)
                        val normX = (motionEvent.x / w).coerceIn(0f, 1f)
                        val normY = (motionEvent.y / h).coerceIn(0f, 1f)

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
                }
            },
            modifier = Modifier.fillMaxSize()
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
                    .background(Color.Black.copy(alpha = 0.9f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Connection Lost or Refused",
                        color = Color(0xFFEF4444),
                        fontSize = 18.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Text(
                        text = "Could not connect to $hostIp:$hostPort.\n\nPlease check:\n1. The Host device has tapped 'Start Screen Sharing' and granted the capture prompt.\n2. Both devices are connected to the same Wi-Fi or Hotspot.",
                        color = TextPrimary.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { streamer?.start() },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black)
                        ) {
                            Text("Retry Connection")
                        }
                        Button(
                            onClick = onDisconnect,
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurface)
                        ) {
                            Text("Back to Menu", color = TextPrimary)
                        }
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
