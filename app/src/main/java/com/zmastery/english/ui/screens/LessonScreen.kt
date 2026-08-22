package com.zmastery.english.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.data.VocabWord
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel

@Composable
fun LessonScreen(vm: AppViewModel, lessonId: Int, onOpenQuiz: (Int) -> Unit = {}) {
    com.zmastery.english.ui.components.TrackStudyTime(vm, "lesson")
    val lesson = vm.lessons.firstOrNull { it.id == lessonId } ?: return
    val course = vm.courses.firstOrNull { it.id == lesson.courseId }
    val accent = Color(course?.accent ?: 0xFFE07856)
    var showConfirm by remember { mutableStateOf(false) }
    var showApproval by remember { mutableStateOf(false) }
    var showMentalLink by remember { mutableStateOf(false) }
    // Two-step un-complete: confirm the undo, then decide about the words.
    var showUndo by remember { mutableStateOf(false) }
    var showUndoWords by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    val onCompleteClick = {
        if (!lesson.isCompleted) showConfirm = true else showUndo = true
    }

    // ---- Route to the course-specific lesson viewer ----
    when (course?.style) {
        com.zmastery.english.data.LessonStyle.VOCAB_CARDS,
        com.zmastery.english.data.LessonStyle.IDIOMS -> {
            com.zmastery.english.ui.screens.lessons.VocabCardsLesson(
                vm = vm,
                lesson = lesson,
                accent = accent,
                onComplete = onCompleteClick,
                onGenerateMentalLink = { showMentalLink = true },
                onOpenQuiz = { onOpenQuiz(lessonId) },
            )
        }
        com.zmastery.english.data.LessonStyle.GRAMMAR_RULES,
        com.zmastery.english.data.LessonStyle.EXAM_PREP -> {
            com.zmastery.english.ui.screens.lessons.GrammarLesson(
                vm = vm,
                lesson = lesson,
                accent = accent,
                onComplete = onCompleteClick,
                onGenerateMentalLink = { showMentalLink = true },
                onOpenQuiz = { onOpenQuiz(lessonId) },
            )
        }
        com.zmastery.english.data.LessonStyle.CONVERSATION -> {
            com.zmastery.english.ui.screens.lessons.ConversationLesson(
                vm = vm,
                lesson = lesson,
                accent = accent,
                onComplete = onCompleteClick,
                onGenerateMentalLink = { showMentalLink = true },
                onOpenQuiz = { onOpenQuiz(lessonId) },
            )
        }
        com.zmastery.english.data.LessonStyle.READING_TEXT,
        com.zmastery.english.data.LessonStyle.CULTURE,
        com.zmastery.english.data.LessonStyle.STORY,
        com.zmastery.english.data.LessonStyle.NEWS,
        com.zmastery.english.data.LessonStyle.THINKING -> {
            com.zmastery.english.ui.screens.lessons.ReadingLesson(
                vm = vm,
                lesson = lesson,
                accent = accent,
                style = course?.style ?: com.zmastery.english.data.LessonStyle.READING_TEXT,
                onComplete = onCompleteClick,
                onGenerateMentalLink = { showMentalLink = true },
                onOpenQuiz = { onOpenQuiz(lessonId) },
            )
        }
        com.zmastery.english.data.LessonStyle.PHONETICS_SOUNDS -> {
            val ph = remember(lesson.rawJson) {
                if (lesson.rawJson.isNotBlank()) com.zmastery.english.data.PhoneticsParser.parse(lesson.rawJson) else null
            }
            if (ph != null && ph.content.focusSounds.isNotEmpty()) {
                com.zmastery.english.ui.screens.PhoneticsLessonScreen(ph, onComplete = onCompleteClick, isCompleted = lesson.isCompleted)
            } else {
                GenericLesson(vm, lesson, accent, onCompleteClick) { showMentalLink = true }
            }
        }
        else -> GenericLesson(vm, lesson, accent, onCompleteClick) { showMentalLink = true }
    }

    LessonDialogs(
        vm = vm, lesson = lesson, lessonId = lessonId, clipboard = clipboard,
        showConfirm = showConfirm, onConfirmChange = { showConfirm = it },
        showApproval = showApproval, onApprovalChange = { showApproval = it },
        showMentalLink = showMentalLink, onMentalLinkChange = { showMentalLink = it },
    )

    // ── Step 1 · confirm the undo ──
    if (showUndo) {
        AlertDialog(
            onDismissRequest = { showUndo = false },
            containerColor = ZSurface,
            icon = { Icon(Icons.Filled.Undo, null, tint = ZAmber) },
            title = { Text("التراجع عن الإكمال؟", color = ZTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "سيعود «${lesson.title}» إلى حالة «غير مكتمل»، وسيُخصم 25 نقطة خبرة مُنحت عند إكماله.",
                    color = ZTextSecondary, fontSize = 14.sp, lineHeight = 21.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showUndo = false
                    // Only ask about words when this lesson actually added some.
                    if (vm.wordsFromLesson(lesson.id).isNotEmpty()) showUndoWords = true
                    else vm.uncompleteLesson(lesson.id, alsoRemoveWords = false)
                }) { Text("نعم، تراجع", color = ZAmber, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showUndo = false }) { Text("إلغاء", color = ZTextSecondary) }
            },
        )
    }

    // ── Step 2 · what about the words this lesson added? ──
    if (showUndoWords) {
        val words = vm.wordsFromLesson(lesson.id)
        AlertDialog(
            onDismissRequest = { showUndoWords = false },
            containerColor = ZSurface,
            icon = { Icon(Icons.Filled.DeleteSweep, null, tint = ZRose) },
            title = { Text("حذف كلمات الدرس من القاموس؟", color = ZTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "أضاف هذا الدرس ${words.size} كلمة للقاموس. هل تريد حذفها أيضاً؟",
                        color = ZTextSecondary, fontSize = 14.sp, lineHeight = 21.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = ZSurfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            words.take(6).forEach { w ->
                                Text("• ${w.english} — ${w.arabic}", color = ZTextMuted, fontSize = 12.sp, lineHeight = 19.sp)
                            }
                            if (words.size > 6) {
                                Text("و${words.size - 6} كلمة أخرى…", color = ZTextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "سيتم حذف تقدّم المراجعة وصور الروابط الذهنية الخاصة بها. لا يمكن التراجع.",
                        color = ZRose, fontSize = 11.sp, lineHeight = 17.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.uncompleteLesson(lesson.id, alsoRemoveWords = true)
                    showUndoWords = false
                }) { Text("احذف الكلمات", color = ZRose, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.uncompleteLesson(lesson.id, alsoRemoveWords = false)
                    showUndoWords = false
                }) { Text("أبقِها في القاموس", color = ZEmerald, fontWeight = FontWeight.Bold) }
            },
        )
    }
}

@Composable
private fun GenericLesson(
    vm: AppViewModel,
    lesson: com.zmastery.english.data.Lesson,
    accent: Color,
    onComplete: () -> Unit,
    onMentalLink: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.72f)))).padding(20.dp)
        ) {
            Column {
                Text("الدرس ${lesson.no}", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(lesson.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                if (lesson.summaryAr.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(lesson.summaryAr, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                }
            }
        }

        if (lesson.keySentences.isNotEmpty()) {
            SectionCard("الجمل الأساسية", Icons.Filled.FormatQuote, accent) {
                lesson.keySentences.forEach { s ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(s.en, color = ZTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(s.ar, color = ZTextSecondary, fontSize = 13.sp)
                    }
                }
            }
        }

        if (lesson.readingEn.isNotBlank() && lesson.keySentences.isEmpty()) {
            SectionCard("نص القراءة", Icons.Filled.AutoStories, ZCyan) {
                Text(lesson.readingEn, color = ZTextPrimary, fontSize = 16.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                Divider(color = ZBorder)
                Spacer(Modifier.height(10.dp))
                Text(lesson.readingAr, color = ZTextSecondary, fontSize = 14.sp, lineHeight = 24.sp)
            }
        }

        if (lesson.dialogues.isNotEmpty()) {
            SectionCard("الحوار", Icons.Filled.Forum, ZRose) {
                lesson.dialogues.forEach { d ->
                    Column(Modifier.padding(vertical = 5.dp)) {
                        Text(d.speaker, color = ZRose, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(d.en, color = ZTextPrimary, fontSize = 14.sp)
                        Text(d.ar, color = ZTextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        if (lesson.keyPoints.isNotEmpty()) {
            SectionCard("النقاط الرئيسية", Icons.Filled.Lightbulb, ZAmber) {
                lesson.keyPoints.forEach { point ->
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(ZAmber))
                        Spacer(Modifier.width(10.dp))
                        Text(point, color = ZTextPrimary, fontSize = 14.sp)
                    }
                }
            }
        }

        if (lesson.notes.isNotEmpty()) {
            SectionCard("ملاحظات الدرس", Icons.Filled.Lightbulb, ZAmber) {
                lesson.notes.forEach { note ->
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(ZAmber).align(Alignment.CenterVertically))
                        Spacer(Modifier.width(10.dp))
                        Text(note, color = ZTextPrimary, fontSize = 14.sp, lineHeight = 22.sp)
                    }
                }
            }
        }

        // Mental link generator
        Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 5.dp, modifier = Modifier.fillMaxWidth(), onClick = onMentalLink) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(ZPurple, ZIndigo))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("توليد رابط ذهني", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("انسخ مطالبة الذكاء الاصطناعي لتوليد الصور", color = ZTextSecondary, fontSize = 12.sp)
                }
                Icon(Icons.Filled.ContentCopy, null, tint = ZCyan)
            }
        }

        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (lesson.isCompleted) ZEmerald else accent),
        ) {
            Icon(if (lesson.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.Check, null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text(if (lesson.isCompleted) "تم الإكمال ✓ (اضغط للتراجع)" else "إكمال الدرس", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun LessonDialogs(
    vm: AppViewModel,
    lesson: com.zmastery.english.data.Lesson,
    lessonId: Int,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    showConfirm: Boolean, onConfirmChange: (Boolean) -> Unit,
    showApproval: Boolean, onApprovalChange: (Boolean) -> Unit,
    showMentalLink: Boolean, onMentalLinkChange: (Boolean) -> Unit,
) {
    // Confirmation dialog
    if (showConfirm) {
        val pending = vm.pendingWordsForLesson(lesson.id)
        AlertDialog(
            onDismissRequest = { onConfirmChange(false) },
            containerColor = ZSurface,
            icon = { Icon(Icons.Filled.HelpOutline, null, tint = ZCyan) },
            title = { Text("إكمال الدرس؟", color = ZTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (pending.isNotEmpty())
                        "هل أنهيت هذا الدرس؟ ستظهر ${pending.size} كلمة للموافقة عليها قبل إضافتها للقاموس."
                    else "هل أنهيت هذا الدرس؟",
                    color = ZTextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onConfirmChange(false)
                    if (pending.isNotEmpty()) onApprovalChange(true) else vm.toggleLesson(lesson.id)
                }) { Text("نعم", color = ZEmerald, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { onConfirmChange(false) }) { Text("لا", color = ZTextSecondary) } },
        )
    }

    // Word approval sheet
    if (showApproval) {
        WordApprovalDialog(
            words = vm.pendingWordsForLesson(lesson.id),
            onConfirm = { approvedIds ->
                vm.approveWords(lesson.id, approvedIds)
                onApprovalChange(false)
                vm.toggleLesson(lesson.id)
            },
            onDismiss = { onApprovalChange(false) },
        )
    }

    // Mental link dialog
    if (showMentalLink) {
        val prompt = buildMentalLinkPrompt(vm, lessonId)
        AlertDialog(
            onDismissRequest = { onMentalLinkChange(false) },
            containerColor = ZSurface,
            icon = { Icon(Icons.Filled.AutoAwesome, null, tint = ZPurple) },
            title = { Text("مطالبة الرابط الذهني", color = ZTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("انسخ هذه المطالبة والصقها في نموذج توليد الصور الخارجي. الصورة المركبة الناتجة ستُقص تلقائياً وتُربط بالكلمات.", color = ZTextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = ZSurfaceVariant) {
                        Text(prompt, color = ZTextPrimary, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(prompt)); onMentalLinkChange(false) }) {
                    Icon(Icons.Filled.ContentCopy, null, tint = ZCyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp)); Text("نسخ", color = ZCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { onMentalLinkChange(false) }) { Text("إغلاق", color = ZTextSecondary) } },
        )
    }
}

private fun buildMentalLinkPrompt(vm: AppViewModel, lessonId: Int): String {
    val lesson = vm.lessons.first { it.id == lessonId }
    val words = vm.vocab.filter { it.id in lesson.newWordIds }
    val course = vm.courses.firstOrNull { it.id == lesson.courseId }
    return when (course?.type?.name) {
        "READING" -> "Create a single illustrative image for this story: \"${lesson.readingEn.take(120)}\". Clear, memorable, educational style."
        "GRAMMAR", "CONVERSATION" -> "Create an expressive illustration representing: ${lesson.title}. Simple, clear educational style."
        else -> buildString {
            append("Create ONE composite image (grid, fixed cells for later cropping) showing each word + its example:\n")
            if (words.isEmpty()) append("- ${lesson.title}")
            else words.forEach { append("- ${it.english} (${it.arabic}): \"${it.exampleEn}\" — mental image: ${it.mentalImage}\n") }
            append("Style: vivid, memorable, one clear cell per word, equal-sized cells.")
        }
    }
}

@Composable
private fun WordApprovalDialog(words: List<VocabWord>, onConfirm: (Set<Int>) -> Unit, onDismiss: () -> Unit) {
    val approved = remember { mutableStateListOf<Int>().apply { addAll(words.map { it.id }) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ZSurface,
        icon = { Icon(Icons.Filled.FactCheck, null, tint = ZEmerald) },
        title = { Text("موافقة الكلمات", color = ZTextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text("راجع الكلمات المولّدة قبل إضافتها للقاموس. أزل الأخطاء بالنقر.", color = ZTextSecondary, fontSize = 12.sp)
                }
                items(words.size, key = { words[it].id }) { i ->
                    val w = words[i]
                    val isOn = w.id in approved
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isOn) ZEmerald.copy(alpha = 0.08f) else ZSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isOn) ZEmerald.copy(alpha = 0.4f) else ZBorder),
                        onClick = { if (isOn) approved.remove(w.id) else approved.add(w.id) },
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            com.zmastery.english.audio.AudioButton(
                                text = if (w.exampleEn.isNotBlank()) "${w.english}. ${w.exampleEn}" else w.english,
                                audioKey = "approve_${w.id}",
                                accent = ZCyanDeep,
                                size = 38.dp, iconSize = 18.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(w.english, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    if (w.phonetic.isNotBlank()) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(w.phonetic, color = ZCyanDeep, fontSize = 11.sp)
                                    }
                                }
                                Text(w.arabic, color = ZTextSecondary, fontSize = 13.sp)
                                if (w.exampleEn.isNotBlank()) {
                                    Text(w.exampleEn, color = ZTextMuted, fontSize = 11.sp, maxLines = 1)
                                }
                            }
                            Icon(
                                if (isOn) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                null, tint = if (isOn) ZEmerald else ZTextMuted,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(approved.toSet()) }) {
                Text("تأكيد وإضافة (${approved.size})", color = ZEmerald, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = ZTextSecondary) } },
    )
}

@Composable
private fun SectionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 5.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
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
