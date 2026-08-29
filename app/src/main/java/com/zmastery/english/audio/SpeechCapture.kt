package com.zmastery.english.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Live English speech capture via Android's SpeechRecognizer.
 *
 * Turn-based by design: tap to start, the engine finalises on silence, and
 * [onFinal] fires with the best transcript. Partial results stream into
 * [partial] so the UI feels live while the learner is still talking.
 */
class SpeechCapture(private val context: Context) {

    var isListening by mutableStateOf(false)
        private set
    var partial by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var onFinal: ((String) -> Unit)? = null

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(locale: String = "en-US", onFinal: (String) -> Unit) {
        this.onFinal = onFinal
        error = null
        partial = ""
        main.post {
            try {
                ensureRecognizer()
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }
                isListening = true
                recognizer?.startListening(intent)
            } catch (e: Exception) {
                isListening = false
                error = e.message ?: "تعذّر تشغيل التعرّف على الكلام"
            }
        }
    }

    fun stop() {
        main.post {
            runCatching { recognizer?.stopListening() }
            isListening = false
        }
    }

    fun cancel() {
        main.post {
            runCatching { recognizer?.cancel() }
            isListening = false
            partial = ""
        }
    }

    fun release() {
        main.post {
            runCatching { recognizer?.destroy() }
            recognizer = null
            isListening = false
            onFinal = null
        }
    }

    private fun ensureRecognizer() {
        if (recognizer != null) return
        if (!isAvailable) {
            error = "تعرّف الكلام غير متاح على هذا الجهاز — اكتب ردّك بدلاً منه"
            return
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
            error = null
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { isListening = false }
        override fun onError(errorCode: Int) {
            isListening = false
            error = when (errorCode) {
                SpeechRecognizer.ERROR_AUDIO -> "خطأ في الميكروفون"
                SpeechRecognizer.ERROR_CLIENT -> null // stop() often fires this
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "نحتاج إذن الميكروفون"
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    "يحتاج التعرّف اتصالاً بالإنترنت"
                SpeechRecognizer.ERROR_NO_MATCH -> "لم أسمع كلاماً واضحاً — حاول مجدداً"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "المعرّف مشغول — انتظر لحظة"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "لم يُلتقط كلام — تحدّث بعد الضغط"
                else -> "تعذّر التعرّف (رمز $errorCode)"
            }
        }
        override fun onResults(results: Bundle?) {
            isListening = false
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty().trim()
            partial = text
            if (text.isNotBlank()) onFinal?.invoke(text)
            else error = "لم أسمع كلاماً واضحاً — حاول مجدداً"
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty().trim()
            if (text.isNotBlank()) partial = text
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    companion object {
        fun localeTag(): String = Locale.US.toLanguageTag()
    }
}

@Composable
fun rememberSpeechCapture(): SpeechCapture {
    val context = LocalContext.current
    val capture = remember { SpeechCapture(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { capture.release() } }
    return capture
}
