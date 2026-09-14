package com.sharedash.app.ui.screens

import android.content.Intent
import android.os.Build
import android.widget.Toast
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
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharedash.app.DeviceIdentity
import com.sharedash.app.storage.SettingsManager
import com.sharedash.app.ui.theme.NeoBg
import com.sharedash.app.ui.theme.NeoBlue
import com.sharedash.app.ui.theme.NeoButton
import com.sharedash.app.ui.theme.NeoCard
import com.sharedash.app.ui.theme.NeoCardPressed
import com.sharedash.app.ui.theme.NeoCyan
import com.sharedash.app.ui.theme.NeoGreen
import com.sharedash.app.ui.theme.NeoInset
import com.sharedash.app.ui.theme.NeoPurple
import com.sharedash.app.ui.theme.NeoRed
import com.sharedash.app.ui.theme.NeoYellow
import com.sharedash.app.ui.theme.TextMuted
import com.sharedash.app.ui.theme.TextPrimary
import com.sharedash.app.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    deviceName: String,
    onDeviceNameChange: (String) -> Unit,
    connectionMode: String,
    onConnectionModeChange: (String) -> Unit,
    preferUsb: Boolean,
    onPreferUsbChange: (Boolean) -> Unit,
    prefer5GHz: Boolean,
    onPrefer5GHzChange: (Boolean) -> Unit,
    serverPort: Int,
    localIpAddresses: List<String>,
    isUsbCablePlugged: Boolean,
    isUsbTetheringActive: Boolean,
    onOpenDownloadsFolder: () -> Unit,
    onOpenTetheringSettings: () -> Unit,
    themeMode: String = com.sharedash.app.storage.SettingsManager.THEME_SYSTEM,
    onThemeModeChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showEditNameDialog by remember { mutableStateOf(false) }
    var tempDeviceName by remember { mutableStateOf(deviceName) }

    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            containerColor = NeoCard,
            title = {
                Text(
                    text = "Edit Device Name",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "This name will be visible to nearby ShareDash PCs and phones during discovery.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempDeviceName,
                        onValueChange = { tempDeviceName = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeoCyan,
                            unfocusedBorderColor = TextMuted,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEditNameDialog = false
                        if (tempDeviceName.isNotBlank()) {
                            onDeviceNameChange(tempDeviceName.trim())
                            Toast.makeText(context, "Device name updated", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Save", color = NeoCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NeoBg)
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 12.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ═══════════════════════════════════════════════════════════════
        //  1. TOP BAR
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
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Settings",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Preferences & Diagnostics",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ═══════════════════════════════════════════════════════════════
        //  2. DEVICE PROFILE CARD
        // ═══════════════════════════════════════════════════════════════
        Text(
            text = "Device Profile",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )

        Spacer(modifier = Modifier.height(8.dp))

        NeoCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            elevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Device Friendly Name",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                        Text(
                            text = deviceName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    NeoButton(
                        onClick = {
                            tempDeviceName = deviceName
                            showEditNameDialog = true
                        },
                        cornerRadius = 12.dp,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Name",
                            tint = NeoCyan,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Device ID",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                    Text(
                        text = DeviceIdentity.id,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Android OS",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                    Text(
                        text = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ═══════════════════════════════════════════════════════════════
        //  2b. APPEARANCE / THEME PREFERENCES
        // ═══════════════════════════════════════════════════════════════
        Text(
            text = "Appearance & Visual Theme",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )

        Spacer(modifier = Modifier.height(8.dp))

        NeoCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            elevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "App Theme",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = "Select your preferred light or dark visual style",
                    fontSize = 11.sp,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val themeOptions = listOf(
                        Triple(com.sharedash.app.storage.SettingsManager.THEME_SYSTEM, "System", Icons.Default.BrightnessMedium),
                        Triple(com.sharedash.app.storage.SettingsManager.THEME_LIGHT, "Light", Icons.Default.LightMode),
                        Triple(com.sharedash.app.storage.SettingsManager.THEME_DARK, "Dark", Icons.Default.DarkMode)
                    )

                    themeOptions.forEach { (mode, label, icon) ->
                        val isSelected = themeMode == mode
                        NeoCard(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onThemeModeChange(mode) },
                            cornerRadius = 14.dp,
                            backgroundColor = if (isSelected) NeoBlue.copy(alpha = 0.2f) else NeoCardPressed,
                            borderColor = if (isSelected) NeoBlue else Color.Transparent,
                            elevation = if (isSelected) 4.dp else 1.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (isSelected) NeoBlue else TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) NeoBlue else TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ═══════════════════════════════════════════════════════════════
        //  3. CONNECTION PREFERENCES
        // ═══════════════════════════════════════════════════════════════
        Text(
            text = "Connection & Transfer Preferences",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )

        Spacer(modifier = Modifier.height(8.dp))

        NeoCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            elevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Default Mode Segment
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Default Mode",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (connectionMode == SettingsManager.MODE_PHONE_TO_PC) "Phone to PC (Windows)" else "Phone to Phone (Android)",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }

                    NeoButton(
                        onClick = {
                            val newMode = if (connectionMode == SettingsManager.MODE_PHONE_TO_PC) SettingsManager.MODE_PHONE_TO_PHONE else SettingsManager.MODE_PHONE_TO_PC
                            onConnectionModeChange(newMode)
                        },
                        cornerRadius = 12.dp
                    ) {
                        Text(
                            text = "Switch",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeoCyan,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // USB Fast-Path Priority Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Prioritize USB Fast-Path",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Auto-connects to USB cable tethering when available",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }

                    Switch(
                        checked = preferUsb,
                        onCheckedChange = onPreferUsbChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = NeoGreen,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = NeoCardPressed
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 5GHz Band Preferred Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Prefer 5GHz Wi-Fi Band",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Forces high-bandwidth 5GHz direct hotspots for wireless speed",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }

                    Switch(
                        checked = prefer5GHz,
                        onCheckedChange = onPrefer5GHzChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = NeoCyan,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = NeoCardPressed
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "HTTP Transfer Port",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "$serverPort",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeoCyan
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ═══════════════════════════════════════════════════════════════
        //  4. STORAGE & DOWNLOADS
        // ═══════════════════════════════════════════════════════════════
        Text(
            text = "Storage & Downloads",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )

        Spacer(modifier = Modifier.height(8.dp))

        NeoCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            elevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Download Location",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Internal Storage / Downloads / ShareDash",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }

                    NeoButton(
                        onClick = onOpenDownloadsFolder,
                        cornerRadius = 12.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = NeoCyan,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Open",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeoCyan
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ═══════════════════════════════════════════════════════════════
        //  5. LIVE NETWORK DIAGNOSTICS
        // ═══════════════════════════════════════════════════════════════
        Text(
            text = "Live Network Diagnostics",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )

        Spacer(modifier = Modifier.height(8.dp))

        NeoCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            elevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // USB Status
                DiagnosticsItem(
                    title = "USB Cable Connection",
                    value = if (isUsbCablePlugged) "Plugged In" else "Disconnected",
                    isGood = isUsbCablePlugged
                )

                DiagnosticsItem(
                    title = "USB Tethering Fast-Path",
                    value = if (isUsbTetheringActive) "Active" else "Inactive",
                    isGood = isUsbTetheringActive
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Assigned Local IP Addresses:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))

                if (localIpAddresses.isEmpty()) {
                    Text(
                        text = "None detected (No active network interface)",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                } else {
                    localIpAddresses.forEach { ip ->
                        val label = when {
                            ip.startsWith("192.168.42.") -> "USB Tethering"
                            ip.startsWith("192.168.43.") -> "Phone Hotspot"
                            ip.startsWith("192.168.49.") -> "Wi-Fi Direct"
                            ip.startsWith("192.168.137.") -> "PC Hotspot Gateway"
                            else -> "Wi-Fi / LAN"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$label:",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                            Text(
                                text = ip,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = NeoCyan
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                NeoButton(
                    onClick = onOpenTetheringSettings,
                    cornerRadius = 14.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Usb,
                            contentDescription = null,
                            tint = NeoCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Open Tethering & Network Settings",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ═══════════════════════════════════════════════════════════════
        //  6. ABOUT
        // ═══════════════════════════════════════════════════════════════
        NeoCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            elevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ShareDash Mobile v1.0.0",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Multipath USB 3.x + 5GHz Wi-Fi High-Throughput Transfer Engine.\nZero cloud telemetry, peer-to-peer authenticated.",
                    fontSize = 11.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        }

        // Bottom padding for floating bottom pill bar
        Spacer(modifier = Modifier.height(85.dp))
    }
}

@Composable
private fun DiagnosticsItem(
    title: String,
    value: String,
    isGood: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            color = TextMuted
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (isGood) NeoGreen else NeoRed)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isGood) NeoGreen else TextSecondary
            )
        }
    }
}
