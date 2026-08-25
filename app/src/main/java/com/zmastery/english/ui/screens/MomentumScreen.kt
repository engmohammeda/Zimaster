package com.zmastery.english.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.data.ChestRarity
import com.zmastery.english.data.ChestTier
import com.zmastery.english.data.MicroHabits
import com.zmastery.english.data.Momentum3D
import com.zmastery.english.data.MomentumEngine
import com.zmastery.english.data.PerkKind
import com.zmastery.english.data.SevenSeals
import com.zmastery.english.ui.components.ProgressRing
import com.zmastery.english.ui.components.SectionTitle
import com.zmastery.english.ui.components.SoftCard
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel

/**
 * زخم التعلم والالتزام — the 3D Momentum dashboard + the Seven Seals vault.
 *
 * Layer 1 (المرحلة الأولى): three independent axes replace the fragile single
 * streak counter, so one missed day can never erase the learner's whole story.
 * Layer 2 (المرحلة الثانية): sealed mystery chests turn the day counter into an
 * unfolding discovery with real, functional perks.
 */
@Composable
fun MomentumScreen(vm: AppViewModel, onNavigate: (String) -> Unit) {
    val m = vm.momentum
    var openTier by remember { mutableStateOf<ChestTier?>(null) }
    var rescueWin by remember { mutableStateOf(0) }
    var breakTarget by remember { mutableStateOf<com.zmastery.english.data.MysteryReward?>(null) }
    var showCalendar by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.loadGlobalLeaderboard()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── 🎯 شرط اليوم — أول ما يجب أن يراه المستخدم هنا ──
        TodayConditionCard(vm) { showCalendar = true }

        // ── كرت المؤشرات الثلاثية (🔥 · 🌱 · ⭐) ──
        MomentumIndicatorsCard(vm.metrics)

        // ── The pyramid apex: combined commitment ──
        OverallCard(m)

        // ── Psychological shield banner (only when it matters most) ──
        if (m.shieldMessage.isNotBlank()) {
            ShieldBanner(m)
        }

        // ── 🟣 المرحلة الرابعة · بوابة الإنقاذ (الأولوية القصوى) ──
        if (vm.showRescueGate) RescueGateCard(
            rescue = vm.rescue,
            onStart = { vm.startRescueTimer(); onNavigate(vm.rescue.kindEnum.route) },
            onClaim = { rescueWin = vm.claimRescue() },
            onDismiss = { vm.dismissRescue() },
        )

        // ── 🔥 المرحلة الرابعة · صندوق اليوم الباكي ──
        CrackingChestCard(vm.decayState) { onNavigate("review") }

        // ── A sealed chest is waiting ──
        if (vm.hasPendingChest) {
            val tier = vm.pendingChests.last()
            SealedChestTeaser(tier) { openTier = tier }
        }

        // ── Axis 1 · 🔥 daily streak + the micro-habit ──
        SectionTitle("سلسلة الحماسة", "المحرك اليومي")
        StreakAxisCard(vm, m, onNavigate)

        // ── 🛡️ دروع تجميد السلسلة — قسمها الطبيعي: حماية الحماسة ──
        SectionTitle("دروع التجميد", "تحمي سلسلتك من يوم فائت واحد")
        StreakShieldsCard(
            freezes = vm.wallet.streakFreezes,
            lastUsedDay = vm.lastFreezeUsedDay,
            todayDay = com.zmastery.english.data.Telemetry.today(),
        )

        // ── Axis 2 · 🌱 continuity ratio ──
        SectionTitle("رصيد الاستمرارية", "الدرع النفسي — لا يضيع بيوم واحد")
        ContinuityAxisCard(vm, m)

        // ── Axis 3 · ⭐ mastery level ──
        SectionTitle("مستوى الإتقان", "مقياس الجودة — يكافئ العمق لا الكمّ")
        MasteryAxisCard(m)

        // ── The perk wallet ──
        if (vm.wallet.streakFreezes > 0 || vm.wallet.multiplierActive() ||
            vm.wallet.badges.isNotEmpty() || vm.wallet.sponsorGifts > 0
        ) {
            SectionTitle("خزنة المكافآت", "ما تملكه الآن")
            WalletCard(vm)
        }

        // ── 🎁 الصناديق الغامضة ──
        SectionTitle(
            "الصناديق الغامضة",
            "${vm.openedRewardCount} مفتوح · ${vm.sealedRewards.size} جاهز للكسر",
        )
        vm.mysteryRewards.forEach { reward ->
            MysteryChestCard(
                reward = reward,
                currentStreak = vm.chestQualifyingStreak,
                onOpen = { breakTarget = reward },
                onView = { breakTarget = reward },
            )
        }

        // ── The Seven Seals ladder ──
        SectionTitle("الأختام السبعة", "صناديق مجهولة تُفتح بالاستمرارية")
        SealsLadder(vm) { tier -> openTier = tier }

        // ── 🏆 لوحة المتصدرين السحابية المباشرة ──
        SectionTitle("لوحة المتصدرين السحابية 🏆", "أقوى الطلاب التزاماً في مجتمع Zimaster")
        GlobalLeaderboardCard(vm)

        Spacer(Modifier.height(80.dp))
    }

    openTier?.let { tier ->
        ChestOpeningDialog(vm, tier) { openTier = null }
    }

    breakTarget?.let { target ->
        ChestBreakDialog(vm, target) { breakTarget = null }
    }

    if (rescueWin > 0) {
        RescueSuccessDialog(rescueWin) { rescueWin = 0 }
    }

    if (showCalendar) {
        StreakCalendarDialog(vm) { showCalendar = false }
    }
}

/* ══════════════ 🎯 شرط اليوم + تقويم الحماسة ══════════════ */

/**
 * يشرح بالضبط ما الذي يجعل اليوم "محسوباً" في السلسلة.
 * قبل هذا الكرت كان النظام غامضاً: يُحتسب اليوم بمجرد إكمال درس.
 * الآن الشرط ظاهر وقابل للقياس، ويتكيّف مع مرحلة المتعلّم.
 */
@Composable
private fun TodayConditionCard(vm: AppViewModel, onOpenCalendar: () -> Unit) {
    val done = vm.dayEarnedStreak
    val accent = if (done) ZEmerald else ZAmber
    val progress by animateFloatAsState(vm.streakConditionProgress, tween(700), label = "cond")

    Surface(
        shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(), onClick = onOpenCalendar,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(16.dp))
                        .background(accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (done) "\u2705" else "\uD83C\uDFAF", fontSize = 21.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (done) "يومك مؤمَّن" else "شرط اليوم",
                        color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp,
                    )
                    Text(vm.streakConditionLabel, color = ZTextSecondary, fontSize = 11.sp, lineHeight = 17.sp)
                }
                Icon(Icons.Filled.CalendarMonth, null, tint = ZTextMuted, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = accent, trackColor = ZBorder,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CondChip(
                    Modifier.weight(1f),
                    "\uD83D\uDCCB", "${vm.activeTasksDone}/${vm.activeDailyTasks.size}", "مهام الخطة",
                    vm.planCompleteToday,
                )
                CondChip(
                    Modifier.weight(1f),
                    "\u26A1", "${vm.todayActivityScore}/${vm.streakActivityThreshold}", "نشاط اليوم",
                    vm.todayActivityScore >= vm.streakActivityThreshold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "يكفي تحقيق أحد الشرطين لتأمين يومك · الشروط تتكيّف مع مرحلة ${vm.learnerTier.label}",
                color = ZTextMuted, fontSize = 10.sp, lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun CondChip(modifier: Modifier, emoji: String, value: String, label: String, met: Boolean) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (met) ZEmerald.copy(alpha = 0.12f) else ZSurfaceVariant,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    value,
                    color = if (met) ZEmerald else ZTextPrimary,
                    fontWeight = FontWeight.Black, fontSize = 13.sp,
                )
                Text(label, color = ZTextMuted, fontSize = 9.sp)
            }
            if (met) Icon(Icons.Filled.Check, null, tint = ZEmerald, modifier = Modifier.size(14.dp))
        }
    }
}

/** تقويم الحماسة بأسلوب GitHub — نُقل من الشاشة الرئيسية إلى مكانه الطبيعي. */
@Composable
private fun StreakCalendarDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val days = vm.heatmapDays(119)
    val eng = vm.engagement
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ZCard,
        title = {
            Column {
                Text("تقويم الحماسة", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text("آخر 17 أسبوعاً من نشاطك", color = ZTextSecondary, fontSize = 11.sp)
            }
        },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CalFact("${eng.streak}", "الحالية")
                    CalFact("${vm.bestActivityStreak}", "الأفضل")
                    CalFact("${days.count { it.isActive }}", "أيام نشطة")
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    days.chunked(7).forEach { week ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            week.forEach { d ->
                                Box(
                                    Modifier.size(13.dp).clip(RoundedCornerShape(4.dp))
                                        .background(heatTint(d.intensity))
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("أقل", color = ZTextMuted, fontSize = 10.sp)
                    Spacer(Modifier.width(8.dp))
                    (0..4).forEach { lv ->
                        Box(
                            Modifier.size(11.dp).clip(RoundedCornerShape(4.dp)).background(heatTint(lv))
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("أكثر", color = ZTextMuted, fontSize = 10.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق", color = ZIndigo, fontWeight = FontWeight.Bold) }
        },
    )
}

@Composable
private fun CalFact(value: String, label: String) {
    Column {
        Text(value, color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 19.sp)
        Text(label, color = ZTextSecondary, fontSize = 10.sp)
    }
}

private fun heatTint(level: Int): Color = when (level) {
    0 -> if (ZThemeState.isDark) Color(0xFF262938) else Color(0xFFE2E4EC)
    1 -> ZEmerald.copy(alpha = 0.30f)
    2 -> ZEmerald.copy(alpha = 0.52f)
    3 -> ZEmerald.copy(alpha = 0.76f)
    else -> ZEmerald
}


/* ══════════════════════ apex · overall commitment ══════════════════════ */

@Composable
private fun OverallCard(m: Momentum3D) {
    val animated by animateFloatAsState(m.overall, tween(900, easing = FastOutSlowInEasing), label = "ov")
    Surface(shape = RoundedCornerShape(24.dp), color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.background(Brush.linearGradient(listOf(ZIndigo, ZPurple))).padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("زخم التعلم والالتزام", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "ثلاثة أبعاد مستقلة تحمي تقدّمك",
                        color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp,
                    )
                }
                ProgressRing(
                    progress = animated, size = 74.dp, stroke = 7.dp,
                    color = Color.White, trackColor = Color.White.copy(alpha = 0.28f),
                ) {
                    Text(
                        "${m.overallPct}%", color = Color.White,
                        fontWeight = FontWeight.Black, fontSize = 17.sp,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ApexPill(Modifier.weight(1f), "\uD83D\uDD25", "${m.streak}", "سلسلة")
                ApexPill(Modifier.weight(1f), "\uD83C\uDF31", "${m.continuityPct}%", "رصيد")
                ApexPill(Modifier.weight(1f), "\u2B50", "${m.masteryPct}%", "إتقان")
            }
        }
    }
}

@Composable
private fun ApexPill(modifier: Modifier, emoji: String, value: String, label: String) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.2f)) {
        Column(
            Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(emoji, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
            Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp)
        }
    }
}

/* ══════════════════════ the shield ══════════════════════ */

/**
 * Shown exactly when the "what-the-hell effect" would strike: the streak has
 * just broken. Instead of an empty zero, the learner sees the evidence that
 * their accumulated effort is still intact.
 */
@Composable
private fun ShieldBanner(m: Momentum3D) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ZEmerald.copy(alpha = 0.13f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(16.dp)).background(ZEmerald.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) { Text("\uD83C\uDF31", fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("إنجازك محفوظ", color = ZEmerald, fontWeight = FontWeight.Black, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(m.shieldMessage, color = ZTextSecondary, fontSize = 12.sp, lineHeight = 19.sp)
            }
        }
    }
}

/* ══════════════════════ axis 1 · streak ══════════════════════ */

@Composable
private fun StreakAxisCard(vm: AppViewModel, m: Momentum3D, onNavigate: (String) -> Unit) {
    val habit = vm.microHabit
    val done = vm.microHabitDone
    var showPicker by remember { mutableStateOf(false) }

    SoftCard(modifier = Modifier.fillMaxWidth(), radius = 20.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("\uD83D\uDD25", fontSize = 26.sp, modifier = Modifier.alpha(if (m.streak == 0) 0.35f else 1f))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (m.streak > 0) "${m.streak} يوم متتالٍ" else "لم تبدأ بعد",
                        color = if (m.streak > 0) ZAmber else ZTextMuted,
                        fontWeight = FontWeight.Black, fontSize = 20.sp,
                    )
                    Text(
                        "أطول سلسلة: ${m.bestStreak} يوم",
                        color = ZTextSecondary, fontSize = 11.sp,
                    )
                }
                if (vm.wallet.streakFreezes > 0) {
                    Surface(shape = RoundedCornerShape(50), color = ZCyanDeep.copy(alpha = 0.15f)) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("\uD83D\uDEE1\uFE0F", fontSize = 12.sp)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "${vm.wallet.streakFreezes}", color = ZCyanDeep,
                                fontSize = 12.sp, fontWeight = FontWeight.Black,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = ZBorder)
            Spacer(Modifier.height(16.dp))

            // ── الورد اليومي — the micro-habit ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (done) ZEmerald.copy(alpha = 0.18f) else ZAmber.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (done) Icons.Filled.CheckCircle else Icons.Filled.Bolt,
                        null, tint = if (done) ZEmerald else ZAmber, modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("الورد اليومي", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "${habit.title} · ${habit.timeLabel}",
                        color = ZTextSecondary, fontSize = 11.sp,
                    )
                }
                TextButton(onClick = { showPicker = true }) {
                    Text("تغيير", color = ZIndigo, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { vm.microHabitFraction },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = if (done) ZEmerald else ZAmber, trackColor = ZBorder,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (done) "مكتمل — سلسلتك محمية اليوم \u2705"
                else "${vm.microHabitProgress} / ${habit.target} · ${habit.detail}",
                color = if (done) ZEmerald else ZTextMuted, fontSize = 11.sp,
            )

            if (!done) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onNavigate(habit.route) },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ZAmber),
                ) {
                    Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("ابدأ الورد (${habit.minutes} دقائق)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }

    if (showPicker) {
        MicroHabitPicker(vm) { showPicker = false }
    }
}

@Composable
private fun MicroHabitPicker(vm: AppViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ZCard,
        title = { Text("اختر وردك اليومي", color = ZTextPrimary, fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text(
                    "مهمة صغيرة (3–5 دقائق) تكفي للحفاظ على سلسلتك في الأيام المزدحمة.",
                    color = ZTextSecondary, fontSize = 12.sp, lineHeight = 19.sp,
                )
                Spacer(Modifier.height(12.dp))
                MicroHabits.all.forEach { h ->
                    val active = h.id == vm.microHabitId
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (active) ZIndigo.copy(alpha = 0.14f) else Color.Transparent,
                        onClick = { vm.setMicroHabit(h.id); onDismiss() },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (active) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                                null, tint = if (active) ZIndigo else ZTextMuted, modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(h.title, color = ZTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("${h.detail} · ${h.timeLabel}", color = ZTextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("تم", color = ZIndigo, fontWeight = FontWeight.Bold) }
        },
    )
}

/* ══════════════════════ axis 2 · continuity ══════════════════════ */

@Composable
private fun ContinuityAxisCard(vm: AppViewModel, m: Momentum3D) {
    val cells = vm.continuityCells
    val animated by animateFloatAsState(m.continuityRatio, tween(900), label = "cont")

    SoftCard(modifier = Modifier.fillMaxWidth(), radius = 20.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("\uD83C\uDF31", fontSize = 24.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${m.continuityPct}%",
                        color = ZEmerald, fontWeight = FontWeight.Black, fontSize = 22.sp,
                    )
                    Text(
                        "${m.activeDays30} يوماً من آخر ${MomentumEngine.CONTINUITY_WINDOW}",
                        color = ZTextSecondary, fontSize = 11.sp,
                    )
                }
                Surface(shape = RoundedCornerShape(50), color = ZEmerald.copy(alpha = 0.14f)) {
                    Text(
                        m.continuityLabel, color = ZEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(8.dp)),
                color = ZEmerald, trackColor = ZBorder,
            )
            Spacer(Modifier.height(16.dp))

            // 30-cell grid — a month of honest effort at a glance
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                cells.chunked(10).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { active ->
                            Box(
                                Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(4.dp))
                                    .background(if (active) ZEmerald else ZBorder.copy(alpha = 0.55f))
                            )
                        }
                        repeat(10 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = ZSurfaceVariant.copy(alpha = 0.6f)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Shield, null, tint = ZEmerald, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "هذا الرصيد لا ينهار بيوم واحد فائت — إنه مجموع جهدك الحقيقي" +
                            if (m.continuityBest > m.continuityRatio)
                                "\nأفضل شهر لك: ${(m.continuityBest * 100).toInt()}%" else "",
                        color = ZTextSecondary, fontSize = 11.sp, lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}

/* ══════════════════════ axis 3 · mastery ══════════════════════ */

@Composable
private fun MasteryAxisCard(m: Momentum3D) {
    val animated by animateFloatAsState(m.masteryLevel, tween(900), label = "mast")

    SoftCard(modifier = Modifier.fillMaxWidth(), radius = 20.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("\u2B50", fontSize = 24.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${m.masteryPct}%",
                        color = ZAmber, fontWeight = FontWeight.Black, fontSize = 22.sp,
                    )
                    Text(m.masteryLabel, color = ZTextSecondary, fontSize = 11.sp)
                }
                // CEFR badge with progress to the next band
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.clip(RoundedCornerShape(50))
                            .background(Brush.linearGradient(listOf(ZIndigo, ZPurple)))
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Text(m.cefr, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("← ${m.nextCefr}", color = ZTextMuted, fontSize = 9.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(8.dp)),
                color = ZAmber, trackColor = ZBorder,
            )
            Spacer(Modifier.height(16.dp))

            // The three depth components
            MasteryBar("كلمات راسخة في الذاكرة", m.masteryWords, ZEmerald, "50%")
            MasteryBar("دروس مكتملة من المنهج", m.masteryLessons, ZCyanDeep, "30%")
            MasteryBar("دقة الاختبارات الحقيقية", m.masteryAccuracy, ZPurple, "20%")

            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = ZSurfaceVariant.copy(alpha = 0.6f)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Verified, null, tint = ZAmber, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "يقيس العمق اللغوي لا عدد النقرات — لا يمكن رفعه بمراجعة الكلمات السهلة فقط",
                        color = ZTextSecondary, fontSize = 11.sp, lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun MasteryBar(label: String, value: Float, accent: Color, weightLabel: String) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = ZTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text(weightLabel, color = ZTextMuted, fontSize = 9.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                "${(value * 100).toInt()}%", color = accent,
                fontSize = 11.sp, fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { value },
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)),
            color = accent, trackColor = ZBorder,
        )
    }
}

/* ══════════════════════ wallet ══════════════════════ */

@Composable
private fun WalletCard(vm: AppViewModel) {
    val w = vm.wallet
    SoftCard(modifier = Modifier.fillMaxWidth(), radius = 20.dp) {
        Column(Modifier.padding(16.dp)) {
            if (w.multiplierActive()) {
                Surface(shape = RoundedCornerShape(16.dp), color = ZAmber.copy(alpha = 0.16f), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("\u26A1", fontSize = 18.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("مضاعف XP ×2 نشط", color = ZAmber, fontWeight = FontWeight.Black, fontSize = 13.sp)
                            Text("متبقٍ ${w.multiplierMinutesLeft()} دقيقة — استغلّها الآن", color = ZTextSecondary, fontSize = 11.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (w.streakFreezes > 0) {
                    PerkChip(Modifier.weight(1f), "\uD83D\uDEE1\uFE0F", "${w.streakFreezes}", "دروع تجميد", ZCyanDeep)
                }
                if (w.sponsorGifts > 0) {
                    PerkChip(Modifier.weight(1f), "\uD83E\uDD1D", "${w.sponsorGifts}", "هدايا رعاية", ZEmerald)
                }
                if (w.badges.isNotEmpty()) {
                    PerkChip(Modifier.weight(1f), "\uD83C\uDFC5", "${w.badges.size}", "أوسمة", ZPurple)
                }
            }
            if (w.badges.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("أوسمتك", color = ZTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    w.badges.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { b ->
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = ZPurple.copy(alpha = 0.12f),
                                ) {
                                    Text(
                                        b, color = ZPurple, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center, maxLines = 1,
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                    )
                                }
                            }
                            repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PerkChip(modifier: Modifier, emoji: String, value: String, label: String, accent: Color) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = accent.copy(alpha = 0.12f)) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = accent, fontWeight = FontWeight.Black, fontSize = 15.sp)
            Text(label, color = ZTextSecondary, fontSize = 9.sp)
        }
    }
}

/* ══════════════════════ seven seals ══════════════════════ */

/** Big animated teaser for a chest that is ready but still sealed. */
@Composable
private fun SealedChestTeaser(tier: ChestTier, onOpen: () -> Unit) {
    val rarity = Color(tier.rarity.colorArgb)
    val glow = Color(tier.rarity.glowArgb)
    val infinite = rememberInfiniteTransition(label = "teaser")
    val pulse by infinite.animateFloat(
        1f, 1.06f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p",
    )
    val shimmer by infinite.animateFloat(
        0.35f, 0.85f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "s",
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().scale(pulse),
        onClick = onOpen,
    ) {
        Column(
            Modifier
                .background(Brush.linearGradient(listOf(rarity, glow.copy(alpha = 0.75f))))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("\uD83C\uDF81", fontSize = 46.sp, modifier = Modifier.alpha(shimmer + 0.15f))
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.3f)) {
                Text(
                    tier.rarity.label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(tier.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(
                "صندوق مجهول جاهز للفتح — لا تعرف ما بداخله!",
                color = Color.White.copy(alpha = 0.93f), fontSize = 12.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.LockOpen, null, tint = rarity, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("افتح الآن", color = rarity, fontWeight = FontWeight.Black, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun SealsLadder(vm: AppViewModel, onOpen: (ChestTier) -> Unit) {
    // Progress toward the next seal
    vm.nextChest?.let { nxt ->
        SoftCard(modifier = Modifier.fillMaxWidth(), radius = 18.dp) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("\uD83D\uDD12", fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("الختم القادم: ${nxt.name}", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            "باقٍ ${vm.daysToNextChest} يوم · اليوم ${nxt.day}",
                            color = ZTextSecondary, fontSize = 11.sp,
                        )
                    }
                    Surface(shape = RoundedCornerShape(50), color = Color(nxt.rarity.colorArgb).copy(alpha = 0.16f)) {
                        Text(
                            nxt.rarity.label, color = Color(nxt.rarity.colorArgb),
                            fontSize = 10.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { vm.chestProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = Color(nxt.rarity.colorArgb), trackColor = ZBorder,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    // Earned status uses the BEST streak: a seal already reached is kept forever.
    val qualifying = vm.chestQualifyingStreak
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SevenSeals.tiers.forEach { tier ->
            val earned = qualifying >= tier.day
            val opened = vm.isChestOpened(tier.id)
            SealRow(tier, earned, opened) { if (earned) onOpen(tier) }
        }
    }
}

@Composable
private fun SealRow(tier: ChestTier, earned: Boolean, opened: Boolean, onClick: () -> Unit) {
    val rarity = Color(tier.rarity.colorArgb)
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 16.dp,
        onClick = if (earned) onClick else null,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp).alpha(if (earned) 1f else 0.55f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(16.dp))
                    .background(
                        if (earned) Brush.linearGradient(listOf(rarity, Color(tier.rarity.glowArgb)))
                        else Brush.linearGradient(listOf(ZBorder, ZSurfaceVariant))
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        opened -> "\u2705"
                        earned -> "\uD83C\uDF81"
                        else -> "\uD83D\uDD12"
                    },
                    fontSize = 20.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${tier.day} يوم", color = rarity,
                        fontSize = 10.sp, fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(50), color = rarity.copy(alpha = 0.14f)) {
                        Text(
                            tier.rarity.label, color = rarity, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(tier.name, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    when {
                        opened -> "مفتوح · ${tier.badge}"
                        earned -> "جاهز للفتح — اضغط لتكتشف!"
                        else -> "مغلق بختم مجهول"
                    },
                    color = if (earned && !opened) rarity else ZTextMuted,
                    fontSize = 10.sp,
                    fontWeight = if (earned && !opened) FontWeight.Bold else FontWeight.Normal,
                )
            }
            if (earned && !opened) {
                Icon(Icons.Filled.ChevronLeft, null, tint = rarity)
            }
        }
    }
}

/* ══════════════════════ the opening ceremony ══════════════════════ */

/**
 * The reveal. A sealed chest shakes, the wax seal melts, then the three reward
 * layers cascade in: the personalised wisdom card, the functional perks, and
 * the narrative badge.
 */
@Composable
private fun ChestOpeningDialog(vm: AppViewModel, tier: ChestTier, onDismiss: () -> Unit) {
    val alreadyOpen = vm.isChestOpened(tier.id)
    var opened by remember { mutableStateOf(alreadyOpen) }
    var record by remember { mutableStateOf(vm.chestRecord(tier.id)) }

    val rarity = Color(tier.rarity.colorArgb)
    val glow = Color(tier.rarity.glowArgb)

    val infinite = rememberInfiniteTransition(label = "chest")
    val shake by infinite.animateFloat(
        -3f, 3f, infiniteRepeatable(tween(140), RepeatMode.Reverse), label = "sh",
    )
    val float by infinite.animateFloat(
        1f, 1.05f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "fl",
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ZCard,
        title = null,
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ── the chest itself ──
                Box(
                    Modifier
                        .size(if (opened) 92.dp else 120.dp)
                        .then(if (opened) Modifier.scale(float) else Modifier.rotate(shake))
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(rarity, glow))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (opened) "\uD83C\uDF8A" else "\uD83C\uDF81", fontSize = if (opened) 42.sp else 54.sp)
                }
                Spacer(Modifier.height(16.dp))
                Surface(shape = RoundedCornerShape(50), color = rarity.copy(alpha = 0.16f)) {
                    Text(
                        tier.rarity.label, color = rarity, fontSize = 10.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    tier.name, color = ZTextPrimary, fontSize = 18.sp,
                    fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))

                if (!opened) {
                    Text(
                        tier.visualNote,
                        color = ZTextMuted, fontSize = 11.sp,
                        textAlign = TextAlign.Center, lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "المحتوى مجهول — لا أحد يعرف ما بداخله حتى يُفتح",
                        color = rarity, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    AnimatedVisibility(true, enter = fadeIn(tween(500)) + scaleIn(tween(500))) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // ── layer 1 · the AI wisdom card ──
                            record?.wisdom?.takeIf { it.isNotBlank() }?.let { wis ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = ZIndigo.copy(alpha = 0.10f),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("\uD83D\uDCDC", fontSize = 15.sp)
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "بطاقة حكمة مخصّصة لك",
                                                color = ZIndigo, fontSize = 12.sp, fontWeight = FontWeight.Black,
                                            )
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text(wis, color = ZTextSecondary, fontSize = 13.sp, lineHeight = 22.sp)
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                            }

                            // ── layer 1.5 · 🧠 مرآة الإدراك (للصناديق التي تمنح AI_REPORT) ──
                            if (tier.rewards.any { it.kind == PerkKind.AI_REPORT }) {
                                ChestMirrorPanel(vm, tier.id)
                                Spacer(Modifier.height(12.dp))
                            }

                            // ── layer 2 · the functional perks ──
                            Text(
                                "ما حصلت عليه", color = ZTextPrimary,
                                fontSize = 12.sp, fontWeight = FontWeight.Black,
                            )
                            Spacer(Modifier.height(8.dp))
                            tier.rewards.forEach { r ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = ZSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                ) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(r.emoji, fontSize = 18.sp)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(r.title, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(r.detail, color = ZTextSecondary, fontSize = 11.sp, lineHeight = 17.sp)
                                        }
                                        if (r.kind == PerkKind.XP_BONUS && r.amount > 0) {
                                            Text(
                                                "+${r.amount}", color = ZAmber,
                                                fontWeight = FontWeight.Black, fontSize = 13.sp,
                                            )
                                        }
                                    }
                                }
                            }

                            // ── layer 3 · the narrative badge ──
                            Spacer(Modifier.height(8.dp))
                            Surface(shape = RoundedCornerShape(50), color = rarity.copy(alpha = 0.14f)) {
                                Row(
                                    Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("\uD83C\uDFC5", fontSize = 14.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "وسام: ${tier.badge}", color = rarity,
                                        fontSize = 12.sp, fontWeight = FontWeight.Black,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!opened) {
                Button(
                    onClick = {
                        record = vm.openChest(tier.id)
                        opened = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = rarity),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Filled.LockOpen, null, modifier = Modifier.size(17.dp), tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("اكسر الختم", fontWeight = FontWeight.Black, color = Color.White)
                }
            } else {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = ZIndigo),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("رائع!", fontWeight = FontWeight.Black, color = Color.White) }
            }
        },
        dismissButton = if (!opened) {
            { TextButton(onClick = onDismiss) { Text("لاحقاً", color = ZTextSecondary) } }
        } else null,
    )
}

/**
 * لوحة مرآة الإدراك داخل الصندوق. تُولَّد مرة واحدة عند الفتح وتُخزَّن،
 * فتبقى ذكرى ثابتة لتلك اللحظة بالذات ولا تستهلك حصة الـ API مجدداً.
 */
@Composable
private fun ChestMirrorPanel(vm: AppViewModel, tierId: String) {
    val report = vm.mirrorReport(tierId)

    // توليد تلقائي أول مرة يُفتح فيها الصندوق.
    LaunchedEffect(tierId) {
        if (vm.mirrorReport(tierId) == null) vm.generateMirrorReport(tierId)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ZPurple.copy(alpha = 0.09f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            when {
                report != null -> MirrorReportView(report)
                vm.isMirrorLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = ZPurple, strokeWidth = 2.dp, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("جارٍ توليد مرآة إدراكك…", color = ZTextSecondary, fontSize = 12.sp)
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Psychology, null, tint = ZPurple, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("مرآة الإدراك قيد التحضير…", color = ZTextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun GlobalLeaderboardCard(vm: AppViewModel) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ZCard,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Leaderboard, null, tint = ZAmber, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("المتصدرون هذا الأسبوع", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                IconButton(onClick = { vm.loadGlobalLeaderboard() }, modifier = Modifier.size(32.dp)) {
                    if (vm.isLoadingLeaderboard) {
                        CircularProgressIndicator(color = ZCyan, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else {
                        Icon(Icons.Filled.Refresh, "تحديث", tint = ZCyan, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (vm.globalLeaderboard.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("لا توجد بيانات متصدرين حالياً", color = ZTextMuted, fontSize = 12.sp)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.globalLeaderboard.forEachIndexed { index, user ->
                        val isCurrentUser = user.uid == vm.cloudUid
                        val rank = index + 1
                        val rankBadge = when (rank) {
                            1 -> "🥇"
                            2 -> "🥈"
                            3 -> "🥉"
                            else -> "$rank"
                        }
                        val itemBg = if (isCurrentUser) ZIndigo.copy(alpha = 0.18f) else ZSurfaceVariant
                        val itemBorder = if (isCurrentUser) androidx.compose.foundation.BorderStroke(1.5.dp, ZIndigo) else null

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = itemBg,
                            border = itemBorder,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Rank
                                Box(
                                    modifier = Modifier.width(32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        rankBadge,
                                        fontSize = if (rank <= 3) 16.sp else 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (rank <= 3) ZAmber else ZTextSecondary,
                                    )
                                }

                                Spacer(Modifier.width(8.dp))

                                // Avatar
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isCurrentUser) ZIndigo else ZBorder),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        (user.displayName?.take(1) ?: "U").uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                    )
                                }

                                Spacer(Modifier.width(12.dp))

                                // Name & details
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            user.displayName ?: "متعلم",
                                            color = ZTextPrimary,
                                            fontWeight = if (isCurrentUser) FontWeight.Black else FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                        )
                                        if (isCurrentUser) {
                                            Spacer(Modifier.width(8.dp))
                                            Text("(أنت)", color = ZIndigo, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "🔥 ${user.streak} يوم  ·  📚 ${user.completedLessonsCount} درس",
                                        color = ZTextMuted,
                                        fontSize = 10.sp,
                                    )
                                }

                                // XP Badge
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = ZAmber.copy(alpha = 0.15f),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Filled.Bolt, null, tint = ZAmber, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "${user.xp}",
                                            color = ZAmber,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

