package com.zmastery.english.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.data.ArchivedStory
import com.zmastery.english.ui.components.SoftCard
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel

/**
 * المولّد — مركز كل التوليدات في مكان واحد:
 *   • قصة اليوم (توليد / قراءة / إعادة توليد / أهداف / إثبات المرحلة)
 *   • توليد الأصوات بالذكاء الاصطناعي (مع زر إيقاف)
 *   • استوديو بناء المناهج والدروس
 *   • الروابط الذهنية
 *
 * الشاشة الرئيسية تعرض فقط «اقرأ قصة اليوم»، وأي توليد ينتقل من هنا.
 */
@Composable
fun GeneratorScreen(
    vm: AppViewModel,
    onOpenMnemonics: () -> Unit,
) {
    com.zmastery.english.ui.components.TrackStudyTime(vm, "generator")
    var showLessonStudio by remember { mutableStateOf(false) }
    var interactiveStory by remember { mutableStateOf<ArchivedStory?>(null) }

    if (showLessonStudio) {
        LessonStudioDialog(
            vm = vm,
            targetCourse = null,
            onDismiss = { showLessonStudio = false },
        )
    }
    interactiveStory?.let { story ->
        com.zmastery.english.ui.components.InteractiveStoryDialog(
            story = story,
            vm = vm,
            onDismiss = { interactiveStory = null },
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { GeneratorHeader() }
        item { StoryGenerationCard(vm, onOpenStory = { s -> interactiveStory = s }) }
        item { AudioGenerationCard(vm) }
        item { LessonStudioCard { showLessonStudio = true } }
        item { MnemonicsCard(onOpenMnemonics) }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

@Composable
private fun GeneratorHeader() {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(ZIndigo, ZPurple)))
            .padding(20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Text("المولّد والتوليدات", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "كل ما يُنشئه الذكاء الاصطناعي من أجلك: قصة اليوم، الأصوات الطبيعية، المناهج والدروس، والروابط الذهنية.",
                color = Color.White.copy(alpha = 0.93f), fontSize = 13.sp, lineHeight = 21.sp,
            )
        }
    }
}

/** بطاقة قصة اليوم — التوليد والقراءة والأهداف وإثبات المرحلة (منقولة من الشاشة الرئيسية). */
@Composable
private fun StoryGenerationCard(vm: AppViewModel, onOpenStory: (ArchivedStory) -> Unit) {
    val today = vm.todayStory
    val ready = today != null
    val goalStory = today
    val goal = vm.activeGoal
    var showDelete by remember { mutableStateOf(false) }
    var showGoals by remember { mutableStateOf(false) }
    var showQuiz by remember { mutableStateOf(false) }
    var ctxAnswer by remember { mutableStateOf("") }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            containerColor = ZCard,
            icon = { Icon(Icons.Filled.DeleteOutline, null, tint = ZRose) },
            title = { Text("حذف قصة اليوم؟", color = ZTextPrimary, fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "ستُحذف قصة اليوم من الأرشيف، ويمكنك توليد قصة جديدة بعدها مباشرة.",
                    color = ZTextSecondary, fontSize = 13.sp, lineHeight = 21.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteTodayStory(); showDelete = false }) {
                    Text("حذف", color = ZRose, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text("إلغاء", color = ZTextSecondary)
                }
            },
        )
    }

    SoftCard(modifier = Modifier.fillMaxWidth(), radius = 20.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(ZAmber, ZRoseDeep))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (ready) Icons.Filled.MenuBook else Icons.Filled.AutoAwesome,
                        null, tint = Color.White, modifier = Modifier.size(23.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (ready) "قصة اليوم جاهزة" else "توليد قصة اليوم",
                        color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp,
                    )
                    Text(
                        when {
                            ready -> if (goalStory != null && goalStory.goalId.isNotBlank())
                                "قصة نحو هدفك · ${goalStory.wordCount} كلمة · ${goalStory.readMinutes} دقيقة"
                            else "${goalStory?.wordCount ?: 0} كلمة · ${goalStory?.readMinutes ?: 0} دقيقة"
                            !vm.storyAiReady -> "تحتاج مفتاح Gemini — تُكتب بالذكاء الاصطناعي"
                            goal != null -> "تُنسج اليوم حول هدفك ومرحلته الحالية — لا حول جدول الحفظ"
                            vm.storySeedCount >= 2 -> "قصة بالذكاء الاصطناعي من ${vm.storySeedCount} من كلماتك"
                            else -> "أضف كلمات للقاموس لتوليد قصة"
                        },
                        color = ZTextSecondary, fontSize = 11.sp,
                    )
                }
                if (ready && !today!!.isRead) {
                    Surface(shape = RoundedCornerShape(50), color = ZRose.copy(alpha = 0.16f)) {
                        Text(
                            "جديدة", color = ZRose, fontSize = 10.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            // ── شريط الهدف: القصة اليومية صارت نحو هدف المتعلم لا نحو جدول الحفظ ──
            if (goal != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ZCyanDeep.copy(alpha = 0.10f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Filled.Route, null, tint = ZCyanDeep, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            goal.title, color = ZTextPrimary, fontSize = 11.sp,
                            fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (goal.isFinished) "مكتمل 🏆"
                            else "المرحلة ${goal.stageIndex + 1}/${goal.stages.size}: ${goal.currentStage}",
                            color = ZCyanDeep, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                    TextButton(onClick = { showGoals = true }) {
                        Icon(Icons.Filled.Tune, null, tint = ZCyanDeep, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("أهدافي", color = ZCyanDeep, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (vm.isMakingStory) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (vm.isWaitingForAi) {
                        Icon(Icons.Filled.CloudOff, null, tint = ZAmber, modifier = Modifier.size(18.dp))
                    } else {
                        CircularProgressIndicator(color = ZAmber, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (vm.isWaitingForAi) "بانتظار الاتصال بالنموذج…" else "الذكاء الاصطناعي ينسج قصة اليوم…",
                            color = ZTextSecondary, fontSize = 13.sp,
                        )
                        if (vm.isWaitingForAi && vm.storyRetryIn > 0) {
                            Text(
                                "إعادة المحاولة بعد ${vm.storyRetryIn} ث · محاولة ${vm.storyAttempt}",
                                color = ZTextMuted, fontSize = 10.sp,
                            )
                        }
                    }
                    TextButton(onClick = { vm.cancelStoryGeneration() }) {
                        Text("إلغاء", color = ZTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            if (ready) onOpenStory(today!!)
                            else vm.generateTodayStory { s -> s?.let(onOpenStory) }
                        },
                        enabled = ready || ((vm.storySeedCount >= 2 || goal != null) && vm.storyAiReady),
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ZAmber, disabledContainerColor = ZBorder),
                    ) {
                        Icon(
                            if (ready) Icons.Filled.MenuBook else Icons.Filled.AutoAwesome,
                            null, modifier = Modifier.size(17.dp), tint = Color.White,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (ready) "اقرأ قصة اليوم" else "ولّد قصة اليوم",
                            fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White,
                        )
                    }
                }
                // ── إعادة التوليد / حذف قصة اليوم ──
                if (ready) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { vm.regenerateTodayStory() },
                            enabled = vm.storySeedCount >= 2 && vm.storyAiReady,
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ZIndigo.copy(alpha = 0.45f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ZIndigo),
                        ) {
                            Icon(Icons.Filled.Autorenew, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("أعد التوليد", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { showDelete = true },
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ZRose.copy(alpha = 0.45f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ZRose),
                        ) {
                            Icon(Icons.Filled.DeleteOutline, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("احذفها", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                // ── تطبيق لا حفظ: سؤال السياق اليومي + اختبار إثبات المرحلة ──
                if (goalStory != null && goalStory.goalId.isNotBlank()) {
                    if (goalStory.contextQuestionAr.isNotBlank() && goalStory.contextAnswer.isBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Surface(shape = RoundedCornerShape(14.dp), color = ZSurfaceVariant.copy(alpha = 0.7f)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "النموذج يريد أن يعرفك أكثر — إجابتك تبني سياقك:",
                                    color = ZTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    goalStory.contextQuestionAr, color = ZTextPrimary,
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp,
                                )
                                if (goalStory.contextQuestionEn.isNotBlank()) {
                                    Text(
                                        goalStory.contextQuestionEn, color = ZTextSecondary,
                                        fontSize = 11.sp, lineHeight = 16.sp,
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = ctxAnswer, onValueChange = { ctxAnswer = it },
                                    label = { Text("إجابتك (عربي أو إنجليزي)") },
                                    minLines = 2,
                                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = ZCard, unfocusedContainerColor = ZCard,
                                        focusedBorderColor = ZCyanDeep, unfocusedBorderColor = ZBorder,
                                        focusedTextColor = ZTextPrimary, unfocusedTextColor = ZTextPrimary,
                                    ),
                                )
                                Spacer(Modifier.height(4.dp))
                                TextButton(
                                    onClick = { vm.saveContextAnswer(goalStory.id, ctxAnswer); ctxAnswer = "" },
                                    enabled = ctxAnswer.isNotBlank(),
                                ) {
                                    Text(
                                        "احفظ إجابتي — قصة الغد تُكتب عنك",
                                        color = ZCyanDeep, fontSize = 11.sp, fontWeight = FontWeight.Black,
                                    )
                                }
                            }
                        }
                    } else if (goalStory.contextAnswer.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "سياقك اليوم: ${goalStory.contextAnswer}",
                            color = ZTextMuted, fontSize = 10.sp, lineHeight = 15.sp,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { showQuiz = true },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ZEmerald.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ZEmeraldDeep),
                    ) {
                        Icon(Icons.Filled.FactCheck, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("إثبات المرحلة (٣ أسئلة موقفية)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                vm.storyMessage?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Text(msg, color = ZTextMuted, fontSize = 11.sp, lineHeight = 17.sp)
                }
            }
        }
    }

    if (showGoals) GoalManagerDialog(vm) { showGoals = false }
    if (showQuiz) StageQuizDialog(vm) { showQuiz = false }
}

/** بطاقة توليد الأصوات — الحالة + توليد الآن + زر إيقاف التوليد. */
@Composable
private fun AudioGenerationCard(vm: AppViewModel) {
    val busy = vm.isGeneratingAudio
    var confirmRegen by remember { mutableStateOf(false) }

    SoftCard(modifier = Modifier.fillMaxWidth(), radius = 20.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(ZCyanDeep, ZCyan))),
                    contentAlignment = Alignment.Center,
                ) {
                    if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
                    else Icon(Icons.Filled.GraphicEq, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("توليد الأصوات", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text(
                        when {
                            busy -> "${vm.audioGenDone}/${vm.audioGenTotal} — ${vm.audioGenLabel}"
                            vm.hasPendingAudio -> "${vm.pendingAudioCount} عنصر بانتظار التوليد"
                            else -> "كل الأصوات مولّدة ✓"
                        },
                        color = ZTextSecondary, fontSize = 12.sp, maxLines = 1,
                    )
                }
            }

            vm.lastAudioMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = ZCyanDeep, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(12.dp))
            if (busy) {
                LinearProgressIndicator(
                    progress = { if (vm.audioGenTotal <= 0) 0f else vm.audioGenDone.toFloat() / vm.audioGenTotal },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                    color = ZCyanDeep, trackColor = ZBorder,
                )
                Spacer(Modifier.height(12.dp))
                // زر إيقاف توليد الأصوات — طلب صريح من المستخدم
                Button(
                    onClick = { vm.stopAudioGeneration() },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ZRose),
                ) {
                    Icon(Icons.Filled.Stop, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("إيقاف توليد الأصوات", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            } else {
                if (vm.hasPendingAudio && vm.aiAudioEnabled) {
                    Button(
                        onClick = { vm.generateMissingAudio() },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ZCyanDeep),
                    ) {
                        Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("توليد الآن (${vm.pendingAudioCount})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                } else if (vm.aiAudioEnabled) {
                    Text(
                        "لا توجد أصوات بحاجة للتوليد — كل المحتوى مغطى بصوت طبيعي.",
                        color = ZTextMuted, fontSize = 11.sp, lineHeight = 17.sp,
                    )
                } else {
                    Text(
                        "توليد الأصوات معطّل نهائياً من الإعدادات · التعلم.",
                        color = ZTextMuted, fontSize = 11.sp, lineHeight = 17.sp,
                    )
                }

                if (vm.aiAudioEnabled) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { confirmRegen = true },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ZPurple.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ZPurple),
                    ) {
                        Icon(Icons.Filled.Autorenew, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("استبدل كل الأصوات بصوت الذكاء الاصطناعي", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (confirmRegen) {
        AlertDialog(
            onDismissRequest = { confirmRegen = false },
            containerColor = ZSurface,
            icon = { Icon(Icons.Filled.Autorenew, null, tint = ZPurple) },
            title = { Text("استبدال كل الأصوات؟", color = ZTextPrimary, fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "سيُعاد توليد أصوات كل الكلمات والأمثلة والدروس والقصص من جديد بصوت الذكاء الاصطناعي الطبيعي، ويُحذف أي صوت محفوظ سابقاً. قد يستغرق هذا وقتاً حسب حجم المحتوى.",
                    color = ZTextSecondary, fontSize = 13.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = { confirmRegen = false; vm.regenerateAllAudioWithAi() },
                    colors = ButtonDefaults.buttonColors(containerColor = ZPurple),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("استبدال", fontWeight = FontWeight.Bold, color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRegen = false }) {
                    Text("إلغاء", color = ZTextSecondary, fontWeight = FontWeight.Bold)
                }
            },
        )
    }
}

/** بطاقة استوديو بناء المناهج والدروس — منقولة من شاشة المستويات. */
@Composable
private fun LessonStudioCard(onOpen: () -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth(), radius = 20.dp, onClick = onOpen) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                    .background(ZAmber.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.AutoAwesome, null, tint = ZAmberDeep, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("استوديو بناء الدروس والمناهج", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Spacer(Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(6.dp), color = ZAmber) {
                        Text("AI + يدوي", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                }
                Text(
                    "توليد منهج أو درس كامل بالذكاء الاصطناعي مع الشخصيات، أو كتابته مباشرة",
                    color = ZTextSecondary, fontSize = 11.sp, lineHeight = 16.sp,
                )
            }
            Icon(Icons.Filled.ChevronLeft, null, tint = ZAmberDeep, modifier = Modifier.size(20.dp))
        }
    }
}

/** بطاقة الروابط الذهنية — توليد صور مركّبة تثبّت الكلمات. */
@Composable
private fun MnemonicsCard(onOpen: () -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth(), radius = 20.dp, onClick = onOpen) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                    .background(ZPurple.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Link, null, tint = ZPurple, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("الروابط الذهنية", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text(
                    "ولّد صورة مركّبة تقصّها لك وتثبّت كل كلمة في بطاقات المراجعة",
                    color = ZTextSecondary, fontSize = 11.sp, lineHeight = 16.sp,
                )
            }
            Icon(Icons.Filled.ChevronLeft, null, tint = ZPurple, modifier = Modifier.size(20.dp))
        }
    }
}

/* ─────────────────── حوارات مسار الهدف التطبيقي (منقولة) ─────────────────── */

/** إدارة الأهداف: تفعيل/إنشاء — الهدف النشط هو بوصلة القصة اليومية. */
@Composable
private fun GoalManagerDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var stagesText by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("أهدافي التطبيقية 🎯", color = ZTextPrimary, fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (vm.goals.isEmpty()) {
                    Text("لا أهداف بعد — أنشئ هدفك الأول أدناه.", color = ZTextSecondary, fontSize = 12.sp)
                }
                vm.goals.forEach { g ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (g.active) ZCyanDeep.copy(alpha = 0.12f) else Color.Transparent)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(g.title, color = ZTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (g.isFinished) "مكتمل 🏆"
                                else "المرحلة ${g.stageIndex + 1}/${g.stages.size}: ${g.currentStage}",
                                color = ZCyanDeep, fontSize = 10.sp,
                            )
                        }
                        if (!g.active) {
                            TextButton(onClick = { vm.setActiveGoal(g.id) }) {
                                Text("تفعيل", color = ZIndigo, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                        } else {
                            Text("نشط ✓", color = ZCyanDeep, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showCreate = !showCreate }) {
                    Icon(Icons.Filled.Add, null, tint = ZAmberDeep, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (showCreate) "إخفاء نموذج الإنشاء" else "هدف جديد",
                        color = ZAmberDeep, fontSize = 11.sp, fontWeight = FontWeight.Black,
                    )
                }
                if (showCreate) {
                    OutlinedTextField(
                        value = title, onValueChange = { title = it },
                        label = { Text("عنوان الهدف") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ZSurfaceVariant, unfocusedContainerColor = ZSurfaceVariant,
                            focusedBorderColor = ZAmber, unfocusedBorderColor = ZBorder,
                            focusedTextColor = ZTextPrimary, unfocusedTextColor = ZTextPrimary,
                        ),
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = stagesText, onValueChange = { stagesText = it },
                        label = { Text("المراحل — سطر لكل مرحلة") }, minLines = 3,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ZSurfaceVariant, unfocusedContainerColor = ZSurfaceVariant,
                            focusedBorderColor = ZAmber, unfocusedBorderColor = ZBorder,
                            focusedTextColor = ZTextPrimary, unfocusedTextColor = ZTextPrimary,
                        ),
                    )
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = {
                        vm.createGoal(title, "", stagesText.split("\n")) { ok, msg ->
                            status = ok to msg
                            if (ok) { title = ""; stagesText = ""; showCreate = false }
                        }
                    }) { Text("إنشاء الهدف", color = ZAmberDeep, fontWeight = FontWeight.Black) }
                }
                status?.let { (ok, msg) ->
                    Spacer(Modifier.height(6.dp))
                    Text(msg, color = if (ok) ZEmeraldDeep else ZRoseDeep, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق", color = ZTextSecondary) } },
    )
}

/** اختبار إثبات المرحلة: ٣ أسئلة موقفية، واجتياز ≥٢ يقدّم المرحلة. */
@Composable
private fun StageQuizDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var answers by remember { mutableStateOf<List<Int>>(emptyList()) }
    var result by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { if (vm.stageQuiz == null) vm.requestStageQuiz() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إثبات المرحلة 🎓", color = ZTextPrimary, fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                vm.activeGoal?.let { g ->
                    Text(
                        "المرحلة الحالية: «${g.currentStage}»",
                        color = ZCyanDeep, fontSize = 11.sp, fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (vm.isMakingQuiz) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = ZEmerald, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("النموذج يكتب أسئلة موقفية…", color = ZTextSecondary, fontSize = 11.sp)
                    }
                } else if (vm.stageQuiz == null) {
                    Text(vm.quizMessage ?: "اطلب الاختبار من شاشة القصة.", color = ZTextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { vm.requestStageQuiz() }) {
                        Text("توليد الاختبار", color = ZEmeraldDeep, fontWeight = FontWeight.Black)
                    }
                } else {
                    vm.stageQuiz!!.forEachIndexed { qi, q ->
                        Text("${qi + 1}. ${q.questionEn}", color = ZTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
                        Spacer(Modifier.height(4.dp))
                        q.options.forEachIndexed { oi, opt ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (answers.getOrNull(qi) == oi) ZEmerald.copy(alpha = 0.15f)
                                        else Color.Transparent
                                    )
                                    .padding(2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = answers.getOrNull(qi) == oi,
                                    onClick = {
                                        answers = answers.toMutableList().apply {
                                            while (size <= qi) add(-1)
                                            this[qi] = oi
                                        }
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = ZEmerald),
                                )
                                Text(opt, color = ZTextSecondary, fontSize = 11.sp, lineHeight = 16.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
                vm.quizMessage?.let { m ->
                    Text(m, color = ZTextMuted, fontSize = 10.sp, lineHeight = 15.sp)
                }
                result?.let { r ->
                    Spacer(Modifier.height(6.dp))
                    Text(r, color = if (r.startsWith("✓") || r.startsWith("🏆")) ZEmeraldDeep else ZRoseDeep,
                        fontSize = 12.sp, fontWeight = FontWeight.Black, lineHeight = 18.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    result = vm.submitStageQuiz(answers)
                    answers = emptyList()
                },
                enabled = vm.stageQuiz != null && !vm.isMakingQuiz &&
                    (answers.count { it >= 0 } >= (vm.stageQuiz?.size ?: 0)),
            ) { Text("سلّم إجاباتي", color = ZEmeraldDeep, fontWeight = FontWeight.Black) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق", color = ZTextSecondary) } },
    )
}
