package com.zmastery.english.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.data.Lesson
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel
import kotlin.math.roundToInt

/**
 * Lesson review = self-assessed spaced repetition for whole lessons.
 * Flow: list of due lessons → open one → re-read content →
 * rate "كم تتذكر من هذا الدرس؟" (0-100%) + tick forgotten key words →
 * schedule next review based on mastery.
 */
@Composable
fun LessonReviewScreen(vm: AppViewModel) {
    com.zmastery.english.ui.components.TrackStudyTime(vm, "lessonReview")
    var activeLessonId by remember { mutableStateOf<Int?>(null) }
    var justReviewed by remember { mutableStateOf(false) }

    val active = activeLessonId?.let { id -> vm.lessons.firstOrNull { it.id == id } }
    if (active != null) {
        LessonReviewDetail(
            vm = vm,
            lesson = active,
            onDone = { activeLessonId = null; justReviewed = true },
            onBack = { activeLessonId = null },
        )
        return
    }

    val due = vm.lessonsToReview
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(ZCyanDeep, ZSage()))).padding(20.dp)
            ) {
                Column {
                    Text("مراجعة الدروس", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "أعد قراءة الدروس المستحقة وقيّم مدى تذكّرك. كلما تذكّرت أكثر، تباعدت المراجعة القادمة.",
                        color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp,
                    )
                }
            }
        }
        if (justReviewed && due.isEmpty()) {
            item { AllReviewedCard() }
        }
        if (due.isEmpty()) {
            item { NoLessonsDue(vm) }
        } else {
            items(due, key = { it.id }) { lesson ->
                LessonReviewRow(vm, lesson) { activeLessonId = lesson.id }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

private fun ZSage() = ZCyan

@Composable
private fun LessonReviewRow(vm: AppViewModel, lesson: Lesson, onClick: () -> Unit) {
    val course = vm.courses.firstOrNull { it.id == lesson.courseId }
    Surface(shape = RoundedCornerShape(16.dp), color = ZCard, shadowElevation = 5.dp, modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(12.dp))
                    .background(Color(course?.accent ?: 0xFFE07856).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Autorenew, null, tint = Color(course?.accent ?: 0xFFE07856)) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(lesson.title, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    buildString {
                        append(course?.name ?: "")
                        if (lesson.reviewCount > 0) append(" • راجعته ${lesson.reviewCount} مرة")
                        else append(" • مراجعة أولى")
                    },
                    color = ZTextSecondary, fontSize = 12.sp,
                )
            }
            Surface(shape = RoundedCornerShape(8.dp), color = ZRose.copy(alpha = 0.14f)) {
                Text("مستحق", color = ZRose, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
    }
}

@Composable
private fun LessonReviewDetail(vm: AppViewModel, lesson: Lesson, onDone: () -> Unit, onBack: () -> Unit) {
    var phase by remember { mutableStateOf(0) } // 0 = read, 1 = rate
    var mastery by remember { mutableStateOf(70f) }
    val words = remember(lesson.id) { vm.vocab.filter { it.id in lesson.newWordIds } }
    val forgotten = remember(lesson.id) { mutableStateListOf<Int>() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowForward, null, tint = ZIndigo); Spacer(Modifier.width(8.dp)); Text("رجوع", color = ZIndigo)
        }

        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(ZIndigo, ZPurple))).padding(20.dp)) {
            Column {
                Text("مراجعة الدرس", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(lesson.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
        }

        AnimatedContent(phase, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }, label = "phase") { p ->
            if (p == 0) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (lesson.readingEn.isNotBlank()) {
                        ReviewSection("النص", Icons.Filled.AutoStories, ZCyanDeep) {
                            Text(lesson.readingEn, color = ZTextPrimary, fontSize = 16.sp, lineHeight = 26.sp)
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = ZBorder)
                            Spacer(Modifier.height(12.dp))
                            Text(lesson.readingAr, color = ZTextSecondary, fontSize = 14.sp, lineHeight = 24.sp)
                        }
                    }
                    if (lesson.keyPoints.isNotEmpty()) {
                        ReviewSection("النقاط الرئيسية", Icons.Filled.Lightbulb, ZAmber) {
                            lesson.keyPoints.forEach {
                                Row(Modifier.padding(vertical = 4.dp)) {
                                    Text("• ", color = ZAmber, fontWeight = FontWeight.Bold)
                                    Text(it, color = ZTextPrimary, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                    Button(
                        onClick = { phase = 1 },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ZIndigo),
                    ) {
                        Text("انتهيت من المراجعة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Mastery self-rating
                    ReviewSection("كم تتذكّر من هذا الدرس؟", Icons.Filled.Insights, ZEmerald) {
                        Text("${mastery.roundToInt()}%", color = masteryColor(mastery), fontSize = 40.sp, fontWeight = FontWeight.Black, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        Text(masteryLabel(mastery), color = ZTextSecondary, fontSize = 13.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        Slider(
                            value = mastery, onValueChange = { mastery = it },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(thumbColor = masteryColor(mastery), activeTrackColor = masteryColor(mastery), inactiveTrackColor = ZBorder),
                        )
                        Text("سيتم جدولة المراجعة القادمة حسب تقييمك", color = ZTextMuted, fontSize = 11.sp)
                    }
                    // Forgotten words
                    if (words.isNotEmpty()) {
                        ReviewSection("ما الكلمات التي نسيتها؟", Icons.Filled.HighlightOff, ZRose) {
                            Text("اختر الكلمات التي لم تتذكرها لتعود لقائمة مراجعة الكلمات", color = ZTextSecondary, fontSize = 12.sp)
                            Spacer(Modifier.height(12.dp))
                            words.forEach { w ->
                                val on = w.id in forgotten
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (on) ZRose.copy(alpha = 0.12f) else ZSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    onClick = { if (on) forgotten.remove(w.id) else forgotten.add(w.id) },
                                ) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(w.english, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text(w.arabic, color = ZTextSecondary, fontSize = 12.sp)
                                        }
                                        Icon(
                                            if (on) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                            null, tint = if (on) ZRose else ZTextMuted,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Button(
                        onClick = { vm.reviewLesson(lesson.id, mastery.roundToInt(), forgotten.toList()); onDone() },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ZEmerald),
                    ) {
                        Icon(Icons.Filled.Check, null); Spacer(Modifier.width(8.dp))
                        Text("حفظ التقييم", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

private fun masteryColor(m: Float) = when { m < 50 -> ZRose; m < 80 -> ZAmber; else -> ZEmerald }
private fun masteryLabel(m: Float) = when {
    m < 30 -> "أحتاج إعادة الدرس بالكامل"
    m < 50 -> "أتذكّر القليل — مراجعة قريبة"
    m < 80 -> "أتذكّر معظمه — جيد"
    else -> "أتقنته تماماً \uD83C\uDF1F"
}

@Composable
private fun ReviewSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 5.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun NoLessonsDue(vm: AppViewModel) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 50.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.TaskAlt, null, tint = ZEmerald, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            if (vm.completedLessons == 0) "أكمل درساً أولاً" else "لا دروس مستحقة للمراجعة الآن",
            color = ZTextPrimary, fontWeight = FontWeight.Bold,
        )
        Text(
            if (vm.completedLessons == 0) "ستظهر الدروس هنا بعد إكمالها" else "عُد لاحقاً حين تحين مواعيد المراجعة",
            color = ZTextMuted, fontSize = 12.sp, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AllReviewedCard() {
    Surface(shape = RoundedCornerShape(16.dp), color = ZEmerald.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Celebration, null, tint = ZEmerald)
            Spacer(Modifier.width(12.dp))
            Text("رائع! راجعت كل الدروس المستحقة", color = ZEmerald, fontWeight = FontWeight.Bold)
        }
    }
}
