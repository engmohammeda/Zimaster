package com.zmastery.english.ui.screens

import android.media.MediaPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate as rotateScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zmastery.english.R
import com.zmastery.english.data.MomentumMetrics
import com.zmastery.english.data.MysteryCatalog
import com.zmastery.english.data.MysteryReward
import com.zmastery.english.data.RewardRarity
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ==========================================================================
//  الصناديق الغامضة — الجزء 2: واجهات العرض وحركات كسر الأختام
//
//   1. MomentumIndicatorsCard — كرت المؤشرات الثلاثية (🔥 🌱 ⭐)
//   2. MysteryChestCard       — بطاقة الصندوق (مقفل رمادي / مستحق مُغلَّل)
//   3. ChestBreakDialog       — اهتزاز + تشظّي الأغلال + كونفيتي + الكشف
//
//  كل الأنيميشن مبني على Animatable / rememberInfiniteTransition — يعمل على
//  الـ GPU بدون أي Thread أو Timer، فلا استهلاك بطارية ولا إبطاء للتمرير.
// ==========================================================================

/* ════════════════ 1 · كرت المؤشرات الثلاثية ════════════════ */

/**
 * كرت عريض أعلى الصفحة الرئيسية يعرض زخم التعلّم على ثلاثة محاور:
 * الشعلة اليومية · رصيد الاستمرارية · مستوى الإتقان الإدراكي.
 */
@Composable
fun MomentumIndicatorsCard(
    m: MomentumMetrics,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = ZCard,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth(),
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Insights, null, tint = ZIndigo, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("زخم التعلّم", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                if (onClick != null) {
                    Icon(Icons.Filled.ChevronLeft, null, tint = ZTextMuted, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(16.dp))

            // ── 🔥 الشعلة + 🌱 الاستمرارية جنباً إلى جنب ──
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StreakFlameTile(Modifier.weight(1f), m)
                ContinuitySproutTile(Modifier.weight(1f), m)
            }

            Spacer(Modifier.height(16.dp))

            // ── ⭐ شريط الإتقان المتدرّج ──
            MasteryGradientBar(m)

            // ── الدرع النفسي عند انكسار السلسلة ──
            if (m.shieldMessage.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(16.dp), color = ZEmerald.copy(alpha = 0.12f)) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Shield, null, tint = ZEmerald, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            m.shieldMessage, color = ZTextSecondary,
                            fontSize = 11.sp, lineHeight = 17.sp,
                        )
                    }
                }
            }
        }
    }
}

/** 🔥 لهب متوهّج ثلاثي الأبعاد ينبض مع عدد أيام السلسلة. */
@Composable
private fun StreakFlameTile(modifier: Modifier, m: MomentumMetrics) {
    val alive = m.dailyStreak > 0
    val inf = rememberInfiniteTransition(label = "flame")
    val flicker by inf.animateFloat(
        initialValue = if (alive) 0.86f else 1f,
        targetValue = if (alive) 1.14f else 1f,
        animationSpec = infiniteRepeatable(tween(720, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "flick",
    )
    val glow by inf.animateFloat(
        initialValue = 0.25f, targetValue = if (alive) 0.65f else 0.25f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )
    val hot = ZAmber
    val core = ZAmber

    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = ZSurfaceVariant.copy(alpha = 0.55f)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                if (alive) {
                    Box(
                        Modifier.size(46.dp).blur(14.dp).clip(RoundedCornerShape(50))
                            .background(hot.copy(alpha = glow))
                    )
                }
                Text(
                    "\uD83D\uDD25",
                    fontSize = 30.sp,
                    modifier = Modifier.scale(flicker).alpha(if (alive) 1f else 0.35f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${m.dailyStreak}",
                    color = if (alive) hot else ZTextMuted,
                    fontWeight = FontWeight.Black, fontSize = 26.sp,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "يوم", color = if (alive) core else ZTextMuted,
                    fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Text(
                m.streakLabel, color = ZTextSecondary, fontSize = 10.sp,
                textAlign = TextAlign.Center, maxLines = 1,
            )
            if (m.bestStreak > m.dailyStreak) {
                Text("الأفضل ${m.bestStreak}", color = ZTextMuted, fontSize = 9.sp)
            }
        }
    }
}

/** 🌱 برعم أخضر نامٍ ناعم مع نسبة الالتزام. */
@Composable
private fun ContinuitySproutTile(modifier: Modifier, m: MomentumMetrics) {
    val target = m.continuityFraction
    val grow by animateFloatAsState(target, tween(900, easing = FastOutSlowInEasing), label = "grow")
    val inf = rememberInfiniteTransition(label = "sprout")
    val sway by inf.animateFloat(
        initialValue = -3.5f, targetValue = 3.5f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sway",
    )
    val green = ZEmerald

    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = ZSurfaceVariant.copy(alpha = 0.55f)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                // حلقة تقدّم دائرية حول البرعم
                CircularProgressIndicator(
                    progress = { grow },
                    modifier = Modifier.size(46.dp),
                    color = green,
                    trackColor = ZBorder,
                    strokeWidth = 3.dp,
                )
                Text("\uD83C\uDF31", fontSize = 22.sp, modifier = Modifier.rotate(sway))
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${m.continuityPercent}",
                    color = green, fontWeight = FontWeight.Black, fontSize = 26.sp,
                )
                Text(
                    "%", color = green, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Text(
                m.continuityLabel, color = ZTextSecondary, fontSize = 10.sp,
                textAlign = TextAlign.Center, maxLines = 1,
            )
            Text("${m.activeDays30}/30 يوماً", color = ZTextMuted, fontSize = 9.sp)
        }
    }
}

/** ⭐ شريط تقدّم متدرّج الألوان يعرض مستوى الإتقان الإدراكي. */
@Composable
private fun MasteryGradientBar(m: MomentumMetrics) {
    val target = m.masteryFraction
    val anim by animateFloatAsState(target, tween(1000, easing = FastOutSlowInEasing), label = "mastery")
    val inf = rememberInfiniteTransition(label = "shimmer")
    val shimmer by inf.animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sh",
    )

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("\u2B50", fontSize = 15.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                m.masteryHeadline, color = ZTextPrimary,
                fontWeight = FontWeight.Bold, fontSize = 13.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${m.masteryPercent}%", color = ZAmber,
                fontWeight = FontWeight.Black, fontSize = 15.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(8.dp)).background(ZBorder)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(anim.coerceAtLeast(0.02f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF06B6D4),
                                Color(0xFF6366F1),
                                Color(0xFF8B5CF6),
                                Color(0xFFF59E0B).copy(alpha = shimmer),
                            )
                        )
                    )
            )
        }
        Spacer(Modifier.height(8.dp))
        // مكوّنات المعادلة الموزونة — شفافية كاملة للمتعلّم
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MasteryChip(Modifier.weight(1f), "كلمات", m.masteredWordsRatio, "40%", Color(0xFF6366F1))
            MasteryChip(Modifier.weight(1f), "دروس", m.completedLessonsRatio, "30%", Color(0xFF06B6D4))
            MasteryChip(Modifier.weight(1f), "اختبارات", m.avgExamScore, "20%", Color(0xFF8B5CF6))
            MasteryChip(Modifier.weight(1f), "التزام", m.continuityRatio, "10%", ZEmerald)
        }
    }
}

@Composable
private fun MasteryChip(modifier: Modifier, label: String, value: Float, weight: String, accent: Color) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = accent.copy(alpha = 0.10f)) {
        Column(
            Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "${(value * 100).toInt()}%", color = accent,
                fontWeight = FontWeight.Black, fontSize = 12.sp,
            )
            Text(label, color = ZTextMuted, fontSize = 8.sp, maxLines = 1)
            Text(weight, color = accent.copy(alpha = 0.65f), fontSize = 7.sp)
        }
    }
}

/* ════════════════ 2 · بطاقة الصندوق الغامض ════════════════ */

/**
 * بطاقة صندوق بأسلوب ألعاب المغامرات.
 *
 *  • خامل  : رمادي هادئ + عدّاد تنازلي لما تبقّى.
 *  • مختوم : ملوّن حسب الندرة، أغلال ملتفّة، وطفو بطيء يحثّ على النقر.
 *  • مفتوح : يعرض الشارة والجائزة المكشوفة.
 */
@Composable
fun MysteryChestCard(
    reward: MysteryReward,
    currentStreak: Int,
    onOpen: () -> Unit,
    onView: () -> Unit,
) {
    val rarity = reward.rarity
    val accent = Color(rarity.colorArgb)
    val glow = Color(rarity.glowArgb)

    val inf = rememberInfiniteTransition(label = "chest_${reward.id}")
    // طفو رأسي بطيء للصناديق المستحقة
    val hover by inf.animateFloat(
        initialValue = if (reward.isSealed) -4f else 0f,
        targetValue = if (reward.isSealed) 4f else 0f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "hover",
    )
    val pulse by inf.animateFloat(
        initialValue = 0.30f, targetValue = if (reward.isSealed) 0.75f else 0.30f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )

    val bg = when {
        reward.isOpened -> Brush.linearGradient(listOf(accent.copy(alpha = 0.14f), ZCard))
        reward.isSealed -> Brush.linearGradient(
            listOf(accent.copy(alpha = 0.10f + pulse * 0.14f), glow.copy(alpha = 0.06f), ZCard)
        )
        else -> Brush.linearGradient(listOf(ZSurfaceVariant.copy(alpha = 0.35f), ZCard))
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
        onClick = { if (reward.isSealed) onOpen() else if (reward.isOpened) onView() },
        enabled = !reward.isDormant,
    ) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(bg)) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ── أيقونة الصندوق ──
                Box(contentAlignment = Alignment.Center) {
                    if (reward.isSealed) {
                        Box(
                            Modifier.size(62.dp).blur(18.dp).clip(RoundedCornerShape(50))
                                .background(accent.copy(alpha = pulse * 0.5f))
                        )
                    }
                    Box(
                        Modifier
                            .offset(y = hover.dp)
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (reward.isDormant) {
                                    Brush.linearGradient(listOf(ZBorder, ZSurfaceVariant))
                                } else {
                                    Brush.linearGradient(listOf(accent, glow))
                                }
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (reward.isOpened) reward.badgeEmoji else "\uD83C\uDF81",
                            fontSize = 27.sp,
                            modifier = Modifier.alpha(if (reward.isDormant) 0.4f else 1f),
                        )
                    }
                    // أغلال برمجية ملتفّة حول الصندوق المختوم
                    if (reward.isSealed) {
                        ChainOverlay(
                            size = 56.dp,
                            tint = Color.White.copy(alpha = 0.85f),
                            offsetY = hover.dp,
                        )
                    }
                    if (reward.isDormant) {
                        Box(
                            Modifier.size(56.dp).clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Lock, null,
                                tint = Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            reward.title,
                            color = if (reward.isDormant) ZTextMuted else ZTextPrimary,
                            fontWeight = FontWeight.Black, fontSize = 15.sp,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(8.dp))
                        RarityPill(rarity, dim = reward.isDormant)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            reward.isOpened -> reward.rewardName
                            reward.isSealed -> "\u2728 محتوى مجهول — اكسر الختم لتكتشفه"
                            else -> MysteryCatalog.countdownLabel(currentStreak, reward.requiredDay)
                        },
                        color = when {
                            reward.isOpened -> accent
                            reward.isSealed -> ZTextSecondary
                            else -> ZTextMuted
                        },
                        fontSize = 11.sp, lineHeight = 17.sp, maxLines = 2,
                    )

                    // شريط تقدّم للصندوق الخامل
                    if (reward.isDormant && reward.requiredDay > 0) {
                        Spacer(Modifier.height(8.dp))
                        val frac = (currentStreak.toFloat() / reward.requiredDay).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { frac },
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)),
                            color = accent.copy(alpha = 0.65f),
                            trackColor = ZBorder,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "$currentStreak / ${reward.requiredDay} يوم",
                            color = ZTextMuted, fontSize = 9.sp,
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                when {
                    reward.isSealed -> Surface(shape = RoundedCornerShape(12.dp), color = accent) {
                        Column(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(Icons.Filled.LockOpen, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.height(4.dp))
                            Text("اكسر", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    reward.isOpened -> Icon(
                        Icons.Filled.CheckCircle, null, tint = accent, modifier = Modifier.size(22.dp),
                    )
                    else -> Icon(
                        Icons.Filled.HourglassEmpty, null, tint = ZTextMuted, modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RarityPill(rarity: RewardRarity, dim: Boolean = false) {
    val c = Color(rarity.colorArgb)
    Surface(shape = RoundedCornerShape(50), color = c.copy(alpha = if (dim) 0.10f else 0.20f)) {
        Text(
            rarity.label,
            color = if (dim) ZTextMuted else c,
            fontSize = 8.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** أغلال برمجية: حلقتان متقاطعتان + قفل صغير. */
@Composable
private fun ChainOverlay(size: Dp, tint: Color, offsetY: Dp = 0.dp, alpha: Float = 1f) {
    androidx.compose.foundation.Canvas(
        Modifier.size(size).offset(y = offsetY).alpha(alpha)
    ) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.075f)
        // شريط أفقي
        drawLine(
            color = tint,
            start = Offset(0f, h * 0.5f),
            end = Offset(w, h * 0.5f),
            strokeWidth = w * 0.085f,
        )
        // شريط رأسي
        drawLine(
            color = tint,
            start = Offset(w * 0.5f, 0f),
            end = Offset(w * 0.5f, h),
            strokeWidth = w * 0.085f,
        )
        // حلقة القفل في المنتصف
        drawCircle(
            color = tint,
            radius = w * 0.15f,
            center = Offset(w * 0.5f, h * 0.5f),
            style = stroke,
        )
    }
}

/* ════════════════ 3 · مراسم كسر الختم ════════════════ */

private enum class BreakPhase { SEALED, SHAKING, BURSTING, REVEALED }

/**
 * نافذة كسر الختم ملء الشاشة.
 *
 * التسلسل:
 *   1) اهتزاز أفقي سريع لمدة [RewardRarity.shakeMs] (الصندوق يتصدّع).
 *   2) تلاشي الأغلال مع تكبيرها (تشظٍّ وسقوط).
 *   3) انفجار كونفيتي ملوّن + صوت notify_success.wav.
 *   4) كشف تدريجي لمحتوى الصندوق والامتياز الممنوح.
 */
@Composable
fun ChestBreakDialog(
    vm: AppViewModel,
    reward: MysteryReward,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val accent = Color(reward.rarity.colorArgb)
    val glow = Color(reward.rarity.glowArgb)

    var phase by remember(reward.id) { mutableStateOf(if (reward.isOpened) BreakPhase.REVEALED else BreakPhase.SEALED) }
    // الصندوق بعد الفتح (يحمل التقرير المولَّد)
    var opened by remember(reward.id) { mutableStateOf(if (reward.isOpened) reward else null) }

    val shake = remember { Animatable(0f) }
    val chainAlpha = remember { Animatable(1f) }
    val chainScale = remember { Animatable(1f) }
    val lidLift = remember { Animatable(0f) }

    suspend fun runCeremony() {
        phase = BreakPhase.SHAKING
        // ① اهتزاز أفقي سريع ومتكرر — الصندوق يتصدّع تحت الضغط
        val ms = reward.rarity.shakeMs
        shake.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = ms
                val step = ms / 14
                var t = 0
                var amp = 5f
                while (t < ms) {
                    (if ((t / step) % 2 == 0) amp else -amp) at t
                    amp = (amp + 1.6f).coerceAtMost(17f)
                    t += step
                }
                0f at ms
            },
        )
        // ② تشظّي الأغلال: تلاش ناعم مع تكبير
        phase = BreakPhase.BURSTING
        opened = vm.openMysteryReward(reward.id) ?: reward
        playSuccessSound(ctx)
        kotlinx.coroutines.coroutineScope {
            launch { chainScale.animateTo(1.9f, tween(420, easing = FastOutSlowInEasing)) }
            launch { chainAlpha.animateTo(0f, tween(420, easing = LinearEasing)) }
            launch { lidLift.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
        }
        phase = BreakPhase.REVEALED
    }

    Dialog(
        onDismissRequest = { if (phase == BreakPhase.REVEALED || phase == BreakPhase.SEALED) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                // خلفية داكنة غامضة
                .background(
                    Brush.radialGradient(
                        listOf(
                            accent.copy(alpha = 0.22f),
                            Color(0xFF0B0A14).copy(alpha = 0.96f),
                            Color(0xFF05040A),
                        ),
                        radius = 1400f,
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            // ③ انفجار الكونفيتي
            if (phase == BreakPhase.BURSTING || phase == BreakPhase.REVEALED) {
                ConfettiBurst(
                    count = reward.rarity.confetti,
                    seed = reward.id.hashCode(),
                    palette = listOf(accent, glow, ZAmber, ZEmerald, Color(0xFFF43F5E)),
                )
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(12.dp))
                RarityPill(reward.rarity)
                Spacer(Modifier.height(16.dp))

                // ── الصندوق نفسه ──
                Box(contentAlignment = Alignment.Center) {
                    val haloAlpha = if (phase == BreakPhase.REVEALED) 0.55f else 0.30f
                    Box(
                        Modifier.size(190.dp).blur(48.dp).clip(RoundedCornerShape(50))
                            .background(accent.copy(alpha = haloAlpha))
                    )
                    Box(
                        Modifier
                            .offset(x = shake.value.dp)
                            .size(132.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Brush.linearGradient(listOf(accent, glow))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (phase == BreakPhase.REVEALED) reward.badgeEmoji else "\uD83C\uDF81",
                            fontSize = if (phase == BreakPhase.REVEALED) 62.sp else 58.sp,
                            modifier = Modifier.scale(1f + lidLift.value * 0.12f),
                        )
                    }
                    // الأغلال (تتشظى وتختفي)
                    if (chainAlpha.value > 0.01f) {
                        ChainOverlay(
                            size = (132 * chainScale.value).dp,
                            tint = Color.White.copy(alpha = 0.9f),
                            offsetY = 0.dp,
                            alpha = chainAlpha.value,
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    reward.title,
                    color = Color.White, fontWeight = FontWeight.Black, fontSize = 23.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    when (phase) {
                        BreakPhase.SEALED -> "صندوق مختوم — المحتوى مجهول تماماً"
                        BreakPhase.SHAKING -> "الختم يتصدّع…"
                        BreakPhase.BURSTING -> "انكسر الختم!"
                        BreakPhase.REVEALED -> "تم الكشف \uD83C\uDF89"
                    },
                    color = glow, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.height(24.dp))

                // ── الكشف التدريجي ──
                AnimatedVisibility(
                    visible = phase == BreakPhase.REVEALED,
                    enter = fadeIn(tween(700)) + scaleIn(tween(700), initialScale = 0.86f),
                ) {
                    val r = opened ?: reward
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RevealPanel(
                            icon = Icons.Filled.CardGiftcard,
                            title = "الجائزة المكشوفة",
                            body = r.rewardName,
                            accent = glow,
                        )
                        if (r.xpAwarded > 0) {
                            Spacer(Modifier.height(12.dp))
                            RevealPanel(
                                icon = Icons.Filled.Bolt,
                                title = "نقاط الخبرة",
                                body = "+${r.xpAwarded} XP أُضيفت لرصيدك",
                                accent = ZAmber,
                            )
                        }
                        r.privilegeAr?.let {
                            Spacer(Modifier.height(12.dp))
                            RevealPanel(
                                icon = Icons.Filled.WorkspacePremium,
                                title = "الصلاحية السيادية",
                                body = it,
                                accent = Color(0xFFA855F7),
                            )
                        }
                        r.themeUnlockKey?.let {
                            Spacer(Modifier.height(12.dp))
                            RevealPanel(
                                icon = Icons.Filled.Palette,
                                title = "سمة مفتوحة",
                                body = it,
                                accent = Color(0xFF06B6D4),
                            )
                        }
                        // مرآة الإدراك: التقرير المحلّي يظهر فوراً، ثم يُستبدل
                        // تلقائياً بتقرير Gemini المخصّص فور وصوله.
                        val live = vm.mysteryRewards.firstOrNull { it.id == r.id } ?: r
                        if (live.descriptionHtmlAr.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            HtmlReportPanel(
                                html = live.descriptionHtmlAr,
                                accent = glow,
                                loading = vm.chestMirrorLoadingId == r.id,
                                onRegenerate = { vm.regenerateRewardMirror(r.id) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))

                when (phase) {
                    BreakPhase.SEALED -> {
                        val scope = rememberCoroutineScope()
                        Button(
                            onClick = { scope.launch { runCeremony() } },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                        ) {
                            Icon(Icons.Filled.LockOpen, null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("اكسر الختم", color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = onDismiss) {
                            Text("لاحقاً", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                        }
                    }
                    BreakPhase.REVEALED -> {
                        Button(
                            onClick = { vm.clearJustOpened(); onDismiss() },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                        ) {
                            Text("رائع، تابع", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                        }
                    }
                    else -> {
                        // أثناء المراسم: لا أزرار حتى لا يُقاطع الاحتفال
                        CircularProgressIndicator(color = glow, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun RevealPanel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    accent: Color,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.07f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(4.dp))
                Text(body, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
            }
        }
    }
}

/**
 * يعرض تقرير الصندوق (مرآة الإدراك) بعارض HTML أصلي.
 *
 * يستخدم [com.zmastery.english.ui.components.HtmlText] المبني على TextView +
 * HtmlCompat.fromHtml، فيدعم كل الوسوم التي يعيدها Gemini
 * (h3/p/strong/ul/li/div class='highlight') بجودة طباعية عربية صحيحة
 * الاتجاه — بلا WebView وبلا مكتبات إضافية.
 */
@Composable
private fun HtmlReportPanel(
    html: String,
    accent: Color,
    loading: Boolean = false,
    onRegenerate: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.055f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("مرآة الإدراك", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                if (loading) {
                    CircularProgressIndicator(
                        color = accent, strokeWidth = 1.6.dp,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("يُحلَّل بالذكاء…", color = accent.copy(alpha = 0.8f), fontSize = 10.sp)
                } else if (onRegenerate != null) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = accent.copy(alpha = 0.16f),
                        onClick = onRegenerate,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Refresh, null, tint = accent, modifier = Modifier.size(11.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("جدّد", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            com.zmastery.english.ui.components.HtmlText(
                html = html,
                modifier = Modifier.fillMaxWidth(),
                textColor = Color.White.copy(alpha = 0.90f),
                accentColor = accent,
                highlightColor = accent.copy(alpha = 0.18f),
                fontSizeSp = 13.5f,
            )
        }
    }
}

/* ════════════════ نظام جسيمات الاحتفال ════════════════ */

private data class Particle(
    val angle: Float,
    val speed: Float,
    val color: Color,
    val size: Float,
    val spin: Float,
    val delay: Float,
    val square: Boolean,
)

/**
 * انفجار كونفيتي: جسيمات تنطلق شعاعياً من المركز ثم تتساقط بالجاذبية.
 * تُرسم كلها في Canvas واحد — إطار واحد فقط لكل 240 جسيماً.
 */
@Composable
fun ConfettiBurst(
    count: Int,
    seed: Int,
    palette: List<Color>,
    durationMs: Int = 2600,
) {
    val particles = remember(seed, count) {
        val rnd = Random(seed)
        List(count) {
            Particle(
                angle = rnd.nextFloat() * 2f * PI.toFloat(),
                speed = 0.35f + rnd.nextFloat() * 0.95f,
                color = palette[rnd.nextInt(palette.size)],
                size = 5f + rnd.nextFloat() * 9f,
                spin = (rnd.nextFloat() - 0.5f) * 24f,
                delay = rnd.nextFloat() * 0.18f,
                square = rnd.nextBoolean(),
            )
        }
    }
    val t = remember { Animatable(0f) }
    LaunchedEffect(seed) { t.animateTo(1f, tween(durationMs, easing = LinearEasing)) }

    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height * 0.42f
        val reach = size.minDimension * 0.95f

        particles.forEach { p ->
            val local = ((t.value - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            // تباطؤ أفقي + جاذبية رأسية
            val ease = 1f - (1f - local) * (1f - local)
            val dx = cos(p.angle) * p.speed * reach * ease
            val dy = sin(p.angle) * p.speed * reach * ease +
                (local * local) * size.height * 0.85f
            val alpha = (1f - local * local).coerceIn(0f, 1f)
            if (alpha <= 0.02f) return@forEach

            val px = cx + dx
            val py = cy + dy
            if (py > size.height + 40f) return@forEach

            rotateScope(degrees = p.spin * local * 18f, pivot = Offset(px, py)) {
                if (p.square) {
                    drawRect(
                        color = p.color.copy(alpha = alpha),
                        topLeft = Offset(px - p.size / 2f, py - p.size / 2f),
                        size = Size(p.size, p.size * 1.7f),
                    )
                } else {
                    drawCircle(
                        color = p.color.copy(alpha = alpha),
                        radius = p.size * 0.55f,
                        center = Offset(px, py),
                    )
                }
            }
        }
    }
}

/** يشغّل صوت الفوز المرفق مرة واحدة ثم يحرّر الموارد. */
private fun playSuccessSound(ctx: android.content.Context) {
    runCatching {
        MediaPlayer.create(ctx, R.raw.notify_success)?.apply {
            setOnCompletionListener { runCatching { it.release() } }
            start()
        }
    }
}

/* ════════════════ شريط تشويقي للصفحة الرئيسية ════════════════ */

/** صف نابض يعلن عن صندوق مختوم دون كشف محتواه. */
@Composable
fun SealedRewardTeaser(reward: MysteryReward, onOpen: () -> Unit) {
    val accent = Color(reward.rarity.colorArgb)
    val glow = Color(reward.rarity.glowArgb)
    val inf = rememberInfiniteTransition(label = "teaser")
    val pulse by inf.animateFloat(
        initialValue = 0.35f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "p",
    )
    val bob by inf.animateFloat(
        initialValue = -3f, targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b",
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
    ) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.10f + pulse * 0.16f), glow.copy(alpha = 0.05f), ZCard)
                    )
                )
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(54.dp).blur(16.dp).clip(RoundedCornerShape(50))
                            .background(accent.copy(alpha = pulse * 0.6f))
                    )
                    Text("\uD83C\uDF81", fontSize = 32.sp, modifier = Modifier.offset(y = bob.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "صندوق غامض ينتظرك!",
                            color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp,
                        )
                        Spacer(Modifier.width(8.dp))
                        RarityPill(reward.rarity)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${reward.title} — اكسر الختم لتكتشف الجائزة",
                        color = ZTextSecondary, fontSize = 11.sp, maxLines = 1,
                    )
                }
                Icon(Icons.Filled.ChevronLeft, null, tint = accent, modifier = Modifier.size(22.dp))
            }
        }
    }
}
