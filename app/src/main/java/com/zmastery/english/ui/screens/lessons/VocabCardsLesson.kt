package com.zmastery.english.ui.screens.lessons

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.data.*
import com.zmastery.english.ui.components.SoftCard
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel

/**
 * Lesson viewer for the VOCAB_CARDS style ("من الصفر" / "Bites").
 * Layout: hero → key sentences → flip vocab cards (with audio) → notes → quiz → complete.
 */
@Composable
fun VocabCardsLesson(
    vm: AppViewModel,
    lesson: Lesson,
    accent: Color,
    onComplete: () -> Unit,
    onGenerateMentalLink: () -> Unit,
    onOpenQuiz: () -> Unit = {},
) {
    val words = vm.vocab.filter { it.id in lesson.newWordIds }
    // Per-lesson reveal mode override; defaults to the global setting.
    var revealMode by remember(lesson.id) { mutableStateOf(vm.revealMode) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { LessonHero(lesson, accent) }

        if (words.isNotEmpty()) {
            item { RevealModeToggle(revealMode, accent) { revealMode = it } }
        }

        if (lesson.keySentences.isNotEmpty()) {
            item { SectionHeader(Icons.Filled.FormatQuote, "الجمل الأساسية", accent) }
            item {
                SoftCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(6.dp)) {
                        lesson.keySentences.forEachIndexed { i, s ->
                            KeySentenceRow(s, accent)
                            if (i != lesson.keySentences.lastIndex) Divider(color = ZBorder, modifier = Modifier.padding(horizontal = 12.dp))
                        }
                    }
                }
            }
        }

        if (words.isNotEmpty()) {
            item { SectionHeader(Icons.Filled.Style, "المفردات (${words.size})", accent) }
            items(words.size, key = { words[it].id }) { idx ->
                WordClassCard(words[idx], accent, revealMode)
            }
        }

        if (lesson.notes.isNotEmpty()) {
            item { SectionHeader(Icons.Filled.Lightbulb, "ملاحظات الدرس", ZAmber) }
            item {
                SoftCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        lesson.notes.forEachIndexed { i, note ->
                            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                                Box(
                                    Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(ZAmber.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) { Text("${i + 1}", color = ZAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                Spacer(Modifier.width(10.dp))
                                Text(note, color = ZTextPrimary, fontSize = 14.sp, lineHeight = 22.sp)
                            }
                        }
                    }
                }
            }
        }

        if (lesson.quiz.isNotEmpty()) {
            item { QuizLauncherCard(lesson.quiz.size, accent, onOpenQuiz) }
        }

        item {
            Button(
                onClick = onGenerateMentalLink,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ZSurfaceVariant, contentColor = ZTextPrimary),
            ) {
                Icon(Icons.Filled.AutoAwesome, null, tint = accent); Spacer(Modifier.width(8.dp))
                Text("توليد رابط ذهني (صورة)", fontWeight = FontWeight.Bold)
            }
        }

        item {
            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (lesson.isCompleted) ZEmerald else accent),
            ) {
                Icon(if (lesson.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.Check, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(if (lesson.isCompleted) "مكتمل ✓ (اضغط للتراجع)" else "إكمال الدرس", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun LessonHero(lesson: Lesson, accent: Color) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.72f))))
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.10f), radius = size.minDimension * 0.5f, center = androidx.compose.ui.geometry.Offset(size.width * 0.88f, size.height * 0.1f))
                drawCircle(Color.White.copy(alpha = 0.07f), radius = size.minDimension * 0.32f, center = androidx.compose.ui.geometry.Offset(size.width * 0.1f, size.height))
            }
            .padding(22.dp)
    ) {
        Column {
            Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.2f)) {
                Text("الدرس ${lesson.no}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(lesson.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, lineHeight = 30.sp)
            if (lesson.summaryAr.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(lesson.summaryAr, color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, lineHeight = 20.sp)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroPill(Icons.Filled.Style, "${lesson.newWordIds.size} كلمة")
                HeroPill(Icons.Filled.Quiz, "${lesson.quiz.size} سؤال")
            }
        }
    }
}

@Composable
private fun HeroPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.18f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(title, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
private fun KeySentenceRow(s: Sentence, accent: Color) {
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(s.en, color = ZTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
            Spacer(Modifier.height(4.dp))
            Text(s.ar, color = ZTextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
        }
        Spacer(Modifier.width(8.dp))
        com.zmastery.english.audio.AudioButton(text = s.en, audioKey = "ks_${s.en.hashCode()}", accent = accent, size = 38.dp, iconSize = 20.dp)
    }
}

/** Toggle at the top of the lesson to switch reveal behaviour. */
@Composable
private fun RevealModeToggle(mode: RevealMode, accent: Color, onChange: (RevealMode) -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth(), radius = 16.dp) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Visibility, null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("العرض:", color = ZTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).background(ZSurfaceVariant).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                RevealSeg("كامل", mode == RevealMode.FULL, accent) { onChange(RevealMode.FULL) }
                RevealSeg("الكلمة فقط", mode == RevealMode.WORD_ONLY, accent) { onChange(RevealMode.WORD_ONLY) }
            }
        }
    }
}

@Composable
private fun RevealSeg(label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (active) accent else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (active) Color.White else ZTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Vocab "class" card — word + translation + example sentence (EN+AR) together,
 * matching the reference layout (word top, gold translation, divider, 💡 example).
 * In WORD_ONLY reveal mode the details are hidden until tapped.
 */
@Composable
private fun WordClassCard(word: VocabWord, accent: Color, mode: RevealMode) {
    var revealed by remember(mode, word.id) { mutableStateOf(mode == RevealMode.FULL) }
    val clickable = mode == RevealMode.WORD_ONLY

    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (clickable) ({ revealed = !revealed }) else null,
    ) {
        Column(Modifier.padding(18.dp)) {
            // Header row: English word (start) + audio + translation (end)
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.zmastery.english.audio.AudioButton(
                    text = if (word.exampleEn.isNotBlank()) "${word.english}. ${word.exampleEn}" else word.english,
                    audioKey = "wc_${word.id}", accent = accent, size = 40.dp, iconSize = 20.dp,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(word.english, color = ZTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    if (word.phonetic.isNotBlank()) Text(word.phonetic, color = accent, fontSize = 12.sp)
                }
                // Translation (gold) — shown only when revealed
                if (revealed) {
                    Text(word.arabic, color = ZAmber, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }

            AnimatedContent(
                revealed,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                label = "reveal",
            ) { show ->
                if (show && word.exampleEn.isNotBlank()) {
                    Column {
                        Spacer(Modifier.height(14.dp))
                        Divider(color = accent.copy(alpha = 0.35f))
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Text("\uD83D\uDCA1", fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(word.exampleEn, color = ZTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
                                Spacer(Modifier.height(6.dp))
                                Text(word.exampleAr, color = ZTextSecondary, fontSize = 14.sp, lineHeight = 22.sp)
                            }
                        }
                        if (word.mentalImage.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Image, null, tint = accent, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(word.mentalImage, color = ZTextMuted, fontSize = 12.sp)
                            }
                        }
                    }
                } else if (!show) {
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.TouchApp, null, tint = ZTextMuted, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("اضغط لكشف المعنى والجملة", color = ZTextMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** A button that navigates to the dedicated lesson quiz (not shown inline). */
@Composable
private fun QuizLauncherCard(count: Int, accent: Color, onOpenQuiz: () -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenQuiz) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(ZCyanDeep, ZCyan))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Quiz, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("اختبار الدرس", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("$count سؤال · اضغط للبدء", color = ZTextSecondary, fontSize = 12.sp)
            }
            Icon(Icons.Filled.ChevronLeft, null, tint = accent)
        }
    }
}
