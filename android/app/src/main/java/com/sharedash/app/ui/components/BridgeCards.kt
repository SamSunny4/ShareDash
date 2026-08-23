package com.sharedash.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharedash.app.ui.theme.AccentBlue
import com.sharedash.app.ui.theme.BgSurface
import com.sharedash.app.ui.theme.BgSurfaceElevated
import com.sharedash.app.ui.theme.BorderSubtle
import com.sharedash.app.ui.theme.InFlightYellow
import com.sharedash.app.ui.theme.LanSky
import com.sharedash.app.ui.theme.TextMuted
import com.sharedash.app.ui.theme.TextPrimary
import com.sharedash.app.ui.theme.TextSecondary
import com.sharedash.app.ui.theme.UsbTeal
import com.sharedash.app.ui.theme.WifiGreen

@Composable
fun SmartBridgeStrip(
    isUsbConnected: Boolean = false,
    wifiSpeedMbps: Int = 866,
    onOpenUsbPrompt: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BgSurfaceElevated, RoundedCornerShape(14.dp))
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NetworkCheck,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "HARDWARE BRIDGES & BOOSTERS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp
                )
            }

            Text(
                text = if (isUsbConnected) "Turbo Active" else "Turbo Boost",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isUsbConnected) WifiGreen else InFlightYellow,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isUsbConnected) WifiGreen.copy(alpha = 0.15f) else InFlightYellow.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .clickable { onOpenUsbPrompt() }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BridgeBadge(
                title = "USB 3.2",
                sub = if (isUsbConnected) "3.2 Gbps Ready" else "Plug in cable",
                icon = Icons.Default.Cable,
                color = UsbTeal,
                isActive = isUsbConnected,
                onClick = onOpenUsbPrompt,
                modifier = Modifier.weight(1f)
            )

            BridgeBadge(
                title = "Wi-Fi Direct",
                sub = "1.2 Gbps P2P",
                icon = Icons.Default.Wifi,
                color = WifiGreen,
                isActive = true,
                modifier = Modifier.weight(1f)
            )

            BridgeBadge(
                title = "Wi-Fi LAN",
                sub = "$wifiSpeedMbps Mbps",
                icon = Icons.Default.Router,
                color = LanSky,
                isActive = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun TransportRecommendationCard(
    isUsbConnected: Boolean = false,
    onOpenUsbPrompt: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BgSurface, RoundedCornerShape(14.dp))
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.ElectricBolt,
                contentDescription = null,
                tint = InFlightYellow,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "TRANSPORT RECOMMENDATIONS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            RecommendationRow(
                rank = "#1",
                title = "USB 3.2 Fast-Path",
                speed = "3.2 Gbps",
                desc = if (isUsbConnected) "Active & Aggregated" else "Plug in USB-C cable for max wire speed",
                isActive = isUsbConnected,
                color = UsbTeal,
                onClick = if (!isUsbConnected) onOpenUsbPrompt else null
            )

            RecommendationRow(
                rank = "#2",
                title = "Wi-Fi 6 Direct P2P",
                speed = "5GHz · ~1.2 Gbps",
                desc = "Direct device-to-device wireless tunnel",
                isActive = true,
                color = WifiGreen
            )

            RecommendationRow(
                rank = "#3",
                title = "Local Subnet Wi-Fi",
                speed = "650 Mbps",
                desc = "Standard LAN router routing",
                isActive = true,
                color = LanSky
            )
        }
    }
}

@Composable
private fun RecommendationRow(
    rank: String,
    title: String,
    speed: String,
    desc: String,
    isActive: Boolean,
    color: Color,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) color.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.03f))
            .border(1.dp, if (isActive) color.copy(alpha = 0.25f) else Color.Transparent, RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Text(text = rank, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) TextPrimary else TextSecondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isActive) color.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = speed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) color else TextMuted
                        )
                    }
                }
                Text(
                    text = desc,
                    fontSize = 10.sp,
                    color = if (isActive) color else TextMuted
                )
            }
        }
    }
}

@Composable
private fun BridgeBadge(
    title: String,
    sub: String,
    icon: ImageVector,
    color: Color,
    isActive: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isActive) color.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f),
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                if (isActive) color.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(8.dp)
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (isActive) color else TextMuted,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) TextPrimary else TextMuted
            )
            Text(
                text = sub,
                fontSize = 9.sp,
                color = if (isActive) color else TextMuted
            )
        }
    }
}
