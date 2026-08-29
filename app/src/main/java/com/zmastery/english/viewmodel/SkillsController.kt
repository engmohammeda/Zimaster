package com.zmastery.english.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zmastery.english.data.AiAgent
import com.zmastery.english.domain.usecases.ChatTurn
import com.zmastery.english.domain.usecases.ConversationScene
import com.zmastery.english.domain.usecases.SkillsEngine
import com.zmastery.english.domain.usecases.WritingFeedback
import kotlinx.coroutines.launch

/**
 * Live conversation + writing evaluation for the training hub.
 *
 * Speech capture lives in the UI (it needs a Context); this controller owns
 * the dialogue history, the AI round-trip, and TTS playback of the partner.
 */
internal class SkillsController(internal val vm: AppViewModel) {

    val conversation = mutableStateListOf<ChatTurn>()
    var isThinking by mutableStateOf(false)
        private set
    var conversationError by mutableStateOf<String?>(null)
    var autoSpeak by mutableStateOf(true)
    var activeSceneId by mutableStateOf("cafe")

    var writingFeedback by mutableStateOf<WritingFeedback?>(null)
    var isEvaluatingWriting by mutableStateOf(false)
        private set
    var writingError by mutableStateOf<String?>(null)

    private val aiAgents get() = vm.aiAgents
    private val aiVoices get() = vm.aiVoices
    private val tts get() = vm.tts
    private val lessons get() = vm.lessons

    fun scenes(): List<ConversationScene> = SkillsEngine.conversationScenes(lessons.toList())

    fun activeScene(): ConversationScene =
        scenes().firstOrNull { it.id == activeSceneId } ?: scenes().last()

    fun startScene(id: String, speakOpener: Boolean = true) {
        activeSceneId = id
        conversation.clear()
        conversationError = null
        val scene = activeScene()
        val opener = ChatTurn(fromLearner = false, en = scene.starter, ar = scene.starterAr)
        conversation.add(opener)
        if (speakOpener && autoSpeak) speakPartner(scene.starter)
    }

    fun resetConversation() {
        startScene(activeSceneId, speakOpener = true)
    }

    /**
     * Push a learner utterance (from the mic or the keyboard) and get a live
     * partner reply. Always produces a reply — AI when the key works, a
     * scripted fallback otherwise — so the dialogue never stalls.
     */
    fun sendLearnerUtterance(text: String) {
        val clean = text.trim()
        if (clean.isBlank() || isThinking) return
        conversation.add(ChatTurn(fromLearner = true, en = clean))
        conversationError = null
        isThinking = true
        vm.vmScope.launch {
            val scene = activeScene()
            val agent = aiAgents.firstOrNull { it.id == "conversation" }
            val reply = tryAiReply(agent, scene, clean)
                ?: SkillsEngine.fallbackPartnerLine(
                    scene.script,
                    conversation.count { !it.fromLearner },
                )
            conversation.add(
                ChatTurn(
                    fromLearner = false,
                    en = reply.replyEn,
                    ar = reply.replyAr,
                    correction = reply.correction,
                    praise = reply.praise,
                )
            )
            isThinking = false
            vm.trackConversationTurn()
            vm.completeTask("speak")
            vm.grantXp(12)
            if (autoSpeak) speakPartner(reply.replyEn)
        }
    }

    private suspend fun tryAiReply(
        agent: AiAgent?,
        scene: ConversationScene,
        learnerText: String,
    ): com.zmastery.english.domain.usecases.ConversationReply? {
        if (!vm.hasAiKey) return null
        val system = SkillsEngine.buildConversationSystem(
            character = agent?.character.orEmpty(),
            style = agent?.style.orEmpty(),
            prompt = agent?.prompt.orEmpty(),
            sceneTitle = scene.title,
            sceneContext = scene.context,
            history = conversation.toList(),
            level = vm.cefrEstimate.first,
        )
        val res = vm.aiComplete(
            system = system,
            user = learnerText,
            agentId = "conversation",
            json = true,
        )
        if (!res.ok) {
            conversationError = res.error.ifBlank { "تعذّر الرد من النموذج — نكمل بالحوار المحفوظ" }
            return null
        }
        return SkillsEngine.parseConversationReply(res.text, scene.script.firstOrNull().orEmpty())
    }

    fun speakPartner(text: String) {
        val engine = tts ?: return
        val clean = text.trim()
        if (clean.isBlank()) return
        val agent = aiAgents.firstOrNull { it.id == "conversation" }
        val voiceId = agent?.voiceId.orEmpty()
        val voiceName = aiVoices.firstOrNull { it.id.equals(voiceId, true) }?.displayName
            ?: voiceId.replaceFirstChar { it.uppercase() }
        val prev = engine.voice
        if (voiceName.isNotBlank()) engine.voice = voiceName
        vm.vmScope.launch {
            try {
                engine.speakInstant(clean, "conv_partner")
            } finally {
                engine.voice = prev
            }
        }
    }

    fun stopPartnerSpeech() {
        tts?.stop()
    }

    fun evaluateWriting(text: String, promptEn: String, targetWord: String) {
        val clean = text.trim()
        if (clean.isBlank() || isEvaluatingWriting) return
        writingError = null
        isEvaluatingWriting = true
        vm.vmScope.launch {
            val local = SkillsEngine.localWritingCheck(clean, targetWord)
            if (!vm.hasAiKey) {
                writingFeedback = local
                isEvaluatingWriting = false
                vm.grantXp(8)
                return@launch
            }
            val system = SkillsEngine.buildWritingSystem(targetWord, promptEn, vm.cefrEstimate.first)
            val res = vm.aiComplete(
                system = system,
                user = clean,
                agentId = "translator",
                json = true,
            )
            writingFeedback = if (res.ok) {
                SkillsEngine.parseWritingFeedback(res.text, clean, targetWord)
            } else {
                writingError = res.error
                local
            }
            isEvaluatingWriting = false
            vm.grantXp(10)
        }
    }

    fun clearWriting() {
        writingFeedback = null
        writingError = null
    }
}
