package com.zmastery.english.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zmastery.english.data.SampleData

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!NotifPrefs.enabled(context)) return
        val ctx = context.applicationContext
        val prefs = ctx.getSharedPreferences("z_notif_state", Context.MODE_PRIVATE)

        // Never nag a learner who has not set the app up yet — an empty install
        // has nothing to review and nothing to lose.
        val hasContent = prefs.getBoolean("has_content", false)
        val hasHistory = prefs.getBoolean("has_history", false)

        when (intent.action) {
            NotifScheduler.ACTION_DAILY -> {
                val due = prefs.getInt("due_words", 0)
                val phrase = SampleData.dailyPhrases.random()
                if (!hasContent) {
                    Notifier.setupReminder(ctx, phrase.substringBefore(" \u2014"))
                } else {
                    Notifier.dailyReminder(ctx, due, phrase.substringBefore(" \u2014"))
                }
            }
            NotifScheduler.ACTION_STREAK -> {
                if (!NotifPrefs.streakAlerts(ctx)) return
                if (!hasContent) return              // nothing to study yet
                val streak = prefs.getInt("streak", 0)
                val doneToday = prefs.getBoolean("done_today", false)
                if (doneToday) return                // nothing at risk — stay quiet
                val rescueActive = prefs.getBoolean("rescue_active", false)
                val rescueStreak = prefs.getInt("rescue_streak", 0)
                when {
                    // Stage 4: a broken streak is recoverable — invite, never blame.
                    rescueActive -> Notifier.rescueMission(ctx, rescueStreak)
                    streak > 0 -> Notifier.streakAtRisk(ctx, streak)
                    hasHistory -> Notifier.comeBack(ctx)
                    else -> Notifier.firstSession(ctx)
                }
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Re-schedule notification alarms
            NotifScheduler.rescheduleAll(context.applicationContext)
            // Refresh widget so it doesn't show stale "can't load" state
            com.zmastery.english.widget.ZMasteryWidget.refreshAll(context.applicationContext)
        }
    }
}
