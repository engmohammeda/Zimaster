package com.zmastery.english.data

/**
 * دمج حالتَي التقدّم (محلي + سحابي) بأمان — **المحلي أساس لا يُفقد منه شيء أبداً**.
 *
 * دواء «خلل فقدان البيانات»: كانت المزامنة عند الإقلاع تستبدل الحالة المحلية
 * بلقطة سحابية قديمة (فتمسح دروساً استُوردت للتو). هذا الدمج يستحيل أن يمسح
 * أي عنصر محلي — السحابة تُضيف فقط:
 *
 *  • الكورسات: اتحاد بهوية (id) أو (key).
 *  • الدروس: اتحاد بهوية (id) أو (courseId + no)؛ وعند التطابق يُتبنى
 *    إكمالُ السحابة (أكملتَ الدرس على جهازك الآخر) مع بقاء المحتوى المحلي.
 *  • الكلمات: المحلية تفوز عند التعارض (حالة FSRS أحدث على هذا الجهاز)،
 *    وسحابية جديدة فقط تُضاف (بهوية id أو النص الإنجليزي).
 *  • القصص/أيام النشاط/الاختبارات: اتحاد بهوية العنصر.
 *  • الملف الشخصي: المحلي يفوز؛ الاسم/البريد يُ adoptان فقط إن كان المحلي
 *    فارغاً؛ والسلسلة/XP/ساعات الدراسة تأخذ الأعلى بين الجهازين (لا رجوع
 *    في تقدّم مطلقاً).
 */
object StateMerger {

    fun merge(local: AppState, cloud: AppState): AppState {
        if (cloud == local) return local

        // ── الكورسات: محلي أولاً + سحابي غير موجود ──
        val courses = local.courses.toMutableList()
        cloud.courses.forEach { c ->
            if (courses.none { it.id == c.id || (c.key.isNotBlank() && it.key == c.key) }) courses += c
        }

        // ── الدروس: محلي أولاً + سحابي جديد + تبنّي الإكمال وشارة الرفع ──
        val lessons = local.lessons.toMutableList()
        cloud.lessons.forEach { cl ->
            val i = lessons.indexOfFirst {
                it.id == cl.id || (it.courseId == cl.courseId && it.no == cl.no)
            }
            if (i < 0) {
                lessons += cl
            } else {
                // أكمله المتعلّم على جهاز آخر → احفظ الإنجاز، أبقِ المحتوى المحلي.
                // وكذلك شارة «تم الرفع»: أحدث طابع زمني بين الجهازين يفوز.
                val done = lessons[i].isCompleted || cl.isCompleted
                val stamp = maxOf(lessons[i].publishedAtMillis, cl.publishedAtMillis)
                val docId = lessons[i].publishedDocId.ifBlank { cl.publishedDocId }
                if (done != lessons[i].isCompleted ||
                    stamp != lessons[i].publishedAtMillis ||
                    docId != lessons[i].publishedDocId
                ) {
                    lessons[i] = lessons[i].copy(
                        isCompleted = done,
                        publishedAtMillis = stamp,
                        publishedDocId = docId,
                    )
                }
            }
        }

        // ── الكلمات: المحلية تفوز (FSRS أحدث هنا) + سحابية جديدة ──
        val vocab = local.vocab.toMutableList()
        cloud.vocab.forEach { w ->
            if (vocab.none { it.id == w.id || it.english.equals(w.english, ignoreCase = true) }) vocab += w
        }

        // ── اتحادات خفيفة لبقية السجل ──
        val stories = local.stories.toMutableList()
        cloud.stories.forEach { s -> if (stories.none { it.id == s.id }) stories += s }

        val dayStats = local.dayStats.toMutableList()
        cloud.dayStats.forEach { ds -> if (dayStats.none { it.epochDay == ds.epochDay }) dayStats += ds }

        val exams = local.exams.toMutableList()
        cloud.exams.forEach { e -> if (exams.none { it.id == e.id }) exams += e }

        // ── الملف الشخصي: محلي يفوز، مع استثناءات مضادة للفقدان ──
        val p = local.profile
        val cp = cloud.profile
        val profile = p.copy(
            learnerName = p.learnerName.ifBlank { cp.learnerName },
            learnerEmail = p.learnerEmail.ifBlank { cp.learnerEmail },
            lastCloudLessonSyncMillis = maxOf(p.lastCloudLessonSyncMillis, cp.lastCloudLessonSyncMillis),
            // الإعلان المُغلق: لا نعيده للظهور على جهاز آخر أغلقه أيضاً.
            dismissedAnnouncementId = p.dismissedAnnouncementId.ifBlank { cp.dismissedAnnouncementId },
            streak = maxOf(p.streak, cp.streak),
            xp = maxOf(p.xp, cp.xp),
            studyHours = maxOf(p.studyHours, cp.studyHours),
        )

        return local.copy(
            courses = courses,
            lessons = lessons,
            vocab = vocab,
            stories = stories,
            dayStats = dayStats,
            exams = exams,
            profile = profile,
        )
    }
}
