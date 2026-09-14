package com.sharedash.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        // Floating pill container with dual neomorphic shadow & subtle neon border
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(34.dp),
                    ambientColor = NeoDarkShadow,
                    spotColor = Color.Black.copy(alpha = 0.6f)
                )
                .clip(RoundedCornerShape(34.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NeoCard.copy(alpha = 0.95f),
                            NeoBg.copy(alpha = 0.98f)
                        )
                    )
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            NeoLightShadow.copy(alpha = 0.5f),
                            NeoCyan.copy(alpha = 0.25f),
                            NeoBlue.copy(alpha = 0.25f),
                            NeoLightShadow.copy(alpha = 0.4f)
                        )
                    ),
                    shape = RoundedCornerShape(34.dp)
                )
                .padding(6.dp)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                val totalWidth = maxWidth
                val tabWidth = totalWidth / tabs.size

                // Animated sliding background pill indicator
                val indicatorOffset by animateDpAsState(
                    targetValue = tabWidth * selectedIndex,
                    animationSpec = spring(
                        dampingRatio = 0.78f,
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
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    NeoBlue.copy(alpha = 0.22f),
                                    NeoCyan.copy(alpha = 0.18f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = NeoCyan.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(28.dp)
                        )
                )

                // Navigation tabs row
                Row(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
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
                            targetValue = if (isSelected) 1.08f else 1.0f,
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
                                        modifier = Modifier.size(22.dp)
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
