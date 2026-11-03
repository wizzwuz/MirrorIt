package com.wizzwuz.mirrorit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wizzwuz.mirrorit.shizuku.ShizukuManager
import com.wizzwuz.mirrorit.ui.theme.*

@Composable
fun ShizukuStatusCard(
    state: ShizukuManager.State,
    onRequestPermission: () -> Unit,
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
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Shizuku API v23",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }

            val (badgeText, badgeBg, badgeTextColor) = when (state) {
                ShizukuManager.State.GRANTED -> Triple("Authorized", EmeraldSuccess.copy(alpha = 0.2f), EmeraldSuccess)
                ShizukuManager.State.WAITING_PERMISSION -> Triple("Need Permission", AmberWarning.copy(alpha = 0.2f), AmberWarning)
                ShizukuManager.State.DEAD -> Triple("Not Running", CrimsonError.copy(alpha = 0.2f), CrimsonError)
                ShizukuManager.State.UNSUPPORTED -> Triple("Unsupported", DarkBorder, TextMuted)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = badgeText,
                    color = badgeTextColor,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (state) {
            ShizukuManager.State.GRANTED -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Elevated shell input injection active with 0-fork latency.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
            ShizukuManager.State.WAITING_PERMISSION -> {
                Text(
                    text = "Shizuku service is running. Grant permission to allow remote touch and keyboard injection without root.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Grant Permission", fontSize = 13.sp)
                }
            }
            ShizukuManager.State.DEAD -> {
                Text(
                    text = "Shizuku is not running. Launch the Shizuku app to activate high-performance input, or enable MirrorIt Accessibility Service as fallback.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
            ShizukuManager.State.UNSUPPORTED -> {
                Text(
                    text = "Shizuku is not installed. MirrorIt will use the Accessibility Service fallback.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}
