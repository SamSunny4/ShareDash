package com.sharedash.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharedash.app.model.DiscoveredPeer
import com.sharedash.app.ui.theme.AccentBlue
import com.sharedash.app.ui.theme.AccentCyan
import com.sharedash.app.ui.theme.BgSurfaceElevated
import com.sharedash.app.ui.theme.TextMuted
import com.sharedash.app.ui.theme.TextPrimary
import com.sharedash.app.ui.theme.TextSecondary
import com.sharedash.app.ui.theme.WifiGreen

@Composable
fun QuickShareRadar(
    discoveredPeers: List<DiscoveredPeer>,
    onDeviceSelected: (DiscoveredPeer) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val pulseProgress1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Pulse1"
    )
    val pulseProgress2 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, delayMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Pulse2"
    )

    Box(
        modifier = modifier.size(340.dp),
        contentAlignment = Alignment.Center
    ) {
        // Radar Ripple Rings
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = this.center
            val maxRadius = size.minDimension / 2f

            // Ripple 1
            drawCircle(
                color = AccentBlue.copy(alpha = (1f - pulseProgress1) * 0.4f),
                radius = maxRadius * pulseProgress1,
                center = center,
                style = Stroke(width = 2f)
            )

            // Ripple 2
            drawCircle(
                color = AccentCyan.copy(alpha = (1f - pulseProgress2) * 0.4f),
                radius = maxRadius * pulseProgress2,
                center = center,
                style = Stroke(width = 2f)
            )

            // Base static ring
            drawCircle(
                color = Color.White.copy(alpha = 0.05f),
                radius = maxRadius * 0.9f,
                center = center,
                style = Stroke(width = 1.5f)
            )
        }

        // Center Avatar (This Android Phone)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(AccentBlue, AccentCyan)
                        )
                    )
                    .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = "This Device",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "This Phone",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Orbiting Discovered Device Nodes
        val offsets = listOf(
            Pair(-100.dp, -75.dp),
            Pair(95.dp, -60.dp),
            Pair(-85.dp, 80.dp),
            Pair(90.dp, 75.dp)
        )

        discoveredPeers.forEachIndexed { index, peer ->
            val offset = offsets.getOrElse(index) { Pair(0.dp, 0.dp) }
            val isPc = peer.osName.contains("Windows") || peer.friendlyName.contains("PC")

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .offset(x = offset.first, y = offset.second)
                    .clickable { onDeviceSelected(peer) }
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(BgSurfaceElevated)
                        .border(1.5.dp, AccentBlue.copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPc) Icons.Default.Computer else Icons.Default.PhoneAndroid,
                        contentDescription = peer.friendlyName,
                        tint = if (isPc) AccentCyan else TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )

                    // Online green dot
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(WifiGreen)
                            .align(Alignment.BottomEnd)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = peer.friendlyName,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
