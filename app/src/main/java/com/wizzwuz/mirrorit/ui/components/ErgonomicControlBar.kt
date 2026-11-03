package com.wizzwuz.mirrorit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wizzwuz.mirrorit.model.ControlPacket
import com.wizzwuz.mirrorit.ui.theme.*

@Composable
fun ErgonomicControlBar(
    fps: Int,
    latencyMs: Long,
    onSendKey: (Int) -> Unit,
    onOpenKeyboard: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(true) }

    Column(
        modifier = modifier.padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Stats HUD pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(DarkBackground.copy(alpha = 0.85f))
                .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "FPS: $fps",
                    color = if (fps >= 25) EmeraldSuccess else AmberWarning,
                    fontSize = 12.sp
                )
                Text(
                    text = "Latency: ${latencyMs}ms",
                    color = if (latencyMs < 50) EmeraldSuccess else if (latencyMs < 100) AmberWarning else CrimsonError,
                    fontSize = 12.sp
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (fps > 0) EmeraldSuccess else CrimsonError)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Main Navigation Pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(DarkSurface.copy(alpha = 0.92f))
                .border(1.dp, DarkBorder, RoundedCornerShape(24.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ChevronLeft else Icons.Default.ChevronRight,
                    contentDescription = "Toggle controls",
                    tint = NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ControlButton(Icons.Default.ArrowBack, "Back") {
                        onSendKey(ControlPacket.Key.KEY_BACK)
                    }
                    ControlButton(Icons.Default.Home, "Home") {
                        onSendKey(ControlPacket.Key.KEY_HOME)
                    }
                    ControlButton(Icons.Default.Apps, "Recents") {
                        onSendKey(ControlPacket.Key.KEY_RECENTS)
                    }
                    ControlButton(Icons.Default.Keyboard, "Keyboard") {
                        onOpenKeyboard()
                    }
                    ControlButton(Icons.Default.PowerSettingsNew, "Power") {
                        onSendKey(ControlPacket.Key.KEY_POWER)
                    }
                    ControlButton(Icons.Default.VolumeUp, "Vol +") {
                        onSendKey(ControlPacket.Key.KEY_VOLUME_UP)
                    }
                    ControlButton(Icons.Default.VolumeDown, "Vol -") {
                        onSendKey(ControlPacket.Key.KEY_VOLUME_DOWN)
                    }
                    ControlButton(Icons.Default.Close, "Disconnect", tint = CrimsonError) {
                        onDisconnect()
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    desc: String,
    tint: Color = TextPrimary,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(DarkSurfaceVariant.copy(alpha = 0.7f))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = desc,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}
