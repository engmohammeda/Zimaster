package com.zmastery.english.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.data.SampleData
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel

private data class Skill(val key: String, val title: String, val desc: String, val icon: ImageVector, val colors: List<Color>)

private val skills = listOf(
    Skill("reading", "القراءة", "اقرأ القطع وسجل قراءتك وقارنها بالنموذج", Icons.Filled.AutoStories, listOf(ZCyanDeep, ZCyan)),
    Skill("listening", "الاستماع", "قصص صوتية مبنية على كلمات دروسك", Icons.Filled.Headphones, listOf(ZEmerald, ZEmeraldDeep)),
    Skill("speaking", "التحدث", "محادثة تستند لحوارات الدروس", Icons.Filled.RecordVoiceOver, listOf(ZRose, Color(0xFFE11D48))),
    Skill("writing", "الكتابة", "تدريبات كتابة يدوية ولوحة داخلية", Icons.Filled.Edit, listOf(ZCyanDeep, ZIndigo)),
    Skill("phonetics", "الصوتيات", "تدريبات نطق الحروف من كورس الصوتيات", Icons.Filled.GraphicEq, listOf(ZAmber, ZRoseDeep)),
)

@Composable
fun SkillsScreen(vm: AppViewModel) {
    var active by remember { mutableStateOf<String?>(null) }
    if (active != null) {
        SkillDetail(active!!, vm) { active = null }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text("المهارات الخمس", color = ZTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text("كل مهارة مرتبطة ببيانات الكورس المستوردة", color = ZTextSecondary, fontSize = 13.sp)
        }
        items(skills, key = { it.key }) { skill ->
            Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 5.dp, modifier = Modifier.fillMaxWidth(), onClick = { active = skill.key }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(skill.colors)), contentAlignment = Alignment.Center) {
                        Icon(skill.icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
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
    val skill = skills.first { it.key == key }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack) { Icon(Icons.Filled.ArrowForward, null, tint = ZCyan); Spacer(Modifier.width(8.dp)); Text("الرجوع", color = ZCyan) }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(skill.colors)).padding(24.dp)) {
            Column {
                Icon(skill.icon, null, tint = Color.White, modifier = Modifier.size(38.dp))
                Spacer(Modifier.height(12.dp))
                Text(skill.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text(skill.desc, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
            }
        }
        when (key) {
            "reading" -> ReadingSkill(vm)
            "listening" -> ListeningSkill()
            "speaking" -> SpeakingSkill(vm)
            "writing" -> WritingSkill(vm)
            else -> PhoneticsSkill()
        }
        Spacer(Modifier.height(80.dp))
    }
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
private fun ReadingSkill(vm: AppViewModel) {
    val lesson = vm.lessons.firstOrNull { it.readingEn.isNotBlank() }
    var recording by remember { mutableStateOf(false) }
    SkillCard("قطعة القراءة") {
        Text(lesson?.readingEn ?: "لا توجد قطعة", color = ZTextPrimary, fontSize = 16.sp, lineHeight = 26.sp)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { recording = !recording },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (recording) ZRose else ZIndigo),
        ) {
            Icon(if (recording) Icons.Filled.Stop else Icons.Filled.Mic, null); Spacer(Modifier.width(8.dp))
            Text(if (recording) "جارٍ التسجيل... اضغط للإيقاف" else "سجل قراءتك", fontWeight = FontWeight.Bold)
        }
        if (!recording) {
            Spacer(Modifier.height(12.dp))
            Text("ستتم مقارنة تسجيلك بالنموذج لتقييم النطق", color = ZTextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ListeningSkill() {
    val story = SampleData.stories.firstOrNull()
    SkillCard("قصة صوتية مولّدة") {
        if (story == null) {
            Text("ستُولّد قصة صوتية بناءً على كلمات وقواعد دروسك المكتملة بعد الاستيراد.", color = ZTextSecondary, fontSize = 14.sp, lineHeight = 24.sp)
            return@SkillCard
        }
        Text(story.title, color = ZCyan, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(story.en, color = ZTextSecondary, fontSize = 14.sp, lineHeight = 24.sp)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(ZEmerald, ZCyan))), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(12.dp))
            LinearProgressIndicator(progress = { 0.35f }, modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(4.dp)), color = ZEmerald, trackColor = ZBorder)
        }
        Spacer(Modifier.height(12.dp))
        Text("مبنية على الكلمات والقواعد من الدروس المكتملة", color = ZTextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun SpeakingSkill(vm: AppViewModel) {
    val lesson = vm.lessons.firstOrNull { it.dialogues.isNotEmpty() }
    SkillCard("محادثة مبنية على حوار الدرس") {
        if (lesson != null) {
            lesson.dialogues.forEachIndexed { i, d ->
                val mine = i % 2 == 1
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Surface(shape = RoundedCornerShape(16.dp), color = if (mine) ZIndigo else ZSurfaceVariant, modifier = Modifier.fillMaxWidth(0.85f)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(d.speaker, color = if (mine) Color.White.copy(alpha = 0.8f) else ZCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(d.en, color = if (mine) Color.White else ZTextPrimary, fontSize = 14.sp)
                            Text(d.ar, color = if (mine) Color.White.copy(alpha = 0.75f) else ZTextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { vm.completeTask("speak") }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = ZRose)) {
                Icon(Icons.Filled.Mic, null); Spacer(Modifier.width(8.dp)); Text("تدرّب على الرد", fontWeight = FontWeight.Bold)
            }
        } else {
            Text("لا توجد حوارات مستوردة بعد", color = ZTextSecondary)
        }
    }
}

@Composable
private fun WritingSkill(vm: AppViewModel) {
    var text by remember { mutableStateOf("") }
    val targetWord = vm.vocab.firstOrNull()?.english
    SkillCard("تدريب الكتابة") {
        Text(if (targetWord != null) "اكتب جملة تستخدم فيها كلمة: $targetWord" else "اكتب جملة قصيرة بالإنجليزية للتدريب", color = ZTextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            placeholder = { Text("اكتب هنا...", color = ZTextMuted) },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = ZSurfaceVariant, unfocusedContainerColor = ZSurfaceVariant,
                focusedBorderColor = ZIndigo, unfocusedBorderColor = ZBorder,
                focusedTextColor = ZTextPrimary, unfocusedTextColor = ZTextPrimary,
            ),
        )
        Spacer(Modifier.height(12.dp))
        Text("عدد الكلمات: ${if (text.isBlank()) 0 else text.trim().split(" ").size}", color = ZCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PhoneticsSkill() {
    val sounds = listOf(
        "/θ/" to "think, three",
        "/ð/" to "this, that",
        "/ʃ/" to "she, ship",
        "/tʃ/" to "chair, watch",
        "/ŋ/" to "sing, ring",
    )
    var playingAll by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableStateOf(-1) } // -1 = none highlighted
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun stopAll() {
        job?.cancel(); job = null; playingAll = false; currentIndex = -1
    }

    fun playAll() {
        job?.cancel()
        playingAll = true
        job = scope.launch {
            for (i in sounds.indices) {
                currentIndex = i
                // Each pair "plays" for ~1.1s before advancing to the next.
                kotlinx.coroutines.delay(1100)
            }
            currentIndex = -1
            playingAll = false
        }
    }

    SkillCard("تدريبات النطق") {
        // Play-all / stop control
        Button(
            onClick = { if (playingAll) stopAll() else playAll() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (playingAll) ZRose else ZAmber),
        ) {
            Icon(if (playingAll) Icons.Filled.Stop else Icons.Filled.PlaylistPlay, null)
            Spacer(Modifier.width(8.dp))
            Text(if (playingAll) "إيقاف التشغيل" else "تشغيل كل الأزواج تباعاً", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text("يمكنك أيضاً تشغيل كل زوج على حدة بالنقر عليه", color = ZTextMuted, fontSize = 11.sp)
        Spacer(Modifier.height(12.dp))

        sounds.forEachIndexed { i, pair ->
            val (sym, ex) = pair
            val active = currentIndex == i
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (active) ZAmber.copy(alpha = 0.16f) else Color.Transparent,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                onClick = { if (!playingAll) currentIndex = i },
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (active) ZAmber else ZAmber.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(sym, color = if (active) Color.White else ZAmber, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(ex, color = ZTextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Icon(
                        if (active) Icons.Filled.GraphicEq else Icons.Filled.VolumeUp,
                        null, tint = if (active) ZAmber else ZCyan,
                    )
                }
            }
        }
    }
}
