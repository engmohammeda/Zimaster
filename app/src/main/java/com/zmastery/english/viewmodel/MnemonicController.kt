package com.zmastery.english.viewmodel

import com.zmastery.english.data.*
import kotlinx.coroutines.launch

/**
 * Controller for the mnemonic-image batch flow (الروابط الذهنية). The persisted
 * settings + transient batch state stay on [AppViewModel]; this class holds the
 * build/slice/clear logic. See [ExamsController] for conventions.
 */
internal class MnemonicController(internal val vm: AppViewModel) {

    // ── Settings owned by the view model (written here) ──
    private var mnemonicVersion
        get() = vm.mnemonicVersion
        set(v) { vm.mnemonicVersion = v }
    private var mnemonicStyle
        get() = vm.mnemonicStyle
        set(v) { vm.mnemonicStyle = v }
    private var mnemonicPersona
        get() = vm.mnemonicPersona
        set(v) { vm.mnemonicPersona = v }
    private var mnemonicModel
        get() = vm.mnemonicModel
        set(v) { vm.mnemonicModel = v }
    private var mnemonicNumbering
        get() = vm.mnemonicNumbering
        set(v) { vm.mnemonicNumbering = v }
    private var mnemonicBatchSize
        get() = vm.mnemonicBatchSize
        set(v) { vm.mnemonicBatchSize = v }

    private val mnemonicBatch get() = vm.mnemonicBatch
    private var mnemonicSpec
        get() = vm.mnemonicSpec
        set(v) { vm.mnemonicSpec = v }
    private var mnemonicPromptText
        get() = vm.mnemonicPromptText
        set(v) { vm.mnemonicPromptText = v }
    private var mnemonicMessage
        get() = vm.mnemonicMessage
        set(v) { vm.mnemonicMessage = v }
    private var isSlicing
        get() = vm.isSlicing
        set(v) { vm.isSlicing = v }

    private val activeVocab get() = vm.activeVocab
    private var xp
        get() = vm.xp
        set(v) { vm.xp = v }
    private val app get() = vm.app
    private fun track(mutate: (DayStat) -> Unit) = vm.track(mutate)
    private fun completeTask(id: String, amount: Int = 1) = vm.completeTask(id, amount)
    private fun launch(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) =
        vm.vmScope.launch(block = block)

    val mnemonicConfig: MnemonicConfig
        get() = MnemonicConfig(mnemonicStyle, mnemonicPersona, mnemonicModel, mnemonicNumbering)

    /** True when this word already has a mnemonic tile on disk. */
    fun hasMnemonic(wordId: Int): Boolean {
        @Suppress("UNUSED_EXPRESSION") mnemonicVersion // read → recompose on change
        return MnemonicStore.has(app, wordId)
    }

    /** Absolute file path of a word's tile (for Coil), or null. */
    fun mnemonicPath(wordId: Int): String? {
        @Suppress("UNUSED_EXPRESSION") mnemonicVersion
        return MnemonicStore.pathFor(app, wordId)
    }

    /** Dictionary words still missing a mnemonic image, oldest id first. */
    val wordsMissingMnemonic: List<VocabWord>
        get() {
            @Suppress("UNUSED_EXPRESSION") mnemonicVersion
            return activeVocab.filter { !MnemonicStore.has(app, it.id) }.sortedBy { it.id }
        }

    /** Count of dictionary words that already have an image. */
    val mnemonicReadyCount: Int
        get() {
            @Suppress("UNUSED_EXPRESSION") mnemonicVersion
            return activeVocab.count { MnemonicStore.has(app, it.id) }
        }

    val mnemonicMissingCount: Int get() = activeVocab.size - mnemonicReadyCount

    /** Disk space used by all tiles, human readable. */
    val mnemonicDiskLabel: String
        get() {
            @Suppress("UNUSED_EXPRESSION") mnemonicVersion
            val b = MnemonicStore.totalBytes(app)
            return when {
                b <= 0L -> "0 KB"
                b < 1024 * 1024 -> "${b / 1024} KB"
                else -> String.format("%.1f MB", b / 1024.0 / 1024.0)
            }
        }

    /**
     * Take the next [size] dictionary words without an image and build the
     * prompt for them. Returns the batch size actually claimed (0 = nothing to do).
     */
    fun startMnemonicBatch(size: Int = mnemonicBatchSize, onlyIds: List<Int>? = null): Int {
        val pool = if (onlyIds != null) {
            val set = onlyIds.toSet()
            activeVocab.filter { it.id in set }
        } else {
            wordsMissingMnemonic
        }
        val batch = pool.take(size.coerceIn(1, MnemonicSpec.MAX_BATCH))
        mnemonicBatch.clear()
        if (batch.isEmpty()) {
            mnemonicPromptText = ""
            return 0
        }
        mnemonicBatch.addAll(batch)
        mnemonicSpec = MnemonicSpec.forCount(batch.size)
        mnemonicPromptText = MnemonicPrompt.build(batch, mnemonicSpec, mnemonicConfig)
        return batch.size
    }

    /** Rebuild the prompt after the user changes style / persona / model. */
    fun refreshMnemonicPrompt() {
        if (mnemonicBatch.isEmpty()) return
        mnemonicSpec = MnemonicSpec.forCount(mnemonicBatch.size)
        mnemonicPromptText = MnemonicPrompt.build(mnemonicBatch.toList(), mnemonicSpec, mnemonicConfig)
    }

    /** Slice an uploaded composite sheet onto the current batch. */
    fun sliceMnemonicSheet(uri: android.net.Uri, onDone: (MnemonicStore.SliceResult) -> Unit = {}) {
        if (mnemonicBatch.isEmpty()) {
            val r = MnemonicStore.SliceResult(false, 0, "ابدأ دفعة أولاً")
            mnemonicMessage = r.message
            onDone(r)
            return
        }
        isSlicing = true
        launch {
            val ids = mnemonicBatch.map { it.id }
            val res = MnemonicStore.sliceAndSave(app, uri, ids, mnemonicSpec)
            isSlicing = false
            mnemonicVersion++
            mnemonicMessage = res.message
            if (res.success) {
                xp += res.saved * 2
                track { it.mnemonicsMade += res.saved; it.xpEarned += res.saved * 2 }
                completeTask("mnemonic", res.saved)
                vm.persist()
            }
            onDone(res)
        }
    }

    /** Drop a single word's image (so it re-enters the missing pool). */
    fun clearMnemonic(wordId: Int) {
        MnemonicStore.delete(app, wordId)
        mnemonicVersion++
    }

    /** Wipe every mnemonic tile. */
    fun clearAllMnemonics(): Int {
        val n = MnemonicStore.clearAll(app)
        mnemonicVersion++
        mnemonicMessage = if (n > 0) "تم حذف $n صورة" else "لا توجد صور لحذفها"
        return n
    }
}
