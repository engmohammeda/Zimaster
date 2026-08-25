package com.zmastery.english.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.data.*
import com.zmastery.english.ui.components.SoftCard
import com.zmastery.english.ui.theme.*

/**
 * Dedicated renderer for the Phonetics course lesson format.
 * Sections: focus sounds · minimal pairs · practice scripts · vocabulary ·
 * teacher notes · interactive quiz (audio + true/false).
 */
@Composable
fun PhoneticsLessonScreen(
    lesson: PhoneticsLesson,
    onComplete: () -> Unit = {},
    isCompleted: Boolean = false,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { PhHeader(lesson) }
        item { FocusSoundsSection(lesson.content.focusSounds) }
        if (lesson.content.minimalPairs.isNotEmpty()) item { MinimalPairsSection(lesson.content.minimalPairs) }
        if (lesson.content.practiceScripts.isNotEmpty()) item { PracticeScriptsSection(lesson.content.practiceScripts) }
        if (lesson.vocabulary.isNotEmpty()) item { VocabSection(lesson.vocabulary) }
        if (lesson.notes.isNotEmpty()) item { NotesSection(lesson.notes) }
        if (lesson.quiz.isNotEmpty()) item { QuizSection(lesson.quiz, onComplete) }
        // Persistent complete button — always available regardless of quiz.
        item {
            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isCompleted) ZEmerald else ZAmber),
            ) {
                Icon(if (isCompleted) Icons.Filled.CheckCircle else Icons.Filled.Check, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isCompleted) "تم الإكمال ✓ (اضغط للتراجع)" else "إكمال الدرس",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                )
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

@Composable
private fun PhHeader(lesson: PhoneticsLesson) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(ZAmber, Color(0xFFD98324))))
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.10f), radius = size.minDimension * 0.4f, center = androidx.compose.ui.geometry.Offset(size.width * 0.9f, size.height * 0.2f))
            }
            .padding(24.dp)
    ) {
        Column {
            Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.2f)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.GraphicEq, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("${lesson.metadata.courseNameAr} · الدرس ${lesson.metadata.lessonNo}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(lesson.metadata.title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black, lineHeight = 29.sp)
        }
    }
}

@Composable
private fun SectionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, content: @Composable ColumnScope.() -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = accent, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(title, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun FocusSoundsSection(sounds: List<PhSound>) {
    SectionCard("الأصوات المستهدفة", Icons.Filled.RecordVoiceOver, ZAmber) {
        sounds.forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                // Big phonetic symbol badge with a play affordance
                Box(
                    Modifier.size(62.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(ZAmber, Color(0xFFD98324)))),
                    contentAlignment = Alignment.Center,
                ) { Text(s.symbol, color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp) }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.description, color = ZTextSecondary, fontSize = 13.sp, lineHeight = 22.sp)
                }
            }
            if (i < sounds.size - 1) HorizontalDivider(color = ZBorder, modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

@Composable
private fun MinimalPairsSection(pairs: List<PhPair>) {
    SectionCard("الأزواج الصغرى (Minimal Pairs)", Icons.Filled.CompareArrows, ZCyan) {
        Text("قارن النطق بين كل كلمتين — الفرق في صوت واحد فقط", color = ZTextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        pairs.chunked(1).forEach { /* keep vertical list of pairs */ }
        pairs.forEach { p ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PairChip(p.word1, Modifier.weight(1f))
                Icon(Icons.Filled.SwapHoriz, null, tint = ZTextMuted, modifier = Modifier.size(18.dp))
                PairChip(p.word2, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PairChip(word: String, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(12.dp), color = ZSurfaceVariant, modifier = modifier) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(word, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.VolumeUp, null, tint = ZCyanDeep, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun PracticeScriptsSection(scripts: List<String>) {
    SectionCard("جُمل التدريب (Shadowing)", Icons.Filled.Repeat, ZEmerald) {
        scripts.forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(ZEmerald.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", color = ZEmerald, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Spacer(Modifier.width(12.dp))
                Text(s, color = ZTextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.VolumeUp, null, tint = ZEmerald, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun VocabSection(vocab: List<PhVocab>) {
    SectionCard("مفردات الدرس", Icons.Filled.Style, ZIndigo) {
        vocab.forEach { v ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(v.word, color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.VolumeUp, null, tint = ZIndigo, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.weight(1f))
                    Text(v.meaning, color = ZTextSecondary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                if (v.exampleEn.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(v.exampleEn, color = ZTextSecondary, fontSize = 13.sp)
                    Text(v.exampleAr, color = ZTextMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun NotesSection(notes: List<String>) {
    SectionCard("ملاحظات الأستاذ", Icons.Filled.Lightbulb, ZAmber) {
        notes.forEach { n ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Box(Modifier.padding(top = 8.dp).size(7.dp).clip(RoundedCornerShape(4.dp)).background(ZAmber))
                Spacer(Modifier.width(12.dp))
                Text(n, color = ZTextSecondary, fontSize = 13.sp, lineHeight = 23.sp)
            }
        }
    }
}

@Composable
private fun QuizSection(quiz: List<PhQuiz>, onComplete: () -> Unit) {
    var index by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<String?>(null) }
    var answered by remember { mutableStateOf(false) }
    var correct by remember { mutableStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    SectionCard("اختبار الدرس", Icons.Filled.Quiz, ZRose) {
        if (finished) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("نتيجتك", color = ZTextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text("$correct / ${quiz.size}", color = ZEmerald, fontWeight = FontWeight.Black, fontSize = 30.sp)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onComplete, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = ZAmber)) {
                    Icon(Icons.Filled.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text("إنهاء الدرس", fontWeight = FontWeight.Bold)
                }
            }
            return@SectionCard
        }

        val q = quiz[index]
        // progress
        LinearProgressIndicator(
            progress = { (index + if (answered) 1 else 0).toFloat() / quiz.size },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
            color = ZRose, trackColor = ZBorder,
        )
        Spacer(Modifier.height(12.dp))
        Text("سؤال ${index + 1} من ${quiz.size}", color = ZTextMuted, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))

        if (q.type == "audio_quiz") {
            // Audio prompt bubble
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Surface(shape = RoundedCornerShape(50), color = ZRose.copy(alpha = 0.12f), onClick = { }) {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.VolumeUp, null, tint = ZRose)
                        Spacer(Modifier.width(8.dp))
                        Text("استمع للكلمة", color = ZRose, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Text(q.question, color = ZTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))

        val opts = q.options ?: listOf("True", "False")
        val optLabels = if (q.options == null) listOf("صحيح ✓", "خطأ ✗") else opts
        opts.forEachIndexed { i, opt ->
            val isCorrect = opt == q.answer
            val bg by animateColorAsState(
                when {
                    !answered -> ZSurfaceVariant
                    isCorrect -> ZEmerald.copy(alpha = 0.18f)
                    opt == selected -> ZRose.copy(alpha = 0.18f)
                    else -> ZSurfaceVariant
                }, label = "bg"
            )
            val border = when {
                !answered && opt == selected -> ZAmber
                answered && isCorrect -> ZEmerald
                answered && opt == selected -> ZRose
                else -> ZBorder
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = bg,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).border(1.5.dp, border, RoundedCornerShape(16.dp)),
                onClick = { if (!answered) selected = opt },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(optLabels[i], color = ZTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    if (answered && isCorrect) Icon(Icons.Filled.CheckCircle, null, tint = ZEmerald)
                    else if (answered && opt == selected) Icon(Icons.Filled.Cancel, null, tint = ZRose)
                }
            }
        }

        AnimatedVisibility(answered) {
            Surface(shape = RoundedCornerShape(12.dp), color = ZCyan.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp)) {
                    Icon(Icons.Filled.Info, null, tint = ZCyanDeep, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(q.explanationAr, color = ZTextSecondary, fontSize = 13.sp, lineHeight = 21.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (!answered) {
                    answered = true
                    if (selected == q.answer) correct++
                } else {
                    if (index + 1 >= quiz.size) finished = true
                    else { index++; selected = null; answered = false }
                }
            },
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ZAmber, disabledContainerColor = ZBorder),
        ) {
            Text(
                if (!answered) "تأكيد" else if (index + 1 >= quiz.size) "عرض النتيجة" else "السؤال التالي",
                fontWeight = FontWeight.Bold, fontSize = 15.sp,
            )
        }
    }
}
