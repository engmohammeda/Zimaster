package com.zmastery.english.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.data.DailyTask
import com.zmastery.english.data.DayStat
import com.zmastery.english.data.Engagement
import com.zmastery.english.data.LearnerStage
import com.zmastery.english.data.PlanItem
import com.zmastery.english.data.Telemetry
import com.zmastery.english.data.VocabWord
import com.zmastery.english.ui.components.SectionTitle
import com.zmastery.english.ui.components.SoftCard
import com.zmastery.english.ui.components.StreakTopBar
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The home screen. Its whole job is to answer, in ten seconds:
 *   ماذا أدرس اليوم؟ · هل أنا متأخر؟ · ماذا نسيت؟
 *
 * Every claim it makes is derived from real telemetry. On a fresh install it
 * says "let's begin" — never "your streak is excellent" or "you finished
 * today's plan", which is what the old hardcoded defaults did.
 */
@Composable
fun DashboardScreen(vm: AppViewModel, onNavigate: (String) -> Unit, onOpenLesson: (Int) -> Unit) {
    // Roll the per-day counters over if the calendar day changed while the
    // process was alive (or since the last launch).
    LaunchedEffect(Unit) {
        vm.applyDayRollover()
        vm.rebuildDailyPlan()
    }
    // Keep the plan honest when content changes (import / new words).
    LaunchedEffect(vm.activeVocab.size, vm.lessons.size) { vm.rebuildDailyPlan(force = true) }

    val eng = vm.engagement
    var rescueWin by remember { mutableStateOf(0) }
    val (cefr, _) = Telemetry.estimatedCefr(vm.masteredCount, vm.completedLessons, vm.lifetime.examAvg)

    Column(Modifier.fillMaxSize()) {
        // ═══ شريط الحماسة العلوي (بأسلوب Duolingo) — بوابة كل ما يخص الزخم ═══
        StreakTopBar(
            streak = eng.streak,
            xp = vm.xp,
            shields = vm.wallet.streakFreezes,
            cefr = cefr,
            conditionProgress = vm.streakConditionProgress,
            conditionLabel = vm.streakConditionLabel,
            dayEarned = vm.dayEarnedStreak,
            onOpen = { onNavigate("momentum") },
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // ── 0 · إعلانات وتنبيهات الإدارة السحابية ──
            val ann = vm.activeAnnouncement
            if (ann != null) {
                item {
                    AnnouncementCard(
                        announcement = ann,
                        isAdmin = vm.isAdmin,
                        onDismiss = { vm.dismissActiveAnnouncement() },
                        onDeactivate = { vm.deactivateAnnouncement(ann.id) },
                    )
                }
            }

            // ── 1 · حالات عاجلة فقط (إنقاذ السلسلة) ──
            if (vm.showRescueGate) {
                item {
                    RescueGateCard(
                        rescue = vm.rescue,
                        onStart = { vm.startRescueTimer(); onNavigate(vm.rescue.kindEnum.route) },
                        onClaim = { rescueWin = vm.claimRescue() },
                        onDismiss = { vm.dismissRescue() },
                    )
                }
            }

            // ── 2 · دليل البدء للمستخدم الجديد ──
            if (eng.stage == LearnerStage.EMPTY) {
                item { GetStartedCard(vm, onNavigate) }
                item { DailyPhraseCard() }
                item { Spacer(Modifier.height(90.dp)) }
                return@LazyColumn
            }

            // ── 3 · الورد اليومي (أخف طريق لتأمين اليوم) ──
            if (!vm.microHabitDone && !vm.dayEarnedStreak) {
                item { MicroHabitRow(vm) { onNavigate(vm.microHabit.route) } }
            }

            // ── 4 · خطة اليوم — قلب الشاشة (أقصر مسافة بصرية للأهم) ──
            item {
                SectionTitle(
                    "خطة اليوم",
                    if (vm.activeDailyTasks.isEmpty()) vm.planRationale
                    else "${vm.activeTasksDone} / ${vm.activeDailyTasks.size} مكتملة · ${todayLabel()}",
                )
            }
            if (vm.activeDailyTasks.isEmpty()) {
                item { NoTasksYet(onNavigate) }
            } else {
                item { PlanTierNote(vm) }
                items(vm.activeDailyTasks, key = { it.id }) { task ->
                    TaskRow(task) { onNavigate(routeForTask(task.id)) }
                }
                if (vm.planCompleteToday) {
                    item { AllDoneBanner() }
                }
            }

            // ── 5 · المذاكرة العاجلة — المراجعة قبل الجديد (مبدأ التكرار المتباعد) ──
            if (vm.forgottenWords.isNotEmpty()) {
                item { SectionTitle("المذاكرة العاجلة \uD83D\uDEA8", "كلمات على وشك النسيان — راجعها أولاً") }
                item { ForgottenWordsRow(vm.forgottenWords) { onNavigate("review") } }
            }

            // ── 6 · دروس اليوم ──
            if (vm.todayPlan.isNotEmpty()) {
                item { SectionTitle("دروس اليوم", "مختارة من كورساتك") }
                items(vm.todayPlan, key = { it.lessonId }) { PlanCard(it) { onOpenLesson(it.lessonId) } }
            }

            // ── 7 · لافتة الحالة — تحفيز بعد إنجاز الخطة، لا قبلها ──
            item { MascotBanner(eng) }

            // ── 8 · قصة اليوم ──
            item { DailyStoryCard(vm, onOpenArchive = { onNavigate("stories") }) }

            // ── 9 · عبارة اليوم (من المكتبة الأسبوعية) ──
            item { DailyPhraseCard() }

            // ── 10 · حالة توليد الصوت (عند العمل فقط) ──
            if (vm.isGeneratingAudio || vm.hasPendingAudio) {
                item { AudioStatusBanner(vm) }
            }

            item { Spacer(Modifier.height(90.dp)) }
        }
    }

    // 🔥 احتفال استعادة الشعلة بعد نجاح مهمة الإنقاذ
    if (rescueWin > 0) {
        RescueSuccessDialog(rescueWin) { rescueWin = 0 }
    }
}

/** شارة صغيرة تشرح لماذا خطة اليوم بهذا الحجم — تجعل التكيّف مرئياً. */
@Composable
private fun PlanTierNote(vm: AppViewModel) {
    val tier = vm.learnerTier
    Surface(shape = RoundedCornerShape(12.dp), color = ZIndigo.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, null, tint = ZIndigo, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "مرحلة ${tier.label} · ${vm.planRationale}",
                color = ZTextSecondary, fontSize = 11.sp, lineHeight = 17.sp,
            )
        }
    }
}

private fun routeForTask(id: String): String = when (id) {
    "review", "listen" -> "review"
    "lesson" -> "levels"
    "quiz" -> "exams"
    "story" -> "stories"
    "speak" -> "skills"
    "addword" -> "vocab"
    "mnemonic" -> "mnemonics"
    else -> "levels"
}

private fun todayLabel(): String {
    val d = LocalDate.now()
    val months = listOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر",
    )
    return "${d.dayOfMonth} ${months[d.monthValue - 1]}"
}

/* ══════════════════════ 1 · streak header ══════════════════════ */


/* ══════════════════════ 2 · first-run guide ══════════════════════ */

/**
 * Shown when the app is genuinely empty. Instead of fake stats it gives a
 * concrete, ordered checklist of what to do first.
 */
@Composable
private fun GetStartedCard(vm: AppViewModel, onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(ZIndigo, ZPurple)))
                .drawDecor()
                .padding(24.dp)
        ) {
            Column {
                Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.2f)) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.WavingHand, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("لنبدأ الإعداد", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "مرحباً بك في Z-Mastery",
                    color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, lineHeight = 32.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "التطبيق فارغ الآن. أضف محتواك أولاً، ثم تظهر خطة يومية حقيقية وسلسلة حماسة تتابع تقدّمك.",
                    color = Color.White.copy(alpha = 0.93f), fontSize = 13.sp, lineHeight = 21.sp,
                )
            }
        }

        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("خطوات البداية", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text("أكمل أي خطوة لتفعيل لوحة القيادة", color = ZTextSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(16.dp))
                if (vm.isAdmin) {
                    StartStep(
                        1, Icons.Filled.UploadFile, "استورد كورساً",
                        "ملف JSON أو لصق مباشر", ZIndigo,
                        done = vm.lessons.isNotEmpty(),
                    ) { onNavigate("import") }
                } else {
                    StartStep(
                        1, Icons.Filled.CloudDownload, "دروسك من السحابة",
                        "تُحدَّث تلقائياً عند فتح التطبيق", ZIndigo,
                        done = vm.lessons.isNotEmpty(),
                    ) { onNavigate("levels") }
                }
                StartStep(
                    2, Icons.Filled.Add, "أضف كلمات للقاموس",
                    "يدوياً أو بالذكاء الاصطناعي", ZCyanDeep,
                    done = vm.activeVocab.isNotEmpty(),
                ) { onNavigate("vocab") }
                StartStep(
                    3, Icons.Filled.Map, "حدّد خطتك",
                    "المدة والوقت اليومي", ZEmerald,
                    done = vm.studyPlan.active,
                ) { onNavigate("roadmap") }
                StartStep(
                    4, Icons.Filled.Notifications, "فعّل التذكير اليومي",
                    "وقت ثابت يحمي سلسلتك", ZAmber,
                    done = false,
                ) { onNavigate("settings") }
            }
        }
    }
}

@Composable
private fun StartStep(
    num: Int,
    icon: ImageVector,
    title: String,
    sub: String,
    accent: Color,
    done: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (done) ZEmerald.copy(alpha = 0.16f) else accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(Icons.Filled.Check, null, tint = ZEmerald, modifier = Modifier.size(18.dp))
                else Text("$num", color = accent, fontWeight = FontWeight.Black, fontSize = 14.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = if (done) ZTextSecondary else ZTextPrimary,
                    fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                )
                Text(sub, color = ZTextMuted, fontSize = 11.sp)
            }
            Icon(icon, null, tint = if (done) ZEmerald else accent, modifier = Modifier.size(19.dp))
        }
    }
}

/* ══════════════════════ 3 · mascot ══════════════════════ */

@Composable
private fun MascotBanner(eng: Engagement) {
    val accent = when (eng.stage) {
        LearnerStage.DONE_TODAY -> ZEmerald
        LearnerStage.IN_PROGRESS -> ZIndigo
        LearnerStage.LAPSED -> ZRose
        LearnerStage.RETURNING -> if (eng.streakAtRisk) ZRose else ZAmber
        else -> ZCyanDeep
    }
    val animated by animateFloatAsState(eng.barValue, tween(700), label = "mood")

    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(eng.emoji, fontSize = 32.sp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(eng.title, color = accent, fontWeight = FontWeight.Black, fontSize = 15.sp, lineHeight = 21.sp)
                Text(eng.subtitle, color = ZTextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { animated },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                    color = accent, trackColor = ZBorder,
                )
                Spacer(Modifier.height(4.dp))
                Text(eng.barLabel, color = ZTextMuted, fontSize = 10.sp)
            }
        }
    }
}

/* ══════════════════════ 4 · tasks & plan ══════════════════════ */

@Composable
private fun NoTasksYet(onNavigate: (String) -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth(), radius = 18.dp) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(16.dp)).background(ZAmber.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.PlaylistAdd, null, tint = ZAmber, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("لا توجد مهام متاحة اليوم", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "ستتولّد خطتك اليومية تلقائياً عند إضافة كلماتك أو نزول دروسك من السحابة",
                    color = ZTextSecondary, fontSize = 12.sp, lineHeight = 18.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = ZIndigo, onClick = { onNavigate("levels") }) {
                Text(
                    "المستويات",
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun TaskRow(task: DailyTask, onStart: () -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth(), radius = 16.dp) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (task.done) ZEmerald.copy(alpha = 0.18f) else ZSurfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (task.done) Icon(Icons.Filled.Check, null, tint = ZEmerald)
                else Icon(taskIcon(task.icon), null, tint = ZCyan, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    color = if (task.done) ZTextMuted else ZTextPrimary,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    textDecoration = if (task.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                )
                Text(task.subtitle, color = ZTextSecondary, fontSize = 11.sp)
                if (task.target > 1) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (task.progress.toFloat() / task.target).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)),
                        color = if (task.done) ZEmerald else ZCyan, trackColor = ZBorder,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            if (task.done) {
                Icon(Icons.Filled.CheckCircle, null, tint = ZEmerald)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (task.target > 1) {
                        Text(
                            "${task.progress}/${task.target}",
                            color = ZTextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Surface(shape = RoundedCornerShape(50), color = ZIndigo, onClick = onStart) {
                        Icon(
                            Icons.Filled.PlayArrow, "ابدأ", tint = Color.White,
                            modifier = Modifier.padding(8.dp).size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun taskIcon(k: String): ImageVector = when (k) {
    "book" -> Icons.Filled.MenuBook
    "brain" -> Icons.Filled.Psychology
    "quiz" -> Icons.Filled.Quiz
    "talk" -> Icons.Filled.Forum
    "story" -> Icons.Filled.AutoStories
    "add" -> Icons.Filled.AddCircle
    "ear" -> Icons.Filled.Hearing
    "link" -> Icons.Filled.Link
    else -> Icons.Filled.Star
}

@Composable
private fun PlanCard(item: PlanItem, onClick: () -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth(), radius = 16.dp, onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).height(40.dp).clip(RoundedCornerShape(4.dp)).background(Color(item.accent)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.lessonTitle, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("كورس ${item.courseName}", color = ZTextSecondary, fontSize = 12.sp)
            }
            Icon(Icons.Filled.PlayCircle, null, tint = Color(item.accent))
        }
    }
}

/** Only rendered when real tasks existed AND every one of them is finished. */
@Composable
private fun AllDoneBanner() {
    Surface(shape = RoundedCornerShape(16.dp), color = ZEmerald.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Celebration, null, tint = ZEmerald)
            Spacer(Modifier.width(12.dp))
            Text("رائع! أنهيت خطة اليوم بالكامل \uD83C\uDF89", color = ZEmerald, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ForgottenWordsRow(words: List<VocabWord>, onReview: () -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(words, key = { it.id }) { w ->
            Surface(shape = RoundedCornerShape(16.dp), color = ZRose.copy(alpha = 0.12f), onClick = onReview) {
                Column(Modifier.padding(16.dp).width(120.dp)) {
                    Icon(Icons.Filled.Warning, null, tint = ZRose, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(w.english, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                    Text(w.arabic, color = ZTextSecondary, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

/* ══════════════════════ shared pieces ══════════════════════ */

private fun Modifier.drawDecor(): Modifier = this.drawBehind {
    drawCircle(color = Color.White.copy(alpha = 0.08f), radius = size.minDimension * 0.42f, center = androidx.compose.ui.geometry.Offset(size.width * 0.92f, size.height * 0.15f))
    drawCircle(color = Color.White.copy(alpha = 0.06f), radius = size.minDimension * 0.30f, center = androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.95f))
}

@Composable
private fun AudioStatusBanner(vm: AppViewModel) {
    val busy = vm.isGeneratingAudio
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
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
                Text(
                    if (busy) "جارٍ توليد الأصوات…" else "أصوات بحاجة للتوليد",
                    color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                )
                Text(
                    if (busy) "${vm.audioGenDone}/${vm.audioGenTotal} — ${vm.audioGenLabel}"
                    else "${vm.pendingAudioCount} عنصر — اضغط للتوليد والاستماع",
                    color = ZTextSecondary, fontSize = 12.sp, maxLines = 1,
                )
            }
            if (!busy) {
                Surface(shape = RoundedCornerShape(12.dp), color = ZCyanDeep, onClick = { vm.generateMissingAudio() }) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("توليد", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyStoryCard(vm: AppViewModel, onOpenArchive: () -> Unit) {
    val today = vm.todayStory
    val ready = today != null
    var showDelete by remember { mutableStateOf(false) }

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
                        if (ready) "قصة اليوم جاهزة" else "قصة اليوم",
                        color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp,
                    )
                    Text(
                        when {
                            ready -> "${today!!.wordCount} كلمة · ${today.readMinutes} دقيقة"
                            !vm.storyAiReady -> "تحتاج مفتاح Gemini — تُكتب بالذكاء الاصطناعي"
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
                            if (ready) onOpenArchive()
                            else vm.generateTodayStory { onOpenArchive() }
                        },
                        enabled = ready || (vm.storySeedCount >= 2 && vm.storyAiReady),
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
                    OutlinedButton(
                        onClick = onOpenArchive,
                        modifier = Modifier.height(46.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ZBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ZTextSecondary),
                    ) {
                        Icon(Icons.Filled.Inventory2, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("الأرشيف", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                            border = androidx.compose.foundation.BorderStroke(1.dp, ZIndigo.copy(alpha = 0.45f)),
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
                            border = androidx.compose.foundation.BorderStroke(1.dp, ZRose.copy(alpha = 0.45f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ZRose),
                        ) {
                            Icon(Icons.Filled.DeleteOutline, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("احذفها", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
                vm.storyMessage?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Text(msg, color = ZTextMuted, fontSize = 11.sp, lineHeight = 17.sp)
                }
            }
        }
    }
}


/* ══════════════════════ 3D momentum surfaces ══════════════════════ */


/** The 3–5 minute ورد — the lowest-friction way to keep the streak alive. */
@Composable
private fun MicroHabitRow(vm: AppViewModel, onStart: () -> Unit) {
    val habit = vm.microHabit
    SoftCard(modifier = Modifier.fillMaxWidth(), radius = 18.dp, onClick = onStart) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(ZAmber.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Bolt, null, tint = ZAmber, modifier = Modifier.size(21.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("الورد اليومي", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(50), color = ZAmber.copy(alpha = 0.15f)) {
                        Text(
                            habit.timeLabel, color = ZAmber, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${habit.title} — يكفي للحفاظ على سلسلتك اليوم",
                    color = ZTextSecondary, fontSize = 11.sp, lineHeight = 17.sp,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { vm.microHabitFraction },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)),
                    color = ZAmber, trackColor = ZBorder,
                )
            }
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Filled.PlayCircleFilled, null, tint = ZAmber, modifier = Modifier.size(28.dp))
        }
    }
}

/* ══════════════════════ عبارة اليوم — من المكتبة الأسبوعية ══════════════════════ */

/**
 * مقولة اليوم — عبارة واحدة ثابتة طوال 24 ساعة من [Quotes]، بلا تصفّح وبلا
 * إمكانية تغييرها من المستخدم. الهدف استيعاب فكرة واحدة والعمل بها، لا
 * التنقّل السريع بين أفكار متعددة.
 */
@Composable
private fun DailyPhraseCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val quote = remember { com.zmastery.english.data.QuoteStore.today(context) }
    val tts = com.zmastery.english.audio.LocalTts.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(ZPurple, ZIndigo)))
            .drawBehind {
                drawCircle(
                    Color.White.copy(alpha = 0.07f),
                    radius = size.minDimension * 0.5f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.9f, size.height * 0.1f),
                )
            }
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.FormatQuote, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "مقولة اليوم",
                    color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                quote.text, color = Color.White, fontSize = 18.sp,
                fontWeight = FontWeight.Black, lineHeight = 27.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "— ${quote.author}", color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.22f),
                onClick = { scope.launch { tts?.speakInstant(quote.text, "quote_day") } },
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(
                    Icons.Filled.VolumeUp, "استمع", tint = Color.White,
                    modifier = Modifier.padding(8.dp).size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun AnnouncementCard(
    announcement: com.zmastery.english.cloud.CloudSync.Announcement,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onDeactivate: () -> Unit,
) {
    val (bgColor, accentColor, icon) = when (announcement.type) {
        "alert" -> Triple(ZRose.copy(alpha = 0.15f), ZRose, Icons.Filled.Warning)
        "update" -> Triple(ZEmerald.copy(alpha = 0.15f), ZEmerald, Icons.Filled.Celebration)
        "challenge" -> Triple(ZAmber.copy(alpha = 0.15f), ZAmber, Icons.Filled.EmojiEvents)
        else -> Triple(ZCyan.copy(alpha = 0.15f), ZCyan, Icons.Filled.Campaign)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ZSurface,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, accentColor.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .background(bgColor)
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, null, tint = accentColor, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        announcement.title.ifBlank { "إشعار عام" },
                        color = ZTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, "إغلاق", tint = ZTextMuted, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                announcement.message,
                color = ZTextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )

            if (isAdmin) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDeactivate) {
                        Icon(Icons.Filled.Delete, null, tint = ZRose, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("إلغاء وتثبيط الإشعار للجميع (مدير)", color = ZRose, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

