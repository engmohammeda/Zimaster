package com.zmastery.english.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.zmastery.english.MainActivity
import com.zmastery.english.R

/**
 * Home-screen integration.
 *
 *  • Dynamic shortcuts  — long-press the app icon for "مراجعة / القاموس / المستويات".
 *  • Pinned shortcut    — a standalone "مراجعة سريعة" icon on the home screen.
 *  • Pinned widget      — the full stats widget.
 *
 * Everything is wrapped in runCatching: launchers vary wildly and a refusal
 * must never crash the app. Each entry point reports back through a Toast so
 * the action is never silently ignored (the old bug: nothing appeared to
 * happen because unsupported launchers were not handled).
 */
object HomeShortcuts {

    private const val ID_REVIEW = "sc_review"
    private const val ID_VOCAB = "sc_vocab"
    private const val ID_LEVELS = "sc_levels"

    private fun routeIntent(ctx: Context, route: String, id: String): Intent =
        Intent(ctx, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("nav_route", route)
            // A unique data URI keeps launchers from collapsing the shortcuts.
            `data` = android.net.Uri.parse("zmastery://$id")
        }

    /** Register the long-press shortcut menu. Safe to call on every launch. */
    fun installDynamic(ctx: Context) {
        runCatching {
            val list = listOf(
                ShortcutInfoCompat.Builder(ctx, ID_REVIEW)
                    .setShortLabel("مراجعة")
                    .setLongLabel("مراجعة الكلمات المستحقة")
                    .setIcon(IconCompat.createWithResource(ctx, R.mipmap.ic_launcher))
                    .setIntent(routeIntent(ctx, "review", ID_REVIEW))
                    .build(),
                ShortcutInfoCompat.Builder(ctx, ID_VOCAB)
                    .setShortLabel("القاموس")
                    .setLongLabel("فتح القاموس")
                    .setIcon(IconCompat.createWithResource(ctx, R.mipmap.ic_launcher))
                    .setIntent(routeIntent(ctx, "vocab", ID_VOCAB))
                    .build(),
                ShortcutInfoCompat.Builder(ctx, ID_LEVELS)
                    .setShortLabel("المستويات")
                    .setLongLabel("المستويات والمناهج")
                    .setIcon(IconCompat.createWithResource(ctx, R.mipmap.ic_launcher))
                    .setIntent(routeIntent(ctx, "levels", ID_LEVELS))
                    .build(),
            )
            ShortcutManagerCompat.setDynamicShortcuts(ctx, list)
        }
    }

    /** True when the launcher accepts pinned shortcuts. */
    fun canPinShortcut(ctx: Context): Boolean =
        runCatching { ShortcutManagerCompat.isRequestPinShortcutSupported(ctx) }.getOrDefault(false)

    /** Ask the launcher to pin a one-tap "quick review" icon. */
    fun pinReviewShortcut(ctx: Context) {
        val ok = runCatching {
            if (!ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) return@runCatching false
            val info = ShortcutInfoCompat.Builder(ctx, "sc_pin_review")
                .setShortLabel("مراجعة سريعة")
                .setLongLabel("Z-Mastery · مراجعة سريعة")
                .setIcon(IconCompat.createWithResource(ctx, R.mipmap.ic_launcher))
                .setIntent(routeIntent(ctx, "review", "sc_pin_review"))
                .build()
            ShortcutManagerCompat.requestPinShortcut(ctx, info, null)
            true
        }.getOrDefault(false)

        Toast.makeText(
            ctx,
            if (ok) "تأكيد الإضافة من نافذة المشغّل…"
            else "مشغّل هاتفك لا يدعم تثبيت الاختصارات — اسحب الأيقونة يدوياً",
            Toast.LENGTH_LONG,
        ).show()
    }

    /** True when the launcher accepts pinned widgets. */
    @SuppressLint("NewApi")
    fun canPinWidget(ctx: Context): Boolean = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@runCatching false
        val mgr = ctx.getSystemService(Context.APPWIDGET_SERVICE) as android.appwidget.AppWidgetManager
        mgr.isRequestPinAppWidgetSupported
    }.getOrDefault(false)

    /** Ask the launcher to place the stats widget. */
    @SuppressLint("NewApi")
    fun pinWidget(ctx: Context) {
        val ok = runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@runCatching false
            val mgr = ctx.getSystemService(Context.APPWIDGET_SERVICE) as android.appwidget.AppWidgetManager
            if (!mgr.isRequestPinAppWidgetSupported) return@runCatching false
            val provider = android.content.ComponentName(ctx, ZMasteryWidget::class.java)
            // A callback so we can confirm success to the user.
            val callback = android.app.PendingIntent.getBroadcast(
                ctx, 0,
                Intent(ctx, ZMasteryWidget::class.java).setAction(ZMasteryWidget.ACTION_REFRESH),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            mgr.requestPinAppWidget(provider, null, callback)
            true
        }.getOrDefault(false)

        Toast.makeText(
            ctx,
            if (ok) "أكّد الإضافة من نافذة المشغّل…"
            else "اضغط مطولاً على الشاشة الرئيسية ← الأدوات (Widgets) ← Z-Mastery",
            Toast.LENGTH_LONG,
        ).show()
    }
}
