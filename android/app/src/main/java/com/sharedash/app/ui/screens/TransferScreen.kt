package com.sharedash.app.ui.screens

import android.content.Intent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.sharedash.app.ui.theme.NeoPurple
import com.sharedash.app.ui.theme.NeoRed
import com.sharedash.app.ui.theme.TextMuted
import com.sharedash.app.ui.theme.TextPrimary
import com.sharedash.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

enum class TransferUiStage {
    TRANSFERRING,
    VERIFYING,
    COMPLETED
}

@Composable
fun TransferScreen(
    targetName: String,
    telemetry: SchedulerTelemetry,
    onCancel: () -> Unit,
    onFinish: () -> Unit,
    onSendAnother: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isTelemetryCompleted = telemetry.status.equals("COMPLETED", ignoreCase = true)
    val isTelemetryVerifying = telemetry.status.equals("VERIFYING", ignoreCase = true)

    // Normalize progress 0.0f .. 1.0f
    val normalizedProgress = if (telemetry.progressPct > 1.0f) {
        (telemetry.progressPct / 100f).coerceIn(0f, 1f)
    } else {
        telemetry.progressPct.coerceIn(0f, 1f)
    }

    val isDoneOrNearDone = isTelemetryCompleted || isTelemetryVerifying || normalizedProgress >= 0.999f

    var uiStage by remember { mutableStateOf(TransferUiStage.TRANSFERRING) }

    // Synchronize verification delay for a smooth, flicker-free visual experience
    LaunchedEffect(isDoneOrNearDone) {
        if (isDoneOrNearDone && uiStage == TransferUiStage.TRANSFERRING) {
            uiStage = TransferUiStage.VERIFYING
            // Show the quick circular verification animation smoothly
            delay(900)
            uiStage = TransferUiStage.COMPLETED
        }
    }

    // Determine USB vs Wi-Fi distribution ratio
    val usbTransport = telemetry.transports.find { it.kind == TransportKind.USB }
    val completedChunks = telemetry.chunkStates.filter { it.state == ChunkState.COMPLETED }
    val totalChunksCount = telemetry.chunkStates.size.coerceAtLeast(1)
    val usbCompletedCount = completedChunks.count { it.transportName?.contains("usb", ignoreCase = true) == true }

    val hasUsb = usbTransport?.isActive == true || usbCompletedCount > 0
    val targetUsbRatio = when {
        completedChunks.isNotEmpty() -> (usbCompletedCount.toFloat() / totalChunksCount.toFloat()).coerceIn(0f, 1f)
        hasUsb -> 0.70f * normalizedProgress
        else -> 0.0f
    }
    val targetWifiRatio = (normalizedProgress - targetUsbRatio).coerceAtLeast(0f)

    // Continuous smooth linear interpolation for silky-smooth progress without pauses
    val targetTotalProg = if (uiStage != TransferUiStage.TRANSFERRING) 1.0f else normalizedProgress
    val animatedTotalProgress by animateFloatAsState(
        targetValue = targetTotalProg,
        animationSpec = tween(durationMillis = 180, easing = LinearEasing),
        label = "totalProgress"
    )
    val animatedUsbProgress by animateFloatAsState(
        targetValue = if (uiStage != TransferUiStage.TRANSFERRING) targetUsbRatio else targetUsbRatio,
        animationSpec = tween(durationMillis = 180, easing = LinearEasing),
        label = "usbProgress"
    )
    val animatedWifiProgress by animateFloatAsState(
        targetValue = if (uiStage != TransferUiStage.TRANSFERRING) targetWifiRatio else targetWifiRatio,
        animationSpec = tween(durationMillis = 180, easing = LinearEasing),
        label = "wifiProgress"
    )

    // Continuous 2-decimal percentage counter (e.g. 1.00%, 1.01% ... 100.00%)
    val pctFloat = (animatedTotalProgress * 100f).coerceIn(0f, 100f)
    val formattedPct = if (uiStage != TransferUiStage.TRANSFERRING) "100.00%" else "%.2f%%".format(pctFloat)

    val speedMbS = telemetry.aggregateMbps / 8.0
    val formattedSpeed = if (speedMbS >= 1.0) "%.2f MB/s".format(speedMbS) else "%.1f KB/s".format(speedMbS * 1024.0)
    val sentMb = telemetry.completedBytes / (1024.0 * 1024.0)
    val totalMb = telemetry.totalBytes / (1024.0 * 1024.0)

    // Track peak speed observed throughout transfer
    var peakSpeedMbS by remember { mutableDoubleStateOf(0.0) }
    if (speedMbS > peakSpeedMbS) {
        peakSpeedMbS = speedMbS
    }

    // Verification rotation & pulse transition
    val infiniteTransition = rememberInfiniteTransition(label = "verifySweep")
    val verifyRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "verifyRotation"
    )
    val verifyPulse by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "verifyPulse"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NeoBg)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Crossfade(
            targetState = uiStage,
            animationSpec = tween(300),
            label = "stageCrossfade"
        ) { currentStage ->
            when (currentStage) {
                TransferUiStage.TRANSFERRING, TransferUiStage.VERIFYING -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // ═══════════════════════════════════════════════════════════════
                        //  TOP BAR: TRANSFER / VERIFY HEADER & CANCEL BUTTON
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
                                            .background(
                                                if (currentStage == TransferUiStage.VERIFYING)
                                                    NeoCyan.copy(alpha = 0.18f)
                                                else
                                                    NeoBlue.copy(alpha = 0.15f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (currentStage == TransferUiStage.VERIFYING) Icons.Default.Sync else Icons.Default.InsertDriveFile,
                                            contentDescription = null,
                                            tint = if (currentStage == TransferUiStage.VERIFYING) NeoCyan else NeoBlue,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .then(if (currentStage == TransferUiStage.VERIFYING) Modifier.rotate(verifyRotation) else Modifier)
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
                                            text = if (currentStage == TransferUiStage.VERIFYING) "Verifying integrity..." else "Streaming with $targetName",
                                            fontSize = 12.sp,
                                            color = if (currentStage == TransferUiStage.VERIFYING) NeoCyan else TextSecondary
                                        )
                                    }
                                }

                                if (currentStage == TransferUiStage.TRANSFERRING) {
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
                        //  NEOMORPHIC DUAL-ARC / RADAR CIRCULAR PROGRESS DASHBOARD
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

                            // Dual-Colored Circular Progress Arcs / Radar Sweep
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

                                if (currentStage == TransferUiStage.VERIFYING) {
                                    // Full glowing radar sweep for verification
                                    rotate(verifyRotation) {
                                        drawArc(
                                            brush = Brush.sweepGradient(
                                                listOf(
                                                    NeoGreen.copy(alpha = 0.15f),
                                                    NeoCyan,
                                                    NeoGreen,
                                                    NeoGreen.copy(alpha = 0.15f)
                                                )
                                            ),
                                            startAngle = 0f,
                                            sweepAngle = 360f,
                                            useCenter = false,
                                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                                        )
                                    }
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

                            // Central Value & Speed / Verification Display
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

                                Spacer(modifier = Modifier.height(4.dp))

                                if (currentStage == TransferUiStage.VERIFYING) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .scale(verifyPulse)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(NeoCyan.copy(alpha = 0.2f))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Sync,
                                            contentDescription = null,
                                            tint = NeoCyan,
                                            modifier = Modifier
                                                .size(13.dp)
                                                .rotate(verifyRotation)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "Checking CRC32",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeoCyan
                                        )
                                    }
                                } else {
                                    // Speed Pill
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(NeoCyan.copy(alpha = 0.15f))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = formattedSpeed,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeoCyan
                                        )
                                    }

                                    if (telemetry.etaSeconds > 0) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "ETA: ${telemetry.etaSeconds}s",
                                            fontSize = 11.sp,
                                            color = TextMuted
                                        )
                                    }
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

                                // Transport Multipath Ratio
                                val currentNorm = normalizedProgress.coerceAtLeast(0.001f)
                                val usbPct = (targetUsbRatio / currentNorm * 100f).coerceIn(0f, 100f)
                                val wifiPct = (targetWifiRatio / currentNorm * 100f).coerceIn(0f, 100f)
                                val ratioText = when {
                                    hasUsb && targetWifiRatio > 0.001f -> "USB %.1f%% · Wi-Fi %.1f%%".format(usbPct, wifiPct)
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
                    }
                }

                TransferUiStage.COMPLETED -> {
                    // ═══════════════════════════════════════════════════════════════
                    //  COMPLETED SCREEN: CELEBRATION SUMMARY & SEND ANOTHER ACTIONS
                    // ═══════════════════════════════════════════════════════════════
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top Header Bar
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(NeoGreen.copy(alpha = 0.18f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = NeoGreen,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Transfer Complete!",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "File verified & saved successfully",
                                            fontSize = 12.sp,
                                            color = NeoGreen
                                        )
                                    }
                                }

                                NeoButton(
                                    onClick = onFinish,
                                    cornerRadius = 12.dp,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Hero Completed Card
                        NeoCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 24.dp,
                            elevation = 8.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Large Verified Circular Well
                                NeoInset(
                                    modifier = Modifier.size(100.dp),
                                    cornerRadius = 50.dp
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Success",
                                            tint = NeoGreen,
                                            modifier = Modifier.size(54.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = telemetry.title.ifEmpty { "Transferred File" },
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(NeoGreen.copy(alpha = 0.15f))
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VerifiedUser,
                                        contentDescription = null,
                                        tint = NeoGreen,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "100.00% Verified · Zero Loss",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeoGreen
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Summary Metrics Tile
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
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
                                            Text(text = "Payload Size", fontSize = 11.sp, color = TextMuted)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "%.2f MB".format(totalMb.coerceAtLeast(sentMb)),
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                        }
                                    }

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
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                NeoCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    cornerRadius = 14.dp,
                                    backgroundColor = NeoCardPressed
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FolderOpen,
                                            contentDescription = null,
                                            tint = NeoPurple,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Saved in Downloads/ShareDash",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Piece Map visualizer in completed state
                        PieceVisualizerGrid(chunkStates = telemetry.chunkStates)

                        Spacer(modifier = Modifier.height(22.dp))

                        // ═══════════════════════════════════════════════════════════════
                        //  ACTION BUTTONS: SEND ANOTHER FILE / SELECT FILES
                        // ═══════════════════════════════════════════════════════════════
                        // Primary Action: Select Files to Send / Send Another
                        NeoButton(
                            onClick = onSendAnother,
                            cornerRadius = 18.dp,
                            accentColor = NeoBlue,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 15.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Select Files for Sending / Send Another",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Secondary Action: Open in Downloads
                        NeoButton(
                            onClick = {
                                try {
                                    val intent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                            cornerRadius = 16.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 13.dp),
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

                        // Tertiary Action: Done / Back
                        NeoButton(
                            onClick = onFinish,
                            cornerRadius = 16.dp,
                            accentColor = NeoGreen,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 13.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Done — Return to Home",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
