package com.zmastery.english.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel

/**
 * المراجعة الموحّدة — دمج «مراجعة الكلمات» و«مراجعة الدروس» في زر المراجعة
 * الأصلي بالشريط السفلي، بمبدّل مقسّم أنيق في الأعلى بدل زرّين منفصلين.
 */
@Composable
fun ReviewHubScreen(vm: AppViewModel) {
    // 0 = مراجعة الكلمات (FSRS المتدرجة) · 1 = مراجعة الدروس (تقييم ذاتي)
    var mode by rememberSaveable { mutableStateOf(0) }
    val dueWords = vm.dueWords.size
    val dueLessons = vm.lessonsToReview.size

    Column(Modifier.fillMaxSize()) {
        ReviewModeSwitcher(
            mode = mode,
            dueWords = dueWords,
            dueLessons = dueLessons,
            onModeChange = { mode = it },
        )
        Box(Modifier.weight(1f)) {
            when (mode) {
                0 -> ReviewScreen(vm)
                else -> LessonReviewScreen(vm)
            }
        }
    }
}

/** مبدّل مقسّم: كلمتان فقط بين نوعَي المراجعة، بشارات أعداد المستحق. */
@Composable
private fun ReviewModeSwitcher(
    mode: Int,
    dueWords: Int,
    dueLessons: Int,
    onModeChange: (Int) -> Unit,
) {
    Column {
        Spacer(Modifier.height(4.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ZSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ReviewModeTab(
                    selected = mode == 0,
                    icon = Icons.Filled.Psychology,
                    title = "مراجعة الكلمات",
                    badge = dueWords,
                    onClick = { onModeChange(0) },
                    modifier = Modifier.weight(1f),
                )
                ReviewModeTab(
                    selected = mode == 1,
                    icon = Icons.Filled.Autorenew,
                    title = "مراجعة الدروس",
                    badge = dueLessons,
                    onClick = { onModeChange(1) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ReviewModeTab(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    badge: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(11.dp),
        color = if (selected) ZIndigo else androidx.compose.ui.graphics.Color.Transparent,
        onClick = onClick,
    ) {
        Row(
            Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, tint = if (selected) androidx.compose.ui.graphics.Color.White else ZTextSecondary, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                title,
                color = if (selected) androidx.compose.ui.graphics.Color.White else ZTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (badge > 0) {
                Spacer(Modifier.width(6.dp))
                Surface(shape = RoundedCornerShape(50), color = if (selected) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.25f) else ZRose.copy(alpha = 0.15f)) {
                    Text(
                        "$badge",
                        color = if (selected) androidx.compose.ui.graphics.Color.White else ZRose,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}
