package com.sharedash.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Language
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
import com.sharedash.app.ui.theme.BgSurfaceElevated
import com.sharedash.app.ui.theme.LanSky
import com.sharedash.app.ui.theme.QuicPurple
import com.sharedash.app.ui.theme.TextMuted
import com.sharedash.app.ui.theme.TextPrimary
import com.sharedash.app.ui.theme.TextSecondary
import com.sharedash.app.ui.theme.UsbTeal
import com.sharedash.app.ui.theme.WifiGreen

@Composable
fun SmartBridgeStrip(
    isUsbConnected: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BgSurfaceElevated, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MULTIPATH BRIDGES & BOOSTERS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 0.5.sp
            )

            Text(
                text = if (isUsbConnected) "🚀 Turbo Multipath Active" else "💡 Plug in USB-C for Turbo Boost",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isUsbConnected) WifiGreen else LanSky
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BridgeBadge(
                title = "USB 3.x Cable",
                sub = if (isUsbConnected) "3.2 Gbps Ready" else "Plug in USB-C",
                icon = Icons.Default.Cable,
                color = UsbTeal,
                isActive = isUsbConnected,
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
                title = "Local Wi-Fi",
                sub = "650 Mbps",
                icon = Icons.Default.Router,
                color = LanSky,
                isActive = true,
                modifier = Modifier.weight(1f)
            )
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                if (isActive) color.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f),
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                if (isActive) color.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(8.dp)
            )
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
