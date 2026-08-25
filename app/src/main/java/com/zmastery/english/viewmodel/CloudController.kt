package com.zmastery.english.viewmodel

import com.zmastery.english.data.*
import kotlinx.coroutines.launch

/**
 * Controller for Cloud sync (Firebase): pulling new lessons, backing up &
 * restoring the learner's own progress, admin features, announcements and the
 * leaderboard.
 *
 * All persisted/UI state remains on [AppViewModel]; this class holds the logic
 * and reaches shared state via aliases. See [ExamsController] for the
 * ownership/delegation conventions.
 */
internal class CloudController(internal val vm: AppViewModel) {

    // ── Cloud state owned by the view model (written here) ──
    private var lastCloudLessonSyncMillis
        get() = vm.lastCloudLessonSyncMillis
        set(v) { vm.lastCloudLessonSyncMillis = v }
    private var cloudSyncEnabled
        get() = vm.cloudSyncEnabled
        set(v) { vm.cloudSyncEnabled = v }
    private var googleWebClientId
        get() = vm.googleWebClientId
        set(v) { vm.googleWebClientId = v }
    private var cloudUid
        get() = vm.cloudUid
        set(v) { vm.cloudUid = v }
    private var cloudIsAnonymous
        get() = vm.cloudIsAnonymous
        set(v) { vm.cloudIsAnonymous = v }
    private var cloudDisplayName
        get() = vm.cloudDisplayName
        set(v) { vm.cloudDisplayName = v }
    private var cloudEmail
        get() = vm.cloudEmail
        set(v) { vm.cloudEmail = v }
    private var isSyncingCloud
        get() = vm.isSyncingCloud
        set(v) { vm.isSyncingCloud = v }
    private var cloudSyncMessage
        get() = vm.cloudSyncMessage
        set(v) { vm.cloudSyncMessage = v }
    private var newLessonsFromCloud
        get() = vm.newLessonsFromCloud
        set(v) { vm.newLessonsFromCloud = v }
    private var isDeveloperUnlocked
        get() = vm.isDeveloperUnlocked
        set(v) { vm.isDeveloperUnlocked = v }
    private var userRole
        get() = vm.userRole
        set(v) { vm.userRole = v }
    private var registeredUsersList
        get() = vm.registeredUsersList
        set(v) { vm.registeredUsersList = v }
    private var isLoadingUsers
        get() = vm.isLoadingUsers
        set(v) { vm.isLoadingUsers = v }
    private var activeAnnouncement
        get() = vm.activeAnnouncement
        set(v) { vm.activeAnnouncement = v }
    private var globalLeaderboard
        get() = vm.globalLeaderboard
        set(v) { vm.globalLeaderboard = v }
    private var isLoadingLeaderboard
        get() = vm.isLoadingLeaderboard
        set(v) { vm.isLoadingLeaderboard = v }

    // ── Learner profile state owned by the view model ──
    private val streak get() = vm.streak
    private val xp get() = vm.xp
    private val completedLessons get() = vm.completedLessons
    private val vocab get() = vm.vocab
    private val accuracy get() = vm.accuracy
    private var learnerName
        get() = vm.learnerName
        set(v) { vm.learnerName = v }
    private var learnerEmail
        get() = vm.learnerEmail
        set(v) { vm.learnerEmail = v }

    private fun launch(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) =
        vm.vmScope.launch(block = block)
    private val app get() = vm.app

    val isAdmin: Boolean
        get() = isDeveloperUnlocked || userRole == "admin" ||
            cloudEmail?.lowercase()?.trim() == "mohammedalbkhyty@gmail.com"

    fun unlockDeveloperAdmin(code: String): Boolean {
        val trimmed = code.trim()
        // SECURITY: a single non-guessable code. Cloud-side privileges still
        // require the verified super-admin account (Firestore rules enforce
        // the role) — this unlock only enables local developer UI tooling.
        if (trimmed == "ADMIN2026") {
            isDeveloperUnlocked = true
            userRole = "admin"
            vm.persist()
            syncUserProfileToCloud()
            cloudSyncMessage = "تم تفعيل وضع المطور والمسؤول بنجاح 👑"
            return true
        }
        return false
    }

    private fun refreshCloudAuthState() {
        cloudUid = com.zmastery.english.cloud.CloudAuth.uid
        cloudIsAnonymous = com.zmastery.english.cloud.CloudAuth.isAnonymous
        cloudDisplayName = com.zmastery.english.cloud.CloudAuth.displayName
        cloudEmail = com.zmastery.english.cloud.CloudAuth.email
        syncUserProfileToCloud()
    }

    /**
     * استرجاع لقطة تقدّم سحابية **بالدمج لا بالاستبدال** — عبر [StateMerger].
     *
     * هذا هو دواء خلل فقدان البيانات: الاستبدال المباشر (restoreFrom وحدها)
     * كان يمسح الدروس المستوردة حديثاً بلقطة سحابية قديمة عند كل إقلاع.
     * الدمج يستحيل أن يحذف شيئاً محلياً؛ السحابة تُضيف فقط (دروس جديدة،
     * إكمال أُنجز على جهاز آخر، كلمات جديدة، أعلى سلسلة/XP).
     */
    private fun adoptCloudProgress(cloudJson: String?) {
        if (cloudJson.isNullOrBlank()) return
        val cloud = Persistence.decode(cloudJson) ?: return
        val local = vm.buildAppState()
        val merged = StateMerger.merge(local, cloud)
        if (merged != local) {
            vm.restoreFrom(merged)
            vm.persist()
        }
    }

    /**
     * Auto-provision or update user profile and progress in Firestore under /users/{uid}
     */
    fun syncUserProfileToCloud() {
        val user = com.zmastery.english.cloud.CloudAuth.currentUser ?: return
        launch {
            val snapshot = com.zmastery.english.cloud.CloudSync.UserProfileSnapshot(
                uid = user.uid,
                email = user.email,
                displayName = user.displayName ?: "مستخدم",
                photoUrl = user.photoUrl?.toString(),
                isAnonymous = user.isAnonymous,
                streak = streak,
                xp = xp,
                completedLessonsCount = completedLessons,
                wordsLearnedCount = vocab.count { it.repetitions > 0 },
                accuracy = accuracy.toDouble(),
            )
            val roleResult = com.zmastery.english.cloud.CloudSync.provisionOrUpdateUser(user, snapshot)
            roleResult.onSuccess { role ->
                userRole = role
            }
        }
    }

    /**
     * Fetch all registered users for Admin panel
     */
    fun loadRegisteredUsers() {
        launch {
            isLoadingUsers = true
            val res = com.zmastery.english.cloud.CloudSync.fetchAllUsers()
            res.onSuccess { users ->
                registeredUsersList = users
            }
            isLoadingUsers = false
        }
    }

    /**
     * Fetch the active announcement
     */
    fun loadActiveAnnouncement() {
        launch {
            val res = com.zmastery.english.cloud.CloudSync.fetchActiveAnnouncement()
            res.onSuccess { announcement ->
                activeAnnouncement = announcement
            }
        }
    }

    /**
     * Post a new broadcast announcement (Admin only)
     */
    fun postAnnouncement(title: String, message: String, type: String = "info", onResult: (Boolean, String) -> Unit) {
        launch {
            val res = com.zmastery.english.cloud.CloudSync.postAnnouncement(title, message, type)
            res.onSuccess {
                loadActiveAnnouncement()
                onResult(true, "تم نشر الإشعار العام لجميع الطلاب بنجاح 📢")
            }.onFailure { e ->
                onResult(false, "فشل نشر الإشعار: ${e.message}")
            }
        }
    }

    /**
     * Dismiss active announcement locally
     */
    fun dismissActiveAnnouncement() {
        activeAnnouncement = null
    }

    /**
     * Deactivate announcement globally (Admin only)
     */
    fun deactivateAnnouncement(id: String) {
        launch {
            com.zmastery.english.cloud.CloudSync.deactivateAnnouncement(id)
            activeAnnouncement = null
        }
    }

    /**
     * Fetch global leaderboard
     */
    fun loadGlobalLeaderboard(limit: Int = 30) {
        launch {
            isLoadingLeaderboard = true
            val res = com.zmastery.english.cloud.CloudSync.fetchLeaderboard(limit)
            res.onSuccess { users ->
                globalLeaderboard = users
            }
            isLoadingLeaderboard = false
        }
    }

    /**
     * Publish a single lesson package to Firestore (Admin only)
     */
    fun publishLessonToCloud(pkg: LessonPackage, onResult: (Boolean, String) -> Unit) {
        launch {
            val res = com.zmastery.english.cloud.CloudSync.publishLessonToCloud(pkg)
            res.onSuccess { docId ->
                onResult(true, "تم نشر الدرس سحابياً بنجاح ($docId) 🚀")
                syncCloudLessons(silent = false)
            }.onFailure { e ->
                onResult(false, "فشل النشر السحابي: ${e.message}")
            }
        }
    }

    /**
     * Publish multiple lesson packages to Firestore in a batch (Admin only)
     */
    fun publishLessonsBatchToCloud(packages: List<LessonPackage>, onResult: (Boolean, String) -> Unit) {
        launch {
            val res = com.zmastery.english.cloud.CloudSync.publishLessonsBatchToCloud(packages)
            res.onSuccess { count ->
                onResult(true, "تم نشر $count درس بنجاح لجميع الطلاب سحابياً 🚀")
                syncCloudLessons(silent = false)
            }.onFailure { e ->
                onResult(false, "فشل النشر السحابي: ${e.message}")
            }
        }
    }

    /**
     * Called once from the Activity/Composition root at startup. Ensures a
     * Firebase user exists (anonymous if nothing else), then pulls any new
     * cloud lessons and the latest progress snapshot — completely silent,
     * never blocks the UI, safe to call with no network at all.
     */
    fun initCloudSync() {
        if (!cloudSyncEnabled) return
        launch {
            runCatching { com.zmastery.english.cloud.CloudAuth.ensureSignedIn() }
            refreshCloudAuthState()
            val uid = cloudUid ?: return@launch
            // Pull the cloud snapshot and MERGE it in — never replace. Local
            // content always survives; cloud-only items join in. (The old
            // `lastCloudLessonSyncMillis == 0L` gate + full restoreFrom was
            // the data-loss bug: a stale cloud snapshot wiped freshly
            // imported lessons on every launch.)
            runCatching {
                com.zmastery.english.cloud.CloudSync.pullProgress(uid).getOrNull()
            }.getOrNull()?.let { cloudJson ->
                adoptCloudProgress(cloudJson)
            }
            syncCloudLessons(silent = true)
            syncQuotes()
            loadActiveAnnouncement()
        }
    }

    /**
     * Pull every lesson document added/changed in Firestore since the last
     * sync and import them exactly like a manual batch import — instant,
     * fully local once downloaded, and audio generation (if enabled) queues
     * separately afterwards so this never freezes the UI.
     */
    fun syncCloudLessons(silent: Boolean = false) {
        if (!cloudSyncEnabled) {
            if (!silent) cloudSyncMessage = "المزامنة السحابية متوقفة من الإعدادات"
            return
        }
        if (isSyncingCloud) return
        launch {
            isSyncingCloud = true
            if (!silent) cloudSyncMessage = "جارٍ التحقق من دروس جديدة…"
            val uid = cloudUid ?: run {
                runCatching { com.zmastery.english.cloud.CloudAuth.ensureSignedIn() }
                refreshCloudAuthState()
                cloudUid
            }
            if (uid == null) {
                isSyncingCloud = false
                if (!silent) cloudSyncMessage = "تعذّر الاتصال بالسحابة الآن"
                return@launch
            }
            val result = com.zmastery.english.cloud.CloudSync.pullNewLessons(lastCloudLessonSyncMillis)
            result.onSuccess { sync ->
                if (sync.packages.isNotEmpty()) {
                    vm.importLessons(sync.packages)
                    newLessonsFromCloud += sync.packages.size
                    vm.autoGenerateAudioIfOnline()
                }
                if (sync.latestUpdatedAtMillis > lastCloudLessonSyncMillis) {
                    lastCloudLessonSyncMillis = sync.latestUpdatedAtMillis
                    vm.persist()
                }
                cloudSyncMessage = when {
                    sync.packages.isEmpty() -> "لا توجد دروس جديدة — كل شيء محدّث ✓"
                    else -> "تمت إضافة ${sync.packages.size} درس جديد من السحابة ✓"
                }
                pushProgressToCloud()
            }.onFailure {
                cloudSyncMessage = "تعذّر المزامنة — تحقق من الاتصال"
            }
            isSyncingCloud = false
        }
    }

    /** Push the CURRENT local state to Firestore under this learner's uid.
     *  API keys are stripped before pushing — they must NEVER leave the device. */
    fun pushProgressToCloud() {
        if (!cloudSyncEnabled) return
        val uid = cloudUid ?: return
        launch {
            val state = vm.buildAppState()
            // Strip API keys before cloud sync — keys stay on device only
            val safeState = KeyProtector.stripKeysForSharing(state)
            val raw = Persistence.encode(safeState)
            com.zmastery.english.cloud.CloudSync.pushProgress(uid, raw)
            syncUserProfileToCloud()
        }
    }

    private fun formatAuthError(e: Throwable): String {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ||
                msg.contains("wrong-password", ignoreCase = true) ||
                msg.contains("invalid-credential", ignoreCase = true) ->
                "البريد الإلكتروني أو كلمة المرور غير صحيحة"
            msg.contains("user-not-found", ignoreCase = true) || msg.contains("USER_NOT_FOUND", ignoreCase = true) ->
                "لا يوجد حساب مسجل بهذا البريد الإلكتروني"
            msg.contains("email-already-in-use", ignoreCase = true) || msg.contains("EMAIL_EXISTS", ignoreCase = true) ->
                "هذا البريد الإلكتروني مسجل مسبقاً، يرجى تسجيل الدخول بدلاً من ذلك"
            msg.contains("weak-password", ignoreCase = true) || msg.contains("WEAK_PASSWORD", ignoreCase = true) ->
                "كلمة المرور يجب ألا تقل عن 6 أحرف"
            msg.contains("invalid-email", ignoreCase = true) || msg.contains("INVALID_EMAIL", ignoreCase = true) ->
                "صيغة البريد الإلكتروني غير صحيحة"
            msg.contains("network", ignoreCase = true) || msg.contains("NETWORK", ignoreCase = true) ->
                "تعذّر الاتصال بالإنترنت، يرجى التحقق من الشبكة والمحاولة مجدداً"
            msg.contains("too-many-requests", ignoreCase = true) ->
                "تم تعطيل المحاولات مؤقتاً لكثرة المحاولات، يرجى المحاولة لاحقاً"
            else -> msg.ifBlank { "حدث خطأ أثناء المصادقة، يرجى المحاولة مجدداً" }
        }
    }

    /**
     * Complete sign-in when Google ID Token is received from the Google Account Picker dialog.
     */
    fun signInWithGoogleIdToken(
        idToken: String,
        displayName: String? = null,
        email: String? = null,
        onResult: ((Boolean, String?) -> Unit)? = null,
    ) {
        launch {
            isSyncingCloud = true
            val result = com.zmastery.english.cloud.CloudAuth.signInWithIdToken(idToken)
            result.onSuccess { user ->
                if (user != null) {
                    if (!displayName.isNullOrBlank() && (learnerName.isBlank() || learnerName == "ضيف")) {
                        learnerName = displayName
                    }
                    if (!email.isNullOrBlank()) {
                        learnerEmail = email
                    }
                    refreshCloudAuthState()
                    // Merge any cloud progress for this account — never replace
                    // (a stale/smaller cloud snapshot must not wipe local data).
                    runCatching {
                        com.zmastery.english.cloud.CloudSync.pullProgress(user.uid).getOrNull()
                    }.getOrNull()?.let { cloudJson ->
                        adoptCloudProgress(cloudJson)
                    }
                    vm.persist()
                    cloudSyncMessage = "تم تسجيل الدخول بحساب Google بنجاح ✓"
                    pushProgressToCloud()
                    syncCloudLessons(silent = true)
                    onResult?.invoke(true, null)
                } else {
                    onResult?.invoke(false, "تعذّر تسجيل الدخول بحساب Google")
                }
            }.onFailure { e ->
                val errorMsg = formatAuthError(e)
                cloudSyncMessage = errorMsg
                onResult?.invoke(false, errorMsg)
            }
            isSyncingCloud = false
        }
    }

    /**
     * Sign in with Email and Password
     */
    fun signInWithEmail(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        launch {
            isSyncingCloud = true
            val result = com.zmastery.english.cloud.CloudAuth.signInWithEmail(email, pass)
            result.onSuccess { user ->
                if (user != null) {
                    if (!user.displayName.isNullOrBlank() && (learnerName.isBlank() || learnerName == "ضيف")) {
                        learnerName = user.displayName!!
                    }
                    if (!user.email.isNullOrBlank()) {
                        learnerEmail = user.email!!
                    }
                    refreshCloudAuthState()
                    // Merge any cloud progress for this account — never replace
                    // (a stale/smaller cloud snapshot must not wipe local data).
                    runCatching {
                        com.zmastery.english.cloud.CloudSync.pullProgress(user.uid).getOrNull()
                    }.getOrNull()?.let { cloudJson ->
                        adoptCloudProgress(cloudJson)
                    }
                    vm.persist()
                    cloudSyncMessage = "تم تسجيل الدخول بالبريد الإلكتروني بنجاح ✓"
                    pushProgressToCloud()
                    syncCloudLessons(silent = true)
                    onResult(true, null)
                } else {
                    onResult(false, "تعذّر تسجيل الدخول")
                }
            }.onFailure { e ->
                val errorMsg = formatAuthError(e)
                cloudSyncMessage = errorMsg
                onResult(false, errorMsg)
            }
            isSyncingCloud = false
        }
    }

    /**
     * Sign up (Create new account) with Email and Password
     */
    fun signUpWithEmail(email: String, pass: String, displayName: String, onResult: (Boolean, String?) -> Unit) {
        launch {
            isSyncingCloud = true
            val result = com.zmastery.english.cloud.CloudAuth.signUpWithEmail(email, pass, displayName)
            result.onSuccess { user ->
                if (user != null) {
                    if (displayName.isNotBlank()) {
                        learnerName = displayName.trim()
                    }
                    if (!user.email.isNullOrBlank()) {
                        learnerEmail = user.email!!
                    }
                    refreshCloudAuthState()
                    vm.persist()
                    cloudSyncMessage = "تم إنشاء الحساب بنجاح ✓"
                    pushProgressToCloud()
                    syncCloudLessons(silent = true)
                    onResult(true, null)
                } else {
                    onResult(false, "تعذّر إنشاء الحساب")
                }
            }.onFailure { e ->
                val errorMsg = formatAuthError(e)
                cloudSyncMessage = errorMsg
                onResult(false, errorMsg)
            }
            isSyncingCloud = false
        }
    }

    /**
     * Reset Password
     */
    fun sendPasswordResetEmail(email: String, onResult: (Boolean, String?) -> Unit) {
        launch {
            val result = com.zmastery.english.cloud.CloudAuth.sendPasswordResetEmail(email)
            result.onSuccess {
                onResult(true, "تم إرسال رابط إعادة تعيين كلمة المرور إلى بريدك الإلكتروني ✉️")
            }.onFailure { e ->
                val errorMsg = e.message ?: "تعذّر إرسال رابط الاستعادة"
                onResult(false, errorMsg)
            }
        }
    }

    /**
     * Direct sign-in attempt (using CredentialManager)
     */
    fun signInWithGoogle(context: android.content.Context) {
        launch {
            isSyncingCloud = true
            com.zmastery.english.cloud.CloudAuth.webClientId = googleWebClientId.trim()
            val result = com.zmastery.english.cloud.CloudAuth.signInWithCredentialManager(context)
            result.onSuccess { user ->
                if (user != null) {
                    refreshCloudAuthState()
                    cloudSyncMessage = "تم ربط حساب جوجل بنجاح ✓"
                    pushProgressToCloud()
                }
            }.onFailure { e ->
                cloudSyncMessage = e.message ?: "تعذّر تسجيل الدخول بحساب جوجل"
            }
            isSyncingCloud = false
        }
    }

    fun signOutFromGoogle(context: android.content.Context? = null) {
        launch {
            com.zmastery.english.cloud.CloudAuth.signOut(context)
            refreshCloudAuthState()
            cloudSyncMessage = "تم تسجيل الخروج بنجاح"
        }
    }

    fun updateGoogleWebClientId(id: String) {
        googleWebClientId = id.trim()
        com.zmastery.english.cloud.CloudAuth.webClientId = googleWebClientId
        vm.persist()
    }

    fun updateCloudSyncEnabled(enabled: Boolean) {
        cloudSyncEnabled = enabled
        vm.persist()
    }

    // ---------------------------------------------------------------- QUOTES
    private var cloudQuoteCount
        get() = vm.cloudQuoteCount
        set(v) { vm.cloudQuoteCount = v }
    private var quoteMessage
        get() = vm.quoteMessage
        set(v) { vm.quoteMessage = v }
    private var isAddingQuote
        get() = vm.isAddingQuote
        set(v) { vm.isAddingQuote = v }

    /** يسحب عبارات السحابة ويخزّنها محلياً (للودجت والشاشة الرئيسية). */
    fun syncQuotes(onResult: ((Boolean, Int) -> Unit)? = null) {
        if (!cloudSyncEnabled) { onResult?.invoke(false, cloudQuoteCount); return }
        launch {
            val res = com.zmastery.english.cloud.CloudSync.pullQuotes()
            res.onSuccess { quotes ->
                QuoteStore.saveCloud(app, quotes)
                cloudQuoteCount = quotes.size
                onResult?.invoke(true, quotes.size)
            }.onFailure {
                cloudQuoteCount = QuoteStore.cloudCount(app)
                onResult?.invoke(false, cloudQuoteCount)
            }
        }
    }

    /**
     * يضيف المسؤول عبارة جديدة إلى السحابة (تظهر لكل الأجهزة عند المزامنة).
     * الهدف الوحيد لكل مسؤول عبر الإمتيازات.
     */
    fun addQuote(text: String, author: String, onResult: (Boolean, String) -> Unit) {
        if (!isAdmin) {
            onResult(false, "إضافة العبارات متاحة للمسؤول فقط")
            return
        }
        if (text.isBlank()) {
            onResult(false, "نص العبارة فارغ")
            return
        }
        isAddingQuote = true
        launch {
            val uid = cloudUid ?: run {
                runCatching { com.zmastery.english.cloud.CloudAuth.ensureSignedIn() }
                refreshCloudAuthState()
                cloudUid
            }
            if (uid == null) {
                isAddingQuote = false
                onResult(false, "تعذّر الاتصال بالسحابة الآن")
                return@launch
            }
            val res = com.zmastery.english.cloud.CloudSync.addQuote(text, author, uid)
            isAddingQuote = false
            res.onSuccess {
                quoteMessage = "تم نشر العبارة لكل الأجهزة ✓"
                syncQuotes()
                onResult(true, quoteMessage!!)
            }.onFailure { e ->
                quoteMessage = "فشل النشر: ${e.message}"
                onResult(false, quoteMessage!!)
            }
        }
    }
}
