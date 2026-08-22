package com.zmastery.english.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.zmastery.english.MainActivity
import com.zmastery.english.R

/** Notification channels, each with a dedicated custom sound + importance. */
object NotifChannels {
    const val DAILY = "z_daily"          // daily study reminder
    const val STREAK = "z_streak"        // motivation / streak-at-risk (high urgency)
    const val ACHIEVE = "z_achieve"      // success / achievement celebrations

    fun ensure(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return

        val audioAttrs = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()

        fun sound(res: Int): Uri =
            Uri.parse("android.resource://${ctx.packageName}/$res")

        val daily = NotificationChannel(DAILY, "التذكير اليومي", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "تذكير بجلسة التعلم اليومية"
            setSound(sound(R.raw.notify_daily), audioAttrs)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 180, 120, 180)
            enableLights(true)
        }
        val streak = NotificationChannel(STREAK, "تنبيهات الحماسة", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "تنبيهات السلسلة والحماسة"
            setSound(sound(R.raw.notify_streak), audioAttrs)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 100, 250, 100, 400)
            enableLights(true)
        }
        val achieve = NotificationChannel(ACHIEVE, "الإنجازات", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "احتفالات إتمام المهام والإنجازات"
            setSound(sound(R.raw.notify_success), audioAttrs)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 120, 80, 120, 80, 120)
        }
        nm.createNotificationChannels(listOf(daily, streak, achieve))
    }
}

object Notifier {

    fun canPost(ctx: Context): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    private fun contentIntent(ctx: Context, route: String?): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            route?.let { putExtra("nav_route", it) }
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(ctx, route.hashCode(), intent, flags)
    }

    fun show(
        ctx: Context,
        channel: String,
        id: Int,
        title: String,
        body: String,
        emoji: String = "",
        route: String? = "dashboard",
        color: Int = 0xFFE07856.toInt(),
        big: Boolean = true,
    ) {
        NotifChannels.ensure(ctx)
        if (!canPost(ctx)) return

        val fullTitle = if (emoji.isNotBlank()) "$emoji  $title" else title
        val builder = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(fullTitle)
            .setContentText(body)
            .setColor(color)
            .setColorized(true)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(ctx, route))
            .setPriority(if (channel == NotifChannels.STREAK) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        if (big) builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))

        try {
            NotificationManagerCompat.from(ctx).notify(id, builder.build())
        } catch (_: SecurityException) {
        }
    }

    // ---- Convenience presets ----
    fun dailyReminder(ctx: Context, dueWords: Int, phrase: String) {
        val body = if (dueWords > 0)
            "لديك $dueWords كلمة جاهزة للمراجعة اليوم. خصّص 10 دقائق الآن!\n\n\u201C$phrase\u201D"
        else
            "حان وقت جلسة التعلم اليومية. افتح درساً جديداً وواصل تقدمك!\n\n\u201C$phrase\u201D"
        show(ctx, NotifChannels.DAILY, 1001, "وقت التعلم مع Z-Mastery", body, "\uD83D\uDCDA", "review", 0xFFE07856.toInt())
    }

    fun streakAtRisk(ctx: Context, streak: Int) {
        show(
            ctx, NotifChannels.STREAK, 1002,
            "حماستك في خطر!",
            "سلسلتك $streak يوم على وشك الانقطاع \uD83D\uDD25 أكمل مهمة واحدة الآن لإنقاذها قبل منتصف الليل!",
            "\u26A0\uFE0F", "dashboard", 0xFFD9776A.toInt(),
        )
    }

    /**
     * Stage 4 — the rescue invitation. Fired when a streak has broken and a
     * recovery mission is waiting. Deliberately warm: no blame, only a door.
     */
    fun rescueMission(ctx: Context, streak: Int) {
        show(
            ctx, NotifChannels.STREAK, 1005,
            "مهمة إنقاذ عاجلة \uD83D\uDFE3",
            if (streak > 0)
                "سلسلتك ($streak يوماً) لم تضِع بعد — أنجز مهمة قصيرة (3 دقائق) لتستعيدها كاملة."
            else
                "بوابة الإنقاذ مفتوحة — 3 دقائق تكفي لتعود أقوى.",
            "\uD83D\uDEE1\uFE0F", "momentum", 0xFF8B5CF6.toInt(),
        )
    }

    /** Fresh install: guide setup instead of pretending there is work due. */
    fun setupReminder(ctx: Context, phrase: String) {
        show(
            ctx, NotifChannels.DAILY, 1001,
            "لنبدأ رحلتك مع Z-Mastery",
            "لم تُضف محتوى بعد. استورد كورساً أو أضف بضع كلمات لتبدأ خطتك اليومية.\n\n\u201C$phrase\u201D",
            "\uD83D\uDC4B", "import", 0xFFE07856.toInt(),
        )
    }

    /** Has content and history, but no active streak right now. */
    fun comeBack(ctx: Context) {
        show(
            ctx, NotifChannels.STREAK, 1003,
            "لنستأنف اليوم",
            "جلسة قصيرة تكفي لإعادة بناء سلسلتك \uD83D\uDD25 ابدأ بخمس كلمات فقط.",
            "\uD83E\uDDED", "review", 0xFFE0A34E.toInt(),
        )
    }

    /** Has content but has never studied — invite the very first session. */
    fun firstSession(ctx: Context) {
        show(
            ctx, NotifChannels.STREAK, 1003,
            "أول جلسة تبدأ سلسلتك",
            "محتواك جاهز \u2705 أكمل مراجعة واحدة اليوم لتضيء أول يوم في سجل حماستك.",
            "\uD83D\uDE80", "review", 0xFFE0A34E.toInt(),
        )
    }

    fun motivation(ctx: Context, streak: Int) {
        show(
            ctx, NotifChannels.STREAK, 1003,
            "أنت في أوج نشاطك!",
            "سلسلة $streak يوم متتالي \uD83D\uDD25 لا تكسرها اليوم — دقائق قليلة تكفي للحفاظ على تقدمك.",
            "\uD83D\uDE80", "dashboard", 0xFFE0A34E.toInt(),
        )
    }

    fun achievement(ctx: Context, title: String, detail: String) {
        show(ctx, NotifChannels.ACHIEVE, 1004, title, detail, "\uD83C\uDF89", "dashboard", 0xFF5E9C76.toInt())
    }
}
