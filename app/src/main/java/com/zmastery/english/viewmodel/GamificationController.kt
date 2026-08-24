package com.zmastery.english.viewmodel

import com.zmastery.english.data.*
import kotlinx.coroutines.launch

/**
 * Controller for the gamification layer: micro-habits, the Seven Seals chests,
 * mystery rewards, the cognitive mirror, and the streak-rescue missions.
 *
 * The persisted state and the derived views (momentum, metrics, cognitiveMirror,
 * decayState, chestQualifyingStreak, …) stay on [AppViewModel]; this class holds
 * the ACTIONS that mutate them. `grantXp` stays central on the view model and is
 * reached via alias. See [ExamsController] for conventions.
 */
internal class GamificationController(internal val vm: AppViewModel) {

    // ── State owned by the view model (written here) ──
    private var microHabitId
        get() = vm.microHabitId
        set(v) { vm.microHabitId = v }
    private var microHabitProgress
        get() = vm.microHabitProgress
        set(v) { vm.microHabitProgress = v }
    private val microHabit get() = vm.microHabit
    private val microHabitDone get() = vm.microHabitDone
    private val openedChests get() = vm.openedChests
    private var wallet
        get() = vm.wallet
        set(v) { vm.wallet = v }
    private val mysteryRewards get() = vm.mysteryRewards
    private var justOpenedReward
        get() = vm.justOpenedReward
        set(v) { vm.justOpenedReward = v }
    private var isChestMirrorLoading
        get() = vm.isChestMirrorLoading
        set(v) { vm.isChestMirrorLoading = v }
    private var chestMirrorLoadingId
        get() = vm.chestMirrorLoadingId
        set(v) { vm.chestMirrorLoadingId = v }
    private val mirrorReports get() = vm.mirrorReports
    private var isMirrorLoading
        get() = vm.isMirrorLoading
        set(v) { vm.isMirrorLoading = v }
    private var mirrorMessage
        get() = vm.mirrorMessage
        set(v) { vm.mirrorMessage = v }
    private var streakBeforeBreak
        get() = vm.streakBeforeBreak
        set(v) { vm.streakBeforeBreak = v }
    private var rescue
        get() = vm.rescue
        set(v) { vm.rescue = v }
    private var lastRescueOfferDay
        get() = vm.lastRescueOfferDay
        set(v) { vm.lastRescueOfferDay = v }

    // ── Derived views owned by the view model (read-only) ──
    private val momentum get() = vm.momentum
    private val metrics get() = vm.metrics
    private val cognitiveMirror get() = vm.cognitiveMirror
    private val chestQualifyingStreak get() = vm.chestQualifyingStreak
    private val learnerName get() = vm.learnerName
    private val studyHours get() = vm.studyHours
    private val masteredCount get() = vm.masteredCount
    private val totalWords get() = vm.totalWords
    private val completedLessons get() = vm.completedLessons
    private val trueRecallRate get() = vm.trueRecallRate
    private val forgottenWords get() = vm.forgottenWords
    private var streak
        get() = vm.streak
        set(v) { vm.streak = v }
    private var motivationLevel
        get() = vm.motivationLevel
        set(v) { vm.motivationLevel = v }
    private val geminiApiKey get() = vm.geminiApiKey
    private val hasAiKey get() = vm.hasAiKey
    private val app get() = vm.app

    private fun nowStamp(): String = vm.nowStamp()
    private fun grantXp(amount: Int, applyMultiplier: Boolean = true) = vm.grantXp(amount, applyMultiplier)
    private fun persist() = vm.persist()
    private fun launch(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) =
        vm.vmScope.launch(block = block)

    // ===================== Micro-habits =====================
    fun setMicroHabit(id: String) {
        if (id == microHabitId) return
        microHabitId = id
        microHabitProgress = 0
        persist()
    }

    /**
     * Advance the micro-habit. Called by real activity (a review, a listen, a
     * story page, a new word) so the 3–5 minute daily ورد completes naturally.
     */
    fun advanceMicroHabit(id: String, amount: Int = 1) {
        if (id != microHabitId) return
        if (microHabitDone) return
        microHabitProgress = (microHabitProgress + amount).coerceAtMost(microHabit.target)
        if (microHabitDone) {
            grantXp(15)
            com.zmastery.english.notify.Notifier.achievement(
                app,
                "الورد اليومي مكتمل \uD83D\uDD25",
                "${microHabit.title} — سلسلتك محمية اليوم. +15 XP",
            )
            checkChests()
            syncMysteryRewards()
        }
        persist()
    }

    // ===================== Seven Seals (chests) =====================
    /** Fires a notification when a new seal becomes available. */
    fun checkChests() {
        val pending = vm.pendingChests
        if (pending.isEmpty()) return
        val tier = pending.last()
        com.zmastery.english.notify.Notifier.achievement(
            app,
            "صندوق مجهول ينتظرك! \uD83C\uDF81",
            "${tier.name} (${tier.rarity.label}) — افتحه لتكتشف ما بداخله.",
        )
    }

    /**
     * Open a sealed chest: reveal the AI wisdom card and APPLY every perk.
     * Idempotent — reopening returns the cached record without re-granting.
     */
    fun openChest(tierId: String): ChestRecord? {
        val tier = SevenSeals.byId(tierId) ?: return null
        openedChests[tierId]?.let { return it }
        // Qualify on the BEST streak so an earned seal is never taken back.
        if (chestQualifyingStreak < tier.day) return null

        val m = momentum
        val wisdom = WisdomCards.forTier(tier, learnerName, m, totalWords)

        // ---- apply the perks ----
        var w = wallet
        tier.rewards.forEach { r ->
            when (r.kind) {
                PerkKind.XP_BONUS -> grantXp(r.amount, applyMultiplier = false)
                PerkKind.XP_MULTIPLIER -> {
                    val until = System.currentTimeMillis() + r.amount * 60_000L
                    w = w.copy(xpMultiplierUntil = maxOf(w.xpMultiplierUntil, until))
                }
                PerkKind.STREAK_FREEZE ->
                    w = w.grantFreezes(r.amount.coerceAtLeast(1))
                PerkKind.THEME ->
                    w = w.copy(themesUnlocked = (w.themesUnlocked + tier.id).distinct())
                PerkKind.UNLOCK_ZONE ->
                    w = w.copy(zonesUnlocked = (w.zonesUnlocked + tier.id).distinct())
                PerkKind.VOICE ->
                    w = w.copy(voicesUnlocked = (w.voicesUnlocked + tier.id).distinct())
                PerkKind.SPONSOR ->
                    w = w.copy(sponsorGifts = w.sponsorGifts + r.amount.coerceAtLeast(1))
                PerkKind.LEGACY -> w = w.copy(legacyUnlocked = true)
                PerkKind.AI_REPORT -> Unit // the wisdom text IS the report
            }
        }
        w = w.copy(badges = (w.badges + tier.badge).distinct())
        wallet = w

        val rec = ChestRecord(
            tierId = tier.id,
            openedEpochDay = Telemetry.today(),
            streakAtOpen = m.streak,
            wisdom = wisdom,
        )
        openedChests[tier.id] = rec
        persist()
        return rec
    }

    // ===================== Mystery rewards =====================
    /**
     * يزامن الصناديق مع السلسلة الحالية. يُستدعى عند كل نشاط حقيقي.
     * لا يُعيد قفل صندوق مفتوح ولا يسحب استحقاقاً سابقاً.
     */
    fun syncMysteryRewards(notify: Boolean = true) {
        val before = mysteryRewards.count { it.isSealed }
        val synced = MysteryCatalog.sync(
            existing = mysteryRewards.toList(),
            streak = chestQualifyingStreak,
            todayDay = Telemetry.today(),
        )
        if (synced != mysteryRewards.toList()) {
            mysteryRewards.clear()
            mysteryRewards.addAll(synced)
            persist()
        }
        val after = mysteryRewards.count { it.isSealed }
        if (notify && after > before) {
            mysteryRewards.lastOrNull { it.isSealed }?.let { r ->
                com.zmastery.english.notify.Notifier.achievement(
                    app,
                    "صندوق غامض ينتظرك! \uD83C\uDF81",
                    "${r.title} (${r.rarity.label}) — اكسر الختم لتكتشف ما بداخله.",
                )
            }
        }
    }

    /** يمنح صندوق إتمام كورس (يُستدعى عند إنهاء آخر درس في الكورس). */
    fun grantCourseReward(courseId: Int, courseName: String, lessonCount: Int) {
        val key = "course_$courseId"
        if (mysteryRewards.any { it.key == key }) return
        mysteryRewards.add(
            MysteryCatalog.buildCourseChest(courseId, courseName, lessonCount, Telemetry.today())
        )
        com.zmastery.english.notify.Notifier.achievement(
            app,
            "صندوق إتمام الكورس! \uD83C\uDF93",
            "$courseName — اكسر الختم لتستلم شارتك.",
        )
        persist()
    }

    /**
     * يكسر ختم صندوق ويطبّق جوائزه. عملية idempotent تماماً.
     */
    fun openMysteryReward(id: String): MysteryReward? {
        val idx = mysteryRewards.indexOfFirst { it.id == id }
        if (idx < 0) return null
        val r = mysteryRewards[idx]
        if (r.isOpened) return r
        if (r.isDormant) return null

        // تقرير محلّي فوري: يُعرض بلا انتظار، ثم يُستبدل بتقرير Gemini إن توفّر.
        val html = OfflineMirrorHtml.build(mirrorStatsFor(r.title))

        // ---- منح الجوائز ----
        if (r.xpAwarded > 0) grantXp(r.xpAwarded, applyMultiplier = false)
        var w = wallet
        r.themeUnlockKey?.let { w = w.copy(themesUnlocked = (w.themesUnlocked + it).distinct()) }
        w = w.copy(badges = (w.badges + "${r.badgeEmoji} ${r.title}").distinct())
        // الصناديق النادرة فأعلى تمنح درع تجميد سلسلة إضافياً.
        if (r.rarity.ordinal >= RewardRarity.RARE.ordinal) {
            w = w.grantFreezes(1)
        }
        wallet = w

        val opened = r.copy(
            isOpened = true,
            openedAt = System.currentTimeMillis(),
            descriptionHtmlAr = html,
        )
        mysteryRewards[idx] = opened
        justOpenedReward = opened
        persist()

        // ثم نرقّي التقرير بالذكاء الاصطناعي في الخلفية (إن وُجد مفتاح وإنترنت).
        upgradeRewardHtmlWithAi(opened.id, opened.title)
        return opened
    }

    // ===================== Cognitive mirror (reward HTML) =====================
    /** يجمع الإحصاءات الحقيقية التي تُغذّي المطالبة. */
    fun mirrorStatsFor(milestoneTitle: String): MirrorStats {
        val mt = metrics
        return MirrorStats(
            learnerName = learnerName,
            milestoneTitle = milestoneTitle,
            streakDays = mt.dailyStreak,
            masteredWords = masteredCount,
            totalWords = totalWords,
            lessonsCompleted = completedLessons,
            studyMinutes = (studyHours * 60).toInt().coerceAtLeast(0),
            recallRate = trueRecallRate,
            continuityPercent = mt.continuityPercent,
            masteryPercent = mt.masteryPercent,
            cefr = mt.cefr,
            nextCefr = mt.nextCefr,
            bestStreak = mt.bestStreak,
        )
    }

    /**
     * يستبدل التقرير المحلّي بتقرير Gemini المخصّص، ويحفظه داخل الصندوق نهائياً.
     * أي فشل يُترك بصمت — التقرير المحلّي الجميل معروض بالفعل.
     */
    private fun upgradeRewardHtmlWithAi(rewardId: String, title: String) {
        if (!hasAiKey) return
        if (isChestMirrorLoading) return
        isChestMirrorLoading = true
        chestMirrorLoadingId = rewardId
        val stats = mirrorStatsFor(title)
        launch {
            val (html, fromAi) = CognitiveMirrorService.generateHtml(stats, geminiApiKey)
            isChestMirrorLoading = false
            chestMirrorLoadingId = null
            if (!fromAi) return@launch
            val i = mysteryRewards.indexOfFirst { it.id == rewardId }
            if (i < 0) return@launch
            val updated = mysteryRewards[i].copy(descriptionHtmlAr = html)
            mysteryRewards[i] = updated
            if (justOpenedReward?.id == rewardId) justOpenedReward = updated
            persist()
        }
    }

    /** إعادة توليد تقرير صندوق مفتوح يدوياً (زر "أعد التوليد"). */
    fun regenerateRewardMirror(rewardId: String) {
        val i = mysteryRewards.indexOfFirst { it.id == rewardId }
        if (i < 0) return
        val r = mysteryRewards[i]
        if (!r.isOpened) return
        if (!hasAiKey) {
            // بلا مفتاح: نعيد بناء التقرير المحلّي بأحدث الأرقام.
            val fresh = OfflineMirrorHtml.build(mirrorStatsFor(r.title))
            mysteryRewards[i] = r.copy(descriptionHtmlAr = fresh)
            persist()
            return
        }
        upgradeRewardHtmlWithAi(rewardId, r.title)
    }

    /** يمسح إشارة "فُتح للتو" بعد انتهاء مراسم الاحتفال. */
    fun clearJustOpened() { justOpenedReward = null }

    /**
     * يولّد تقرير مرآة الإدراك لصندوق مفتوح.
     */
    fun generateMirrorReport(tierId: String, force: Boolean = false) {
        if (!force && mirrorReports.containsKey(tierId)) return
        if (isMirrorLoading) return
        val tier = SevenSeals.byId(tierId) ?: return
        val m = cognitiveMirror
        val mo = momentum
        isMirrorLoading = true
        mirrorMessage = null
        launch {
            val report = MirrorService.generate(
                m = m,
                mo = mo,
                name = learnerName,
                totalWords = totalWords,
                masteredWords = masteredCount,
                leechSamples = forgottenWords.take(5).map { it.english },
                tierName = tier.name,
                apiKey = geminiApiKey,
                stamp = nowStamp(),
            )
            mirrorReports[tierId] = report
            isMirrorLoading = false
            mirrorMessage = if (report.local) {
                "تم التوليد محلياً — أضف مفتاح Gemini لتحليل أعمق"
            } else {
                "تم توليد مرآة الإدراك بالذكاء الاصطناعي"
            }
            persist()
        }
    }

    // ===================== Streak rescue =====================
    private val RESCUE_VALID_DAYS = 2L

    /**
     * يُستدعى عند تدوير اليوم: إذا انكسرت سلسلة معتبرة (> 2 أيام) ولم يحمها
     * درع، نطلق مهمة إنقاذ بدل إظهار واجهة لوم.
     */
    fun maybeOfferRescue(brokenStreak: Int) {
        val today = Telemetry.today()
        if (brokenStreak <= 2) return              // لا شيء يستحق الإنقاذ
        if (lastRescueOfferDay == today) return     // عُرضت اليوم بالفعل
        if (rescue.isActive) return                 // مهمة قائمة
        streakBeforeBreak = brokenStreak
        lastRescueOfferDay = today
        rescue = RescueMission(
            offeredEpochDay = today,
            streakToRestore = brokenStreak,
            kind = EnigmaStreakEngine.pickRescueKind(cognitiveMirror).name,
        )
        com.zmastery.english.notify.Notifier.achievement(
            app,
            "مهمة إنقاذ عاجلة! \uD83D\uDFE3",
            "سلسلتك ($brokenStreak يوماً) قابلة للاستعادة — أنجز مهمة قصيرة الآن.",
        )
    }

    fun startRescueTimer() {
        val r = rescue
        if (!r.isActive || r.completed) return
        rescue = r.copy(progress = 0, startedAtMs = System.currentTimeMillis())
        persist()
    }

    fun timeoutRescue() {
        val r = rescue
        if (!r.isRunning) return
        if (!r.isExpired()) return
        rescue = r.copy(progress = 0, startedAtMs = 0L, timeouts = r.timeouts + 1)
        persist()
    }

    /** يسجّل تقدّماً في مهمة الإنقاذ (يُستدعى من شاشة المراجعة). */
    fun advanceRescue(amount: Int = 1) {
        val r = rescue
        if (!r.isActive || r.completed) return
        if (!r.isRunning) return
        if (r.isExpired()) {
            timeoutRescue()
            return
        }
        val p = (r.progress + amount).coerceAtMost(r.target)
        rescue = r.copy(progress = p, completed = p >= r.target)
        if (rescue.completed) {
            com.zmastery.english.notify.Notifier.achievement(
                app,
                "أنقذت شعلتك! \uD83D\uDD25",
                "المهمة اكتملت — استلم سلسلتك المستعادة الآن.",
            )
        }
        persist()
    }

    /**
     * استلام مكافأة الإنقاذ: تعود الشعلة القديمة كاملة.
     * @return السلسلة المستعادة، أو 0 عند الفشل.
     */
    fun claimRescue(): Int {
        val r = rescue
        if (!r.isActive || !r.completed || r.claimed) return 0
        val restored = r.streakToRestore.coerceAtLeast(1)
        streak = restored
        rescue = r.copy(claimed = true)
        grantXp(60)
        // درع مجاني: مكافأة على العودة، ويحمي من انتكاسة فورية.
        wallet = wallet.grantFreezes(1)
        motivationLevel = (motivationLevel + 0.15f).coerceAtMost(1f)
        persist()
        return restored
    }

    /** تجاهل مهمة الإنقاذ (يبقى الخيار للمتعلّم دائماً). */
    fun dismissRescue() {
        rescue = rescue.copy(claimed = true)
        persist()
    }

    /** Spend a sponsor gift (rewards generosity — a real retention driver). */
    fun useSponsorGift(): Boolean {
        if (wallet.sponsorGifts <= 0) return false
        wallet = wallet.copy(sponsorGifts = wallet.sponsorGifts - 1)
        persist()
        return true
    }
}
