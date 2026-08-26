package com.zmastery.english.viewmodel

import com.zmastery.english.data.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
    private var dismissedAnnouncementId
        get() = vm.dismissedAnnouncementId
        set(v) { vm.dismissedAnnouncementId = v }
    private var isVerifyingCloudLessons
        get() = vm.isVerifyingCloudLessons
        set(v) { vm.isVerifyingCloudLessons = v }
    private var lastCloudVerifyMillis
        get() = vm.lastCloudVerifyMillis
        set(v) { vm.lastCloudVerifyMillis = v }
    private var cloudLessonCount
        get() = vm.cloudLessonCount
        set(v) { vm.cloudLessonCount = v }
    private var cloudPublishMessage
        get() = vm.cloudPublishMessage
        set(v) { vm.cloudPublishMessage = v }
    private var isProbingCloud
        get() = vm.isProbingCloud
        set(v) { vm.isProbingCloud = v }
    private var cloudRoleDoc
        get() = vm.cloudRoleDoc
        set(v) { vm.cloudRoleDoc = v }
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

    /**
     * حساب المالك الوحيد المعترف به سحابياً — نفس البريد المكتوب في
     * `firestore.rules` (isSuperAdminToken). أي تغيير هنا يجب أن يرافقه تغيير هناك.
     */
    private val OWNER_EMAIL = "mohammedalbkhyty@gmail.com"

    val isAdmin: Boolean
        get() = isDeveloperUnlocked || userRole == "admin" ||
            cloudEmail?.lowercase()?.trim() == OWNER_EMAIL

    /**
     * هل يملك هذا الحساب صلاحية كتابة **سحابية** فعلية؟
     *
     * الواجهة قد تفتح بكود وضع المطور، لكن قواعد Firestore لا تعترف إلا
     * بحساب المالك أو بمستخدم دوره `admin` في `/users/{uid}`.
     */
    private val hasCloudWritePower: Boolean
        get() = cloudEmail?.lowercase()?.trim() == OWNER_EMAIL || userRole == "admin"

    /** مسؤول محلياً فقط — كل محاولة نشر سحابي منه ستُرفض. */
    val isLocalOnlyAdmin: Boolean
        get() = isAdmin && !hasCloudWritePower

    /**
     * مهلة قصوى لأي عملية سحابية تنتظرها الواجهة.
     *
     * لقطات الشاشة من الجهاز كانت تظهر زرّي «جارٍ البث…» و«جارٍ النشر…»
     * عالقين إلى الأبد: `await()` على كتابة Firestore لا ينتهي أبداً حين يكون
     * الاتصال مقطوعاً/بطيئاً (الكتابة المعلّقة تُعاد محاولة إرسالها بلا نهاية)،
     * فلا يعود الـ callback ولا يتحرر الزر. المهلة تحوّل التعليق الأبدي إلى
     * رسالة فشل واضحة بعد ثوانٍ.
     */
    private val CLOUD_TIMEOUT_MS = 25_000L

    /** ينفّذ عملية سحابية بمهلة؛ عند انتهائها يفشل برسالة عربية بدل التعليق. */
    private suspend fun <T> timedCloud(op: String, block: suspend () -> Result<T>): Result<T> =
        withTimeoutOrNull(CLOUD_TIMEOUT_MS) { block() }
            ?: Result.failure(
                java.util.concurrent.TimeoutException(
                    "$op: انتهى الانتظار بعد ${CLOUD_TIMEOUT_MS / 1000} ثانية — " +
                        "الاتصال بالسحابة بطيء أو مقطوع؛ تحقق من الإنترنت وأعد المحاولة"
                )
            )

    /**
     * يترجم أخطاء Firestore إلى سبب مفهوم + الحل المباشر.
     *
     * قبل هذا كانت الرسالة الخام (PERMISSION_DENIED: Missing or insufficient
     * permissions) تُعرض كما هي وبلا تمييز بين «لا يوجد حساب» و«القواعد لم
     * تُنشر» و«الحساب ليس مسؤولاً» — ولهذا بدا النشر وكأنه «لا يعمل» بلا سبب.
     */
    private fun describeCloudError(e: Throwable): String {
        val raw = "${e.javaClass.simpleName}: ${e.message.orEmpty()}".trim().trimEnd(':')
        return when {
            // انتهت المهلة — الرسالة عربية بالفعل فلا نُضيف عليها.
            e is java.util.concurrent.TimeoutException -> e.message.orEmpty().ifBlank { raw }
            raw.contains("PERMISSION_DENIED", true) || raw.contains("Missing or insufficient permissions", true) ->
                "رفضت قواعد Firestore الكتابة.\nالسبب المرجّح: $raw\n" +
                    "الحل (بأيٍّ منهما):\n" +
                    "١) سجّل الدخول بحساب المالك $OWNER_EMAIL\n" +
                    "٢) أو انشر القواعد: firebase deploy --only firestore:rules\n" +
                    "٣) أو اجعل دور هذا الحساب admin في مستند /users/{uid}"
            raw.contains("UNAUTHENTICATED", true) ->
                "الجلسة السحابية غير صالحة ($raw) — أعد تسجيل الدخول ثم جرّب مجدداً."
            raw.contains("UNAVAILABLE", true) || raw.contains("DEADLINE_EXCEEDED", true) ||
                raw.contains("network", true) || raw.contains("NETWORK", true) ->
                "تعذّر الوصول إلى السحابة ($raw) — تحقق من الاتصال بالإنترنت وأعد المحاولة."
            raw.contains("FAILED_PRECONDITION", true) && raw.contains("index", true) ->
                "ينقص فهرس في Firestore ($raw) — نفّذ: firebase deploy --only firestore:indexes"
            raw.contains("NOT_FOUND", true) || raw.contains("no project", true) ->
                "مشروع Firebase غير مرتبط بهذا البناء ($raw) — تأكد من وجود google-services.json الصحيح."
            else -> raw.ifBlank { "حدث خطأ غير متوقع أثناء الاتصال بالسحابة" }
        }
    }

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
            cloudSyncMessage = if (hasCloudWritePower) {
                "تم تفعيل وضع المطور والمسؤول بنجاح 👑"
            } else {
                "تم فتح أدوات المطور محلياً 👑 — لكن النشر السحابي يتطلب حساب المالك $OWNER_EMAIL"
            }
            return true
        }
        return false
    }

    /**
     * يضمن وجود حساب سحابي ويعيد قراءة حالة المصادقة.
     * يُستدعى قبل أي كتابة — فالكتابة بلا حساب تفشل برسالة غامضة.
     */
    private suspend fun ensureCloudSession(): String? {
        cloudUid ?: run {
            runCatching { com.zmastery.english.cloud.CloudAuth.ensureSignedIn() }
            refreshCloudAuthState()
        }
        return cloudUid
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
            val roleResult = timedCloud("مزامنة الملف الشخصي") {
                com.zmastery.english.cloud.CloudSync.provisionOrUpdateUser(user, snapshot)
            }
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
            val res = timedCloud("قائمة الطلاب") { com.zmastery.english.cloud.CloudSync.fetchAllUsers() }
            res.onSuccess { users ->
                registeredUsersList = users
            }
            isLoadingUsers = false
        }
    }

    /**
     * Fetch the active announcement — مع تجاهل الإعلان الذي أغلقه المستخدم.
     */
    fun loadActiveAnnouncement() {
        launch {
            val res = timedCloud("جلب الإعلان") { com.zmastery.english.cloud.CloudSync.fetchActiveAnnouncement() }
            res.onSuccess { announcement ->
                activeAnnouncement = announcement?.takeIf { it.id.isNotBlank() && it.id != dismissedAnnouncementId }
            }
        }
    }

    /**
     * Post a new broadcast announcement (Admin only).
     *
     * ثلاث إصلاحات جعلت البث يعمل فعلاً بدل أن يبدو ميتاً:
     *   ١) ضمان وجود جلسة سحابية قبل الكتابة (كانت الكتابة تتم بلا حساب أحياناً).
     *   ٢) فحص الصلاحية المحلية أولاً برسالة عربية واضحة.
     *   ٣) ترجمة الخطأ الخام إلى سبب + حل (عبر [describeCloudError]).
     */
    fun postAnnouncement(title: String, message: String, type: String = "info", onResult: (Boolean, String) -> Unit) {
        if (title.isBlank() || message.isBlank()) {
            onResult(false, "العنوان ونص الإعلان مطلوبان معاً")
            return
        }
        if (!isAdmin) {
            onResult(false, "بث الإعلانات متاح للمسؤول فقط — فعّل وضع المطور من الإعدادات")
            return
        }
        launch {
            if (ensureCloudSession() == null) {
                onResult(false, "لا يوجد اتصال بالسحابة الآن — تحقق من الإنترنت ثم أعد المحاولة")
                return@launch
            }
            val res = timedCloud("بث الإعلان") {
                com.zmastery.english.cloud.CloudSync.postAnnouncement(title.trim(), message.trim(), type)
            }
            res.onSuccess { id ->
                // إعلاننا الجديد أحدث من أي إغلاق سابق — لا نخفيه عن صاحبه.
                dismissedAnnouncementId = ""
                vm.persist()
                loadActiveAnnouncement()
                onResult(true, "تم بث الإعلان لجميع الطلاب بنجاح 📢\nمعرّف الإعلان: $id")
            }.onFailure { e ->
                onResult(false, "فشل بث الإعلان — ${describeCloudError(e)}")
            }
        }
    }

    /**
     * Dismiss active announcement locally — ويُحفظ الإغلاق حتى لا يعود الإعلان
     * للظهور عند كل إقلاع.
     */
    fun dismissActiveAnnouncement() {
        activeAnnouncement?.let { ann ->
            dismissedAnnouncementId = ann.id
            vm.persist()
        }
        activeAnnouncement = null
    }

    /**
     * Deactivate announcement globally (Admin only)
     */
    fun deactivateAnnouncement(id: String) {
        launch {
            val res = timedCloud("تثبيط الإعلان") { com.zmastery.english.cloud.CloudSync.deactivateAnnouncement(id) }
            activeAnnouncement = null
            dismissedAnnouncementId = id
            vm.persist()
            res.onFailure { e ->
                cloudPublishMessage = "تعذّر تثبيط الإعلان — ${describeCloudError(e)}"
            }
        }
    }

    /**
     * فحص حيّ لصلاحية النشر: يكتب مستند اختبار في `/announcements` ثم يحذفه.
     * يعطي المسؤول جواباً قاطعاً بدل التخمين عندما «لا يعمل» البث.
     */
    fun probePublishPermission(onResult: (Boolean, String) -> Unit) {
        launch {
            isProbingCloud = true
            cloudPublishMessage = null
            val uid = ensureCloudSession()
            if (uid == null) {
                isProbingCloud = false
                onResult(false, "لا يوجد حساب سحابي — تحقق من الاتصال بالإنترنت ثم أعد الفحص")
                return@launch
            }
            // ما الدور المسجّل فعلاً لهذا الحساب في السحابة؟
            cloudRoleDoc = withTimeoutOrNull(CLOUD_TIMEOUT_MS) {
                com.zmastery.english.cloud.CloudSync.fetchRoleDoc(uid).getOrNull()
            }
            // نعتمده محلياً حتى تصدق شارة «مسؤول محلي فقط» في الواجهة.
            cloudRoleDoc?.takeIf { it != "no-doc" }?.let { userRole = it }
            val res = timedCloud("فحص صلاحية النشر") {
                com.zmastery.english.cloud.CloudSync.probePublishPermission()
            }
            isProbingCloud = false
            res.onSuccess {
                onResult(true, "الاتصال وصلاحية النشر يعملان ✓ — يمكنك بث إعلان الآن")
            }.onFailure { e ->
                onResult(false, describeCloudError(e))
            }
        }
    }

    /**
     * Fetch global leaderboard
     */
    fun loadGlobalLeaderboard(limit: Int = 30) {
        launch {
            isLoadingLeaderboard = true
            val res = timedCloud("لوحة الصدارة") { com.zmastery.english.cloud.CloudSync.fetchLeaderboard(limit) }
            res.onSuccess { users ->
                globalLeaderboard = users
            }
            isLoadingLeaderboard = false
        }
    }

    /**
     * معرّف مستند Firestore لدرس محلي — نفس القاعدة المستخدمة في
     * [com.zmastery.english.cloud.CloudSync.publishLessonToCloud] وفي سكربت
     * `upload_lessons.py`: `{courseId}_lesson_{lessonNo}`.
     * وجودها في مكان واحد هو ما يجعل شارة «تم الرفع» مطابقة للحقيقة.
     */
    fun lessonDocId(lesson: Lesson): String {
        val course = vm.courses.firstOrNull { it.id == lesson.courseId }
        val courseKey = course?.jsonId?.ifBlank { null }
            ?: course?.key?.ifBlank { null }
            ?: "course_${lesson.courseId}"
        return "${courseKey}_lesson_${lesson.no}"
    }

    /**
     * يضع شارة «تم الرفع» على الدروس المحلية المطابقة لمعرّفات السحابة.
     * @param cloudIndex معرّف المستند → طابع آخر تحديث في السحابة.
     * @return عدد الدروس التي تغيّرت شارتها.
     */
    private fun applyCloudMarks(cloudIndex: Map<String, Long>): Int {
        var changed = 0
        for (i in vm.lessons.indices) {
            val lesson = vm.lessons[i]
            val docId = lessonDocId(lesson)
            val stamp = cloudIndex[docId] ?: continue
            val effective = if (stamp > 0L) stamp else System.currentTimeMillis()
            if (lesson.publishedAtMillis != effective || lesson.publishedDocId != docId) {
                vm.lessons[i] = lesson.copy(publishedAtMillis = effective, publishedDocId = docId)
                changed++
            }
        }
        if (changed > 0) vm.persist()
        return changed
    }

    /**
     * يمسح شارة الرفع عن درس لم يعد موجوداً في السحابة (حُذف أو تغيّر معرّفه).
     */
    private fun clearStaleMarks(cloudIndex: Map<String, Long>): Int {
        var cleared = 0
        for (i in vm.lessons.indices) {
            val lesson = vm.lessons[i]
            if (lesson.publishedAtMillis > 0L && lessonDocId(lesson) !in cloudIndex) {
                vm.lessons[i] = lesson.copy(publishedAtMillis = 0L, publishedDocId = "")
                cleared++
            }
        }
        if (cleared > 0) vm.persist()
        return cleared
    }

    /**
     * التحقق من السحابة: يقارن معرّفات الدروس المحلية بما هو موجود فعلاً في
     * `/lessons` ويضبط شارة «تم الرفع» — يشمل الدروس المرفوعة بسكربت البايثون.
     */
    fun verifyCloudLessons(onResult: ((Boolean, String) -> Unit)? = null) {
        if (isVerifyingCloudLessons) return
        launch {
            isVerifyingCloudLessons = true
            cloudPublishMessage = "جارٍ فحص دروس السحابة…"
            if (ensureCloudSession() == null) {
                isVerifyingCloudLessons = false
                cloudPublishMessage = "تعذّر الاتصال بالسحابة للتحقق"
                onResult?.invoke(false, "تعذّر الاتصال بالسحابة الآن")
                return@launch
            }
            val res = timedCloud("فهرس دروس السحابة") {
                com.zmastery.english.cloud.CloudSync.fetchCloudLessonIndex()
            }
            isVerifyingCloudLessons = false
            res.onSuccess { index ->
                cloudLessonCount = index.size
                lastCloudVerifyMillis = System.currentTimeMillis()
                val marked = applyCloudMarks(index)
                val cleared = clearStaleMarks(index)
                val published = vm.publishedLessonsCount
                val total = vm.lessons.size
                val msg = when {
                    total == 0 -> "لا توجد دروس محلية للمقارنة"
                    published == total ->
                        "كل الدروس مرفوعة ✓ — $published من $total درس موجودة في السحابة"
                    else ->
                        "في السحابة $published من $total درساً · المتبقي ${total - published}" +
                            if (marked > 0 || cleared > 0) " (حُدّثت شارة $marked درساً)" else ""
                }
                cloudPublishMessage = msg
                onResult?.invoke(true, msg)
            }.onFailure { e ->
                val msg = "تعذّر التحقق من السحابة — ${describeCloudError(e)}"
                cloudPublishMessage = msg
                onResult?.invoke(false, msg)
            }
        }
    }

    /**
     * Publish a single lesson package to Firestore (Admin only)
     */
    fun publishLessonToCloud(pkg: LessonPackage, onResult: (Boolean, String) -> Unit) {
        launch {
            val res = timedCloud("نشر الدرس") { com.zmastery.english.cloud.CloudSync.publishLessonToCloud(pkg) }
            res.onSuccess { docId ->
                // شارة «تم الرفع» على الدرس المحلي المطابق.
                applyCloudMarks(mapOf(docId to System.currentTimeMillis()))
                onResult(true, "تم نشر الدرس سحابياً بنجاح ✓ ($docId)")
                syncCloudLessons(silent = false)
            }.onFailure { e ->
                onResult(false, "فشل النشر السحابي — ${describeCloudError(e)}")
            }
        }
    }

    /**
     * Publish multiple lesson packages to Firestore in a batch (Admin only)
     */
    fun publishLessonsBatchToCloud(packages: List<LessonPackage>, onResult: (Boolean, String) -> Unit) {
        launch {
            val res = timedCloud("نشر الدفعة") {
                com.zmastery.english.cloud.CloudSync.publishLessonsBatchToCloud(packages)
            }
            res.onSuccess { count ->
                verifyCloudLessons()
                onResult(true, "تم نشر $count درس بنجاح لجميع الطلاب سحابياً ✓")
                syncCloudLessons(silent = false)
            }.onFailure { e ->
                onResult(false, "فشل النشر السحابي — ${describeCloudError(e)}")
            }
        }
    }

    /**
     * Publish ALL locally stored lessons to Firestore `/lessons` collection.
     */
    fun publishAllLocalLessonsToCloud(onResult: (Boolean, String) -> Unit) {
        launch {
            val allLessons = vm.lessons.toList()
            if (allLessons.isEmpty()) {
                onResult(false, "لا توجد دروس محلية لنشرها")
                return@launch
            }
            // لا نشر بلا جلسة سحابية — وإلا فشلت كل الدروس برسالة غامضة واحدة.
            if (ensureCloudSession() == null) {
                onResult(false, "لا يوجد اتصال بالسحابة الآن — تحقق من الإنترنت ثم أعد المحاولة")
                return@launch
            }
            cloudPublishMessage = "جارٍ رفع ${allLessons.size} درساً إلى السحابة…"
            var count = 0
            var failed = 0
            var firstError: Throwable? = null
            val publishedDocs = mutableMapOf<String, Long>()
            allLessons.forEach { lesson ->
                val course = vm.courses.firstOrNull { it.id == lesson.courseId }
                val courseKey = course?.jsonId?.ifBlank { null } ?: course?.key?.ifBlank { null } ?: "course_${lesson.courseId}"
                val docId = "${courseKey}_lesson_${lesson.no}"
                // اسم المسار التخصصي وأيقونته حتى يصل المنهج المخصص للطلاب
                // بهويته الكاملة («إنجليزية المساحة والطرق» لا «المسار التخصصي 4»).
                val lvl = vm.allLevels.firstOrNull { it.id == (course?.levelId ?: 1) }
                
                val vocabWords = vm.vocab.filter { it.lessonId == lesson.id || lesson.newWordIds.contains(it.id) }
                val globalVocab = vocabWords.map {
                    JsonGlobalWord(
                        word = it.english,
                        meaning = it.arabic,
                        exampleEn = it.exampleEn,
                        exampleAr = it.exampleAr,
                        phonetic = it.phonetic,
                        mentalImage = it.mentalImage,
                    )
                }
                
                val pkg = LessonPackage(
                    metadata = LessonMeta(
                        courseId = courseKey,
                        courseNameAr = course?.name ?: "",
                        level = course?.levelId ?: 1,
                        levelName = lvl?.name ?: "",
                        levelEmoji = lvl?.emoji ?: "",
                        lessonNo = lesson.no,
                        title = lesson.title,
                        style = course?.style?.name ?: "",
                        courseType = course?.type?.name ?: "",
                    ),
                    lessonContent = LessonContent(
                        fullTextEn = lesson.fullTextEn.ifBlank { lesson.readingEn },
                        fullTextAr = lesson.fullTextAr.ifBlank { lesson.readingAr },
                        segments = lesson.segments.map { JsonSentence(it.en, it.ar) },
                        explanationAr = lesson.explanationAr.ifBlank { lesson.summaryAr },
                        logicAr = lesson.logicAr,
                        examples = lesson.examples.map { JsonSentence(it.en, it.ar) },
                        dialogue = lesson.dialogues.map { JsonDialogue(it.speaker, it.en, it.ar) },
                        keyExpressions = lesson.keyExpressions.map { JsonKeyExpression(it.expressionEn, it.expressionAr, it.usageAr) },
                        keySentences = lesson.keySentences.map { JsonSentence(it.en, it.ar) },
                        topicEn = lesson.topicEn,
                        topicAr = lesson.topicAr,
                        brainstormingQuestions = lesson.brainstorming.map { JsonBrainstorm(it.questionEn, it.questionAr, it.suggestedAnswerEn, it.suggestedAnswerAr) },
                        guidedSentences = lesson.guidedSentences.map { JsonSentence(it.en, it.ar) },
                        finalDraft = lesson.finalDraft?.let { JsonSentence(it.en, it.ar) } ?: JsonSentence(),
                    ),
                    globalVocabulary = globalVocab,
                    lessonNotes = lesson.notes,
                    quiz = lesson.quiz.map { q ->
                        JsonQuiz(
                            type = q.type.name.lowercase(),
                            question = q.question,
                            options = q.options,
                            answer = q.answer,
                            explanationAr = q.explanationAr,
                            wordToSpeak = q.audioText,
                        )
                    },
                )
                
                // بمهلة لكل درس: درس واحد بلا اتصال لا يُجمّد رفع البقية ولا الزر.
                val res = timedCloud("رفع درس ${lesson.no}") {
                    com.zmastery.english.cloud.CloudSync.publishLessonToCloud(pkg)
                }
                if (res.isSuccess) {
                    count++
                    publishedDocs[docId] = System.currentTimeMillis()
                } else {
                    failed++
                    firstError = firstError ?: res.exceptionOrNull()
                }
                cloudPublishMessage = "جارٍ الرفع… $count/${allLessons.size}"
            }
            // شارة «تم الرفع» لكل درس نجح نشره فعلاً — لا لكل درس حاولنا رفعه.
            if (publishedDocs.isNotEmpty()) applyCloudMarks(publishedDocs)
            pushProgressToCloud()
            // الإصلاح الجوهري: قبل هذا كانت الرسالة «تم النشر» تُرسل حتى لو فشل
            // رفع كل الدروس، فتبدو العملية ناجحة بينما السحابة فارغة.
            val msg = when {
                count == 0 ->
                    "لم يُرفع أي درس ✗ — ${firstError?.let { describeCloudError(it) } ?: "سبب غير معروف"}"
                failed == 0 ->
                    "تم رفع $count درساً بنجاح إلى السحابة ✓ — ستصل لكل الطلاب عند فتح التطبيق"
                else ->
                    "تم رفع $count درساً · وفشل رفع $failed ✗ — " +
                        (firstError?.let { describeCloudError(it) } ?: "سبب غير معروف")
            }
            cloudPublishMessage = msg
            onResult(count > 0, msg)
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
            // بمهلة: إقلاع التطبيق بلا إنترنت لا يجوز أن يعلّق مسار البدء.
            withTimeoutOrNull(CLOUD_TIMEOUT_MS) {
                runCatching { com.zmastery.english.cloud.CloudAuth.ensureSignedIn() }
            }
            refreshCloudAuthState()
            val uid = cloudUid ?: return@launch
            // Pull the cloud snapshot and MERGE it in — never replace. Local
            // content always survives; cloud-only items join in. (The old
            // `lastCloudLessonSyncMillis == 0L` gate + full restoreFrom was
            // the data-loss bug: a stale cloud snapshot wiped freshly
            // imported lessons on every launch.)
            withTimeoutOrNull(CLOUD_TIMEOUT_MS) {
                runCatching {
                    com.zmastery.english.cloud.CloudSync.pullProgress(uid).getOrNull()
                }.getOrNull()
            }?.let { cloudJson ->
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
            val result = timedCloud("مزامنة الدروس") {
                com.zmastery.english.cloud.CloudSync.pullNewLessons(lastCloudLessonSyncMillis)
            }
            result.onSuccess { sync ->
                if (sync.packages.isNotEmpty()) {
                    vm.importLessons(sync.packages)
                    newLessonsFromCloud += sync.packages.size
                    // كل درس وصل من السحابة موجود فيها بالتعريف → شارة «تم الرفع».
                    applyCloudMarks(
                        sync.packages.associate { pkg ->
                            "${pkg.metadata.courseId}_lesson_${pkg.metadata.lessonNo}" to
                                System.currentTimeMillis()
                        }
                    )
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
            // بمهلة حتى لا يعلّق دفعُ التقدم أي سلسلة استدعاءات تنتظره.
            withTimeoutOrNull(CLOUD_TIMEOUT_MS) {
                com.zmastery.english.cloud.CloudSync.pushProgress(uid, raw)
            }
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
                    withTimeoutOrNull(CLOUD_TIMEOUT_MS) {
                        runCatching {
                            com.zmastery.english.cloud.CloudSync.pullProgress(user.uid).getOrNull()
                        }.getOrNull()
                    }?.let { cloudJson ->
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
                    withTimeoutOrNull(CLOUD_TIMEOUT_MS) {
                        runCatching {
                            com.zmastery.english.cloud.CloudSync.pullProgress(user.uid).getOrNull()
                        }.getOrNull()
                    }?.let { cloudJson ->
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
            val res = timedCloud("مزامنة العبارات") { com.zmastery.english.cloud.CloudSync.pullQuotes() }
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
     */
    fun addQuote(text: String, author: String, onResult: (Boolean, String) -> Unit) {
        if (!isAdmin) {
            onResult(false, "إضافة العبارات متاحة للمسؤول فقط — فعّل وضع المطور من الإعدادات")
            return
        }
        if (text.isBlank()) {
            onResult(false, "نص العبارة فارغ")
            return
        }
        isAddingQuote = true
        launch {
            val uid = ensureCloudSession()
            if (uid == null) {
                isAddingQuote = false
                onResult(false, "لا يوجد اتصال بالسحابة الآن — تحقق من الإنترنت ثم أعد المحاولة")
                return@launch
            }
            val res = timedCloud("نشر العبارة") {
                com.zmastery.english.cloud.CloudSync.addQuote(text, author, uid)
            }
            isAddingQuote = false
            res.onSuccess {
                quoteMessage = "تم نشر العبارة لكل الأجهزة ✓"
                syncQuotes()
                onResult(true, quoteMessage!!)
            }.onFailure { e ->
                quoteMessage = "فشل نشر العبارة — ${describeCloudError(e)}"
                onResult(false, quoteMessage!!)
            }
        }
    }
}
