package com.sharedash.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharedash.app.discovery.HotspotState
import com.sharedash.app.model.DiscoveredPeer
import com.sharedash.app.ui.theme.NeoBg
import com.sharedash.app.ui.theme.NeoBlue
import com.sharedash.app.ui.theme.NeoButton
import com.sharedash.app.ui.theme.NeoCard
import com.sharedash.app.ui.theme.NeoCardPressed
import com.sharedash.app.ui.theme.NeoCyan
import com.sharedash.app.ui.theme.NeoDarkShadow
import com.sharedash.app.ui.theme.NeoGreen
import com.sharedash.app.ui.theme.NeoInset
import com.sharedash.app.ui.theme.NeoLightShadow
import com.sharedash.app.ui.theme.NeoRed
import com.sharedash.app.ui.theme.TextMuted
import com.sharedash.app.ui.theme.TextPrimary
import com.sharedash.app.ui.theme.TextSecondary

@Composable
fun DiscoveryScreen(
    discoveredPeers: List<DiscoveredPeer>,
    connectedPeer: DiscoveredPeer?,
    isUsbConnected: Boolean,
    isWifiEnabled: Boolean,
    isBluetoothEnabled: Boolean,
    onDeviceSelected: (DiscoveredPeer) -> Unit,
    onDisconnect: () -> Unit,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
    onOpenPairing: () -> Unit,
    onOpenDownloadsFolder: () -> Unit,
    onOpenUsbSettings: () -> Unit = {},
    onOpenHotspotSettings: () -> Unit = {},
    hotspotState: HotspotState = HotspotState.Idle,
    onStartHotspot: () -> Unit = {},
    onStopHotspot: () -> Unit = {},
    onEnableBluetooth: () -> Unit = {},
    onEnableWifi: () -> Unit = {},
    onReturnToUsbMode: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "radarPulse")
    val pulse1 by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "pulse1"
    )
    val pulse1Alpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "pulse1Alpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NeoBg)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ═══════════════════════════════════════════════════════════════
        //  NEOMORPHIC HEADER BAR
        // ═══════════════════════════════════════════════════════════════
        NeoCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
            elevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(NeoBlue, NeoCyan)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Logo",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "ShareDash",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(NeoGreen)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Ready to Share",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Received Files Shortcut
                NeoButton(
                    onClick = onOpenDownloadsFolder,
                    cornerRadius = 14.dp,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Received Files",
                        tint = NeoCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ═══════════════════════════════════════════════════════════════
        //  BLUETOOTH OFF ALERT BANNER
        // ═══════════════════════════════════════════════════════════════
        if (!isBluetoothEnabled) {
            NeoCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                cornerRadius = 18.dp,
                elevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(NeoBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = "Bluetooth Off",
                                tint = NeoBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Bluetooth is Turned Off",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Enable for wireless nearby discovery",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    NeoButton(
                        onClick = onEnableBluetooth,
                        cornerRadius = 12.dp,
                        accentColor = NeoBlue
                    ) {
                        Text(
                            text = "Turn On",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════
        //  USB 3.x TURBO BOOST BANNER
        // ═══════════════════════════════════════════════════════════════
        NeoCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            cornerRadius = 18.dp,
            elevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(NeoGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Usb,
                            contentDescription = "USB",
                            tint = NeoGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Switch to USB Mode",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Plug USB cable for 10x faster 3+ Gbps transfer",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                NeoButton(
                    onClick = onReturnToUsbMode,
                    cornerRadius = 12.dp,
                    accentColor = NeoGreen
                ) {
                    Text(
                        text = "USB Mode",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════
        //  NEOMORPHIC RADAR SCANNER
        // ═══════════════════════════════════════════════════════════════
        Box(
            modifier = Modifier
                .size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            // Concentric Sunken Well
            NeoInset(
                modifier = Modifier.size(190.dp),
                cornerRadius = 95.dp
            ) {}

            // Pulsing Outer Radar Ring
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .scale(pulse1)
                    .clip(CircleShape)
                    .background(NeoBlue.copy(alpha = pulse1Alpha * 0.25f))
            )

            // Inner Neomorphic Central Node
            NeoCard(
                modifier = Modifier.size(76.dp),
                cornerRadius = 38.dp,
                elevation = 6.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = "This Device",
                        tint = NeoBlue,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (discoveredPeers.isEmpty()) "Searching for nearby PCs..." else "Found ${discoveredPeers.size} Device(s)",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "Make sure ShareDash is open on your PC",
            fontSize = 12.sp,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ═══════════════════════════════════════════════════════════════
        //  FOUND DEVICES LIST (NEOMORPHIC CARDS)
        // ═══════════════════════════════════════════════════════════════
        if (discoveredPeers.isNotEmpty()) {
            discoveredPeers.forEach { peer ->
                NeoCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    cornerRadius = 20.dp,
                    elevation = 6.dp,
                    onClick = { onDeviceSelected(peer) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(NeoBlue.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Computer,
                                    contentDescription = "PC",
                                    tint = NeoBlue,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = peer.friendlyName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(NeoGreen)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "High-Speed Multipath Ready",
                                        fontSize = 11.sp,
                                        color = NeoCyan,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        NeoButton(
                            onClick = { onDeviceSelected(peer) },
                            cornerRadius = 14.dp,
                            accentColor = NeoBlue,
                            modifier = Modifier.padding(start = 6.dp)
                        ) {
                            Text(
                                text = "Connect",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // Empty State Inset Card
            NeoInset(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                cornerRadius = 18.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Scanning over Wi-Fi, Bluetooth & USB...",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ═══════════════════════════════════════════════════════════════
        //  QUICK TOOLS & FAST-PATH SHORTCUTS
        // ═══════════════════════════════════════════════════════════════
        Text(
            text = "Transfer Boost Tools",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 5GHz Hotspot Card
            val isHotspotActive = hotspotState is HotspotState.Active
            NeoCard(
                modifier = Modifier.weight(1f),
                cornerRadius = 18.dp,
                backgroundColor = if (isHotspotActive) NeoCardPressed else NeoCard,
                onClick = {
                    if (isHotspotActive) onStopHotspot() else onStartHotspot()
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background((if (isHotspotActive) NeoGreen else NeoCyan).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiTethering,
                            contentDescription = "Hotspot",
                            tint = if (isHotspotActive) NeoGreen else NeoCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isHotspotActive) "5GHz Active" else "5GHz Hotspot",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isHotspotActive) NeoGreen else TextPrimary
                    )
                    Text(
                        text = if (isHotspotActive) "Tap to turn off" else "1200 Mbps link",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            // USB Cable Mode Card
            NeoCard(
                modifier = Modifier.weight(1f),
                cornerRadius = 18.dp,
                onClick = onOpenUsbSettings
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NeoCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Usb,
                            contentDescription = "USB",
                            tint = NeoCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "USB Cable",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "30+ MB/s wired",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
        }
    }
}
