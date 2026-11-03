package com.wizzwuz.mirrorit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wizzwuz.mirrorit.network.NetworkUtils
import com.wizzwuz.mirrorit.ui.theme.*

@Composable
fun NetworkStatusBadge(
    interfaces: List<NetworkUtils.NetworkInterfaceInfo>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val hasHotspot = interfaces.any { it.isHotspot }
            Icon(
                imageVector = if (hasHotspot) Icons.Default.WifiTethering else Icons.Default.Wifi,
                contentDescription = null,
                tint = if (interfaces.isNotEmpty()) EmeraldSuccess else AmberWarning,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = "Network Interfaces",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (interfaces.isEmpty()) {
            Text(
                text = "No Wi-Fi or Hotspot connected. Please connect to a Wi-Fi network or enable Mobile Hotspot.",
                color = TextSecondary,
                fontSize = 13.sp
            )
        } else {
            interfaces.forEach { intf ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = intf.displayName,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (intf.isHotspot) "Gateway: 192.168.43.1" else "Local subnet",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = intf.ipAddress,
                            color = NeonCyan,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
