package com.zmastery.english.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 4-stage progressive spaced-repetition review.
 *
 *  Stage 1: Audio only — listen (auto-played) and try to recall.
 *  Stage 2: Mental image + audio.
 *  Stage 3: Word + phonetic + example + audio (NO translation).
 *  Stage 4: Full reveal (translation + everything).
 *
 * ── Quick rating ──────────────────────────────────────────────────────────
 * The learner is NOT forced through all four stages. A single positive action
 * ("تذكرتها") is available at every stage. Pressing it immediately files the
 * review and jumps to the next word. Behind the scenes the *stage* at which it
 * was pressed silently decides the FSRS rating:
 *
 *   Stage 1 → Easy (4)  ·  Stage 2 → Good (3)  ·  Stage 3 → Hard (2)
 *   Stage 4 "نسيتها"    → Again (1)
 *
 * So the UI shows one button, while the scheduler receives a precise signal.
 *
 * ── Audio ─────────────────────────────────────────────────────────────────
 * Pronunciation auto-plays the moment each stage appears (all 4 stages), and
 * stays replayable by tapping the speaker. Auto-play is user-toggleable in
 * Settings → التعلم.
 *
 * We log per-word analytics: listens, time spent, and the recall stage.
 */
@Composable
fun ReviewScreen(vm: AppViewModel) {
    // Measure real time-on-screen for study analytics.
    com.zmastery.english.ui.components.TrackStudyTime(vm, "review")
    val queue = remember { mutableStateListOf<Int>() }
    var started by remember { mutableStateOf(false) }
    var index by remember { mutableStateOf(0) }
    var stage by remember { mutableStateOf(1) }
    var reviewedCount by remember { mutableStateOf(0) }
    var recalledCount by remember { mutableStateOf(0) }
    var sessionListens by remember { mutableStateOf(0) }

    // Per-card tracking
    var listens by remember { mutableStateOf(0) }        // total plays (auto + manual)
    var manualReplays by remember { mutableStateOf(0) }  // taps on the speaker
    var cardStartMs by remember { mutableStateOf(0L) }

    val tts = com.zmastery.english.audio.LocalTts.current
    val scope = rememberCoroutineScope()

    if (!started) {
        ReviewIntro(vm) {
            val ids = vm.dueWords.map { it.id }.ifEmpty { vm.activeVocab.map { it.id } }
            if (ids.isNotEmpty()) {
                queue.clear()
                queue.addAll(ids)
                index = 0; stage = 1; reviewedCount = 0; recalledCount = 0; sessionListens = 0
                listens = 0; manualReplays = 0
                cardStartMs = System.currentTimeMillis(); started = true
            }
        }
        return
    }

    if (index >= queue.size) {
        ReviewComplete(reviewedCount, recalledCount, sessionListens, vm) { started = false }
        return
    }

    val word = vm.vocab.firstOrNull { it.id == queue[index] } ?: return

    /** Move to the next card, resetting per-card counters. */
    fun advanceCard() {
        tts?.stop()
        index++
        stage = 1
        listens = 0
        manualReplays = 0
        cardStartMs = System.currentTimeMillis()
    }

    /** "تذكرتها" — file the review with the grade implied by the current stage. */
    fun recalledNow() {
        val elapsed = System.currentTimeMillis() - cardStartMs
        vm.reviewWordAtStage(word.id, stage = stage, replays = listens, timeMs = elapsed)
        reviewedCount++
        recalledCount++
        // مهمة الإنقاذ: كل بطاقة يتذكّرها المتعلّم تقرّبه من استعادة شعلته.
        vm.advanceRescue()
        advanceCard()
    }

    /** "نسيتها" — full lapse (only offered once all cues were shown). */
    fun forgotNow() {
        val elapsed = System.currentTimeMillis() - cardStartMs
        vm.failWord(word.id, reachedStage = stage, replays = listens, timeMs = elapsed)
        reviewedCount++
        advanceCard()
    }

    /** The English text spoken at a given stage — word alone early, word+example later. */
    fun phraseFor(s: Int): String =
        if (s >= 3 && word.exampleEn.isNotBlank()) "${word.english}. ${word.exampleEn}" else word.english

    val audioKey = "rev_${word.id}_$stage"
    val isPlaying = tts?.speakingKey == audioKey
    // Real mnemonic tile for this word (null = fall back to the text hint box).
    val tilePath = vm.mnemonicPath(word.id)

    fun playNow(manual: Boolean) {
        if (tts == null) return
        if (manual) manualReplays++
        listens++
        sessionListens++
        scope.launch {
            val t0 = System.currentTimeMillis()
            tts.speakInstant(phraseFor(stage), audioKey)
            vm.trackListening((System.currentTimeMillis() - t0) / 1000)
        }
    }

    // ── Auto-play on every stage of every card ────────────────────────────
    LaunchedEffect(word.id, stage, vm.reviewAutoPlay) {
        if (!vm.reviewAutoPlay || tts == null) return@LaunchedEffect
        delay(320) // let the stage transition settle before speaking
        listens++
        sessionListens++
        val t0 = System.currentTimeMillis()
        tts.speakInstant(phraseFor(stage), audioKey)
        vm.trackListening((System.currentTimeMillis() - t0) / 1000)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // ── شريط مهمة الإنقاذ: عدّاد حيّ يظهر فقط أثناء المهمة ──
        RescueTimerBar(vm)

        LinearProgressIndicator(
            progress = { index.toFloat() / queue.size },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(6.dp)),
            color = ZIndigo, trackColor = ZBorder,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1} / ${queue.size}", color = ZTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            StageIndicator(stage)
        }
        Spacer(Modifier.height(14.dp))

        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(28.dp),
            color = ZCard,
            shadowElevation = 6.dp,
        ) {
            AnimatedContent(
                stage,
                transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(140)) },
                label = "stage",
            ) { s ->
                // Centered when short, scrollable when tall — so text is never clipped
                // by the audio bubble or the mental-image box.
                Box(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        when (s) {
                            1 -> {
                                AudioBubble(listens, manualReplays, isPlaying) { playNow(true) }
                                Spacer(Modifier.height(20.dp))
                                StageBadge("المرحلة 1", "استمع وتذكّر", ZIndigo)
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "استمع للنطق وحاول تذكّر معنى الكلمة.\nإن تذكّرتها الآن اضغط «تذكرتها» فوراً.",
                                    color = ZTextMuted, fontSize = 13.sp,
                                    textAlign = TextAlign.Center, lineHeight = 20.sp,
                                )
                            }
                            2 -> {
                                AudioBubble(listens, manualReplays, isPlaying, size = 72) { playNow(true) }
                                Spacer(Modifier.height(16.dp))
                                MnemonicView(tilePath, word.mentalImage, big = true)
                                Spacer(Modifier.height(14.dp))
                                StageBadge("المرحلة 2", "الصورة الذهنية", ZAmber)
                            }
                            3 -> {
                                // Compact header keeps the word + example fully visible.
                                CompactAudioRow(listens, manualReplays, isPlaying, ZCyanDeep) { playNow(true) }
                                if (tilePath != null) {
                                    Spacer(Modifier.height(14.dp))
                                    MnemonicView(tilePath, word.mentalImage, big = false)
                                }
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    word.english, color = ZTextPrimary, fontSize = 34.sp,
                                    fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                                    lineHeight = 40.sp,
                                )
                                if (word.phonetic.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(word.phonetic, color = ZCyanDeep, fontSize = 15.sp, textAlign = TextAlign.Center)
                                }
                                if (word.exampleEn.isNotBlank()) {
                                    Spacer(Modifier.height(14.dp))
                                    Surface(shape = RoundedCornerShape(14.dp), color = ZSurfaceVariant) {
                                        Text(
                                            word.exampleEn, color = ZTextSecondary, fontSize = 15.sp,
                                            textAlign = TextAlign.Center, lineHeight = 24.sp,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        )
                                    }
                                }
                                if (tilePath == null && word.mentalImage.isNotBlank()) {
                                    Spacer(Modifier.height(12.dp))
                                    HintChip(word.mentalImage)
                                }
                                Spacer(Modifier.height(14.dp))
                                StageBadge("المرحلة 3", "الكلمة والمثال (بدون ترجمة)", ZCyanDeep)
                            }
                            else -> {
                                CompactAudioRow(listens, manualReplays, isPlaying, ZEmerald) { playNow(true) }
                                if (tilePath != null) {
                                    Spacer(Modifier.height(14.dp))
                                    MnemonicView(tilePath, word.mentalImage, big = false)
                                }
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    word.english, color = ZTextPrimary, fontSize = 30.sp,
                                    fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                                    lineHeight = 36.sp,
                                )
                                if (word.phonetic.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(word.phonetic, color = ZCyanDeep, fontSize = 14.sp, textAlign = TextAlign.Center)
                                }
                                Spacer(Modifier.height(10.dp))
                                Surface(shape = RoundedCornerShape(14.dp), color = ZEmerald.copy(alpha = 0.12f)) {
                                    Text(
                                        word.arabic, color = ZEmerald, fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                                        lineHeight = 32.sp,
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                                    )
                                }
                                if (word.exampleEn.isNotBlank() || word.exampleAr.isNotBlank()) {
                                    Spacer(Modifier.height(12.dp))
                                    HorizontalDivider(color = ZBorder, modifier = Modifier.fillMaxWidth(0.4f))
                                    Spacer(Modifier.height(12.dp))
                                    if (word.exampleEn.isNotBlank()) {
                                        Text(word.exampleEn, color = ZTextSecondary, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 23.sp)
                                    }
                                    if (word.exampleAr.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(word.exampleAr, color = ZTextMuted, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 21.sp)
                                    }
                                }
                                if (tilePath == null && word.mentalImage.isNotBlank()) {
                                    Spacer(Modifier.height(12.dp))
                                    HintChip(word.mentalImage)
                                }
                                Spacer(Modifier.height(12.dp))
                                StageBadge("المرحلة 4", "الكشف الكامل", ZEmerald)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        StageActions(
            stage = stage,
            recalledInterval = vm.formatInterval(vm.previewStageIntervalDays(word.id, stage)),
            forgotInterval = vm.formatInterval(vm.previewFailIntervalDays(word.id)),
            onRecalled = ::recalledNow,
            onForgot = ::forgotNow,
            onNextStage = { tts?.stop(); stage++ },
        )
        Spacer(Modifier.height(70.dp))
    }
}

/**
 * شريط العدّ التنازلي لمهمة الإنقاذ.
 *
 * يظهر فقط أثناء وجود مهمة جارية، ويحدّث نفسه كل ثانية عبر [LaunchedEffect]
 * (مؤقّت واحد فقط داخل التركيب — يتوقف تلقائياً عند الخروج من الشاشة).
 * عند انتهاء المهلة يُبلّغ الـ ViewModel ليصفّر المحاولة دون أي عقوبة.
 */
@Composable
private fun RescueTimerBar(vm: AppViewModel) {
    val r = vm.rescue
    if (!r.isActive || r.completed || !r.isRunning) return

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(r.startedAtMs) {
        while (true) {
            now = System.currentTimeMillis()
            if (r.isExpired(now)) {
                vm.timeoutRescue()
                break
            }
            delay(250)
        }
    }

    val msLeft = r.msLeft(now)
    val fraction = (msLeft.toFloat() / com.zmastery.english.data.RescueMission.LIMIT_MS)
        .coerceIn(0f, 1f)
    val urgent = msLeft < 45_000L
    val violet = Color(0xFF8B5CF6)
    val accent = if (urgent) ZRose else violet

    val inf = rememberInfiniteTransition(label = "rescueBar")
    val pulse by inf.animateFloat(
        initialValue = 1f, targetValue = if (urgent) 1.10f else 1f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "rbp",
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.14f),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Shield, null, tint = accent,
                    modifier = Modifier.size(18.dp).scale(pulse),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "مهمة إنقاذ — أنقذ ${r.streakToRestore} يوماً",
                        color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 13.sp,
                    )
                    Text(
                        "تذكّر ${r.remaining} بطاقة أخرى بنجاح",
                        color = ZTextSecondary, fontSize = 11.sp,
                    )
                }
                Text(
                    r.timerLabel(now),
                    color = accent, fontWeight = FontWeight.Black, fontSize = 20.sp,
                    modifier = Modifier.scale(pulse),
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                color = accent, trackColor = ZBorder,
            )
        }
    }
}

/**
 * The action bar. One positive button at every stage ("تذكرتها") plus a way to
 * ask for the next cue. At the final stage the escape hatch becomes "نسيتها".
 */
@Composable
private fun StageActions(
    stage: Int,
    recalledInterval: String,
    forgotInterval: String,
    onRecalled: () -> Unit,
    onForgot: () -> Unit,
    onNextStage: () -> Unit,
) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            // ── Primary: recalled it (grade derived from the current stage) ──
            Surface(
                modifier = Modifier.weight(1f).height(58.dp),
                shape = RoundedCornerShape(16.dp),
                color = ZEmerald,
                onClick = onRecalled,
            ) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("تذكرتها", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                        Text(recalledInterval, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            // ── Secondary: need another cue, or admit the lapse at stage 4 ──
            if (stage < 4) {
                Surface(
                    modifier = Modifier.weight(1f).height(58.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = ZSurfaceVariant,
                    onClick = onNextStage,
                ) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Filled.NavigateBefore, null, tint = ZIndigo, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(6.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                when (stage) {
                                    1 -> "أرني الصورة"
                                    2 -> "أرني الكلمة"
                                    else -> "اكشف الترجمة"
                                },
                                color = ZIndigo, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            )
                            Text("لم أتذكّر بعد", color = ZTextMuted, fontSize = 10.sp)
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.weight(1f).height(58.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = ZRose.copy(alpha = 0.16f),
                    onClick = onForgot,
                ) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Filled.Cancel, null, tint = ZRose, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("نسيتها", color = ZRose, fontWeight = FontWeight.Black, fontSize = 16.sp)
                            Text(forgotInterval, color = ZRose.copy(alpha = 0.75f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when (stage) {
                1 -> "تذكّرتها من الصوت وحده؟ هذا أقوى تذكّر — سيطول موعد المراجعة كثيراً"
                2 -> "تذكّرتها بمساعدة الصورة — تذكّر جيد"
                3 -> "احتجت لرؤية الكلمة — سيقرب موعد المراجعة"
                else -> "قيّم بصدق: التقدير الدقيق يجعل الجدولة أذكى"
            },
            color = ZTextMuted, fontSize = 11.sp, textAlign = TextAlign.Center,
            lineHeight = 17.sp, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun HintChip(text: String) {
    Surface(shape = RoundedCornerShape(50), color = ZAmber.copy(alpha = 0.12f)) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Lightbulb, null, tint = ZAmber, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, color = ZTextSecondary, fontSize = 12.sp, maxLines = 2, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun StageBadge(stage: String, title: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f)) {
            Text(stage, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(title, color = ZTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StageIndicator(stage: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..4).forEach { s ->
            Box(
                Modifier.size(width = 22.dp, height = 6.dp).clip(RoundedCornerShape(3.dp))
                    .background(if (s <= stage) ZIndigo else ZBorder)
            )
        }
    }
}

/** Large animated, replayable audio button. Pulses while playing. */
@Composable
private fun AudioBubble(
    listens: Int,
    manualReplays: Int,
    isPlaying: Boolean = false,
    size: Int = 96,
    onPlay: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f, targetValue = if (isPlaying) 1.12f else 1.05f,
        animationSpec = infiniteRepeatable(tween(if (isPlaying) 500 else 900), RepeatMode.Reverse), label = "p",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(size.dp).scale(pulse),
            shape = RoundedCornerShape(size.dp / 2),
            color = Color.Transparent,
            onClick = onPlay,
        ) {
            Box(
                Modifier.background(Brush.linearGradient(listOf(ZIndigo, ZPurple))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.GraphicEq else Icons.Filled.VolumeUp,
                    "استمع", tint = Color.White,
                    modifier = Modifier.size((size * 0.48f).dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        ListenCaption(listens, manualReplays, isPlaying)
    }
}

/** Space-saving audio control for the text-heavy stages (3 & 4). */
@Composable
private fun CompactAudioRow(
    listens: Int,
    manualReplays: Int,
    isPlaying: Boolean,
    accent: Color,
    onPlay: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "cpulse")
    val pulse by transition.animateFloat(
        initialValue = 1f, targetValue = if (isPlaying) 1.10f else 1f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "cp",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(52.dp).scale(pulse),
            shape = RoundedCornerShape(26.dp),
            color = Color.Transparent,
            onClick = onPlay,
        ) {
            Box(
                Modifier.background(Brush.linearGradient(listOf(ZIndigo, ZPurple))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.GraphicEq else Icons.Filled.VolumeUp,
                    "استمع", tint = Color.White, modifier = Modifier.size(26.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        ListenCaption(listens, manualReplays, isPlaying, accent)
    }
}

@Composable
private fun ListenCaption(listens: Int, manualReplays: Int, isPlaying: Boolean, accent: Color = ZTextMuted) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (isPlaying) Icons.Filled.VolumeUp else Icons.Filled.Replay,
            null, tint = accent, modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            when {
                isPlaying -> "يُنطق الآن…"
                listens == 0 -> "اضغط للاستماع"
                manualReplays > 0 -> "استمعت $listens مرة"
                else -> "شُغّل تلقائياً · اضغط للتكرار"
            },
            color = accent, fontSize = 11.sp,
        )
    }
}

/**
 * Shows the sliced mnemonic tile when one exists, otherwise the textual
 * mental-image hint. Sized so the word/example/translation always stay visible.
 */
@Composable
private fun MnemonicView(tilePath: String?, fallbackText: String, big: Boolean) {
    if (tilePath != null) {
        val side = if (big) 190.dp else 132.dp
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            coil3.compose.AsyncImage(
                model = java.io.File(tilePath),
                contentDescription = "الرابط الذهني",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(side).clip(RoundedCornerShape(20.dp)),
            )
            if (big && fallbackText.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                HintChip(fallbackText)
            }
        }
    } else {
        MentalImageBox(fallbackText, small = !big)
    }
}

@Composable
private fun MentalImageBox(image: String, small: Boolean = false) {
    Box(
        Modifier.fillMaxWidth(if (small) 0.55f else 0.78f)
            .height(if (small) 88.dp else 132.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(ZSurfaceVariant, ZBackground))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(12.dp)) {
            Icon(Icons.Filled.Image, null, tint = ZAmber, modifier = Modifier.size(if (small) 26.dp else 34.dp))
            if (image.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(image, color = ZTextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 3, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun ReviewIntro(vm: AppViewModel, onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.size(100.dp).clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(ZIndigo, ZPurple))),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Psychology, null, tint = Color.White, modifier = Modifier.size(56.dp)) }
        Spacer(Modifier.height(20.dp))
        Text("المراجعة المتدرجة", color = ZTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text(
            "قيّم الكلمة فور تذكّرها — لا حاجة لإكمال كل المراحل",
            color = ZTextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 5.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                StageHint("1", "الصوت فقط", "تذكّرتها؟ اضغط «تذكرتها» ← أقوى تثبيت", ZIndigo)
                StageHint("2", "الصورة الذهنية", "تذكّرتها هنا؟ تثبيت جيد", ZAmber)
                StageHint("3", "الكلمة + المثال", "دون الترجمة — تثبيت أقصر", ZCyanDeep)
                StageHint("4", "الكشف الكامل", "الترجمة وكل شيء — أو «نسيتها»", ZEmerald)
                Spacer(Modifier.height(10.dp))
                Surface(shape = RoundedCornerShape(14.dp), color = ZEmerald.copy(alpha = 0.10f)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = ZEmerald, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "زر واحد فقط: «تذكرتها». يحتسب المحرك تقييمك تلقائياً حسب المرحلة التي تذكّرت فيها.",
                            color = ZTextSecondary, fontSize = 12.sp, lineHeight = 19.sp,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(14.dp), color = ZIndigo.copy(alpha = 0.10f)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.VolumeUp, null, tint = ZIndigo, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (vm.reviewAutoPlay) "النطق يعمل تلقائياً في كل مرحلة"
                            else "التشغيل التلقائي مُعطّل — فعّله من الإعدادات › التعلم",
                            color = ZTextSecondary, fontSize = 12.sp, lineHeight = 19.sp,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        val empty = vm.activeVocab.isEmpty()
        Surface(shape = RoundedCornerShape(16.dp), color = ZCard, shadowElevation = 5.dp, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (empty) Icons.Filled.UploadFile else Icons.Filled.Schedule, null, tint = ZIndigo)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (empty) "لا توجد كلمات بعد — استورد كورساً لتبدأ" else "${vm.dueWords.size} كلمة مستحقة الآن",
                    color = ZTextPrimary, fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onStart,
            enabled = !empty,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ZIndigo, disabledContainerColor = ZBorder),
        ) {
            Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(8.dp))
            Text("ابدأ المراجعة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun StageHint(num: String, title: String, sub: String, color: Color) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Text(num, color = color, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ZTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(sub, color = ZTextSecondary, fontSize = 11.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun ReviewComplete(count: Int, recalled: Int, listens: Int, vm: AppViewModel, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        Box(
            Modifier.size(110.dp).clip(RoundedCornerShape(55.dp)).background(Brush.linearGradient(listOf(ZEmerald, ZCyanDeep))),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.CheckCircle, null, tint = Color.White, modifier = Modifier.size(64.dp)) }
        Spacer(Modifier.height(24.dp))
        Text("أحسنت! \uD83C\uDF89", color = ZTextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text("أكملت مراجعة $count كلمة", color = ZTextSecondary, fontSize = 15.sp)
        Spacer(Modifier.height(20.dp))
        // Session insights
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InsightChip(Icons.Filled.CheckCircle, "$recalled/$count", "تذكّرتها")
            InsightChip(Icons.Filled.Replay, "$listens", "مرات استماع")
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InsightChip(Icons.Filled.Timer, "${vm.avgSecondsPerWord.toInt()}ث", "متوسط/كلمة")
            InsightChip(Icons.Filled.Insights, "${(vm.trueRecallRate * 100).toInt()}%", "نسبة التذكّر")
        }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ZIndigo),
        ) { Text("العودة", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun InsightChip(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = ZCard, shadowElevation = 4.dp) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = ZIndigo, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(value, color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text(label, color = ZTextSecondary, fontSize = 11.sp)
            }
        }
    }
}
