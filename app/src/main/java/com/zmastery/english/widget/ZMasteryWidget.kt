package com.zmastery.english.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.zmastery.english.MainActivity
import com.zmastery.english.R
import com.zmastery.english.data.ProgressStore
import com.zmastery.english.data.Quotes

/**
 * Z-Mastery home-screen widget — compatible with Samsung, Xiaomi (MIUI),
 * Huawei (EMUI), Google Pixel, and stock Android launchers.
 *
 * Design principles for RemoteViews reliability:
 *  1. The layout XML uses LinearLayout root (no FrameLayout) — most compatible.
 *  2. No android:src defaults on ImageViews — all set programmatically.
 *  3. Every data read and view update is independently wrapped in try/catch.
 *  4. updateAppWidget() is called UNCONDITIONALLY at the end — the widget
 *     always renders SOMETHING, preventing "can't load" on all launchers.
 *  5. WidgetDiagnostics logs every stage for troubleshooting.
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
            try {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(ComponentName(context, ZMasteryWidget::class.java))
                if (ids != null && ids.isNotEmpty()) onUpdate(context, mgr, ids)
            } catch (e: Exception) {
                WidgetDiagnostics.logError("onReceive", -1, e)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.zmastery.english.WIDGET_REFRESH"

        /** Call from the app to push fresh stats to all widgets. */
        fun refreshAll(context: Context) {
            try {
                val intent = Intent(context, ZMasteryWidget::class.java).apply {
                    action = ACTION_REFRESH
                }
                context.sendBroadcast(intent)
            } catch (e: Exception) {
                WidgetDiagnostics.logError("refreshAll", -1, e)
            }
        }

        private fun updateWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            // ── Step 1: Load data defensively ──
            val data: ProgressStore.Snapshot? = try {
                ProgressStore.load(context)
            } catch (e: Exception) {
                WidgetDiagnostics.logError("load_data", widgetId, e)
                null
            }

            val quote = try {
                Quotes.today()
            } catch (e: Exception) {
                WidgetDiagnostics.logError("load_quote", widgetId, e)
                null
            }

            // Extract values with safe defaults
            val streak = data?.streak ?: 0
            val xp = data?.xp ?: 0
            val reviews = data?.reviewsToday ?: 0
            val goal = (data?.dailyGoal ?: 30).coerceAtLeast(1)
            val hasContent = data?.hasContent ?: false
            val mood = data?.chestMood ?: "IDLE"
            val cracking = mood == "CRACKING"
            val broken = mood == "BROKEN"

            WidgetDiagnostics.logDataState(widgetId, data != null, streak, xp, mood)

            // ── Step 2: Create RemoteViews ──
            val views = RemoteViews(context.packageName, R.layout.widget_zmastery)
            WidgetDiagnostics.logStage("create_views", widgetId)

            // ── Step 3: Set background based on mood ──
            try {
                val bgRes = when {
                    cracking -> R.drawable.widget_bg_cracking
                    broken -> R.drawable.widget_bg_rescue
                    else -> R.drawable.widget_bg
                }
                views.setInt(R.id.widget_root, "setBackgroundResource", bgRes)
                WidgetDiagnostics.logStage("set_background", widgetId, "mood=$mood")
            } catch (e: Exception) {
                WidgetDiagnostics.logError("set_background", widgetId, e)
            }

            // ── Step 4: Smoke layer (only when cracking) ──
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
                WidgetDiagnostics.logStage("set_smoke", widgetId, "cracking=$cracking")
            } catch (e: Exception) {
                WidgetDiagnostics.logError("set_smoke", widgetId, e)
                // Hide smoke on failure — never block the widget
                try { views.setViewVisibility(R.id.widget_smoke, View.GONE) } catch (_: Exception) {}
            }

            // ── Step 5: Streak text ──
            try {
                views.setTextViewText(
                    R.id.widget_streak,
                    when {
                        cracking && streak > 0 -> "\u26A0 $streak يوم في خطر"
                        broken -> "أنقذ شعلتك"
                        streak > 0 -> "$streak يوم"
                        else -> "ابدأ اليوم"
                    },
                )
            } catch (e: Exception) {
                WidgetDiagnostics.logError("set_streak", widgetId, e)
            }

            // ── Step 6: XP text ──
            try {
                views.setTextViewText(R.id.widget_xp, "⚡ $xp XP")
            } catch (e: Exception) {
                WidgetDiagnostics.logError("set_xp", widgetId, e)
            }

            // ── Step 7: Quote ──
            try {
                views.setTextViewText(
                    R.id.widget_quote,
                    ""${quote?.text ?: "ابدأ رحلتك في تعلم الإنجليزية"}""
                )
                views.setTextViewText(
                    R.id.widget_author,
                    "— ${quote?.author ?: "Z-Mastery"}"
                )
            } catch (e: Exception) {
                WidgetDiagnostics.logError("set_quote", widgetId, e)
            }

            // ── Step 8: Progress ──
            try {
                val pct = (reviews * 100 / goal).coerceIn(0, 100)
                views.setProgressBar(R.id.widget_progressbar, 100, pct, false)
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
                WidgetDiagnostics.logStage("set_progress", widgetId, "pct=$pct")
            } catch (e: Exception) {
                WidgetDiagnostics.logError("set_progress", widgetId, e)
            }

            // ── Step 9: CTA text ──
            try {
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
            } catch (e: Exception) {
                WidgetDiagnostics.logError("set_cta", widgetId, e)
            }

            // ── Step 10: Click handler ──
            try {
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    if (cracking || broken) putExtra("nav_route", "review")
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                val pending = PendingIntent.getActivity(context, 0, openIntent, flags)
                views.setOnClickPendingIntent(R.id.widget_root, pending)
                views.setOnClickPendingIntent(R.id.widget_cta, pending)
                WidgetDiagnostics.logStage("set_click", widgetId)
            } catch (e: Exception) {
                WidgetDiagnostics.logError("set_click", widgetId, e)
            }

            // ── Step 11: GUARANTEED update — always push to launcher ──
            try {
                mgr.updateAppWidget(widgetId, views)
                WidgetDiagnostics.logFinalUpdate(widgetId, success = true)
            } catch (e: Exception) {
                WidgetDiagnostics.logFinalUpdate(widgetId, success = false, error = e)
            }
        }
    }
}
