package com.zmastery.english.viewmodel

import com.zmastery.english.data.*
import kotlinx.coroutines.launch

/**
 * Controller for the AI configuration layer: API-key management, model fetching,
 * and the model/voice catalogue helpers. The persisted key/model/agent state and
 * the `geminiApiKey`/`activeKey`/`hasAiKey` accessors stay on [AppViewModel]; this
 * class holds the management actions. See [ExamsController] for conventions.
 */
internal class AiConfigController(internal val vm: AppViewModel) {

    private val apiKeys get() = vm.apiKeys
    private val aiAgents get() = vm.aiAgents
    private val aiModels get() = vm.aiModels
    private val aiVoices get() = vm.aiVoices
    private var geminiApiKey
        get() = vm.geminiApiKey
        set(v) { vm.geminiApiKey = v }
    private var tts
        get() = vm.tts
        set(v) { vm.tts = v }
    private val activeKey: ApiKeyEntry? get() = vm.activeKey
    private val hasAiKey: Boolean get() = vm.hasAiKey

    private var isFetchingModels
        get() = vm.isFetchingModels
        set(v) { vm.isFetchingModels = v }
    private var fetchModelsMessage
        get() = vm.fetchModelsMessage
        set(v) { vm.fetchModelsMessage = v }
    private var fetchModelsDetail
        get() = vm.fetchModelsDetail
        set(v) { vm.fetchModelsDetail = v }
    private var fetchModelsFailed
        get() = vm.fetchModelsFailed
        set(v) { vm.fetchModelsFailed = v }
    private val showFreeModelsOnly get() = vm.showFreeModelsOnly
    private var keyMessage
        get() = vm.keyMessage
        set(v) { vm.keyMessage = v }
    private var verifyingKeyId
        get() = vm.verifyingKeyId
        set(v) { vm.verifyingKeyId = v }

    private fun launch(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) =
        vm.vmScope.launch(block = block)
    private fun persist() = vm.persist()

    fun updateAgent(updated: AiAgent) {
        val i = aiAgents.indexOfFirst { it.id == updated.id }
        if (i >= 0) { aiAgents[i] = updated; persist() }
    }

    /** Keep the legacy [geminiApiKey] field pointing at the active Gemini key
     *  so older code paths (TTS, models.list) keep working unchanged. */
    internal fun syncActiveKey() {
        val gem = apiKeys.firstOrNull { it.active && it.providerEnum == AiProvider.GEMINI }
            ?: apiKeys.firstOrNull { it.providerEnum == AiProvider.GEMINI }
        geminiApiKey = gem?.rawKey ?: ""
        tts?.apiKey = geminiApiKey
    }

    fun addApiKey(
        label: String,
        rawKey: String,
        provider: AiProvider = AiProvider.GEMINI,
        baseUrl: String = "",
    ) {
        val clean = rawKey.trim()
        if (clean.isBlank()) { keyMessage = "المفتاح فارغ"; return }
        if (apiKeys.any { it.rawKey == clean }) { keyMessage = "هذا المفتاح مضاف مسبقاً"; return }
        val entry = ApiKeyEntry(
            id = "k${System.currentTimeMillis()}",
            label = label.ifBlank { provider.label },
            provider = provider.name,
            rawKey = clean,
            active = apiKeys.isEmpty(),
            baseUrl = baseUrl.trim(),
        )
        apiKeys.add(entry)
        syncActiveKey()
        persist()
        keyMessage = "تمت إضافة المفتاح — جارٍ التحقق…"
        verifyKey(entry.id)
    }

    /** Live-test a stored credential against its provider. */
    fun verifyKey(id: String) {
        val i = apiKeys.indexOfFirst { it.id == id }
        if (i < 0) return
        verifyingKeyId = id
        launch {
            val res = AiClient.verify(apiKeys[i])
            val j = apiKeys.indexOfFirst { it.id == id }
            if (j >= 0) {
                apiKeys[j] = apiKeys[j].copy(status = if (res.ok) "ok" else res.error)
            }
            keyMessage = if (res.ok) (res.text.ifBlank { "المفتاح يعمل ✓" }) else res.error
            verifyingKeyId = null
            persist()
        }
    }

    fun activateKey(id: String) {
        for (i in apiKeys.indices) apiKeys[i] = apiKeys[i].copy(active = apiKeys[i].id == id)
        syncActiveKey()
        persist()
    }

    fun updateKeyLabel(id: String, label: String) {
        val i = apiKeys.indexOfFirst { it.id == id }
        if (i >= 0) { apiKeys[i] = apiKeys[i].copy(label = label.trim().ifBlank { apiKeys[i].label }); persist() }
    }

    /** Delete a credential. Callers MUST confirm first (destructive). */
    fun removeKey(id: String) {
        val removed = apiKeys.firstOrNull { it.id == id }
        apiKeys.removeAll { it.id == id }
        // Never leave the app keyless-but-marked-active.
        if (apiKeys.isNotEmpty() && apiKeys.none { it.active }) {
            apiKeys[0] = apiKeys[0].copy(active = true)
        }
        syncActiveKey()
        persist()
        keyMessage = removed?.let { "تم حذف «${it.label}»" }
    }

    /**
     * Run a text completion through the ACTIVE credential, whoever the provider
     * is. Feature code calls this instead of talking to Gemini directly.
     */
    suspend fun aiComplete(
        system: String,
        user: String,
        agentId: String = "",
        json: Boolean = false,
    ): AiClient.Reply {
        val key = activeKey
            ?: return AiClient.Reply(false, "", "أضف مفتاح API من إعدادات الذكاء الاصطناعي")
        val model = aiAgents.firstOrNull { it.id == agentId }?.modelId.orEmpty()
        return AiClient.complete(key, model, system, user, json)
    }

    /**
     * Fetch EVERY model the active API key can access — no allow-list, no
     * filtering. See [AppViewModel.fetchModels] docstring for the full rationale.
     */
    fun fetchModels() {
        if (isFetchingModels) return
        val cred = activeKey
        if (cred == null || cred.rawKey.isBlank()) {
            fetchModelsFailed = true
            fetchModelsMessage = "أضف مفتاح API من هذه الشاشة أولاً"
            return
        }
        // OpenAI-compatible providers use /models; Gemini uses its own lister.
        if (cred.protocol == AiProtocol.OPENAI) {
            isFetchingModels = true
            fetchModelsFailed = false
            launch {
                val list = AiClient.listOpenAiModels(cred)
                isFetchingModels = false
                if (list.isEmpty()) {
                    fetchModelsFailed = true
                    fetchModelsMessage = "تعذّر جلب النماذج من ${cred.providerEnum.label}"
                } else {
                    aiModels.clear(); aiModels.addAll(list.sortedByDescending { it.familyRank })
                    fetchModelsMessage = "تم جلب ${list.size} نموذج من ${cred.providerEnum.label}"
                    fetchModelsDetail = cred.providerEnum.label
                    persist()
                }
            }
            return
        }
        val key = cred.rawKey.trim()
        isFetchingModels = true
        fetchModelsFailed = false
        fetchModelsMessage = null
        fetchModelsDetail = null
        launch {
            val res = GeminiModelsService.listAll(key, includeAllVersions = true)
            isFetchingModels = false
            if (res.success && res.models.isNotEmpty()) {
                // Keep any built-in the provider did not return, so a selected
                // model never vanishes from an agent mid-session.
                val fetchedIds = res.models.map { it.id }.toSet()
                val keptBuiltins = aiModels.filter { !it.fetched && it.id !in fetchedIds }
                aiModels.clear()
                aiModels.addAll(res.models)
                aiModels.addAll(keptBuiltins)
                fetchModelsFailed = false
                fetchModelsMessage = res.message
                fetchModelsDetail = res.detail
                persist()
            } else {
                fetchModelsFailed = true
                fetchModelsMessage = res.message
                fetchModelsDetail = res.detail
            }
        }
    }

    /** Models of a kind, honouring the "free only" toggle, newest first. */
    fun modelsOfKind(kind: ModelKind): List<AiModel> {
        val base = aiModels.filter { it.kind == kind }
        val filtered = if (showFreeModelsOnly) {
            base.filter { GeminiQuotas.isFree(it.id) || GeminiQuotas.isUnknown(it.id) }
        } else base
        return filtered.sortedWith(compareByDescending<AiModel> { it.familyRank }.thenBy { it.id })
    }

    /**
     * Every model, grouped by kind — powers the full catalogue view. Kinds with
     * no models are omitted.
     */
    fun modelsGrouped(): List<Pair<ModelKind, List<AiModel>>> =
        ModelKind.values().mapNotNull { k ->
            val list = modelsOfKind(k)
            if (list.isEmpty()) null else k to list
        }

    /** Count of models with a documented free-tier allowance. */
    val freeModelCount: Int get() = aiModels.count { GeminiQuotas.isFree(it.id) }

    /**
     * Candidate models for an agent — **only the kinds this persona actually
     * uses**. A TTS teacher never sees Imagen; an image artist never sees
     * Gemini TTS. LIVE personas also receive TEXT models as a turn-based
     * fallback when the key has no native-audio models.
     */
    fun modelChoicesFor(agent: AiAgent): List<Pair<ModelKind, List<AiModel>>> =
        agent.kind.pickerKinds.mapNotNull { k ->
            val list = modelsOfKind(k)
            if (list.isEmpty()) null else k to list
        }

    fun setShowFreeModelsOnly(value: Boolean) {
        vm.showFreeModelsOnly = value
        persist()
    }

    fun modelName(id: String) = aiModels.firstOrNull { it.id == id }?.displayName ?: id
    fun modelById(id: String) = aiModels.firstOrNull { it.id == id }
    fun voiceName(id: String) = aiVoices.firstOrNull { it.id == id }?.displayName ?: id
}
