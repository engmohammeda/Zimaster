package com.zmastery.english.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.zmastery.english.MainActivity
import com.zmastery.english.R
import com.zmastery.english.data.ProgressStore

/**
 * Z-Mastery home-screen widget — تخطيط «القيادة المدمجة + كلمة اللحظة».
 *
 * التخطيط القديم كدّس العناصر رأسياً داخل ودجت عريض، فأهدر العرض وضغط كل
 * عنصر، وأعطى الاقتباس أكبر مساحة رغم أنه لا يُعلّم شيئاً. الجديد:
 *
 *   • ثلاثة أعمدة أفقية: شعلة │ حالة + هدف + نقاط أسبوع │ زر.
 *   • نقاط الأسبوع السبع — تُظهر نمط المواظبة لا الرقم المجرد.
 *   • صف «كلمة اللحظة»: الكلمة المستحقة للمراجعة الآن (FSRS) بالنطق
 *     والترجمة — فكل نظرة للهاتف تصير تعرّضاً حقيقياً للمفردة.
 *     يختفي الصف تلقائياً حين لا توجد كلمة مستحقة.
 *
 * 100% RemoteViews — لا Canvas ولا حلقات دائرية، متوافق مع Samsung (OneUI)
 * وXiaomi (MIUI/HyperOS) وHuawei (EMUI) وPixel وOppo وVivo والمشغّلات العامة.
 */
class ZMasteryWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == ACTION_REFRESH ||
            action == AppWidgetManager.ACTION_APPWIDGET_UPDATE ||
            action == Intent.ACTION_USER_PRESENT ||
            action == Intent.ACTION_BOOT_COMPLETED
        ) {
            try {
                val mgr = AppWidgetManager.getInstance(context)
                val cn = ComponentName(context, ZMasteryWidget::class.java)
                val ids = mgr.getAppWidgetIds(cn)
                if (ids != null && ids.isNotEmpty()) {
                    for (id in ids) {
                        updateWidget(context, mgr, id)
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error in onReceive", e)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.zmastery.english.WIDGET_REFRESH"
        private const val TAG = "ZMasteryWidget"

        /** Call from the app whenever progress or streak changes */
        fun refreshAll(context: Context) {
            try {
                val intent = Intent(context, ZMasteryWidget::class.java).apply {
                    this.action = ACTION_REFRESH
                }
                context.sendBroadcast(intent)
            } catch (e: Throwable) {
                Log.e(TAG, "Error broadcasting refresh", e)
            }
        }

        fun updateWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_zmastery)

                val data = try {
                    ProgressStore.load(context)
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed to load progress snapshot", e)
                    null
                }

                val streak = data?.streak ?: 0
                val reviews = data?.reviewsToday ?: 0
                val goal = (data?.dailyGoal ?: 30).coerceAtLeast(1)
                val hasContent = data?.hasContent ?: false
                val daySecured = data?.minimumDone ?: false
                val mood = data?.chestMood ?: "IDLE"
                val cracking = mood == "CRACKING"
                val broken = mood == "BROKEN"

                // 1. Mood-aware background
                try {
                    val bgRes = when {
                        cracking -> R.drawable.widget_bg_cracking
                        broken -> R.drawable.widget_bg_rescue
                        else -> R.drawable.widget_bg
                    }
                    views.setImageViewResource(R.id.widget_bg_image, bgRes)
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to set background", e)
                }

                // 2. Smoke overlay when the streak is at risk
                try {
                    if (cracking) {
                        val severity = data?.decaySeverity ?: 0f
                        val smokeRes = when {
                            severity >= 0.66f -> R.drawable.widget_smoke_3
                            severity >= 0.33f -> R.drawable.widget_smoke_2
                            else -> R.drawable.widget_smoke_1
                        }
                        views.setImageViewResource(R.id.widget_smoke, smokeRes)
                        views.setViewVisibility(R.id.widget_smoke, View.VISIBLE)
                    } else {
                        views.setViewVisibility(R.id.widget_smoke, View.GONE)
                    }
                } catch (e: Throwable) {
                    views.setViewVisibility(R.id.widget_smoke, View.GONE)
                }

                // 3. العمود ① — رقم الشعلة (الوسم صار كلمة واحدة: العرض ضيق)
                try {
                    views.setTextViewText(R.id.widget_streak_number, streak.toString())
                    val streakLabel = when {
                        broken -> "أنقذها"
                        streak > 0 -> "يوم"
                        else -> "ابدأ"
                    }
                    views.setTextViewText(R.id.widget_streak_label, streakLabel)
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to set streak hero", e)
                }

                // 3b. نقاط الأسبوع السبع — النمط لا الرقم.
                // النقطة الممتلئة = يوم دُرِس فعلاً؛ الأخيرة هي اليوم.
                try {
                    val week = data?.weekDays ?: List(7) { false }
                    val dayIds = intArrayOf(
                        R.id.widget_day_0, R.id.widget_day_1, R.id.widget_day_2,
                        R.id.widget_day_3, R.id.widget_day_4, R.id.widget_day_5,
                        R.id.widget_day_6,
                    )
                    for (i in dayIds.indices) {
                        val studied = week.getOrElse(i) { false }
                        views.setTextViewText(dayIds[i], if (studied) "●" else "○")
                        // اليوم الحالي (الأخير) يُبرز بالأبيض الكامل حتى لو لم يُدرس بعد.
                        val isToday = i == dayIds.lastIndex
                        views.setTextColor(
                            dayIds[i],
                            when {
                                studied -> 0xFFFFFFFF.toInt()
                                isToday -> 0xFFE8EAF8.toInt()
                                else -> 0x66FFFFFF
                            },
                        )
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to set week dots", e)
                }

                // 4. Day-status pill — the heart of "يوم الحماسة"
                try {
                    val status = when {
                        broken -> "🚨 مهمة إنقاذ عاجلة"
                        cracking -> "⚠️ سلسلتك في خطر"
                        daySecured -> "يومك مؤمَّن ✓"
                        !hasContent -> "ابدأ رحلتك"
                        else -> "أكمل هدف اليوم"
                    }
                    views.setTextViewText(R.id.widget_status, status)
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to set status", e)
                }

                // 5. صف «كلمة اللحظة» — الودجت يُعلّم لا يُذكّر فقط.
                //
                // يعرض الكلمة الأكثر استحقاقاً للمراجعة الآن (FSRS). لا كلمة
                // مستحقة ⇒ يختفي الصف بالكامل، فيبقى الودجت نظيفاً في 4×2
                // ويتمدّد لعرضها في 4×3 — بلا تخطيط ثانٍ.
                try {
                    val wordEn = data?.wordEn.orEmpty()
                    if (wordEn.isNotBlank()) {
                        views.setViewVisibility(R.id.widget_word_row, View.VISIBLE)
                        views.setTextViewText(R.id.widget_word_en, wordEn)
                        views.setTextViewText(R.id.widget_word_ipa, data?.wordIpa.orEmpty())
                        views.setTextViewText(R.id.widget_word_ar, data?.wordAr.orEmpty())
                    } else {
                        views.setViewVisibility(R.id.widget_word_row, View.GONE)
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to set word row", e)
                    try {
                        views.setViewVisibility(R.id.widget_word_row, View.GONE)
                    } catch (_: Throwable) {
                    }
                }

                // 6. Daily learning-goal progress (الوسم انتقل إلى حبة الحالة)
                try {
                    val pct = ((reviews.toFloat() / goal) * 100).toInt().coerceIn(0, 100)
                    views.setProgressBar(R.id.widget_progressbar, 100, pct, false)
                    views.setTextViewText(R.id.widget_progress_value, "$reviews/$goal")
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to set progress", e)
                }

                // 7. CTA — نص قصير جداً: الزر صار عموداً ضيقاً لا شريطاً عريضاً
                try {
                    val ctaText = when {
                        cracking -> "أنقذ"
                        broken -> "إنقاذ"
                        reviews >= goal -> "المزيد"
                        else -> "ابدأ"
                    }
                    views.setTextViewText(R.id.widget_cta, ctaText)
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to set cta text", e)
                }

                // 8. النقر → فتح التطبيق. الجذر والزر يفتحان المسار العام،
                //    وصف الكلمة يفتح المراجعة مباشرة (نية منفصلة بطلب مختلف
                //    حتى لا يدهس أحدهما الآخر في ذاكرة PendingIntent).
                try {
                    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }

                    val openIntent = Intent(context, MainActivity::class.java).apply {
                        this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        if (cracking || broken) putExtra("nav_route", "review")
                    }
                    val pending = PendingIntent.getActivity(context, widgetId, openIntent, flags)
                    views.setOnClickPendingIntent(R.id.widget_root, pending)
                    views.setOnClickPendingIntent(R.id.widget_cta, pending)

                    val reviewIntent = Intent(context, MainActivity::class.java).apply {
                        this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("nav_route", "review")
                    }
                    val reviewPending = PendingIntent.getActivity(
                        context,
                        // مُعرّف طلب مختلف — وإلا أعاد النظام نفس النية للاثنين.
                        widgetId + 100_000,
                        reviewIntent,
                        flags,
                    )
                    views.setOnClickPendingIntent(R.id.widget_word_row, reviewPending)
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to set pending intent", e)
                }

                mgr.updateAppWidget(widgetId, views)
            } catch (e: Throwable) {
                Log.e(TAG, "Fatal error updating widget $widgetId", e)
            }
        }
    }
}
