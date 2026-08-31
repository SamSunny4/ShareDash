package com.sharedash.app.ui.screens

import android.content.Intent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharedash.app.model.ChunkState
import com.sharedash.app.model.SchedulerTelemetry
import com.sharedash.app.model.TransportKind
import com.sharedash.app.ui.components.PieceVisualizerGrid
import com.sharedash.app.ui.theme.NeoBg
import com.sharedash.app.ui.theme.NeoBlue
import com.sharedash.app.ui.theme.NeoButton
import com.sharedash.app.ui.theme.NeoCard
import com.sharedash.app.ui.theme.NeoCardPressed
import com.sharedash.app.ui.theme.NeoCyan
import com.sharedash.app.ui.theme.NeoDarkShadow
import com.sharedash.app.ui.theme.NeoGreen
import com.sharedash.app.ui.theme.NeoInset
import com.sharedash.app.ui.theme.NeoRed
import com.sharedash.app.ui.theme.TextMuted
import com.sharedash.app.ui.theme.TextPrimary
import com.sharedash.app.ui.theme.TextSecondary

@Composable
fun TransferScreen(
    targetName: String,
    telemetry: SchedulerTelemetry,
    onCancel: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isCompleted = telemetry.status.equals("COMPLETED", ignoreCase = true)

    // Normalize progress 0.0f .. 1.0f
    val normalizedProgress = if (telemetry.progressPct > 1.0f) {
        (telemetry.progressPct / 100f).coerceIn(0f, 1f)
    } else {
        telemetry.progressPct.coerceIn(0f, 1f)
    }

    // Determine USB vs Wi-Fi distribution ratio
    val usbTransport = telemetry.transports.find { it.kind == TransportKind.USB }
    val completedChunks = telemetry.chunkStates.filter { it.state == ChunkState.COMPLETED }
    val totalChunksCount = telemetry.chunkStates.size.coerceAtLeast(1)
    val usbCompletedCount = completedChunks.count { it.transportName?.contains("usb", ignoreCase = true) == true }

    val hasUsb = usbTransport?.isActive == true || usbCompletedCount > 0
    val usbRatio = when {
        completedChunks.isNotEmpty() -> (usbCompletedCount.toFloat() / totalChunksCount.toFloat()).coerceIn(0f, 1f)
        hasUsb -> 0.70f * normalizedProgress
        else -> 0.0f
    }
    val wifiRatio = (normalizedProgress - usbRatio).coerceAtLeast(0f)

    // Smooth continuous linear interpolation (80ms) to prevent pausing, spring bounce, or stutter
    val animatedUsbProgress by animateFloatAsState(
        targetValue = usbRatio,
        animationSpec = tween(durationMillis = 80, easing = LinearEasing),
        label = "usbProgress"
    )
    val animatedWifiProgress by animateFloatAsState(
        targetValue = wifiRatio,
        animationSpec = tween(durationMillis = 80, easing = LinearEasing),
        label = "wifiProgress"
    )
    val animatedTotalProgress by animateFloatAsState(
        targetValue = normalizedProgress,
        animationSpec = tween(durationMillis = 80, easing = LinearEasing),
        label = "totalProgress"
    )

    val speedMbS = telemetry.aggregateMbps / 8.0
    val formattedSpeed = if (speedMbS >= 1.0) "%.2f MB/s".format(speedMbS) else "%.1f KB/s".format(speedMbS * 1024.0)
    val sentMb = telemetry.completedBytes / (1024.0 * 1024.0)
    val totalMb = telemetry.totalBytes / (1024.0 * 1024.0)

    // Track peak speed observed throughout transfer
    var peakSpeedMbS by remember { mutableDoubleStateOf(0.0) }
    if (speedMbS > peakSpeedMbS) {
        peakSpeedMbS = speedMbS
    }

    // Precise 2-decimal floating point percentage
    val pctFloat = (normalizedProgress * 100f).coerceIn(0f, 100f)
    val formattedPct = if (isCompleted) "100.00%" else "%.2f%%".format(pctFloat)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NeoBg)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ═══════════════════════════════════════════════════════════════
        //  TOP BAR: TRANSFER HEADER & CANCEL BUTTON
        // ═══════════════════════════════════════════════════════════════
        NeoCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            elevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isCompleted) NeoGreen.copy(alpha = 0.15f) else NeoBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = if (isCompleted) NeoGreen else NeoBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = telemetry.title.ifEmpty { "File Transfer" },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isCompleted) "Transfer Completed" else "Streaming to $targetName",
                            fontSize = 12.sp,
                            color = if (isCompleted) NeoGreen else TextSecondary
                        )
                    }
                }

                if (!isCompleted) {
                    NeoButton(
                        onClick = onCancel,
                        cornerRadius = 12.dp,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel Transfer",
                            tint = NeoRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ═══════════════════════════════════════════════════════════════
        //  NEOMORPHIC DUAL-ARC CIRCULAR PROGRESS DASHBOARD
        // ═══════════════════════════════════════════════════════════════
        Box(
            modifier = Modifier.size(220.dp),
            contentAlignment = Alignment.Center
        ) {
            // Inset Background Well
            NeoInset(
                modifier = Modifier.size(210.dp),
                cornerRadius = 105.dp
            ) {}

            // Dual-Colored Circular Progress Arcs
            Canvas(modifier = Modifier.size(180.dp)) {
                val strokeW = 14.dp.toPx()

                // Background Track
                drawArc(
                    color = NeoDarkShadow.copy(alpha = 0.6f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )

                if (isCompleted) {
                    // Full Completed Verified Arc
                    drawArc(
                        brush = Brush.sweepGradient(listOf(NeoGreen, NeoCyan, NeoGreen)),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                } else {
                    // Arc 1: USB Fast-Path (Cyan)
                    if (animatedUsbProgress > 0.001f) {
                        drawArc(
                            color = NeoCyan,
                            startAngle = -90f,
                            sweepAngle = animatedUsbProgress * 360f,
                            useCenter = false,
                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )
                    }

                    // Arc 2: 5GHz Wi-Fi (Green)
                    if (animatedWifiProgress > 0.001f) {
                        drawArc(
                            color = NeoGreen,
                            startAngle = -90f + (animatedUsbProgress * 360f),
                            sweepAngle = animatedWifiProgress * 360f,
                            useCenter = false,
                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )
                    }
                }
            }

            // Central Value & Speed Display
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = formattedPct,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Speed Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isCompleted) NeoGreen.copy(alpha = 0.15f) else NeoCyan.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isCompleted) "Verified 100%" else formattedSpeed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCompleted) NeoGreen else NeoCyan
                    )
                }

                if (!isCompleted && telemetry.etaSeconds > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ETA: ${telemetry.etaSeconds}s",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ═══════════════════════════════════════════════════════════════
        //  ESSENTIAL METRICS CARD (PEAK SPEED, TOTAL PAYLOAD, RATIO)
        // ═══════════════════════════════════════════════════════════════
        NeoCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 18.dp,
            elevation = 5.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Row 1: Peak Speed & Total Transferred
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Peak Speed Tile
                    NeoCard(
                        modifier = Modifier.weight(1f),
                        cornerRadius = 14.dp,
                        backgroundColor = NeoCardPressed
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(text = "Peak Speed", fontSize = 11.sp, color = TextMuted)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (peakSpeedMbS >= 1.0) "%.2f MB/s".format(peakSpeedMbS) else "%.1f KB/s".format(peakSpeedMbS * 1024.0),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeoCyan
                            )
                        }
                    }

                    // Total Transferred Tile
                    NeoCard(
                        modifier = Modifier.weight(1f),
                        cornerRadius = 14.dp,
                        backgroundColor = NeoCardPressed
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(text = "Total Payload", fontSize = 11.sp, color = TextMuted)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "%.2f / %.2f MB".format(sentMb, totalMb.coerceAtLeast(sentMb)),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Row 2: Transport Multipath Ratio
                val currentNorm = normalizedProgress.coerceAtLeast(0.001f)
                val usbPct = (usbRatio / currentNorm * 100f).coerceIn(0f, 100f)
                val wifiPct = (wifiRatio / currentNorm * 100f).coerceIn(0f, 100f)
                val ratioText = when {
                    hasUsb && wifiRatio > 0.001f -> "USB %.1f%% · Wi-Fi %.1f%%".format(usbPct, wifiPct)
                    hasUsb -> "100% USB Fast-Path"
                    else -> "100% 5GHz Wi-Fi"
                }

                NeoCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 14.dp,
                    backgroundColor = NeoCardPressed
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (hasUsb) Icons.Default.Usb else Icons.Default.Wifi,
                                contentDescription = null,
                                tint = if (hasUsb) NeoCyan else NeoGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Link Distribution", fontSize = 12.sp, color = TextMuted)
                        }
                        Text(
                            text = ratioText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasUsb) NeoCyan else NeoGreen
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ═══════════════════════════════════════════════════════════════
        //  BITTORRENT-STYLE PIECE MAP VISUALIZER
        // ═══════════════════════════════════════════════════════════════
        PieceVisualizerGrid(chunkStates = telemetry.chunkStates)

        Spacer(modifier = Modifier.height(18.dp))

        // ═══════════════════════════════════════════════════════════════
        //  POST-TRANSFER ACTIONS (WHEN COMPLETE)
        // ═══════════════════════════════════════════════════════════════
        if (isCompleted) {
            // Open Downloads Button
            NeoButton(
                onClick = {
                    try {
                        val intent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                },
                cornerRadius = 14.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = NeoCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Open in Downloads / ShareDash",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            NeoButton(
                onClick = onFinish,
                cornerRadius = 18.dp,
                accentColor = NeoGreen,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Done — File Saved",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
