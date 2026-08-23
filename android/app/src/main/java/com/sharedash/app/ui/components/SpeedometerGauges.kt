package com.sharedash.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharedash.app.model.TransportStats
import com.sharedash.app.ui.theme.AccentBlue
import com.sharedash.app.ui.theme.AccentCyan
import com.sharedash.app.ui.theme.BgSurfaceElevated
import com.sharedash.app.ui.theme.LanSky
import com.sharedash.app.ui.theme.QuicPurple
import com.sharedash.app.ui.theme.TextMuted
import com.sharedash.app.ui.theme.TextPrimary
import com.sharedash.app.ui.theme.TextSecondary
import com.sharedash.app.ui.theme.UsbTeal
import com.sharedash.app.ui.theme.WifiGreen

@Composable
fun MultipathSpeedometer(
    aggregateMbps: Double,
    progressPct: Float,
    completedBytes: Long,
    totalBytes: Long,
    etaSeconds: Long,
    transports: List<TransportStats>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BgSurfaceElevated, RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        // Aggregate Bandwidth Dial Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AGGREGATE BANDWIDTH",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 0.5.sp
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "%.1f".format(aggregateMbps),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "MB/s",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "~%.2f Gbps".format((aggregateMbps * 8) / 1000.0),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = WifiGreen,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = if (etaSeconds > 0) "ETA: ${etaSeconds}s" else "Complete",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Progress Bar
        LinearProgressIndicator(
            progress = { progressPct / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = AccentBlue,
            trackColor = Color.White.copy(alpha = 0.08f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "%.1f MB / %.1f MB (%.0f%%)".format(
                    completedBytes / (1024f * 1024f),
                    totalBytes / (1024f * 1024f),
                    progressPct
                ),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Per-Transport Breakdown Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            transports.forEach { t ->
                val color = when {
                    t.name.contains("USB", true) -> UsbTeal
                    t.name.contains("Direct", true) || t.name.contains("Hotspot", true) -> WifiGreen
                    else -> LanSky
                }

                TransportBox(
                    name = t.name,
                    speedMbps = t.currentMbps,
                    rttMs = t.rttMs,
                    color = color,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TransportBox(
    name: String,
    speedMbps: Double,
    rttMs: Double,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Text(
            text = name,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "%.1f MB/s".format(speedMbps),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "%.1f ms".format(rttMs),
            fontSize = 10.sp,
            color = TextMuted,
            fontFamily = FontFamily.Monospace
        )
    }
}
