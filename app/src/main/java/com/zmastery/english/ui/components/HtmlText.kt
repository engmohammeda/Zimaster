package com.zmastery.english.ui.components

import android.graphics.Typeface
import android.text.Html
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat

/**
 * عارض HTML أصلي للتقارير المولَّدة (مرآة الإدراك).
 *
 * يستخدم [AndroidView] مع [TextView] مهيّأ لقراءة [HtmlCompat.fromHtml]، لأن
 * TextView يدعم أصلاً وسوم <h3>/<p>/<strong>/<b>/<i>/<ul>/<li> وفقرات RTL
 * بجودة طباعية أعلى بكثير من إعادة بناء الوسوم يدوياً في Compose.
 *
 * إضافات ما بعد التحليل:
 *  • <h3>  → أكبر حجماً + عريض + ملوّن بلون الصندوق (accent)
 *  • <div class='highlight'> → خلفية خفيفة تبرز الإحصاءات
 *  • ضبط اتجاه النص للعربية (RTL) وتباعد أسطر مريح للعين
 *
 * لا WebView ولا مكتبات خارجية — خفيف وسريع ولا يستهلك ذاكرة تُذكر.
 */
@Composable
fun HtmlText(
    html: String,
    modifier: Modifier = Modifier,
    textColor: Color,
    accentColor: Color,
    highlightColor: Color,
    fontSizeSp: Float = 14f,
    lineSpacingMultiplier: Float = 1.42f,
) {
    val textArgb = textColor.toArgb()
    val accentArgb = accentColor.toArgb()
    val highlightArgb = highlightColor.toArgb()

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                textDirection = View.TEXT_DIRECTION_RTL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                setTextIsSelectable(true)
                includeFontPadding = false
            }
        },
        update = { tv ->
            tv.setTextColor(textArgb)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
            tv.setLineSpacing(0f, lineSpacingMultiplier)
            tv.text = styleHtml(html, accentArgb, highlightArgb)
        },
    )
}

/**
 * يحوّل HTML إلى [Spanned] منسّق.
 *
 * نعالج `<div class='highlight'>` يدوياً قبل التحليل (لأن TextView يتجاهل
 * الأصناف) بتحويلها إلى وسم علامة نستبدله لاحقاً بخلفية ملوّنة.
 */
private fun styleHtml(raw: String, accentArgb: Int, highlightArgb: Int): Spanned {
    // علامة فريدة نستبدلها بخلفية بعد التحليل.
    val marker = "\u2063" // invisible separator
    val prepared = raw
        .replace(
            Regex("(?is)<div[^>]*class=['\"]?highlight['\"]?[^>]*>(.*?)</div>"),
            "<p>$marker$1$marker</p>",
        )
        // TextView لا يعرف <h3> بأحجام مميّزة دائماً — نضمن العرض بوسم قوي.
        .replace(Regex("(?i)<h3>"), "<br/><b><big>")
        .replace(Regex("(?i)</h3>"), "</big></b><br/>")

    val spanned = HtmlCompat.fromHtml(prepared, HtmlCompat.FROM_HTML_MODE_COMPACT)
    val sb = SpannableStringBuilder(spanned)

    // 1) لوّن العناوين (كل ما كان عريضاً + كبيراً) بلون الصندوق.
    sb.getSpans(0, sb.length, RelativeSizeSpan::class.java).forEach { span ->
        val start = sb.getSpanStart(span)
        val end = sb.getSpanEnd(span)
        sb.setSpan(ForegroundColorSpan(accentArgb), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    // 2) استبدل علامات الإبراز بخلفية ملوّنة.
    while (true) {
        val open = sb.indexOf(marker)
        if (open < 0) break
        val close = sb.indexOf(marker, open + 1)
        if (close < 0) {
            sb.delete(open, open + 1)
            break
        }
        // احذف العلامة الثانية أولاً حتى لا تتغيّر المواضع.
        sb.delete(close, close + 1)
        sb.delete(open, open + 1)
        val end = (close - 1).coerceAtMost(sb.length)
        if (end > open) {
            sb.setSpan(BackgroundColorSpan(highlightArgb), open, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(StyleSpan(Typeface.BOLD), open, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    // 3) قلّم الأسطر الفارغة الزائدة في الطرفين.
    var s = 0
    var e = sb.length
    while (s < e && sb[s] == '\n') s++
    while (e > s && sb[e - 1] == '\n') e--
    return sb.subSequence(s, e) as? Spanned ?: sb
}
