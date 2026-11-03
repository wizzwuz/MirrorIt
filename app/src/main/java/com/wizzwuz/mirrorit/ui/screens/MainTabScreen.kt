package com.wizzwuz.mirrorit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wizzwuz.mirrorit.network.DiscoveryBeacon
import com.wizzwuz.mirrorit.network.NetworkUtils
import com.wizzwuz.mirrorit.service.ScreenCaptureService
import com.wizzwuz.mirrorit.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTabScreen(
    interfaces: List<NetworkUtils.NetworkInterfaceInfo>,
    onStartStream: () -> Unit,
    onStopStream: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val isStreaming by ScreenCaptureService.isRunning.collectAsState()

    var activeControlTarget by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val context = LocalContext.current

    DisposableEffect(activeControlTarget) {
        if (activeControlTarget != null) {
            DiscoveryBeacon.stopListening()
        } else {
            DiscoveryBeacon.startListening(context)
        }
        onDispose {}
    }

    if (activeControlTarget != null) {
        val (ip, port) = activeControlTarget!!
        RemoteViewerScreen(
            hostIp = ip,
            hostPort = port,
            onDisconnect = { activeControlTarget = null }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NeonCyan),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ScreenShare,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "MirrorIt",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Screen Mirror & Control",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    },
                    actions = {
                        if (isStreaming) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(EmeraldSuccess.copy(alpha = 0.2f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(EmeraldSuccess)
                                    )
                                    Text(
                                        text = "LIVE",
                                        color = EmeraldSuccess,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DarkBackground,
                        titleContentColor = TextPrimary
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = DarkSurface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.ScreenShare,
                                contentDescription = "Share Screen"
                            )
                        },
                        label = { Text("Share Screen") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = NeonCyan,
                            indicatorColor = NeonCyan,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = "Control Device"
                            )
                        },
                        label = { Text("Control Device") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = NeonCyan,
                            indicatorColor = NeonCyan,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                }
            },
            containerColor = DarkBackground
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (selectedTab) {
                    0 -> HostScreen(
                        interfaces = interfaces,
                        isStreaming = isStreaming,
                        onStartStream = onStartStream,
                        onStopStream = onStopStream
                    )
                    1 -> ClientScreen(
                        onConnectToDevice = { ip, port ->
                            activeControlTarget = Pair(ip, port)
                        }
                    )
                }
            }
        }
    }
}
