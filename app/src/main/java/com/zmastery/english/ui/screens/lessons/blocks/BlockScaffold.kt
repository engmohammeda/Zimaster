package com.zmastery.english.ui.screens.lessons.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.ui.components.SoftCard
import com.zmastery.english.ui.theme.*

// ==========================================================================
// Scaffold shared by the universal lesson blocks:
// section headers, numbered notes card, key-points glossary, quiz launcher,
// mental-link card and the complete button — one polished design for every course.
// ==========================================================================

/** العنوان الموحّد لأقسام الدرس. */
@Composable
internal fun SectionHeader(icon: ImageVector, title: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(10.dp))
        Text(title, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

/**
 * الرأس الموحّد المرقّم لبلوكات الدرس — مطابق لنظام التصميم المعتمد:
 * شارة دائرية برقم البلوك + عنوان عريض + زر صوت اختياري + فاصل أسفله.
 * يمنح المتعلّم إحساساً بالتقدّم والتوجّه داخل الدرس.
 *
 * @param number ترتيب البلوك (1-based).
 * @param onPlay عند توفّره يُظهر زر تشغيل الصوت بجانب العنوان.
 */
@Composable
internal fun BlockHeader(
    number: Int,
    title: String,
    accent: Color,
    onPlay: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("$number", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                color = ZTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
            if (onPlay != null) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(50))
                        .background(accent.copy(alpha = 0.10f))
                        .clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.VolumeUp, null, tint = accent, modifier = Modifier.size(18.dp))
                }
            }
        }
        Divider(color = ZBorder, thickness = 1.dp)
    }
}

/** ملاحظات الدرس — ترقيم كهرماني موحّد. */
internal fun LazyListScope.notesBlock(notes: List<String>, title: String = "ملاحظات الدرس") {
    item { SectionHeader(Icons.Filled.Lightbulb, title, ZAmber) }
    item {
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                notes.forEachIndexed { i, note ->
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(ZAmber.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) { Text("${i + 1}", color = ZAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(10.dp))
                        Text(note, color = ZTextPrimary, fontSize = 14.sp, lineHeight = 22.sp)
                    }
                }
            }
        }
    }
}

/** مسرد مصغّر «كلمة = معنى» — بديل خفيف عند غياب بطاقات المفردات. */
internal fun LazyListScope.keyPointsBlock(points: List<String>, accent: Color) {
    item { SectionHeader(Icons.Filled.Lightbulb, "النقاط الرئيسية", accent) }
    item {
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                points.forEach { point ->
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(ZAmber))
                        Spacer(Modifier.width(10.dp))
                        Text(point, color = ZTextPrimary, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/** بطاقة تشغيل اختبار الدرس — موحّدة لكل الكورسات. */
@Composable
internal fun QuizLauncherCard(count: Int, accent: Color, onOpenQuiz: () -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenQuiz) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(ZCyanDeep, ZCyan))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Quiz, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("اختبار الدرس", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("$count سؤال · اضغط للبدء", color = ZTextSecondary, fontSize = 12.sp)
            }
            Icon(Icons.Filled.ChevronLeft, null, tint = accent)
        }
    }
}

/** بطاقة توليد الرابط الذهني — موحّدة. */
@Composable
internal fun MentalLinkCard(onGenerateMentalLink: () -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth(), onClick = onGenerateMentalLink) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(ZPurple, ZIndigo))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.AutoAwesome, null, tint = Color.White) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("توليد رابط ذهني", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("انسخ مطالبة الذكاء الاصطناعي لتوليد الصور", color = ZTextSecondary, fontSize = 12.sp)
            }
            Icon(Icons.Filled.ContentCopy, null, tint = ZCyan)
        }
    }
}

/** زر إكمال الدرس — موحّد. */
@Composable
internal fun CompleteLessonButton(isCompleted: Boolean, accent: Color, onComplete: () -> Unit) {
    Button(
        onClick = onComplete,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (isCompleted) ZEmerald else accent),
    ) {
        Icon(if (isCompleted) Icons.Filled.CheckCircle else Icons.Filled.Check, null, tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text(
            if (isCompleted) "مكتمل ✓ (اضغط للتراجع)" else "إكمال الدرس",
            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp,
        )
    }
}
