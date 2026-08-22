package com.zmastery.english.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import kotlinx.coroutines.delay

/**
 * The mission screen — shown once, before anything else.
 *
 * This is where the app states what it actually is: not a course, but the
 * *system* that makes studying a course survivable. The core claim —
 * "ساعة منتظمة يومياً تتغلب على حماس مؤقت" — is the product thesis, so it gets
 * its own page rather than being buried in an About dialog.
 */
private data class MissionPage(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val kicker: String,
    val title: String,
    val body: String,
    val colors: List<Color>,
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pages = remember {
        listOf(
            MissionPage(
                Icons.Filled.Insights,
                "رسالتنا",
                "لا تعلّم بدون استمرارية",
                "ساعة منتظمة كل يوم تتغلّب على حماسٍ مؤقت يزول بعد أسبوع.\n\n" +
                    "هذا التطبيق لا يعلّمك مكانَ منهجك — بل يجعلك تصمد معه حتى النهاية.",
                listOf(ZIndigo, ZPurple),
            ),
            MissionPage(
                Icons.Filled.Hub,
                "منصّة واحدة",
                "كل كورساتك في مكان واحد",
                "المستويات، الكورسات، الكلمات، القواعد، القراءة والصوتيات — " +
                    "كلها مدمجة في منصّة واحدة.\n\n" +
                    "ولكل كورس طريقة دراسة ومراجعة خاصة به تناسب طبيعته.",
                listOf(ZCyanDeep, ZCyan),
            ),
            MissionPage(
                Icons.Filled.NotificationsActive,
                "نظام الالتزام",
                "التذكيرات والحماسة والمهام",
                "تذكير يومي في وقتك الثابت، مهام واضحة، سلسلة أيام لا تريد كسرها، " +
                    "وتنبيه لطيف حين تتعثّر.\n\n" +
                    "الالتزام ليس شعوراً — إنه نظام.",
                listOf(ZAmber, ZRose),
            ),
            MissionPage(
                Icons.Filled.Psychology,
                "المراجعة الذكية",
                "تراجع قبل أن تنسى — لا بعدها",
                "محرّك FSRS-5 يحسب لحظة اقتراب النسيان لكل كلمة، فتراجعها في " +
                    "التوقيت الأمثل.\n\n" +
                    "الصوت، الصورة الذهنية، والسياق — أربع مراحل تثبّت الكلمة فعلياً.",
                listOf(ZEmerald, ZCyanDeep),
            ),
        )
    }

    var page by remember { mutableStateOf(0) }
    val current = pages[page]
    val last = page == pages.lastIndex

    Box(Modifier.fillMaxSize().background(ZBackground)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(20.dp))

            // Skip
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(!last, enter = fadeIn(), exit = fadeOut()) {
                    TextButton(onClick = onFinish) {
                        Text("تخطّي", color = ZTextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("Z-Mastery", color = ZTextMuted, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }

            Spacer(Modifier.height(24.dp))

            // Animated icon medallion
            PulseMedallion(current.icon, current.colors)

            Spacer(Modifier.height(30.dp))

            androidx.compose.animation.AnimatedContent(
                targetState = page,
                transitionSpec = {
                    (fadeIn(tween(360)) + slideInVertically(tween(360)) { it / 8 })
                        .togetherWith(fadeOut(tween(160)))
                },
                label = "page",
            ) { p ->
                val pg = pages[p]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = RoundedCornerShape(50), color = pg.colors.first().copy(alpha = 0.14f)) {
                        Text(
                            pg.kicker, color = pg.colors.first(), fontSize = 12.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        pg.title, color = ZTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center, lineHeight = 36.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        pg.body, color = ZTextSecondary, fontSize = 15.sp,
                        textAlign = TextAlign.Center, lineHeight = 27.sp,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // The thesis, restated as a pledge on the last page
            AnimatedVisibility(last, enter = fadeIn(tween(500))) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ZCard,
                    shadowElevation = 5.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.FormatQuote, null, tint = ZIndigo, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("تعهّدك", color = ZIndigo, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "\"سأدرس كل يوم — ولو قليلاً. الانتظام أهم من الكمّية، " +
                                "والاستمرار أهم من الحماس.\"",
                            color = ZTextPrimary, fontSize = 15.sp, lineHeight = 26.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        // ---- Bottom controls ----
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, ZBackground, ZBackground)))
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Dots
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                pages.indices.forEach { i ->
                    val active = i == page
                    val w by animateFloatAsState(if (active) 26f else 8f, tween(280), label = "dot")
                    Box(
                        Modifier.width(w.dp).height(8.dp).clip(RoundedCornerShape(4.dp))
                            .background(if (active) ZIndigo else ZBorder)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { if (last) onFinish() else page++ },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ZIndigo),
            ) {
                Text(
                    if (last) "ابدأ الالتزام" else "التالي",
                    fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color.White,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    if (last) Icons.Filled.RocketLaunch else Icons.Filled.NavigateBefore,
                    null, tint = Color.White,
                )
            }
            if (page > 0) {
                TextButton(onClick = { page-- }) {
                    Text("السابق", color = ZTextMuted, fontSize = 13.sp)
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** Softly breathing gradient medallion behind the page icon. */
@Composable
private fun PulseMedallion(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    colors: List<Color>,
) {
    val transition = rememberInfiniteTransition(label = "medallion")
    val pulse by transition.animateFloat(
        initialValue = 1f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "p",
    )
    Box(contentAlignment = Alignment.Center) {
        // Halo
        Box(
            Modifier.size(150.dp).scale(pulse).clip(RoundedCornerShape(75.dp))
                .background(Brush.radialGradient(listOf(colors.first().copy(alpha = 0.20f), Color.Transparent)))
        )
        Box(
            Modifier.size(104.dp).clip(RoundedCornerShape(34.dp))
                .background(Brush.linearGradient(colors)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(52.dp)) }
    }
}

/**
 * Brief branded splash shown while the persisted state loads, carrying the same
 * message so the very first frame already says what the app is for.
 */
@Composable
fun SplashScreen(onTimeout: () -> Unit = {}) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
        delay(1200)
        onTimeout()
    }
    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(ZIndigo, ZPurple))),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(visible, enter = fadeIn(tween(600))) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(96.dp).clip(RoundedCornerShape(30.dp))
                        .background(Color.White.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.School, null, tint = Color.White, modifier = Modifier.size(52.dp)) }
                Spacer(Modifier.height(20.dp))
                Text("Z-Mastery", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                Text(
                    "لا تعلّم بدون استمرارية",
                    color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(26.dp))
                CircularProgressIndicator(
                    color = Color.White.copy(alpha = 0.85f),
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}
