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
import com.zmastery.english.data.Quotes

/**
 * Z-Mastery home-screen widget.
 * Engineered for 100% compatibility across Samsung (OneUI), Xiaomi (MIUI/HyperOS),
 * Huawei (EMUI), Google Pixel, Oppo, Vivo, and generic Android launchers.
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

                // 1. Defensively load progress data
                val data = try {
                    ProgressStore.load(context)
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed to load progress snapshot", e)
                    null
                }

                val quote = try {
                    Quotes.today()
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed to load quote", e)
                    null
                }

                val streak = data?.streak ?: 0
                val xp = data?.xp ?: 0
                val reviews = data?.reviewsToday ?: 0
                val goal = (data?.dailyGoal ?: 30).coerceAtLeast(1)
                val hasContent = data?.hasContent ?: false
                val mood = data?.chestMood ?: "IDLE"
                val cracking = mood == "CRACKING"
                val broken = mood == "BROKEN"

                // 2. Set background
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

                // 3. Smoke overlay for cracking state
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

                // 4. Streak text
                try {
                    val streakText = when {
                        cracking && streak > 0 -> "$streak أيام ⚠️"
                        broken -> "أنقذ الشعلة"
                        streak > 0 -> "$streak أيام"
                        else -> "ابدأ اليوم"
                    }
                    views.setTextViewText(R.id.widget_streak, streakText)
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to set streak text", e)
                }

                // 5. XP text
                try {
                    views.setTextViewText(R.id.widget_xp, "⚡ $xp XP")
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to set xp text", e)
                }

                // 6. Quote text
                try {
                    val quoteText = quote?.text ?: "استمر في التعلم يومياً لتصل إلى الطلاقة."
                    val authorText = "— ${quote?.author ?: "Z-Mastery"}"
                    views.setTextViewText(R.id.widget_quote, "\"$quoteText\"")
                    views.setTextViewText(R.id.widget_author, authorText)
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to set quote text", e)
                }

                // 7. Progress & Target
                try {
                    val pct = ((reviews.toFloat() / goal) * 100).toInt().coerceIn(0, 100)
                    views.setProgressBar(R.id.widget_progressbar, 100, pct, false)
                    views.setTextViewText(
                        R.id.widget_progress_value,
                        if (hasContent) "$reviews / $goal" else "$reviews / $goal"
                    )
                    val labelText = when {
                        cracking -> "السلسلة في خطر!"
                        broken -> "استعد شعلتك"
                        pct >= 100 -> "تم إنجاز هدف اليوم 🎉"
                        else -> "هدف اليوم"
                    }
                    views.setTextViewText(R.id.widget_progress_label, labelText)
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to set progress", e)
                }

                // 8. CTA Button text
                try {
                    val ctaText = when {
                        cracking -> "أنقذ سلسلتك الآن 🔥"
                        broken -> "مهمة إنقاذ عاجلة ⚡"
                        reviews >= goal -> "أحسنت! راجع المزيد ✨"
                        else -> "ابدأ المراجعة الآن ⚡"
                    }
                    views.setTextViewText(R.id.widget_cta, ctaText)
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to set cta text", e)
                }

                // 9. Click pending intent
                try {
                    val openIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        if (cracking || broken) putExtra("nav_route", "review")
                    }
                    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                    val pending = PendingIntent.getActivity(context, widgetId, openIntent, flags)
                    views.setOnClickPendingIntent(R.id.widget_root, pending)
                    views.setOnClickPendingIntent(R.id.widget_cta, pending)
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to set pending intent", e)
                }

                // 10. Perform the update
                mgr.updateAppWidget(widgetId, views)
            } catch (e: Throwable) {
                Log.e(TAG, "Fatal error updating widget $widgetId", e)
            }
        }
    }
}
