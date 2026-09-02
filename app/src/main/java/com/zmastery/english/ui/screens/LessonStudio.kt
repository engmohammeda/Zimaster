package com.zmastery.english.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zmastery.english.data.*
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel
import kotlinx.coroutines.launch

/**
 * Complete Studio for creating English lessons & curricula:
 *  1. AI-Powered Generation Studio (Personas, Custom Prompts, Single/Full Curricula).
 *  2. Direct Manual Authoring (Vocabulary, Dialogues, Grammar, Reading, Quizzes).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonStudioDialog(
    vm: AppViewModel,
    targetCourse: Course? = null,
    onDismiss: () -> Unit,
    onLessonCreated: (String) -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ZBackground
        ) {
            LessonStudioContent(
                vm = vm,
                targetCourse = targetCourse,
                onClose = onDismiss,
                onSuccess = { msg ->
                    onLessonCreated(msg)
                    onDismiss()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LessonStudioContent(
    vm: AppViewModel,
    targetCourse: Course?,
    onClose: () -> Unit,
    onSuccess: (String) -> Unit
) {
    var mode by remember { mutableStateOf(0) } // 0 = AI Generator · 1 = Direct Authoring

    Scaffold(
        topBar = {
            Surface(
                color = ZSurface,
                shadowElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.Close, "إغلاق", tint = ZTextPrimary)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            Text(
                                "استوديو بناء الدروس والمناهج",
                                color = ZTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                if (targetCourse != null) "إضافة إلى: ${targetCourse.name}" else "توليد أو كتابة مباشرة لمنهجك",
                                color = ZTextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Mode switch tabs
                    TabRow(
                        selectedTabIndex = mode,
                        containerColor = ZSurface,
                        contentColor = ZAmber,
                        divider = {}
                    ) {
                        Tab(
                            selected = mode == 0,
                            onClick = { mode = 0 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("توليد بالذكاء الاصطناعي", fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                        Tab(
                            selected = mode == 1,
                            onClick = { mode = 1 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.EditNote, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("كتابة مباشرة يدوية", fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (mode == 0) {
                AiGeneratorView(
                    vm = vm,
                    targetCourse = targetCourse,
                    onSuccess = onSuccess
                )
            } else {
                DirectAuthoringView(
                    vm = vm,
                    targetCourse = targetCourse,
                    onSuccess = onSuccess
                )
            }
        }
    }
}

// ==============================================================================
// 1) AI GENERATOR VIEW (Personas, Prompts, Curriculum / Single Lesson)
// ==============================================================================

@Composable
private fun AiGeneratorView(
    vm: AppViewModel,
    targetCourse: Course?,
    onSuccess: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var genScope by remember { mutableStateOf(0) } // 0 = Single Lesson · 1 = Full Curriculum Course
    var selectedPersona by remember { mutableStateOf(AiLessonService.builtinPersonas.first()) }
    var topic by remember { mutableStateOf("") }
    var level by remember { mutableStateOf(targetCourse?.levelId ?: 1) }
    var courseType by remember { mutableStateOf(targetCourse?.type ?: CourseType.VOCABULARY) }
    var lessonCount by remember { mutableStateOf(3) }
    var customInstructions by remember { mutableStateOf("") }

    var isGenerating by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var generatedLessonPkg by remember { mutableStateOf<LessonPackage?>(null) }
    var generatedCoursePkg by remember { mutableStateOf<CoursePackage?>(null) }

    val key = vm.activeKey
    val hasKey = key != null && key.rawKey.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Scope switcher (Single Lesson vs Full Course)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ZCard,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(4.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (genScope == 0) ZIndigo else Color.Transparent,
                    onClick = { genScope = 0 },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "درس واحد",
                        color = if (genScope == 0) Color.White else ZTextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (genScope == 1) ZIndigo else Color.Transparent,
                    onClick = { genScope = 1 },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "منهج / كورس كامل (عدة دروس)",
                        color = if (genScope == 1) Color.White else ZTextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            }
        }

        // Persona Selection
        Column {
            Text("اختر شخصية وأسلوب المعلم (Teacher Persona):", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(AiLessonService.builtinPersonas) { persona ->
                    val isSelected = selectedPersona.id == persona.id
                    val personaColor = Color(persona.accentColor)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) personaColor.copy(alpha = 0.18f) else ZCard,
                        border = androidx.compose.foundation.BorderStroke(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) personaColor else ZBorder
                        ),
                        onClick = { selectedPersona = persona },
                        modifier = Modifier.width(180.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(persona.avatarEmoji, fontSize = 24.sp)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(persona.nameAr, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                    Text(persona.nameEn, color = ZTextMuted, fontSize = 10.sp, maxLines = 1)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(persona.taglineAr, color = ZTextSecondary, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 2)
                        }
                    }
                }
            }
        }

        // Topic / Title Input
        OutlinedTextField(
            value = topic,
            onValueChange = { topic = it },
            label = { Text(if (genScope == 0) "موضوع الدرس (مثال: طلب الطعام في المطعم)" else "عنوان المنهج (مثال: إنجليزية المقابلات الوظيفية)") },
            placeholder = { Text("اكتب الموضوع أو الكلمات المفتاحية...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = ZCard, unfocusedContainerColor = ZCard,
                focusedBorderColor = ZAmber, unfocusedBorderColor = ZBorder,
                focusedTextColor = ZTextPrimary, unfocusedTextColor = ZTextPrimary,
            )
        )

        // Quick Topic Suggestions Chips
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val suggestions = listOf("محادثات المطار والسفر", "قواعد الأزمنة الماضية", "عبارات العمل والاجتماعات", "مفردات التكنولوجيا الحديثة", "قصة إنجليزية قصيرة A2")
            suggestions.forEach { s ->
                SuggestionChip(
                    onClick = { topic = s },
                    label = { Text(s, fontSize = 11.sp) },
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        // Level & Course Type
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Level Selector
            Column(Modifier.weight(1f)) {
                Text("المستوى التعليمي (CEFR):", color = ZTextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { lvl ->
                        val isSel = level == lvl
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) ZAmber else ZCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) ZAmber else ZBorder),
                            onClick = { level = lvl },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "A$lvl".replace("A3", "B1").replace("A4", "B2").replace("A5", "C1"),
                                color = if (isSel) Color.Black else ZTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // Lesson Count (if Full Curriculum)
            if (genScope == 1) {
                Column(Modifier.weight(0.8f)) {
                    Text("عدد الدروس: $lessonCount", color = ZTextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(2, 3, 5, 8).forEach { cnt ->
                            val isSel = lessonCount == cnt
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) ZCyanDeep else ZCard,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) ZCyanDeep else ZBorder),
                                onClick = { lessonCount = cnt },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "$cnt",
                                    color = if (isSel) Color.White else ZTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Custom Prompts / Instructions
        OutlinedTextField(
            value = customInstructions,
            onValueChange = { customInstructions = it },
            label = { Text("توجيهات ومطالبات مخصصة للذكاء الاصطناعي (اختياري)") },
            placeholder = { Text("مثال: ركز على لهجة أمريكية، أضف صورا ذهنية طريفة، اجعل القصة مشوقة...") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = ZCard, unfocusedContainerColor = ZCard,
                focusedBorderColor = ZIndigo, unfocusedBorderColor = ZBorder,
                focusedTextColor = ZTextPrimary, unfocusedTextColor = ZTextPrimary,
            )
        )

        // API Key status warning if missing
        if (!hasKey) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ZAmber.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, null, tint = ZAmber, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "تحتاج لإضافة مفتاح Gemini / OpenAI في إعدادات الذكاء الاصطناعي لتشغيل التوليد.",
                        color = ZAmber, fontSize = 11.sp, lineHeight = 16.sp
                    )
                }
            }
        }

        // Error message if any
        errorMessage?.let { err ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ZRose.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Error, null, tint = ZRose, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(err, color = ZRose, fontSize = 12.sp)
                }
            }
        }

        // Live Progress Indicator during Generation
        if (isGenerating) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ZCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, ZAmber.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = ZAmber, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        progressMessage.ifBlank { "الذكاء الاصطناعي يقوم ببناء وهندسة المحتوى…" },
                        color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("يتم توليد الكلمات، الصوتيات، القواعد، المحادثات، والتمارين بالكامل", color = ZTextMuted, fontSize = 11.sp)
                }
            }
        }

        // Generate Action Button
        Button(
            onClick = {
                if (topic.isBlank()) {
                    errorMessage = "اكتب عنوان أو موضوع الدرس أولاً"
                    return@Button
                }
                isGenerating = true
                errorMessage = null
                generatedLessonPkg = null
                generatedCoursePkg = null

                scope.launch {
                    val levelName = when (level) {
                        1 -> "A1"
                        2 -> "A2"
                        3 -> "B1"
                        4 -> "B2"
                        else -> "C1"
                    }
                    val creator = vm.aiAgents.firstOrNull {
                        it.id == if (genScope == 0) "lesson_creator" else "curriculum_builder"
                    }
                    val studioGuidance = buildString {
                        if (creator != null) {
                            appendLine(
                                AiPrompts.fill(
                                    creator.prompt,
                                    mapOf(
                                        "TOPIC" to topic,
                                        "LEVEL" to levelName,
                                        "STYLE" to selectedPersona.toneDescription,
                                        "COUNT" to lessonCount.toString(),
                                    ),
                                )
                            )
                            if (creator.character.isNotBlank()) appendLine("PERSONA: ${creator.character}")
                            if (creator.style.isNotBlank()) appendLine("TONE: ${creator.style}")
                        }
                        if (customInstructions.isNotBlank()) {
                            appendLine()
                            appendLine(customInstructions)
                        }
                    }
                    if (genScope == 0) {
                        progressMessage = "جارٍ توليد درس «$topic» عبر ${selectedPersona.nameAr}…"
                        val res = AiLessonService.generateLesson(
                            topic = topic,
                            level = level,
                            courseType = courseType,
                            lessonStyle = when (courseType) {
                                CourseType.VOCABULARY -> LessonStyle.VOCAB_CARDS
                                CourseType.GRAMMAR -> LessonStyle.GRAMMAR_RULES
                                CourseType.READING -> LessonStyle.READING_TEXT
                                CourseType.CONVERSATION -> LessonStyle.CONVERSATION
                                CourseType.PHONETICS -> LessonStyle.PHONETICS_SOUNDS
                                CourseType.WRITING -> LessonStyle.WRITING_PRACTICE
                                else -> LessonStyle.VOCAB_CARDS
                            },
                            lessonNo = (if (targetCourse != null) vm.lessons.count { it.courseId == targetCourse.id } else 0) + 1,
                            courseNameAr = targetCourse?.name ?: "كورس $topic",
                            courseId = targetCourse?.jsonId ?: "",
                            persona = selectedPersona,
                            customInstructions = studioGuidance,
                            key = key,
                            modelId = creator?.modelId.orEmpty(),
                        )
                        isGenerating = false
                        when (res) {
                            is AiLessonService.Result.Success -> {
                                generatedLessonPkg = res.data
                            }
                            is AiLessonService.Result.Error -> {
                                errorMessage = res.message
                            }
                        }
                    } else {
                        val res = AiLessonService.generateCurriculumCourse(
                            courseTitle = topic,
                            level = level,
                            courseType = courseType,
                            lessonCount = lessonCount,
                            persona = selectedPersona,
                            customInstructions = studioGuidance,
                            key = key,
                            modelId = creator?.modelId.orEmpty(),
                            onProgress = { cur, total, msg ->
                                progressMessage = msg
                            }
                        )
                        isGenerating = false
                        when (res) {
                            is AiLessonService.Result.Success -> {
                                generatedCoursePkg = res.data
                            }
                            is AiLessonService.Result.Error -> {
                                errorMessage = res.message
                            }
                        }
                    }
                }
            },
            enabled = hasKey && !isGenerating && topic.isNotBlank(),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ZAmber, contentColor = Color.Black)
        ) {
            Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (genScope == 0) "توليد الدرس بالذكاء الاصطناعي" else "توليد المنهج كاملاً ($lessonCount دروس)",
                fontWeight = FontWeight.Black,
                fontSize = 14.sp
            )
        }

        // ==========================================================
        // PREVIEW & IMPORT GENERATED CONTENT
        // ==========================================================
        generatedLessonPkg?.let { pkg ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = ZCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, ZEmerald.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, tint = ZEmerald, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("تم توليد الدرس بنجاح جاهز للاعتماد:", color = ZEmerald, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(pkg.metadata.title, color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text(pkg.lessonNotes.firstOrNull() ?: pkg.metadata.courseNameAr, color = ZTextSecondary, fontSize = 12.sp)

                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetaChip("${pkg.globalVocabulary.size} كلمات", ZIndigo)
                        MetaChip("${pkg.lessonContent.examples.size} قواعد", ZPurple)
                        MetaChip("${pkg.lessonContent.dialogue.size} محادثات", ZCyanDeep)
                        MetaChip("${pkg.quiz.size} أسئلة", ZAmber)
                    }

                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            val pkgToImport = if (targetCourse != null) {
                                pkg.copy(
                                    metadata = pkg.metadata.copy(
                                        courseId = targetCourse.jsonId,
                                        courseNameAr = targetCourse.name,
                                        level = targetCourse.levelId,
                                    )
                                )
                            } else pkg
                            vm.importLesson(pkgToImport)
                            onSuccess("تمت إضافة الدرس «${pkg.metadata.title}» بنجاح ✓")
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ZEmerald, contentColor = Color.White)
                    ) {
                        Icon(Icons.Filled.AddCircle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("اعتماد وإضافة الدرس للكورس", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        generatedCoursePkg?.let { cPkg ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = ZCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, ZEmerald.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, tint = ZEmerald, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("تم توليد المنهج الكامل بنجاح:", color = ZEmerald, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(cPkg.courseName, color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text("المستوى: ${cPkg.levelName} · ${cPkg.lessons.size} دروس مجهزة بالكامل", color = ZTextSecondary, fontSize = 12.sp)

                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            vm.importPackage(cPkg)
                            onSuccess("تم إنشاء كورس «${cPkg.courseName}» بـ ${cPkg.lessons.size} دروس بنجاح ✓")
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ZEmerald, contentColor = Color.White)
                    ) {
                        Icon(Icons.Filled.LibraryAdd, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("اعتماد وإضافة المنهج إلى كورساتي", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

// ==============================================================================
// 2) DIRECT MANUAL AUTHORING VIEW (Write Lessons Directly)
// ==============================================================================

@Composable
private fun DirectAuthoringView(
    vm: AppViewModel,
    targetCourse: Course?,
    onSuccess: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    var title by remember { mutableStateOf("") }
    var summaryAr by remember { mutableStateOf("") }
    var readingEn by remember { mutableStateOf("") }
    var readingAr by remember { mutableStateOf("") }

    // Words dynamic list
    val words = remember { mutableStateListOf(JsonWord("hello", "مرحباً", "/həˈloʊ/", "Hello, nice to meet you.", "مرحباً، سررت بلقائك.")) }
    // Dialogues dynamic list
    val dialogues = remember { mutableStateListOf<JsonDialogue>() }
    // Grammar rules dynamic list
    val grammarRules = remember { mutableStateListOf<JsonGrammarRule>() }
    // Quizzes dynamic list
    val quizzes = remember { mutableStateListOf<JsonQuiz>() }

    var activeTab by remember { mutableStateOf(0) } // 0: Basic Info · 1: Words · 2: Dialogues & Reading · 3: Quizzes

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section Pills
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StudioSectionTab(activeTab == 0, "1. بيانات الدرس الأساسية", Icons.Filled.Info) { activeTab = 0 }
            StudioSectionTab(activeTab == 1, "2. المفردات (${words.size})", Icons.Filled.Abc) { activeTab = 1 }
            StudioSectionTab(activeTab == 2, "3. القراءة والمحادثات", Icons.Filled.MenuBook) { activeTab = 2 }
            StudioSectionTab(activeTab == 3, "4. الأسئلة والتمارين (${quizzes.size})", Icons.Filled.Quiz) { activeTab = 3 }
        }

        when (activeTab) {
            0 -> {
                // Metadata
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان الدرس بالإنجليزية (Title)") },
                    placeholder = { Text("e.g. Ordering Coffee & Pastries") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ZCard, unfocusedContainerColor = ZCard,
                        focusedBorderColor = ZAmber, unfocusedBorderColor = ZBorder,
                        focusedTextColor = ZTextPrimary, unfocusedTextColor = ZTextPrimary,
                    )
                )

                OutlinedTextField(
                    value = summaryAr,
                    onValueChange = { summaryAr = it },
                    label = { Text("ملخص الدرس بالعربية") },
                    placeholder = { Text("مثال: تعلّم كيفية طلب المشروبات والمأكولات في المقهى") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ZCard, unfocusedContainerColor = ZCard,
                        focusedBorderColor = ZAmber, unfocusedBorderColor = ZBorder,
                        focusedTextColor = ZTextPrimary, unfocusedTextColor = ZTextPrimary,
                    )
                )
            }
            1 -> {
                // Words Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("قائمة مفردات الدرس:", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    FilledTonalButton(
                        onClick = { words.add(JsonWord("", "", "", "", "")) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("إضافة كلمة", fontSize = 12.sp)
                    }
                }

                words.forEachIndexed { index, w ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ZCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ZBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("كلمة ${index + 1}", color = ZAmber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { words.removeAt(index) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.DeleteOutline, "حذف", tint = ZRose, modifier = Modifier.size(18.dp))
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = w.word,
                                    onValueChange = { words[index] = w.copy(word = it) },
                                    label = { Text("Word (English)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                OutlinedTextField(
                                    value = w.translation,
                                    onValueChange = { words[index] = w.copy(translation = it) },
                                    label = { Text("الترجمة") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                            OutlinedTextField(
                                value = w.example,
                                onValueChange = { words[index] = w.copy(example = it) },
                                label = { Text("Example sentence") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
            }
            2 -> {
                // Reading & Dialogues
                OutlinedTextField(
                    value = readingEn,
                    onValueChange = { readingEn = it },
                    label = { Text("النص القرائي الإنجليزي (Reading Passage)") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = readingAr,
                    onValueChange = { readingAr = it },
                    label = { Text("الترجمة العربية للنص القرائي") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("حوارات الدرس (Dialogues):", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    FilledTonalButton(
                        onClick = { dialogues.add(JsonDialogue("Speaker", "", "")) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("إضافة سطر حواري", fontSize = 12.sp)
                    }
                }

                dialogues.forEachIndexed { index, d ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ZCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ZBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = d.speaker,
                                    onValueChange = { dialogues[index] = d.copy(speaker = it) },
                                    label = { Text("المتحدث") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                IconButton(onClick = { dialogues.removeAt(index) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.DeleteOutline, "حذف", tint = ZRose, modifier = Modifier.size(18.dp))
                                }
                            }
                            OutlinedTextField(
                                value = d.en,
                                onValueChange = { dialogues[index] = d.copy(en = it) },
                                label = { Text("English line") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = d.ar,
                                onValueChange = { dialogues[index] = d.copy(ar = it) },
                                label = { Text("الترجمة العربية") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
            }
            3 -> {
                // Quizzes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("أسئلة وتمارين الدرس:", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    FilledTonalButton(
                        onClick = {
                            quizzes.add(
                                JsonQuiz(
                                    type = "multiple_choice",
                                    question = "",
                                    options = listOf("Option 1", "Option 2", "Option 3"),
                                    answer = "Option 1",
                                    explanationAr = ""
                                )
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("إضافة سؤال", fontSize = 12.sp)
                    }
                }

                quizzes.forEachIndexed { index, q ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ZCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ZBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("سؤال ${index + 1}", color = ZAmber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { quizzes.removeAt(index) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.DeleteOutline, "حذف", tint = ZRose, modifier = Modifier.size(18.dp))
                                }
                            }
                            OutlinedTextField(
                                value = q.question,
                                onValueChange = { quizzes[index] = q.copy(question = it) },
                                label = { Text("نص السؤال (Question)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = q.answer,
                                onValueChange = { quizzes[index] = q.copy(answer = it) },
                                label = { Text("الإجابة الصحيحة (Correct Answer)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = q.explanationAr,
                                onValueChange = { quizzes[index] = q.copy(explanationAr = it) },
                                label = { Text("توضيح الإجابة بالعربية") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // Save & Add Lesson Button
        Button(
            onClick = {
                val lessonCount = if (targetCourse != null) vm.lessons.count { it.courseId == targetCourse.id } else 0
                val cleanTitle = title.ifBlank { "Lesson ${lessonCount + 1}" }
                val meta = LessonMeta(
                    courseId = targetCourse?.jsonId ?: "custom_course_${System.currentTimeMillis()}",
                    courseNameAr = targetCourse?.name ?: "كورس مخصص",
                    courseType = (targetCourse?.type ?: CourseType.VOCABULARY).name.lowercase(),
                    level = targetCourse?.levelId ?: 1,
                    levelName = "المستوى ${targetCourse?.levelId ?: 1}",
                    lessonNo = lessonCount + 1,
                    style = "vocab_cards",
                    title = cleanTitle,
                )
                val pkg = LessonPackage(
                    metadata = meta,
                    lessonContent = LessonContent(
                        dialogue = dialogues.filter { it.en.isNotBlank() },
                        examples = grammarRules.map { JsonSentence(it.rule, it.ruleAr) },
                        fullTextEn = readingEn,
                        fullTextAr = readingAr,
                    ),
                    globalVocabulary = words.filter { it.word.isNotBlank() }.map {
                        JsonGlobalWord(
                            word = it.word,
                            meaning = it.translation,
                            phonetic = it.phonetic,
                            exampleEn = it.example,
                            exampleAr = it.exampleAr,
                            mentalImage = it.mentalImage,
                        )
                    },
                    lessonNotes = if (summaryAr.isNotBlank()) listOf(summaryAr) else listOf("مراجعة وحفظ مفردات $cleanTitle"),
                    quiz = quizzes.filter { it.question.isNotBlank() }
                )

                vm.importLesson(pkg)
                onSuccess("تم حفظ وإضافة الدرس «$cleanTitle» بنجاح ✓")
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ZEmerald, contentColor = Color.White)
        ) {
            Icon(Icons.Filled.Save, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("حفظ ونشر الدرس في المنهج", fontWeight = FontWeight.Black, fontSize = 14.sp)
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun StudioSectionTab(
    active: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (active) ZAmber else ZCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) ZAmber else ZBorder),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = if (active) Color.Black else ZTextSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = if (active) Color.Black else ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MetaChip(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.15f)) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}
