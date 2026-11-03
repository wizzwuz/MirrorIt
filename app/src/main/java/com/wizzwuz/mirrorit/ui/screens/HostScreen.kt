package com.wizzwuz.mirrorit.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wizzwuz.mirrorit.model.StreamConfig
import com.wizzwuz.mirrorit.network.NetworkUtils
import com.wizzwuz.mirrorit.service.ScreenCaptureService
import com.wizzwuz.mirrorit.shizuku.ShizukuManager
import com.wizzwuz.mirrorit.ui.components.NetworkStatusBadge
import com.wizzwuz.mirrorit.ui.components.ShizukuStatusCard
import com.wizzwuz.mirrorit.ui.theme.*

@Composable
fun HostScreen(
    interfaces: List<NetworkUtils.NetworkInterfaceInfo>,
    isStreaming: Boolean,
    onStartStream: () -> Unit,
    onStopStream: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shizukuState by ShizukuManager.shizukuState.collectAsState()
    var selectedPreset by remember { mutableStateOf(StreamConfig.PRESET_HD) }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Main Stream Action Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        colors = if (isStreaming) listOf(
                            EmeraldSuccess.copy(alpha = 0.25f),
                            DarkSurface
                        ) else listOf(
                            DarkSurfaceVariant,
                            DarkSurface
                        )
                    )
                )
                .border(
                    1.dp,
                    if (isStreaming) EmeraldSuccess.copy(alpha = 0.6f) else DarkBorder,
                    RoundedCornerShape(20.dp)
                )
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            if (isStreaming) EmeraldSuccess.copy(alpha = 0.2f) else NeonCyan.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isStreaming) Icons.Default.ScreenShare else Icons.Default.Cast,
                        contentDescription = null,
                        tint = if (isStreaming) EmeraldSuccess else NeonCyan,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (isStreaming) "Screen Broadcast Active" else "Ready to Broadcast",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Text(
                    text = if (isStreaming)
                        "Other devices on the same Wi-Fi or Hotspot can view and control this screen."
                    else
                        "Start sharing to allow another Android device to mirror and remotely control this device.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                    lineHeight = 18.sp
                )

                Button(
                    onClick = {
                        if (isStreaming) onStopStream() else {
                            ScreenCaptureService.currentConfig = selectedPreset
                            onStartStream()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isStreaming) CrimsonError else NeonCyan,
                        contentColor = if (isStreaming) Color.White else Color.Black
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(
                        imageVector = if (isStreaming) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isStreaming) "Stop Mirroring" else "Start Screen Sharing",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        // Network Status
        NetworkStatusBadge(interfaces = interfaces)

        // Shizuku Status
        ShizukuStatusCard(
            state = shizukuState,
            onRequestPermission = { ShizukuManager.requestPermission() }
        )

        // Stream Quality Preset Selector
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "Stream Quality",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QualityChip(
                    title = "720p HD",
                    subtitle = "30 FPS • Balanced",
                    isSelected = selectedPreset == StreamConfig.PRESET_HD,
                    onClick = { selectedPreset = StreamConfig.PRESET_HD },
                    modifier = Modifier.weight(1f)
                )
                QualityChip(
                    title = "1080p FHD",
                    subtitle = "60 FPS • High Bitrate",
                    isSelected = selectedPreset == StreamConfig.PRESET_FHD,
                    onClick = { selectedPreset = StreamConfig.PRESET_FHD },
                    modifier = Modifier.weight(1f)
                )
                QualityChip(
                    title = "480p SD",
                    subtitle = "30 FPS • Ultra Fast",
                    isSelected = selectedPreset == StreamConfig.PRESET_SD,
                    onClick = { selectedPreset = StreamConfig.PRESET_SD },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Hotspot Quick Instructions Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurfaceVariant.copy(alpha = 0.5f))
                .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WifiTethering,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Direct Hotspot Mode (Zero Router)",
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Turn ON Mobile Hotspot on this phone, connect the other phone to this Hotspot's Wi-Fi, and tap 'Connect to Hotspot Host (192.168.43.1)' on that phone. No router or internet data is needed!",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun QualityChip(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) NeonCyan.copy(alpha = 0.15f) else DarkSurfaceVariant)
            .border(
                1.dp,
                if (isSelected) NeonCyan else DarkBorder,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) NeonCyan else TextPrimary,
                fontSize = 13.sp
            )
            Text(
                text = subtitle,
                color = TextMuted,
                fontSize = 10.sp
            )
        }
    }
}
