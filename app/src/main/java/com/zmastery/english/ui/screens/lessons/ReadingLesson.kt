package com.zmastery.english.ui.screens.lessons

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.audio.AudioButton
import com.zmastery.english.data.Lesson
import com.zmastery.english.data.LessonStyle
import com.zmastery.english.data.Sentence
import com.zmastery.english.data.VocabWord
import com.zmastery.english.ui.components.SoftCard
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel

/**
 * Lesson viewer for reading-style courses:
 * READING_TEXT ("القراءة", "I Know", "Book Worm"), CULTURE ("شفرة أمريكا", "الغرب"),
 * STORY ("Story"), NEWS ("الأخبار"), THINKING ("التفكير").
 *
 * Layout: themed hero → reading mode toggle (segments / full text) →
 * bilingual passage with per-sentence audio & tap-to-reveal translation →
 * global vocabulary cards → notes → quiz → complete.
 */
@Composable
fun ReadingLesson(
    vm: AppViewModel,
    lesson: Lesson,
    accent: Color,
    style: LessonStyle,
    onComplete: () -> Unit,
    onGenerateMentalLink: () -> Unit,
    onOpenQuiz: () -> Unit = {},
) {
    val words = vm.vocab.filter { it.id in lesson.newWordIds }
    val segments = lesson.segments.ifEmpty {
        if (lesson.fullTextEn.isNotBlank()) splitToSentences(lesson.fullTextEn, lesson.fullTextAr) else emptyList()
    }
    val fullEn = lesson.fullTextEn.ifBlank { lesson.readingEn }
    val fullAr = lesson.fullTextAr.ifBlank { lesson.readingAr }

    // 0 = segmented (interactive), 1 = continuous full text
    var mode by remember(lesson.id) { mutableStateOf(0) }
    var showAllAr by remember(lesson.id) { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { ReadingHero(lesson, accent, style, segments.size, words.size, fullEn) }

        // Reading controls
        if (segments.isNotEmpty() || fullEn.isNotBlank()) {
            item {
                ReadingControls(
                    accent = accent,
                    mode = mode,
                    onMode = { mode = it },
                    showAllAr = showAllAr,
                    onToggleAr = { showAllAr = !showAllAr },
                    hasSegments = segments.isNotEmpty(),
                )
            }
        }

        // Passage
        if (mode == 0 && segments.isNotEmpty()) {
            item { SectionHeader(Icons.Filled.MenuBook, "النص التفاعلي", accent) }
            itemsIndexed_segments(segments, accent, showAllAr)
        } else if (fullEn.isNotBlank()) {
            item { SectionHeader(Icons.Filled.Article, "النص الكامل", accent) }
            item { FullTextCard(fullEn, fullAr, accent, showAllAr, vm, lesson.id, lesson.audioReady) }
        }

        // Global vocabulary
        if (words.isNotEmpty()) {
            item { SectionHeader(Icons.Filled.Style, "مفردات الدرس (${words.size})", accent) }
            items_words(words, accent)
        }

        // Notes
        if (lesson.notes.isNotEmpty()) {
            item { SectionHeader(Icons.Filled.Lightbulb, "ملاحظات الدرس", ZAmber) }
            item {
                SoftCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        lesson.notes.forEach { note ->
                            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                                Box(Modifier.padding(top = 6.dp).size(7.dp).clip(RoundedCornerShape(4.dp)).background(ZAmber))
                                Spacer(Modifier.width(10.dp))
                                Text(note, color = ZTextPrimary, fontSize = 14.sp, lineHeight = 23.sp)
                            }
                        }
                    }
                }
            }
        }

        // Quiz launcher
        if (lesson.quiz.isNotEmpty()) {
            item {
                SoftCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenQuiz) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.7f)))), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Quiz, null, tint = Color.White)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("اختبار الفهم", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("${lesson.quiz.size} أسئلة على النص", color = ZTextSecondary, fontSize = 12.sp)
                        }
                        Icon(Icons.Filled.ChevronLeft, null, tint = ZTextMuted)
                    }
                }
            }
        }

        // Mental link
        item {
            SoftCard(modifier = Modifier.fillMaxWidth(), onClick = onGenerateMentalLink) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(ZPurple, ZIndigo))), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = Color.White)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("توليد رابط ذهني", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("صورة توضيحية للقصة كاملة", color = ZTextSecondary, fontSize = 12.sp)
                    }
                    Icon(Icons.Filled.ContentCopy, null, tint = ZCyan)
                }
            }
        }

        // Complete
        item {
            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (lesson.isCompleted) ZEmerald else accent),
            ) {
                Icon(if (lesson.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.Check, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(if (lesson.isCompleted) "تم الإكمال ✓ (اضغط للتراجع)" else "إكمال الدرس", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

/* ------------------------------------------------------------------ */
/* Hero                                                                */
/* ------------------------------------------------------------------ */

@Composable
private fun ReadingHero(lesson: Lesson, accent: Color, style: LessonStyle, segCount: Int, wordCount: Int, fullEn: String) {
    val (badge, badgeIcon) = styleBadge(style)
    val words = fullEn.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    val minutes = (words / 130).coerceAtLeast(1)
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.72f))))
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.08f), radius = size.minDimension * 0.42f, center = Offset(size.width * 0.9f, size.height * 0.12f))
                drawCircle(Color.White.copy(alpha = 0.06f), radius = size.minDimension * 0.30f, center = Offset(size.width * 0.1f, size.height * 0.95f))
            }
            .padding(22.dp),
    ) {
        Column {
            Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.2f)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(badgeIcon, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(badge, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("الدرس ${lesson.no}", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(lesson.title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black, lineHeight = 30.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                HeroStat(Icons.Filled.Schedule, "$minutes دقيقة", "قراءة")
                HeroStat(Icons.Filled.Segment, "${segCount.coerceAtLeast(1)}", "مقطع")
                HeroStat(Icons.Filled.Style, "$wordCount", "كلمة")
            }
        }
    }
}

@Composable
private fun HeroStat(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Column {
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 9.sp)
        }
    }
}

/* ------------------------------------------------------------------ */
/* Controls                                                            */
/* ------------------------------------------------------------------ */

@Composable
private fun ReadingControls(
    accent: Color,
    mode: Int,
    onMode: (Int) -> Unit,
    showAllAr: Boolean,
    onToggleAr: () -> Unit,
    hasSegments: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (hasSegments) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ZSurfaceVariant,
                modifier = Modifier.weight(1f),
            ) {
                Row(Modifier.padding(4.dp)) {
                    ModeTab("مقاطع", mode == 0, accent, Modifier.weight(1f)) { onMode(0) }
                    ModeTab("نص كامل", mode == 1, accent, Modifier.weight(1f)) { onMode(1) }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (showAllAr) accent else ZSurfaceVariant,
            onClick = onToggleAr,
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Translate, null, tint = if (showAllAr) Color.White else accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (showAllAr) "إخفاء" else "الترجمة", color = if (showAllAr) Color.White else ZTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) accent else Color.Transparent, tween(200), label = "tab")
    Surface(shape = RoundedCornerShape(11.dp), color = bg, modifier = modifier, onClick = onClick) {
        Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) Color.White else ZTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/* ------------------------------------------------------------------ */
/* Segmented passage                                                   */
/* ------------------------------------------------------------------ */

private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexed_segments(
    segments: List<Sentence>,
    accent: Color,
    showAllAr: Boolean,
) {
    items(count = segments.size, key = { "seg_$it" }) { i ->
        SegmentRow(segments[i], i + 1, accent, showAllAr)
    }
}

@Composable
private fun SegmentRow(seg: Sentence, index: Int, accent: Color, forceAr: Boolean) {
    var revealed by remember { mutableStateOf(false) }
    val show = revealed || forceAr
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(26.dp).clip(RoundedCornerShape(9.dp)).background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Text("$index", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(seg.en, color = ZTextPrimary, fontSize = 16.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
                AnimatedVisibility(show) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        Text(seg.ar, color = accent, fontSize = 14.sp, lineHeight = 23.sp)
                    }
                }
                if (!forceAr && seg.ar.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (revealed) "إخفاء الترجمة" else "اضغط لإظهار الترجمة",
                        color = ZTextMuted, fontSize = 11.sp,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.Transparent),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AudioButton(text = seg.en, audioKey = "seg_${index}_${seg.en.hashCode()}", accent = accent, size = 38.dp, iconSize = 18.dp)
                if (seg.ar.isNotBlank() && !forceAr) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = accent.copy(alpha = 0.10f),
                        modifier = Modifier.size(38.dp),
                        onClick = { revealed = !revealed },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, tint = accent, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Full text                                                           */
/* ------------------------------------------------------------------ */

@Composable
private fun FullTextCard(en: String, ar: String, accent: Color, showAr: Boolean, vm: AppViewModel, lessonId: Int, audioReady: Boolean) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AudioButton(text = en, audioKey = "fulltext_${en.hashCode()}", accent = accent, size = 44.dp, iconSize = 22.dp)
                Spacer(Modifier.width(12.dp))
                Text("استمع للنص كاملاً", color = ZTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                // Generate a natural AI narration for the whole lesson text
                // (Gemini), permanently cached — sounds far more natural than
                // the local device TTS used by the button above.
                if (audioReady) {
                    Surface(shape = RoundedCornerShape(8.dp), color = ZEmerald.copy(alpha = 0.15f)) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = ZEmerald, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("صوت AI جاهز", color = ZEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    TextButton(onClick = { vm.generateLessonAudio(lessonId) }, enabled = !vm.isGeneratingAudio) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = ZPurple, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("صوت طبيعي AI", color = ZPurple, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(en, color = ZTextPrimary, fontSize = 17.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium)
            AnimatedVisibility(showAr && ar.isNotBlank()) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    Divider(color = ZBorder)
                    Spacer(Modifier.height(14.dp))
                    Text(ar, color = accent, fontSize = 15.sp, lineHeight = 28.sp)
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Vocabulary cards                                                    */
/* ------------------------------------------------------------------ */

private fun androidx.compose.foundation.lazy.LazyListScope.items_words(words: List<VocabWord>, accent: Color) {
    items(count = words.size, key = { "w_${words[it].id}" }) { i ->
        ReadingWordCard(words[i], accent)
    }
}

@Composable
private fun ReadingWordCard(word: VocabWord, accent: Color) {
    var expanded by remember { mutableStateOf(false) }
    SoftCard(modifier = Modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AudioButton(
                    text = if (word.exampleEn.isNotBlank()) "${word.english}. ${word.exampleEn}" else word.english,
                    audioKey = "rw_${word.id}", accent = accent, size = 40.dp, iconSize = 19.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(word.english, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (word.phonetic.isNotBlank()) {
                            Spacer(Modifier.width(6.dp))
                            Text(word.phonetic, color = accent, fontSize = 11.sp)
                        }
                    }
                    Text(word.arabic, color = ZTextSecondary, fontSize = 13.sp)
                }
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = ZTextMuted)
            }
            AnimatedVisibility(expanded && word.exampleEn.isNotBlank()) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Divider(color = ZBorder)
                    Spacer(Modifier.height(10.dp))
                    Text(word.exampleEn, color = ZTextPrimary, fontSize = 14.sp, lineHeight = 22.sp)
                    if (word.exampleAr.isNotBlank()) {
                        Text(word.exampleAr, color = ZTextMuted, fontSize = 12.sp, lineHeight = 20.sp)
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Shared helpers                                                      */
/* ------------------------------------------------------------------ */

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(title, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

private fun styleBadge(style: LessonStyle): Pair<String, androidx.compose.ui.graphics.vector.ImageVector> = when (style) {
    LessonStyle.CULTURE -> "ثقافة" to Icons.Filled.Public
    LessonStyle.STORY -> "قصة" to Icons.Filled.AutoStories
    LessonStyle.NEWS -> "خبر" to Icons.Filled.Newspaper
    LessonStyle.THINKING -> "تفكير" to Icons.Filled.Psychology
    else -> "قراءة" to Icons.Filled.MenuBook
}

/** Fallback: split a full passage into sentence segments, pairing EN/AR by order. */
private fun splitToSentences(en: String, ar: String): List<Sentence> {
    val enParts = en.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotBlank() }
    val arParts = ar.split(Regex("(?<=[.!؟])\\s+")).map { it.trim() }.filter { it.isNotBlank() }
    return enParts.mapIndexed { i, e -> Sentence(e, arParts.getOrElse(i) { "" }) }
}
