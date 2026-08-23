package com.sharedash.app.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Neomorphic soft container with dual-light simulated extrusion.
 */
@Composable
fun NeoCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    backgroundColor: Color = NeoCard,
    borderColor: Color = NeoLightShadow.copy(alpha = 0.35f),
    elevation: Dp = 8.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed && onClick != null) 0.98f else 1f, label = "neoCardPress")

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = if (isPressed && onClick != null) 2.dp else elevation,
                shape = shape,
                ambientColor = NeoDarkShadow,
                spotColor = NeoDarkShadow
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = if (isPressed && onClick != null) {
                        listOf(NeoCardPressed, NeoCard)
                    } else {
                        listOf(NeoLightShadow.copy(alpha = 0.25f), backgroundColor, backgroundColor)
                    },
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
            .border(1.dp, borderColor, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            ),
        content = content
    )
}

/**
 * Sunken / Inset Neomorphic well for inputs, gauges, and PIN slots.
 */
@Composable
fun NeoInset(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    backgroundColor: Color = NeoCardPressed,
    borderColor: Color = NeoDarkShadow.copy(alpha = 0.6f),
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            .drawBehind {
                // Top-left inner drop shadow
                drawLine(
                    color = NeoDarkShadow.copy(alpha = 0.8f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 3.dp.toPx()
                )
                drawLine(
                    color = NeoDarkShadow.copy(alpha = 0.8f),
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = 3.dp.toPx()
                )
                // Bottom-right inner highlight
                drawLine(
                    color = NeoLightShadow.copy(alpha = 0.2f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 2.dp.toPx()
                )
                drawLine(
                    color = NeoLightShadow.copy(alpha = 0.2f),
                    start = Offset(size.width, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            },
        content = content
    )
}

/**
 * Tactile Neomorphic Button with vibrant accent option.
 */
@Composable
fun NeoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    accentColor: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.96f else 1f, label = "neoBtnScale")

    val bgBrush = if (accentColor != null) {
        Brush.linearGradient(
            colors = if (isPressed) {
                listOf(accentColor.copy(alpha = 0.8f), accentColor)
            } else {
                listOf(accentColor, accentColor.copy(alpha = 0.85f))
            }
        )
    } else {
        Brush.linearGradient(
            colors = if (isPressed) {
                listOf(NeoCardPressed, NeoCard)
            } else {
                listOf(NeoLightShadow.copy(alpha = 0.35f), NeoCard)
            }
        )
    }

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = if (isPressed) 2.dp else 6.dp,
                shape = shape,
                ambientColor = accentColor?.copy(alpha = 0.3f) ?: NeoDarkShadow,
                spotColor = accentColor?.copy(alpha = 0.4f) ?: NeoDarkShadow
            )
            .clip(shape)
            .background(bgBrush)
            .border(
                1.dp,
                accentColor?.copy(alpha = 0.5f) ?: NeoLightShadow.copy(alpha = 0.4f),
                shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}
