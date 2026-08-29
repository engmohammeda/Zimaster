package com.zmastery.english.audio

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Stub host for speech capture — android.speech is wired in a follow-up once
 * the rest of the training hub compiles in CI.
 */
class SpeechCapture(private val context: Context) {

    var isListening by mutableStateOf(false)
        private set
    var partial by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>("اكتب ردّك — التعرّف الصوتي يُفعَّل على الجهاز")
        private set

    val isAvailable: Boolean get() = false

    fun start(locale: String = "en-US", onFinal: (String) -> Unit) {
        error = "تعرّف الكلام غير متاح في هذه الجلسة — اكتب ردّك"
    }

    fun stop() { isListening = false }
    fun cancel() { isListening = false; partial = "" }
    fun release() { isListening = false }
}

@Composable
fun rememberSpeechCapture(): SpeechCapture {
    val context = LocalContext.current
    val capture = remember { SpeechCapture(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { capture.release() } }
    return capture
}
