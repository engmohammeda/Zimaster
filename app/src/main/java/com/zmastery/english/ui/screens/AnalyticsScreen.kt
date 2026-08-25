package com.zmastery.english.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.data.*
import com.zmastery.english.ui.components.ProgressRing
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Analytics — everything the learner has ever done, in three time lenses
 * (يومي / أسبوعي / شهري) plus an always-available lifetime section, and the
 * on-demand AI coach that turns those numbers into a treatment plan.
 */
@Composable
fun AnalyticsScreen(vm: AppViewModel) {
    var scope by remember { mutableStateOf(CoachScope.WEEKLY) }
    val span = vm.spanFor(scope)
    val prev = vm.previousSpanFor(scope)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { AnalyticsHeader(vm) }
        item { ScopeTabs(scope) { scope = it } }

        // ---- period summary ----
        item { PeriodCard(scope, span, prev) }

        when (scope) {
            CoachScope.DAILY -> {
                item { TodayDetail(vm) }
                item { ForgottenTodayCard(vm) }
            }
            CoachScope.WEEKLY -> {
                item { TrendChartCard("نشاط آخر 7 أيام", span.days, ZIndigo) }
                item { TopHardWordsCard(vm) }
            }
            CoachScope.MONTHLY -> {
                item { TrendChartCard("نشاط آخر 30 يوماً", span.days, ZCyanDeep) }
                item { HeatmapCard(vm) }
                item { CefrCard(vm) }
            }
        }

        // ---- real measured study time + weekly rhythm ----
        item { StudyTimeCard(vm, span) }

        // ---- my curriculum coverage (always — it's the backbone) ----
        item { CurriculumCard(vm) }

        // ---- نشاط الأسبوع (نُقل من الشاشة الرئيسية) ----
        item { WeeklyActivityCard(vm) }

        // ---- 🧠 مرآة الإدراك + التحليل السيكولوجي العميق (نُقلا إلى قسمهما) ----
        item {
            Text(
                "مرآة الإدراك",
                color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        item {
            Text(
                "بصمتك المعرفية كما تكشفها بياناتك",
                color = ZTextSecondary, fontSize = 11.sp,
            )
        }
        item { CognitiveMirrorCard(vm.cognitiveMirror) }
        item { MirrorInlineReport(vm) }

        // ---- AI coach ----
        item { CoachCard(vm, scope) }

        // ---- memory health (always) ----
        item { MemoryHealthCard(vm) }

        // ---- skill radar + lifetime ----
        item { SkillRadarCard(vm) }
        item { LifetimeCard(vm) }

        // ---- exam trend ----
        if (vm.examHistory.isNotEmpty()) {
            item { ExamTrendCard(vm) }
        }

        // ---- hard words list ----
        item {
            Text(
                "الكلمات الصعبة",
                color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        val hard = vm.activeVocab
            .filter { it.totalReviews > 0 && (it.difficulty >= 6.0 || it.lapses >= 2) }
            .sortedByDescending { it.difficulty + it.lapses * 0.7 }
            .take(15)
        if (hard.isEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(16.dp), color = ZEmerald.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.SentimentVerySatisfied, null, tint = ZEmerald)
                        Spacer(Modifier.width(12.dp))
                        Text("لا توجد كلمات صعبة حالياً — ذاكرتك نظيفة \uD83D\uDC4F", color = ZTextSecondary, fontSize = 13.sp)
                    }
                }
            }
        } else {
            items(hard, key = { it.id }) { w -> HardWordRow(w, vm) }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

/* ────────────────────────── header & tabs ────────────────────────── */

@Composable
private fun AnalyticsHeader(vm: AppViewModel) {
    val (cefr, prog) = vm.cefrEstimate
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(ZPurple, ZIndigo))).padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("التحليلات", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text("كل رقم هنا من نشاطك الحقيقي", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniPill(Icons.Filled.LocalFireDepartment, "${vm.activityStreak}", "سلسلة")
                    MiniPill(Icons.Filled.EmojiEvents, "${vm.bestActivityStreak}", "الأفضل")
                    MiniPill(Icons.Filled.Bolt, "${vm.xp}", "XP")
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(contentAlignment = Alignment.Center) {
                ProgressRing(progress = prog, size = 78.dp, stroke = 8.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(cefr, color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text("المستوى", color = Color.White.copy(alpha = 0.8f), fontSize = 8.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPill(icon: ImageVector, value: String, label: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.2f)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
            Spacer(Modifier.width(4.dp))
            Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 9.sp)
        }
    }
}

@Composable
private fun ScopeTabs(current: CoachScope, onPick: (CoachScope) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ZSurfaceVariant).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CoachScope.values().forEach { s ->
            val active = s == current
            Surface(
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                color = if (active) ZIndigo else Color.Transparent, onClick = { onPick(s) },
            ) {
                Text(
                    s.label,
                    color = if (active) Color.White else ZTextSecondary,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                )
            }
        }
    }
}

/* ────────────────────────── period summary ────────────────────────── */

@Composable
private fun PeriodCard(scope: CoachScope, span: StatSpan, prev: StatSpan) {
    Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.QueryStats, null, tint = ZIndigo, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text("ملخص ${scope.label}", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Text("${span.activeDays}/${scope.days} يوم نشط", color = ZTextMuted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DeltaStat(Modifier.weight(1f), Telemetry.formatMinutes(span.studyMinutes), "وقت دراسة",
                    span.deltaPct(prev) { it.studyMinutes }, ZIndigo)
                DeltaStat(Modifier.weight(1f), "${span.reviews}", "مراجعة",
                    span.deltaPct(prev) { it.reviews }, ZCyanDeep)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DeltaStat(Modifier.weight(1f), "${span.lessons}", "دروس",
                    span.deltaPct(prev) { it.lessons }, ZEmerald)
                DeltaStat(Modifier.weight(1f), "${span.wordsAdded}", "كلمات جديدة",
                    span.deltaPct(prev) { it.wordsAdded }, ZAmber)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DeltaStat(Modifier.weight(1f), "${(span.recallRate * 100).toInt()}%", "دقة التذكّر",
                    span.deltaPct(prev) { (it.recallRate * 100).toInt() }, ZPurple)
                DeltaStat(Modifier.weight(1f), "${span.mistakes}", "أخطاء",
                    span.deltaPct(prev) { it.mistakes }, ZRose, inverse = true)
            }
        }
    }
}

@Composable
private fun DeltaStat(
    modifier: Modifier, value: String, label: String, delta: Int, accent: Color, inverse: Boolean = false,
) {
    val improving = if (inverse) delta < 0 else delta > 0
    val neutral = delta == 0
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = accent.copy(alpha = 0.10f)) {
        Column(Modifier.padding(12.dp)) {
            Text(value, color = accent, fontWeight = FontWeight.Black, fontSize = 19.sp)
            Text(label, color = ZTextSecondary, fontSize = 10.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                Telemetry.arrow(delta),
                color = when { neutral -> ZTextMuted; improving -> ZEmerald; else -> ZRose },
                fontSize = 9.sp, fontWeight = FontWeight.Bold,
            )
        }
    }
}

/* ────────────────────────── daily tab ────────────────────────── */

@Composable
private fun TodayDetail(vm: AppViewModel) {
    val t = vm.todayStat
    val y = vm.yesterdayStat
    Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Today, null, tint = ZEmerald, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text("تفصيل اليوم", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
            }
            Spacer(Modifier.height(12.dp))
            RowStat("وقت الدراسة", Telemetry.formatMinutes(t.studyMinutes), "أمس ${Telemetry.formatMinutes(y.studyMinutes)}", ZIndigo)
            RowStat("مراجعات", "${t.reviews}", "أمس ${y.reviews}", ZCyanDeep)
            RowStat("دروس مكتملة", "${t.lessonsCompleted}", "أمس ${y.lessonsCompleted}", ZEmerald)
            RowStat("كلمات مضافة", "${t.wordsAdded}", "أمس ${y.wordsAdded}", ZAmber)
            RowStat("اختبارات", "${t.examsTaken}${if (t.examsTaken > 0) " (${t.examAvg}%)" else ""}", "أمس ${y.examsTaken}", ZPurple)
            RowStat("أخطاء", "${t.mistakes}", "أمس ${y.mistakes}", ZRose)
            RowStat("استماع", Telemetry.formatMinutes((t.listenSeconds / 60).toInt()), "أمس ${Telemetry.formatMinutes((y.listenSeconds / 60).toInt())}", ZCyan)
            vm.peakStudyHour?.let { h ->
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = ZIndigo.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Schedule, null, tint = ZIndigo, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("ساعة ذروتك: ${h}:00 — استغلها لأصعب المهام", color = ZTextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowStat(label: String, value: String, sub: String, accent: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(accent))
        Spacer(Modifier.width(12.dp))
        Text(label, color = ZTextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(sub, color = ZTextMuted, fontSize = 9.sp)
        Spacer(Modifier.width(8.dp))
        Text(value, color = accent, fontWeight = FontWeight.Black, fontSize = 14.sp)
    }
}

@Composable
private fun ForgottenTodayCard(vm: AppViewModel) {
    val forgotten = vm.activeVocab
        .filter { it.lastReviewedDay == Telemetry.today() && it.lastGrade == 1 }
        .take(8)
    if (forgotten.isEmpty()) return
    Surface(shape = RoundedCornerShape(20.dp), color = ZRose.copy(alpha = 0.09f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ErrorOutline, null, tint = ZRose, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("نسيتها اليوم (${forgotten.size})", color = ZRose, fontWeight = FontWeight.Black, fontSize = 14.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                forgotten.forEach { w ->
                    Surface(shape = RoundedCornerShape(50), color = ZCard) {
                        Text(
                            w.english, color = ZTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/* ────────────────────────── charts ────────────────────────── */

/** Dual-series line chart: reviews (filled area) + study minutes (line). */
@Composable
private fun TrendChartCard(title: String, days: List<DayStat>, accent: Color) {
    val maxReviews = (days.maxOfOrNull { it.reviews } ?: 0).coerceAtLeast(1)
    val maxMinutes = (days.maxOfOrNull { it.studyMinutes } ?: 0).coerceAtLeast(1)

    Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ShowChart, null, tint = accent, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SeriesKey(accent, "مراجعات")
                SeriesKey(ZAmber, "دقائق دراسة")
            }
            Spacer(Modifier.height(16.dp))
            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                if (days.size < 2) return@Canvas
                val w = size.width
                val h = size.height
                val stepX = w / (days.size - 1)

                // grid
                repeat(4) { i ->
                    val y = h * i / 3f
                    drawLine(ZBorder.copy(alpha = 0.5f), Offset(0f, y), Offset(w, y), 1.5f)
                }

                // reviews — filled area
                val areaPath = Path().apply {
                    moveTo(0f, h)
                    days.forEachIndexed { i, d ->
                        val x = stepX * i
                        val y = h - (d.reviews.toFloat() / maxReviews) * h * 0.92f
                        lineTo(x, y)
                    }
                    lineTo(w, h)
                    close()
                }
                drawPath(
                    areaPath,
                    Brush.verticalGradient(listOf(accent.copy(alpha = 0.42f), accent.copy(alpha = 0.03f))),
                )
                val linePath = Path().apply {
                    days.forEachIndexed { i, d ->
                        val x = stepX * i
                        val y = h - (d.reviews.toFloat() / maxReviews) * h * 0.92f
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                drawPath(linePath, accent, style = Stroke(width = 4.5f, cap = StrokeCap.Round))

                // study minutes — second line
                val minPath = Path().apply {
                    days.forEachIndexed { i, d ->
                        val x = stepX * i
                        val y = h - (d.studyMinutes.toFloat() / maxMinutes) * h * 0.92f
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                drawPath(minPath, ZAmber.copy(alpha = 0.9f), style = Stroke(width = 3f, cap = StrokeCap.Round))

                // point markers on the reviews series (only when few days)
                if (days.size <= 10) {
                    days.forEachIndexed { i, d ->
                        val x = stepX * i
                        val y = h - (d.reviews.toFloat() / maxReviews) * h * 0.92f
                        drawCircle(accent, radius = 5.5f, center = Offset(x, y))
                        drawCircle(Color.White, radius = 2.2f, center = Offset(x, y))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val step = if (days.size > 10) days.size / 5 else 1
                days.filterIndexed { i, _ -> i % step == 0 }.forEach {
                    Text(it.label, color = ZTextMuted, fontSize = 9.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("ذروة: $maxReviews مراجعة/يوم", color = ZTextMuted, fontSize = 10.sp)
                Text("·", color = ZTextMuted, fontSize = 10.sp)
                Text("ذروة: $maxMinutes دقيقة/يوم", color = ZTextMuted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun SeriesKey(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 14.dp, height = 4.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, color = ZTextMuted, fontSize = 10.sp)
    }
}

/** GitHub-style contribution heatmap — 17 weeks of activity. */
@Composable
private fun HeatmapCard(vm: AppViewModel) {
    val days = vm.heatmapDays(119)
    val weeks = days.chunked(7)
    Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CalendarMonth, null, tint = ZEmerald, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text("خريطة النشاط", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Text("${days.count { it.isActive }} يوم نشط", color = ZTextMuted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                weeks.forEach { week ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        week.forEach { d ->
                            Box(
                                Modifier.size(14.dp).clip(RoundedCornerShape(4.dp))
                                    .background(heatColor(d.intensity))
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("أقل", color = ZTextMuted, fontSize = 9.sp)
                Spacer(Modifier.width(8.dp))
                (0..4).forEach { i ->
                    Box(Modifier.size(11.dp).clip(RoundedCornerShape(4.dp)).background(heatColor(i)))
                    Spacer(Modifier.width(4.dp))
                }
                Spacer(Modifier.width(4.dp))
                Text("أكثر", color = ZTextMuted, fontSize = 9.sp)
                Spacer(Modifier.weight(1f))
                Text("أطول سلسلة: ${vm.bestActivityStreak} يوم", color = ZTextMuted, fontSize = 10.sp)
            }
        }
    }
}

private fun heatColor(intensity: Int): Color = when (intensity) {
    0 -> if (ZThemeState.isDark) Color(0xFF262938) else Color(0xFFE2E4EC) // خلية فارغة محايدة من الثيم
    1 -> Color(0xFF9BE9A8)
    2 -> Color(0xFF40C463)
    3 -> Color(0xFF30A14E)
    else -> Color(0xFF216E39)
}

@Composable
private fun CefrCard(vm: AppViewModel) {
    val (cefr, prog) = vm.cefrEstimate
    val next = Telemetry.nextCefr(cefr)
    Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MilitaryTech, null, tint = ZAmber, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text("مستوى الطلاقة المتوقع", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cefr, color = ZAmber, fontWeight = FontWeight.Black, fontSize = 30.sp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    LinearProgressIndicator(
                        progress = { prog },
                        modifier = Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(4.dp)),
                        color = ZAmber, trackColor = ZBorder,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("${(prog * 100).toInt()}% نحو $next", color = ZTextSecondary, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "يُحتسب من ${vm.masteredCount} كلمة محفوظة + ${vm.completedLessons} درس + دقة الاختبارات ${vm.lifetime.examAvg}%",
                color = ZTextMuted, fontSize = 10.sp, lineHeight = 16.sp,
            )
        }
    }
}

/* ────────────────────────── AI coach ────────────────────────── */

@Composable
private fun CoachCard(vm: AppViewModel, scope: CoachScope) {
    val report = vm.coachReport(scope)
    var showLocal by remember { mutableStateOf(false) }
    val shown = report ?: if (showLocal) vm.quickCoach(scope) else null

    Surface(
        shape = RoundedCornerShape(24.dp), color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.background(Brush.linearGradient(listOf(ZPurple, ZIndigo))).padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Psychology, null, tint = Color.White, modifier = Modifier.size(21.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("المدرب الذكي", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text(
                        shown?.stamp?.takeIf { it.isNotBlank() }?.let { "آخر تحليل: $it" }
                            ?: "تحليل ${scope.label} لأدائك",
                        color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp,
                    )
                }
                if (shown?.local == true) {
                    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.2f)) {
                        Text(
                            "محلي", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (shown == null) {
                Text(
                    "اضغط لتحليل بياناتك: ما الذي نجح، أين الضعف، وماذا تفعل تحديداً في الفترة القادمة.",
                    color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp, lineHeight = 20.sp,
                )
            } else {
                CoachSection("ما الذي نجح", Icons.Filled.ThumbUp, shown.good, Color(0xFFB9F6CA))
                CoachSection("نقاط الضعف", Icons.Filled.ReportProblem, shown.weak, Color(0xFFFFCDD2))
                CoachSection("خطة العلاج", Icons.Filled.Checklist, shown.suggestions, Color(0xFFFFF9C4))
                if (shown.focusNextWeek.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.18f), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CenterFocusStrong, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(shown.focusNextWeek, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp)
                        }
                    }
                }
                if (shown.motivation.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "\u201C${shown.motivation}\u201D",
                        color = Color.White.copy(alpha = 0.93f), fontSize = 12.sp,
                        lineHeight = 20.sp, fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            vm.coachError?.let {
                Text(it, color = Color(0xFFFFE0B2), fontSize = 10.sp)
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    onClick = { if (!vm.isCoaching) vm.runCoach(scope) },
                ) {
                    Row(
                        Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (vm.isCoaching) {
                            CircularProgressIndicator(color = ZIndigo, strokeWidth = 2.dp, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("جاري التحليل…", color = ZIndigo, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Icon(Icons.Filled.AutoAwesome, null, tint = ZIndigo, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (report == null) "حلّل أدائي" else "تحليل جديد",
                                color = ZIndigo, fontWeight = FontWeight.Black, fontSize = 13.sp,
                            )
                        }
                    }
                }
                if (shown == null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.22f),
                        onClick = { showLocal = true },
                    ) {
                        Text(
                            "تحليل سريع", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                        )
                    }
                }
            }
            if (vm.geminiApiKey.isBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "بدون مفتاح Gemini سيُستخدم التحليل المحلي (يعمل دائماً)",
                    color = Color.White.copy(alpha = 0.75f), fontSize = 9.sp,
                )
            }
        }
    }
}

@Composable
private fun CoachSection(title: String, icon: ImageVector, items: List<String>, tint: Color) {
    if (items.isEmpty()) return
    Column(Modifier.padding(bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, color = tint, fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        items.forEach { line ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Text("•", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Text(line, color = Color.White.copy(alpha = 0.93f), fontSize = 12.sp, lineHeight = 19.sp)
            }
        }
    }
}

/* ────────────────────────── memory / skills / lifetime ────────────────────────── */

@Composable
private fun MemoryHealthCard(vm: AppViewModel) {
    Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Memory, null, tint = ZCyan, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text("صحة الذاكرة (FSRS)", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MemoryStat(Modifier.weight(1f), "${(vm.predictedRetention * 100).toInt()}%", "الاحتفاظ المتوقع", ZEmerald)
                MemoryStat(Modifier.weight(1f), "${(vm.trueRecallRate * 100).toInt()}%", "معدل التذكر", ZCyan)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MemoryStat(Modifier.weight(1f), if (vm.avgStability >= 1) "${vm.avgStability.toInt()} يوم" else "<1 يوم", "متوسط الاستقرار", ZAmber)
                MemoryStat(Modifier.weight(1f), "${vm.dueCount}", "مستحقة الآن", ZRose)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "الهدف: الاحتفاظ ${(vm.desiredRetention * 100).toInt()}% — يُجدول النظام كل كلمة لتراجعها لحظة اقتراب نسيانها.",
                color = ZTextMuted, fontSize = 11.sp, lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun MemoryStat(modifier: Modifier, value: String, label: String, accent: Color) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = accent.copy(alpha = 0.10f)) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = accent, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(label, color = ZTextSecondary, fontSize = 11.sp)
        }
    }
}

/** Six-axis radar chart drawn from real skill scores. */
@Composable
private fun SkillRadarCard(vm: AppViewModel) {
    val skills = vm.skillRadar
    val anim by animateFloatAsState(1f, tween(800), label = "radar")

    Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Radar, null, tint = ZPurple, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text("رادار المهارات", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val radius = min(cx, cy) * 0.72f
                    val n = skills.size
                    // grid rings
                    (1..4).forEach { ring ->
                        val r = radius * ring / 4f
                        val p = Path()
                        for (i in 0 until n) {
                            val a = (-Math.PI / 2 + 2 * Math.PI * i / n).toFloat()
                            val x = cx + r * cos(a)
                            val y = cy + r * sin(a)
                            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                        }
                        p.close()
                        drawPath(p, ZBorder.copy(alpha = 0.6f), style = Stroke(width = 1.5f))
                    }
                    // spokes
                    for (i in 0 until n) {
                        val a = (-Math.PI / 2 + 2 * Math.PI * i / n).toFloat()
                        drawLine(
                            ZBorder.copy(alpha = 0.6f),
                            Offset(cx, cy),
                            Offset(cx + radius * cos(a), cy + radius * sin(a)),
                            1.5f,
                        )
                    }
                    // data polygon
                    val dp = Path()
                    for (i in 0 until n) {
                        val a = (-Math.PI / 2 + 2 * Math.PI * i / n).toFloat()
                        val v = (skills[i].value * anim).coerceIn(0.02f, 1f)
                        val x = cx + radius * v * cos(a)
                        val y = cy + radius * v * sin(a)
                        if (i == 0) dp.moveTo(x, y) else dp.lineTo(x, y)
                    }
                    dp.close()
                    drawPath(dp, ZPurple.copy(alpha = 0.30f))
                    drawPath(dp, ZPurple, style = Stroke(width = 3.5f, cap = StrokeCap.Round))
                    for (i in 0 until n) {
                        val a = (-Math.PI / 2 + 2 * Math.PI * i / n).toFloat()
                        val v = (skills[i].value * anim).coerceIn(0.02f, 1f)
                        drawCircle(ZPurple, 5f, Offset(cx + radius * v * cos(a), cy + radius * v * sin(a)))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            skills.forEach { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.emoji, fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.label, color = ZTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text(s.detail, color = ZTextMuted, fontSize = 9.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.width(70.dp)) {
                        LinearProgressIndicator(
                            progress = { s.value },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                            color = ZPurple, trackColor = ZBorder,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("${(s.value * 100).toInt()}%", color = ZPurple, fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun LifetimeCard(vm: AppViewModel) {
    val lt = vm.lifetime
    Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AllInclusive, null, tint = ZEmerald, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text("كل الأوقات", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
            }
            Spacer(Modifier.height(16.dp))
            val cells = listOf(
                Triple(Icons.Filled.Style, "${vm.totalWords}", "كلمة كلية"),
                Triple(Icons.Filled.Verified, "${vm.masteredCount}", "محفوظة"),
                Triple(Icons.Filled.MenuBook, "${vm.completedLessons}", "درس مكتمل"),
                Triple(Icons.Filled.Quiz, "${vm.examHistory.size}", "اختبار"),
                Triple(Icons.Filled.AutoStories, "${lt.stories}", "قطعة مقروءة"),
                Triple(Icons.Filled.Forum, "${lt.conversationTurns}", "جولة محادثة"),
                Triple(Icons.Filled.RecordVoiceOver, "${lt.phonetics}", "تدريب نطق"),
                Triple(Icons.Filled.Link, "${vm.mnemonicReadyCount}", "رابط ذهني"),
                Triple(Icons.Filled.Timer, Telemetry.formatMinutes(lt.studyMinutes), "وقت دراسة"),
            )
            cells.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (icon, value, label) ->
                        Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), color = ZSurfaceVariant) {
                            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(icon, null, tint = ZEmerald, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.height(4.dp))
                                Text(value, color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 14.sp, maxLines = 1)
                                Text(label, color = ZTextMuted, fontSize = 9.sp, maxLines = 1, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * Study-time analytics: real measured minutes, consistency, and the pattern of
 * WHEN the learner studies (weekday + peak hour).
 */
@Composable
private fun StudyTimeCard(vm: AppViewModel, span: StatSpan) {
    val days = span.days
    val total = span.studyMinutes
    val best = days.maxByOrNull { it.studyMinutes }
    // Aggregate by day-of-week (1=Mon .. 7=Sun) to reveal weekly rhythm.
    val byDow = remember(days) {
        val acc = IntArray(7)
        days.forEach { acc[it.date.dayOfWeek.value - 1] += it.studyMinutes }
        acc
    }
    val dowMax = (byDow.maxOrNull() ?: 0).coerceAtLeast(1)
    val dowNames = listOf("إث", "ثل", "أر", "خم", "جم", "سب", "أح")

    Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Timer, null, tint = ZCyanDeep, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text("وقت الدراسة", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Text(Telemetry.formatMinutes(total), color = ZCyanDeep, fontWeight = FontWeight.Black, fontSize = 15.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text("مقيس فعلياً من وقتك على شاشات التعلم", color = ZTextMuted, fontSize = 10.sp)

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniBox(Modifier.weight(1f), Telemetry.formatMinutes(span.avgMinutesPerActiveDay), "متوسط/يوم نشط", ZIndigo)
                MiniBox(Modifier.weight(1f), "${span.activeDays}/${days.size}", "أيام نشطة", ZEmerald)
                MiniBox(
                    Modifier.weight(1f),
                    best?.let { Telemetry.formatMinutes(it.studyMinutes) } ?: "0 د",
                    "أطول يوم", ZAmber,
                )
            }

            // ---- weekday rhythm ----
            Spacer(Modifier.height(16.dp))
            Text("إيقاعك الأسبوعي", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().height(72.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                byDow.forEachIndexed { i, mins ->
                    val h = (mins.toFloat() / dowMax).coerceIn(0.04f, 1f)
                    val isTop = mins == dowMax && mins > 0
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (mins > 0) "$mins" else "",
                            color = ZTextMuted, fontSize = 8.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight(h)
                                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                .background(if (isTop) ZCyanDeep else ZCyanDeep.copy(alpha = 0.35f))
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(dowNames[i], color = ZTextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ---- peak hour ----
            vm.peakStudyHour?.let { h ->
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = ZAmber.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Schedule, null, tint = ZAmber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "أكثر ساعة تراجع فيها: ${h}:00 — ثبّت جلستك اليومية هنا",
                            color = ZTextSecondary, fontSize = 11.sp, lineHeight = 17.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniBox(modifier: Modifier, value: String, label: String, accent: Color) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = accent.copy(alpha = 0.10f)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = accent, fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 1)
            Text(label, color = ZTextSecondary, fontSize = 9.sp, textAlign = TextAlign.Center)
        }
    }
}

/**
 * Curriculum coverage — the heart of "MY course, not generic English".
 * Every figure here is derived from the learner's own imported content.
 */
@Composable
private fun CurriculumCard(vm: AppViewModel) {
    val cur = vm.curriculum
    val withContent = cur.coursesWithContent
    var expanded by remember { mutableStateOf(false) }

    Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.School, null, tint = ZIndigo, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text("تغطية منهجي", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Text("${cur.overallPct}%", color = ZIndigo, fontWeight = FontWeight.Black, fontSize = 15.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "محسوبة من كورساتك المستوردة فقط — لا محتوى عشوائي",
                color = ZTextMuted, fontSize = 10.sp,
            )

            if (withContent.isEmpty()) {
                Spacer(Modifier.height(16.dp))
                Surface(shape = RoundedCornerShape(16.dp), color = ZAmber.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.UploadFile, null, tint = ZAmber, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "لا توجد دروس مستوردة بعد — استورد كورساً لتبدأ القياس الحقيقي.",
                            color = ZTextSecondary, fontSize = 12.sp, lineHeight = 19.sp,
                        )
                    }
                }
                return@Column
            }

            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { cur.overallProgress },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(8.dp)),
                color = ZIndigo, trackColor = ZBorder,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${cur.lessonsDone} من ${cur.lessonsTotal} درساً · ${cur.coursesCompleted} كورس مكتمل من ${withContent.size}",
                color = ZTextSecondary, fontSize = 11.sp,
            )

            // ---- what I have actually studied (content inventory) ----
            Spacer(Modifier.height(16.dp))
            Text("ما درستُه فعلياً", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            val inv = listOf(
                Triple(Icons.Filled.Rule, "${cur.grammarPointsStudied}", "قاعدة"),
                Triple(Icons.Filled.Forum, "${cur.dialogueLinesStudied}", "سطر حوار"),
                Triple(Icons.Filled.MenuBook, "${cur.readingSegmentsStudied}", "مقطع قراءة"),
                Triple(Icons.Filled.Bookmarks, "${cur.expressionsStudied}", "تعبير"),
                Triple(Icons.Filled.ShortText, "${cur.sentencesStudied}", "جملة مفتاحية"),
                Triple(Icons.Filled.Quiz, "${cur.quizItemsAvailable}", "سؤال متاح"),
            )
            inv.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (icon, value, label) ->
                        Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), color = ZSurfaceVariant) {
                            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(icon, null, tint = ZIndigo, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.height(4.dp))
                                Text(value, color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                                Text(label, color = ZTextSecondary, fontSize = 9.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            // ---- per-skill coverage ----
            Spacer(Modifier.height(8.dp))
            Text("التغطية حسب المهارة", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            cur.skills.filter { it.hasContent }.forEach { sk ->
                val c = skillColor(sk.type)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        sk.type.label, color = ZTextSecondary, fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.width(64.dp),
                    )
                    LinearProgressIndicator(
                        progress = { sk.progress },
                        modifier = Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(4.dp)),
                        color = c, trackColor = ZBorder,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${sk.lessonsDone}/${sk.lessonsTotal}",
                        color = c, fontSize = 10.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.width(42.dp), textAlign = TextAlign.End,
                    )
                }
            }

            // ---- next up ----
            cur.nextCourse?.let { nc ->
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(16.dp), color = ZEmerald.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PlayCircle, null, tint = ZEmerald, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("التالي في منهجك", color = ZEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(nc.name, color = ZTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(
                                "بقي ${nc.remaining} درساً · أنت عند ${nc.pct}%",
                                color = ZTextSecondary, fontSize = 10.sp,
                            )
                        }
                    }
                }
            }

            // ---- per-course breakdown (collapsible) ----
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(12.dp), color = Color.Transparent,
                onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (expanded) "إخفاء تفاصيل الكورسات" else "تفاصيل كل كورس (${withContent.size})",
                        color = ZIndigo, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        null, tint = ZIndigo, modifier = Modifier.size(18.dp),
                    )
                }
            }
            AnimatedVisibility(expanded, enter = fadeIn(tween(200)), exit = fadeOut(tween(150))) {
                Column {
                    withContent.sortedWith(compareBy({ it.levelId }, { it.courseId })).forEach { c ->
                        val accent = Color(c.accent)
                        Surface(
                            shape = RoundedCornerShape(12.dp), color = ZSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(accent)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        c.name, color = ZTextPrimary, fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold, maxLines = 1,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (c.isComplete) {
                                        Icon(Icons.Filled.Verified, null, tint = ZEmerald, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text("${c.pct}%", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                }
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { c.progress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                                    color = accent, trackColor = ZBorder,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "${c.type.label} · ${c.lessonsDone}/${c.lessonsTotal} درس" +
                                        if (c.wordsTotal > 0) " · ${c.wordsMastered}/${c.wordsTotal} كلمة محفوظة" else "",
                                    color = ZTextMuted, fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun skillColor(t: CourseType): Color = when (t) {
    CourseType.VOCABULARY -> ZIndigo
    CourseType.GRAMMAR -> ZPurple
    CourseType.READING -> ZCyanDeep
    CourseType.LISTENING -> ZCyan
    CourseType.CONVERSATION -> ZRose
    CourseType.PHONETICS -> ZAmber
    CourseType.WRITING -> ZEmerald
}

@Composable
private fun ExamTrendCard(vm: AppViewModel) {
    val recent = vm.examHistory.takeLast(10)
    Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Assessment, null, tint = ZAmber, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text("مسار الاختبارات", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Text("متوسط ${vm.examAverage}%", color = ZAmber, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().height(96.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                recent.forEach { e ->
                    val h = (e.pct / 100f).coerceIn(0.06f, 1f)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${e.pct}", color = ZTextMuted, fontSize = 8.sp)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight(h).clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                .background(if (e.passed) ZEmerald else ZRose)
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            val skills = vm.skillAccuracy
            if (skills.isNotEmpty()) {
                Text("الدقة حسب المهارة", color = ZTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                skills.entries.sortedBy { it.value }.forEach { (skill, acc) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(skill.emoji, fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(skill.label, color = ZTextSecondary, fontSize = 11.sp, modifier = Modifier.width(96.dp))
                        LinearProgressIndicator(
                            progress = { acc },
                            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(4.dp)),
                            color = if (acc >= 0.7f) ZEmerald else if (acc >= 0.5f) ZAmber else ZRose,
                            trackColor = ZBorder,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("${(acc * 100).toInt()}%", color = ZTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopHardWordsCard(vm: AppViewModel) {
    val leeches = vm.forgottenWords
    if (leeches.isEmpty()) return
    Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, null, tint = ZRose, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text("أصعب 5 كلمات هذا الأسبوع", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
            }
            Spacer(Modifier.height(12.dp))
            leeches.forEachIndexed { i, w ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(22.dp).clip(RoundedCornerShape(8.dp)).background(ZRose.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) { Text("${i + 1}", color = ZRose, fontWeight = FontWeight.Black, fontSize = 10.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(w.english, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(w.arabic, color = ZTextSecondary, fontSize = 10.sp)
                    }
                    Text("نُسيت ${w.lapses}×", color = ZRose, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HardWordRow(w: VocabWord, vm: AppViewModel) {
    Surface(shape = RoundedCornerShape(16.dp), color = ZCard, shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val path = vm.mnemonicPath(w.id)
            if (path != null) {
                coil3.compose.AsyncImage(
                    model = java.io.File(path), contentDescription = w.english,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)),
                )
            } else {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(ZRose))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(w.english, color = ZTextPrimary, fontWeight = FontWeight.Bold)
                Text(w.arabic, color = ZTextSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("صعوبة ${String.format("%.1f", w.difficulty)}", color = ZRose, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                if (w.lapses > 0) Text("نُسيت ${w.lapses}×", color = ZTextMuted, fontSize = 10.sp)
                val miss = vm.examMisses[w.id] ?: 0
                if (miss > 0) Text("خطأ بالاختبار ${miss}×", color = ZAmber, fontSize = 9.sp)
            }
        }
    }
}
/**
 * التقرير العميق أسفل بطاقة المرآة. يُولَّد عند الطلب فقط (زر) حفاظاً على
 * حصة الـ API، ويُخزَّن فلا يُعاد توليده.
 */
@Composable
private fun MirrorInlineReport(vm: AppViewModel) {
    val key = "live_mirror"
    val report = vm.mirrorReport(key)

    if (report == null) {
        Surface(
            shape = RoundedCornerShape(16.dp), color = ZCard, shadowElevation = 3.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = ZPurple, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "التحليل السيكولوجي العميق",
                        color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "يحلّل سرعتك وتوقيتك وقناتك الحسّية ونمط نسيانك، ثم يكتب لك من أنت كمتعلّم.",
                    color = ZTextSecondary, fontSize = 12.sp, lineHeight = 19.sp,
                )
                Spacer(Modifier.height(12.dp))
                if (vm.isMirrorLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = ZPurple, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("جارٍ قراءة بصمتك المعرفية…", color = ZTextSecondary, fontSize = 12.sp)
                    }
                } else {
                    Button(
                        onClick = { vm.generateMirrorReport(key, force = true) },
                        enabled = vm.cognitiveMirror.hasEnoughData,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ZPurple, disabledContainerColor = ZBorder,
                        ),
                    ) {
                        Icon(Icons.Filled.Psychology, null, tint = Color.White, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (vm.cognitiveMirror.hasEnoughData) "اكشف مرآة إدراكي"
                            else "راجع كلمات أكثر أولاً",
                            fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color.White,
                        )
                    }
                }
            }
        }
    } else {
        Surface(
            shape = RoundedCornerShape(16.dp), color = ZCard, shadowElevation = 3.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                MirrorReportView(report)
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { vm.generateMirrorReport(key, force = true) },
                    enabled = !vm.isMirrorLoading,
                ) {
                    Icon(Icons.Filled.Refresh, null, tint = ZPurple, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (vm.isMirrorLoading) "جارٍ التحديث…" else "حدّث التحليل",
                        color = ZPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
/* ══════════════ نشاط الأسبوع (نُقل من الشاشة الرئيسية) ══════════════ */

@Composable
private fun WeeklyActivityCard(vm: AppViewModel) {
    val data = vm.spanFor(CoachScope.WEEKLY).days
    val maxVal = (data.maxOfOrNull { it.reviews } ?: 1).toFloat().coerceAtLeast(1f)
    val dayNames = listOf("أحد", "إثن", "ثلا", "أرب", "خمي", "جمع", "سبت")

    Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.BarChart, null, tint = ZIndigo, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("نشاط الأسبوع", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "${data.sumOf { it.reviews }} مراجعة",
                    color = ZTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().height(110.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                data.forEach { d ->
                    val frac = (d.reviews / maxVal).coerceIn(0f, 1f)
                    val isToday = d.epochDay == com.zmastery.english.data.Telemetry.today()
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        if (d.reviews > 0) {
                            Text(
                                "${d.reviews}",
                                color = if (isToday) ZIndigo else ZTextMuted,
                                fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        Box(
                            Modifier.fillMaxWidth()
                                .height((6 + frac * 74).dp)
                                .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                                .background(
                                    when {
                                        d.reviews == 0 -> ZBorder
                                        isToday -> ZIndigo
                                        else -> ZCyanDeep.copy(alpha = 0.75f)
                                    }
                                )
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            dayNames[d.date.dayOfWeek.value % 7],
                            color = if (isToday) ZIndigo else ZTextMuted,
                            fontSize = 9.sp,
                            fontWeight = if (isToday) FontWeight.Black else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}
