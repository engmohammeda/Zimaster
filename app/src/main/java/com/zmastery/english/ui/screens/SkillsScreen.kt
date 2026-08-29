package com.zmastery.english.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zmastery.english.audio.AudioButton
import com.zmastery.english.audio.rememberSpeechCapture
import com.zmastery.english.domain.usecases.ChatTurn
import com.zmastery.english.domain.usecases.PhoneticDrill
import com.zmastery.english.domain.usecases.SkillScore
import com.zmastery.english.domain.usecases.SkillsEngine
import com.zmastery.english.domain.usecases.TrainingPassage
import com.zmastery.english.domain.usecases.WritingPrompt
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel
import kotlinx.coroutines.launch

private data class SkillDef(
    val key: String,
    val title: String,
    val desc: String,
    val icon: ImageVector,
    val colors: List<Color>,
)

@Composable
private fun skillDefs(): List<SkillDef> = listOf(
    SkillDef("reading", "القراءة", "اقرأ القطعة بصوت عالٍ وقارن نطقك بالنموذج", Icons.Filled.AutoStories, listOf(ZCyanDeep, ZCyan)),
    SkillDef("listening", "الاستماع", "استمع ثم اكتب ما سمعت أو أجب عن الفهم", Icons.Filled.Headphones, listOf(ZEmerald, ZEmeraldDeep)),
    SkillDef("speaking", "التحدث", "محادثة لايف: تكلّم فيردّ عليك النموذج فوراً", Icons.Filled.RecordVoiceOver, listOf(ZRose, Color(0xFFE11D48))),
    SkillDef("writing", "الكتابة", "اكتب فقرة وصحّحها الذكاء الاصطناعي", Icons.Filled.Edit, listOf(ZCyanDeep, ZIndigo)),
    SkillDef("phonetics", "الصوتيات", "تدريب مخارج الحروف مع التظليل والتسجيل", Icons.Filled.GraphicEq, listOf(ZAmber, ZRoseDeep)),
)

@Composable
fun SkillsScreen(vm: AppViewModel) {
    var active by remember { mutableStateOf<String?>(null) }
    if (active != null) {
        SkillDetail(active!!, vm) { active = null }
        return
    }
    val defs = skillDefs()
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text("التدريب", color = ZTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(
                "خمس مهارات جاهزة: اقرأ · اسمع · تحدّث لايف · اكتب · انطق",
                color = ZTextSecondary, fontSize = 13.sp,
            )
        }
        items(defs, key = { it.key }) { skill ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = ZCard,
                shadowElevation = 5.dp,
                modifier = Modifier.fillMaxWidth(),
                onClick = { active = skill.key },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(skill.colors)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(skill.icon, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(skill.title, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(skill.desc, color = ZTextSecondary, fontSize = 12.sp)
                    }
                    Icon(Icons.Filled.ChevronLeft, null, tint = ZTextMuted)
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun SkillDetail(key: String, vm: AppViewModel, onBack: () -> Unit) {
    val skill = skillDefs().first { it.key == key }
    if (key == "speaking") {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                TextButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowForward, null, tint = ZCyan)
                    Spacer(Modifier.width(8.dp))
                    Text("الرجوع", color = ZCyan)
                }
            }
            SkillHero(skill, compact = true)
            SpeakingSkill(vm, Modifier.weight(1f))
        }
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowForward, null, tint = ZCyan)
            Spacer(Modifier.width(8.dp))
            Text("الرجوع", color = ZCyan)
        }
        SkillHero(skill, compact = false)
        when (key) {
            "reading" -> ReadingSkill(vm)
            "listening" -> ListeningSkill(vm)
            "writing" -> WritingSkill(vm)
            else -> PhoneticsSkill(vm)
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun SkillHero(skill: SkillDef, compact: Boolean) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = if (compact) 16.dp else 0.dp)
            .clip(RoundedCornerShape(if (compact) 18.dp else 24.dp))
            .background(Brush.linearGradient(skill.colors))
            .padding(if (compact) 16.dp else 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(skill.icon, null, tint = Color.White, modifier = Modifier.size(if (compact) 28.dp else 38.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(skill.title, color = Color.White, fontSize = if (compact) 18.sp else 24.sp, fontWeight = FontWeight.Black)
                Text(skill.desc, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
            }
        }
    }
    if (compact) Spacer(Modifier.height(8.dp))
}

@Composable
private fun SkillCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 5.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(title, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ScoreBanner(score: SkillScore) {
    val color = when {
        score.percent >= 75 -> ZEmerald
        score.percent >= 50 -> ZAmber
        else -> ZRose
    }
    Surface(shape = RoundedCornerShape(16.dp), color = color.copy(alpha = 0.14f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${score.percent}%", color = color, fontWeight = FontWeight.Black, fontSize = 28.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(score.grade, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("${score.matched} / ${score.total} كلمة مطابقة", color = ZTextSecondary, fontSize = 11.sp)
                }
            }
            if (score.missed.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("فاتتك: ${score.missed.take(8).joinToString(" · ")}", color = ZTextMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PassageChooser(items: List<TrainingPassage>, selected: TrainingPassage, onPick: (TrainingPassage) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(14.dp), color = ZSurfaceVariant, onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(selected.title, color = ZTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                Text("${selected.source} · ${selected.en.split(" ").size} كلمة", color = ZTextMuted, fontSize = 11.sp)
            }
            Icon(Icons.Filled.ExpandMore, null, tint = ZTextMuted)
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = ZSurface,
            title = { Text("اختر المادة", color = ZTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 360.dp)) {
                    items.forEach { p ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (p.id == selected.id) ZIndigo.copy(alpha = 0.12f) else Color.Transparent,
                            onClick = { onPick(p); open = false },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(p.title, color = ZTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(p.en.take(80), color = ZTextMuted, fontSize = 11.sp, maxLines = 2)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("إغلاق", color = ZTextSecondary) } },
        )
    }
}

@Composable
private fun rememberMicPermission(): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
    }
    val request = {
        if (!granted) launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
    return granted to request
}

// ═══════════════════════════════════════════════════════════════════════════
//  القراءة
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ReadingSkill(vm: AppViewModel) {
    val passages = remember(vm.lessons.toList()) { SkillsEngine.readingPassages(vm.lessons.toList()) }
    var index by remember { mutableStateOf(0) }
    val passage = passages.getOrNull(index) ?: passages.first()
    var transcript by remember { mutableStateOf("") }
    var score by remember { mutableStateOf<SkillScore?>(null) }
    val speech = rememberSpeechCapture()
    val (micOk, requestMic) = rememberMicPermission()

    fun grade(spoken: String) {
        transcript = spoken
        val s = SkillsEngine.overlapScore(passage.en, spoken)
        score = s
        vm.grantXp(if (s.percent >= 70) 20 else 8)
    }

    SkillCard("قطعة القراءة") {
        PassageChooser(passages, passage) { picked ->
            index = passages.indexOf(picked).coerceAtLeast(0)
            transcript = ""; score = null
        }
        Spacer(Modifier.height(12.dp))
        Text(passage.en, color = ZTextPrimary, fontSize = 16.sp, lineHeight = 26.sp)
        if (passage.ar.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(passage.ar, color = ZTextSecondary, fontSize = 13.sp, lineHeight = 22.sp)
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AudioButton(text = passage.en, audioKey = "read-${passage.id}", accent = ZCyanDeep, size = 48.dp)
            Text("استمع للنموذج ثم اقرأ أنت", color = ZTextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (!micOk) { requestMic(); return@Button }
                if (speech.isListening) speech.stop()
                else speech.start { grade(it) }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (speech.isListening) ZRose else ZIndigo),
        ) {
            Icon(if (speech.isListening) Icons.Filled.Stop else Icons.Filled.Mic, null)
            Spacer(Modifier.width(8.dp))
            Text(
                if (speech.isListening) "جارٍ الاستماع... تحدّث الآن" else "اقرأ القطعة بصوت عالٍ",
                fontWeight = FontWeight.Bold,
            )
        }
        if (speech.partial.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("سمعت: ${speech.partial}", color = ZCyan, fontSize = 12.sp)
        }
        speech.error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = ZRose, fontSize = 11.sp)
        }
        score?.let {
            Spacer(Modifier.height(12.dp))
            ScoreBanner(it)
        }
        if (transcript.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("نصّك: $transcript", color = ZTextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  الاستماع
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ListeningSkill(vm: AppViewModel) {
    val passages = remember(vm.lessons.toList(), vm.storyArchive.toList(), vm.activeVocab.size) {
        SkillsEngine.listeningPassages(vm.lessons.toList(), vm.storyArchive.toList(), vm.activeVocab)
    }
    var index by remember { mutableStateOf(0) }
    val passage = passages.getOrNull(index) ?: passages.first()
    var revealed by remember { mutableStateOf(false) }
    var dictation by remember { mutableStateOf("") }
    var score by remember { mutableStateOf<SkillScore?>(null) }

    fun check() {
        val s = SkillsEngine.overlapScore(passage.en, dictation)
        score = s
        revealed = true
        vm.completeTask("listen")
        vm.grantXp(if (s.percent >= 70) 18 else 8)
    }

    SkillCard("استمع ثم اكتب ما سمعت") {
        PassageChooser(passages, passage) { picked ->
            index = passages.indexOf(picked).coerceAtLeast(0)
            revealed = false; dictation = ""; score = null
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AudioButton(text = passage.en, audioKey = "listen-${passage.id}", accent = ZEmerald, size = 56.dp, iconSize = 28.dp)
            Column(Modifier.weight(1f)) {
                Text("اضغط للتشغيل — النص مخفي حتى تكشف أو تصحّح", color = ZTextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                TextButton(onClick = { revealed = !revealed }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (revealed) "إخفاء النص" else "كشف النص", color = ZEmerald, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
        if (revealed) {
            Spacer(Modifier.height(8.dp))
            Text(passage.en, color = ZTextPrimary, fontSize = 15.sp, lineHeight = 24.sp)
            if (passage.ar.isNotBlank()) Text(passage.ar, color = ZTextSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = dictation,
            onValueChange = { dictation = it },
            modifier = Modifier.fillMaxWidth().height(110.dp),
            placeholder = { Text("اكتب ما سمعته بالإنجليزية...", color = ZTextMuted) },
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors(),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { check() },
            enabled = dictation.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ZEmerald, disabledContainerColor = ZBorder),
        ) {
            Icon(Icons.Filled.FactCheck, null)
            Spacer(Modifier.width(8.dp))
            Text("تصحيح الإملاء السماعي", fontWeight = FontWeight.Bold)
        }
        score?.let {
            Spacer(Modifier.height(12.dp))
            ScoreBanner(it)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  التحدث — محادثة لايف
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun SpeakingSkill(vm: AppViewModel, modifier: Modifier = Modifier) {
    val scenes = remember(vm.lessons.toList()) { vm.conversationScenes() }
    val speech = rememberSpeechCapture()
    val (micOk, requestMic) = rememberMicPermission()
    var typed by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val turns = vm.conversationTurnsList

    LaunchedEffect(Unit) {
        if (turns.isEmpty()) vm.startConversationScene(vm.conversationSceneId)
    }
    LaunchedEffect(turns.size, vm.isConversationThinking) {
        val last = turns.size
        if (last > 0) runCatching { listState.animateScrollToItem(last) }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // Scene picker + auto-speak
        Row(verticalAlignment = Alignment.CenterVertically) {
            var open by remember { mutableStateOf(false) }
            val scene = scenes.firstOrNull { it.id == vm.conversationSceneId } ?: scenes.last()
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ZCard,
                onClick = { open = true },
                modifier = Modifier.weight(1f),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Forum, null, tint = ZRose, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(scene.title, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                        Text(scene.subtitle, color = ZTextMuted, fontSize = 10.sp, maxLines = 1)
                    }
                    Icon(Icons.Filled.ExpandMore, null, tint = ZTextMuted, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { vm.resetConversation() }) {
                Icon(Icons.Filled.Refresh, "حوار جديد", tint = ZIndigo)
            }
            if (open) {
                AlertDialog(
                    onDismissRequest = { open = false },
                    containerColor = ZSurface,
                    title = { Text("اختر المشهد", color = ZTextPrimary, fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            scenes.forEach { s ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (s.id == vm.conversationSceneId) ZRose.copy(alpha = 0.12f) else Color.Transparent,
                                    onClick = { vm.startConversationScene(s.id); open = false },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                ) {
                                    Column(Modifier.padding(10.dp)) {
                                        Text(s.title, color = ZTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(s.subtitle, color = ZTextMuted, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { open = false }) { Text("إغلاق", color = ZTextSecondary) } },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = vm.conversationAutoSpeak,
                onCheckedChange = { vm.conversationAutoSpeak = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ZRose),
            )
            Text("اقرأ رد الشريك تلقائياً", color = ZTextSecondary, fontSize = 12.sp)
            if (!vm.hasAiKey) {
                Spacer(Modifier.width(8.dp))
                Text("بدون مفتاح: حوار محفوظ", color = ZAmber, fontSize = 10.sp)
            }
        }

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(turns.size, key = { i -> "t$i-${turns[i].en.take(12)}" }) { i ->
                val turn = turns[i]
                ConversationBubble(turn) { vm.speakConversationLine(turn.en) }
            }
            if (vm.isConversationThinking) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Surface(shape = RoundedCornerShape(16.dp), color = ZSurfaceVariant) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = ZRose, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("الشريك يفكّر…", color = ZTextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            if (speech.isListening && speech.partial.isNotBlank()) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Surface(shape = RoundedCornerShape(16.dp), color = ZIndigo.copy(alpha = 0.20f)) {
                            Text("… ${speech.partial}", color = ZTextPrimary, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }
        }

        vm.conversationError?.let {
            Text(it, color = ZAmber, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        speech.error?.let {
            Text(it, color = ZRose, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
        }

        // Composer
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 12.dp)) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("أو اكتب ردّك هنا…", color = ZTextMuted, fontSize = 13.sp) },
                shape = RoundedCornerShape(18.dp),
                colors = fieldColors(),
                maxLines = 3,
            )
            Spacer(Modifier.width(8.dp))
            if (typed.isNotBlank()) {
                Surface(
                    shape = CircleShape,
                    color = ZIndigo,
                    onClick = {
                        val t = typed; typed = ""
                        vm.sendConversationUtterance(t)
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Send, "إرسال", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            } else {
                LiveMicButton(
                    listening = speech.isListening,
                    enabled = !vm.isConversationThinking,
                    onClick = {
                        if (!micOk) { requestMic(); return@LiveMicButton }
                        if (speech.isListening) speech.stop()
                        else {
                            vm.stopConversationSpeech()
                            speech.start { vm.sendConversationUtterance(it) }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ConversationBubble(turn: ChatTurn, onSpeak: () -> Unit) {
    val mine = turn.fromLearner
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (mine) 16.dp else 4.dp,
                bottomEnd = if (mine) 4.dp else 16.dp,
            ),
            color = if (mine) ZIndigo else ZSurfaceVariant,
            modifier = Modifier.fillMaxWidth(0.86f),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    if (mine) "أنت" else "الشريك",
                    color = if (mine) Color.White.copy(alpha = 0.8f) else ZRose,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold,
                )
                Text(turn.en, color = if (mine) Color.White else ZTextPrimary, fontSize = 14.sp, lineHeight = 21.sp)
                if (turn.ar.isNotBlank() && !mine) {
                    Text(turn.ar, color = ZTextSecondary, fontSize = 12.sp)
                }
                if (turn.correction.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Surface(shape = RoundedCornerShape(8.dp), color = ZAmber.copy(alpha = 0.16f)) {
                        Text(
                            "تصحيح: ${turn.correction}",
                            color = ZAmberDeep, fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                if (turn.praise.isNotBlank()) {
                    Text(turn.praise, color = ZEmerald, fontSize = 11.sp)
                }
                if (!mine) {
                    Spacer(Modifier.height(4.dp))
                    IconButton(onClick = onSpeak, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.VolumeUp, "استمع", tint = ZCyanDeep, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveMicButton(listening: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "mic")
    val pulse by infinite.animateFloat(
        initialValue = 1f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "p",
    )
    Surface(
        shape = CircleShape,
        color = if (listening) ZRose else ZRose.copy(alpha = if (enabled) 1f else 0.4f),
        onClick = { if (enabled) onClick() },
        modifier = Modifier.size(56.dp).scale(if (listening) pulse else 1f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (listening) Icons.Filled.GraphicEq else Icons.Filled.Mic,
                "تحدّث",
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  الكتابة
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun WritingSkill(vm: AppViewModel) {
    val prompts = remember(vm.activeVocab.size, vm.lessons.toList()) {
        SkillsEngine.writingPrompts(vm.activeVocab, vm.lessons.toList())
    }
    var index by remember { mutableStateOf(0) }
    val prompt = prompts.getOrNull(index) ?: prompts.first()
    var text by remember { mutableStateOf("") }
    val words = if (text.isBlank()) 0 else text.trim().split(Regex("\\s+")).size

    SkillCard("تدريب الكتابة") {
        Surface(shape = RoundedCornerShape(14.dp), color = ZSurfaceVariant, onClick = {
            index = (index + 1) % prompts.size
            text = ""
            vm.clearWritingFeedback()
        }, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(prompt.promptAr, color = ZTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(prompt.promptEn, color = ZTextSecondary, fontSize = 12.sp)
                    if (prompt.targetWord.isNotBlank()) {
                        Text("الكلمة المطلوبة: ${prompt.targetWord}", color = ZCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Icon(Icons.Filled.Shuffle, "موضوع آخر", tint = ZIndigo)
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().height(140.dp),
            placeholder = { Text("اكتب هنا بالإنجليزية...", color = ZTextMuted) },
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors(),
        )
        Spacer(Modifier.height(8.dp))
        Text("عدد الكلمات: $words", color = ZCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { vm.evaluateWriting(text, prompt.promptEn, prompt.targetWord) },
            enabled = text.isNotBlank() && !vm.isEvaluatingWriting,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ZIndigo, disabledContainerColor = ZBorder),
        ) {
            if (vm.isEvaluatingWriting) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("جارٍ التصحيح…", fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Filled.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text(if (vm.hasAiKey) "صحّح بالذكاء الاصطناعي" else "قيّم الكتابة", fontWeight = FontWeight.Bold)
            }
        }
        vm.writingError?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = ZAmber, fontSize = 11.sp)
        }
        vm.writingFeedback?.let { fb ->
            Spacer(Modifier.height(12.dp))
            val color = when {
                fb.score >= 75 -> ZEmerald
                fb.score >= 50 -> ZAmber
                else -> ZRose
            }
            Surface(shape = RoundedCornerShape(16.dp), color = color.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("${fb.score}%", color = color, fontWeight = FontWeight.Black, fontSize = 24.sp)
                    if (fb.notesAr.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(fb.notesAr, color = ZTextSecondary, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                    if (fb.corrected.isNotBlank() && fb.corrected != text.trim()) {
                        Spacer(Modifier.height(8.dp))
                        Text("الصيغة المصحّحة", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(fb.corrected, color = ZTextPrimary, fontSize = 14.sp, lineHeight = 22.sp)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  الصوتيات
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun PhoneticsSkill(vm: AppViewModel) {
    val drills = remember(vm.lessons.toList()) { SkillsEngine.phoneticDrills(vm.lessons.toList()) }
    val sounds = drills.filter { !it.isPair }
    val pairs = drills.filter { it.isPair }
    var currentIndex by remember { mutableStateOf(-1) }
    var playingAll by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val tts = com.zmastery.english.audio.LocalTts.current
    val speech = rememberSpeechCapture()
    val (micOk, requestMic) = rememberMicPermission()
    var shadowTarget by remember { mutableStateOf<String?>(null) }
    var shadowScore by remember { mutableStateOf<SkillScore?>(null) }

    fun stopAll() {
        job?.cancel(); job = null; playingAll = false; currentIndex = -1
        tts?.stop()
    }

    fun playAll() {
        val engine = tts ?: return
        job?.cancel()
        playingAll = true
        job = scope.launch {
            for (i in sounds.indices) {
                currentIndex = i
                val words = sounds[i].examples.joinToString(". ")
                engine.speakInstant(words, "ph-all-$i")
            }
            currentIndex = -1
            playingAll = false
        }
    }

    SkillCard("تدريبات النطق") {
        Button(
            onClick = { if (playingAll) stopAll() else playAll() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (playingAll) ZRose else ZAmber),
        ) {
            Icon(if (playingAll) Icons.Filled.Stop else Icons.Filled.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text(if (playingAll) "إيقاف التشغيل" else "تشغيل كل الأصوات تباعاً", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text("استمع ثم ظلّل (Shadowing): سجّل نطقك بعد النموذج", color = ZTextMuted, fontSize = 11.sp)
        Spacer(Modifier.height(12.dp))

        sounds.forEachIndexed { i, drill ->
            PhoneticRow(
                drill = drill,
                active = currentIndex == i,
                onPlay = { currentIndex = i },
                onShadow = {
                    if (!micOk) { requestMic(); return@PhoneticRow }
                    val target = drill.examples.firstOrNull().orEmpty()
                    shadowTarget = target
                    shadowScore = null
                    if (speech.isListening) speech.stop()
                    else speech.start { spoken ->
                        val s = SkillsEngine.overlapScore(target, spoken)
                        shadowScore = s
                        vm.trackPhoneticsDrill()
                        vm.grantXp(if (s.percent >= 70) 12 else 5)
                    }
                },
            )
        }

        if (pairs.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("الأزواج الدنيا", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            pairs.take(12).forEach { drill ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    drill.examples.take(2).forEach { w ->
                        Surface(shape = RoundedCornerShape(12.dp), color = ZSurfaceVariant, modifier = Modifier.weight(1f)) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(w, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                AudioButton(text = w, audioKey = "pair-$w", accent = ZCyan, size = 32.dp, iconSize = 16.dp)
                            }
                        }
                    }
                }
            }
        }

        speech.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = ZRose, fontSize = 11.sp)
        }
        if (speech.isListening) {
            Spacer(Modifier.height(8.dp))
            Text("ظلّل «${shadowTarget.orEmpty()}» الآن…", color = ZAmber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        shadowScore?.let {
            Spacer(Modifier.height(12.dp))
            ScoreBanner(it)
        }
    }
}

@Composable
private fun PhoneticRow(
    drill: PhoneticDrill,
    active: Boolean,
    onPlay: () -> Unit,
    onShadow: () -> Unit,
) {
    val sample = drill.examples.joinToString(". ")
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (active) ZAmber.copy(alpha = 0.16f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (active) ZAmber else ZAmber.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(drill.symbol, color = if (active) Color.White else ZAmber, fontWeight = FontWeight.Black, fontSize = 14.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(drill.examples.joinToString(", "), color = ZTextPrimary, fontSize = 13.sp)
                if (drill.description.isNotBlank()) {
                    Text(drill.description, color = ZTextMuted, fontSize = 10.sp, maxLines = 2)
                }
            }
            AudioButton(text = sample, audioKey = "ph-${drill.id}", accent = ZAmber, size = 36.dp, iconSize = 18.dp, onPlayed = onPlay)
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = onShadow) {
                Icon(Icons.Filled.Mic, "ظلّل", tint = ZRose, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = ZSurfaceVariant, unfocusedContainerColor = ZSurfaceVariant,
    focusedBorderColor = ZIndigo, unfocusedBorderColor = ZBorder,
    focusedTextColor = ZTextPrimary, unfocusedTextColor = ZTextPrimary,
)
