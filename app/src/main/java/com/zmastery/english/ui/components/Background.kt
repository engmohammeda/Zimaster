package com.zmastery.english.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.zmastery.english.ui.theme.ZAmber
import com.zmastery.english.ui.theme.ZBackground
import com.zmastery.english.ui.theme.ZCyan
import com.zmastery.english.ui.theme.ZIndigo

@Composable
fun AppBackground(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(ZBackground)) {
        // Soft warm glow top-start (terracotta)
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(ZIndigo.copy(alpha = 0.07f), Color.Transparent),
                    center = Offset(160f, 100f),
                    radius = 850f,
                )
            )
        )
        // Gentle sage glow bottom-end
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(ZCyan.copy(alpha = 0.06f), Color.Transparent),
                    center = Offset(1000f, 1700f),
                    radius = 1000f,
                )
            )
        )
        // Warm gold hint mid-right
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(ZAmber.copy(alpha = 0.05f), Color.Transparent),
                    center = Offset(1050f, 500f),
                    radius = 700f,
                )
            )
        )
        content()
    }
}
