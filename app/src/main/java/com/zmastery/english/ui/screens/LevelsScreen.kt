package com.zmastery.english.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.data.Course
import com.zmastery.english.data.CourseType
import com.zmastery.english.data.SampleData
import com.zmastery.english.ui.components.SoftCard
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel

/**
 * المستويات والمناهج — نسخة معاد تصميمها حول قاعدة «نقرة واحدة تكفي»:
 *
 *   المستوى  ← بطاقة مضغوطة بشريط مزدوج الطبقة (إنجاز فوق متوفر) ودليل ألوان.
 *   الكورسات ← شبكة بطاقات من عمودين داخل المستوى المفتوح؛ لمسة واحدة تفتح
 *   الكورس مباشرة (بدلاً من الأكورديون المتداخل السابق)، مع تلميح
 *   «التالي: الدرس …» للاستئناف الفوري.
 *
 * ── النسب ─────────────────────────────────────────────────────────────────
 * كل نسبة تُحسب مقابل حجم المنهج الكامل (target) لا مقابل ما هو مستورد،
 * فيقرأ درسٌ منتهٍ في المستوى الأول ≈1% (1 من 116) بصدق.
 * الشريط المزدوج يعرض فكرتين مختلفتين بلا لبس:
 *   • الإنجاز (أخضر صلب)   — المكتمل ÷ المنهج الكامل
 *   • المتوفر (فيروزي شفاف) — المستورد ÷ المنهج الكامل
 */
@Composable
fun LevelsScreen(vm: AppViewModel, onOpenCourse: (Int) -> Unit) {
    // مستوى واحد مفتوح في كل لحظة — تبقى الصفحة قصيرة وواضحة.
    var expandedLevel by rememberSaveable { mutableStateOf<Int?>(null) }
    // استوديو المنهج المخصص — للمسؤول فقط.
    var showCreateCourse by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { OverallHeader(vm) }

        // ── بوابة المنهج المخصص: مستوى/مسار خاص خارج منهج الأكاديمية ──
        if (vm.isAdmin) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ZSurface,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, ZAmber.copy(alpha = 0.55f)),
                    onClick = { showCreateCourse = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.background(ZAmber.copy(alpha = 0.10f)).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                                .background(ZAmber.copy(alpha = 0.20f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.LibraryAdd, null, tint = ZAmberDeep, modifier = Modifier.size(21.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("إضافة منهج مخصص", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                            Text(
                                "إنجليزية هندسية/طبية/مساحة… مسار تخصصي باسمك ومستوى مستقل",
                                color = ZTextSecondary, fontSize = 11.sp, lineHeight = 16.sp,
                            )
                        }
                        Icon(Icons.Filled.Add, null, tint = ZAmberDeep, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }


        // الأكاديمية الثلاثة + المسارات التخصصية الديناميكية — بترتيب واحد.
        val levels = vm.allLevels
        items(count = levels.size, key = { levels[it].id }) { idx ->
            val level = levels[idx]
            val stats = vm.levelStats(level.id)
            val expanded = expandedLevel == level.id

            LevelCard(
                levelId = level.id,
                name = level.name,
                description = level.description,
                emoji = level.emoji,
                stats = stats,
                expanded = expanded,
                onToggle = {
                    expandedLevel = if (expanded) null else level.id
                },
            ) {
                // شبكة كورسات من عمودين — كل بطاقة تفتح الكورس مباشرة.
                vm.coursesForLevel(level.id).chunked(2).forEach { rowCourses ->
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        rowCourses.forEach { course ->
                            CourseTile(
                                course = course,
                                vm = vm,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onOpen = { onOpenCourse(course.id) },
                            )
                        }
                        if (rowCourses.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }

    if (showCreateCourse) {
        CreateCustomCourseDialog(vm) { showCreateCourse = false }
    }
}

/* ─────────────────────────── حوار إنشاء منهج مخصص ─────────────────────────── */

@Composable
private fun CreateCustomCourseDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(CourseType.VOCABULARY) }
    // 0 = لم يُحدد؛ 1..3 أكاديمية؛ ≥4 مسار تخصصي جديد.
    var levelId by remember { mutableStateOf(0) }
    var customLevelName by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("20") }
    var status by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    val academies = vm.allLevels.filter { it.id <= 3 }
    val nextCustomId = (vm.allLevels.maxOfOrNull { it.id } ?: 3) + 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إنشاء منهج مخصص 👑", color = ZTextPrimary, fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("اسم المنهج") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ZSurfaceVariant, unfocusedContainerColor = ZSurfaceVariant,
                        focusedBorderColor = ZAmber, unfocusedBorderColor = ZBorder,
                        focusedTextColor = ZTextPrimary, unfocusedTextColor = ZTextPrimary,
                    ),
                )
                Spacer(Modifier.height(12.dp))
                Text("نوع المحتوى", color = ZTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                ) {
                    CourseType.values().forEach { t ->
                        val selected = type == t
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (selected) ZIndigo else ZSurfaceVariant,
                            onClick = { type = t },
                        ) {
                            Text(
                                t.label,
                                color = if (selected) Color.White else ZTextSecondary,
                                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("المستوى", color = ZTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                ) {
                    academies.forEach { lv ->
                        val selected = levelId == lv.id
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (selected) ZIndigo else ZSurfaceVariant,
                            onClick = { levelId = lv.id },
                        ) {
                            Text(
                                "${lv.emoji} ${lv.name}",
                                color = if (selected) Color.White else ZTextSecondary,
                                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                    val selectedNew = levelId >= 4
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (selectedNew) ZAmber else ZSurfaceVariant,
                        onClick = { levelId = nextCustomId },
                    ) {
                        Text(
                            "🎯 مسار تخصصي جديد",
                            color = if (selectedNew) Color.Black else ZTextSecondary,
                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                if (levelId >= 4) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customLevelName, onValueChange = { customLevelName = it },
                        label = { Text("اسم المسار (مثلاً: إنجليزية المساحة والطرق)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ZSurfaceVariant, unfocusedContainerColor = ZSurfaceVariant,
                            focusedBorderColor = ZAmber, unfocusedBorderColor = ZBorder,
                            focusedTextColor = ZTextPrimary, unfocusedTextColor = ZTextPrimary,
                        ),
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = target, onValueChange = { target = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("الهدف: عدد دروس المنهج") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ZSurfaceVariant, unfocusedContainerColor = ZSurfaceVariant,
                        focusedBorderColor = ZAmber, unfocusedBorderColor = ZBorder,
                        focusedTextColor = ZTextPrimary, unfocusedTextColor = ZTextPrimary,
                    ),
                )
                status?.let { (ok, msg) ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        msg,
                        color = if (ok) ZEmeraldDeep else ZRoseDeep,
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    vm.createCustomCourse(
                        name = name,
                        type = type,
                        levelId = levelId,
                        levelName = customLevelName,
                        target = target.toIntOrNull() ?: 20,
                    ) { ok, msg ->
                        status = ok to msg
                        if (ok) onDismiss()
                    }
                },
                enabled = name.isNotBlank() && levelId > 0 && (levelId < 4 || customLevelName.isNotBlank()),
            ) { Text("إنشاء المنهج", color = ZAmberDeep, fontWeight = FontWeight.Black) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء", color = ZTextSecondary) }
        },
    )
}

/* ─────────────────────────── الرأس العام ─────────────────────────── */

@Composable
private fun OverallHeader(vm: AppViewModel) {
    val pct = vm.overallCompletion
    val animated by animateFloatAsState(pct, tween(700), label = "overall")
    val totalLessons = vm.courses.sumOf { vm.courseTotal(it.id) }
    val doneLessons = vm.courses.sumOf { vm.courseDone(it.id) }

    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(ZIndigo, ZPurple)))
            .drawBehind {
                drawCircle(
                    Color.White.copy(alpha = 0.08f),
                    radius = size.minDimension * 0.6f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.95f, -size.height * 0.1f),
                )
            }
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("المستويات والمناهج", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.16f)) {
                    Text(
                        "$doneLessons / $totalLessons درساً",
                        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${(pct * 100).toInt()}%",
                    color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("من رحلتك الكاملة", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("افتح مستوى لتصفّح كورساته", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = Color.White, trackColor = Color.White.copy(alpha = 0.25f),
            )
        }
    }
}

/* ─────────────────────────── بطاقة المستوى ─────────────────────────── */

@Composable
private fun LevelCard(
    levelId: Int,
    name: String,
    description: String,
    emoji: String,
    stats: AppViewModel.LevelStats,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val arrow by animateFloatAsState(if (expanded) 90f else 0f, tween(250), label = "arrow")
    val doneAnim by animateFloatAsState(stats.completion, tween(650), label = "done")
    val covAnim by animateFloatAsState(stats.coverage, tween(650), label = "cov")

    SoftCard(modifier = Modifier.fillMaxWidth(), radius = 20.dp) {
        Column {
            Surface(color = Color.Transparent, onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(listOf(ZIndigo, ZPurple))),
                            contentAlignment = Alignment.Center,
                        ) { Text(emoji, fontSize = 22.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "المستوى $levelId · $name",
                                color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                description.ifBlank { "${stats.courseCount} كورس" },
                                color = ZTextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        // النسبة الصادقة مقابل المنهج الكامل
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
                            modifier = Modifier.size(20.dp).rotate(-arrow),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    // شريط واحد بطبقتين: الإنجاز (أخضر) فوق المتوفر (فيروزي شفاف)
                    Box(
                        Modifier.fillMaxWidth().height(8.dp)
                            .clip(RoundedCornerShape(4.dp)).background(ZBorder)
                    ) {
                        Box(
                            Modifier.fillMaxWidth(covAnim).fillMaxHeight()
                                .background(ZCyanDeep.copy(alpha = 0.40f))
                        )
                        Box(
                            Modifier.fillMaxWidth(doneAnim).fillMaxHeight()
                                .background(ZEmerald)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(ZEmerald))
                        Spacer(Modifier.width(4.dp))
                        Text("الإنجاز ${stats.done}/${stats.total}", color = ZTextMuted, fontSize = 9.sp)
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.size(6.dp).clip(CircleShape).background(ZCyanDeep.copy(alpha = 0.5f)))
                        Spacer(Modifier.width(4.dp))
                        Text("المتوفر ${stats.imported}", color = ZTextMuted, fontSize = 9.sp)
                        Spacer(Modifier.weight(1f))
                        if (stats.imported > 0 && stats.done < stats.imported) {
                            Text(
                                "${(stats.completionOfImported * 100).toInt()}% مما لديك",
                                color = ZTextMuted, fontSize = 9.sp,
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(260)) + fadeIn(tween(260)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(140)),
            ) {
                Column {
                    HorizontalDivider(color = ZBorder)
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        content = content,
                    )
                }
            }
        }
    }
}

/* ─────────────────────────── بطاقة الكورس ─────────────────────────── */

/**
 * بطاقة كورس مضغوطة لشبكة العمودين — لمسة واحدة تفتح الكورس.
 * تعرض: الأيقونة/الاسم · التقدّم x/y · شريطاً رفيعاً · وتلميح الدرس التالي.
 */
@Composable
private fun CourseTile(
    course: Course,
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    val accent = Color(course.accent)
    val done = vm.courseDone(course.id)
    val imported = vm.courseImported(course.id)
    val total = vm.courseTotal(course.id)
    val completion = vm.courseCompletion(course.id)
    val anim by animateFloatAsState(completion, tween(600), label = "cp")
    val finished = total > 0 && done >= total
    val nextLesson = vm.lessons
        .filter { it.courseId == course.id }
        .sortedBy { it.no }
        .firstOrNull { !it.isCompleted }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = ZSurfaceVariant,
        onClick = onOpen,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = if (imported > 0) 0.20f else 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (finished) {
                        Icon(Icons.Filled.Verified, null, tint = accent, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(courseIcon(course.type.icon), null, tint = accent, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        course.name,
                        color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            imported == 0 -> "غير مستورد · $total درساً"
                            else -> "$done/$total درساً"
                        },
                        color = if (imported == 0) ZTextMuted else ZTextSecondary,
                        fontSize = 10.sp,
                    )
                }
                Text(
                    "${(completion * 100).toInt()}%",
                    color = if (done > 0) accent else ZTextMuted,
                    fontWeight = FontWeight.Black, fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { anim },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)),
                color = accent, trackColor = ZBorder,
            )
            // تلميح الاستئناف: الدرس التالي أو حالة الاكتمال
            if (imported > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        finished -> "اكتمل الكورس ✓"
                        nextLesson != null -> "التالي: ${nextLesson.title}"
                        else -> ""
                    },
                    color = if (finished) ZEmeraldDeep else accent,
                    fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/* ─────────────────────────── أدوات مشتركة ─────────────────────────── */

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
