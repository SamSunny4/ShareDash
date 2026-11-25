package com.sharedash.app.ui.screens

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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.sharedash.app.model.SchedulerTelemetry
import com.sharedash.app.model.TransportKind
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
import com.sharedash.app.model.TransferDirection
import com.sharedash.app.model.formatTransferSpeed

enum class TransferUiStage {
    TRANSFERRING,
    VERIFYING,
    COMPLETED,
    FAILED
}

@Composable
fun TransferScreen(
    targetName: String,
    telemetry: SchedulerTelemetry?,
    onCancel: () -> Unit,
    onFinish: () -> Unit,
    onSendAnother: () -> Unit = {},
    onPickFiles: () -> Unit = {},
    onPickFolder: () -> Unit = {},
    onPickGallery: () -> Unit = {},
    onPickDocuments: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (telemetry != null) {
        ActiveTransferScreen(
            targetName = targetName,
            telemetry = telemetry,
            onCancel = onCancel,
            onFinish = onFinish,
            onSendAnother = onSendAnother,
            modifier = modifier
        )
    } else {
        TransferIdleScreen(
            targetName = targetName,
            onPickFiles = onPickFiles,
            onPickFolder = onPickFolder,
            onPickGallery = onPickGallery,
            onPickDocuments = onPickDocuments,
            onNavigateToHistory = onNavigateToHistory,
            modifier = modifier
        )
    }
}

@Composable
private fun ActiveTransferScreen(
    targetName: String,
    telemetry: SchedulerTelemetry,
    onCancel: () -> Unit,
    onFinish: () -> Unit,
    onSendAnother: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val received = telemetry.direction == TransferDirection.RECEIVED
    val recipient = telemetry.peerName.ifBlank { targetName }.ifBlank { "the other device" }
    val normalizedProgress = telemetry.progressPct.coerceIn(0f, 1f)
    val uiStage = when (telemetry.status) {
        "COMPLETED" -> TransferUiStage.COMPLETED
        "FAILED" -> TransferUiStage.FAILED
        "VERIFYING" -> TransferUiStage.VERIFYING
        else -> TransferUiStage.TRANSFERRING
    }
    val usbBytes = telemetry.usbBytes
    val showDistribution = usbBytes != null && telemetry.completedBytes > 0
    val usbShare = ((usbBytes ?: 0L).toDouble() / telemetry.completedBytes.coerceAtLeast(1)).coerceIn(0.0, 1.0).toFloat()
    val hasUsb = usbShare > 0f
    val targetUsbRatio = usbShare * normalizedProgress
    val targetWifiRatio = normalizedProgress - targetUsbRatio

    // Continuous smooth linear interpolation for silky-smooth progress without pauses
    val targetTotalProg = if (uiStage == TransferUiStage.COMPLETED) 1.0f else normalizedProgress
    val animatedTotalProgress by animateFloatAsState(
        targetValue = targetTotalProg,
        animationSpec = tween(durationMillis = 180, easing = LinearEasing),
        label = "totalProgress"
    )
    val animatedUsbProgress by animateFloatAsState(
        targetValue = targetUsbRatio,
        animationSpec = tween(durationMillis = 180, easing = LinearEasing),
        label = "usbProgress"
    )
    val animatedWifiProgress by animateFloatAsState(
        targetValue = targetWifiRatio,
        animationSpec = tween(durationMillis = 180, easing = LinearEasing),
        label = "wifiProgress"
    )

    // Continuous 2-decimal percentage counter (e.g. 1.00%, 1.01% ... 100.00%)
    val pctFloat = (animatedTotalProgress * 100f).coerceIn(0f, 100f)
    val formattedPct = if (uiStage != TransferUiStage.TRANSFERRING) "100.00%" else "%.2f%%".format(pctFloat)

    val speedMbS = telemetry.aggregateMbps / 8.0
    val formattedSpeed = formatTransferSpeed(telemetry.aggregateMbps)

    // Track peak speed observed throughout transfer
    var peakSpeedMbS by remember(telemetry.transferId) { mutableDoubleStateOf(0.0) }
    if (speedMbS > peakSpeedMbS) {
        peakSpeedMbS = speedMbS
    }

    // Verification rotation & pulse transition - only animated during VERIFYING stage
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
    // Only apply rotation when actually in VERIFYING stage
    val activeVerifyRotation = if (uiStage == TransferUiStage.VERIFYING) verifyRotation else 0f
    val activeVerifyPulse = if (uiStage == TransferUiStage.VERIFYING) verifyPulse else 1f

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
                                                .then(if (currentStage == TransferUiStage.VERIFYING) Modifier.rotate(activeVerifyRotation) else Modifier)
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
                                            text = if (currentStage == TransferUiStage.VERIFYING) "Finishing transfer…" else if (received) "Receiving from $recipient" else "Sending to $recipient",
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
                            val arcTrackColor = NeoDarkShadow.copy(alpha = 0.6f)
                            Canvas(modifier = Modifier.size(180.dp)) {
                                val strokeW = 8.dp.toPx()
                                val inset = 12.dp.toPx()
                                val innerOrigin = androidx.compose.ui.geometry.Offset(inset, inset)
                                val innerSize = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset)
                                if (showDistribution) drawArc(arcTrackColor, -90f, 360f, false, innerOrigin, innerSize, style = Stroke(width = strokeW))

                                // Background Track
                                drawArc(
                                    color = arcTrackColor,
                                    startAngle = -90f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                                )

                                if (currentStage == TransferUiStage.VERIFYING) {
                                    rotate(activeVerifyRotation) {
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
                                            sweepAngle = -animatedUsbProgress * 360f,
                                            useCenter = false,
                                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                                        )
                                    }

                                    // Arc 2: 5GHz Wi-Fi (Green)
                                    if (animatedWifiProgress > 0.001f) {
                                        drawArc(
                                            color = if (showDistribution) NeoGreen else NeoBlue,
                                            startAngle = -90f,
                                            sweepAngle = animatedWifiProgress * 360f,
                                            topLeft = if (showDistribution) innerOrigin else androidx.compose.ui.geometry.Offset.Zero,
                                            size = if (showDistribution) innerSize else size,
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
                                            .scale(activeVerifyPulse)
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
                                                .rotate(activeVerifyRotation)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "Finishing…",
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
                                            text = if (telemetry.aggregateMbps <= 0.0) "Estimating time…" else if (telemetry.etaSeconds < 1) "Less than a second left" else "${telemetry.etaSeconds}s left",
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
                                                text = if (peakSpeedMbS > 0) formatTransferSpeed(peakSpeedMbS * 8) else "—",
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
                                                text = "${formatFileSize(telemetry.completedBytes)} / ${formatFileSize(telemetry.totalBytes)}",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Transport Multipath Ratio
                                val ratioText = "USB %.0f%% · Wi-Fi %.0f%%".format(usbShare * 100, (1 - usbShare) * 100)
                                if (showDistribution) NeoCard(
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
                                            text = if (received) "File received" else "Transfer complete",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = if (received) "From $recipient" else "Sent to $recipient",
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

                                if (received) Text(
                                    text = "Saved to Downloads/ShareDash",
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
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
                                                text = formatFileSize(telemetry.totalBytes),
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
                                            Text(text = "Transfer speed", fontSize = 11.sp, color = TextMuted)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = if (telemetry.aggregateMbps > 0) formattedSpeed else "—",
                                                fontSize = 15.sp,
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
                                    text = "Send more files",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (received) NeoButton(
                            onClick = { openReceivedFile(context, java.io.File(
                                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                                "ShareDash/${telemetry.title}"
                            )) },
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
                                    text = "Open file",
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
                                    text = "Done",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                TransferUiStage.FAILED -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top Bar
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
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(NeoRed.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = null,
                                            tint = NeoRed,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Transfer Failed",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeoRed
                                        )
                                        Text(
                                            text = "The transfer did not finish",
                                            fontSize = 12.sp,
                                            color = TextSecondary
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

                        Spacer(modifier = Modifier.height(24.dp))

                        // Hero Error Card
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
                                NeoInset(
                                    modifier = Modifier.size(90.dp),
                                    cornerRadius = 45.dp
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Failed",
                                            tint = NeoRed,
                                            modifier = Modifier.size(44.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = telemetry.title.ifEmpty { "File Transfer" },
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Check the file is available and $recipient is still connected, then try again.",
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        NeoButton(
                            onClick = onFinish,
                            cornerRadius = 16.dp,
                            accentColor = NeoRed,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 13.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Return to Home",
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

        // Padding at bottom for floating bottom pill bar
        Spacer(modifier = Modifier.height(85.dp))
    }
}

@Composable
private fun TransferIdleScreen(
    targetName: String,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
    onPickGallery: () -> Unit,
    onPickDocuments: () -> Unit,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NeoBg)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ═══════════════════════════════════════════════════════════════
        //  HEADER
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(NeoCyan, NeoBlue)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Transfer",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Transfer Hub",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Send files to a nearby device",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                NeoButton(
                    onClick = onNavigateToHistory,
                    cornerRadius = 14.dp,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "History",
                        tint = NeoCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ═══════════════════════════════════════════════════════════════
        //  HERO READY / TARGET STATUS
        // ═══════════════════════════════════════════════════════════════
        NeoCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(90.dp),
                    contentAlignment = Alignment.Center
                ) {
                    NeoInset(
                        modifier = Modifier.size(90.dp),
                        cornerRadius = 45.dp
                    ) {}

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(NeoBlue, NeoCyan)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Select Content to Transfer",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (targetName.isBlank()) "Choose files, then select a recipient." else "Send to $targetName",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Big Primary Send CTA
                NeoButton(
                    onClick = onPickFiles,
                    cornerRadius = 18.dp,
                    accentColor = NeoBlue,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Choose files",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ═══════════════════════════════════════════════════════════════
        //  CATEGORY GRID
        // ═══════════════════════════════════════════════════════════════
        Text(
            text = "Categories",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TransferCategoryItem(
                icon = Icons.Default.PhotoLibrary,
                title = "Gallery",
                subtitle = "Photos & Videos",
                accentColor = NeoPurple,
                onClick = onPickGallery,
                modifier = Modifier.weight(1f)
            )

            TransferCategoryItem(
                icon = Icons.Default.Folder,
                title = "Folders",
                subtitle = "Files in a folder",
                accentColor = NeoCyan,
                onClick = onPickFolder,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TransferCategoryItem(
                icon = Icons.Default.Description,
                title = "Documents",
                subtitle = "PDFs, Docs, Zips",
                accentColor = NeoGreen,
                onClick = onPickDocuments,
                modifier = Modifier.weight(1f)
            )

            TransferCategoryItem(
                icon = Icons.Default.History,
                title = "History",
                subtitle = "Past Transfers",
                accentColor = NeoBlue,
                onClick = onNavigateToHistory,
                modifier = Modifier.weight(1f)
            )
        }

        // Bottom padding for floating bottom pill bar
        Spacer(modifier = Modifier.height(85.dp))
    }
}

@Composable
private fun TransferCategoryItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NeoCard(
        modifier = modifier,
        cornerRadius = 18.dp,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = TextMuted
            )
        }
    }
}
