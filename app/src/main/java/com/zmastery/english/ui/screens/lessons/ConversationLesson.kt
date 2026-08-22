package com.zmastery.english.ui.screens.lessons

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.data.Dialogue
import com.zmastery.english.data.KeyExpression
import com.zmastery.english.data.Lesson
import com.zmastery.english.data.VocabWord
import com.zmastery.english.ui.components.SoftCard
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel

/**
 * Lesson viewer for the CONVERSATION style ("المحادثة").
 * Mirrors the conversation JSON:
 *   hero → dialogue[] (chat bubbles + audio) → key_expressions[] →
 *   global_vocabulary → lesson_notes → quiz → complete.
 */
@Composable
fun ConversationLesson(
    vm: AppViewModel,
    lesson: Lesson,
    accent: Color,
    onComplete: () -> Unit,
    onGenerateMentalLink: () -> Unit,
    onOpenQuiz: () -> Unit = {},
) {
    val words = vm.vocab.filter { it.id in lesson.newWordIds }
    // Determine the two speakers so we can align bubbles left/right consistently.
    val speakers = lesson.dialogues.map { it.speaker }.distinct()
    val firstSpeaker = speakers.firstOrNull()

    var showTranslations by remember { mutableStateOf(true) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { ConversationHero(lesson, accent, words.size) }

        // ---- Dialogue ----
        if (lesson.dialogues.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeaderC(Icons.Filled.Forum, "الحوار", accent)
                    Spacer(Modifier.weight(1f))
                    TranslationToggle(showTranslations) { showTranslations = !showTranslations }
                }
            }
            item { PlayAllCard(lesson.dialogues, accent, vm) }
            items(lesson.dialogues.size, key = { "dlg_$it" }) { i ->
                val d = lesson.dialogues[i]
                val isRight = d.speaker == firstSpeaker
                ChatBubble(d, isRight, accent, showTranslations, vm)
            }
        }

        // ---- Key expressions ----
        if (lesson.keyExpressions.isNotEmpty()) {
            item { SectionHeaderC(Icons.Filled.Bookmarks, "تعبيرات مهمة (${lesson.keyExpressions.size})", ZAmber) }
            items(lesson.keyExpressions.size, key = { "exp_$it" }) { i ->
                ExpressionCard(lesson.keyExpressions[i], accent)
            }
        }

        // ---- Global vocabulary ----
        if (words.isNotEmpty()) {
            item { SectionHeaderC(Icons.Filled.Style, "مفردات الدرس (${words.size})", accent) }
            items(words.size, key = { words[it].id }) { i ->
                ConvWordCard(words[i], accent)
            }
        }

        // ---- Notes ----
        if (lesson.notes.isNotEmpty()) {
            item { SectionHeaderC(Icons.Filled.Lightbulb, "ملاحظات الدرس", ZAmber) }
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

        // ---- Quiz ----
        if (lesson.quiz.isNotEmpty()) {
            item { ConvQuizCard(lesson.quiz.size, accent, onOpenQuiz) }
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
private fun ConversationHero(lesson: Lesson, accent: Color, wordCount: Int) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.72f))))
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.10f), radius = size.minDimension * 0.5f, center = Offset(size.width * 0.88f, size.height * 0.1f))
                drawCircle(Color.White.copy(alpha = 0.07f), radius = size.minDimension * 0.32f, center = Offset(size.width * 0.1f, size.height))
            }
            .padding(22.dp)
    ) {
        Column {
            Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.2f)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Forum, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("محادثة · الدرس ${lesson.no}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(lesson.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, lineHeight = 30.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroPillC(Icons.Filled.ChatBubble, "${lesson.dialogues.size} سطر")
                if (lesson.keyExpressions.isNotEmpty()) HeroPillC(Icons.Filled.Bookmarks, "${lesson.keyExpressions.size} تعبير")
                if (wordCount > 0) HeroPillC(Icons.Filled.Style, "$wordCount كلمة")
            }
        }
    }
}

@Composable
private fun HeroPillC(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.18f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SectionHeaderC(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(title, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
private fun TranslationToggle(showing: Boolean, onToggle: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (showing) ZCyanDeep.copy(alpha = 0.12f) else ZSurfaceVariant,
        onClick = onToggle,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (showing) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, null, tint = ZCyanDeep, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text(if (showing) "الترجمة ظاهرة" else "الترجمة مخفية", color = ZCyanDeep, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Play the whole dialogue as one narration. */
@Composable
private fun PlayAllCard(dialogue: List<Dialogue>, accent: Color, vm: AppViewModel) {
    val fullText = dialogue.joinToString(". ") { it.en }
    SoftCard(modifier = Modifier.fillMaxWidth(), radius = 16.dp) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            com.zmastery.english.audio.AudioButton(
                text = fullText, audioKey = "dlg_all_${fullText.hashCode()}", accent = accent, size = 44.dp, iconSize = 22.dp,
                // Listening to the whole dialogue counts as every turn.
                onPlayed = { vm.trackConversationTurn(dialogue.size) },
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("استمع للحوار كاملاً", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("تشغيل متتابع لكل الأسطر", color = ZTextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ChatBubble(d: Dialogue, isRight: Boolean, accent: Color, showTranslation: Boolean, vm: AppViewModel) {
    val bubbleColor = if (isRight) accent else ZSurfaceVariant
    val textColor = if (isRight) Color.White else ZTextPrimary
    val subColor = if (isRight) Color.White.copy(alpha = 0.85f) else ZTextSecondary

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isRight) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier.fillMaxWidth(0.86f),
            horizontalAlignment = if (isRight) Alignment.End else Alignment.Start,
        ) {
            Text(
                d.speaker,
                color = if (isRight) accent else ZTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp, topEnd = 18.dp,
                    bottomStart = if (isRight) 18.dp else 4.dp,
                    bottomEnd = if (isRight) 4.dp else 18.dp,
                ),
                color = bubbleColor,
                shadowElevation = 3.dp,
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!isRight) {
                        com.zmastery.english.audio.AudioButton(
                            text = d.en, audioKey = "dlg_${d.hashCode()}", accent = accent, size = 34.dp, iconSize = 17.dp,
                            onPlayed = { vm.trackConversationTurn() },
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(d.en, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp)
                        AnimatedVisibility(showTranslation && d.ar.isNotBlank()) {
                            Text(d.ar, color = subColor, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                    if (isRight) {
                        Spacer(Modifier.width(10.dp))
                        com.zmastery.english.audio.AudioButton(
                            text = d.en, audioKey = "dlg_${d.hashCode()}", accent = Color.White.copy(alpha = 0.25f), size = 34.dp, iconSize = 17.dp,
                            onPlayed = { vm.trackConversationTurn() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpressionCard(exp: KeyExpression, accent: Color) {
    SoftCard(modifier = Modifier.fillMaxWidth(), radius = 16.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.zmastery.english.audio.AudioButton(
                    text = exp.expressionEn, audioKey = "exp_${exp.expressionEn.hashCode()}", accent = ZAmber, size = 38.dp, iconSize = 18.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(exp.expressionEn, color = ZTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Text(exp.expressionAr, color = ZAmber, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (exp.usageAr.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ZAmber.copy(alpha = 0.08f)).padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Info, null, tint = ZAmber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(exp.usageAr, color = ZTextSecondary, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConvWordCard(word: VocabWord, accent: Color) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.zmastery.english.audio.AudioButton(
                    text = if (word.exampleEn.isNotBlank()) "${word.english}. ${word.exampleEn}" else word.english,
                    audioKey = "cw_${word.id}", accent = accent, size = 38.dp, iconSize = 18.dp,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(word.english, color = ZTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    if (word.phonetic.isNotBlank()) Text(word.phonetic, color = accent, fontSize = 11.sp)
                }
                Text(word.arabic, color = ZAmber, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            if (word.exampleEn.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Divider(color = ZBorder)
                Spacer(Modifier.height(10.dp))
                Text(word.exampleEn, color = ZTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (word.exampleAr.isNotBlank()) {
                    Text(word.exampleAr, color = ZTextSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ConvQuizCard(count: Int, accent: Color, onOpenQuiz: () -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenQuiz) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(ZCyanDeep, ZCyan))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Quiz, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("اختبار المحادثة", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("$count سؤال · فهم واستيعاب", color = ZTextSecondary, fontSize = 12.sp)
            }
            Icon(Icons.Filled.ChevronLeft, null, tint = accent)
        }
    }
}
