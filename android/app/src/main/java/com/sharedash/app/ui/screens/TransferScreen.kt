package com.sharedash.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharedash.app.model.SchedulerTelemetry
import com.sharedash.app.ui.components.MultipathSpeedometer
import com.sharedash.app.ui.components.PieceVisualizerGrid
import com.sharedash.app.ui.theme.AccentBlue
import com.sharedash.app.ui.theme.BgApp
import com.sharedash.app.ui.theme.BgSurface
import com.sharedash.app.ui.theme.DangerRed
import com.sharedash.app.ui.theme.TextMuted
import com.sharedash.app.ui.theme.TextPrimary
import com.sharedash.app.ui.theme.TextSecondary
import com.sharedash.app.ui.theme.WifiGreen

@Composable
fun TransferScreen(
    targetName: String,
    telemetry: SchedulerTelemetry,
    onCancel: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCompleted = telemetry.status.equals("COMPLETED", ignoreCase = true)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgApp)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Transfer Header Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgSurface, RoundedCornerShape(14.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(AccentBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Computer,
                        contentDescription = "Target PC",
                        tint = AccentBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = targetName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = telemetry.title,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            if (!isCompleted) {
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = DangerRed
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Real-Time Speedometer & Transports Breakdown
        MultipathSpeedometer(
            aggregateMbps = telemetry.aggregateMbps,
            progressPct = telemetry.progressPct,
            completedBytes = telemetry.completedBytes,
            totalBytes = telemetry.totalBytes,
            etaSeconds = telemetry.etaSeconds,
            transports = telemetry.transports
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Dynamic Chunk Piece Grid
        PieceVisualizerGrid(
            chunkStates = telemetry.chunkStates
        )

        Spacer(modifier = Modifier.weight(1f))

        // Completion Card / Action
        if (isCompleted) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WifiGreen.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Done",
                        tint = WifiGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Transfer Complete! All chunks verified.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = WifiGreen
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "📁 Saved to: Internal Storage > Download > ShareDash",
                    fontSize = 11.sp,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onFinish,
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    }
}
