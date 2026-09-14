package com.sharedash.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
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
import com.sharedash.app.storage.SettingsManager
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
import com.sharedash.app.ui.theme.NeoPurple
import com.sharedash.app.ui.theme.NeoRed
import com.sharedash.app.ui.theme.NeoYellow
import com.sharedash.app.ui.theme.TextMuted
import com.sharedash.app.ui.theme.TextPrimary
import com.sharedash.app.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    connectionMode: String,
    onConnectionModeChange: (String) -> Unit,
    isUsbCablePlugged: Boolean,
    isUsbTetheringActive: Boolean,
    connectedPeer: DiscoveredPeer?,
    discoveredPeers: List<DiscoveredPeer>,
    onEnableUsbTethering: () -> Unit,
    onSkipUsb: () -> Unit,
    onDeviceSelected: (DiscoveredPeer) -> Unit,
    onDisconnect: () -> Unit,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
    onOpenDownloadsFolder: () -> Unit,
    hotspotState: HotspotState = HotspotState.Idle,
    onStartHotspot: () -> Unit = {},
    onStopHotspot: () -> Unit = {},
    deviceName: String = "My Phone",
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "usbPulse")
    val pulseScale by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseScale"
    )

    val radarPulse by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "radarPulse"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NeoBg)
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ═══════════════════════════════════════════════════════════════
        //  1. TOP BRAND & PROFILE BAR
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
                                    .background(
                                        if (connectedPeer != null) NeoGreen
                                        else if (isUsbTetheringActive) NeoCyan
                                        else if (isUsbCablePlugged) NeoYellow
                                        else TextMuted
                                    )
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (connectedPeer != null) "Connected to ${connectedPeer.friendlyName}"
                                else if (isUsbTetheringActive) "USB Tethering Active"
                                else if (isUsbCablePlugged) "USB Cable Plugged"
                                else deviceName,
                                fontSize = 12.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Downloads Shortcut Button
                NeoButton(
                    onClick = onOpenDownloadsFolder,
                    cornerRadius = 14.dp,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Received Files",
                        tint = NeoCyan,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ═══════════════════════════════════════════════════════════════
        //  2. MODE SELECTOR: PHONE TO PC vs PHONE TO PHONE
        // ═══════════════════════════════════════════════════════════════
        NeoInset(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 18.dp,
            backgroundColor = NeoCardPressed
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val isPhoneToPc = connectionMode == SettingsManager.MODE_PHONE_TO_PC

                // Option: Phone to PC
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .then(
                            if (isPhoneToPc) Modifier.background(Brush.linearGradient(listOf(NeoBlue, NeoCyan)))
                            else Modifier
                        )
                        .clickable { onConnectionModeChange(SettingsManager.MODE_PHONE_TO_PC) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = null,
                            tint = if (isPhoneToPc) Color.White else TextMuted,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Phone to PC",
                            fontSize = 13.sp,
                            fontWeight = if (isPhoneToPc) FontWeight.Bold else FontWeight.Medium,
                            color = if (isPhoneToPc) Color.White else TextSecondary
                        )
                    }
                }

                // Option: Phone to Phone
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .then(
                            if (!isPhoneToPc) Modifier.background(Brush.linearGradient(listOf(NeoPurple, NeoBlue)))
                            else Modifier
                        )
                        .clickable { onConnectionModeChange(SettingsManager.MODE_PHONE_TO_PHONE) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = if (!isPhoneToPc) Color.White else TextMuted,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Phone to Phone",
                            fontSize = 13.sp,
                            fontWeight = if (!isPhoneToPc) FontWeight.Bold else FontWeight.Medium,
                            color = if (!isPhoneToPc) Color.White else TextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ═══════════════════════════════════════════════════════════════
        //  3. HERO CONNECTION BOX: WAITING FOR USB & TETHERING / RADAR
        // ═══════════════════════════════════════════════════════════════
        if (connectionMode == SettingsManager.MODE_PHONE_TO_PC) {
            // PHONE TO PC MODE: USB FAST-PATH EMPHASIS
            NeoCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                elevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {


                    // Animated Glowing Center Hub
                    Box(
                        modifier = Modifier.size(105.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        NeoInset(
                            modifier = Modifier.size(105.dp),
                            cornerRadius = 53.dp
                        ) {}

                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .scale(if (isUsbCablePlugged || isUsbTetheringActive) pulseScale else radarPulse)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = if (isUsbTetheringActive) listOf(NeoGreen, NeoCyan)
                                        else if (isUsbCablePlugged) listOf(NeoYellow, NeoCyan)
                                        else listOf(NeoBlue, NeoCyan)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isUsbTetheringActive) Icons.Default.Usb
                                else if (isUsbCablePlugged) Icons.Default.Cable
                                else Icons.Default.Cable,
                                contentDescription = "USB Hub",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Waiting for USB connection and tethering text
                    if (isUsbTetheringActive) {
                        Text(
                            text = "USB Tethering Link Established!",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeoGreen,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Connected at line speed. Ready to stream files directly to ShareDash PC.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )
                    } else if (isUsbCablePlugged) {
                        Text(
                            text = "USB Cable Plugged In",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Waiting for USB Tethering... Turn on USB Tethering in Settings to activate maximum speed.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // One-tap Enable USB Tethering button
                        NeoButton(
                            onClick = onEnableUsbTethering,
                            cornerRadius = 16.dp,
                            accentColor = NeoGreen,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Enable USB Tethering",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Waiting for USB Connection & Tethering",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Connect a USB cable from phone to PC, then enable USB Tethering for high-speed transfers.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Open Tethering Settings
                        NeoButton(
                            onClick = onEnableUsbTethering,
                            cornerRadius = 16.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = NeoCyan,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Open Tethering Settings",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ═══════════════════════════════════════════════════════════════
            //  4. SKIP USB BUTTON (WIRELESS BYPASS)
            // ═══════════════════════════════════════════════════════════════
            NeoCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                elevation = 4.dp,
                onClick = onSkipUsb
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(NeoBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = "Wireless",
                                tint = NeoBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Skip USB",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Continue wirelessly via Wi-Fi Direct or LAN",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    NeoButton(
                        onClick = onSkipUsb,
                        cornerRadius = 12.dp
                    ) {
                        Text(
                            text = "Skip →",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeoCyan,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        } else {
            // PHONE TO PHONE MODE: WIRELESS HOTSPOT & WI-FI DIRECT
            NeoCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                elevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(NeoPurple.copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiTethering,
                            contentDescription = null,
                            tint = NeoPurple,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Direct Phone to Phone Link",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeoPurple
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Box(
                        modifier = Modifier.size(105.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        NeoInset(
                            modifier = Modifier.size(105.dp),
                            cornerRadius = 53.dp
                        ) {}

                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .scale(radarPulse)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(NeoPurple, NeoBlue)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = "Phone",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Connect to Nearby Android Phone",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Open ShareDash on both phones. Use high-speed 5GHz Direct Hotspot or Wi-Fi Direct.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Hotspot Toggle Button
                    val isHotspotActive = hotspotState is HotspotState.Active
                    NeoButton(
                        onClick = if (isHotspotActive) onStopHotspot else onStartHotspot,
                        cornerRadius = 16.dp,
                        accentColor = if (isHotspotActive) NeoGreen else NeoPurple,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiTethering,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isHotspotActive) "5GHz Hotspot Running (Tap to Stop)" else "Create 5GHz Direct Hotspot",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ═══════════════════════════════════════════════════════════════
        //  5. CONNECTED TARGET / DISCOVERED PEERS LIST
        // ═══════════════════════════════════════════════════════════════
        if (connectedPeer != null) {
            // Already Connected Banner Card
            NeoCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                elevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(NeoGreen)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Active Target Connection",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeoGreen
                            )
                        }

                        Text(
                            text = "Disconnect",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeoRed,
                            modifier = Modifier.clickable { onDisconnect() }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(NeoBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (connectedPeer.osName.contains("Windows", true)) Icons.Default.Computer else Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = NeoBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = connectedPeer.friendlyName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "${connectedPeer.ipAddress} · ${connectedPeer.supportedBridges.joinToString()}",
                                fontSize = 12.sp,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Primary Action: Pick and Send Files
                    NeoButton(
                        onClick = onPickFiles,
                        cornerRadius = 16.dp,
                        accentColor = NeoBlue,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Send Files Now",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }

        // Discovered Devices Section (if not connected, or additional nearby devices)
        if (discoveredPeers.isNotEmpty()) {
            Text(
                text = "Discovered Devices Nearby",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )

            Spacer(modifier = Modifier.height(10.dp))

            discoveredPeers.forEach { peer ->
                NeoCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    cornerRadius = 18.dp,
                    onClick = { onDeviceSelected(peer) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NeoCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (peer.osName.contains("Windows", true)) Icons.Default.Computer else Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = NeoCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = peer.friendlyName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${peer.osName} · ${if (peer.ipAddress.isNotBlank()) peer.ipAddress else "BLE Nearby"}",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        NeoButton(
                            onClick = { onDeviceSelected(peer) },
                            cornerRadius = 12.dp
                        ) {
                            Text(
                                text = "Connect",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeoCyan,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
        }

        // ═══════════════════════════════════════════════════════════════
        //  6. QUICK FILE ACTION SHORTCUTS
        // ═══════════════════════════════════════════════════════════════
        Text(
            text = "Quick Send",
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
            NeoCard(
                modifier = Modifier.weight(1f),
                cornerRadius = 18.dp,
                onClick = onPickFiles
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(NeoBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            tint = NeoBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Pick Files",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            NeoCard(
                modifier = Modifier.weight(1f),
                cornerRadius = 18.dp,
                onClick = onPickFolder
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(NeoCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = NeoCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Pick Folder",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        }

        // Padding at the bottom to ensure nothing is hidden behind the floating bottom pill bar
        Spacer(modifier = Modifier.height(85.dp))
    }
}
