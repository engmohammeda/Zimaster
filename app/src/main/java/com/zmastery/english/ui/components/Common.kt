package com.zmastery.english.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zmastery.english.ui.theme.*

/** White card with soft warm shadow + hairline border — depth on cream canvas. */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    radius: Dp = 22.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    val base = modifier
        .shadow(elevation = 10.dp, shape = shape, ambientColor = ZIndigo.copy(alpha = 0.10f), spotColor = ZIndigo.copy(alpha = 0.10f))
        .clip(shape)
        .background(ZCard)
        .border(BorderStroke(1.dp, ZBorder), shape)
    if (onClick != null) {
        Surface(color = Color.Transparent, shape = shape, onClick = onClick, modifier = base) { content() }
    } else {
        Box(base) { content() }
    }
}

@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(ZIndigo, ZPurple),
    radius: Dp = 24.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(Brush.linearGradient(colors)),
        content = content,
    )
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ProgressRing(
    progress: Float,
    size: Dp = 64.dp,
    stroke: Dp = 7.dp,
    color: Color = ZCyan,
    trackColor: Color = ZBorder,
    label: @Composable () -> Unit = {},
) {
    val animated by animateFloatAsState(targetValue = progress, animationSpec = tween(700), label = "ring")
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        androidx.compose.foundation.Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            drawArc(color = trackColor, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                topLeft = androidx.compose.ui.geometry.Offset(sw / 2, sw / 2),
                size = androidx.compose.ui.geometry.Size(this.size.width - sw, this.size.height - sw))
            drawArc(color = color, startAngle = -90f, sweepAngle = 360f * animated.coerceIn(0f, 1f), useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                topLeft = androidx.compose.ui.geometry.Offset(sw / 2, sw / 2),
                size = androidx.compose.ui.geometry.Size(this.size.width - sw, this.size.height - sw))
        }
        label()
    }
}
