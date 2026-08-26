package com.zmastery.english.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.data.SampleData
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel

@Composable
fun CourseScreen(vm: AppViewModel, courseId: Int, onOpenLesson: (Int) -> Unit, onOpenPhonetics: () -> Unit = {}) {
    val course = vm.courses.first { it.id == courseId }
    val lessons = vm.lessons.filter { it.courseId == courseId }.sortedBy { it.no }
    val done = vm.courseDone(courseId)
    val total = vm.courseTotal(courseId)
    val imported = vm.courseImported(courseId)
    val completion = vm.courseCompletion(courseId)
    val showPhoneticsSample = course.style == com.zmastery.english.data.LessonStyle.PHONETICS_SOUNDS && lessons.isEmpty()
    // علامة «تم الرفع» تظهر للمسؤول فقط — الطالب لا يحتاجها.
    val showPublishMark = vm.isAdmin
    val publishedCount = lessons.count { it.isPublishedToCloud }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 5.dp, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color(course.accent).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Icon(courseIcon(course.type.icon), null, tint = Color(course.accent), modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(course.name, color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text(
                            "${course.type.label} • $done مكتمل من $total درساً في المنهج",
                            color = ZTextSecondary, fontSize = 12.sp,
                        )
                        if (imported < total) {
                            Text(
                                "المتوفر لديك: $imported درساً",
                                color = ZTextMuted, fontSize = 10.sp,
                            )
                        }
                        if (showPublishMark && lessons.isNotEmpty()) {
                            // حالة النشر السحابي لهذا الكورس — خضراء عند الاكتمال.
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (publishedCount == lessons.size) Icons.Filled.TaskAlt else Icons.Filled.CloudUpload,
                                    null,
                                    tint = if (publishedCount == lessons.size) ZEmerald else ZAmber,
                                    modifier = Modifier.size(12.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (publishedCount == lessons.size)
                                        "كل دروس هذا الكورس مرفوعة سحابياً ✓"
                                    else
                                        "مرفوع سحابياً: $publishedCount من ${lessons.size}",
                                    color = if (publishedCount == lessons.size) ZEmerald else ZAmberDeep,
                                    fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LinearProgressIndicator(
                                progress = { completion },
                                modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(4.dp)),
                                color = Color(course.accent), trackColor = ZBorder,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${(completion * 100).toInt()}%",
                                color = Color(course.accent), fontSize = 11.sp, fontWeight = FontWeight.Black,
                            )
                        }
                    }
                }
            }
        }
        if (showPhoneticsSample) {
            item {
                Text("درس تجريبي (معاينة التصميم)", color = ZTextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = ZCard,
                    shadowElevation = 5.dp,
                    onClick = onOpenPhonetics,
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(course.accent).copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) { Text("2", color = Color(course.accent), fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("الفرق بين أصوات A و E و I", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("أصوات · أزواج صغرى · تدريب · اختبار", color = ZTextSecondary, fontSize = 12.sp, maxLines = 1)
                        }
                        Icon(Icons.Filled.ChevronLeft, null, tint = ZTextMuted)
                    }
                }
            }
        } else if (lessons.isEmpty()) {
            item { EmptyLessons() }
        } else {
            items(lessons, key = { it.id }) { lesson ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = ZCard,
                    shadowElevation = 5.dp,
                    onClick = { onOpenLesson(lesson.id) },
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (lesson.isCompleted) ZEmerald.copy(alpha = 0.18f) else ZBorder.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (lesson.isCompleted) Icon(Icons.Filled.Check, null, tint = ZEmerald)
                            else Text("${lesson.no}", color = ZTextSecondary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(lesson.title, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(lesson.summaryAr, color = ZTextSecondary, fontSize = 12.sp, maxLines = 1)
                        }
                        // علامة «تم الرفع» لكل درس — للمسؤول فقط.
                        if (showPublishMark) {
                            Icon(
                                if (lesson.isPublishedToCloud) Icons.Filled.TaskAlt else Icons.Filled.CloudOff,
                                if (lesson.isPublishedToCloud) "مرفوع سحابياً" else "غير مرفوع بعد",
                                tint = if (lesson.isPublishedToCloud) ZEmerald else ZTextMuted,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Icon(Icons.Filled.ChevronLeft, null, tint = ZTextMuted)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun EmptyLessons() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Inventory2, null, tint = ZTextMuted, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text("لا توجد دروس بعد", color = ZTextSecondary, fontWeight = FontWeight.Bold)
        Text("سيتم إضافة الدروس قريباً", color = ZTextMuted, fontSize = 12.sp)
    }
}
