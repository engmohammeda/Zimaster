package com.zmastery.english.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * مخزن العبارات الديناميكي — عبارة واحدة يومياً، عشوائية لكل جهاز،
 * تدمج مكتبة [Quotes] المدمجة مع عبارات السحابة (التي يضيفها المسؤول
 * وتتزامن عبر الأجهزة).
 *
 * التصميم:
 *  • تُختار عبارة عشوائية واحدة لكل يوم (مستقرّة طوال اليوم على نفس الجهاز)
 *    باستخدام بذرة عشوائية فريدة لكل جهاز + رقم اليوم → تنوّع حقيقي لكل متعلم.
 *  • تُخزَّن عبارات السحابة محلياً (SharedPreferences) فيعمل الودجت دون اتصال.
 *  • المصادر الثلاثة: مدمجة (64) + سحابية + المجموعة النهائية = اتحادها.
 */
object QuoteStore {

    /** عبارة سحابية (يضيفها المسؤول عبر Firestore وتتزامن). */
    @Serializable
    data class CloudQuote(
        val id: String = "",
        val text: String,
        val author: String = "",
        val active: Boolean = true,
    )

    private const val PREFS = "z_quotes"
    private const val KEY_SEED = "device_seed"
    private const val KEY_CLOUD = "cloud_quotes_json"
    private const val KEY_LAST_SYNC = "last_sync_millis"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** بذرة عشوائية ثابتة لكل جهاز (تُولَّد مرة واحدة). */
    private fun seed(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var s = prefs.getLong(KEY_SEED, 0L)
        if (s == 0L) {
            // XOR بين مصدرين زمنيين للحصول على تشتّت جيد.
            s = System.nanoTime() xor (System.currentTimeMillis() shl 32)
            prefs.edit().putLong(KEY_SEED, s).apply()
        }
        return s
    }

    /** المجموعة الكاملة المعروضة للمتعلم (المدمجة + السحابية النشطة). */
    fun pool(context: Context): List<Quotes.Quote> {
        val cloud = loadCloud(context).filter { it.active && it.text.isNotBlank() }
        val cloudDomain = cloud.map { Quotes.Quote(it.text, it.author.ifBlank { "Z-Mastery" }) }
        return Quotes.all + cloudDomain
    }

    /**
     * عبارة اليوم — عشوائية لكن مستقرّة طوال نفس اليوم على هذا الجهاز.
     * تتغيّر تلقائياً عند منتصف الليل بتوقيت الجهاز.
     */
    fun today(context: Context): Quotes.Quote {
        val list = pool(context)
        if (list.isEmpty()) {
            return Quotes.Quote("استمر في التعلم يومياً لتصل إلى الطلاقة.", "Z-Mastery")
        }
        val day = java.time.LocalDate.now().toEpochDay()
        val s = seed(context)
        // floorMod يضمن مؤشّراً غير سالب رغم أن (day xor s) قد يكون سالباً.
        val idx = Math.floorMod((day xor s), list.size.toLong()).toInt()
        return list[idx]
    }

    /** عدد عبارات السحابة المخزَّنة محلياً (للواجهة). */
    fun cloudCount(context: Context): Int = loadCloud(context).size

    /** آخر مزامنة ناجحة (لعرض «آخر تحديث» في الواجهة). */
    fun lastSyncMillis(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_SYNC, 0L)

    /** حفظ عبارات السحابة محلياً بعد كل مزامنة. */
    fun saveCloud(context: Context, quotes: List<CloudQuote>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_CLOUD, json.encodeToString(quotes))
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .apply()
    }

    /** قراءة عبارات السحابة المخزَّنة (قائمة فارغة عند أول تشغيل). */
    fun loadCloud(context: Context): List<CloudQuote> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CLOUD, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<CloudQuote>>(raw) }.getOrDefault(emptyList())
    }
}
