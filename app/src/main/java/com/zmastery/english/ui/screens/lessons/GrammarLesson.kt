package com.zmastery.english.ui.screens.lessons

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.data.Lesson
import com.zmastery.english.data.Sentence
import com.zmastery.english.data.VocabWord
import com.zmastery.english.ui.components.SoftCard
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel

/**
 * Lesson viewer for the GRAMMAR_RULES style ("القواعد").
 * Layout mirrors the grammar JSON:
 *   hero → explanation_ar (the rule) → logic_ar (why) →
 *   examples[] (en/ar with audio) → global_vocabulary → notes → quiz → complete.
 */
@Composable
fun GrammarLesson(
    vm: AppViewModel,
    lesson: Lesson,
    accent: Color,
    onComplete: () -> Unit,
    onGenerateMentalLink: () -> Unit,
    onOpenQuiz: () -> Unit = {},
) {
    val words = vm.vocab.filter { it.id in lesson.newWordIds }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { GrammarHero(lesson, accent, words.size) }

        // ---- The rule (explanation_ar) ----
        if (lesson.explanationAr.isNotBlank()) {
            item { SectionHeaderG(Icons.Filled.MenuBook, "القاعدة", accent) }
            item {
                SoftCard(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier.size(6.dp).height(1.dp),
                        )
                        Text(
                            lesson.explanationAr,
                            color = ZTextPrimary,
                            fontSize = 16.sp,
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        // ---- The logic / why (logic_ar) ----
        if (lesson.logicAr.isNotBlank()) {
            item {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(accent.copy(alpha = 0.08f))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(accent.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Psychology, null, tint = accent, modifier = Modifier.size(20.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("لماذا؟ منطق القاعدة", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(lesson.logicAr, color = ZTextPrimary, fontSize = 14.sp, lineHeight = 24.sp)
                        }
                    }
                }
            }
        }

        // ---- Examples (en / ar with audio) ----
        if (lesson.examples.isNotEmpty()) {
            item { SectionHeaderG(Icons.Filled.FormatListBulleted, "أمثلة (${lesson.examples.size})", accent) }
            items(lesson.examples.size, key = { "ex_$it" }) { i ->
                ExampleCard(lesson.examples[i], accent)
            }
        }

        // ---- Global vocabulary ----
        if (words.isNotEmpty()) {
            item { SectionHeaderG(Icons.Filled.Style, "مفردات الدرس (${words.size})", accent) }
            items(words.size, key = { words[it].id }) { i ->
                GrammarWordCard(words[i], accent)
            }
        }

        // ---- Notes / exceptions ----
        if (lesson.notes.isNotEmpty()) {
            item { SectionHeaderG(Icons.Filled.Lightbulb, "ملاحظات واستثناءات", ZAmber) }
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

        // ---- Quiz launcher ----
        if (lesson.quiz.isNotEmpty()) {
            item { GrammarQuizCard(lesson.quiz.size, accent, onOpenQuiz) }
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
private fun GrammarHero(lesson: Lesson, accent: Color, wordCount: Int) {
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
                Row(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Rule, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("قاعدة · الدرس ${lesson.no}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(lesson.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, lineHeight = 30.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroPillG(Icons.Filled.FormatListBulleted, "${lesson.examples.size} مثال")
                if (wordCount > 0) HeroPillG(Icons.Filled.Style, "$wordCount كلمة")
                HeroPillG(Icons.Filled.Quiz, "${lesson.quiz.size} سؤال")
            }
        }
    }
}

@Composable
private fun HeroPillG(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.18f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SectionHeaderG(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(title, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

/** An example line: English (with audio) + Arabic gloss. */
@Composable
private fun ExampleCard(ex: Sentence, accent: Color) {
    SoftCard(modifier = Modifier.fillMaxWidth(), radius = 16.dp) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            com.zmastery.english.audio.AudioButton(
                text = ex.en, audioKey = "gex_${ex.en.hashCode()}", accent = accent, size = 40.dp, iconSize = 20.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ex.en, color = ZTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
                if (ex.ar.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(ex.ar, color = ZTextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun GrammarWordCard(word: VocabWord, accent: Color) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.zmastery.english.audio.AudioButton(
                    text = if (word.exampleEn.isNotBlank()) "${word.english}. ${word.exampleEn}" else word.english,
                    audioKey = "gw_${word.id}", accent = accent, size = 38.dp, iconSize = 18.dp,
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
private fun GrammarQuizCard(count: Int, accent: Color, onOpenQuiz: () -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenQuiz) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(ZCyanDeep, ZCyan))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Quiz, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("اختبار القاعدة", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("$count سؤال · اختيار وصح/خطأ وكتابة", color = ZTextSecondary, fontSize = 12.sp)
            }
            Icon(Icons.Filled.ChevronLeft, null, tint = accent)
        }
    }
}
