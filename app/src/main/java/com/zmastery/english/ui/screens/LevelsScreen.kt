package com.zmastery.english.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.data.Course
import com.zmastery.english.data.Lesson
import com.zmastery.english.data.SampleData
import com.zmastery.english.ui.components.SoftCard
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel

/**
 * المستويات والمناهج — a two-tier accordion.
 *
 *  Tap a LEVEL  → its courses slide open (only one level open at a time).
 *  Tap a COURSE → its lessons slide open inside it (only one course at a time).
 *
 * ── Percentages ───────────────────────────────────────────────────────────
 * Every percentage is measured against the CURRICULUM size (the syllabus
 * target), never against however many lessons happen to be imported. So one
 * finished lesson in Level 1 reads ≈1% (1 of 116), not 6%.
 *
 * Two distinct bars are shown so the two ideas never get confused:
 *   • الإنجاز  (solid, emerald)  — completed ÷ full curriculum
 *   • المتوفر  (faint, cyan)     — imported  ÷ full curriculum
 */
@Composable
fun LevelsScreen(vm: AppViewModel, onOpenCourse: (Int) -> Unit) {
    // Only one level and one course open at a time — keeps the page short.
    var expandedLevel by rememberSaveable { mutableStateOf<Int?>(null) }
    var expandedCourse by rememberSaveable { mutableStateOf<Int?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { OverallHeader(vm) }

        items(count = SampleData.levels.size, key = { SampleData.levels[it].id }) { idx ->
            val level = SampleData.levels[idx]
            val stats = vm.levelStats(level.id)
            val expanded = expandedLevel == level.id

            LevelAccordion(
                title = "المستوى ${level.id} · ${level.name}",
                emoji = level.emoji,
                stats = stats,
                expanded = expanded,
                onToggle = {
                    expandedLevel = if (expanded) null else level.id
                    expandedCourse = null
                },
            ) {
                vm.coursesForLevel(level.id).forEach { course ->
                    CourseAccordion(
                        course = course,
                        vm = vm,
                        expanded = expandedCourse == course.id,
                        onToggle = {
                            expandedCourse = if (expandedCourse == course.id) null else course.id
                        },
                        onOpenCourse = { onOpenCourse(course.id) },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

/* ─────────────────────────── overall header ─────────────────────────── */

@Composable
private fun OverallHeader(vm: AppViewModel) {
    val pct = vm.overallCompletion
    val animated by animateFloatAsState(pct, tween(700), label = "overall")
    val totalLessons = vm.courses.sumOf { vm.courseTotal(it.id) }
    val doneLessons = vm.courses.sumOf { vm.courseDone(it.id) }

    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(ZIndigo, ZPurple))).padding(20.dp)
    ) {
        Column {
            Text("المستويات والمناهج", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text(
                "انقر على المستوى لعرض كورساته، ثم على الكورس لعرض دروسه",
                color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, lineHeight = 18.sp,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${(pct * 100).toInt()}%",
                    color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("من المنهج كاملاً", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("$doneLessons من $totalLessons درساً", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(6.dp)),
                color = Color.White, trackColor = Color.White.copy(alpha = 0.25f),
            )
        }
    }
}

/* ─────────────────────────── level accordion ─────────────────────────── */

@Composable
private fun LevelAccordion(
    title: String,
    emoji: String,
    stats: AppViewModel.LevelStats,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val arrow by animateFloatAsState(if (expanded) 90f else 0f, tween(250), label = "arrow")
    val doneAnim by animateFloatAsState(stats.completion, tween(650), label = "done")
    val covAnim by animateFloatAsState(stats.coverage, tween(650), label = "cov")

    SoftCard(modifier = Modifier.fillMaxWidth(), radius = 22.dp) {
        Column {
            Surface(color = Color.Transparent, onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
                                .background(Brush.linearGradient(listOf(ZIndigo, ZPurple))),
                            contentAlignment = Alignment.Center,
                        ) { Text(emoji, fontSize = 26.sp) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title, color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 17.sp)
                            Text(
                                "${stats.courseCount} كورس · ${stats.total} درساً في المنهج",
                                color = ZTextSecondary, fontSize = 12.sp,
                            )
                        }
                        // Big honest percentage
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${(stats.completion * 100).toInt()}%",
                                color = if (stats.done > 0) ZEmerald else ZTextMuted,
                                fontWeight = FontWeight.Black, fontSize = 18.sp,
                            )
                            Text("إنجاز", color = ZTextMuted, fontSize = 9.sp)
                        }
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.ChevronLeft, null, tint = ZTextMuted,
                            modifier = Modifier.rotate(-arrow),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    // Completion (what really counts)
                    DualBar(
                        label = "الإنجاز",
                        value = "${stats.done} / ${stats.total} درس",
                        progress = doneAnim,
                        accent = ZEmerald,
                    )
                    Spacer(Modifier.height(8.dp))
                    // Coverage (how much content exists on the device)
                    DualBar(
                        label = "المتوفر",
                        value = "${stats.imported} مستورد",
                        progress = covAnim,
                        accent = ZCyanDeep,
                        thin = true,
                    )
                    if (stats.imported > 0 && stats.done < stats.imported) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "أكملت ${stats.done} من ${stats.imported} درساً متوفراً " +
                                "(${(stats.completionOfImported * 100).toInt()}% مما لديك)",
                            color = ZTextMuted, fontSize = 10.sp,
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(260)) + fadeIn(tween(260)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(140)),
            ) {
                Column {
                    Divider(color = ZBorder)
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        content = content,
                    )
                }
            }
        }
    }
}

@Composable
private fun DualBar(label: String, value: String, progress: Float, accent: Color, thin: Boolean = false) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = ZTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(value, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(if (thin) 5.dp else 8.dp).clip(RoundedCornerShape(5.dp)),
            color = if (thin) accent.copy(alpha = 0.65f) else accent,
            trackColor = ZBorder,
        )
    }
}

/* ─────────────────────────── course accordion ─────────────────────────── */

@Composable
private fun CourseAccordion(
    course: Course,
    vm: AppViewModel,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenCourse: () -> Unit,
) {
    val accent = Color(course.accent)
    val done = vm.courseDone(course.id)
    val imported = vm.courseImported(course.id)
    val total = vm.courseTotal(course.id)
    val completion = vm.courseCompletion(course.id)
    val anim by animateFloatAsState(completion, tween(600), label = "cp")
    val arrow by animateFloatAsState(if (expanded) 90f else 0f, tween(220), label = "ca")
    val finished = total > 0 && done >= total

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = ZSurfaceVariant,
    ) {
        Column {
            Surface(color = Color.Transparent, onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(13.dp))
                                .background(accent.copy(alpha = 0.20f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (finished) {
                                Icon(Icons.Filled.Verified, null, tint = accent, modifier = Modifier.size(24.dp))
                            } else {
                                Icon(courseIcon(course.type.icon), null, tint = accent)
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(course.name, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                "$done مكتمل · $imported متوفر · من $total درساً",
                                color = ZTextSecondary, fontSize = 11.sp,
                            )
                        }
                        Text(
                            "${(completion * 100).toInt()}%",
                            color = if (done > 0) accent else ZTextMuted,
                            fontWeight = FontWeight.Black, fontSize = 14.sp,
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.ChevronLeft, null, tint = ZTextMuted,
                            modifier = Modifier.size(18.dp).rotate(-arrow),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { anim },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                        color = accent, trackColor = ZBorder,
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(240)) + fadeIn(tween(240)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(130)),
            ) {
                Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                    Divider(color = ZBorder.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 10.dp))
                    val lessons = vm.lessons.filter { it.courseId == course.id }.sortedBy { it.no }
                    if (lessons.isEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.CloudDownload, null, tint = ZTextMuted, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "لم تُستورد دروس هذا الكورس بعد ($total درساً متوقعاً)",
                                color = ZTextMuted, fontSize = 11.sp, lineHeight = 17.sp,
                            )
                        }
                    } else {
                        lessons.take(MAX_INLINE_LESSONS).forEach { lesson ->
                            LessonRow(lesson, accent)
                        }
                        if (lessons.size > MAX_INLINE_LESSONS) {
                            Text(
                                "+ ${lessons.size - MAX_INLINE_LESSONS} درساً آخر",
                                color = ZTextMuted, fontSize = 11.sp,
                                modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onOpenCourse,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("افتح الكورس", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

private const val MAX_INLINE_LESSONS = 8

@Composable
private fun LessonRow(lesson: Lesson, accent: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(9.dp))
                .background(if (lesson.isCompleted) accent else ZBorder.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            if (lesson.isCompleted) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
            } else {
                Text("${lesson.no}", color = ZTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            lesson.title,
            color = if (lesson.isCompleted) ZTextSecondary else ZTextPrimary,
            fontSize = 12.sp,
            fontWeight = if (lesson.isCompleted) FontWeight.Normal else FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (lesson.isCompleted) {
            Text("مكتمل", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

fun courseIcon(name: String) = when (name) {
    "book" -> Icons.Filled.MenuBook
    "rule" -> Icons.Filled.Rule
    "read" -> Icons.Filled.AutoStories
    "listen" -> Icons.Filled.Headphones
    "talk" -> Icons.Filled.Forum
    "sound" -> Icons.Filled.RecordVoiceOver
    "write" -> Icons.Filled.Edit
    else -> Icons.Filled.School
}
