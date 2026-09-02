package com.zmastery.english.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.data.DailyTask
import com.zmastery.english.data.Telemetry
import com.zmastery.english.ui.components.SoftCard
import com.zmastery.english.ui.theme.*

// ══════════════════════════════════════════════════════════════════════════
// كتل الشاشة الرئيسية الجديدة — «هجين التركيز + الإتقان».
//
// الفلسفة: الشاشة تجيب سؤالين فقط، وبهذا الترتيب:
//   ① ماذا أفعل الآن؟      → NextActionHero  (بطاقة بطل واحدة مهيمنة)
//   ② كم تقدمت فعلاً؟      → MasteryPanel    (إتقان بالأرقام لا بالأيام)
//
// السبب: عدّاد الأيام يقيس الحضور لا التعلّم. لوحة الإتقان تعرض الكلمات
// المتقنة ومستوى CEFR — أرقاماً تنمو بالفهم، لا بمجرد فتح التطبيق.
// ══════════════════════════════════════════════════════════════════════════

/**
 * ① بطاقة البطل — الإجراء التالي الوحيد.
 *
 * تحل محل قائمة المهام الطويلة: تعرض **مهمة واحدة** بأكبر وزن بصري ممكن،
 * مع زر بدء عريض. بقية المهام تُطوى في [RemainingTasksStrip].
 */
@Composable
fun NextActionHero(
    task: DailyTask?,
    doneCount: Int,
    totalCount: Int,
    dayLabel: String,
    allDone: Boolean,
    onStart: () -> Unit,
) {
    val shape = RoundedCornerShape(ZRadius.XL)
    val gradient = if (allDone) {
        Brush.linearGradient(listOf(ZEmerald, ZCyanDeep))
    } else {
        Brush.linearGradient(listOf(ZIndigo, ZPurple))
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(gradient)
            .padding(ZSpace.XL),
    ) {
        Column(Modifier.fillMaxWidth()) {
            // سطر علوي خافت: السياق (اليوم + كم أنجزت)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (allDone) "اكتملت خطة اليوم" else "الخطوة التالية",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (totalCount > 0) {
                    Text(
                        "$doneCount / $totalCount · $dayLabel",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(Modifier.height(ZSpace.M))

            if (allDone || task == null) {
                Text(
                    if (allDone) "أنهيت كل شيء لليوم 🎉" else "لا مهام بعد",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 32.sp,
                )
                Spacer(Modifier.height(ZSpace.S))
                Text(
                    if (allDone) "راجع كلماتك أو اقرأ قصة — تقدم إضافي بلا ضغط."
                    else "أضف محتوى أو استورد درساً لتبدأ خطة اليوم.",
                    color = Color.White.copy(alpha = 0.80f),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            } else {
                Text(
                    task.title,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 34.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(ZSpace.XS))
                Text(
                    task.subtitle,
                    color = Color.White.copy(alpha = 0.80f),
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                // شريط تقدم المهمة نفسها — يظهر فقط للمهام متعددة الخطوات
                if (task.target > 1) {
                    Spacer(Modifier.height(ZSpace.M))
                    val frac = (task.progress.toFloat() / task.target).coerceIn(0f, 1f)
                    val animated by animateFloatAsState(frac, tween(600), label = "heroTask")
                    LinearProgressIndicator(
                        progress = { animated },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(ZRadius.PILL)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.24f),
                    )
                    Spacer(Modifier.height(ZSpace.XS))
                    Text(
                        "${task.progress} / ${task.target}",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(Modifier.height(ZSpace.XL))

            // زر البدء — عريض بالكامل، أعلى تباين ممكن على التدرج
            Surface(
                onClick = onStart,
                shape = RoundedCornerShape(ZRadius.PILL),
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (allDone) Icons.Filled.Refresh else Icons.Filled.PlayArrow,
                        null,
                        tint = ZPurple,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(ZSpace.S))
                    Text(
                        if (allDone) "تدريب إضافي" else "ابدأ الآن",
                        color = ZPurple,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

/**
 * شريط المهام المتبقية — صف أفقي مضغوط من حلقات صغيرة.
 * يبقي بقية الخطة مرئية دون أن تنافس بطاقة البطل.
 */
@Composable
fun RemainingTasksStrip(tasks: List<DailyTask>, onOpen: (DailyTask) -> Unit) {
    if (tasks.isEmpty()) return
    SoftCard(modifier = Modifier.fillMaxWidth(), radius = ZRadius.L) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ZSpace.M, vertical = ZSpace.L),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            tasks.take(4).forEach { task ->
                MiniTaskRing(task) { onOpen(task) }
            }
        }
    }
}

@Composable
private fun MiniTaskRing(task: DailyTask, onClick: () -> Unit) {
    val frac = if (task.target <= 0) 0f
    else (task.progress.toFloat() / task.target).coerceIn(0f, 1f)
    val animated by animateFloatAsState(frac, tween(600), label = "miniRing")
    val tint = when {
        task.done -> ZEmerald
        frac > 0f -> ZCyan
        else -> ZTextMuted
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(ZRadius.S))
            .clickable(onClick = onClick)
            .padding(horizontal = ZSpace.XS, vertical = ZSpace.XS)
            .widthIn(max = 76.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(44.dp)) {
                val stroke = 4.dp.toPx()
                val inset = stroke / 2
                drawArc(
                    color = tint.copy(alpha = 0.20f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                if (animated > 0f) {
                    drawArc(
                        color = tint,
                        startAngle = -90f,
                        sweepAngle = 360f * animated,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
            if (task.done) {
                Icon(Icons.Filled.Check, null, tint = ZEmerald, modifier = Modifier.size(18.dp))
            } else {
                Icon(homeTaskIcon(task.icon), null, tint = tint, modifier = Modifier.size(17.dp))
            }
        }
        Spacer(Modifier.height(ZSpace.XS))
        Text(
            task.title,
            color = if (task.done) ZTextMuted else ZTextSecondary,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * ② لوحة الإتقان — الجواب على «هل تقدمت فعلاً؟».
 *
 * الحلقة الكبرى = الكلمات المتقنة (رقم ينمو بالفهم فقط).
 * إلى جانبها ثلاث إحصاءات، وتحتها شريط CEFR المجزّأ.
 */
@Composable
fun MasteryPanel(
    mastered: Int,
    totalWords: Int,
    lessonsDone: Int,
    reviewsToday: Int,
    accuracyPct: Int,
    cefr: String,
    cefrProgress: Float,
    onOpenAnalytics: () -> Unit,
) {
    SoftCard(modifier = Modifier.fillMaxWidth(), radius = ZRadius.XL, onClick = onOpenAnalytics) {
        Column(Modifier.padding(ZSpace.XL)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // الحلقة الكبرى — نسبة الإتقان من إجمالي المفردات
                val frac = if (totalWords <= 0) 0f
                else (mastered.toFloat() / totalWords).coerceIn(0f, 1f)
                val animated by animateFloatAsState(frac, tween(900), label = "masteryRing")

                Box(contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(124.dp)) {
                        val stroke = 12.dp.toPx()
                        val inset = stroke / 2
                        drawArc(
                            color = ZBorder,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - stroke, size.height - stroke),
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                        if (animated > 0f) {
                            drawArc(
                                brush = Brush.sweepGradient(listOf(ZCyan, ZEmerald, ZCyan)),
                                startAngle = -90f,
                                sweepAngle = 360f * animated,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = Size(size.width - stroke, size.height - stroke),
                                style = Stroke(width = stroke, cap = StrokeCap.Round),
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$mastered",
                            color = ZTextPrimary,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("كلمة متقنة", color = ZTextSecondary, fontSize = 11.sp)
                        if (totalWords > 0) {
                            Text(
                                "من $totalWords",
                                color = ZTextMuted,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }

                Spacer(Modifier.width(ZSpace.L))

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ZSpace.M)) {
                    MasteryStat(ZEmerald, "$lessonsDone", "درساً مكتملاً")
                    MasteryStat(ZCyan, "$reviewsToday", "مراجعة اليوم")
                    MasteryStat(
                        if (accuracyPct >= 70) ZAmber else ZRose,
                        if (accuracyPct > 0) "$accuracyPct%" else "—",
                        "دقة الإجابات",
                    )
                }
            }

            Spacer(Modifier.height(ZSpace.XL))

            // شريط CEFR المجزّأ — يجعل «أين أنا» مرئياً بلا أرقام مجردة
            CefrTrack(cefr, cefrProgress)
        }
    }
}

@Composable
private fun MasteryStat(color: Color, value: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(ZRadius.PILL))
                .background(color),
        )
        Spacer(Modifier.width(ZSpace.S))
        Text(value, color = ZTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(ZSpace.XS))
        Text(
            label,
            color = ZTextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** شريط المستوى الأوروبي — 6 خانات، الحالية مملوءة جزئياً. */
@Composable
private fun CefrTrack(cefr: String, progress: Float) {
    val bands = listOf("A1", "A2", "B1", "B2", "C1", "C2")
    val currentIndex = bands.indexOf(cefr).coerceAtLeast(0)
    val next = Telemetry.nextCefr(cefr)

    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("مستواك التقديري", color = ZTextSecondary, fontSize = 11.sp)
            Text(
                if (cefr == "C2") "أعلى مستوى" else "التالي · $next",
                color = ZTextMuted,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(ZSpace.S))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ZSpace.XS),
        ) {
            bands.forEachIndexed { i, band ->
                val fill = when {
                    i < currentIndex -> 1f
                    i == currentIndex -> progress.coerceIn(0.04f, 1f)
                    else -> 0f
                }
                val animated by animateFloatAsState(fill, tween(700), label = "cefr$band")
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(ZRadius.PILL))
                            .background(ZBorder),
                    ) {
                        if (animated > 0f) {
                            Box(
                                Modifier
                                    .fillMaxWidth(animated)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(ZRadius.PILL))
                                    .background(if (i <= currentIndex) ZCyan else ZBorder),
                            )
                        }
                    }
                    Spacer(Modifier.height(ZSpace.XS))
                    Text(
                        band,
                        color = if (i == currentIndex) ZCyan else ZTextMuted,
                        fontSize = 10.sp,
                        fontWeight = if (i == currentIndex) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/** أيقونة المهمة — نسخة مشتركة بين بطاقة البطل والشريط المصغّر. */
internal fun homeTaskIcon(k: String): ImageVector = when (k) {
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
