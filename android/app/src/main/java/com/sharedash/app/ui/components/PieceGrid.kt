package com.sharedash.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharedash.app.model.ChunkState
import com.sharedash.app.model.ChunkVisualItem
import com.sharedash.app.ui.theme.BgApp
import com.sharedash.app.ui.theme.BgSurfaceElevated
import com.sharedash.app.ui.theme.DangerRed
import com.sharedash.app.ui.theme.InFlightYellow
import com.sharedash.app.ui.theme.LanSky
import com.sharedash.app.ui.theme.QuicPurple
import com.sharedash.app.ui.theme.TextMuted
import com.sharedash.app.ui.theme.TextPrimary
import com.sharedash.app.ui.theme.TextSecondary
import com.sharedash.app.ui.theme.UsbTeal
import com.sharedash.app.ui.theme.WifiGreen

@Composable
fun PieceVisualizerGrid(
    chunkStates: List<ChunkVisualItem>,
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DYNAMIC CHUNK PIECE GRID",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${chunkStates.size} Chunks",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Canvas Piece Grid
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .background(BgApp, RoundedCornerShape(6.dp))
                .padding(4.dp)
        ) {
            val total = chunkStates.size
            if (total == 0) return@Canvas

            val cols = kotlin.math.max(kotlin.math.ceil(kotlin.math.sqrt(total * 2.0)).toInt(), 12)
            val rows = kotlin.math.ceil(total.toDouble() / cols).toInt()

            val padding = 2f
            val cellW = (size.width - (cols + 1) * padding) / cols
            val cellH = kotlin.math.min((size.height - (rows + 1) * padding) / rows, cellW)

            for (i in 0 until total) {
                val col = i % cols
                val row = i / cols
                val x = padding + col * (cellW + padding)
                val y = padding + row * (cellH + padding)

                val chunk = chunkStates[i]
                val color = when (chunk.state) {
                    ChunkState.PENDING -> Color(0xFF243044)
                    ChunkState.IN_FLIGHT -> InFlightYellow
                    ChunkState.CORRUPTED -> DangerRed
                    ChunkState.COMPLETED -> {
                        val tid = chunk.transportName?.lowercase() ?: ""
                        when {
                            tid.contains("usb") -> UsbTeal
                            tid.contains("direct") || tid.contains("wifi") -> WifiGreen
                            tid.contains("lan") -> LanSky
                            else -> WifiGreen
                        }
                    }
                }

                drawRect(
                    color = color,
                    topLeft = Offset(x, y),
                    size = Size(cellW, cellH)
                )
            }
        }
    }
}
