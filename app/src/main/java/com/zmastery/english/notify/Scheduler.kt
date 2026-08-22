package com.zmastery.english.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/** Persisted notification preferences (lightweight SharedPreferences store). */
object NotifPrefs {
    private const val FILE = "z_notif_prefs"
    const val KEY_ENABLED = "enabled"
    const val KEY_HOUR = "hour"
    const val KEY_MINUTE = "minute"
    const val KEY_STREAK_ALERTS = "streak_alerts"

    fun enabled(ctx: Context) = prefs(ctx).getBoolean(KEY_ENABLED, true)
    fun setEnabled(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_ENABLED, v).apply()

    fun hour(ctx: Context) = prefs(ctx).getInt(KEY_HOUR, 20)
    fun minute(ctx: Context) = prefs(ctx).getInt(KEY_MINUTE, 0)
    fun setTime(ctx: Context, h: Int, m: Int) = prefs(ctx).edit().putInt(KEY_HOUR, h).putInt(KEY_MINUTE, m).apply()

    fun streakAlerts(ctx: Context) = prefs(ctx).getBoolean(KEY_STREAK_ALERTS, true)
    fun setStreakAlerts(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_STREAK_ALERTS, v).apply()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

object NotifScheduler {

    const val ACTION_DAILY = "com.zmastery.english.DAILY_REMINDER"
    const val ACTION_STREAK = "com.zmastery.english.STREAK_ALERT"
    private const val REQ_DAILY = 5101
    private const val REQ_STREAK = 5102

    fun rescheduleAll(ctx: Context) {
        cancelAll(ctx)
        if (!NotifPrefs.enabled(ctx)) return
        scheduleDaily(ctx, NotifPrefs.hour(ctx), NotifPrefs.minute(ctx))
        if (NotifPrefs.streakAlerts(ctx)) scheduleStreak(ctx)
    }

    fun scheduleDaily(ctx: Context, hour: Int, minute: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = nextTrigger(hour, minute)
        am.setRepeatingSafe(ctx, ACTION_DAILY, REQ_DAILY, next, AlarmManager.INTERVAL_DAY)
    }

    /** Streak "nag" fires in the evening (2 hours before daily if possible) as a safety net. */
    fun scheduleStreak(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // fire at 21:30 daily as a positive nudge / streak guardian
        val next = nextTrigger(21, 30)
        am.setRepeatingSafe(ctx, ACTION_STREAK, REQ_STREAK, next, AlarmManager.INTERVAL_DAY)
    }

    fun cancelAll(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx, ACTION_DAILY, REQ_DAILY))
        am.cancel(pending(ctx, ACTION_STREAK, REQ_STREAK))
    }

    private fun nextTrigger(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= now.timeInMillis) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    private fun pending(ctx: Context, action: String, req: Int): PendingIntent {
        val i = Intent(ctx, NotificationReceiver::class.java).apply { this.action = action }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(ctx, req, i, flags)
    }

    private fun AlarmManager.setRepeatingSafe(ctx: Context, action: String, req: Int, triggerAt: Long, interval: Long) {
        val pi = pending(ctx, action, req)
        // Use inexact repeating — battery friendly and doesn't need exact-alarm permission
        setInexactRepeating(AlarmManager.RTC_WAKEUP, triggerAt, interval, pi)
    }

    /** Fire an immediate test notification (used from Settings). */
    fun fireTest(ctx: Context) {
        NotifChannels.ensure(ctx)
        Notifier.dailyReminder(ctx, 12, "Small steps every day lead to big results.")
    }
}
