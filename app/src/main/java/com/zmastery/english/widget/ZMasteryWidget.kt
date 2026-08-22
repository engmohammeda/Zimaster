package com.zmastery.english.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.zmastery.english.MainActivity
import com.zmastery.english.R
import com.zmastery.english.data.ProgressStore
import com.zmastery.english.data.Quotes

/**
 * Duolingo-style home-screen widget:
 * streak flame + days, XP, a powerful rotating motivational quote,
 * daily-goal progress bar, and a "Study now" call-to-action that opens the app.
 *
 * المرحلة الرابعة — الودجت نفسه منبّه بصري صامت:
 *   • CRACKING → خلفية حمراء متصدّعة + عدّاد خطر (السلسلة على وشك الضياع)
 *   • BROKEN   → خلفية بنفسجية متوهّجة (مهمة إنقاذ متاحة)
 *
 * IMPORTANT — reliability fix: launchers like MIUI mark a widget "can't load"
 * the moment [AppWidgetManager.updateAppWidget] is never called for it after
 * a refresh. The previous version wrapped the ENTIRE render in one
 * `runCatching`, so a single exception anywhere (a bad drawable, a corrupt
 * saved value, a locale quirk) meant `updateAppWidget` was skipped entirely —
 * and the widget would stay stuck on "can't load" forever, even after fixing
 * the underlying bug, because nothing ever pushed a fresh RemoteViews again.
 *
 * The fix: every individual piece of data is read defensively with its own
 * fallback, and [AppWidgetManager.updateAppWidget] is called UNCONDITIONALLY
 * at the end of [updateWidget] — the widget always renders *something*
 * sensible, never nothing.
 */
class ZMasteryWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH || intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            runCatching {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(ComponentName(context, ZMasteryWidget::class.java))
                if (ids != null && ids.isNotEmpty()) onUpdate(context, mgr, ids)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.zmastery.english.WIDGET_REFRESH"

        /** Call from the app to push fresh stats to all widgets. */
        fun refreshAll(context: Context) {
            runCatching {
                val intent = Intent(context, ZMasteryWidget::class.java).apply {
                    action = ACTION_REFRESH
                }
                context.sendBroadcast(intent)
            }
        }

        private fun updateWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            // Every read below is independently defensive — a failure in ONE
            // never prevents the final updateAppWidget() call from happening.
            val data = runCatching { ProgressStore.load(context) }.getOrNull()
            val quote = runCatching { Quotes.today() }.getOrNull()
            val views = RemoteViews(context.packageName, R.layout.widget_zmastery)

            val streak = data?.streak ?: 0
            val xp = data?.xp ?: 0
            val reviews = data?.reviewsToday ?: 0
            val goal = (data?.dailyGoal ?: 30).coerceAtLeast(1)
            val hasContent = data?.hasContent ?: false

            // ── المرحلة الرابعة · حالة الصندوق ──
            val mood = data?.chestMood ?: "IDLE"
            val cracking = mood == "CRACKING"
            val broken = mood == "BROKEN"

            // الخلفية تتبدّل حسب الخطر — منبّه صامت يلفت العين من شاشة الهاتف.
            runCatching {
                views.setInt(
                    R.id.widget_root, "setBackgroundResource",
                    when {
                        cracking -> R.drawable.widget_bg_cracking
                        broken -> R.drawable.widget_bg_rescue
                        else -> R.drawable.widget_bg
                    },
                )
            }

            // ── الجزء 3 · طبقة الدخان الأحمر المتصاعد ──
            runCatching {
                if (cracking) {
                    val severity = data?.decaySeverity ?: 0f
                    views.setInt(
                        R.id.widget_smoke, "setImageResource",
                        when {
                            severity >= 0.66f -> R.drawable.widget_smoke_3
                            severity >= 0.33f -> R.drawable.widget_smoke_2
                            else -> R.drawable.widget_smoke_1
                        },
                    )
                    views.setViewVisibility(R.id.widget_smoke, android.view.View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.widget_smoke, android.view.View.GONE)
                }
            }

            runCatching {
                views.setTextViewText(
                    R.id.widget_streak,
                    when {
                        cracking && streak > 0 -> "\u26A0 $streak يوم في خطر"
                        broken -> "أنقذ شعلتك"
                        streak > 0 -> "$streak يوم"
                        else -> "ابدأ اليوم"
                    },
                )
            }
            runCatching { views.setTextViewText(R.id.widget_xp, "⚡ $xp XP") }
            runCatching {
                views.setTextViewText(R.id.widget_quote, "”${quote?.text ?: "ابدأ رحلتك في تعلم الإنجليزية"}“")
                views.setTextViewText(R.id.widget_author, "— ${quote?.author ?: "Z-Mastery"}")
            }

            runCatching {
                val pct = (reviews * 100 / goal).coerceIn(0, 100)
                views.setProgressBar(R.id.widget_progressbar, 100, pct, false)
                // A brand-new install must not claim progress it does not have.
                views.setTextViewText(
                    R.id.widget_progress_value,
                    if (hasContent) "$reviews / $goal" else "—",
                )
                views.setTextViewText(
                    R.id.widget_progress_label,
                    when {
                        cracking -> "سلسلتك تتصدّع!"
                        broken -> "استعد شعلتك"
                        !hasContent -> "لم تبدأ بعد"
                        pct >= 100 -> "تم إنجاز هدف اليوم 🎉"
                        else -> "هدف اليوم"
                    },
                )
            }
            runCatching {
                views.setTextViewText(
                    R.id.widget_cta,
                    when {
                        cracking -> "أنقذ سلسلتك الآن"
                        broken -> "مهمة إنقاذ عاجلة"
                        !hasContent -> "أضف محتواك للبدء"
                        (reviews * 100 / goal) >= 100 -> "أحسنت! واصل التقدم"
                        else -> "ابدأ الدراسة الآن"
                    },
                )
            }

            // Tap anywhere -> open app (deep-links straight to review when at risk)
            runCatching {
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    if (cracking || broken) putExtra("nav_route", "review")
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                val pending = PendingIntent.getActivity(context, 0, openIntent, flags)
                views.setOnClickPendingIntent(R.id.widget_root, pending)
                views.setOnClickPendingIntent(R.id.widget_cta, pending)
            }

            // GUARANTEED — always push whatever we managed to build, so the
            // launcher never sees a widget that "never finished loading".
            runCatching { mgr.updateAppWidget(widgetId, views) }
        }
    }
}
