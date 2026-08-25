package com.zmastery.english.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.ui.theme.*

/**
 * شريط الحماسة العلوي — نسخة مُعاد تصميمها: أنيقة، مضغوطة، واحترافية.
 *
 * التصميم السابق كان صفاً كاملاً بعرض الشاشة + شريط تقدّم أسفله، مما جعله
 * يبدو "عريضاً" وثقيلاً بصرياً رغم أنه أعلى شاشة تُستخدم يومياً. هذه النسخة:
 *
 *  • كبسولة واحدة مدمجة (pill) على اليمين تجمع 🔥 + ⚡ + 💎 في تصميم واحد
 *    متدرّج بدل ثلاث كتل منفصلة تتمدّد بعرض الشاشة.
 *  • 🛡️ الدروع تظهر فقط عند امتلاك درع واحد على الأقل (لا تشغل مساحة فارغة).
 *  • شريط "شرط اليوم" أصبح خيطاً رفيعاً جداً (3dp) ملاصقاً لأسفل الكبسولة
 *    بدل شريط كامل العرض منفصل — إشارة بصرية دون ثِقَل.
 *  • كل الكبسولة داخل Surface بارتفاع ثابت (56dp) وحواف دائرية كاملة، تبدو
 *    كعنصر تنقّل عائم أنيق بدل شريط تطبيق تقليدي.
 */
@Composable
fun StreakTopBar(
    streak: Int,
    xp: Int,
    shields: Int,
    cefr: String,
    conditionProgress: Float,
    conditionLabel: String,
    dayEarned: Boolean,
    onOpen: () -> Unit,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = conditionProgress.coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "cond",
    )

    val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "flame")
    val flamePulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (streak > 0 && !dayEarned) 1.15f else 1f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "fp",
    )

    Surface(
        color = ZSurface.copy(alpha = 0.95f),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                // ── Status Indicator & Label ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpen),
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (dayEarned) ZEmerald else ZAmber),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (dayEarned) "يومك مؤمَّن ✓" else conditionLabel,
                        color = if (dayEarned) ZEmerald else ZTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }

                Spacer(Modifier.width(8.dp))

                // ── Compact Action Capsule ──
                Surface(
                    shape = RoundedCornerShape(50),
                    color = ZSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ZBorder),
                    onClick = onOpen,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        // Flame & Streak
                        Text(
                            "🔥",
                            fontSize = 14.sp,
                            modifier = Modifier
                                .alpha(if (streak == 0) 0.35f else 1f)
                                .scale(flamePulse),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "$streak",
                            color = if (streak > 0) ZAmber else ZTextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                        )

                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.width(1.dp).height(12.dp).background(ZBorder))
                        Spacer(Modifier.width(8.dp))

                        // XP Bolt
                        Icon(Icons.Filled.Bolt, null, tint = ZIndigo, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            compact(xp),
                            color = ZIndigo,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                        )

                        if (shields > 0) {
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.width(1.dp).height(12.dp).background(ZBorder))
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Filled.Shield, null, tint = ZCyanDeep, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "$shields",
                                color = ZCyanDeep,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }

                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    androidx.compose.ui.graphics.Brush.linearGradient(listOf(ZIndigo, ZPurple))
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                cefr,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                }
            }

            // ── Thin 2dp Progress Line anchored directly at bottom ──
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(ZBorder.copy(alpha = 0.5f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .background(if (dayEarned) ZEmerald else ZAmber),
                )
            }
        }
    }
}

/** 1250 → "1.2k" — يمنع تمدد الكبسولة مع نمو الخبرة. */
private fun compact(n: Int): String = when {
    n < 1000 -> "$n"
    n < 10_000 -> String.format("%.1fk", n / 1000.0)
    else -> "${n / 1000}k"
}
