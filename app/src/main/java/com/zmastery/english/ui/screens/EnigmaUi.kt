package com.zmastery.english.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.data.ChestMood
import com.zmastery.english.data.CognitiveMirror
import com.zmastery.english.data.DecayState
import com.zmastery.english.data.MirrorReport
import com.zmastery.english.data.RescueMission
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel

// ==========================================================================
//  واجهات المرحلة الثالثة والرابعة
//
//   • CrackingChestCard — صندوق اليوم الباكي (دخان أحمر + تصدّع متصاعد)
//   • RescueGateCard    — بوابة الإنقاذ البنفسجية المتوهّجة
//   • CognitiveMirrorCard / MirrorReportView — مرآة الإدراك
//
//  كل الأنيميشن مبنيّ على rememberInfiniteTransition (GPU-driven) بدون أي
//  مؤقتات أو Threads — لا استهلاك بطارية ولا إبطاء للتمرير.
// ==========================================================================

/* ══════════════════════ 1 · صندوق اليوم الباكي ══════════════════════ */

/**
 * منبّه بصري صامت: صندوق متصدّع تتصاعد منه أدخنة حمراء.
 * لا يظهر إلا عندما تكون هناك سلسلة حقيقية معرّضة للضياع.
 */
@Composable
fun CrackingChestCard(
    decay: DecayState,
    onRescue: () -> Unit,
) {
    if (!decay.isCracking) return

    val inf = rememberInfiniteTransition(label = "crack")
    // اهتزاز خفيف يتصاعد مع الشدّة
    val shake by inf.animateFloat(
        initialValue = -1.4f * decay.severity,
        targetValue = 1.4f * decay.severity,
        animationSpec = infiniteRepeatable(tween(110, easing = LinearEasing), RepeatMode.Reverse),
        label = "shake",
    )
    // نبض الوهج الأحمر
    val glow by inf.animateFloat(
        initialValue = 0.35f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )
    // ارتفاع الدخان
    val smoke by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "smoke",
    )

    val danger = Color(0xFFDC2626)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            danger.copy(alpha = 0.16f + glow * 0.12f),
                            Color(0xFF7F1D1D).copy(alpha = 0.10f),
                            ZCard,
                        )
                    )
                ),
        ) {
            // ── طبقة الدخان المتصاعد ──
            SmokeLayer(progress = smoke, intensity = decay.severity, tint = danger)

            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // الصندوق المتصدّع
                    Box(
                        Modifier
                            .size(58.dp)
                            .rotate(shake)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(listOf(danger, Color(0xFF991B1B)))
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.HeartBroken, null,
                            tint = Color.White.copy(alpha = 0.55f + glow * 0.45f),
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("\uD83D\uDD25", fontSize = 15.sp)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "سلسلتك تتصدّع!",
                                color = danger, fontWeight = FontWeight.Black, fontSize = 17.sp,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${decay.streakAtRisk} يوماً من الالتزام على وشك الضياع",
                            color = ZTextSecondary, fontSize = 12.sp, lineHeight = 18.sp,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // العدّاد التنازلي
                Surface(shape = RoundedCornerShape(16.dp), color = danger.copy(alpha = 0.12f)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Timer, null, tint = danger, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("تبقّى", color = ZTextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            decay.timeLeftLabel,
                            color = danger, fontWeight = FontWeight.Black, fontSize = 15.sp,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "قبل منتصف الليل",
                            color = ZTextMuted, fontSize = 10.sp,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // شريط التآكل — يمتلئ كلما اقترب الخطر
                Box(
                    Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp))
                        .background(ZBorder),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(decay.severity)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Brush.horizontalGradient(listOf(ZAmber, danger))),
                    )
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onRescue,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = danger),
                ) {
                    Icon(Icons.Filled.Bolt, null, tint = Color.White, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "أنقذ سلسلتك الآن (3 دقائق)",
                        fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.White,
                    )
                }
            }
        }
    }
}

/** أدخنة حمراء متصاعدة مرسومة على Canvas — خفيفة جداً. */
@Composable
private fun SmokeLayer(progress: Float, intensity: Float, tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val n = (5 * intensity).toInt().coerceIn(2, 6)
        for (i in 0 until n) {
            val seed = i * 0.37f
            val local = ((progress + seed) % 1f)
            val x = size.width * (0.12f + seed * 0.72f % 0.8f)
            val y = size.height * (1f - local)
            val r = (8f + local * 26f) * intensity
            val alpha = ((1f - local) * 0.20f * intensity).coerceIn(0f, 0.25f)
            drawCircle(
                color = tint.copy(alpha = alpha),
                radius = r,
                center = Offset(x, y),
            )
        }
    }
}

/* ══════════════════════ 2 · بوابة الإنقاذ ══════════════════════ */

/**
 * البوابة البنفسجية المتوهّجة. تظهر بعد انكسار السلسلة —
 * بلا لوم، وبفرصة فورية لاستعادة الشعلة كاملة.
 */
@Composable
fun RescueGateCard(
    rescue: RescueMission,
    onStart: () -> Unit,
    onClaim: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!rescue.isActive) return

    val violet = Color(0xFF8B5CF6)
    val inf = rememberInfiniteTransition(label = "gate")
    val halo by inf.animateFloat(
        initialValue = 0.30f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "halo",
    )
    val spin by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "spin",
    )
    val done = rescue.completed
    val animProgress by animateFloatAsState(rescue.ratio, tween(600), label = "rp")

    Surface(shape = RoundedCornerShape(24.dp), color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            violet.copy(alpha = 0.20f + halo * 0.14f),
                            Color(0xFF4C1D95).copy(alpha = 0.14f),
                            ZCard,
                        )
                    )
                )
                .padding(20.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // البوّابة الدوّارة
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(64.dp).rotate(spin).clip(RoundedCornerShape(24.dp))
                                .background(
                                    Brush.sweepGradient(
                                        listOf(violet, Color(0xFFC4B5FD), violet, Color(0xFF6D28D9), violet)
                                    )
                                )
                                .blur(if (done) 0.dp else 1.dp),
                        )
                        Box(
                            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(ZCard),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (done) Icons.Filled.LocalFireDepartment else Icons.Filled.Shield,
                                null,
                                tint = if (done) ZAmber else violet,
                                modifier = Modifier.size(28.dp).scale(if (done) 1f + halo * 0.12f else 1f),
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Surface(shape = RoundedCornerShape(50), color = violet.copy(alpha = 0.18f)) {
                            Text(
                                if (done) "المهمة اكتملت" else "مهمة إنقاذ عاجلة",
                                color = violet, fontSize = 10.sp, fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (done) "شعلتك جاهزة للعودة! \uD83D\uDD25"
                            else "أنقذ شعلتك (${rescue.streakToRestore} يوماً)",
                            color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 17.sp,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    if (done)
                        "لقد أثبتّ أن انقطاعك كان استثناءً لا قاعدة. استلم سلسلتك المستعادة كاملة."
                    else
                        "لا لوم ولا بداية من الصفر. ${rescue.kindEnum.detail} " +
                            "وستعود سلسلتك (${rescue.streakToRestore} يوماً) كما كانت تماماً.",
                    color = ZTextSecondary, fontSize = 13.sp, lineHeight = 21.sp,
                )

                Spacer(Modifier.height(16.dp))

                // شريط تقدّم المهمة
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(rescue.kindEnum.emoji, fontSize = 15.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        rescue.kindEnum.label,
                        color = ZTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${rescue.progress} / ${rescue.target}",
                        color = violet, fontSize = 13.sp, fontWeight = FontWeight.Black,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(4.dp)).background(ZBorder),
                ) {
                    Box(
                        Modifier.fillMaxWidth(animProgress).fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.horizontalGradient(listOf(violet, Color(0xFFC4B5FD)))
                            ),
                    )
                }

                Spacer(Modifier.height(16.dp))

                if (done) {
                    Button(
                        onClick = onClaim,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ZAmber),
                    ) {
                        Icon(Icons.Filled.LocalFireDepartment, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "استعد شعلتك (${rescue.streakToRestore} \uD83D\uDD25)",
                            fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.White,
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onStart,
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = violet),
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("ابدأ الإنقاذ", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color.White)
                        }
                        TextButton(onClick = onDismiss, modifier = Modifier.height(50.dp)) {
                            Text("لاحقاً", color = ZTextMuted, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

/* ══════════════════════ 2b · دروع تجميد السلسلة ══════════════════════ */

/**
 * بطاقة الدروع الثلاثة.
 *
 * تعرض الخانات الثلاث دائماً (ممتلئة/فارغة) حتى يدرك المتعلّم بصرياً أن
 * لديه حماية محدودة وقابلة للنفاد — وهذا جوهر الخوف من الفقد الإيجابي.
 *
 * @param freezes       عدد الدروع المملوكة (0..3)
 * @param lastUsedDay   يوم آخر استهلاك (لعرض شارة "استُهلك أمس")
 * @param todayDay      اليوم الحالي (Epoch Day)
 */
@Composable
fun StreakShieldsCard(
    freezes: Int,
    lastUsedDay: Long,
    todayDay: Long,
) {
    val max = com.zmastery.english.data.PerkWallet.MAX_STREAK_FREEZES
    val teal = ZCyanDeep
    val justUsed = lastUsedDay > 0L && (todayDay - lastUsedDay) <= 1L

    val inf = rememberInfiniteTransition(label = "shieldGlow")
    val glow by inf.animateFloat(
        initialValue = 0.35f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sg",
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ZCard,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                        .background(teal.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Shield, null, tint = teal, modifier = Modifier.size(21.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "دروع تجميد السلسلة",
                        color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp,
                    )
                    Text(
                        if (freezes > 0)
                            "يحمي كل درع يوماً واحداً فائتاً تلقائياً"
                        else
                            "لا دروع — يوم واحد فائت يكسر سلسلتك",
                        color = if (freezes > 0) ZTextSecondary else ZRose,
                        fontSize = 11.sp, lineHeight = 17.sp,
                    )
                }
                Text(
                    "$freezes/$max",
                    color = if (freezes > 0) teal else ZTextMuted,
                    fontWeight = FontWeight.Black, fontSize = 17.sp,
                )
            }

            Spacer(Modifier.height(16.dp))

            // الخانات الثلاث
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                repeat(max) { i ->
                    val filled = i < freezes
                    Surface(
                        modifier = Modifier.weight(1f).height(58.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = if (filled) teal.copy(alpha = 0.10f + glow * 0.10f)
                        else ZSurfaceVariant.copy(alpha = 0.55f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (filled) teal.copy(alpha = 0.45f) else ZBorder.copy(alpha = 0.5f),
                        ),
                    ) {
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                if (filled) Icons.Filled.Shield else Icons.Filled.ShieldMoon,
                                null,
                                tint = if (filled) teal else ZTextMuted.copy(alpha = 0.5f),
                                modifier = Modifier.size(22.dp)
                                    .scale(if (filled) 0.95f + glow * 0.10f else 1f),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (filled) "جاهز" else "فارغ",
                                color = if (filled) teal else ZTextMuted,
                                fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            if (justUsed) {
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = ZAmber.copy(alpha = 0.13f)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Verified, null, tint = ZAmber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "استُهلك درع لإنقاذ شعلتك من يوم فائت — سلسلتك لا تزال قائمة!",
                            color = ZTextSecondary, fontSize = 11.sp, lineHeight = 17.sp,
                        )
                    }
                }
            }

            if (freezes < max) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "اكسب دروعاً إضافية بفتح الصناديق النادرة فأعلى (الحد الأقصى $max).",
                    color = ZTextMuted, fontSize = 10.sp, lineHeight = 16.sp,
                )
            }
        }
    }
}

/** احتفال استعادة الشعلة. */
@Composable
fun RescueSuccessDialog(restored: Int, onDismiss: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "win")
    val pulse by inf.animateFloat(
        initialValue = 1f, targetValue = 1.14f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "wp",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ZSurface,
        icon = {
            Box(
                Modifier.size(78.dp).scale(pulse).clip(RoundedCornerShape(40.dp))
                    .background(Brush.linearGradient(listOf(ZAmber, Color(0xFFDC2626)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.LocalFireDepartment, null, tint = Color.White, modifier = Modifier.size(44.dp))
            }
        },
        title = {
            Text(
                "أنقذت شعلتك من الموت!",
                color = ZTextPrimary, fontWeight = FontWeight.Black,
                fontSize = 20.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "أهلاً بعودتك يا بطل \uD83C\uDF89",
                    color = ZTextSecondary, fontSize = 15.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RescueStat("\uD83D\uDD25", "$restored", "يوم مستعاد")
                    RescueStat("\u2728", "+60", "XP")
                    RescueStat("\uD83D\uDEE1\uFE0F", "+1", "درع هدية")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = ZIndigo),
                shape = RoundedCornerShape(16.dp),
            ) { Text("واصل الرحلة", fontWeight = FontWeight.Black, color = Color.White) }
        },
    )
}

@Composable
private fun RescueStat(emoji: String, value: String, label: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = ZSurfaceVariant) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(emoji, fontSize = 17.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
            Text(label, color = ZTextMuted, fontSize = 9.sp)
        }
    }
}

/* ══════════════════════ 3 · مرآة الإدراك ══════════════════════ */

/** بطاقة البصمة المعرفية — الأنماط الأربعة + المؤشرات. */
@Composable
fun CognitiveMirrorCard(m: CognitiveMirror, onGenerate: (() -> Unit)? = null) {
    val tempoC = Color(m.tempo.colorArgb)
    val chronoC = Color(m.chrono.colorArgb)
    val sensoryC = Color(m.sensory.colorArgb)

    Surface(shape = RoundedCornerShape(24.dp), color = ZCard, shadowElevation = 5.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(ZPurple, ZIndigo))),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Psychology, null, tint = Color.White, modifier = Modifier.size(23.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("مرآة الإدراك", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Text(
                        "${m.confidenceLabel} · ${m.totalReviews} مراجعة",
                        color = ZTextSecondary, fontSize = 11.sp,
                    )
                }
            }

            if (!m.hasEnoughData) {
                Spacer(Modifier.height(16.dp))
                Surface(shape = RoundedCornerShape(16.dp), color = ZSurfaceVariant) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.HourglassEmpty, null, tint = ZTextMuted, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "راجع ${(12 - m.totalReviews).coerceAtLeast(1)} بطاقة أخرى لتتكوّن بصمتك المعرفية",
                            color = ZTextSecondary, fontSize = 12.sp, lineHeight = 19.sp,
                        )
                    }
                }
                return@Column
            }

            Spacer(Modifier.height(16.dp))

            // ── الأنماط الثلاثة ──
            ArchetypeRow(m.tempo.emoji, "الإيقاع", m.tempo.label, m.tempo.desc, tempoC)
            Spacer(Modifier.height(8.dp))
            ArchetypeRow(m.chrono.emoji, "التوقيت", m.chrono.label, m.chrono.desc, chronoC)
            Spacer(Modifier.height(8.dp))
            ArchetypeRow(m.sensory.emoji, "القناة الحسّية", m.sensory.label, m.sensory.desc, sensoryC)
            Spacer(Modifier.height(8.dp))
            ArchetypeRow(
                m.forgetPattern.emoji, "نمط النسيان",
                m.forgetPattern.label, m.forgetPattern.advice, ZRose,
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = ZBorder)
            Spacer(Modifier.height(16.dp))

            // ── المؤشرات الرقمية ──
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MirrorMetric(Modifier.weight(1f), "الزمن/بطاقة", if (m.avgSecondsPerCard > 0) String.format("%.1fث", m.avgSecondsPerCard) else "—", ZIndigo)
                MirrorMetric(Modifier.weight(1f), "ذروتك", m.peakHourLabel, ZAmber)
                MirrorMetric(Modifier.weight(1f), "التذكّر", "${(m.recallRate * 100).toInt()}%", ZEmerald)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MirrorMetric(Modifier.weight(1f), "عمق الترسيخ", "${(m.depthIndex * 100).toInt()}%", ZPurple)
                MirrorMetric(Modifier.weight(1f), "الانتظام", "${(m.consistencyIndex * 100).toInt()}%", ZCyanDeep)
                MirrorMetric(Modifier.weight(1f), "الإعادات", String.format("%.1f", m.avgReplays), ZTextSecondary)
            }

            // ── مدرّج مراحل التذكّر ──
            Spacer(Modifier.height(16.dp))
            Text("من أين تسترجع كلماتك؟", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            StageHistogram(m.stageHistogram)

            if (onGenerate != null) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onGenerate,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ZPurple),
                ) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("ولّد تحليلاً عميقاً بالذكاء الاصطناعي", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ArchetypeRow(emoji: String, kicker: String, title: String, desc: String, accent: Color) {
    Surface(shape = RoundedCornerShape(16.dp), color = accent.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp)) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Text(emoji, fontSize = 17.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(kicker, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Black)
                Text(title, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(desc, color = ZTextSecondary, fontSize = 11.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun MirrorMetric(modifier: Modifier, label: String, value: String, accent: Color) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = ZSurfaceVariant) {
        Column(Modifier.padding(vertical = 8.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = accent, fontWeight = FontWeight.Black, fontSize = 14.sp, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(label, color = ZTextMuted, fontSize = 9.sp, maxLines = 1)
        }
    }
}

/** مدرّج مراحل التذكّر الأربع. */
@Composable
private fun StageHistogram(hist: IntArray) {
    val total = hist.sum().coerceAtLeast(1)
    val labels = listOf("الصوت", "الصورة", "النص", "الكشف")
    val colors = listOf(Color(0xFF06B6D4), Color(0xFFA855F7), Color(0xFFF59E0B), Color(0xFFF43F5E))
    Column {
        // شريط مكدّس
        Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(8.dp))) {
            hist.forEachIndexed { i, v ->
                if (v > 0) {
                    Box(Modifier.weight(v.toFloat()).fillMaxHeight().background(colors[i]))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            hist.forEachIndexed { i, v ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(9.dp).clip(RoundedCornerShape(4.dp)).background(colors[i]))
                    Spacer(Modifier.height(4.dp))
                    Text("${(v * 100 / total)}%", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 11.sp)
                    Text(labels[i], color = ZTextMuted, fontSize = 9.sp)
                }
            }
        }
    }
}

/** عرض التقرير المولَّد (AI أو محلي). */
@Composable
fun MirrorReportView(report: MirrorReport, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("\uD83E\uDE9E", fontSize = 17.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                report.title.ifBlank { "مرآة الإدراك" },
                color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = if (report.local) ZTextMuted.copy(alpha = 0.14f) else ZPurple.copy(alpha = 0.16f),
            ) {
                Text(
                    if (report.local) "محلي" else "AI",
                    color = if (report.local) ZTextMuted else ZPurple,
                    fontSize = 9.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        MirrorSection(Icons.Filled.Fingerprint, "من أنت كمتعلّم", report.identity, ZIndigo)
        MirrorSection(Icons.Filled.Bolt, "قوّتك الخارقة", report.superpower, ZEmerald)
        MirrorSection(Icons.Filled.VisibilityOff, "نقطتك العمياء", report.blindSpot, ZRose)
        MirrorSection(Icons.Filled.Schedule, "طقسك الأمثل", report.ritual, ZAmber)
        MirrorSection(Icons.Filled.AutoAwesome, "النبوءة", report.prophecy, ZPurple)
    }
}

@Composable
private fun MirrorSection(icon: ImageVector, title: String, body: String, accent: Color) {
    if (body.isBlank()) return
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, color = accent, fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            body.replace("**", ""),
            color = ZTextSecondary, fontSize = 13.sp, lineHeight = 22.sp,
        )
    }
}
