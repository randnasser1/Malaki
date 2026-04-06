package com.example.malaki.ui.components.angel

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// Enum for Angel variants - THIS IS THE ONLY DEFINITION
enum class AngelVariant {
    SPLASH, SMALL, SUCCESS
}

// Enum for Angel styles
enum class AngelStyle {
    SILHOUETTE, GLOWING, CLASSIC, DETAILED
}

@Composable
fun Angel(
    variant: AngelVariant = AngelVariant.SPLASH,
    moodColor: Color? = null,
    style: AngelStyle = AngelStyle.DETAILED,
    modifier: Modifier = Modifier
) {
    val size = when (variant) {
        AngelVariant.SPLASH -> 160.dp
        AngelVariant.SMALL -> 100.dp
        AngelVariant.SUCCESS -> 120.dp
    }

    val defaultColor = Color(0xFFF3D97F)
    val angelColor = moodColor ?: defaultColor

    // Floating animation
    val infiniteTransition = rememberInfiniteTransition()
    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Scale animation for splash variant
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = modifier
            .size(size)
            .offset(y = if (variant == AngelVariant.SPLASH) 0.dp else floatY.dp),
        contentAlignment = Alignment.Center
    ) {
        // Main Angel Drawing
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .scale(if (variant == AngelVariant.SPLASH) scale else 1f)
        ) {
            val centerX = size.toPx() / 2
            val centerY = size.toPx() / 2

            // Draw Halo
            drawHalo(centerX, centerY, size.toPx(), angelColor, style)

            // Draw Wings (Left and Right)
            drawWings(centerX, centerY, size.toPx(), angelColor, style)

            // Draw Body
            drawBody(centerX, centerY, size.toPx(), angelColor, style)

            // Draw Head
            drawHead(centerX, centerY, size.toPx(), angelColor, style)
        }
    }
}

private fun DrawScope.drawHalo(
    centerX: Float,
    centerY: Float,
    size: Float,
    angelColor: Color,
    style: AngelStyle
) {
    val haloWidth = size * 0.35f
    val haloHeight = size * 0.08f
    val haloX = centerX - haloWidth / 2
    val haloY = centerY - size * 0.35f

    val haloBrush = if (style == AngelStyle.SILHOUETTE) {
        Brush.radialGradient(colors = listOf(Color(0xFF475569), Color.Transparent))
    } else {
        Brush.radialGradient(colors = listOf(angelColor, angelColor.copy(alpha = 0.3f), Color.Transparent))
    }

    drawRoundRect(
        brush = haloBrush,
        topLeft = androidx.compose.ui.geometry.Offset(haloX, haloY),
        size = androidx.compose.ui.geometry.Size(haloWidth, haloHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(haloHeight / 2)
    )
}

private fun DrawScope.drawWings(
    centerX: Float,
    centerY: Float,
    size: Float,
    angelColor: Color,
    style: AngelStyle
) {
    val wingWidth = size * 0.6f
    val wingHeight = size * 0.5f

    // Left Wing
    drawWingShape(
        startX = centerX - wingWidth,
        startY = centerY - wingHeight / 2,
        width = wingWidth,
        height = wingHeight,
        isLeft = true,
        angelColor = angelColor,
        style = style
    )

    // Right Wing
    drawWingShape(
        startX = centerX,
        startY = centerY - wingHeight / 2,
        width = wingWidth,
        height = wingHeight,
        isLeft = false,
        angelColor = angelColor,
        style = style
    )
}

private fun DrawScope.drawWingShape(
    startX: Float,
    startY: Float,
    width: Float,
    height: Float,
    isLeft: Boolean,
    angelColor: Color,
    style: AngelStyle
) {
    val path = Path().apply {
        if (isLeft) {
            moveTo(startX + width * 0.2f, startY)
            lineTo(startX, startY + height * 0.4f)
            lineTo(startX + width * 0.2f, startY + height * 0.7f)
            lineTo(startX + width * 0.4f, startY + height * 0.9f)
            lineTo(startX + width * 0.6f, startY + height)
            lineTo(startX + width * 0.8f, startY + height * 0.9f)
            lineTo(startX + width, startY + height * 0.7f)
            lineTo(startX + width * 0.9f, startY + height * 0.4f)
            lineTo(startX + width * 0.7f, startY + height * 0.2f)
            lineTo(startX + width * 0.5f, startY + height * 0.1f)
            close()
        } else {
            moveTo(startX + width * 0.8f, startY)
            lineTo(startX + width, startY + height * 0.4f)
            lineTo(startX + width * 0.8f, startY + height * 0.7f)
            lineTo(startX + width * 0.6f, startY + height * 0.9f)
            lineTo(startX + width * 0.4f, startY + height)
            lineTo(startX + width * 0.2f, startY + height * 0.9f)
            lineTo(startX, startY + height * 0.7f)
            lineTo(startX + width * 0.1f, startY + height * 0.4f)
            lineTo(startX + width * 0.3f, startY + height * 0.2f)
            lineTo(startX + width * 0.5f, startY + height * 0.1f)
            close()
        }
    }

    val brush = if (style == AngelStyle.SILHOUETTE) {
        Brush.linearGradient(colors = listOf(Color(0xFF1E293B), Color(0xFF334155)))
    } else {
        Brush.linearGradient(colors = listOf(angelColor.copy(alpha = 0.7f), angelColor.copy(alpha = 0.3f)))
    }

    drawPath(path = path, brush = brush)

    if (style != AngelStyle.SILHOUETTE) {
        drawPath(
            path = path,
            brush = Brush.linearGradient(colors = listOf(angelColor.copy(alpha = 0.4f), angelColor.copy(alpha = 0.1f))),
            style = Stroke(width = 1f)
        )
    }
}

private fun DrawScope.drawBody(
    centerX: Float,
    centerY: Float,
    size: Float,
    angelColor: Color,
    style: AngelStyle
) {
    val bodyWidth = size * 0.3f
    val bodyHeight = size * 0.4f
    val bodyX = centerX - bodyWidth / 2
    val bodyY = centerY - bodyHeight / 2 + size * 0.1f

    val bodyPath = Path().apply {
        moveTo(bodyX + bodyWidth * 0.25f, bodyY)
        lineTo(bodyX + bodyWidth * 0.75f, bodyY)
        lineTo(bodyX + bodyWidth * 0.85f, bodyY + bodyHeight * 0.3f)
        lineTo(bodyX + bodyWidth * 0.9f, bodyY + bodyHeight * 0.7f)
        lineTo(bodyX + bodyWidth * 0.75f, bodyY + bodyHeight)
        lineTo(bodyX + bodyWidth * 0.5f, bodyY + bodyHeight * 0.85f)
        lineTo(bodyX + bodyWidth * 0.25f, bodyY + bodyHeight)
        lineTo(bodyX + bodyWidth * 0.1f, bodyY + bodyHeight * 0.7f)
        lineTo(bodyX + bodyWidth * 0.15f, bodyY + bodyHeight * 0.3f)
        close()
    }

    val bodyBrush = if (style == AngelStyle.SILHOUETTE) {
        Brush.linearGradient(colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A)))
    } else {
        Brush.linearGradient(colors = listOf(Color.White.copy(alpha = 0.8f), angelColor.copy(alpha = 0.3f)))
    }

    drawPath(path = bodyPath, brush = bodyBrush)
}

private fun DrawScope.drawHead(
    centerX: Float,
    centerY: Float,
    size: Float,
    angelColor: Color,
    style: AngelStyle
) {
    val headRadius = size * 0.12f
    val headY = centerY - size * 0.2f

    val headBrush = if (style == AngelStyle.SILHOUETTE) {
        Brush.radialGradient(colors = listOf(Color(0xFF334155), Color(0xFF1E293B)))
    } else {
        Brush.radialGradient(colors = listOf(angelColor, angelColor.copy(alpha = 0.6f)))
    }

    drawCircle(
        brush = headBrush,
        radius = headRadius,
        center = Offset(centerX, headY)
    )
}

@Preview(showBackground = true)
@Composable
fun AngelPreview() {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFFFF8E7)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Angel(variant = AngelVariant.SPLASH)
            Angel(variant = AngelVariant.SMALL)
            Angel(variant = AngelVariant.SUCCESS, moodColor = Color(0xFFE8F5A8))
        }
    }
}

// Helper extension
private fun Modifier.offset(y: androidx.compose.ui.unit.Dp): Modifier = this