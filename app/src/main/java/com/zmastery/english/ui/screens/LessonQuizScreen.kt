package com.zmastery.english.ui.screens

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.data.QuizItem
import com.zmastery.english.data.QuizType
import com.zmastery.english.ui.components.SoftCard
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel

/**
 * Dedicated lesson-quiz screen. Reached by tapping the "اختبار الدرس" button
 * inside a lesson. Presents all quiz items interactively with feedback.
 */
@Composable
fun LessonQuizScreen(vm: AppViewModel, lessonId: Int, onBack: () -> Unit) {
    val lesson = vm.lessons.firstOrNull { it.id == lessonId }
    val course = vm.courses.firstOrNull { it.id == lesson?.courseId }
    val accent = Color(course?.accent ?: 0xFFE07856)
    val quiz = lesson?.quiz ?: emptyList()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(ZCyanDeep, ZCyan))).padding(20.dp)
            ) {
                Column {
                    Text("اختبار الدرس", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(4.dp))
                    Text(lesson?.title ?: "", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("${quiz.size} سؤال", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                }
            }
        }

        if (quiz.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 50.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.Quiz, null, tint = ZTextMuted, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("لا يوجد اختبار لهذا الدرس", color = ZTextSecondary, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            items(quiz.size, key = { it }) { i ->
                QuizCard(i + 1, quiz[i], accent)
            }
        }

        item {
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
            ) {
                Icon(Icons.Filled.ArrowForward, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("العودة للدرس", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        item { Spacer(Modifier.height(60.dp)) }
    }
}

@Composable
private fun QuizCard(number: Int, q: QuizItem, accent: Color) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Text("$number", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text(q.question, color = ZTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
            }
            // audio_quiz: the learner must hear the word before choosing an answer.
            if (q.audioText.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    com.zmastery.english.audio.AudioButton(
                        text = q.audioText,
                        audioKey = "quiz_audio_${q.audioText.hashCode()}",
                        accent = accent, size = 40.dp, iconSize = 20.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("استمع أولاً ثم اختر ما سمعتَه", color = ZTextSecondary, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            when (q.type) {
                QuizType.MULTIPLE_CHOICE -> ChoiceQuiz(q.options, q.answer, q.explanationAr)
                QuizType.TRUE_FALSE -> ChoiceQuiz(listOf("True", "False"), q.answer, q.explanationAr)
                QuizType.WRITTEN -> WrittenQuiz(q.answer, q.explanationAr)
            }
        }
    }
}

@Composable
private fun ChoiceQuiz(options: List<String>, answer: String, explanation: String) {
    var selected by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            val answered = selected != null
            val isCorrect = opt.equals(answer, ignoreCase = true)
            val isPicked = opt == selected
            val bg by animateColorAsState(
                when {
                    !answered -> ZSurfaceVariant
                    isCorrect -> ZEmerald.copy(alpha = 0.18f)
                    isPicked -> ZRose.copy(alpha = 0.18f)
                    else -> ZSurfaceVariant
                }, label = "bg"
            )
            val border = when {
                !answered -> ZBorder
                isCorrect -> ZEmerald
                isPicked -> ZRose
                else -> ZBorder
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg)
                    .border(1.5.dp, border, RoundedCornerShape(12.dp))
                    .clickable(enabled = !answered) { selected = opt }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(opt, color = ZTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (answered && isCorrect) Icon(Icons.Filled.CheckCircle, null, tint = ZEmerald, modifier = Modifier.size(20.dp))
                else if (answered && isPicked) Icon(Icons.Filled.Cancel, null, tint = ZRose, modifier = Modifier.size(20.dp))
            }
        }
        if (selected != null && explanation.isNotBlank()) {
            ExplanationBox(explanation, selected!!.equals(answer, ignoreCase = true))
        }
    }
}

@Composable
private fun WrittenQuiz(answer: String, explanation: String) {
    var input by remember { mutableStateOf("") }
    var checked by remember { mutableStateOf(false) }
    val correct = input.trim().trim('.').equals(answer.trim().trim('.'), ignoreCase = true)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it; checked = false },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("اكتب إجابتك بالإنجليزية...", color = ZTextMuted) },
            singleLine = false,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = ZSurfaceVariant, unfocusedContainerColor = ZSurfaceVariant,
                focusedBorderColor = ZIndigo, unfocusedBorderColor = ZBorder,
                focusedTextColor = ZTextPrimary, unfocusedTextColor = ZTextPrimary,
            ),
        )
        Button(
            onClick = { checked = true },
            enabled = input.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ZIndigo, disabledContainerColor = ZBorder),
        ) { Text("تحقّق", fontWeight = FontWeight.Bold) }
        if (checked) {
            Surface(shape = RoundedCornerShape(12.dp), color = (if (correct) ZEmerald else ZRose).copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (correct) Icons.Filled.CheckCircle else Icons.Filled.Info, null, tint = if (correct) ZEmerald else ZRose, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (correct) "إجابة صحيحة!" else "الإجابة الصحيحة:", color = if (correct) ZEmerald else ZRose, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    if (!correct) {
                        Spacer(Modifier.height(4.dp))
                        Text(answer, color = ZTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (explanation.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(explanation, color = ZTextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExplanationBox(explanation: String, correct: Boolean) {
    Surface(shape = RoundedCornerShape(12.dp), color = (if (correct) ZEmerald else ZAmber).copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Info, null, tint = if (correct) ZEmerald else ZAmber, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(explanation, color = ZTextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}
