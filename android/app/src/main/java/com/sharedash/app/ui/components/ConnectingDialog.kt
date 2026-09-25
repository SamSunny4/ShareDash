package com.sharedash.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sharedash.app.ui.theme.NeoBg
import com.sharedash.app.ui.theme.NeoBlue
import com.sharedash.app.ui.theme.NeoButton
import com.sharedash.app.ui.theme.NeoCard
import com.sharedash.app.ui.theme.NeoCyan
import com.sharedash.app.ui.theme.NeoGreen
import com.sharedash.app.ui.theme.NeoInset
import com.sharedash.app.ui.theme.NeoRed
import com.sharedash.app.ui.theme.NeoYellow
import com.sharedash.app.ui.theme.TextMuted
import com.sharedash.app.ui.theme.TextPrimary
import com.sharedash.app.ui.theme.TextSecondary

@Composable
fun ConnectingDialog(
    targetName: String,
    endpoint: String = "192.168.42.1:54321",
    transportType: String = "USB Fast-Path",
    pin: String = "",
    step: Int = 1,
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit
) {
    val isConnected = step >= 3

    // ── Animation transitions ─────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "connectingTransition")

    // Radar ripple wave for phone node (expanding outward)
    val phoneRippleScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.48f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "phoneRippleScale"
    )
    val phoneRippleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "phoneRippleAlpha"
    )

    // Radar ripple wave for PC node (slightly offset in time)
    val pcRippleScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.48f,
        animationSpec = infiniteRepeatable(tween(1600, delayMillis = 350, easing = LinearEasing), RepeatMode.Restart),
        label = "pcRippleScale"
    )
    val pcRippleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1600, delayMillis = 350, easing = LinearEasing), RepeatMode.Restart),
        label = "pcRippleAlpha"
    )

    // Breathing pulse for device icons (natural, subtle, no rubber-banding)
    val deviceBreathe by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "deviceBreathe"
    )

    // Moving data beam packet between phone and PC (0f to 1f across full track width)
    val beamOffsetPct by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (isConnected) 650 else 1000, easing = LinearEasing), RepeatMode.Restart),
        label = "beamOffset"
    )

    // Blinking terminal cursor
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(550, easing = LinearEasing), RepeatMode.Reverse),
        label = "cursorAlpha"
    )

    Dialog(onDismissRequest = onCancel) {
        NeoCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            cornerRadius = 28.dp,
            elevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ═══════════════════════════════════════════════════════════
                //  1. ANIMATED DEVICE-TO-DEVICE CONNECTION BRIDGE
                // ═══════════════════════════════════════════════════════════
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Phone Device Node
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(contentAlignment = Alignment.Center) {
                            // Radiating radar ripple ring
                            Box(
                                modifier = Modifier
                                    .size(62.dp)
                                    .scale(phoneRippleScale)
                                    .clip(CircleShape)
                                    .border(
                                        width = 1.5.dp,
                                        color = NeoCyan.copy(alpha = phoneRippleAlpha),
                                        shape = CircleShape
                                    )
                            )

                            // Outer soft glow halo
                            Box(
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape)
                                    .background(NeoCyan.copy(alpha = 0.16f))
                            )

                            // Inset circular button with Phone icon
                            NeoInset(
                                modifier = Modifier.size(52.dp),
                                cornerRadius = 26.dp
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhoneAndroid,
                                        contentDescription = "Phone",
                                        tint = NeoCyan,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .scale(deviceBreathe)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "This Phone",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                    }

                    // Animated Data Beam / Highway in Middle
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Transport Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isConnected) NeoGreen.copy(alpha = 0.18f)
                                        else NeoCyan.copy(alpha = 0.18f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isConnected) NeoGreen.copy(alpha = 0.5f)
                                        else NeoCyan.copy(alpha = 0.5f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (transportType.contains("USB")) Icons.Default.Usb else Icons.Default.ElectricBolt,
                                        contentDescription = null,
                                        tint = if (isConnected) NeoGreen else NeoCyan,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = transportType,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isConnected) NeoGreen else NeoCyan
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Glowing animated track spanning full highway length
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF101726))
                                    .border(
                                        width = 0.8.dp,
                                        color = if (isConnected) NeoGreen.copy(alpha = 0.45f) else NeoCyan.copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                            ) {
                                val trackWidth = maxWidth
                                val packetWidth = (trackWidth * 0.32f).coerceIn(24.dp, 48.dp)
                                val maxTravel = (trackWidth - packetWidth).coerceAtLeast(0.dp)
                                val packetX = maxTravel * beamOffsetPct

                                if (isConnected) {
                                    // Connected: Glowing solid energized conduit bridge
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(
                                                        NeoCyan.copy(alpha = 0.7f),
                                                        NeoGreen,
                                                        NeoCyan.copy(alpha = 0.7f)
                                                    )
                                                )
                                            )
                                    )
                                    // High-velocity shimmer traversing across the active conduit
                                    Box(
                                        modifier = Modifier
                                            .offset(x = packetX)
                                            .width(packetWidth)
                                            .fillMaxHeight()
                                            .background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color.White.copy(alpha = 0.9f),
                                                        Color.Transparent
                                                    )
                                                )
                                            )
                                    )
                                } else {
                                    // Negotiating: Data beam packet travelling 100% of the distance from Phone to PC
                                    Box(
                                        modifier = Modifier
                                            .offset(x = packetX)
                                            .width(packetWidth)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(
                                                        Color.Transparent,
                                                        NeoCyan,
                                                        Color.White.copy(alpha = 0.9f),
                                                        NeoCyan,
                                                        Color.Transparent
                                                    )
                                                )
                                            )
                                    )
                                }
                            }
                        }
                    }

                    // PC Device Node
                    val pcColor = if (isConnected) NeoGreen else NeoBlue
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(contentAlignment = Alignment.Center) {
                            // Radiating radar ripple ring
                            Box(
                                modifier = Modifier
                                    .size(62.dp)
                                    .scale(pcRippleScale)
                                    .clip(CircleShape)
                                    .border(
                                        width = 1.5.dp,
                                        color = pcColor.copy(alpha = pcRippleAlpha),
                                        shape = CircleShape
                                    )
                            )

                            // Outer soft glow halo
                            Box(
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape)
                                    .background(pcColor.copy(alpha = 0.16f))
                            )

                            // Inset circular button with PC icon
                            NeoInset(
                                modifier = Modifier.size(52.dp),
                                cornerRadius = 26.dp
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isConnected) Icons.Default.Check else Icons.Default.Computer,
                                        contentDescription = "PC",
                                        tint = pcColor,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .scale(if (isConnected) 1.08f else deviceBreathe)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = targetName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isConnected) NeoGreen else TextPrimary,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Phase Header Banner
                val phaseTitle = when {
                    isConnected -> "✔ Connected to $targetName"
                    step == 2 -> "Phase 2: Hardware Capabilities"
                    else -> "Phase 1: Link & Handshake"
                }
                Text(
                    text = phaseTitle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isConnected) NeoGreen else TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ═══════════════════════════════════════════════════════════
                //  2. MINIMAL PC-TERMINAL CONSOLE HUD
                // ═══════════════════════════════════════════════════════════
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF090D14))
                        .border(1.dp, Color(0x3306B6D4), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        // Terminal Header Bar with Dots
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(NeoRed))
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(NeoYellow))
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(NeoGreen))
                            }
                            Text(
                                text = "ShareDash CLI v0.1.0",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // CLI Terminal Output Lines
                        Text(
                            text = "[PHASE 1] USB Connection Check",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = " • Target  : $targetName",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = NeoCyan
                        )
                        Text(
                            text = " • Endpoint: $endpoint",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )

                        AnimatedVisibility(
                            visible = step >= 2,
                            enter = fadeIn(tween(250)) + expandVertically(tween(250))
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "[PHASE 2] Capability Exchange",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeoCyan
                                )
                                Text(
                                    text = " • Transport: $transportType (480+ Mbps)",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = " • Wi-Fi PHY: 5GHz Wi-Fi 6 (1200 Mbps)",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = step >= 3,
                            enter = fadeIn(tween(250)) + expandVertically(tween(250))
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "[PHASE 3] Security & Session Ready",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeoGreen
                                )
                                Text(
                                    text = " ✔ AES-256-GCM Ephemeral Handshake OK",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = NeoGreen
                                )
                                Text(
                                    text = " ✔ Multipath Fast-Path Ready",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = NeoGreen
                                )
                            }
                        }

                        // Terminal Blinking Cursor Line
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isConnected) "ShareDash ❯ ready " else "ShareDash ❯ negotiating... ",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = if (isConnected) NeoGreen else TextMuted
                            )
                            Text(
                                text = "█",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = (if (isConnected) NeoGreen else NeoCyan).copy(alpha = cursorAlpha)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons
                if (!isConnected) {
                    NeoButton(
                        onClick = onCancel,
                        cornerRadius = 14.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }
                } else {
                    NeoButton(
                        onClick = onConfirm,
                        cornerRadius = 14.dp,
                        accentColor = NeoGreen,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Enter Connected Dashboard",
                                fontSize = 13.sp,
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
