package com.zmastery.english.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zmastery.english.data.*
import com.zmastery.english.domain.usecases.AiService
import com.zmastery.english.domain.usecases.BackupCoordinator
import com.zmastery.english.domain.usecases.CloudSyncService
import com.zmastery.english.domain.usecases.PerformanceUtils
import com.zmastery.english.domain.usecases.ReviewScheduler
import com.zmastery.english.domain.usecases.StoryService
import com.zmastery.english.domain.usecases.StreakManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

class AppViewModel(app: Application) : AndroidViewModel(app) {

    // ── Use Cases (extracted logic — independently testable) ──
    val reviewScheduler = ReviewScheduler()
    val streakManager = StreakManager()
    val backupCoordinator = BackupCoordinator()
    val aiService = AiService()
    val cloudSyncService = CloudSyncService()
    val storyService = StoryService()

    // ── Feature controllers (decomposed from this view model — pure logic) ──
    // Each one holds a cohesive slice of the logic that used to live here as
    // member functions. All state stays on this view model; the controllers
    // reach it through the small aliases below. The public API of this class
    // is unchanged, so no screen needs to be modified.
    internal val exam = ExamsController(this)
    internal val story = StoryController(this)
    internal val audio = AudioController(this)
    internal val cloud = CloudController(this)
    internal val aiConfig = AiConfigController(this)
    internal val progress = CurriculumController(this)
    internal val roadmap = StudyPlanController(this)
    internal val coach = CoachController(this)
    internal val mnemonic = MnemonicController(this)
    internal val importer = ImportController(this)
    internal val lessonReview = LessonReviewController(this)
    internal val wordReview = WordReviewController(this)
    internal val telemetry = TelemetryController(this)
    internal val gamification = GamificationController(this)
    internal val dailyPlan = DailyPlanController(this)
    internal val skills = SkillsController(this)

    /** Application context, exposed to the feature controllers. */
    internal val app: Application get() = getApplication<Application>()
    /** viewModelScope, exposed to the feature controllers. */
    internal val vmScope: CoroutineScope get() = viewModelScope

    // ── Performance (throttle widget refresh + cache expensive computations) ──
    private val widgetThrottle = PerformanceUtils.Throttle(5 * 60_000L)  // 5 min
    private val statsCache = PerformanceUtils.TimedCache<Any>(30_000L)   // 30 sec

    // ----- Courses (mutable so imports can add) -----
    val courses = mutableStateListOf<Course>().apply { addAll(SampleData.courses) }
    /** المسارات التخصصية الديناميكية (id ≥ 4) — تُنشأ تلقائياً من بيانات المناهج المستوردة. */
    val customLevels = mutableStateListOf<Level>()
    /** كل المستويات: الأكاديمية الثلاثة + المسارات التخصصية — تُقرأ بها كل الشاشات. */
    val allLevels: List<Level> get() = SampleData.levels + customLevels

    /** يضمن وجود مستوى تخصصي (id ≥ 4) في السجل — يستدعيه الاستيراد تلقائياً. */
    internal fun ensureCustomLevel(id: Int, name: String, emoji: String = "") {
        if (id <= 3 || customLevels.any { it.id == id }) return
        customLevels.add(
            Level(
                id = id,
                name = name.ifBlank { "المسار التخصصي $id" },
                description = "مسار تخصصي — مصطلحات وحوارات وأسلوب المجال",
                emoji = emoji.ifBlank { "🎯" },
            )
        )
    }
    val vocab = mutableStateListOf<VocabWord>().apply { addAll(SampleData.vocab.map { it.copy() }) }
    val lessons = mutableStateListOf<Lesson>().apply { addAll(SampleData.lessons.map { it.copy() }) }

    internal var nextCourseId = (courses.maxOfOrNull { it.id } ?: 0) + 1
    internal var nextLessonId = (lessons.maxOfOrNull { it.id } ?: 0) + 1
    internal var nextWordId = (vocab.maxOfOrNull { it.id } ?: 0) + 1

    // ----- Persistence -----
    /** True once the initial load from disk completes (UI can show a splash if needed). */
    var isLoaded by mutableStateOf(false)
        internal set
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            // Safe load with automatic backup recovery — if the primary data
            // is corrupted or empty, DataGuard tries the last good backup.
            val result = DataGuard.safeLoad(getApplication())
            if (result.source != LoadSource.EMPTY) {
                restoreFrom(result.state)
                if (result.recovered) {
                    android.util.Log.w("AppViewModel",
                        "Data recovered from backup: ${result.health.lessonCount} lessons, " +
                            "${result.health.vocabCount} vocab")
                }
            }
            isLoaded = true
            // مزامنة صامتة عند الإقلاع: تبني الصناديق الناقصة دون إشعارات.
            syncMysteryRewards(notify = false)
            syncWidget()
        }
    }

    internal fun repairLessonRichContent() {
        var repaired = 0
        for (i in lessons.indices) {
            val l = lessons[i]
            if (l.rawJson.isBlank()) continue
            val pkg = runCatching {
                ImportEngine.json.decodeFromString(LessonPackage.serializer(), l.rawJson)
            }.getOrNull() ?: continue

            // Heal audio_quiz spoken words (index-aligned with the JSON array).
            val healedQuiz = l.quiz.mapIndexed { idx, qi ->
                val spoken = pkg.quiz.getOrNull(idx)?.wordToSpeak?.trim().orEmpty()
                if (qi.audioText.isBlank() && spoken.isNotBlank()) qi.copy(audioText = spoken) else qi
            }

            val refilled = l.copy(
                keyExpressions = if (l.keyExpressions.isEmpty()) pkg.lessonContent.keyExpressions.map {
                    KeyExpression(it.expressionEn, it.expressionAr, it.usageAr)
                } else l.keyExpressions,
                explanationAr = l.explanationAr.ifBlank { pkg.lessonContent.explanationAr },
                logicAr = l.logicAr.ifBlank { pkg.lessonContent.logicAr },
                examples = if (l.examples.isEmpty()) pkg.lessonContent.examples.map { Sentence(it.en, it.ar) } else l.examples,
                fullTextEn = l.fullTextEn.ifBlank { pkg.lessonContent.fullTextEn },
                fullTextAr = l.fullTextAr.ifBlank { pkg.lessonContent.fullTextAr },
                segments = if (l.segments.isEmpty()) pkg.lessonContent.segments.map { Sentence(it.en, it.ar) } else l.segments,
                topicEn = l.topicEn.ifBlank { pkg.lessonContent.topicEn },
                topicAr = l.topicAr.ifBlank { pkg.lessonContent.topicAr },
                brainstorming = if (l.brainstorming.isEmpty()) pkg.lessonContent.brainstormingQuestions.map {
                    BrainstormQ(it.questionEn, it.questionAr, it.suggestedAnswerEn, it.suggestedAnswerAr)
                } else l.brainstorming,
                guidedSentences = if (l.guidedSentences.isEmpty()) pkg.lessonContent.guidedSentences.map { Sentence(it.en, it.ar) } else l.guidedSentences,
                finalDraft = l.finalDraft ?: pkg.lessonContent.finalDraft.let {
                    if (it.en.isNotBlank() || it.ar.isNotBlank()) Sentence(it.en, it.ar) else null
                },
                quiz = healedQuiz,
            )
            if (refilled != l) {
                lessons[i] = refilled
                repaired++
            }
        }
        if (repaired > 0) {
            android.util.Log.i("Zmastery", "استُشفي المحتوى الغني لـ $repaired درساً من rawJson")
        }
    }

    /** Rebuild in-memory state from a persisted snapshot. Curriculum courses are
     *  merged with any imported ones (imported course ids > built-in). */
    internal fun restoreFrom(s: AppState) {
        if (s.courses.isNotEmpty()) {
            courses.clear()
            courses.addAll(s.courses.map { it.toDomain() })
            // Ensure all built-in curriculum courses still exist (in case schema grew).
            SampleData.courses.forEach { base ->
                if (courses.none { it.id == base.id }) courses.add(base)
            }
            // Dusk Indigo migration: built-in courses adopt the new palette accents
            // (imported cloud courses keep their own colors).
            for (i in courses.indices) {
                SampleData.courses.firstOrNull { it.id == courses[i].id }?.let { base ->
                    if (courses[i].accent != base.accent) courses[i] = courses[i].copy(accent = base.accent)
                }
            }
        }
        // المسارات التخصصية (id ≥ 4)
        customLevels.clear()
        customLevels.addAll(s.customLevels.map { it.toDomain() })
        lessons.clear(); lessons.addAll(s.lessons.map { it.toDomain() })
        vocab.clear(); vocab.addAll(s.vocab.map { it.toDomain() })
        repairLessonRichContent()

        val p = s.profile
        streak = p.streak; xp = p.xp; totalReviewsToday = p.totalReviewsToday
        dailyGoal = p.dailyGoal; lessonsPerDay = p.lessonsPerDay; studyHours = p.studyHours
        motivationLevel = p.motivationLevel; accuracy = p.accuracy
        geminiApiKey = p.geminiApiKey; ttsVoice = p.ttsVoice
        desiredRetention = p.desiredRetention; maxIntervalDays = p.maxIntervalDays
        revealMode = runCatching { com.zmastery.english.data.RevealMode.valueOf(p.revealMode) }
            .getOrDefault(com.zmastery.english.data.RevealMode.FULL)
        reviewAutoPlay = p.reviewAutoPlay
        autoGenerateAiAudio = p.autoGenerateAiAudio
        aiAudioEnabled = p.aiAudioEnabled
        mnemonicStyle = MnemonicArtStyle.from(p.mnemonicStyle)
        mnemonicPersona = MnemonicPersona.from(p.mnemonicPersona)
        mnemonicModel = MnemonicModel.from(p.mnemonicModel)
        mnemonicNumbering = p.mnemonicNumbering
        mnemonicBatchSize = p.mnemonicBatchSize.coerceIn(MnemonicSpec.MIN_BATCH, MnemonicSpec.MAX_BATCH)
        nextCourseId = maxOf(p.nextCourseId, (courses.maxOfOrNull { it.id } ?: 0) + 1)
        nextLessonId = maxOf(p.nextLessonId, (lessons.maxOfOrNull { it.id } ?: 0) + 1)
        nextWordId = maxOf(p.nextWordId, (vocab.maxOfOrNull { it.id } ?: 0) + 1)

        // Restore AI agent overlays by id. Short leftover one-liners are treated
        // as legacy and replaced by the professional studio prompt; a long
        // custom prompt the learner actually edited is kept. Model and voice
        // always overlay so a chosen catalogue id is not lost.
        if (s.aiAgents.isNotEmpty()) {
            s.aiAgents.forEach { dto ->
                val i = aiAgents.indexOfFirst { it.id == dto.id }
                if (i < 0) return@forEach
                val current = aiAgents[i]
                val overlay = current.copy(
                    modelId = dto.modelId.ifBlank { current.modelId },
                    character = dto.character,
                    voiceId = dto.voiceId,
                    style = dto.style,
                    prompt = dto.prompt,
                )
                aiAgents[i] = if (AiPrompts.isLegacy(overlay)) {
                    current.copy(modelId = overlay.modelId, voiceId = overlay.voiceId)
                } else overlay
            }
        }
        if (s.aiModels.isNotEmpty()) {
            aiModels.clear()
            aiModels.addAll(s.aiModels.map { it.toDomain() })
            AiDefaults.builtinModels.forEach { b ->
                if (aiModels.none { it.id == b.id }) aiModels.add(b)
            }
        }
        showFreeModelsOnly = p.showFreeModelsOnly
        apiKeys.clear()
        apiKeys.addAll(
            s.apiKeys
                // Drop legacy placeholder rows that only ever held a masked string.
                .filter { it.rawKey.isNotBlank() }
                .map {
                    ApiKeyEntry(
                        id = it.id, label = it.label, provider = it.provider,
                        rawKey = it.rawKey, active = it.active,
                        baseUrl = it.baseUrl, status = it.status,
                    )
                }
        )
        // Migration: a key typed into the old single-field settings box becomes
        // a real credential entry so nothing the user already saved is lost.
        if (apiKeys.isEmpty() && geminiApiKey.isNotBlank()) {
            apiKeys.add(
                ApiKeyEntry(
                    id = "migrated", label = "مفتاح Gemini", provider = AiProvider.GEMINI.name,
                    rawKey = geminiApiKey.trim(), active = true,
                )
            )
        }
        if (apiKeys.isNotEmpty() && apiKeys.none { it.active }) {
            apiKeys[0] = apiKeys[0].copy(active = true)
        }
        aiConfig.syncActiveKey()
        examHistory.clear()
        examHistory.addAll(s.exams.map { it.toDomain() })
        examMisses.clear()
        s.examMisses.forEach { (k, v) -> k.toIntOrNull()?.let { examMisses[it] = v } }
        onboardingDone = p.onboardingDone
        lastActiveDay = p.lastActiveDay
        // Restore today's task progress, then roll over if the date changed.
        p.dailyTaskProgress.forEach { entry ->
            val id = entry.substringBefore(':')
            val prog = entry.substringAfter(':', "0").toIntOrNull() ?: 0
            val i = dailyTasks.indexOfFirst { it.id == id }
            if (i >= 0) dailyTasks[i] = dailyTasks[i].copy(progress = prog)
        }
        applyDayRollover()
        lastStoryDay = p.lastStoryDay
        storyArchive.clear()
        storyArchive.addAll(s.stories.map { it.toDomain() })
        nextStoryId = maxOf(p.nextStoryId, (storyArchive.maxOfOrNull { it.id } ?: 0) + 1)
        goals.clear()
        goals.addAll(s.goals)
        // Reading-course lessons always contribute their text to the archive.
        syncLessonStories()
        dayStats.clear()
        s.dayStats.forEach { dayStats[it.epochDay] = it }
        studyPlan = s.studyPlan
        coachReports.clear()
        s.coachReports.forEach { coachReports[it.scope] = it }
        // ----- 3D Momentum + Seven Seals -----
        microHabitId = p.microHabitId.ifBlank { MicroHabits.all.first().id }
        microHabitProgress = p.microHabitProgress
        dayEarnedStreak = p.dayEarnedStreak
        learnerName = p.learnerName
        learnerEmail = p.learnerEmail
        lastCloudLessonSyncMillis = p.lastCloudLessonSyncMillis
        cloudSyncEnabled = p.cloudSyncEnabled
        dismissedAnnouncementId = p.dismissedAnnouncementId
        isDeveloperUnlocked = p.developerUnlocked
        val rawWebClientId = p.googleWebClientId
        googleWebClientId = if (rawWebClientId.isBlank() || rawWebClientId.contains("567438543557")) {
            com.zmastery.english.cloud.CloudAuth.DEFAULT_WEB_CLIENT_ID
        } else {
            rawWebClientId
        }
        com.zmastery.english.cloud.CloudAuth.webClientId = googleWebClientId
        openedChests.clear()
        s.chests.forEach { openedChests[it.tierId] = it }
        wallet = s.wallet
        // ----- الصناديق الغامضة -----
        mysteryRewards.clear()
        mysteryRewards.addAll(s.mysteryRewards)
        // ----- المرحلة 3/4 — مرآة الإدراك + الإنقاذ -----
        mirrorReports.clear()
        mirrorReports.putAll(s.mirrorReports)
        // إعادة بناء التيليمتري السلوكي — بدونه تُفقد بصمة المتعلّم عند كل تشغيل.
        reviewLogs.clear()
        s.reviewSignals.forEach { sig ->
            reviewLogs.add(
                ReviewLog(
                    wordId = sig.wordId,
                    recall = RecallSource.NONE,
                    grade = sig.grade,
                    reachedStage = sig.stage,
                    replays = sig.replays,
                    timeMs = sig.timeMs,
                    retrievability = 0.0,
                    stabilityAfter = 0.0,
                    intervalAfter = 0,
                )
            )
        }
        reviewHours.clear()
        reviewHours.addAll(s.reviewHours)
        rescue = s.rescue
        lastRescueOfferDay = s.lastRescueOfferDay
        streakBeforeBreak = s.streakBeforeBreak
    }

    /** Debounced save — batches rapid mutations into one write with backup. */
    fun persist() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            val state = buildAppState()
            // Safe save: creates a backup of the previous state before overwriting,
            // and logs errors clearly instead of silently swallowing them.
            val result = DataGuard.safeSave(getApplication(), state)
            if (!result.success) {
                android.util.Log.e("AppViewModel", "Save FAILED: ${result.error}")
            }
            syncWidget()
        }
    }

    /** Push current stats to the shared store and refresh home-screen widgets. */
    /** Push current stats to the shared store and refresh home-screen widgets.
     *  Widget refresh is throttled to 5 minutes to avoid excessive broadcasts. */
    fun syncWidget() {
        val ctx = getApplication<Application>()
        ProgressStore.save(
            ctx,
            streak = streak,
            xp = xp,
            reviewsToday = totalReviewsToday,
            dailyGoal = dailyGoal,
            tasksDone = activeTasksDone,
            tasksTotal = activeDailyTasks.size,
            hasContent = hasContent,
            chestMood = decayState.mood.name,
            decaySeverity = decayState.severity,
            minimumDone = microHabitDone || tasksDone >= dailyTasks.size,
        )
        // Throttle widget refresh to avoid excessive broadcasts
        if (widgetThrottle.allow()) {
            com.zmastery.english.widget.ZMasteryWidget.refreshAll(ctx)
        }
        syncNotifState()
    }

    /** Force an immediate widget refresh (bypasses throttle).
     *  Use for explicit user actions like completing a lesson. */
    fun forceWidgetRefresh() {
        widgetThrottle.reset()
        val ctx = getApplication<Application>()
        com.zmastery.english.widget.ZMasteryWidget.refreshAll(ctx)
    }

    /** Keep the notification receiver's state fresh (runs even when app is closed). */
    fun syncNotifState() {
        val ctx = getApplication<Application>()
        val prefs = ctx.getSharedPreferences("z_notif_state", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("due_words", dueWords.size)
            .putInt("streak", streak)
            // "done" must mean real, achievable work was finished.
            .putBoolean("done_today", planCompleteToday)
            // Suppress nagging entirely until the learner has content to study.
            .putBoolean("has_content", hasContent)
            .putBoolean("has_history", hasHistory)
            // ----- Stage 4: loss-aversion state for the evening alarm -----
            .putString("chest_mood", decayState.mood.name)
            .putInt("rescue_streak", rescue.streakToRestore)
            .putBoolean("rescue_active", rescue.isActive && !rescue.completed)
            .apply()
    }

    /** Wipe all imported content & progress, restoring the empty progress. */
    fun resetAll() {
        // Cancel any pending debounced save — it would re-write the pre-reset
        // state right after the wipe below (resurrection race).
        saveJob?.cancel()
        courses.clear(); courses.addAll(SampleData.courses)
        lessons.clear(); vocab.clear()
        nextCourseId = (courses.maxOfOrNull { it.id } ?: 0) + 1
        nextLessonId = 1; nextWordId = 1
        streak = 0; xp = 0; totalReviewsToday = 0; studyHours = 0.0; accuracy = 0
        motivationLevel = 0f
        // Stories referenced words/lessons that no longer exist — clear the archive.
        // (Onboarding is intentionally NOT reset: the learner already saw the mission.)
        storyArchive.clear()
        nextStoryId = 1
        lastStoryDay = 0L
        // Mnemonic tiles belong to word ids that no longer exist — drop them all.
        MnemonicStore.clearAll(getApplication())
        dayStats.clear(); reviewHours.clear(); coachReports.clear()
        studyPlan = StudyPlanDto()
        examHistory.clear(); examMisses.clear(); examQuestions.clear()
        mnemonicBatch.clear()
        mnemonicPromptText = ""
        mnemonicVersion++
        viewModelScope.launch {
            // امسح النسخة الاحتياطية أيضاً — وإلا لاسترجعها مسار loss recovery
            // عند الإقلاع القادم كلَّ ما حذفه المستخدم عمداً.
            DataGuard.clearBackup(getApplication())
            Persistence.clear(getApplication())
            // Also clear the CLOUD copy — otherwise the next launch would merge
            // the old cloud snapshot back in and resurrect everything the user
            // just deleted. Push the freshly-cleared state (state is already
            // empty in memory at this point).
            runCatching {
                com.zmastery.english.cloud.CloudAuth.uid?.let { uid ->
                    // Strip API keys exactly like the normal sync push — keys
                    // must NEVER leave the device.
                    val safeState = KeyProtector.stripKeysForSharing(buildAppState())
                    com.zmastery.english.cloud.CloudSync.pushProgress(
                        uid, Persistence.encode(safeState),
                    )
                }
            }
        }
    }

    // ======================================================================
    //  Backup & Restore
    // ======================================================================

    /** Build a full snapshot of the current in-memory state. */
    fun currentAppState(): AppState = AppState(
        courses = courses.map { it.toDto() },
        customLevels = customLevels.map { it.toDto() },
        lessons = lessons.map { it.toDto() },
        vocab = vocab.map { it.toDto() },
        profile = ProfileDto(
            streak = streak, xp = xp, totalReviewsToday = totalReviewsToday,
            dailyGoal = dailyGoal, lessonsPerDay = lessonsPerDay, studyHours = studyHours,
            motivationLevel = motivationLevel, accuracy = accuracy,
            geminiApiKey = geminiApiKey, ttsVoice = ttsVoice,
            nextCourseId = nextCourseId, nextLessonId = nextLessonId, nextWordId = nextWordId,
            desiredRetention = desiredRetention, maxIntervalDays = maxIntervalDays,
            revealMode = revealMode.name,
            reviewAutoPlay = reviewAutoPlay,
            autoGenerateAiAudio = autoGenerateAiAudio,
            aiAudioEnabled = aiAudioEnabled,
            mnemonicStyle = mnemonicStyle.name, mnemonicPersona = mnemonicPersona.name,
            mnemonicModel = mnemonicModel.name, mnemonicNumbering = mnemonicNumbering,
            mnemonicBatchSize = mnemonicBatchSize,
        ),
        exams = examHistory.map { it.toDto() },
        examMisses = examMisses.mapKeys { it.key.toString() },
    )

    internal fun nowStamp(): String = java.time.LocalDateTime.now()
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

    // ---- Exports (return the text content to be written to a file) ----

    fun exportFullBackup(): String = BackupManager.exportFull(currentAppState(), nowStamp())

    fun exportLessonsOnly(): String = BackupManager.exportLessons(
        courses = courses.filter { c -> lessons.any { it.courseId == c.id } }.map { it.toDto() },
        lessons = lessons.map { it.toDto() },
        vocab = vocab.map { it.toDto() },
        createdAt = nowStamp(),
    )

    fun exportHardWordsJson(): String =
        BackupManager.exportWordsJson(hardWordsForExport().map { it.toDto() }, nowStamp())

    fun exportHardWordsCsv(): String =
        BackupManager.exportWordsCsv(hardWordsForExport().map { it.toDto() })

    fun exportAllWordsCsv(): String =
        BackupManager.exportWordsCsv(activeVocab.map { it.toDto() })

    /** Difficult words = leeches + words currently below target retention. */
    fun hardWordsForExport(): List<VocabWord> {
        val leeches = activeVocab.filter { it.lapses >= 2 && it.stability < 7.0 }
        val hardByDifficulty = activeVocab.filter { it.difficulty >= 6.5 }
        return (leeches + hardByDifficulty).distinctBy { it.id }.sortedByDescending { it.lapses }
    }

    val hardWordsCount: Int get() = hardWordsForExport().size

    // ---- Imports / Restore ----

    data class RestoreResult(val ok: Boolean, val message: String)

    /** Full restore — replaces everything with the backup's contents. */
    fun restoreFullBackup(raw: String): RestoreResult {
        val parsed = BackupManager.parseFull(raw)
        return parsed.fold(
            onSuccess = { state ->
                restoreFrom(state)
                persist()
                RestoreResult(true, "تمت الاستعادة: ${state.courses.size} كورس · ${state.lessons.size} درس · ${state.vocab.size} كلمة")
            },
            onFailure = { RestoreResult(false, "تعذّر قراءة الملف: ${it.message?.take(60) ?: "تنسيق غير صالح"}") },
        )
    }

    /** Merge lessons backup into the current library (delta update, keeps progress). */
    fun importLessonsBackup(raw: String): RestoreResult {
        val parsed = BackupManager.parseLessons(raw)
        return parsed.fold(
            onSuccess = { b ->
                var addedCourses = 0; var addedLessons = 0; var addedWords = 0
                // Remap incoming ids to avoid collisions.
                val courseIdMap = HashMap<Int, Int>()
                b.courses.forEach { cdto ->
                    val existing = courses.firstOrNull { it.name == cdto.name && it.levelId == cdto.levelId }
                    if (existing != null) {
                        courseIdMap[cdto.id] = existing.id
                    } else {
                        val newId = nextCourseId++
                        courseIdMap[cdto.id] = newId
                        courses.add(cdto.toDomain().copy(id = newId))
                        addedCourses++
                    }
                }
                val wordIdMap = HashMap<Int, Int>()
                b.vocab.forEach { wdto ->
                    val newId = nextWordId++
                    wordIdMap[wdto.id] = newId
                    val mappedCourse = courseIdMap[wdto.courseId] ?: wdto.courseId
                    vocab.add(wdto.toDomain().copy(id = newId, courseId = mappedCourse))
                    addedWords++
                }
                b.lessons.forEach { ldto ->
                    val mappedCourse = courseIdMap[ldto.courseId] ?: ldto.courseId
                    val newWordIds = ldto.newWordIds.map { wordIdMap[it] ?: it }
                    lessons.add(ldto.toDomain().copy(id = nextLessonId++, courseId = mappedCourse, newWordIds = newWordIds))
                    addedLessons++
                }
                // Heal rich content for lessons coming from backups made before
                // the persistence fix — their rawJson still carries everything.
                repairLessonRichContent()
                persist()
                RestoreResult(true, "تمت الإضافة: $addedCourses كورس · $addedLessons درس · $addedWords كلمة")
            },
            onFailure = { RestoreResult(false, "تعذّر قراءة ملف الدروس: ${it.message?.take(60) ?: "تنسيق غير صالح"}") },
        )
    }

    /** Import a words file (JSON) into the dictionary as new active words. */
    fun importWordsBackup(raw: String): RestoreResult {
        val parsed = BackupManager.parseWords(raw)
        return parsed.fold(
            onSuccess = { words ->
                var added = 0
                words.forEach { wdto ->
                    val exists = vocab.any { it.english.equals(wdto.english, ignoreCase = true) }
                    if (!exists) {
                        vocab.add(wdto.toDomain().copy(id = nextWordId++, pendingApproval = false))
                        added++
                    }
                }
                persist()
                RestoreResult(true, "تمت إضافة $added كلمة جديدة إلى القاموس")
            },
            onFailure = { RestoreResult(false, "تعذّر قراءة ملف الكلمات: ${it.message?.take(60) ?: "تنسيق غير صالح"}") },
        )
    }

    // ----- User profile / gamification (fresh start) -----
    var streak by mutableStateOf(0)
        internal set
    var totalReviewsToday by mutableStateOf(0)
        internal set
    var dailyGoal by mutableStateOf(30)
    var lessonsPerDay by mutableStateOf(2)
    var xp by mutableStateOf(0)
        internal set
    var themeMode by mutableStateOf(com.zmastery.english.data.ThemeMode.SYSTEM)
    var studyHours by mutableStateOf(0.0)
        internal set
    /**
     * Legacy stored mood value. Prefer [engagement] — this is kept only for
     * backward compatibility with saved profiles and the widget, and now
     * starts at 0 so a fresh install never claims "excellent motivation".
     */
    var motivationLevel by mutableStateOf(0f) // 0..1 mascot mood

    // Global default for how vocab cards reveal inside lessons.
    var revealMode by mutableStateOf(com.zmastery.english.data.RevealMode.FULL)

    /** Auto-play the English pronunciation as soon as each review stage appears. */
    var reviewAutoPlay by mutableStateOf(true)

    /**
     * Settings toggle: when true, freshly imported content is queued for
     * permanent AI voice generation automatically in the background. When
     * false, nothing is generated until the learner taps "توليد" manually.
     * On-demand instant playback is unaffected either way.
     */
    var autoGenerateAiAudio by mutableStateOf(true)

    /**
     * MASTER switch — "إيقاف توليد الأصوات نهائياً". When false, AI audio
     * generation is completely disabled: no auto-generation after import, no
     * manual "توليد" button, and any generation currently in flight is
     * cancelled immediately. Instant local playback (speakInstant) is always
     * unaffected — this only ever controls the PERMANENT AI-cached path.
     */
    var aiAudioEnabled by mutableStateOf(true)
        internal set

    fun updateAiAudioEnabled(enabled: Boolean) {
        aiAudioEnabled = enabled
        if (!enabled) stopAudioGeneration()
        persist()
    }

    // ----- API keys / voice (for Gemini TTS + LLM) -----
    var geminiApiKey by mutableStateOf("")
    var ttsVoice by mutableStateOf("Kore")

    // ===================== AI configuration layer =====================
    // Per-feature agents (character / prompt / style / voice / model).
    val aiAgents = mutableStateListOf<AiAgent>().apply { addAll(AiDefaults.agents()) }
    // Available models (starts with built-ins; "fetch models" appends provider models).
    val aiModels = mutableStateListOf<AiModel>().apply { addAll(AiDefaults.builtinModels) }
    val aiVoices = mutableStateListOf<AiVoice>().apply { addAll(AiDefaults.builtinVoices) }
    val apiKeys = mutableStateListOf<ApiKeyEntry>().apply { addAll(AiDefaults.sampleKeys) }

    var isFetchingModels by mutableStateOf(false)
        internal set
    var fetchModelsMessage by mutableStateOf<String?>(null)
    /** Per-version diagnostics from the last fetch (e.g. "v1beta: 68 · v1alpha: 9"). */
    var fetchModelsDetail by mutableStateOf<String?>(null)
        internal set
    /** True when the last fetch failed (drives error styling). */
    var fetchModelsFailed by mutableStateOf(false)
        internal set
    /** Show only models with a documented free-tier allowance. */
    var showFreeModelsOnly by mutableStateOf(false)

    fun updateAgent(updated: AiAgent) = aiConfig.updateAgent(updated)

    fun resetAgentPrompt(id: String) = aiConfig.resetAgentPrompt(id)

    /** Named so it does not clash with the `showFreeModelsOnly` property setter. */
    fun applyFreeModelsFilter(value: Boolean) = aiConfig.setShowFreeModelsOnly(value)

    /** The credential currently used by every AI feature (null = none). */
    val activeKey: ApiKeyEntry?
        get() = apiKeys.firstOrNull { it.active } ?: apiKeys.firstOrNull()

    /** True when at least one real key is stored — gates every AI feature. */
    val hasAiKey: Boolean get() = apiKeys.any { it.rawKey.isNotBlank() }

    var keyMessage by mutableStateOf<String?>(null)
    var verifyingKeyId by mutableStateOf<String?>(null)
        internal set

    fun addApiKey(
        label: String,
        rawKey: String,
        provider: AiProvider = AiProvider.GEMINI,
        baseUrl: String = "",
    ) = aiConfig.addApiKey(label, rawKey, provider, baseUrl)

    /** Live-test a stored credential against its provider. */
    fun verifyKey(id: String) = aiConfig.verifyKey(id)

    fun activateKey(id: String) = aiConfig.activateKey(id)

    fun updateKeyLabel(id: String, label: String) = aiConfig.updateKeyLabel(id, label)

    /** Delete a credential. Callers MUST confirm first (destructive). */
    fun removeKey(id: String) = aiConfig.removeKey(id)

    /**
     * Run a text completion through the ACTIVE credential, whoever the provider
     * is. Feature code calls this instead of talking to Gemini directly.
     */
    suspend fun aiComplete(
        system: String,
        user: String,
        agentId: String = "",
        json: Boolean = false,
    ): AiClient.Reply = aiConfig.aiComplete(system, user, agentId, json)

    /**
     * Fetch EVERY model the active API key can access. See [AiConfigController].
     */
    fun fetchModels() = aiConfig.fetchModels()

    /** Models of a kind, honouring the "free only" toggle, newest first. */
    fun modelsOfKind(kind: ModelKind): List<AiModel> = aiConfig.modelsOfKind(kind)

    /** Every model, grouped by kind. */
    fun modelsGrouped(): List<Pair<ModelKind, List<AiModel>>> = aiConfig.modelsGrouped()

    /** Count of models with a documented free-tier allowance. */
    val freeModelCount: Int get() = aiConfig.freeModelCount

    /** Candidate models for an agent — strictly that persona's kind. */
    fun modelChoicesFor(agent: AiAgent): List<Pair<ModelKind, List<AiModel>>> = aiConfig.modelChoicesFor(agent)

    fun modelName(id: String) = aiConfig.modelName(id)
    fun modelById(id: String) = aiConfig.modelById(id)
    fun voiceName(id: String) = aiConfig.voiceName(id)

    // ----- Training hub (live conversation + writing evaluation) -----
    val conversationTurnsList get() = skills.conversation
    val isConversationThinking get() = skills.isThinking
    var conversationError
        get() = skills.conversationError
        set(v) { skills.conversationError = v }
    var conversationAutoSpeak
        get() = skills.autoSpeak
        set(v) { skills.autoSpeak = v }
    val conversationSceneId get() = skills.activeSceneId
    fun conversationScenes() = skills.scenes()
    fun startConversationScene(id: String) = skills.startScene(id)
    fun resetConversation() = skills.resetConversation()
    fun sendConversationUtterance(text: String) = skills.sendLearnerUtterance(text)
    fun speakConversationLine(text: String) = skills.speakPartner(text)
    fun stopConversationSpeech() = skills.stopPartnerSpeech()
    val writingFeedback get() = skills.writingFeedback
    val isEvaluatingWriting get() = skills.isEvaluatingWriting
    val writingError get() = skills.writingError
    fun evaluateWriting(text: String, promptEn: String, targetWord: String) =
        skills.evaluateWriting(text, promptEn, targetWord)
    fun clearWritingFeedback() = skills.clearWriting()

    // ----- Daily phrase -----
    val dailyPhrase: String get() = SampleData.dailyPhrases[java.time.LocalDate.now().dayOfYear % SampleData.dailyPhrases.size]

    // ======================================================================
    //  Onboarding / mission
    // ======================================================================
    /** False until the learner has seen the mission ("why consistency") screen. */
    var onboardingDone by mutableStateOf(false)

    fun completeOnboarding() {
        onboardingDone = true
        persist()
    }

    // ======================================================================
    //  Story archive — daily stories + every reading lesson's story
    // ======================================================================
    val storyArchive = mutableStateListOf<ArchivedStory>()
    internal var nextStoryId = 1

    /** أهداف المتعلم التطبيقية — القصة اليومية تُنسج حول النشط منها. */
    val goals = mutableStateListOf<LifeGoal>()

    // ---- حالة اختبار إثبات المرحلة (تُكتب من StoryController) ----
    var stageQuiz by mutableStateOf<List<StageQuestion>?>(null)
        internal set
    var isMakingQuiz by mutableStateOf(false)
        internal set
    var quizMessage by mutableStateOf<String?>(null)
        internal set

    /** Epoch-day of the last generated daily story (0 = never). */
    var lastStoryDay by mutableStateOf(0L)
        internal set

    var isMakingStory by mutableStateOf(false)
        internal set
    var storyMessage by mutableStateOf<String?>(null)

    /** True when today's daily story already exists. */
    val hasTodayStory: Boolean get() = story.hasTodayStory

    /** Today's daily story, if generated. */
    val todayStory: ArchivedStory? get() = story.todayStory

    /** Archive newest-first, favourites pinned on top. */
    val storiesSorted: List<ArchivedStory> get() = story.storiesSorted

    val dailyStoryCount: Int get() = story.dailyStoryCount
    val lessonStoryCount: Int get() = story.lessonStoryCount
    val unreadStoryCount: Int get() = story.unreadStoryCount

    /** How many words are available to build a story from right now. */
    val storySeedCount: Int get() = story.storySeedCount

    // ---- AI-only daily story state ----
    /** True while the generator is parked waiting for internet / the model. */
    var isWaitingForAi by mutableStateOf(false)
        internal set
    /** Attempt number of the current waiting loop (1-based, 0 = idle). */
    var storyAttempt by mutableStateOf(0)
        internal set
    /** Seconds remaining before the next automatic retry. */
    var storyRetryIn by mutableStateOf(0)
        internal set

    /** True when the story engine can run at all (key configured). */
    val storyAiReady: Boolean get() = story.storyAiReady

    /** Cancel an in-flight / waiting story generation. */
    fun cancelStoryGeneration() = story.cancelStoryGeneration()

    /**
     * Generate (or regenerate) today's story with the AI **only**.
     * See [StoryController.generateTodayStory] for the no-fallback retry policy.
     */
    fun generateTodayStory(force: Boolean = false, onDone: (ArchivedStory?) -> Unit = {}) =
        story.generateTodayStory(force, onDone)

    /** AI prompt for enriching today's story (used by the AI-assisted path). */
    fun todayStoryPrompt(): String = story.todayStoryPrompt()

    /**
     * Mirror every reading-style lesson's text into the archive. Idempotent.
     */
    fun syncLessonStories() = story.syncLessonStories()

    fun toggleStoryRead(id: Int) = story.toggleStoryRead(id)

    fun toggleStoryFavorite(id: Int) = story.toggleStoryFavorite(id)

    // ── مسار الهدف التطبيقي ──
    /** الهدف النشط (أو الأول إن لم يُفعَّل شيء). */
    val activeGoal: LifeGoal? get() = story.activeGoal

    /** ينشئ هدف المتعلم الأول إن لم يوجد أي هدف بعد. */
    fun ensureGoalExists() = story.ensureGoalExists()

    /** ينشئ هدفاً جديداً بعنوان ومراحل يحددها المتعلم. */
    fun createGoal(title: String, description: String, stages: List<String>, onResult: (Boolean, String) -> Unit) =
        story.createGoal(title, description, stages, onResult)

    /** يفعّل هدفاً ويوقف بقية الأهداف. */
    fun setActiveGoal(id: String) = story.setActiveGoal(id)

    /** يحفظ إجابة سؤال السياق اليومي ويبني بها سياق المتعلم تدريجياً. */
    fun saveContextAnswer(storyId: Int, answer: String) = story.saveContextAnswer(storyId, answer)

    /** يولّد اختبار إثبات المرحلة الحالية (٣ أسئلة موقفية). */
    fun requestStageQuiz() = story.requestStageQuiz()

    /** يصحح الاختبار؛ اجتياز ≥٢/٣ يقدّم المرحلة. يعيد رسالة النتيجة. */
    fun submitStageQuiz(answers: List<Int>): String = story.submitStageQuiz(answers)

    /** Delete a story. Lesson stories are re-created on the next sync by design. */
    fun deleteStory(id: Int) = story.deleteStory(id)

    /**
     * حذف قصة اليوم الحالية فقط. نصفّر [lastStoryDay] أيضاً حتى يعود زر
     * "ولّد قصة اليوم" للعمل مباشرة بعد الحذف.
     */
    fun deleteTodayStory() = story.deleteTodayStory()

    /** إعادة توليد قصة اليوم: تحذف الحالية ثم تطلب واحدة جديدة من النموذج. */
    fun regenerateTodayStory(onDone: (ArchivedStory?) -> Unit = {}) =
        story.regenerateTodayStory(onDone)

    // ----- Daily tasks (adaptive) -----
    // الخطة تُولَّد يومياً من حالة المتعلّم الحقيقية عبر [AdaptiveTasks]، فلا
    // تُطلب مهمة مستحيلة (محادثة بلا دروس، اختبار بلا كلمات) ولا تبقى ثابتة.
    val dailyTasks = mutableStateListOf<DailyTask>()

    /** اليوم الذي بُنيت له الخطة الحالية — يمنع إعادة البناء داخل نفس اليوم. */
    /** شرح مختصر لسبب حجم خطة اليوم. */
    val planRationale: String get() = dailyPlan.planRationale
    val learnerTier: LearnerTier get() = dailyPlan.learnerTier
    fun rebuildDailyPlan(force: Boolean = false) = dailyPlan.rebuildDailyPlan(force)

    var lastActiveDay by mutableStateOf(0L)
        internal set

    /**
     * Reset the per-day counters when the calendar day changes.
     *
     * Without this, a task completed yesterday still shows as done today and
     * the dashboard wrongly claims "you finished today's plan" right after
     * install/restore. Called on load and whenever the dashboard is composed.
     */
    fun applyDayRollover() {
        val today = Telemetry.today()
        if (lastActiveDay == today) return
        val hadPrevious = lastActiveDay > 0L
        lastActiveDay = today
        if (hadPrevious) {
            // New day: clear task progress and today's review counter, then
            // regenerate the plan so it reflects the learner's new state.
            for (i in dailyTasks.indices) dailyTasks[i] = dailyTasks[i].copy(progress = 0)
            totalReviewsToday = 0
            microHabitProgress = 0
            dayEarnedStreak = false
            rebuildDailyPlan(force = true)
            // A missed day breaks the streak — but a Streak Freeze can absorb it.
            val rawStreak = Telemetry.currentStreak(dayStats)
            if (rawStreak == 0 && streak > 0 && wallet.streakFreezes > 0) {
                // Spend one shield: the streak survives a single missed day.
                wallet = wallet.spendFreeze()
                lastFreezeUsedDay = today
                com.zmastery.english.notify.Notifier.achievement(
                    getApplication(),
                    "درع تجميد السلسلة عمل!",
                    "فاتك يوم لكن الدرع حماك — سلسلتك ($streak) لا تزال قائمة. تبقّى ${wallet.streakFreezes} درع.",
                )
            } else {
                // المرحلة الرابعة: بدل واجهة اللوم، نطلق مهمة إنقاذ فورية.
                val broken = streak
                streak = rawStreak
                if (rawStreak == 0 && broken > 0) maybeOfferRescue(broken)
            }
            persist()
        }
    }

    fun completeTask(id: String, amount: Int = 1) {
        val i = dailyTasks.indexOfFirst { it.id == id }
        if (i >= 0) {
            val t = dailyTasks[i]
            if (!t.done) {
                dailyTasks[i] = t.copy(progress = (t.progress + amount).coerceAtMost(t.target))
                if (dailyTasks[i].done) {
                    xp += 30
                    motivationLevel = (motivationLevel + 0.05f).coerceAtMost(1f)
                    // Celebrate the individual task
                    com.zmastery.english.notify.Notifier.achievement(
                        getApplication(),
                        "أحسنت! أكملت مهمة",
                        "${t.title} \u2705 حصلت على 30 نقطة XP. واصل التقدم!",
                    )
                    // إتمام كل المهام هو *شرط* اليوم — نمرّره لمقيّم الحماسة
                    // الذي يتحقق من الشروط الحقيقية قبل احتساب اليوم.
                    evaluateStreakDay()
                    syncNotifState()
                }
            }
        }
    }

    /** Explicitly flush pending state to disk immediately (e.g. on app pause). */
    fun flush() = persist()

    val tasksDone: Int get() = dailyTasks.count { it.done }

    // ======================================================================
    //  احتساب يوم الحماسة — بشروط حقيقية لا بمجرد لمس زر
    // ======================================================================
    // القاعدة القديمة: أي درس مكتمل ⇒ اليوم محسوب فوراً. النتيجة: سلسلة
    // متضخّمة لا تعكس جهداً حقيقياً.
    //
    // القاعدة الجديدة: اليوم لا يُحتسب إلا بعد تحقّق **شرط اليوم** فعلياً،
    // وهو أحد أمرين:
    //   • إتمام كل مهام الخطة المتكيّفة (وهي مهام واقعية بحجم مناسب), أو
    //   • بلوغ حد النشاط الحقيقي المقاس من التيليمتري (احتياط للمستخدم
    //     الذي يدرس خارج الخطة).
    // وبما أن الخطة نفسها تتكيّف مع مستوى المتعلّم، فالشرط عادل من اليوم الأول.

    /** هل حُسب اليوم الحالي ضمن السلسلة؟ (يُصفَّر عند تغيّر اليوم) */
    var dayEarnedStreak by mutableStateOf(false)
        internal set

    /**
     * الحد الأدنى من النشاط المقاس الذي يُعتبر "يوم دراسة" حتى لو لم تُكمل
     * الخطة. يتكيّف مع مرحلة المتعلّم بدل أن يكون رقماً ثابتاً.
     */
    val streakActivityThreshold: Int
        get() = when (learnerTier) {
            LearnerTier.SEED -> 3        // 3 نقاط نشاط تكفي في يوم التأسيس
            LearnerTier.SPROUT -> 6
            LearnerTier.GROWING -> 10
            LearnerTier.ESTABLISHED -> 14
        }

    /** نقاط نشاط اليوم المقاسة من التيليمتري (مراجعات + دروس + قراءة…). */
    val todayActivityScore: Int
        get() {
            val t = todayStat
            return t.reviews +
                t.lessonsCompleted * 5 +
                t.examsTaken * 6 +
                t.storiesRead * 3 +
                t.wordsAdded * 2 +
                t.conversationTurns +
                t.studyMinutes / 5
        }

    /** هل تحقّق شرط اليوم؟ (خطة مكتملة أو نشاط كافٍ) */
    val streakConditionMet: Boolean
        get() = planCompleteToday || todayActivityScore >= streakActivityThreshold

    /** وصف الشرط المتبقّي — يُعرض للمستخدم بشفافية. */
    val streakConditionLabel: String
        get() = when {
            dayEarnedStreak -> "تم تأمين يومك ✅"
            planCompleteToday -> "أنهيت خطة اليوم"
            activeDailyTasks.isEmpty() -> "أضف محتوى لتبدأ خطة اليوم"
            else -> {
                val remaining = activeDailyTasks.size - activeTasksDone
                "أكمل $remaining " +
                    (if (remaining == 1) "مهمة" else "مهام") +
                    " لتأمين يومك"
            }
        }

    /** تقدّم شرط اليوم 0..1 — لشريط التقدّم في رأس الشاشة. */
    val streakConditionProgress: Float
        get() {
            if (dayEarnedStreak || streakConditionMet) return 1f
            val byPlan = if (activeDailyTasks.isEmpty()) 0f
            else activeTasksDone.toFloat() / activeDailyTasks.size
            val byActivity = if (streakActivityThreshold <= 0) 0f
            else todayActivityScore.toFloat() / streakActivityThreshold
            return maxOf(byPlan, byActivity).coerceIn(0f, 1f)
        }

    /**
     * يقيّم شرط اليوم ويحتسبه في السلسلة عند تحقّقه (مرة واحدة فقط).
     * يُستدعى بعد كل نشاط مؤثّر.
     */
    fun evaluateStreakDay() {
        if (dayEarnedStreak) return
        if (!streakConditionMet) return
        dayEarnedStreak = true
        // السلسلة تبقى مشتقّة من التيليمتري (لا تُزاد بشكل أعمى) لتظل صادقة
        // عبر إعادة التثبيت والاستعادة والأيام الفائتة.
        streak = Telemetry.currentStreak(dayStats).coerceAtLeast(1)
        grantXp(40)
        com.zmastery.english.notify.Notifier.achievement(
            getApplication(),
            "يوم مؤمَّن! \uD83D\uDD25",
            "حقّقت شرط اليوم وسلسلتك الآن $streak " +
                (if (streak == 1) "يوم" else "أيام") + " متتالية. إنجاز رائع!",
        )
        syncNotifState()
        persist()
    }

    // ======================================================================
    //  Engagement — honest, derived learner state
    // ======================================================================

    /** True once the learner has any real content (lessons or dictionary words). */
    val hasContent: Boolean get() = activeVocab.isNotEmpty() || lessons.isNotEmpty()

    /** True once any activity has ever been recorded. */
    val hasHistory: Boolean get() = dayStats.values.any { it.isActive }

    /** Today's progress toward the review goal, 0..1. */
    val todayGoalProgress: Float
        get() = if (dailyGoal <= 0) 0f
        else (totalReviewsToday.toFloat() / dailyGoal).coerceIn(0f, 1f)

    /**
     * Daily tasks that are actually ACHIEVABLE right now.
     *
     * A fresh install has no words and no lessons, so "review 20 words" is not
     * a real task — showing it (and counting it toward completion) is what made
     * the app claim the plan was finished on day one.
     */
    val activeDailyTasks: List<DailyTask>
        get() = dailyTasks.filter { t ->
            when (t.id) {
                "review" -> activeVocab.isNotEmpty()
                "lesson" -> lessons.any { !it.isCompleted }
                "quiz" -> activeVocab.size >= 4
                "story" -> storySeedCount >= 2 || todayStory != null
                "speak" -> true // café scene is always available
                else -> true
            }
        }

    val activeTasksDone: Int get() = activeDailyTasks.count { it.done }

    /** True only when there is real work scheduled AND all of it is finished. */
    val planCompleteToday: Boolean
        get() = activeDailyTasks.isNotEmpty() && activeTasksDone >= activeDailyTasks.size

    /** The single derived snapshot every engagement surface reads. */
    val engagement: Engagement
        get() = EngagementEngine.derive(
            rows = dayStats,
            hasContent = hasContent,
            goalProgress = todayGoalProgress,
            planDone = planCompleteToday,
            planHasTasks = activeDailyTasks.isNotEmpty(),
        )

    /** Last 7 days as (date, wasActive) for the streak dot rail. */
    val weekDots: List<Pair<java.time.LocalDate, Boolean>>
        get() = EngagementEngine.weekDots(dayStats)

    // ======================================================================
    //  المرحلة الأولى — 3D Momentum Model
    // ======================================================================
    // Three independent axes so one missed day can never erase the whole story:
    //   🔥 streak (engine) · 🌱 continuity (shield) · ⭐ mastery (quality gauge)

    /** Learner's display name — personalises the AI wisdom cards. */
    var learnerName by mutableStateOf("")

    /** Learner's email (for future Firebase Auth integration). */
    var learnerEmail by mutableStateOf("")

    /** The chosen micro-habit (الورد اليومي) that keeps the streak winnable. */
    var microHabitId by mutableStateOf(MicroHabits.all.first().id)
        internal set

    /** Progress on today's micro-habit. */
    var microHabitProgress by mutableStateOf(0)
        internal set

    /** Epoch-day a Streak Freeze was last spent (for the UI notice). */
    var lastFreezeUsedDay by mutableStateOf(0L)
        internal set

    val microHabit: MicroHabit
        get() = MicroHabits.byId(microHabitId) ?: MicroHabits.all.first()

    val microHabitDone: Boolean get() = microHabitProgress >= microHabit.target

    val microHabitFraction: Float
        get() = if (microHabit.target <= 0) 0f
        else (microHabitProgress.toFloat() / microHabit.target).coerceIn(0f, 1f)

    fun setMicroHabit(id: String) = gamification.setMicroHabit(id)

    /**
     * Advance the micro-habit. Called by real activity (a review, a listen, a
     * story page, a new word) so the 3–5 minute daily ورد completes naturally.
     */
    fun advanceMicroHabit(id: String, amount: Int = 1) = gamification.advanceMicroHabit(id, amount)

    /** The single derived 3D snapshot every momentum surface reads. */
    val momentum: Momentum3D
        get() = MomentumEngine.derive(
            rows = dayStats,
            microHabitDone = microHabitDone,
            masteredWords = masteredCount,
            totalWords = totalWords,
            lessonsDone = completedLessons,
            totalLessons = totalLessons,
            examAvg = lifetime.examAvg,
        )

    /** The 30 continuity cells (oldest → today) for the shield visual. */
    val continuityCells: List<Boolean>
        get() = MomentumEngine.continuityCells(dayStats)

    // ======================================================================
    //  المرحلة الثانية — Seven Seals (Z-Mastery Enigma)
    // ======================================================================

    /** tierId -> the record of it being opened. */
    val openedChests = mutableStateMapOf<String, ChestRecord>()

    /** Everything the learner currently owns. */
    var wallet by mutableStateOf(PerkWallet())
        internal set

    /**
     * The streak that QUALIFIES for seals.
     *
     * Deliberately the learner's BEST streak, not the current one: a seal that
     * was genuinely earned must never be revoked by a later missed day. Taking
     * a reward away is precisely the punishment the 3D model exists to prevent.
     */
    val chestQualifyingStreak: Int
        get() = maxOf(momentum.streak, momentum.bestStreak)

    /** Chests whose day requirement is met but which are still SEALED. */
    val pendingChests: List<ChestTier>
        get() = SevenSeals.earned(chestQualifyingStreak).filter { it.id !in openedChests }

    val hasPendingChest: Boolean get() = pendingChests.isNotEmpty()

    /** The next seal being worked toward. */
    val nextChest: ChestTier? get() = SevenSeals.next(chestQualifyingStreak)

    val chestProgress: Float get() = SevenSeals.progressToNext(chestQualifyingStreak)

    /** Days remaining until the next seal breaks (measured on the live streak). */
    val daysToNextChest: Int
        get() = nextChest?.let { (it.day - momentum.streak).coerceAtLeast(0) } ?: 0

    fun isChestOpened(tierId: String): Boolean = tierId in openedChests

    fun chestRecord(tierId: String): ChestRecord? = openedChests[tierId]

    /** Fires a notification when a new seal becomes available. */
    internal fun checkChests() = gamification.checkChests()

    /**
     * Open a sealed chest: reveal the AI wisdom card and APPLY every perk.
     * Idempotent — reopening returns the cached record without re-granting.
     */
    fun openChest(tierId: String): ChestRecord? = gamification.openChest(tierId)

    // ======================================================================
    //  الصناديق الغامضة — الحالة والمؤشرات الثلاثة
    // ======================================================================
    // نظام مستقل خفيف يعمل بجوار السبعة أختام: يحفظ حالة كل صندوق
    // (خامل / مستحق / مفتوح) مع تقريره المولَّد، ولا يُعيد قفل شيء أبداً.

    /** كل الصناديق الغامضة بحالتها الحالية. */
    val mysteryRewards = mutableStateListOf<MysteryReward>()

    /** آخر صندوق فُتح للتو — يقود نافذة الكشف الاحتفالية. */
    var justOpenedReward by mutableStateOf<MysteryReward?>(null)

    /**
     * المؤشرات الثلاثة (🔥 شعلة · 🌱 استمرارية · ⭐ إتقان).
     * دالة get() نقيّة تُشتق من الصفوف اليومية، فلا تتعارض أبداً مع الواقع.
     */
    val metrics: MomentumMetrics
        get() = MomentumMetricsEngine.computeMetrics(
            rows = dayStats,
            masteredWords = masteredCount,
            totalWords = totalWords,
            lessonsDone = completedLessons,
            totalLessons = totalLessons,
            examAvg = lifetime.examAvg,
        )

    /** الصناديق المستحقة والمختومة (جاهزة للكسر). */
    val sealedRewards: List<MysteryReward>
        get() = mysteryRewards.filter { it.isSealed }

    val hasSealedReward: Boolean get() = sealedRewards.isNotEmpty()

    val openedRewardCount: Int get() = mysteryRewards.count { it.isOpened }

    /** المعلم التالي الذي يسعى إليه المتعلّم. */
    val nextMilestone: MysteryCatalog.Milestone?
        get() = MysteryCatalog.next(chestQualifyingStreak)

    /** التقدّم 0..1 نحو المعلم التالي. */
    val milestoneProgress: Float
        get() = MysteryCatalog.progressToNext(chestQualifyingStreak)

    /**
     * يزامن الصناديق مع السلسلة الحالية. يُستدعى عند كل نشاط حقيقي.
     * لا يُعيد قفل صندوق مفتوح ولا يسحب استحقاقاً سابقاً.
     */
    fun syncMysteryRewards(notify: Boolean = true) = gamification.syncMysteryRewards(notify)

    /** يمنح صندوق إتمام كورس (يُستدعى عند إنهاء آخر درس في الكورس). */
    fun grantCourseReward(courseId: Int, courseName: String, lessonCount: Int) = gamification.grantCourseReward(courseId, courseName, lessonCount)

    /**
     * يكسر ختم صندوق ويطبّق جوائزه. عملية idempotent تماماً:
     * إعادة الفتح تُعيد نفس الصندوق دون منح الجوائز مرة أخرى.
     */
    fun openMysteryReward(id: String): MysteryReward? = gamification.openMysteryReward(id)

    // ======================================================================
    //  الجزء 4 · مرآة الإدراك بالذكاء الاصطناعي (Gemini 2.0 Flash)
    // ======================================================================

    /** true أثناء توليد تقرير الصندوق بالذكاء الاصطناعي. */
    var isChestMirrorLoading by mutableStateOf(false)
        internal set

    /** معرّف الصندوق الذي يُولَّد تقريره الآن (لعرض مؤشّر داخل بطاقته). */
    var chestMirrorLoadingId by mutableStateOf<String?>(null)
        internal set

    /** يجمع الإحصاءات الحقيقية التي تُغذّي المطالبة. */
    fun mirrorStatsFor(milestoneTitle: String): MirrorStats = gamification.mirrorStatsFor(milestoneTitle)

    /**
     * يستبدل التقرير المحلّي بتقرير Gemini المخصّص، ويحفظه داخل الصندوق نهائياً.
     * أي فشل يُترك بصمت — التقرير المحلّي الجميل معروض بالفعل.
     */

    /** إعادة توليد تقرير صندوق مفتوح يدوياً (زر "أعد التوليد"). */
    fun regenerateRewardMirror(rewardId: String) = gamification.regenerateRewardMirror(rewardId)

    /** يمسح إشارة "فُتح للتو" بعد انتهاء مراسم الاحتفال. */
    fun clearJustOpened() = gamification.clearJustOpened()

    // ======================================================================
    //  المرحلة الثالثة — مرآة الإدراك (AI Cognitive Mirroring)
    // ======================================================================

    /** tierId -> التقرير المولّد (يُخزَّن فلا يتغيّر عند إعادة الفتح). */
    val mirrorReports = mutableStateMapOf<String, MirrorReport>()

    /** جارٍ توليد تقرير من Gemini الآن. */
    var isMirrorLoading by mutableStateOf(false)
        internal set

    /** آخر رسالة حالة لتوليد المرآة. */
    var mirrorMessage by mutableStateOf<String?>(null)

    /**
     * البصمة المعرفية الحيّة — مشتقّة بالكامل من التيليمتري.
     * دالة get() نقيّة: لا تُخزَّن فلا تتعارض أبداً مع الواقع.
     */
    val cognitiveMirror: CognitiveMirror
        get() = EnigmaStreakEngine.computeMirror(
            logs = reviewLogs,
            hours = reviewHours,
            leeches = forgottenWords,
            dayRows = dayStats,
        )

    fun mirrorReport(tierId: String): MirrorReport? = mirrorReports[tierId]

    /**
     * يولّد تقرير مرآة الإدراك لصندوق مفتوح.
     * يستدعي Gemini عند توفّر مفتاح، وإلا يولّد التقرير محلياً فوراً.
     * النتيجة تُحفظ فلا تُستهلك حصة الـ API مرتين لنفس الصندوق.
     */
    fun generateMirrorReport(tierId: String, force: Boolean = false) = gamification.generateMirrorReport(tierId, force)

    // ======================================================================
    //  المرحلة الرابعة — هندسة الخوف من السقوط والتعافي
    // ======================================================================

    /** أعلى سلسلة قبل الانكسار — مرجع الاستعادة. */
    var streakBeforeBreak by mutableStateOf(0)
        internal set

    /** مهمة الإنقاذ النشطة. */
    var rescue by mutableStateOf(RescueMission())
        internal set

    /** آخر يوم عُرضت فيه مهمة إنقاذ. */
    var lastRescueOfferDay by mutableStateOf(0L)
        internal set

    /**
     * حالة "صندوق اليوم الباكي".
     * تُقرأ عند كل إعادة تركيب — لا مؤقتات ولا خدمات، فلا استهلاك بطارية.
     */
    val decayState: DecayState
        get() {
            val now = java.time.LocalTime.now()
            return EnigmaStreakEngine.computeDecay(
                minimumDone = microHabitDone || tasksDone >= dailyTasks.size,
                currentStreak = momentum.streak,
                hourNow = now.hour,
                minuteNow = now.minute,
                streakBroken = rescue.isActive && !rescue.completed,
            )
        }

    val isChestCracking: Boolean get() = decayState.isCracking

    /**
     * هل تُعرض بوابة الإنقاذ البنفسجية الآن؟
     *
     * المهمة صالحة ليومين فقط من تاريخ عرضها: بعد ذلك تنتهي صلاحيتها بهدوء
     * فلا تبقى بوابة قديمة معلّقة على الواجهة إلى الأبد. (المهمة المكتملة
     * تبقى معروضة دائماً حتى يستلم المتعلّم مكافأته.)
     */
    val showRescueGate: Boolean
        get() {
            val r = rescue
            if (!r.isActive) return false
            if (r.completed) return true
            return (Telemetry.today() - r.offeredEpochDay) <= RESCUE_VALID_DAYS
        }

    private val RESCUE_VALID_DAYS = 2L

    /**
     * يُستدعى عند تدوير اليوم: إذا انكسرت سلسلة معتبرة (> 2 أيام) ولم يحمها
     * درع، نطلق مهمة إنقاذ بدل إظهار واجهة لوم.
     */
    internal fun maybeOfferRescue(brokenStreak: Int) = gamification.maybeOfferRescue(brokenStreak)

    /**
     * يبدأ العدّ التنازلي (3 دقائق) ويصفّر التقدّم.
     * يُستدعى من زر «ابدأ الإنقاذ» قبل الانتقال لشاشة المراجعة.
     */
    fun startRescueTimer() = gamification.startRescueTimer()

    /**
     * يُنهي محاولة انتهت مهلتها: يصفّر العدّاد ويسمح بإعادة المحاولة فوراً.
     * لا عقوبة ولا فقدان — الهدف هو الإلحاح لا الإحباط.
     */
    fun timeoutRescue() = gamification.timeoutRescue()

    /** يسجّل تقدّماً في مهمة الإنقاذ (يُستدعى من شاشة المراجعة). */
    fun advanceRescue(amount: Int = 1) = gamification.advanceRescue(amount)

    /**
     * استلام مكافأة الإنقاذ: تعود الشعلة القديمة كاملة.
     * @return السلسلة المستعادة، أو 0 عند الفشل.
     */
    fun claimRescue(): Int = gamification.claimRescue()

    /** تجاهل مهمة الإنقاذ (يبقى الخيار للمتعلّم دائماً). */
    fun dismissRescue() = gamification.dismissRescue()

    /** Spend a sponsor gift (rewards generosity — a real retention driver). */
    fun useSponsorGift(): Boolean = gamification.useSponsorGift()

    /**
     * Central XP grant. Honours an active ×2 multiplier so the Seven Seals perk
     * is genuinely felt. All XP should flow through here.
     */
    fun grantXp(amount: Int, applyMultiplier: Boolean = true) {
        if (amount <= 0) return
        val mult = if (applyMultiplier && wallet.multiplierActive()) 2 else 1
        val gained = amount * mult
        xp += gained
        track { it.xpEarned += gained }
    }

    // ----- Derived stats -----
    /** Words that have been approved into the active dictionary. */
    val activeVocab: List<VocabWord> get() = vocab.filter { !it.pendingApproval }

    /** Words still awaiting approval for a given lesson. */
    fun pendingWordsForLesson(lessonId: Int): List<VocabWord> = wordReview.pendingWordsForLesson(lessonId)

    val dueWords: List<VocabWord>
        get() {
            val today = todayEpochDay()
            return activeVocab.filter { w ->
                when {
                    w.totalReviews == 0 -> true
                    else -> {
                        val elapsed = if (w.lastReviewedDay < 0) 0.0 else (today - w.lastReviewedDay).toDouble()
                        Fsrs.retrievability(elapsed, w.stability) <= desiredRetention
                    }
                }
            }
        }

    /** Leeches — words forgotten repeatedly (high lapses, low stability). */
    val forgottenWords: List<VocabWord>
        get() = activeVocab.filter { it.lapses >= 2 && it.stability < 7.0 }
            .sortedByDescending { it.lapses }
            .take(5)

    val masteredCount: Int get() = activeVocab.count { it.mastered }
    val totalWords: Int get() = activeVocab.size

    /**
     * Approve a subset of a lesson's pending words into the active dictionary,
     * and discard the rest. Called from the word-approval sheet on completion.
     */
    fun approveWords(lessonId: Int, approvedIds: Set<Int>) = wordReview.approveWords(lessonId, approvedIds)

    val completedLessons: Int get() = lessons.count { it.isCompleted }
    val totalLessons: Int get() = lessons.size
    var accuracy by mutableStateOf(0) // exam accuracy avg (updated by quiz)
        internal set

    fun recordExamResult(correct: Int, total: Int) {
        if (total <= 0) return
        val pct = correct * 100 / total
        accuracy = if (accuracy == 0) pct else (accuracy + pct) / 2
        studyHours += 0.1
        completeTask("quiz")
        persist()
    }

    // ======================================================================
    //  Exams — studied, weakness-driven assessment
    // ======================================================================

    /** Finished exams, newest last. Powers history + the trend chart. */
    val examHistory = mutableStateListOf<ExamRecord>()

    /** wordId → how many times it was answered wrong in an exam. */
    val examMisses = mutableStateMapOf<Int, Int>()

    /** The exam currently being taken (empty = none in progress). */
    val examQuestions = mutableStateListOf<ExamQuestion>()
    var examMode by mutableStateOf(ExamMode.SMART)
        internal set
    var examTitle by mutableStateOf("")
        internal set
    var examQuestionCount by mutableStateOf(10)

    /** Lessons the learner has actually completed — the only exam material. */
    val doneLessons: List<Lesson> get() = lessons.filter { it.isCompleted }

    /** Courses that have at least one completed lesson. */
    val examableCourses: List<Course>
        get() {
            val ids = doneLessons.map { it.courseId }.toSet()
            return courses.filter { it.id in ids }
        }

    /** Completed lessons for a course, ordered. */
    fun completedLessonsOf(courseId: Int): List<Lesson> =
        doneLessons.filter { it.courseId == courseId }.sortedBy { it.no }

    /** Words eligible for exams (approved + from completed lessons or reviewed). */
    val examableWords: List<VocabWord>
        get() {
            val done = doneLessons.map { it.id }.toSet()
            return activeVocab.filter { it.lessonId == 0 || it.lessonId in done || it.totalReviews > 0 }
        }

    /** Weakness-ranked words — drives the "نقاط ضعفك" panel. */
    val weakestWords: List<WeakWord> get() = exam.weakestWords

    /** True when there is enough studied material for a given mode. */
    fun canTakeExam(mode: ExamMode): Boolean = exam.canTakeExam(mode)

    /** How many questions a mode could actually produce right now (for badges). */
    fun examAvailability(mode: ExamMode): Int = exam.examAvailability(mode)

    /**
     * Build and start an exam. Returns the number of questions created
     * (0 = not enough material).
     */
    fun startExam(
        mode: ExamMode,
        count: Int = examQuestionCount,
        courseId: Int? = null,
        lessonId: Int? = null,
    ): Int = exam.startExam(mode, count, courseId, lessonId)

    fun clearExam() = exam.clearExam()

    /** Record a single answer. A wrong answer feeds both the miss counter and
     *  the FSRS scheduler (see [ExamsController.recordExamAnswer]). */
    fun recordExamAnswer(q: ExamQuestion, correct: Boolean) = exam.recordExamAnswer(q, correct)

    /** Finish an exam: store the record, update accuracy, award XP. */
    fun finishExam(
        correct: Int,
        total: Int,
        durationMs: Long,
        skillCorrect: Map<ExamSkill, Int>,
        skillTotal: Map<ExamSkill, Int>,
    ) = exam.finishExam(correct, total, durationMs, skillCorrect, skillTotal)

    /** Lifetime average across all recorded exams. */
    val examAverage: Int get() = exam.examAverage

    val examBest: Int get() = exam.examBest

    /** Aggregated accuracy per skill across all exams — powers the radar list. */
    val skillAccuracy: Map<ExamSkill, Float> get() = exam.skillAccuracy

    /** The weakest skill overall, or null when there is no data yet. */
    val weakestSkill: ExamSkill? get() = exam.weakestSkill

    // ----- Daily plan (synced from roadmap) -----
    val todayPlan: List<PlanItem> get() = progress.todayPlan
    fun courseTotal(courseId: Int): Int = progress.courseTotal(courseId)
    fun courseDone(courseId: Int): Int = progress.courseDone(courseId)
    fun courseImported(courseId: Int): Int = progress.courseImported(courseId)
    fun courseCompletion(courseId: Int): Float = progress.courseCompletion(courseId)
    fun courseCoverage(courseId: Int): Float = progress.courseCoverage(courseId)
    fun courseProgress(courseId: Int): Pair<Int, Int> = progress.courseProgress(courseId)

    /** Aggregate stats for a whole level, computed over curriculum size. */
    data class LevelStats(
        val done: Int,        // lessons completed
        val imported: Int,    // lessons available on device
        val total: Int,       // curriculum size (sum of course targets)
        val courseCount: Int,
        val coursesStarted: Int,
        val coursesDone: Int,
    ) {
        /** Real progress through the level's progress. */
        val completion: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
        /** Share of the curriculum that has been imported. */
        val coverage: Float get() = if (total <= 0) 0f else (imported.toFloat() / total).coerceIn(0f, 1f)
        /** Completion measured only against what is actually available. */
        val completionOfImported: Float get() = if (imported <= 0) 0f else (done.toFloat() / imported).coerceIn(0f, 1f)
    }

    fun levelStats(levelId: Int): LevelStats {
        val lvlCourses = courses.filter { it.levelId == levelId }
        var done = 0
        var imported = 0
        var total = 0
        var started = 0
        var finished = 0
        lvlCourses.forEach { c ->
            val d = courseDone(c.id)
            val i = courseImported(c.id)
            val t = courseTotal(c.id)
            done += d; imported += i; total += t
            if (d > 0) started++
            if (t > 0 && d >= t) finished++
        }
        return LevelStats(done, imported, total, lvlCourses.size, started, finished)
    }

    /** Real level completion (completed lessons ÷ full curriculum of the level). */
    fun levelProgress(levelId: Int): Float = levelStats(levelId).completion

    /** Overall completion across every level in the progress. */
    val overallCompletion: Float get() = progress.overallCompletion
    fun coursesForLevel(levelId: Int) = progress.coursesForLevel(levelId)

    fun toggleLesson(lessonId: Int) = lessonReview.toggleLesson(lessonId)
    fun isCompleted(lessonId: Int) = lessonReview.isCompleted(lessonId)
    fun wordsFromLesson(lessonId: Int): List<VocabWord> = lessonReview.wordsFromLesson(lessonId)
    fun uncompleteLesson(lessonId: Int, alsoRemoveWords: Boolean): Int =
        lessonReview.uncompleteLesson(lessonId, alsoRemoveWords)
    val lessonsToReview: List<Lesson> get() = lessonReview.lessonsToReview
    fun reviewLesson(lessonId: Int, mastery: Int, forgottenWordIds: List<Int> = emptyList()) =
        lessonReview.reviewLesson(lessonId, mastery, forgottenWordIds)

    val reviewLogs = mutableStateListOf<ReviewLog>()

    /** سقف الأحداث المحفوظة — يمنع تضخّم ملف الحالة مهما طال الاستخدام. */
    private val SIGNAL_CAP = 400

    // ----- FSRS configuration -----
    /** Target recall probability (Anki default 0.90). User-tunable. */
    var desiredRetention by mutableStateOf(0.90)
    var maxIntervalDays by mutableStateOf(365)
    internal fun todayEpochDay(): Long = java.time.LocalDate.now().toEpochDay()

    /**
     * Records a full 4-stage review using the FSRS scheduler.
     *
     * The recall source maps to an FSRS rating (Again/Hard/Good/Easy). We first
     * apply a *stage penalty*: if the learner only recalled after seeing more
     * hints than the memory strength warranted, the rating is softened — this
     * captures how hard the retrieval actually was, improving accuracy.
     */
    fun reviewWord(
        wordId: Int, source: RecallSource, reachedStage: Int = 4,
        replays: Int = 0, timeMs: Long = 0L, explicitGrade: Int? = null,
    ) = wordReview.reviewWord(wordId, source, reachedStage, replays, timeMs, explicitGrade)

    val dayStats = mutableStateMapOf<Long, DayStat>()

    /** Hour-of-day of every review, used to find the learner's peak hour. */
    val reviewHours = mutableStateListOf<Int>()

    /** Mutate today's row. All counters are additive. */
    internal fun track(mutate: (DayStat) -> Unit) {
        val d = Telemetry.today()
        val row = dayStats[d] ?: DayStat(d)
        mutate(row)
        // Reassign to trigger Compose snapshot invalidation.
        dayStats[d] = row.copy()
        // كل نشاط حقيقي قد يُحقّق شرط اليوم — نقيّمه فوراً (مرّة واحدة).
        if (!dayEarnedStreak) evaluateStreakDay()
    }

    val todayStat: DayStat get() = dayStats[Telemetry.today()] ?: DayStat(Telemetry.today())
    val yesterdayStat: DayStat get() = dayStats[Telemetry.today() - 1] ?: DayStat(Telemetry.today() - 1)

    fun spanFor(scope: CoachScope): StatSpan = telemetry.spanFor(scope)
    fun previousSpanFor(scope: CoachScope): StatSpan = telemetry.previousSpanFor(scope)
    fun heatmapDays(n: Int = 119): List<DayStat> = telemetry.heatmapDays(n)
    val activityStreak: Int get() = telemetry.activityStreak
    val bestActivityStreak: Int get() = telemetry.bestActivityStreak
    val lifetime: StatSpan get() = telemetry.lifetime
    val grammarAccuracy: Float get() = telemetry.grammarAccuracy
    val skillRadar: List<SkillScore> get() = telemetry.skillRadar
    val cefrEstimate: Pair<String, Float> get() = telemetry.cefrEstimate
    val curriculum: CurriculumReport get() = telemetry.curriculum
    val peakStudyHour: Int? get() = telemetry.peakStudyHour
    fun trackListening(seconds: Long) = telemetry.trackListening(seconds)
    fun beginStudySession(label: String) = telemetry.beginStudySession(label)
    fun endStudySession() = telemetry.endStudySession()
    fun trackStoryRead() = telemetry.trackStoryRead()
    fun trackConversationTurn(n: Int = 1) = telemetry.trackConversationTurn(n)
    fun trackPhoneticsDrill(n: Int = 1) = telemetry.trackPhoneticsDrill(n)

    var studyPlan by mutableStateOf(StudyPlanDto())

    /** Highest level that has any lessons — the natural default target. */
    val effectivePlan: StudyPlanDto get() = roadmap.effectivePlan
    val planSummary: PlanSummary get() = roadmap.planSummary
    val planTimeline: List<PlanDay> get() = roadmap.planTimeline
    val planToday: PlanDay? get() = roadmap.planToday
    fun savePlan(plan: StudyPlanDto) = roadmap.savePlan(plan)
    fun resetPlan() = roadmap.resetPlan()

    val coachReports = mutableStateMapOf<String, CoachReport>()

    var isCoaching by mutableStateOf(false)
        internal set
    var coachError by mutableStateOf<String?>(null)

    fun coachReport(scope: CoachScope): CoachReport? = coach.coachReport(scope)
    fun runCoach(scope: CoachScope) = coach.runCoach(scope)
    fun quickCoach(scope: CoachScope = CoachScope.WEEKLY): CoachReport = coach.quickCoach(scope)

    val avgReplaysPerWord: Float
        get() = if (reviewLogs.isEmpty()) 0f else reviewLogs.map { it.replays }.average().toFloat()
    val avgSecondsPerWord: Float
        get() = if (reviewLogs.isEmpty()) 0f else reviewLogs.map { it.timeMs }.average().toFloat() / 1000f

    /** Predicted retention right now = mean retrievability across all seen words. */
    val predictedRetention: Float
        get() {
            val seen = vocab.filter { it.totalReviews > 0 }
            if (seen.isEmpty()) return 0f
            val today = todayEpochDay()
            return seen.map {
                val elapsed = if (it.lastReviewedDay < 0) 0.0 else (today - it.lastReviewedDay).toDouble()
                Fsrs.retrievability(elapsed, it.stability)
            }.average().toFloat()
        }

    /** True recall rate observed in reviews (grade ≥ 2 counts as recalled). */
    val trueRecallRate: Float
        get() = if (reviewLogs.isEmpty()) 0f else reviewLogs.count { it.grade >= 2 }.toFloat() / reviewLogs.size

    /** Average memory stability (days) across studied words. */
    val avgStability: Float
        get() {
            val seen = vocab.filter { it.totalReviews > 0 }
            return if (seen.isEmpty()) 0f else seen.map { it.stability }.average().toFloat()
        }

    /** Count of words currently due for review (retrievability-aware). */
    val dueCount: Int get() = dueWords.size

    // ======================================================================
    //  Stage-based quick rating (single visible "تذكرتها" button)
    // ======================================================================
    // The learner sees ONE positive action at every stage: "تذكرتها".
    // Behind the scenes the stage at which they pressed it decides both the
    // recall source (what cue unlocked the memory) and the FSRS rating:
    //
    //   Stage 1 (audio only)          → SOUND  → 4 Easy   (strongest memory)
    //   Stage 2 (+ mental image)      → IMAGE  → 3 Good
    //   Stage 3 (+ word & example)    → TEXT   → 2 Hard
    //   Stage 4 (full reveal, forgot) → FAILED → 1 Again
    //
    // This keeps the UI to a single tap while preserving full FSRS fidelity.

    /** The implicit recall source for recalling at [stage] (1..4). */
    fun sourceForStage(stage: Int): RecallSource = wordReview.sourceForStage(stage)
    fun gradeForStage(stage: Int): Int = wordReview.gradeForStage(stage)
    fun reviewWordAtStage(wordId: Int, stage: Int, replays: Int = 0, timeMs: Long = 0L) =
        wordReview.reviewWordAtStage(wordId, stage, replays, timeMs)
    fun failWord(wordId: Int, reachedStage: Int = 4, replays: Int = 0, timeMs: Long = 0L) =
        wordReview.failWord(wordId, reachedStage, replays, timeMs)
    fun previewStageIntervalDays(wordId: Int, stage: Int): Int = wordReview.previewStageIntervalDays(wordId, stage)
    fun previewFailIntervalDays(wordId: Int): Int = wordReview.previewFailIntervalDays(wordId)
    fun previewIntervalWithGrade(wordId: Int, grade: Int): Int = wordReview.previewIntervalWithGrade(wordId, grade)
    fun previewIntervalDays(wordId: Int, source: RecallSource, reachedStage: Int): Int =
        wordReview.previewIntervalDays(wordId, source, reachedStage)
    fun formatInterval(days: Int): String = wordReview.formatInterval(days)
    fun generateQuiz(count: Int): List<QuizQuestion> = wordReview.generateQuiz(count)
    fun addXp(amount: Int) = wordReview.addXp(amount)
    fun addWord(english: String, arabic: String, exampleEn: String = "", exampleAr: String = "",
        phonetic: String = "", mentalImage: String = "", courseId: Int = 0): Boolean =
        wordReview.addWord(english, arabic, exampleEn, exampleAr, phonetic, mentalImage, courseId)
    fun updateWord(id: Int, english: String, arabic: String, exampleEn: String,
        exampleAr: String, phonetic: String, mentalImage: String): Boolean =
        wordReview.updateWord(id, english, arabic, exampleEn, exampleAr, phonetic, mentalImage)
    fun deleteWord(id: Int) = wordReview.deleteWord(id)

    var mnemonicVersion by mutableStateOf(0)
        internal set

    /** User-tunable generation settings (persisted). */
    var mnemonicStyle by mutableStateOf(MnemonicArtStyle.CARTOON_3D)
    var mnemonicPersona by mutableStateOf(MnemonicPersona.NONE)
    var mnemonicModel by mutableStateOf(MnemonicModel.GEMINI)
    var mnemonicNumbering by mutableStateOf(false)
    var mnemonicBatchSize by mutableStateOf(MnemonicSpec.DEFAULT_BATCH)

    // ----- The batch currently being generated (transient state) -----
    val mnemonicBatch = mutableStateListOf<VocabWord>()
    var mnemonicSpec by mutableStateOf(MnemonicSpec.forCount(MnemonicSpec.DEFAULT_BATCH))
        internal set
    var mnemonicPromptText by mutableStateOf("")
        internal set
    var mnemonicMessage by mutableStateOf<String?>(null)
    var isSlicing by mutableStateOf(false)
        internal set

    val mnemonicConfig: MnemonicConfig get() = mnemonic.mnemonicConfig
    fun hasMnemonic(wordId: Int): Boolean = mnemonic.hasMnemonic(wordId)
    fun mnemonicPath(wordId: Int): String? = mnemonic.mnemonicPath(wordId)
    val wordsMissingMnemonic: List<VocabWord> get() = mnemonic.wordsMissingMnemonic
    val mnemonicReadyCount: Int get() = mnemonic.mnemonicReadyCount
    val mnemonicMissingCount: Int get() = mnemonic.mnemonicMissingCount
    val mnemonicDiskLabel: String get() = mnemonic.mnemonicDiskLabel
    fun startMnemonicBatch(size: Int = mnemonicBatchSize, onlyIds: List<Int>? = null): Int =
        mnemonic.startMnemonicBatch(size, onlyIds)
    fun refreshMnemonicPrompt() = mnemonic.refreshMnemonicPrompt()
    fun sliceMnemonicSheet(uri: android.net.Uri, onDone: (MnemonicStore.SliceResult) -> Unit = {}) =
        mnemonic.sliceMnemonicSheet(uri, onDone)
    fun clearMnemonic(wordId: Int) = mnemonic.clearMnemonic(wordId)
    fun clearAllMnemonics(): Int = mnemonic.clearAllMnemonics()

    var lastImportSummary by mutableStateOf<String?>(null)


    fun importPackage(pkg: CoursePackage): String = importer.importPackage(pkg)
    fun importLesson(pkg: LessonPackage, rawJson: String = ""): String = importer.importLesson(pkg, rawJson)
    fun importLessons(packages: List<LessonPackage>): String = importer.importLessons(packages)

    // ── استوديو المنهج المخصص (المسؤول ينشئ منهجاً كاملاً بلا ملف) ──
    /** ينشئ منهجاً مخصصاً: كورس فارغ بمستوى أكاديمي أو مسار تخصصي جديد باسم حر. */
    fun createCustomCourse(
        name: String, type: CourseType, levelId: Int, levelName: String, target: Int,
        onResult: (Boolean, String) -> Unit,
    ) = importer.createCustomCourse(name, type, levelId, levelName, target, onResult)

    /** يضيف درساً مؤلَّفاً يدوياً من الهاتف إلى أي منهج (+ مفردات اختيارية). */
    fun addManualLesson(
        courseId: Int, title: String, summaryAr: String, readingEn: String, readingAr: String,
        vocabLines: String = "",
        onResult: (Boolean, String) -> Unit,
    ) = importer.addManualLesson(courseId, title, summaryAr, readingEn, readingAr, vocabLines, onResult)

    /** حذف درس ومفرداته (تصحيح أخطاء التأليف) — للمسؤول. */
    fun deleteLessonAdmin(lessonId: Int, onResult: (Boolean, String) -> Unit) =
        importer.deleteLessonAdmin(lessonId, onResult)

    /** حذف منهج مخصص كاملاً — للمسؤول. */
    fun deleteCustomCourse(courseId: Int, onResult: (Boolean, String) -> Unit) =
        importer.deleteCustomCourse(courseId, onResult)

    /** يلصق JSON كتبه وكيل AI ويوجّه دروسه إلى منهج محدد. */
    fun importJsonIntoCourse(courseId: Int, jsonText: String, onResult: (Boolean, String) -> Unit) =
        importer.importJsonIntoCourse(courseId, jsonText, onResult)

    internal var tts: com.zmastery.english.audio.TtsManager? = null

    /** Wire the shared TTS engine (called once from the Activity/Composition). */
    fun attachTts(engine: com.zmastery.english.audio.TtsManager) = audio.attachTts(engine)

    var isGeneratingAudio by mutableStateOf(false)
        internal set
    var audioGenTotal by mutableStateOf(0)
        internal set
    var audioGenDone by mutableStateOf(0)
        internal set
    var audioGenLabel by mutableStateOf("")
        internal set
    var lastAudioMessage by mutableStateOf<String?>(null)

    /** Count of clips still needing generation (drives the button badge). */
    val pendingAudioCount: Int get() = audio.pendingAudioCount

    val hasPendingAudio: Boolean get() = audio.hasPendingAudio

    /**
     * Scan all content for items lacking a PERMANENT AI voice and generate
     * them via a bounded-concurrency background queue. See [AudioController].
     */
    fun generateMissingAudio() = audio.generateMissingAudio()

    /**
     * Hard stop: cancels any AI audio generation currently running right now.
     * Does NOT touch the [autoGenerateAiAudio] setting.
     */
    fun stopAudioGeneration() = audio.stopAudioGeneration()

    /**
     * "استبدل بصوت الذكاء الاصطناعي" — forces EVERY cached clip to be
     * regenerated by Gemini neural TTS from scratch. See [AudioController].
     */
    fun regenerateAllAudioWithAi() = audio.regenerateAllAudioWithAi()

    /**
     * Auto-queue AI voice generation for freshly imported content — only when
     * the setting is enabled AND the device is online. See [AudioController].
     */
    fun autoGenerateAudioIfOnline() = audio.autoGenerateAudioIfOnline()

    /** Generate (and permanently cache) the AI narration for ONE story on demand. */
    fun generateStoryAudio(storyId: Int) = audio.generateStoryAudio(storyId)

    /** Generate (and permanently cache) the AI narration for ONE lesson on demand. */
    fun generateLessonAudio(lessonId: Int) = audio.generateLessonAudio(lessonId)

    // ==========================================================================
    // CLOUD SYNC (Firebase) — pull new lessons added outside the app (Firestore),
    // and back up / restore this learner's own progress across devices.
    //
    // The app always has a Firebase user from the very first launch (anonymous
    // by default — see CloudAuth.ensureSignedIn). Everything below works with
    // that uid immediately, no extra setup required; Google Sign-In (optional,
    // see CloudAuth.signInWithGoogle) simply links onto the SAME uid so nothing
    // is ever lost when the learner later decides to "claim" their account.
    // ==========================================================================
    var lastCloudLessonSyncMillis by mutableStateOf(0L)
    var cloudSyncEnabled by mutableStateOf(true)
    // Web Client ID from google-services.json (client_type: 3) — enables Google Sign-In
    var googleWebClientId by mutableStateOf(com.zmastery.english.cloud.CloudAuth.DEFAULT_WEB_CLIENT_ID)

    var cloudUid by mutableStateOf<String?>(null)
        internal set
    var cloudIsAnonymous by mutableStateOf(true)
        internal set
    var cloudDisplayName by mutableStateOf<String?>(null)
        internal set
    var cloudEmail by mutableStateOf<String?>(null)
        internal set

    var isSyncingCloud by mutableStateOf(false)
        internal set
    var cloudSyncMessage by mutableStateOf<String?>(null)
    var newLessonsFromCloud by mutableStateOf(0)

    /** عدد عبارات السحابة المخزَّنة محلياً (لعرضه في واجهة المسؤول). */
    var cloudQuoteCount by mutableStateOf(0)
        internal set
    var quoteMessage by mutableStateOf<String?>(null)
    var isAddingQuote by mutableStateOf(false)
        internal set

    var isDeveloperUnlocked by mutableStateOf(false)
    var userRole by mutableStateOf("student")
        internal set

    val isAdmin: Boolean get() = cloud.isAdmin

    fun unlockDeveloperAdmin(code: String): Boolean = cloud.unlockDeveloperAdmin(code)

    var registeredUsersList by mutableStateOf<List<com.zmastery.english.cloud.CloudSync.UserRecord>>(emptyList())
        internal set
    var isLoadingUsers by mutableStateOf(false)
        internal set

    var activeAnnouncement by mutableStateOf<com.zmastery.english.cloud.CloudSync.Announcement?>(null)
        internal set
    /**
     * معرّف آخر إعلان أغلقه المستخدم. الإغلاق كان في الذاكرة فقط، فكان الإعلان
     * يعود للظهور عند كل إقلاع — الآن يُحفظ مع الحالة.
     */
    var dismissedAnnouncementId by mutableStateOf("")
        internal set
    var globalLeaderboard by mutableStateOf<List<com.zmastery.english.cloud.CloudSync.UserRecord>>(emptyList())
        internal set
    var isLoadingLeaderboard by mutableStateOf(false)
        internal set

    // ── مؤشر «تم الرفع»: حالة نشر الدروس سحابياً ──
    /** جارٍ مطابقة الدروس المحلية بما هو منشور فعلاً في `/lessons`. */
    var isVerifyingCloudLessons by mutableStateOf(false)
        internal set
    /** آخر لحظة اكتمل فيها فحص السحابة (0 = لم يُفحص بعد). */
    var lastCloudVerifyMillis by mutableStateOf(0L)
        internal set
    /** عدد مستندات الدروس الموجودة في السحابة وقت آخر فحص. */
    var cloudLessonCount by mutableStateOf(0)
        internal set
    /** رسالة حالة النشر/الفحص — تُعرض داخل بطاقة النشر. */
    var cloudPublishMessage by mutableStateOf<String?>(null)

    /** جارٍ فحص صلاحية النشر (كتابة تجريبية في `/announcements` ثم حذفها). */
    var isProbingCloud by mutableStateOf(false)
        internal set
    /** الدور المسجّل فعلاً في `/users/{uid}`: admin / student / no-doc / null = لم يُفحص. */
    var cloudRoleDoc by mutableStateOf<String?>(null)
        internal set

    /** عدد الدروس المحلية المؤكد وجودها في السحابة. */
    val publishedLessonsCount: Int get() = lessons.count { it.isPublishedToCloud }

    /** دروس محلية لم تُرفع بعد. */
    val unpublishedLessons: List<Lesson> get() = lessons.filterNot { it.isPublishedToCloud }

    /** آخر طابع زمني لنشر أو تحقق ناجح (0 = لم يحدث بعد). */
    val lastLessonPublishMillis: Long get() = lessons.maxOfOrNull { it.publishedAtMillis } ?: 0L

    /** هل كل الدروس المحلية مرفوعة سحابياً؟ */
    val allLessonsPublished: Boolean get() = lessons.isNotEmpty() && unpublishedLessons.isEmpty()

    /**
     * مسؤول محلياً فقط: فتح وضع المطور بالكود على حساب غير حساب المالك يمنح
     * الواجهة لا السحابة — قواعد Firestore سترفض أي نشر. هذه هي الحالة التي
     * تجعل «بث إعلان» يبدو وكأنه لا يعمل، وتُعرض صراحة في بطاقة التشخيص.
     */
    val isLocalOnlyAdmin: Boolean get() = cloud.isLocalOnlyAdmin

    /** بريد هذا الحساب مطابق لبريد المالك لكنه غير موثّق بعد — يحتاج ضغط رابط التأكيد. */
    val ownerEmailUnverified: Boolean get() = cloud.ownerEmailUnverified

    /** يعيد إرسال رابط توثيق البريد لحساب البريد/كلمة المرور الحالي. */
    fun resendEmailVerification(onResult: (Boolean, String) -> Unit) = vmScope.launch {
        val res = com.zmastery.english.cloud.CloudAuth.resendEmailVerification()
        res.onSuccess { onResult(true, "تم إرسال رابط التوثيق إلى بريدك — افتحه واضغط الرابط ثم أعد فتح التطبيق") }
            .onFailure { onResult(false, it.message ?: "تعذّر إرسال رابط التوثيق") }
    }

    /** يعيد تحميل حالة الحساب (بعد ضغط رابط التوثيق) ويحدّث الصلاحيات فوراً. */
    fun refreshEmailVerification(onResult: (Boolean) -> Unit) = vmScope.launch {
        com.zmastery.english.cloud.CloudAuth.reloadCurrentUser()
        cloud.syncUserProfileToCloud()
        onResult(com.zmastery.english.cloud.CloudAuth.isEmailVerified)
    }

    /**
     * Auto-provision or update user profile and progress in Firestore under /users/{uid}
     */
    fun syncUserProfileToCloud() = cloud.syncUserProfileToCloud()

    /** Fetch all registered users for Admin panel */
    fun loadRegisteredUsers() = cloud.loadRegisteredUsers()

    /** Fetch the active announcement */
    fun loadActiveAnnouncement() = cloud.loadActiveAnnouncement()

    /** Post a new broadcast announcement (Admin only) */
    fun postAnnouncement(title: String, message: String, type: String = "info", onResult: (Boolean, String) -> Unit) =
        cloud.postAnnouncement(title, message, type, onResult)

    /** Dismiss active announcement locally */
    fun dismissActiveAnnouncement() = cloud.dismissActiveAnnouncement()

    /** Deactivate announcement globally (Admin only) */
    fun deactivateAnnouncement(id: String) = cloud.deactivateAnnouncement(id)

    /** Fetch global leaderboard */
    fun loadGlobalLeaderboard(limit: Int = 30) = cloud.loadGlobalLeaderboard(limit)

    /** Publish a single lesson package to Firestore (Admin only) */
    fun publishLessonToCloud(pkg: LessonPackage, onResult: (Boolean, String) -> Unit) =
        cloud.publishLessonToCloud(pkg, onResult)

    /** Publish multiple lesson packages to Firestore in a batch (Admin only) */
    fun publishLessonsBatchToCloud(packages: List<LessonPackage>, onResult: (Boolean, String) -> Unit) =
        cloud.publishLessonsBatchToCloud(packages, onResult)

    /**
     * Called once from the Activity/Composition root at startup. Ensures a
     * Firebase user exists, then pulls any new cloud lessons. See [CloudController].
     */
    fun initCloudSync() = cloud.initCloudSync()

    /**
     * Pull every lesson document added/changed in Firestore since the last sync
     * and import them. See [CloudController.syncCloudLessons].
     */
    fun syncCloudLessons(silent: Boolean = false) = cloud.syncCloudLessons(silent)

    /** Push the CURRENT local state to Firestore under this learner's uid.
     *  API keys are stripped before pushing — they must NEVER leave the device. */
    fun pushProgressToCloud() = cloud.pushProgressToCloud()

    /** Complete sign-in when a Google ID Token is received from the account picker. */
    fun signInWithGoogleIdToken(
        idToken: String,
        displayName: String? = null,
        email: String? = null,
        onResult: ((Boolean, String?) -> Unit)? = null,
    ) = cloud.signInWithGoogleIdToken(idToken, displayName, email, onResult)

    /** Sign in with email and password */
    fun signInWithEmail(email: String, pass: String, onResult: (Boolean, String?) -> Unit) =
        cloud.signInWithEmail(email, pass, onResult)

    /** Sign up with email and password */
    fun signUpWithEmail(email: String, pass: String, displayName: String, onResult: (Boolean, String?) -> Unit) =
        cloud.signUpWithEmail(email, pass, displayName, onResult)

    /** Send password reset email */
    fun sendPasswordResetEmail(email: String, onResult: (Boolean, String?) -> Unit) =
        cloud.sendPasswordResetEmail(email, onResult)

    /** Direct sign-in attempt (using CredentialManager) */
    fun signInWithGoogle(context: android.content.Context) = cloud.signInWithGoogle(context)

    fun signOutFromGoogle(context: android.content.Context? = null) = cloud.signOutFromGoogle(context)

    fun updateGoogleWebClientId(id: String) = cloud.updateGoogleWebClientId(id)

    fun updateCloudSyncEnabled(enabled: Boolean) = cloud.updateCloudSyncEnabled(enabled)

    /** مزامنة عبارات السحابة محلياً (للودجت والشاشة الرئيسية). */
    fun syncQuotes(onResult: ((Boolean, Int) -> Unit)? = null) = cloud.syncQuotes(onResult)

    /** يضيف المسؤول عبارة جديدة تظهر لكل الأجهزة. */
    fun addQuote(text: String, author: String, onResult: (Boolean, String) -> Unit) =
        cloud.addQuote(text, author, onResult)

    /** نشر ورفع جميع الدروس المحفوظة محلياً إلى السحابة فوراً */
    fun publishAllLocalLessonsToCloud(onResult: (Boolean, String) -> Unit) =
        cloud.publishAllLocalLessonsToCloud(onResult)

    /**
     * يفحص السحابة ويضبط شارة «تم الرفع» على كل درس محلي — يشمل الدروس التي
     * رفعها سكربت البايثون خارج التطبيق.
     */
    fun verifyCloudLessons(onResult: ((Boolean, String) -> Unit)? = null) =
        cloud.verifyCloudLessons(onResult)

    /** فحص حيّ لصلاحية النشر: كتابة تجريبية في `/announcements` ثم حذفها. */
    fun probePublishPermission(onResult: (Boolean, String) -> Unit) =
        cloud.probePublishPermission(onResult)

    /**
     * Build the exact same [AppState] snapshot [persist] writes locally — used
     * both by local save and by [pushProgressToCloud] so the two never drift.
     */
    internal fun buildAppState(): AppState = AppState(
        courses = courses.map { it.toDto() },
        customLevels = customLevels.map { it.toDto() },
        lessons = lessons.map { it.toDto() },
        vocab = vocab.map { it.toDto() },
        profile = ProfileDto(
            streak = streak, xp = xp, totalReviewsToday = totalReviewsToday,
            dailyGoal = dailyGoal, lessonsPerDay = lessonsPerDay, studyHours = studyHours,
            motivationLevel = motivationLevel, accuracy = accuracy,
            geminiApiKey = geminiApiKey, ttsVoice = ttsVoice,
            nextCourseId = nextCourseId, nextLessonId = nextLessonId, nextWordId = nextWordId,
            desiredRetention = desiredRetention, maxIntervalDays = maxIntervalDays,
            revealMode = revealMode.name,
            reviewAutoPlay = reviewAutoPlay,
            autoGenerateAiAudio = autoGenerateAiAudio,
            aiAudioEnabled = aiAudioEnabled,
            mnemonicStyle = mnemonicStyle.name, mnemonicPersona = mnemonicPersona.name,
            mnemonicModel = mnemonicModel.name, mnemonicNumbering = mnemonicNumbering,
            mnemonicBatchSize = mnemonicBatchSize,
            onboardingDone = onboardingDone, lastStoryDay = lastStoryDay,
            lastActiveDay = lastActiveDay,
            dailyTaskProgress = dailyTasks.map { "${it.id}:${it.progress}" },
            nextStoryId = nextStoryId,
            microHabitId = microHabitId,
            microHabitProgress = microHabitProgress,
            learnerName = learnerName,
            learnerEmail = learnerEmail,
            dayEarnedStreak = dayEarnedStreak,
            lastCloudLessonSyncMillis = lastCloudLessonSyncMillis,
            cloudSyncEnabled = cloudSyncEnabled,
            googleWebClientId = googleWebClientId,
            dismissedAnnouncementId = dismissedAnnouncementId,
            developerUnlocked = isDeveloperUnlocked,
            showFreeModelsOnly = showFreeModelsOnly,
        ),
        aiAgents = aiAgents.map { AiAgentDto(it.id, it.modelId, it.character, it.voiceId, it.style, it.prompt) },
        aiModels = aiModels.map { it.toDto() },
        apiKeys = apiKeys.map {
            ApiKeyDto(
                id = it.id, label = it.label, provider = it.provider,
                maskedKey = it.maskedKey, active = it.active,
                rawKey = it.rawKey, baseUrl = it.baseUrl, status = it.status,
            )
        },
        exams = examHistory.map { it.toDto() },
        examMisses = examMisses.mapKeys { it.key.toString() },
        stories = storyArchive.map { it.toDto() },
        goals = goals.toList(),
        dayStats = dayStats.values.sortedBy { it.epochDay },
        studyPlan = studyPlan,
        coachReports = coachReports.values.toList(),
        chests = openedChests.values.toList(),
        mysteryRewards = mysteryRewards.toList(),
        wallet = wallet,
        mirrorReports = mirrorReports.toMap(),
        reviewSignals = reviewLogs.takeLast(SIGNAL_CAP).map {
            ReviewSignalDto(it.wordId, it.grade, it.reachedStage, it.replays, it.timeMs)
        },
        reviewHours = reviewHours.takeLast(SIGNAL_CAP),
        rescue = rescue,
        lastRescueOfferDay = lastRescueOfferDay,
        streakBeforeBreak = streakBeforeBreak,
    )
}
