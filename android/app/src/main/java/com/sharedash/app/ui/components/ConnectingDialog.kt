package com.sharedash.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import com.sharedash.app.ui.theme.NeoCardPressed
import com.sharedash.app.ui.theme.NeoCyan
import com.sharedash.app.ui.theme.NeoGreen
import com.sharedash.app.ui.theme.NeoInset
import com.sharedash.app.ui.theme.NeoRed
import com.sharedash.app.ui.theme.TextMuted
import com.sharedash.app.ui.theme.TextPrimary
import com.sharedash.app.ui.theme.TextSecondary

@Composable
fun ConnectingDialog(
    targetName: String,
    pin: String,
    step: Int,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "pulseRing")
    val ringScale by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "ringScale"
    )
    val ringAlpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "ringAlpha"
    )

    val isConnected = step >= 3
    val digits = pin.padEnd(6, '0').take(6)

    Dialog(onDismissRequest = onCancel) {
        NeoCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            cornerRadius = 28.dp,
            elevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ═══════════════════════════════════════════════════════════
                //  ANIMATED LOCK BADGE
                // ═══════════════════════════════════════════════════════════
                Box(
                    modifier = Modifier.size(90.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isConnected) {
                        Box(
                            modifier = Modifier
                                .size(85.dp)
                                .scale(ringScale)
                                .clip(CircleShape)
                                .background(NeoBlue.copy(alpha = ringAlpha))
                        )
                    }

                    NeoInset(
                        modifier = Modifier.size(68.dp),
                        cornerRadius = 34.dp
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isConnected) Icons.Default.Check else Icons.Default.Security,
                                contentDescription = null,
                                tint = if (isConnected) NeoGreen else NeoBlue,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isConnected) "Connected!" else "Pairing with $targetName",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isConnected) "Secure high-speed connection established" else "Confirm the 6-digit PIN matches on your PC screen",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // ═══════════════════════════════════════════════════════════
                //  6-DIGIT PIN SLOTS (SUNKEN NEOMORPHIC WELLS)
                // ═══════════════════════════════════════════════════════════
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    digits.forEach { digit ->
                        NeoInset(
                            modifier = Modifier.size(44.dp),
                            cornerRadius = 12.dp
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = digit.toString(),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isConnected) NeoGreen else NeoCyan,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ═══════════════════════════════════════════════════════════
                //  ACTION BUTTONS
                // ═══════════════════════════════════════════════════════════
                if (step == 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        NeoButton(
                            onClick = onCancel,
                            cornerRadius = 16.dp,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Decline",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeoRed,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }

                        NeoButton(
                            onClick = onConfirm,
                            cornerRadius = 16.dp,
                            accentColor = NeoGreen,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Accept",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                } else if (!isConnected) {
                    NeoButton(
                        onClick = onCancel,
                        cornerRadius = 16.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }
}
