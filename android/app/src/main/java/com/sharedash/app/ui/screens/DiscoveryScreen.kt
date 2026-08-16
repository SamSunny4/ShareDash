package com.sharedash.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharedash.app.model.DiscoveredPeer
import com.sharedash.app.ui.components.QuickShareRadar
import com.sharedash.app.ui.theme.AccentBlue
import com.sharedash.app.ui.theme.BgApp
import com.sharedash.app.ui.theme.BgSurface
import com.sharedash.app.ui.theme.BgSurfaceElevated
import com.sharedash.app.ui.theme.DangerRed
import com.sharedash.app.ui.theme.InFlightYellow
import com.sharedash.app.ui.theme.TextMuted
import com.sharedash.app.ui.theme.TextPrimary
import com.sharedash.app.ui.theme.TextSecondary
import com.sharedash.app.ui.theme.WifiGreen

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
    onEnableBluetooth: () -> Unit = {},
    onEnableWifi: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgApp)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgSurface, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AccentBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Logo",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "ShareDash",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(WifiGreen)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Visible nearby",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenDownloadsFolder) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Received Files",
                        tint = TextPrimary
                    )
                }
                IconButton(onClick = onOpenPairing) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan QR",
                        tint = TextPrimary
                    )
                }
            }
        }

        // Radio Warning Banners if Bluetooth or Wi-Fi is Off
        if (!isWifiEnabled || !isBluetoothEnabled) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(InFlightYellow.copy(alpha = 0.12f))
                    .border(1.dp, InFlightYellow.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = InFlightYellow, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (!isWifiEnabled && !isBluetoothEnabled) "Wi-Fi & Bluetooth are off" else if (!isWifiEnabled) "Wi-Fi is turned off" else "Bluetooth is turned off",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = InFlightYellow
                    )
                }

                if (!isWifiEnabled) {
                    TextButton(onClick = onEnableWifi) {
                        Text("Enable Wi-Fi", fontSize = 11.sp, color = AccentBlue, fontWeight = FontWeight.Bold)
                    }
                } else if (!isBluetoothEnabled) {
                    TextButton(onClick = onEnableBluetooth) {
                        Text("Enable Bluetooth", fontSize = 11.sp, color = AccentBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Pulsing Quick Share Radar Discovery
        QuickShareRadar(
            discoveredPeers = discoveredPeers,
            onDeviceSelected = onDeviceSelected
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (connectedPeer == null) {
            Text(
                text = "Looking for nearby devices...",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Text(
                text = "Tap a device on the radar above to establish a secure connection",
                fontSize = 12.sp,
                color = TextMuted
            )
        } else {
            // Secure Connection Banner & Post-Connection Tunnels
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WifiGreen.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    .border(1.dp, WifiGreen.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Secure",
                        tint = WifiGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Securely Connected to ${connectedPeer.friendlyName}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = WifiGreen
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "🔒 AES-256-GCM Encrypted",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    TextButton(onClick = onDisconnect) {
                        Text("Disconnect", color = DangerRed, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Post-Connection Tunnels & Booster Suggestions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(AccentBlue.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("📶 Wi-Fi LAN Active", fontSize = 10.sp, color = AccentBlue, fontWeight = FontWeight.Bold)
                    }

                    if (isUsbConnected) {
                        Box(
                            modifier = Modifier
                                .background(WifiGreen.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("⚡ USB 3.2 Active", fontSize = 10.sp, color = WifiGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (!isUsbConnected) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "💡 Tip: Plug in USB cable to enable 3.2 Gbps wire-speed turbo tunnel!",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons (Only Enabled After Secure Connection)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onPickFiles,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Select Files", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onPickFolder,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Select Folder", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
