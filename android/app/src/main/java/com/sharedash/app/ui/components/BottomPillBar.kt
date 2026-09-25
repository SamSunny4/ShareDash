package com.sharedash.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharedash.app.ui.theme.NeoBg
import com.sharedash.app.ui.theme.NeoBlue
import com.sharedash.app.ui.theme.NeoCard
import com.sharedash.app.ui.theme.NeoCyan
import com.sharedash.app.ui.theme.NeoDarkShadow
import com.sharedash.app.ui.theme.NeoGreen
import com.sharedash.app.ui.theme.NeoLightShadow
import com.sharedash.app.ui.theme.NeoPurple
import com.sharedash.app.ui.theme.NeoYellow
import com.sharedash.app.ui.theme.TextMuted
import com.sharedash.app.ui.theme.TextPrimary
import com.sharedash.app.ui.theme.TextSecondary

enum class NavTab(val id: String, val title: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Default.Home),
    TRANSFER("transfer", "Transfer", Icons.Default.SwapHoriz),
    HISTORY("history", "History", Icons.Default.History),
    SETTINGS("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun BottomPillBar(
    currentTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    hasActiveTransfer: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tabs = NavTab.values()
    val selectedIndex = tabs.indexOf(currentTab).coerceAtLeast(0)

    val infiniteTransition = rememberInfiniteTransition(label = "transferBorderSweep")
    val sweepRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle"
    )

    val transferAlpha by animateFloatAsState(
        targetValue = if (hasActiveTransfer) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "transferAlpha"
    )

    val elevation by animateDpAsState(
        targetValue = if (hasActiveTransfer) 22.dp else 16.dp,
        animationSpec = tween(durationMillis = 400),
        label = "elevation"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        // Floating pill container with dual neomorphic shadow & dynamic animated transfer border
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(
                    elevation = elevation,
                    shape = RoundedCornerShape(34.dp),
                    ambientColor = if (hasActiveTransfer) NeoCyan else NeoDarkShadow,
                    spotColor = if (hasActiveTransfer) NeoBlue.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.6f)
                )
                .clip(RoundedCornerShape(34.dp))
        ) {
            // Rotating Accent Sweep Gradient (fades in smoothly when transferring without sudden layout pop)
            if (transferAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            alpha = transferAlpha
                            rotationZ = sweepRotation
                        }
                        .scale(2.5f)
                        .background(
                            Brush.sweepGradient(
                                colors = listOf(
                                    NeoCyan,
                                    NeoBlue,
                                    NeoPurple,
                                    NeoGreen,
                                    NeoYellow,
                                    NeoCyan
                                )
                            )
                        )
                )
            }

            // Inner pill container: Constant 1.5dp padding preserves rock-solid geometry with 0 layout shift
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(1.5.dp)
                    .clip(RoundedCornerShape(32.5.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                NeoCard.copy(alpha = 0.96f),
                                NeoBg.copy(alpha = 0.98f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                NeoLightShadow.copy(alpha = 0.5f * (1f - transferAlpha)),
                                NeoCyan.copy(alpha = 0.25f * (1f - transferAlpha)),
                                NeoBlue.copy(alpha = 0.25f * (1f - transferAlpha)),
                                NeoLightShadow.copy(alpha = 0.4f * (1f - transferAlpha))
                            )
                        ),
                        shape = RoundedCornerShape(32.5.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val totalWidth = maxWidth
                    val tabWidth = totalWidth / tabs.size

                    // Animated sliding background pill indicator
                    val indicatorOffset by animateDpAsState(
                        targetValue = tabWidth * selectedIndex,
                        animationSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "pillIndicatorOffset"
                    )

                    // The glowing sliding pill
                    Box(
                        modifier = Modifier
                            .offset(x = indicatorOffset)
                            .width(tabWidth)
                            .fillMaxHeight()
                            .padding(horizontal = 3.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        NeoBlue.copy(alpha = 0.28f),
                                        NeoCyan.copy(alpha = 0.20f)
                                    )
                                )
                            )
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    listOf(
                                        NeoCyan.copy(alpha = 0.6f),
                                        NeoBlue.copy(alpha = 0.35f)
                                    )
                                ),
                                shape = RoundedCornerShape(26.dp)
                            )
                    )

                    // Navigation tabs row: Arrangement.Start guarantees exact coordinate alignment with indicator
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            val isSelected = tab == currentTab
                            val iconColor by animateColorAsState(
                                targetValue = if (isSelected) NeoCyan else TextMuted,
                                label = "tabIconColor"
                            )
                            val textColor by animateColorAsState(
                                targetValue = if (isSelected) TextPrimary else TextMuted,
                                label = "tabTextColor"
                            )
                            val scale by animateFloatAsState(
                                targetValue = if (isSelected) 1.06f else 1.0f,
                                label = "tabScale"
                            )

                            Box(
                                modifier = Modifier
                                    .width(tabWidth)
                                    .fillMaxHeight()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { onTabSelected(tab) }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.scale(scale)
                                ) {
                                    Box(contentAlignment = Alignment.TopEnd) {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.title,
                                            tint = iconColor,
                                            modifier = Modifier.size(21.dp)
                                        )

                                        // Active transfer indicator badge
                                        if (tab == NavTab.TRANSFER && hasActiveTransfer) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .offset(x = 3.dp, y = (-2).dp)
                                                    .clip(CircleShape)
                                                    .background(NeoGreen)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(3.dp))

                                    Text(
                                        text = tab.title,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = textColor,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
