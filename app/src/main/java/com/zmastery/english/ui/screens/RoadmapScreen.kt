package com.zmastery.english.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.zmastery.english.data.*
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel

/**
 * Roadmap = two complementary halves, switched by a segmented control:
 *
 *  • الخريطة  — a Duolingo-style zig-zag path of every level → course → lesson.
 *               Node colour encodes real state: locked / added / done / urgent.
 *  • الخطة    — the study plan: goal, duration, intensity, custom per-day mix,
 *               and the generated dated timeline that the dashboard reads.
 */
@Composable
fun RoadmapScreen(
    vm: AppViewModel,
    onOpenCourse: (Int) -> Unit,
    onOpenLesson: (Int) -> Unit = {},
) {
    var tab by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(16.dp))
                .background(ZSurfaceVariant).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SegTab(Modifier.weight(1f), "الخريطة", Icons.Filled.Map, tab == 0) { tab = 0 }
            SegTab(Modifier.weight(1f), "الخطة", Icons.Filled.EventNote, tab == 1) { tab = 1 }
        }
        when (tab) {
            0 -> ZigZagMap(vm, onOpenCourse, onOpenLesson)
            else -> PlanTab(vm, onOpenLesson)
        }
    }
}

@Composable
private fun SegTab(modifier: Modifier, label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = modifier, shape = RoundedCornerShape(12.dp),
        color = if (active) ZIndigo else Color.Transparent, onClick = onClick,
    ) {
        Row(
            Modifier.padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = if (active) Color.White else ZTextSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = if (active) Color.White else ZTextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

/* ══════════════════════════ A · ZIG-ZAG MAP ══════════════════════════ */

/** Visual state of one lesson dot. */
private enum class DotState(val color: Color, val icon: ImageVector?) {
    MISSING(Color(0xFF9E9E9E), null),      // course exists but lesson not imported
    ADDED(Color(0xFF64B5F6), null),        // imported, not completed
    DONE(Color(0xFF43A047), Icons.Filled.Check),
    URGENT(Color(0xFFE53935), Icons.Filled.PriorityHigh),
}

@Composable
private fun ZigZagMap(vm: AppViewModel, onOpenCourse: (Int) -> Unit, onOpenLesson: (Int) -> Unit) {
    var sheetLesson by remember { mutableStateOf<Lesson?>(null) }
    // Collapsed by default: tap a level to reveal its courses, then tap a
    // course to reveal its lesson path. Only one of each stays open.
    var expandedLevel by rememberSaveable { mutableStateOf<Int?>(null) }
    var expandedCourse by rememberSaveable { mutableStateOf<Int?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { MapHeader(vm) }

        SampleData.levels.forEach { level ->
            val levelCourses = vm.coursesForLevel(level.id)
            val stats = vm.levelStats(level.id)
            val levelOpen = expandedLevel == level.id

            item(key = "lvl_${level.id}") {
                LevelHeader(
                    level = level,
                    stats = stats,
                    expanded = levelOpen,
                    onToggle = {
                        expandedLevel = if (levelOpen) null else level.id
                        expandedCourse = null
                    },
                )
            }

            if (levelOpen) {
                levelCourses.forEach { course ->
                    val cl = vm.lessons.filter { it.courseId == course.id }.sortedBy { it.no }
                    val courseOpen = expandedCourse == course.id
                    item(key = "c_${course.id}") {
                        CourseBanner(
                            course = course,
                            done = vm.courseDone(course.id),
                            imported = vm.courseImported(course.id),
                            total = vm.courseTotal(course.id),
                            completion = vm.courseCompletion(course.id),
                            expanded = courseOpen,
                            onToggle = { expandedCourse = if (courseOpen) null else course.id },
                            onOpen = { onOpenCourse(course.id) },
                        )
                    }
                    if (courseOpen) {
                        if (cl.isNotEmpty()) {
                            item(key = "z_${course.id}") {
                                ZigZagRow(
                                    lessons = cl,
                                    accent = Color(course.accent),
                                    urgentIds = vm.lessonsToReview.map { it.id }.toSet(),
                                ) { sheetLesson = it }
                            }
                        } else {
                            item(key = "e_${course.id}") { EmptyCourseHint(vm.courseTotal(course.id)) }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }

    sheetLesson?.let { l ->
        LessonPeekDialog(
            lesson = l,
            course = vm.courses.firstOrNull { it.id == l.courseId },
            onStart = { sheetLesson = null; onOpenLesson(l.id) },
            onDismiss = { sheetLesson = null },
        )
    }
}

@Composable
private fun MapHeader(vm: AppViewModel) {
    val s = vm.planSummary
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(ZEmerald, ZCyanDeep))).padding(20.dp)
    ) {
        Column {
            Text("خريطة المنهج", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text(
                "انقر على المستوى لعرض كورساته، ثم على الكورس لعرض مسار دروسه",
                color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, lineHeight = 18.sp,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeaderPill("${s.completedLessons}", "مكتمل")
                HeaderPill("${s.curriculumLessons}", "في المنهج")
                HeaderPill("${(s.progress * 100).toInt()}%", "التقدم")
            }
            if (s.totalLessons > 0 && s.curriculumLessons > s.totalLessons) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "متوفر لديك ${s.totalLessons} درساً من ${s.curriculumLessons} " +
                        "(${(s.coverage * 100).toInt()}% من المنهج)",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
            // Legend
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendDot(DotState.DONE.color, "مكتمل")
                LegendDot(DotState.ADDED.color, "متاح")
                LegendDot(DotState.URGENT.color, "عاجل")
                LegendDot(DotState.MISSING.color, "غير مضاف")
            }
        }
    }
}

@Composable
private fun HeaderPill(value: String, label: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.22f)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
            Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp)
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, color = Color.White.copy(alpha = 0.9f), fontSize = 10.sp)
    }
}

@Composable
private fun LevelHeader(
    level: Level,
    stats: AppViewModel.LevelStats,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val arrow by animateFloatAsState(if (expanded) 90f else 0f, tween(240), label = "lvArrow")
    Surface(
        shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        onClick = onToggle,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(ZIndigo, ZPurple))),
                    contentAlignment = Alignment.Center,
                ) { Text(level.emoji, fontSize = 23.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "المستوى ${level.id} · ${level.name}",
                        color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp,
                    )
                    Text(
                        "${stats.courseCount} كورس · ${stats.total} درساً في المنهج",
                        color = ZTextSecondary, fontSize = 11.sp, maxLines = 1,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(stats.completion * 100).toInt()}%",
                        color = if (stats.done > 0) ZEmerald else ZTextMuted,
                        fontWeight = FontWeight.Black, fontSize = 17.sp,
                    )
                    Text("إنجاز", color = ZTextMuted, fontSize = 9.sp)
                }
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Filled.ChevronLeft, null, tint = ZTextMuted,
                    modifier = Modifier.size(18.dp).rotate(-arrow),
                )
            }
            Spacer(Modifier.height(16.dp))
            // Real completion measured against the FULL curriculum of the level.
            TwinBar("الإنجاز", stats.done, stats.total, stats.completion, ZEmerald, "من المنهج")
            Spacer(Modifier.height(8.dp))
            // How much of that curriculum actually exists on the device.
            TwinBar("المتوفر", stats.imported, stats.total, stats.coverage, ZCyanDeep, "درس مُستورد")
        }
    }
}

@Composable
private fun TwinBar(title: String, value: Int, total: Int, pct: Float, accent: Color, hint: String) {
    val animated by animateFloatAsState(pct.coerceIn(0f, 1f), tween(600), label = "bar")
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = ZTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(hint, color = ZTextMuted, fontSize = 9.sp)
            Spacer(Modifier.weight(1f))
            Text("$value / $total", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
            color = accent, trackColor = ZBorder,
        )
    }
}

@Composable
private fun CourseBanner(
    course: Course,
    done: Int,
    imported: Int,
    total: Int,
    completion: Float,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val accent = Color(course.accent)
    val arrow by animateFloatAsState(if (expanded) 90f else 0f, tween(220), label = "cbArrow")
    val anim by animateFloatAsState(completion, tween(600), label = "cbBar")
    Surface(
        shape = RoundedCornerShape(16.dp), color = accent.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp), onClick = onToggle,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(accent),
                    contentAlignment = Alignment.Center,
                ) { Text(courseEmoji(course.type), fontSize = 17.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(course.name, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                    Text(
                        "${course.type.label} · $done مكتمل · $imported متوفر · من $total",
                        color = ZTextSecondary, fontSize = 10.sp, maxLines = 1,
                    )
                }
                Text(
                    "${(completion * 100).toInt()}%",
                    color = accent, fontWeight = FontWeight.Black, fontSize = 13.sp,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Filled.ChevronLeft, null, tint = accent,
                    modifier = Modifier.size(18.dp).rotate(-arrow),
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { anim },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)),
                color = accent, trackColor = ZBorder,
            )
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp), color = accent,
                    modifier = Modifier.fillMaxWidth(), onClick = onOpen,
                ) {
                    Row(
                        Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("افتح الكورس", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

private fun courseEmoji(t: CourseType): String = when (t) {
    CourseType.VOCABULARY -> "\uD83C\uDF31"
    CourseType.GRAMMAR -> "\uD83D\uDCD0"
    CourseType.READING -> "\uD83D\uDCD6"
    CourseType.LISTENING -> "\uD83D\uDD0A"
    CourseType.CONVERSATION -> "\uD83D\uDCAC"
    CourseType.PHONETICS -> "\uD83D\uDDE3\uFE0F"
    CourseType.WRITING -> "\u270D\uFE0F"
}

/**
 * The zig-zag ladder. Lessons alternate left → right → left, joined by a
 * curved path drawn behind them, exactly like a game world map.
 */
@Composable
private fun ZigZagRow(
    lessons: List<Lesson>,
    accent: Color,
    urgentIds: Set<Int>,
    onTap: (Lesson) -> Unit,
) {
    val rowH = 74.dp
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        lessons.chunked(1).forEachIndexed { _, _ -> }
        lessons.forEachIndexed { i, lesson ->
            // 4 horizontal anchor positions produce the zig-zag rhythm
            val slot = when (i % 4) { 0 -> 0.10f; 1 -> 0.38f; 2 -> 0.66f; else -> 0.38f }
            val nextSlot = if (i + 1 < lessons.size) {
                when ((i + 1) % 4) { 0 -> 0.10f; 1 -> 0.38f; 2 -> 0.66f; else -> 0.38f }
            } else null

            val state = when {
                lesson.isCompleted && lesson.id in urgentIds -> DotState.URGENT
                lesson.isCompleted -> DotState.DONE
                else -> DotState.ADDED
            }

            Box(Modifier.fillMaxWidth().height(rowH)) {
                // connector to the next dot
                if (nextSlot != null) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        val x1 = size.width * (slot + 0.07f)
                        val y1 = size.height * 0.5f
                        val x2 = size.width * (nextSlot + 0.07f)
                        val y2 = size.height * 1.5f
                        drawLine(
                            color = accent.copy(alpha = 0.30f),
                            start = Offset(x1, y1),
                            end = Offset(x2, y2),
                            strokeWidth = 7f,
                            cap = StrokeCap.Round,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.fillMaxWidth(slot))
                    LessonDot(lesson, state, accent) { onTap(lesson) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "درس ${lesson.no}",
                            color = ZTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        )
                        Text(
                            lesson.title,
                            color = if (state == DotState.DONE) ZTextSecondary else ZTextPrimary,
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                            lineHeight = 15.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonDot(lesson: Lesson, state: DotState, accent: Color, onClick: () -> Unit) {
    val color = if (state == DotState.ADDED) accent else state.color
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = color,
        shadowElevation = if (state == DotState.DONE) 2.dp else 6.dp,
        modifier = Modifier.size(46.dp),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (state.icon != null) {
                Icon(state.icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
            } else {
                Text("${lesson.no}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun EmptyCourseHint(target: Int) {
    Surface(
        shape = RoundedCornerShape(16.dp), color = ZSurfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CloudDownload, null, tint = ZTextMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                "لم تُستورد دروس هذا الكورس بعد ($target درساً متوقعاً)",
                color = ZTextMuted, fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun LessonPeekDialog(lesson: Lesson, course: Course?, onStart: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = ZCard) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(16.dp))
                            .background(Color(course?.accent ?: 0xFF6366F5)),
                        contentAlignment = Alignment.Center,
                    ) { Text("${lesson.no}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(course?.name ?: "درس", color = ZTextSecondary, fontSize = 11.sp)
                        Text(lesson.title, color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp, lineHeight = 21.sp)
                    }
                }
                if (lesson.summaryAr.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(lesson.summaryAr, color = ZTextSecondary, fontSize = 13.sp, lineHeight = 21.sp)
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (lesson.newWordIds.isNotEmpty()) PeekChip(Icons.Filled.Style, "${lesson.newWordIds.size} كلمة")
                    if (lesson.quiz.isNotEmpty()) PeekChip(Icons.Filled.Quiz, "${lesson.quiz.size} سؤال")
                    if (lesson.isCompleted) PeekChip(Icons.Filled.CheckCircle, "مكتمل")
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ZIndigo),
                ) {
                    Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(8.dp))
                    Text(if (lesson.isCompleted) "أعد الدرس" else "ابدأ الآن", fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("إغلاق", color = ZTextSecondary)
                }
            }
        }
    }
}

@Composable
private fun PeekChip(icon: ImageVector, text: String) {
    Surface(shape = RoundedCornerShape(50), color = ZSurfaceVariant) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = ZTextSecondary, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, color = ZTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/* ══════════════════════════ B · STUDY PLAN ══════════════════════════ */

@Composable
private fun PlanTab(vm: AppViewModel, onOpenLesson: (Int) -> Unit) {
    var showBuilder by remember { mutableStateOf(false) }
    val summary = vm.planSummary
    val timeline = remember(vm.studyPlan, vm.lessons.size, vm.lessons.count { it.isCompleted }) {
        vm.planTimeline
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { PlanHero(vm, summary) { showBuilder = true } }
        item { PlanPaceCard(summary) }

        if (timeline.isEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(16.dp), color = ZCard, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.EventBusy, null, tint = ZTextMuted, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("لا توجد دروس لجدولتها", color = ZTextPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            "استورد كورساً للمستوى ${vm.effectivePlan.targetLevel} لتُبنى خطتك تلقائياً",
                            color = ZTextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        } else {
            item {
                Text(
                    "الجدول اليومي (${timeline.size} يوم)",
                    color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            itemsIndexed(timeline, key = { _, d -> d.epochDay }) { _, day ->
                PlanDayCard(day, onOpenLesson)
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }

    if (showBuilder) {
        PlanBuilderDialog(vm) { showBuilder = false }
    }
}

@Composable
private fun PlanHero(vm: AppViewModel, s: PlanSummary, onEdit: () -> Unit) {
    val plan = vm.effectivePlan
    val level = SampleData.levels.firstOrNull { it.id == plan.targetLevel }
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(ZIndigo, ZPurple))).padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.22f)) {
                        Text(
                            if (plan.custom) "خطة مخصصة" else "الخطة الافتراضية",
                            color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "إتمام ${level?.name ?: "المستوى ${plan.targetLevel}"}",
                        color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black,
                    )
                    Text(
                        "${PlanDuration.from(plan.duration).label} · ${PlanIntensity.from(plan.intensity).minutes} دقيقة يومياً",
                        color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.22f), onClick = onEdit,
                ) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Tune, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Text("تخصيص", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { s.progress },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(8.dp)),
                color = Color.White, trackColor = Color.White.copy(alpha = 0.25f),
            )
            Spacer(Modifier.height(8.dp))
            Row {
                Text(
                    "${s.completedLessons} / ${s.totalLessons} درس",
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text("${(s.progress * 100).toInt()}%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun PlanPaceCard(s: PlanSummary) {
    Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PaceStat(Modifier.weight(1f), String.format("%.1f", s.lessonsPerDay), "درس/يوم", ZIndigo)
                PaceStat(Modifier.weight(1f), "${s.minutesPerDay}د", "يومياً", ZCyanDeep)
                PaceStat(Modifier.weight(1f), "${s.remainingLessons}", "متبقٍ", ZAmber)
            }
            Spacer(Modifier.height(16.dp))
            val onTrack = s.onTrack
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = (if (onTrack) ZEmerald else ZRose).copy(alpha = 0.13f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (onTrack) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                        null, tint = if (onTrack) ZEmerald else ZRose, modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (onTrack) "أنت ضمن الجدول \uD83D\uDC4F" else "متأخر عن الجدول",
                            color = if (onTrack) ZEmerald else ZRose,
                            fontWeight = FontWeight.Black, fontSize = 14.sp,
                        )
                        Text(
                            if (onTrack) "المتوقع ${s.expectedByNow} درساً · أنجزت ${s.completedLessons}"
                            else "المتوقع ${s.expectedByNow} · أنجزت ${s.completedLessons} (فارق ${-s.driftLessons})",
                            color = ZTextSecondary, fontSize = 11.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Flag, null, tint = ZTextMuted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "تاريخ الإنهاء المتوقع: ${s.endDate.dayOfMonth}/${s.endDate.monthValue}/${s.endDate.year}",
                    color = ZTextMuted, fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun PaceStat(modifier: Modifier, value: String, label: String, accent: Color) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = accent.copy(alpha = 0.11f)) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = accent, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(label, color = ZTextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun PlanDayCard(day: PlanDay, onOpenLesson: (Int) -> Unit) {
    val accent = when {
        day.isToday -> ZIndigo
        day.isComplete -> ZEmerald
        day.isPast -> ZRose
        else -> ZTextMuted
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (day.isToday) ZIndigo.copy(alpha = 0.08f) else ZCard,
        shadowElevation = if (day.isToday) 6.dp else 3.dp,
        modifier = Modifier.fillMaxWidth()
            .then(if (day.isToday) Modifier.border(2.dp, ZIndigo, RoundedCornerShape(16.dp)) else Modifier),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (day.isComplete) Icon(Icons.Filled.Check, null, tint = accent, modifier = Modifier.size(20.dp))
                    else Text("${day.index + 1}", color = accent, fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${day.dayLabel} ${day.dateLabel}",
                            color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        )
                        if (day.isToday) {
                            Spacer(Modifier.width(8.dp))
                            Surface(shape = RoundedCornerShape(50), color = ZIndigo) {
                                Text(
                                    "اليوم", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Text(
                        if (day.isRest) "يوم راحة"
                        else "${day.tasks.size} درس · ${day.reviewWords} كلمة مراجعة · ${day.conversationMinutes}د محادثة",
                        color = ZTextSecondary, fontSize = 10.sp,
                    )
                }
                if (day.tasks.isNotEmpty()) {
                    Text(
                        "${day.doneCount}/${day.tasks.size}",
                        color = accent, fontWeight = FontWeight.Black, fontSize = 13.sp,
                    )
                }
            }
            if (day.tasks.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                day.tasks.forEach { t ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(t.accent).copy(alpha = if (t.done) 0.06f else 0.12f),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        onClick = { onOpenLesson(t.lessonId) },
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (t.done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                null, tint = Color(t.accent), modifier = Modifier.size(17.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    t.lessonTitle,
                                    color = if (t.done) ZTextMuted else ZTextPrimary,
                                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1,
                                )
                                Text(
                                    "${t.track.emoji} ${t.courseName}",
                                    color = ZTextMuted, fontSize = 9.sp, maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ──────────────────── plan builder dialog ──────────────────── */

@Composable
private fun PlanBuilderDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val current = vm.effectivePlan
    var level by remember { mutableStateOf(current.targetLevel) }
    var duration by remember { mutableStateOf(PlanDuration.from(current.duration)) }
    var intensity by remember { mutableStateOf(PlanIntensity.from(current.intensity)) }
    var customPace by remember { mutableStateOf(current.lessonsPerDay > 0) }
    var lessonsPerDay by remember { mutableStateOf(if (current.lessonsPerDay > 0) current.lessonsPerDay else 2) }
    var reviewWords by remember {
        mutableStateOf(if (current.reviewWordsPerDay > 0) current.reviewWordsPerDay else 25)
    }
    var convoMin by remember {
        mutableStateOf(if (current.conversationMinutes > 0) current.conversationMinutes else 5)
    }
    var restFriday by remember { mutableStateOf(!current.includeWeekends) }

    val draft = StudyPlanDto(
        active = true, custom = true, targetLevel = level,
        duration = duration.name, intensity = intensity.name,
        startEpochDay = current.startEpochDay,
        lessonsPerDay = if (customPace) lessonsPerDay else 0,
        reviewWordsPerDay = reviewWords, conversationMinutes = convoMin,
        includeWeekends = !restFriday,
    )
    val preview = StudyPlanner.summarize(draft, vm.courses, vm.lessons)

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = ZCard) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(20.dp)
            ) {
                Text("إنشاء خطة دراسة", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 19.sp)
                Text("حدّد هدفك ومدتك — سنولّد جدولاً يومياً دقيقاً", color = ZTextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))

                FieldLabel("المستوى المستهدف")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SampleData.levels.forEach { l ->
                        PickChip("${l.emoji} ${l.id}", l.id == level, ZIndigo) { level = l.id }
                    }
                }
                Spacer(Modifier.height(16.dp))

                FieldLabel("المدة")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlanDuration.values().forEach { d ->
                        PickChip(d.label, d == duration, ZCyanDeep) { duration = d }
                    }
                }
                Spacer(Modifier.height(16.dp))

                FieldLabel("الوقت اليومي")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlanIntensity.values().forEach { p ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = if (p == intensity) ZAmber else ZSurfaceVariant,
                            onClick = { intensity = p },
                        ) {
                            Column(
                                Modifier.padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(p.emoji, fontSize = 18.sp)
                                Text(
                                    p.label,
                                    color = if (p == intensity) Color.White else ZTextSecondary,
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "${p.minutes}د",
                                    color = if (p == intensity) Color.White.copy(alpha = 0.85f) else ZTextMuted,
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                // Advanced per-day mix
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("تحديد الوتيرة يدوياً", color = ZTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("بدل الحساب التلقائي من المدة", color = ZTextMuted, fontSize = 10.sp)
                    }
                    Switch(
                        checked = customPace, onCheckedChange = { customPace = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ZIndigo),
                    )
                }
                if (customPace) {
                    StepperRow("دروس يومياً", lessonsPerDay, 1, 8) { lessonsPerDay = it }
                }
                StepperRow("كلمات المراجعة يومياً", reviewWords, 5, 100, step = 5) { reviewWords = it }
                StepperRow("دقائق المحادثة", convoMin, 0, 60, step = 5) { convoMin = it }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("راحة يوم الجمعة", color = ZTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("يُستثنى من الجدول", color = ZTextMuted, fontSize = 10.sp)
                    }
                    Switch(
                        checked = restFriday, onCheckedChange = { restFriday = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ZIndigo),
                    )
                }

                Spacer(Modifier.height(16.dp))
                Surface(shape = RoundedCornerShape(16.dp), color = ZIndigo.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Preview, null, tint = ZIndigo, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("معاينة الخطة", color = ZIndigo, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${preview.totalLessons} درساً · ${String.format("%.1f", preview.lessonsPerDay)} درس/يوم · " +
                                "ينتهي ${preview.endDate.dayOfMonth}/${preview.endDate.monthValue}",
                            color = ZTextSecondary, fontSize = 12.sp, lineHeight = 19.sp,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { vm.savePlan(draft); onDismiss() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ZIndigo),
                ) {
                    Icon(Icons.Filled.Check, null); Spacer(Modifier.width(8.dp))
                    Text("إنشاء الخطة", fontWeight = FontWeight.Bold)
                }
                Row {
                    TextButton(onClick = { vm.resetPlan(); onDismiss() }, modifier = Modifier.weight(1f)) {
                        Text("استعادة الافتراضي", color = ZTextSecondary, fontSize = 12.sp)
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("إلغاء", color = ZTextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun PickChip(label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(50), color = if (active) accent else ZSurfaceVariant, onClick = onClick) {
        Text(
            label, color = if (active) Color.White else ZTextSecondary,
            fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun StepperRow(label: String, value: Int, min: Int, max: Int, step: Int = 1, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = ZTextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Surface(
            shape = RoundedCornerShape(12.dp), color = ZSurfaceVariant,
            onClick = { onChange((value - step).coerceAtLeast(min)) },
        ) { Icon(Icons.Filled.Remove, null, tint = ZTextPrimary, modifier = Modifier.padding(8.dp).size(16.dp)) }
        Text(
            "$value", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp,
            textAlign = TextAlign.Center, modifier = Modifier.width(46.dp),
        )
        Surface(
            shape = RoundedCornerShape(12.dp), color = ZSurfaceVariant,
            onClick = { onChange((value + step).coerceAtMost(max)) },
        ) { Icon(Icons.Filled.Add, null, tint = ZTextPrimary, modifier = Modifier.padding(8.dp).size(16.dp)) }
    }
}
