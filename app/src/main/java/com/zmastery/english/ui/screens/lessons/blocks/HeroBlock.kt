package com.zmastery.english.ui.screens.lessons.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.data.Lesson
import com.zmastery.english.data.LessonStyle

// ==========================================================================
// Unified hero — ONE card for every course.
// Data-driven: the badge follows the course style, the stat pills only show
// counts that actually exist in this lesson.
// ==========================================================================

@Composable
internal fun LessonHeroBlock(
    lesson: Lesson,
    accent: Color,
    style: LessonStyle?,
    wordCount: Int,
    segmentCount: Int,
    soundCount: Int,
) {
    val (badge, badgeIcon) = styleBadgeOf(style)
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.72f))))
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.10f), radius = size.minDimension * 0.5f, center = Offset(size.width * 0.88f, size.height * 0.1f))
                drawCircle(Color.White.copy(alpha = 0.07f), radius = size.minDimension * 0.32f, center = Offset(size.width * 0.1f, size.height))
            }
            .padding(22.dp),
    ) {
        Column {
            Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.2f)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(badgeIcon, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("$badge · الدرس ${lesson.no}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(lesson.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, lineHeight = 30.sp)
            if (lesson.summaryAr.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(lesson.summaryAr, color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, lineHeight = 20.sp)
            }
            val stats = buildList {
                if (wordCount > 0) add(Icons.Filled.Style to "$wordCount كلمة")
                if (lesson.examples.isNotEmpty()) add(Icons.Filled.FormatListBulleted to "${lesson.examples.size} مثال")
                if (lesson.dialogues.isNotEmpty()) add(Icons.Filled.ChatBubble to "${lesson.dialogues.size} سطر")
                if (lesson.keyExpressions.isNotEmpty()) add(Icons.Filled.Bookmarks to "${lesson.keyExpressions.size} تعبير")
                if (segmentCount > 0) add(Icons.Filled.Segment to "$segmentCount مقطع")
                if (soundCount > 0) add(Icons.Filled.GraphicEq to "$soundCount صوت")
                if (lesson.quiz.isNotEmpty()) add(Icons.Filled.Quiz to "${lesson.quiz.size} سؤال")
            }.take(4)
            if (stats.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    stats.forEach { (icon, label) -> HeroPill(icon, label) }
                }
            }
        }
    }
}

@Composable
private fun HeroPill(icon: ImageVector, label: String) {
    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.18f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** شارة النمط — هوية بصرية فقط، لا تحكم أي منطق. */
internal fun styleBadgeOf(style: LessonStyle?): Pair<String, ImageVector> = when (style) {
    LessonStyle.GRAMMAR_RULES -> "قاعدة" to Icons.Filled.Rule
    LessonStyle.EXAM_PREP -> "امتحان" to Icons.Filled.Quiz
    LessonStyle.CONVERSATION -> "محادثة" to Icons.Filled.Forum
    LessonStyle.CULTURE -> "ثقافة" to Icons.Filled.Public
    LessonStyle.STORY -> "قصة" to Icons.Filled.AutoStories
    LessonStyle.NEWS -> "خبر" to Icons.Filled.Newspaper
    LessonStyle.THINKING -> "تفكير" to Icons.Filled.Psychology
    LessonStyle.LISTENING_AUDIO -> "استماع" to Icons.Filled.Headphones
    LessonStyle.COMEDY -> "كوميدي" to Icons.Filled.EmojiEmotions
    LessonStyle.PHONETICS_SOUNDS -> "صوتيات" to Icons.Filled.GraphicEq
    LessonStyle.WRITING_PRACTICE -> "كتابة" to Icons.Filled.Edit
    LessonStyle.IDIOMS -> "تعابير" to Icons.Filled.FormatQuote
    LessonStyle.VOCAB_CARDS, null -> "مفردات" to Icons.Filled.Style
    else -> "قراءة" to Icons.Filled.MenuBook
}
