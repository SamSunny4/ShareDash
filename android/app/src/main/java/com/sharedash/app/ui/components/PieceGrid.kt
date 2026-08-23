package com.sharedash.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharedash.app.model.ChunkState
import com.sharedash.app.model.ChunkVisualItem
import com.sharedash.app.ui.theme.NeoCard
import com.sharedash.app.ui.theme.NeoCardPressed
import com.sharedash.app.ui.theme.NeoCyan
import com.sharedash.app.ui.theme.NeoGreen
import com.sharedash.app.ui.theme.NeoInset
import com.sharedash.app.ui.theme.NeoLightShadow
import com.sharedash.app.ui.theme.NeoRed
import com.sharedash.app.ui.theme.NeoYellow
import com.sharedash.app.ui.theme.TextMuted
import com.sharedash.app.ui.theme.TextPrimary
import com.sharedash.app.ui.theme.TextSecondary

@Composable
fun PieceVisualizerGrid(
    chunkStates: List<ChunkVisualItem>,
    modifier: Modifier = Modifier
) {
    val totalChunks = chunkStates.size
    val completedChunks = chunkStates.count { it.state == ChunkState.COMPLETED }
    val inFlightChunks = chunkStates.count { it.state == ChunkState.IN_FLIGHT }
    val usbChunks = chunkStates.count { it.state == ChunkState.COMPLETED && (it.transportName?.contains("usb", ignoreCase = true) == true) }
    val wifiChunks = chunkStates.count { it.state == ChunkState.COMPLETED && (it.transportName?.contains("usb", ignoreCase = true) != true) }

    val transition = rememberInfiniteTransition(label = "chunkPulse")
    val inFlightPulse by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "inFlightPulse"
    )

    NeoCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        elevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "BITTORRENT PIECE MAP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "$completedChunks of $totalChunks Pieces Verified",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeoGreen.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (totalChunks > 0) "${((completedChunks * 100f) / totalChunks).toInt()}%" else "0%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeoGreen,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Recessed Sunken Canvas Grid Container
            NeoInset(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                cornerRadius = 12.dp,
                backgroundColor = NeoCardPressed
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .padding(8.dp)
                ) {
                    if (totalChunks == 0 || size.width <= 10f || size.height <= 10f) return@Canvas

                    val cols = kotlin.math.max(kotlin.math.ceil(kotlin.math.sqrt(totalChunks * 2.2)).toInt(), 16)
                    val rows = kotlin.math.max(1, kotlin.math.ceil(totalChunks.toDouble() / cols).toInt())

                    val gap = 2.5f
                    val availableW = (size.width - (cols - 1) * gap).coerceAtLeast(1f)
                    val cellW = (availableW / cols).coerceAtLeast(1f)
                    val availableH = (size.height - (rows - 1) * gap).coerceAtLeast(1f)
                    val cellH = kotlin.math.min(availableH / rows, cellW * 1.3f).coerceAtLeast(1f)

                    for (i in 0 until totalChunks) {
                        if (i >= chunkStates.size) break
                        val col = i % cols
                        val row = i / cols
                        val x = col * (cellW + gap)
                        val y = row * (cellH + gap)

                        val chunk = chunkStates[i]
                        val color = when (chunk.state) {
                            ChunkState.PENDING -> Color(0xFF1E2634)
                            ChunkState.IN_FLIGHT -> NeoYellow.copy(alpha = inFlightPulse.coerceIn(0.1f, 1.0f))
                            ChunkState.CORRUPTED -> NeoRed
                            ChunkState.COMPLETED -> {
                                val tid = chunk.transportName?.lowercase() ?: ""
                                if (tid.contains("usb")) NeoCyan else NeoGreen
                            }
                        }

                        drawRoundRect(
                            color = color,
                            topLeft = Offset(x, y),
                            size = Size(cellW, cellH),
                            cornerRadius = CornerRadius(2f, 2f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legend Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendChip(color = NeoCyan, label = "USB ($usbChunks)")
                LegendChip(color = NeoGreen, label = "Wi-Fi ($wifiChunks)")
                LegendChip(color = NeoYellow, label = "In-Flight ($inFlightChunks)")
                LegendChip(color = Color(0xFF1E2634), label = "Pending (${(totalChunks - completedChunks - inFlightChunks).coerceAtLeast(0)})")
            }
        }
    }
}

@Composable
private fun LegendChip(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = TextMuted
        )
    }
}
