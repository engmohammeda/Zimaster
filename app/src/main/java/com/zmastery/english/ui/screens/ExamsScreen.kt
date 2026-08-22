package com.zmastery.english.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.data.ExamBuilder
import com.zmastery.english.data.ExamMode
import com.zmastery.english.data.ExamQType
import com.zmastery.english.data.ExamRecord
import com.zmastery.english.data.ExamSkill
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel

/** Screens inside the exams section. */
private enum class ExamStage { HUB, PICK_LESSON, PICK_COURSE, RUNNING, RESULT, REVIEW }

/**
 * The exams section — a full assessment centre.
 *
 *  • HUB      : weakness dashboard, 9 exam modes, history + trend.
 *  • PICKERS  : choose a completed lesson or a course.
 *  • RUNNING  : the runner (ExamRunner).
 *  • RESULT   : score ring, per-skill breakdown, XP.
 *  • REVIEW   : every question with the right answer + explanation.
 */
@Composable
fun ExamsScreen(vm: AppViewModel) {
    var stage by remember { mutableStateOf(ExamStage.HUB) }
    var pendingMode by remember { mutableStateOf(ExamMode.SMART) }
    var answers by remember { mutableStateOf<List<ExamAnswer>>(emptyList()) }
    var duration by remember { mutableStateOf(0L) }
    var buildError by remember { mutableStateOf<String?>(null) }

    fun launch(mode: ExamMode, courseId: Int? = null, lessonId: Int? = null) {
        val n = vm.startExam(mode, vm.examQuestionCount, courseId, lessonId)
        if (n == 0) {
            buildError = "لا توجد مادة مدروسة كافية لهذا النوع بعد — أكمل درساً أو أضف كلمات للقاموس."
        } else {
            buildError = null
            stage = ExamStage.RUNNING
        }
    }

    when (stage) {
        ExamStage.HUB -> ExamHub(
            vm = vm,
            error = buildError,
            onDismissError = { buildError = null },
            onPick = { mode ->
                pendingMode = mode
                when (mode) {
                    ExamMode.LESSON -> stage = ExamStage.PICK_LESSON
                    ExamMode.COURSE -> stage = ExamStage.PICK_COURSE
                    else -> launch(mode)
                }
            },
            onOpenHistory = { rec -> /* history rows are informational */ },
        )
        ExamStage.PICK_LESSON -> LessonPicker(
            vm = vm,
            onBack = { stage = ExamStage.HUB },
            onPick = { lessonId -> launch(ExamMode.LESSON, lessonId = lessonId) },
        )
        ExamStage.PICK_COURSE -> CoursePicker(
            vm = vm,
            onBack = { stage = ExamStage.HUB },
            onPick = { courseId -> launch(ExamMode.COURSE, courseId = courseId) },
        )
        ExamStage.RUNNING -> ExamRunner(
            vm = vm,
            onFinish = { a, ms ->
                answers = a
                duration = ms
                val skillCorrect = a.filter { it.correct }.groupingBy { it.question.skill }.eachCount()
                val skillTotal = a.groupingBy { it.question.skill }.eachCount()
                vm.finishExam(a.count { it.correct }, a.size, ms, skillCorrect, skillTotal)
                vm.clearExam()
                stage = ExamStage.RESULT
            },
            onQuit = { vm.clearExam(); stage = ExamStage.HUB },
        )
        ExamStage.RESULT -> ExamResultScreen(
            vm = vm,
            answers = answers,
            durationMs = duration,
            onReview = { stage = ExamStage.REVIEW },
            onAgain = { launch(pendingMode) },
            onDone = { stage = ExamStage.HUB },
        )
        ExamStage.REVIEW -> ExamReviewScreen(
            answers = answers,
            onBack = { stage = ExamStage.RESULT },
        )
    }
}

/* ══════════════════════════════ HUB ══════════════════════════════ */

@Composable
private fun ExamHub(
    vm: AppViewModel,
    error: String?,
    onDismissError: () -> Unit,
    onPick: (ExamMode) -> Unit,
    onOpenHistory: (ExamRecord) -> Unit,
) {
    val weakest = vm.weakestWords
    val hasMaterial = vm.examableWords.size >= ExamBuilder.MIN_WORDS || vm.doneLessons.isNotEmpty()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ---------- readiness hero ----------
        Surface(shape = RoundedCornerShape(22.dp), color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.background(Brush.linearGradient(listOf(ZCyanDeep, ZIndigo))).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("مركز الاختبارات", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "اختبارات مبنية على ما أكملته فعلاً — وتستهدف نقاط ضعفك",
                            color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp, lineHeight = 19.sp,
                        )
                    }
                    if (vm.examHistory.isNotEmpty()) {
                        Spacer(Modifier.width(12.dp))
                        com.zmastery.english.ui.components.ProgressRing(
                            progress = vm.examAverage / 100f, size = 76.dp, stroke = 8.dp,
                            color = Color.White, trackColor = Color.White.copy(alpha = 0.28f),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${vm.examAverage}%", color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
                                Text("المعدل", color = Color.White.copy(alpha = 0.85f), fontSize = 8.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroPill(Icons.Filled.MenuBook, "${vm.doneLessons.size}", "درس مكتمل")
                    HeroPill(Icons.Filled.Translate, "${vm.examableWords.size}", "كلمة مؤهّلة")
                    if (vm.examHistory.isNotEmpty()) {
                        HeroPill(Icons.Filled.EmojiEvents, "${vm.examBest}%", "أفضل نتيجة")
                    }
                }
            }
        }

        error?.let {
            Surface(shape = RoundedCornerShape(14.dp), color = ZRose.copy(alpha = 0.14f), modifier = Modifier.fillMaxWidth(), onClick = onDismissError) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ErrorOutline, null, tint = ZRose, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(it, color = ZRose, fontSize = 12.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
                }
            }
        }

        if (!hasMaterial) {
            ExamEmptyState()
            Spacer(Modifier.height(90.dp))
            return@Column
        }

        // ---------- weakness diagnosis ----------
        if (weakest.isNotEmpty()) {
            SectionCard(Icons.Filled.MonitorHeart, "تشخيص نقاط الضعف", ZRose) {
                val critical = weakest.filter { it.weakness >= 0.45f }
                Text(
                    if (critical.isEmpty()) "لا توجد نقاط ضعف حرجة — أداؤك متوازن \uD83D\uDC4D"
                    else "${critical.size} كلمة تحتاج تدخّلاً عاجلاً",
                    color = ZTextSecondary, fontSize = 12.sp,
                )
                Spacer(Modifier.height(12.dp))
                weakest.take(5).forEach { ww ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        val c = when {
                            ww.weakness >= 0.55f -> ZRose
                            ww.weakness >= 0.35f -> ZAmber
                            else -> ZEmerald
                        }
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(c))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ww.word.english, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(ww.reason, color = ZTextMuted, fontSize = 10.sp)
                        }
                        Box(Modifier.width(56.dp)) {
                            LinearProgressIndicator(
                                progress = { ww.weakness },
                                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                                color = c, trackColor = ZBorder,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("${(ww.weakness * 100).toInt()}%", color = c, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ---------- per-skill accuracy ----------
        val skills = vm.skillAccuracy
        if (skills.isNotEmpty()) {
            SectionCard(Icons.Filled.Insights, "دقّتك بحسب المهارة", ZPurple) {
                vm.weakestSkill?.let { ws ->
                    Text(
                        "أضعف مهارة: ${ws.label} — ركّز عليها",
                        color = ZAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                ExamSkill.values().forEach { s ->
                    val acc = skills[s] ?: return@forEach
                    val c = when {
                        acc >= 0.8f -> ZEmerald
                        acc >= 0.6f -> ZAmber
                        else -> ZRose
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.emoji, fontSize = 13.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(s.label, color = ZTextSecondary, fontSize = 12.sp, modifier = Modifier.width(96.dp))
                        LinearProgressIndicator(
                            progress = { acc },
                            modifier = Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(4.dp)),
                            color = c, trackColor = ZBorder,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("${(acc * 100).toInt()}%", color = c, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        // ---------- question count ----------
        SectionCard(Icons.Filled.Tune, "عدد الأسئلة", ZIndigo) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${vm.examQuestionCount}", color = ZIndigo, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(6.dp))
                Text("سؤالاً", color = ZTextSecondary, fontSize = 13.sp)
            }
            Slider(
                value = vm.examQuestionCount.toFloat(),
                onValueChange = { vm.examQuestionCount = it.toInt() },
                valueRange = 5f..30f,
                steps = 24,
                colors = SliderDefaults.colors(thumbColor = ZIndigo, activeTrackColor = ZIndigo, inactiveTrackColor = ZBorder),
            )
        }

        // ---------- modes ----------
        Text("أنواع الاختبارات", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 17.sp)
        ExamMode.values().forEach { mode ->
            ModeCard(
                mode = mode,
                enabled = vm.canTakeExam(mode),
                available = vm.examAvailability(mode),
                onClick = { onPick(mode) },
            )
        }

        // ---------- history + trend ----------
        if (vm.examHistory.isNotEmpty()) {
            SectionCard(Icons.Filled.Timeline, "تطوّر نتائجك", ZEmerald) {
                TrendChart(vm.examHistory.map { it.pct })
                Spacer(Modifier.height(14.dp))
                vm.examHistory.reversed().take(6).forEach { rec ->
                    HistoryRow(rec)
                }
            }
        }
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
private fun HeroPill(icon: ImageVector, value: String, label: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.20f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
            Spacer(Modifier.width(3.dp))
            Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp)
        }
    }
}

@Composable
private fun SectionCard(icon: ImageVector, title: String, accent: Color, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ModeCard(mode: ExamMode, enabled: Boolean, available: Int, onClick: () -> Unit) {
    val (icon, accent) = when (mode) {
        ExamMode.DAILY -> Icons.Filled.Today to ZEmerald
        ExamMode.WEEKLY -> Icons.Filled.DateRange to ZPurple
        ExamMode.SMART -> Icons.Filled.AutoAwesome to ZIndigo
        ExamMode.WEAKNESS -> Icons.Filled.LocalHospital to ZRose
        ExamMode.LISTENING -> Icons.Filled.Hearing to ZCyanDeep
        ExamMode.WRITING -> Icons.Filled.EditNote to ZPurple
        ExamMode.GRAMMAR -> Icons.Filled.Rule to ZAmber
        ExamMode.CONVERSATION -> Icons.Filled.Forum to ZEmerald
        ExamMode.LESSON -> Icons.Filled.MenuBook to ZCyan
        ExamMode.COURSE -> Icons.Filled.School to ZIndigo
        ExamMode.FINAL -> Icons.Filled.EmojiEvents to ZAmber
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ZCard,
        shadowElevation = if (enabled) 4.dp else 0.dp,
        modifier = Modifier.fillMaxWidth().then(
            if (mode == ExamMode.FINAL && enabled)
                Modifier.border(1.5.dp, ZAmber.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            else Modifier
        ),
        onClick = { if (enabled) onClick() },
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(15.dp))
                    .background(
                        if (enabled) Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.7f)))
                        else Brush.linearGradient(listOf(ZBorder, ZBorder))
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon, null,
                    tint = if (enabled) Color.White else ZTextMuted,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        mode.label,
                        color = if (enabled) ZTextPrimary else ZTextMuted,
                        fontWeight = FontWeight.Black, fontSize = 15.sp,
                    )
                    if (mode == ExamMode.FINAL) {
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(50), color = ZAmber.copy(alpha = 0.2f)) {
                            Text(
                                "شامل", color = ZAmber, fontSize = 8.sp, fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                Text(
                    mode.desc,
                    color = if (enabled) ZTextSecondary else ZTextMuted,
                    fontSize = 11.sp, lineHeight = 17.sp,
                )
                if (enabled && available > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text("$available عنصراً متاحاً", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                } else if (!enabled) {
                    Spacer(Modifier.height(4.dp))
                    Text("أكمل دروساً أو أضف كلمات لفتحه", color = ZTextMuted, fontSize = 9.sp)
                }
            }
            Icon(
                if (enabled) Icons.Filled.ChevronLeft else Icons.Filled.Lock,
                null,
                tint = if (enabled) accent else ZTextMuted,
                modifier = Modifier.size(if (enabled) 22.dp else 16.dp),
            )
        }
    }
}

@Composable
private fun HistoryRow(rec: ExamRecord) {
    val c = when {
        rec.pct >= 80 -> ZEmerald
        rec.pct >= 60 -> ZAmber
        else -> ZRose
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Text("${rec.pct}%", color = c, fontSize = 11.sp, fontWeight = FontWeight.Black) }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(rec.title, color = ZTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
            Text(
                "${rec.correct}/${rec.total} · ${rec.stamp}",
                color = ZTextMuted, fontSize = 10.sp,
            )
        }
        Icon(
            if (rec.passed) Icons.Filled.CheckCircle else Icons.Filled.Replay,
            null, tint = c, modifier = Modifier.size(17.dp),
        )
    }
}

/** Sparkline of exam scores over time. */
@Composable
private fun TrendChart(scores: List<Int>) {
    if (scores.size < 2) {
        Text("أكمل اختبارين لرؤية منحنى التطوّر", color = ZTextMuted, fontSize = 11.sp)
        return
    }
    val pts = scores.takeLast(12)
    val lineColor = ZEmerald
    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
        val w = size.width
        val h = size.height
        val stepX = if (pts.size > 1) w / (pts.size - 1) else w
        fun y(v: Int) = h - (v / 100f) * h * 0.9f - h * 0.05f

        // 60% pass guide
        drawLine(
            color = ZBorder,
            start = Offset(0f, y(60)), end = Offset(w, y(60)),
            strokeWidth = 1.5f,
        )
        val path = Path()
        val fill = Path()
        pts.forEachIndexed { i, v ->
            val x = i * stepX
            val yy = y(v)
            if (i == 0) { path.moveTo(x, yy); fill.moveTo(x, h); fill.lineTo(x, yy) }
            else { path.lineTo(x, yy); fill.lineTo(x, yy) }
        }
        fill.lineTo(w, h)
        fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.28f), Color.Transparent)))
        drawPath(path, lineColor, style = Stroke(width = 3.5f, cap = StrokeCap.Round))
        pts.forEachIndexed { i, v ->
            drawCircle(lineColor, radius = 4.5f, center = Offset(i * stepX, y(v)))
        }
    }
}

@Composable
private fun ExamEmptyState() {
    Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(84.dp).clip(RoundedCornerShape(26.dp)).background(ZSurfaceVariant),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Quiz, null, tint = ZTextMuted, modifier = Modifier.size(44.dp)) }
            Spacer(Modifier.height(18.dp))
            Text("لا توجد مادة للاختبار بعد", color = ZTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "الاختبارات تُبنى فقط من الدروس التي أكملتها والكلمات المعتمدة في قاموسك — " +
                    "وليس من أي درس مستورد فقط.\n\nأكمل درساً واحداً أو أضف 4 كلمات لتبدأ.",
                color = ZTextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 21.sp,
            )
        }
    }
}

/* ══════════════════════════════ pickers ══════════════════════════════ */

@Composable
private fun LessonPicker(vm: AppViewModel, onBack: () -> Unit, onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        PickerHeader("اختر درساً مكتملاً", "${vm.doneLessons.size} درس متاح", onBack)
        Spacer(Modifier.height(14.dp))
        if (vm.doneLessons.isEmpty()) {
            Text("لم تُكمل أي درس بعد", color = ZTextMuted, fontSize = 13.sp)
        }
        vm.examableCourses.forEach { course ->
            val ls = vm.completedLessonsOf(course.id)
            if (ls.isEmpty()) return@forEach
            Text(
                course.name, color = ZIndigo, fontWeight = FontWeight.Black, fontSize = 14.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )
            ls.forEach { l ->
                val bank = l.quiz.size + l.dialogues.size + l.newWordIds.size
                Surface(
                    shape = RoundedCornerShape(16.dp), color = ZCard, shadowElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    onClick = { onPick(l.id) },
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(ZEmerald.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) { Text("${l.no}", color = ZEmerald, fontWeight = FontWeight.Black, fontSize = 13.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(l.title, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                            Text("$bank عنصراً قابلاً للاختبار", color = ZTextMuted, fontSize = 10.sp)
                        }
                        Icon(Icons.Filled.ChevronLeft, null, tint = ZIndigo, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
private fun CoursePicker(vm: AppViewModel, onBack: () -> Unit, onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        PickerHeader("اختر كورساً", "${vm.examableCourses.size} كورس فيه دروس مكتملة", onBack)
        Spacer(Modifier.height(14.dp))
        if (vm.examableCourses.isEmpty()) {
            Text("لا يوجد كورس فيه دروس مكتملة بعد", color = ZTextMuted, fontSize = 13.sp)
        }
        vm.examableCourses.forEach { c ->
            val done = vm.courseDone(c.id)
            val total = vm.courseTotal(c.id)
            Surface(
                shape = RoundedCornerShape(18.dp), color = ZCard, shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                onClick = { onPick(c.id) },
            ) {
                Column(Modifier.padding(15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                                .background(Color(c.accent).copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.School, null, tint = Color(c.accent), modifier = Modifier.size(22.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                            Text("${c.type.label} · $done مكتمل من $total درساً", color = ZTextSecondary, fontSize = 11.sp)
                        }
                        Icon(Icons.Filled.ChevronLeft, null, tint = ZIndigo, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { if (total > 0) done.toFloat() / total else 0f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                        color = Color(c.accent), trackColor = ZBorder,
                    )
                }
            }
        }
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
private fun PickerHeader(title: String, sub: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.ArrowForward, "رجوع", tint = ZTextPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(sub, color = ZTextSecondary, fontSize = 11.sp)
        }
    }
}

/* ══════════════════════════════ result ══════════════════════════════ */

@Composable
private fun ExamResultScreen(
    vm: AppViewModel,
    answers: List<ExamAnswer>,
    durationMs: Long,
    onReview: () -> Unit,
    onAgain: () -> Unit,
    onDone: () -> Unit,
) {
    val correct = answers.count { it.correct }
    val total = answers.size
    val pct = if (total > 0) correct * 100 / total else 0
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }
    val animPct by animateFloatAsState(if (show) pct / 100f else 0f, tween(900), label = "ring")

    val skillTotal = answers.groupingBy { it.question.skill }.eachCount()
    val skillCorrect = answers.filter { it.correct }.groupingBy { it.question.skill }.eachCount()
    val xpGained = answers.sumOf { if (it.correct) 8 + it.question.difficulty * 4 else 1 }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(18.dp))
        com.zmastery.english.ui.components.ProgressRing(progress = animPct, size = 158.dp, stroke = 13.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$pct%", color = ZTextPrimary, fontSize = 36.sp, fontWeight = FontWeight.Black)
                Text("$correct / $total", color = ZTextSecondary, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        val (msg, emoji) = when {
            pct >= 90 -> "متفوّق!" to "\uD83C\uDFC6"
            pct >= 75 -> "ممتاز!" to "\uD83C\uDF1F"
            pct >= 60 -> "جيد — واصل" to "\uD83D\uDCAA"
            pct >= 40 -> "تحتاج مراجعة" to "\uD83D\uDCDA"
            else -> "لا بأس، أعد المحاولة" to "\uD83C\uDF31"
        }
        Text("$msg $emoji", color = ZTextPrimary, fontSize = 23.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ResultChip(Icons.Filled.Bolt, "+$xpGained", "XP", ZAmber)
            ResultChip(Icons.Filled.Timer, "${(durationMs / 1000 / 60)}:${String.format("%02d", (durationMs / 1000) % 60)}", "الوقت", ZCyanDeep)
            ResultChip(Icons.Filled.Cancel, "${total - correct}", "أخطاء", ZRose)
        }
        Spacer(Modifier.height(18.dp))

        // Per-skill breakdown for THIS exam
        AnimatedVisibility(show, enter = fadeIn(tween(500)), exit = fadeOut()) {
            Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Analytics, null, tint = ZPurple, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("أداؤك في هذا الاختبار", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    skillTotal.entries.sortedBy { it.key.ordinal }.forEach { (s, tot) ->
                        val c = skillCorrect[s] ?: 0
                        val ratio = c.toFloat() / tot
                        val col = when {
                            ratio >= 0.8f -> ZEmerald
                            ratio >= 0.5f -> ZAmber
                            else -> ZRose
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(s.emoji, fontSize = 13.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(s.label, color = ZTextSecondary, fontSize = 12.sp, modifier = Modifier.width(96.dp))
                            LinearProgressIndicator(
                                progress = { ratio },
                                modifier = Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(4.dp)),
                                color = col, trackColor = ZBorder,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("$c/$tot", color = col, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    val weakHere = skillTotal.entries
                        .minByOrNull { (s, t) -> (skillCorrect[s] ?: 0).toFloat() / t }?.key
                    if (weakHere != null && total >= 3) {
                        Spacer(Modifier.height(10.dp))
                        Surface(shape = RoundedCornerShape(12.dp), color = ZAmber.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.TipsAndUpdates, null, tint = ZAmber, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(9.dp))
                                Text(
                                    "نصيحة: ركّز على ${weakHere.label} في جلستك القادمة",
                                    color = ZTextSecondary, fontSize = 11.sp, lineHeight = 18.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        val wrong = total - correct
        if (wrong > 0) {
            Surface(shape = RoundedCornerShape(14.dp), color = ZIndigo.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Autorenew, null, tint = ZIndigo, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "أُعيدت $wrong كلمة إلى قائمة المراجعة تلقائياً لتقويتها",
                        color = ZTextSecondary, fontSize = 11.sp, lineHeight = 18.sp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Button(
            onClick = onReview,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ZIndigo),
        ) {
            Icon(Icons.Filled.FactCheck, null); Spacer(Modifier.width(8.dp))
            Text("راجع الأسئلة والأخطاء", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onAgain,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ZBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ZTextPrimary),
            ) {
                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp)); Text("مرة أخرى", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = onDone,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ZBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ZTextPrimary),
            ) { Text("المركز", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
private fun ResultChip(icon: ImageVector, value: String, label: String, accent: Color) {
    Surface(shape = RoundedCornerShape(15.dp), color = ZCard, shadowElevation = 3.dp) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Column {
                Text(value, color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 14.sp)
                Text(label, color = ZTextSecondary, fontSize = 9.sp)
            }
        }
    }
}

/* ══════════════════════════════ review ══════════════════════════════ */

@Composable
private fun ExamReviewScreen(answers: List<ExamAnswer>, onBack: () -> Unit) {
    var onlyWrong by remember { mutableStateOf(true) }
    val shown = if (onlyWrong) answers.filter { !it.correct } else answers
    val wrongCount = answers.count { !it.correct }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        PickerHeader("مراجعة الاختبار", "${answers.size} سؤالاً · $wrongCount خطأ", onBack)
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ZSurfaceVariant).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(true to "الأخطاء ($wrongCount)", false to "الكل (${answers.size})").forEach { (v, label) ->
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(9.dp),
                    color = if (onlyWrong == v) ZIndigo else Color.Transparent,
                    onClick = { onlyWrong = v },
                ) {
                    Box(Modifier.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                        Text(
                            label,
                            color = if (onlyWrong == v) Color.White else ZTextSecondary,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (shown.isEmpty()) {
            Surface(shape = RoundedCornerShape(18.dp), color = ZEmerald.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.EmojiEvents, null, tint = ZEmerald, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("لا أخطاء! إجابات كاملة \uD83C\uDF89", color = ZEmerald, fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
            }
        }

        shown.forEachIndexed { i, a ->
            val q = a.question
            val c = if (a.correct) ZEmerald else ZRose
            Surface(
                shape = RoundedCornerShape(18.dp), color = ZCard, shadowElevation = 3.dp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            ) {
                Column(Modifier.padding(15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(26.dp).clip(RoundedCornerShape(9.dp)).background(c.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (a.correct) Icons.Filled.Check else Icons.Filled.Close,
                                null, tint = c, modifier = Modifier.size(15.dp),
                            )
                        }
                        Spacer(Modifier.width(9.dp))
                        Text(q.type.label, color = c, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        if (q.audioText.isNotBlank()) {
                            com.zmastery.english.audio.AudioButton(
                                text = q.audioText, audioKey = "rev_ex_${q.key}_$i",
                                accent = ZIndigo, size = 30.dp, iconSize = 15.dp,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(q.prompt, color = ZTextMuted, fontSize = 11.sp)
                    if (q.subject.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(q.subject, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 24.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    if (!a.correct) {
                        AnswerLine("إجابتك", a.given.ifBlank { "—" }, ZRose)
                        Spacer(Modifier.height(6.dp))
                    }
                    AnswerLine(
                        "الصحيحة",
                        if (q.correctIndex >= 0) q.options.getOrNull(q.correctIndex) ?: q.correctText else q.correctText,
                        ZEmerald,
                    )
                    if (q.explanationAr.isNotBlank()) {
                        Spacer(Modifier.height(9.dp))
                        Divider(color = ZBorder)
                        Spacer(Modifier.height(9.dp))
                        Text(q.explanationAr, color = ZTextSecondary, fontSize = 12.sp, lineHeight = 20.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
private fun AnswerLine(label: String, value: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(7.dp), color = accent.copy(alpha = 0.14f)) {
            Text(
                label, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(value, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 21.sp)
    }
}
