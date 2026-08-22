package com.zmastery.english.audio

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

val LocalTts = staticCompositionLocalOf<TtsManager?> { null }

/**
 * Telemetry sink for audio playback.
 *
 * Every [AudioButton] in the app reports the seconds it actually spoke through
 * this sink, so listening time is captured from ALL features (lessons, stories,
 * conversation, dictionary, phonetics) without touching each call site.
 */
val LocalListenSink = staticCompositionLocalOf<((Long) -> Unit)?> { null }

@Composable
fun ProvideTts(tts: TtsManager, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTts provides tts, content = content)
}

/** Provide both the engine and the telemetry sink at the app root. */
@Composable
fun ProvideAudio(tts: TtsManager, onListened: (Long) -> Unit, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalTts provides tts,
        LocalListenSink provides onListened,
        content = content,
    )
}

/**
 * Circular speaker button that plays [text] via Gemini/Android TTS.
 * Shows a spinner while generating and a pulse while playing.
 *
 * @param onPlayed optional extra callback fired once playback finishes — used by
 *                 features that count engagement (e.g. conversation turns).
 */
@Composable
fun AudioButton(
    text: String,
    audioKey: String,
    accent: Color,
    size: Dp = 42.dp,
    iconSize: Dp = 22.dp,
    onPlayed: (() -> Unit)? = null,
) {
    val tts = LocalTts.current
    val listenSink = LocalListenSink.current
    val scope = rememberCoroutineScope()
    val isActive = tts?.speakingKey == audioKey

    val infinite = rememberInfiniteTransition(label = "pulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseA",
    )

    Surface(
        shape = RoundedCornerShape(50),
        color = if (isActive) accent else accent.copy(alpha = 0.12f),
        modifier = Modifier.size(size),
        onClick = {
            if (tts == null) return@Surface
            if (isActive) {
                tts.stop()
            } else {
                scope.launch {
                    val t0 = System.currentTimeMillis()
                    tts.speakInstant(text, audioKey)
                    val secs = (System.currentTimeMillis() - t0) / 1000
                    if (secs > 0) listenSink?.invoke(secs)
                    onPlayed?.invoke()
                }
            }
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.VolumeUp,
                contentDescription = "استمع",
                tint = if (isActive) Color.White else accent,
                modifier = Modifier.size(iconSize).alpha(if (isActive) pulse else 1f),
            )
        }
    }
}
