package com.zmastery.english.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.data.ExamQType
import com.zmastery.english.data.ExamQuestion
import com.zmastery.english.data.ExamSkill
import com.zmastery.english.data.ExamText
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One graded answer, kept so the review screen can explain every mistake. */
data class ExamAnswer(
    val question: ExamQuestion,
    val correct: Boolean,
    val given: String,
)

/**
 * The exam runner. Renders every question type, grades typed answers with
 * typo tolerance, auto-plays listening prompts, and shows an explanation after
 * each answer so the exam also teaches.
 */
@Composable
fun ExamRunner(
    vm: AppViewModel,
    onFinish: (List<ExamAnswer>, Long) -> Unit,
    onQuit: () -> Unit,
) {
    com.zmastery.english.ui.components.TrackStudyTime(vm, "exam")
    val questions = vm.examQuestions
    var index by remember { mutableStateOf(0) }
    var answered by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(-1) }
    var typed by remember { mutableStateOf("") }
    var orderPicked by remember { mutableStateOf<List<String>>(emptyList()) }
    var lastCorrect by remember { mutableStateOf(false) }
    val answers = remember { mutableStateListOf<ExamAnswer>() }
    val startMs = remember { System.currentTimeMillis() }
    var showQuit by remember { mutableStateOf(false) }

    val tts = com.zmastery.english.audio.LocalTts.current
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }

    if (questions.isEmpty()) return
    val q = questions[index.coerceAtMost(questions.size - 1)]

    // Auto-play listening questions the moment they appear.
    LaunchedEffect(q.key) {
        if (q.type == ExamQType.LISTENING_CHOICE || q.type == ExamQType.LISTENING_WRITTEN) {
            delay(300)
            tts?.speakInstant(q.audioText, "exam_${q.key}")
        }
    }

    fun grade(): Boolean = when {
        q.isOrdering -> ExamText.normalize(orderPicked.joinToString(" ")) == ExamText.normalize(q.correctText)
        q.isWritten -> q.matchesTyped(typed)
        else -> selected == q.correctIndex
    }

    fun canSubmit(): Boolean = when {
        q.isOrdering -> orderPicked.size == q.options.size
        q.isWritten -> typed.isNotBlank()
        else -> selected >= 0
    }

    fun submit() {
        val ok = grade()
        lastCorrect = ok
        answered = true
        vm.recordExamAnswer(q, ok)
        answers.add(
            ExamAnswer(
                question = q,
                correct = ok,
                given = when {
                    q.isOrdering -> orderPicked.joinToString(" ")
                    q.isWritten -> typed
                    else -> q.options.getOrNull(selected) ?: ""
                },
            )
        )
        // Replay the correct pronunciation as reinforcement.
        if (q.audioText.isNotBlank()) {
            scope.launch { delay(180); tts?.speakInstant(q.audioText, "exam_ans_${q.key}") }
        }
    }

    fun next() {
        if (index + 1 >= questions.size) {
            onFinish(answers.toList(), System.currentTimeMillis() - startMs)
        } else {
            index++
            answered = false; selected = -1; typed = ""; orderPicked = emptyList()
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ---------- header ----------
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showQuit = true }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.Close, "إنهاء", tint = ZTextSecondary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(vm.examTitle, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                    Text("${index + 1} من ${questions.size}", color = ZTextMuted, fontSize = 11.sp)
                }
                // Live score
                Surface(shape = RoundedCornerShape(50), color = ZEmerald.copy(alpha = 0.14f)) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, tint = ZEmerald, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${answers.count { it.correct }}",
                            color = ZEmerald, fontSize = 12.sp, fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val prog by animateFloatAsState(
                (index + if (answered) 1 else 0).toFloat() / questions.size, tween(320), label = "p",
            )
            LinearProgressIndicator(
                progress = { prog },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                color = ZIndigo, trackColor = ZBorder,
            )
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            // ---------- type + difficulty chips ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkillChip(q.skill)
                Spacer(Modifier.width(6.dp))
                DifficultyChip(q.difficulty)
                Spacer(Modifier.weight(1f))
                if (q.audioText.isNotBlank()) {
                    com.zmastery.english.audio.AudioButton(
                        text = q.audioText, audioKey = "exam_btn_${q.key}",
                        accent = ZIndigo, size = 38.dp, iconSize = 19.dp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---------- question card ----------
            Surface(
                shape = RoundedCornerShape(22.dp), color = ZCard, shadowElevation = 5.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        q.prompt, color = ZTextSecondary, fontSize = 13.sp,
                        textAlign = TextAlign.Center, lineHeight = 20.sp,
                    )
                    // Listening questions hide the word until answered.
                    if (q.hideSubject && !answered) {
                        Spacer(Modifier.height(16.dp))
                        ListenPad(
                            isPlaying = tts?.speakingKey == "exam_${q.key}",
                            onPlay = { scope.launch { tts?.speakInstant(q.audioText, "exam_${q.key}") } },
                        )
                    } else if (q.subject.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            q.subject, color = ZTextPrimary,
                            fontSize = if (q.subject.length > 42) 18.sp else 25.sp,
                            fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                            lineHeight = if (q.subject.length > 42) 27.sp else 33.sp,
                        )
                    }
                    if (q.subtitle.isNotBlank() && (!q.hideSubject || answered)) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            q.subtitle, color = ZCyanDeep, fontSize = 13.sp,
                            textAlign = TextAlign.Center, lineHeight = 20.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // ---------- answer area ----------
            when {
                q.isOrdering -> OrderArea(
                    tokens = q.options,
                    picked = orderPicked,
                    answered = answered,
                    correctText = q.correctText,
                    onPick = { t -> if (!answered) orderPicked = orderPicked + t },
                    onUndo = { if (!answered && orderPicked.isNotEmpty()) orderPicked = orderPicked.dropLast(1) },
                    onClear = { if (!answered) orderPicked = emptyList() },
                )
                q.isWritten -> WrittenArea(
                    value = typed,
                    onValue = { typed = it },
                    answered = answered,
                    correct = lastCorrect,
                    expected = q.correctText,
                    focus = focus,
                    onSubmit = { if (canSubmit() && !answered) submit() },
                )
                else -> q.options.forEachIndexed { i, opt ->
                    OptionRow(
                        text = opt,
                        index = i,
                        selected = selected == i,
                        answered = answered,
                        isCorrect = i == q.correctIndex,
                        onClick = { if (!answered) selected = i },
                    )
                }
            }

            // ---------- feedback ----------
            AnimatedVisibility(
                visible = answered,
                enter = fadeIn(tween(220)) + expandVertically(tween(220)),
                exit = fadeOut() + shrinkVertically(),
            ) {
                FeedbackCard(q, lastCorrect)
            }
            Spacer(Modifier.height(16.dp))
        }

        // ---------- action bar ----------
        Surface(color = ZSurface, shadowElevation = 10.dp) {
            Column(Modifier.padding(16.dp)) {
                Button(
                    onClick = { if (!answered) submit() else next() },
                    enabled = answered || canSubmit(),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (answered) ZIndigo else ZEmerald,
                        disabledContainerColor = ZBorder,
                    ),
                ) {
                    Text(
                        when {
                            !answered -> "تأكيد الإجابة"
                            index + 1 >= questions.size -> "عرض النتيجة"
                            else -> "السؤال التالي"
                        },
                        fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    )
                    if (answered) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.NavigateBefore, null)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (q.isWritten) "لا تقلق من خطأ إملائي بسيط — التصحيح متسامح"
                    else "اختر إجابة ثم أكّد",
                    color = ZTextMuted, fontSize = 10.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showQuit) {
        AlertDialog(
            onDismissRequest = { showQuit = false },
            containerColor = ZCard,
            title = { Text("إنهاء الاختبار؟", color = ZTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "أجبت على ${answers.size} من ${questions.size} سؤالاً. " +
                        if (answers.isEmpty()) "لن يتم تسجيل أي نتيجة." else "سيتم تسجيل ما أجبت عليه.",
                    color = ZTextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showQuit = false
                    if (answers.isEmpty()) onQuit()
                    else onFinish(answers.toList(), System.currentTimeMillis() - startMs)
                }) { Text("إنهاء", color = ZRose, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showQuit = false }) { Text("متابعة", color = ZIndigo) }
            },
        )
    }
}

/* ─────────────────────────── pieces ─────────────────────────── */

@Composable
private fun SkillChip(skill: ExamSkill) {
    val c = when (skill) {
        ExamSkill.VOCAB -> ZIndigo
        ExamSkill.SPELLING -> ZPurple
        ExamSkill.LISTENING -> ZCyanDeep
        ExamSkill.GRAMMAR -> ZAmber
        ExamSkill.CONVERSATION -> ZEmerald
        ExamSkill.READING -> ZRose
    }
    Surface(shape = RoundedCornerShape(50), color = c.copy(alpha = 0.14f)) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(skill.emoji, fontSize = 11.sp)
            Spacer(Modifier.width(4.dp))
            Text(skill.label, color = c, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DifficultyChip(level: Int) {
    val (c, label) = when (level) {
        3 -> ZRose to "صعب"
        2 -> ZAmber to "متوسط"
        else -> ZEmerald to "سهل"
    }
    Surface(shape = RoundedCornerShape(50), color = c.copy(alpha = 0.12f)) {
        Row(
            Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(level) {
                Box(Modifier.size(5.dp).clip(RoundedCornerShape(3.dp)).background(c))
                Spacer(Modifier.width(2.dp))
            }
            Spacer(Modifier.width(3.dp))
            Text(label, color = c, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Big tap-to-listen pad used by "ماذا سمعت؟" questions. */
@Composable
private fun ListenPad(isPlaying: Boolean, onPlay: () -> Unit) {
    val scale by animateFloatAsState(if (isPlaying) 1.07f else 1f, tween(320), label = "s")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(104.dp).scale(scale),
            shape = RoundedCornerShape(52.dp),
            color = Color.Transparent,
            onClick = onPlay,
        ) {
            Box(
                Modifier.background(Brush.linearGradient(listOf(ZCyanDeep, ZCyan))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.GraphicEq else Icons.Filled.Hearing,
                    "استمع", tint = Color.White, modifier = Modifier.size(50.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (isPlaying) "يُنطق الآن…" else "اضغط للاستماع مرة أخرى",
            color = ZTextMuted, fontSize = 11.sp,
        )
    }
}

@Composable
private fun OptionRow(
    text: String,
    index: Int,
    selected: Boolean,
    answered: Boolean,
    isCorrect: Boolean,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        when {
            !answered && selected -> ZIndigo.copy(alpha = 0.12f)
            !answered -> ZCard
            isCorrect -> ZEmerald.copy(alpha = 0.18f)
            selected -> ZRose.copy(alpha = 0.18f)
            else -> ZCard
        }, tween(220), label = "bg",
    )
    val border = when {
        !answered && selected -> ZIndigo
        answered && isCorrect -> ZEmerald
        answered && selected -> ZRose
        else -> ZBorder
    }
    val letter = ('A' + index).toString()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bg,
        modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp)
            .border(1.5.dp, border, RoundedCornerShape(16.dp)),
        onClick = onClick,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(9.dp))
                    .background(border.copy(alpha = if (answered || selected) 0.20f else 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    letter,
                    color = if (answered || selected) border else ZTextMuted,
                    fontSize = 12.sp, fontWeight = FontWeight.Black,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text, color = ZTextPrimary, fontSize = 15.sp,
                fontWeight = FontWeight.Medium, lineHeight = 22.sp,
                modifier = Modifier.weight(1f),
            )
            if (answered && isCorrect) {
                Icon(Icons.Filled.CheckCircle, null, tint = ZEmerald, modifier = Modifier.size(20.dp))
            } else if (answered && selected) {
                Icon(Icons.Filled.Cancel, null, tint = ZRose, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun WrittenArea(
    value: String,
    onValue: (String) -> Unit,
    answered: Boolean,
    correct: Boolean,
    expected: String,
    focus: FocusRequester,
    onSubmit: () -> Unit,
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = { if (!answered) onValue(it) },
            enabled = !answered,
            placeholder = { Text("اكتب إجابتك بالإنجليزية…", color = ZTextMuted, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Filled.Edit, null, tint = ZTextSecondary) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = ZCard, unfocusedContainerColor = ZCard,
                disabledContainerColor = ZCard,
                focusedBorderColor = when {
                    !answered -> ZIndigo
                    correct -> ZEmerald
                    else -> ZRose
                },
                unfocusedBorderColor = when {
                    !answered -> ZBorder
                    correct -> ZEmerald
                    else -> ZRose
                },
                disabledBorderColor = if (correct) ZEmerald else ZRose,
                focusedTextColor = ZTextPrimary, unfocusedTextColor = ZTextPrimary,
                disabledTextColor = ZTextPrimary,
            ),
        )
        if (answered && !correct) {
            Spacer(Modifier.height(10.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = ZEmerald.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lightbulb, null, tint = ZEmerald, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("الإجابة الصحيحة", color = ZTextSecondary, fontSize = 10.sp)
                        Text(expected, color = ZEmerald, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

/** Tap tokens in order to build the sentence. */
@Composable
private fun OrderArea(
    tokens: List<String>,
    picked: List<String>,
    answered: Boolean,
    correctText: String,
    onPick: (String) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
) {
    // Remaining pool = tokens minus what has been used (respecting duplicates).
    val remaining = remember(tokens, picked) {
        val pool = tokens.toMutableList()
        picked.forEach { pool.remove(it) }
        pool.toList()
    }
    val ok = ExamText.normalize(picked.joinToString(" ")) == ExamText.normalize(correctText)

    Column {
        // Answer line
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ZCard,
            modifier = Modifier.fillMaxWidth().heightIn(min = 74.dp)
                .border(
                    1.5.dp,
                    when { !answered -> ZBorder; ok -> ZEmerald; else -> ZRose },
                    RoundedCornerShape(16.dp),
                ),
        ) {
            Column(Modifier.padding(12.dp)) {
                if (picked.isEmpty()) {
                    Text("اضغط الكلمات بالترتيب الصحيح…", color = ZTextMuted, fontSize = 13.sp)
                } else {
                    FlowRowSimple(picked) { t ->
                        Surface(shape = RoundedCornerShape(10.dp), color = ZIndigo.copy(alpha = 0.14f)) {
                            Text(
                                t, color = ZIndigo, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
        if (!answered) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onUndo, enabled = picked.isNotEmpty()) {
                    Icon(Icons.Filled.Undo, null, modifier = Modifier.size(15.dp), tint = ZTextSecondary)
                    Spacer(Modifier.width(4.dp))
                    Text("رجوع", color = ZTextSecondary, fontSize = 12.sp)
                }
                TextButton(onClick = onClear, enabled = picked.isNotEmpty()) {
                    Icon(Icons.Filled.Clear, null, modifier = Modifier.size(15.dp), tint = ZRose)
                    Spacer(Modifier.width(4.dp))
                    Text("تفريغ", color = ZRose, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            FlowRowSimple(remaining) { t ->
                Surface(
                    shape = RoundedCornerShape(12.dp), color = ZSurfaceVariant,
                    onClick = { onPick(t) },
                ) {
                    Text(
                        t, color = ZTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    )
                }
            }
        } else if (!ok) {
            Spacer(Modifier.height(10.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = ZEmerald.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("الترتيب الصحيح", color = ZTextSecondary, fontSize = 10.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(correctText, color = ZEmerald, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
                }
            }
        }
    }
}

/** Minimal wrapping row (avoids the experimental FlowRow API). */
@Composable
private fun <T> FlowRowSimple(items: List<T>, item: @Composable (T) -> Unit) {
    // Chunk into rows of 3 — predictable and stable for short tokens.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item(it) }
            }
        }
    }
}

@Composable
private fun FeedbackCard(q: ExamQuestion, correct: Boolean) {
    val c = if (correct) ZEmerald else ZRose
    Column {
        Spacer(Modifier.height(6.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = c.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (correct) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                        null, tint = c, modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (correct) "إجابة صحيحة" else "إجابة خاطئة",
                        color = c, fontWeight = FontWeight.Black, fontSize = 14.sp,
                    )
                    if (!correct && q.wordId > 0) {
                        Spacer(Modifier.weight(1f))
                        Surface(shape = RoundedCornerShape(50), color = ZAmber.copy(alpha = 0.18f)) {
                            Text(
                                "أُضيفت للمراجعة",
                                color = ZAmber, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                if (q.explanationAr.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(q.explanationAr, color = ZTextSecondary, fontSize = 13.sp, lineHeight = 21.sp)
                }
            }
        }
    }
}
