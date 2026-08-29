package com.zmastery.english.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Temporary stub while isolating the CI compile failure. */
internal class SkillsController(internal val vm: AppViewModel) {
    val conversation = mutableStateListOf<Any>()
    var isThinking by mutableStateOf(false)
        private set
    var conversationError by mutableStateOf<String?>(null)
    var autoSpeak by mutableStateOf(true)
    var activeSceneId by mutableStateOf("cafe")
    var writingFeedback by mutableStateOf<Any?>(null)
    var isEvaluatingWriting by mutableStateOf(false)
        private set
    var writingError by mutableStateOf<String?>(null)

    fun scenes(): List<Any> = emptyList()
    fun startScene(id: String, speakOpener: Boolean = true) { activeSceneId = id }
    fun resetConversation() {}
    fun sendLearnerUtterance(text: String) {}
    fun speakPartner(text: String) {}
    fun stopPartnerSpeech() {}
    fun evaluateWriting(text: String, promptEn: String, targetWord: String) {}
    fun clearWriting() { writingFeedback = null; writingError = null }
}
