package com.zmastery.english.ui.screens.lessons.blocks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.audio.AudioButton
import com.zmastery.english.data.Sentence
import com.zmastery.english.ui.components.SoftCard
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel

// ==========================================================================
// Reading block — bilingual passage with two modes:
// segmented (interactive, tap-to-reveal per sentence) or continuous full text.
// Used by reading / listening / story / news / culture / thinking / comedy.
// ==========================================================================

internal fun LazyListScope.readingBlock(
    segments: List<Sentence>,
    fullEn: String,
    fullAr: String,
    accent: Color,
    mode: Int,
    onMode: (Int) -> Unit,
    showAllAr: Boolean,
    onToggleAllAr: () -> Unit,
    vm: AppViewModel,
    lessonId: Int,
    audioReady: Boolean,
) {
    // Controls
    item {
        ReadingControls(
            accent = accent,
            mode = mode,
            onMode = onMode,
            showAllAr = showAllAr,
            onToggleAr = onToggleAllAr,
            hasSegments = segments.isNotEmpty(),
        )
    }

    // Passage
    if (mode == 0 && segments.isNotEmpty()) {
        item { SectionHeader(Icons.Filled.MenuBook, "النص التفاعلي", accent) }
        items(segments.size, key = { "segb_$it" }) { i ->
            SegmentRow(segments[i], i + 1, accent, showAllAr)
        }
    } else if (fullEn.isNotBlank()) {
        item { SectionHeader(Icons.Filled.Article, "النص الكامل", accent) }
        item { FullTextCard(fullEn, fullAr, accent, showAllAr, vm, lessonId, audioReady) }
    }
}

@Composable
private fun ReadingControls(
    accent: Color,
    mode: Int,
    onMode: (Int) -> Unit,
    showAllAr: Boolean,
    onToggleAr: () -> Unit,
    hasSegments: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (hasSegments) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ZSurfaceVariant,
                modifier = Modifier.weight(1f),
            ) {
                Row(Modifier.padding(4.dp)) {
                    ModeTab("مقاطع", mode == 0, accent, Modifier.weight(1f)) { onMode(0) }
                    ModeTab("نص كامل", mode == 1, accent, Modifier.weight(1f)) { onMode(1) }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (showAllAr) accent else ZSurfaceVariant,
            onClick = onToggleAr,
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Translate, null, tint = if (showAllAr) Color.White else accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (showAllAr) "إخفاء" else "الترجمة", color = if (showAllAr) Color.White else ZTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) accent else Color.Transparent, tween(200), label = "tab")
    Surface(shape = RoundedCornerShape(11.dp), color = bg, modifier = modifier, onClick = onClick) {
        Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) Color.White else ZTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SegmentRow(seg: Sentence, index: Int, accent: Color, forceAr: Boolean) {
    var revealed by remember { mutableStateOf(false) }
    val show = revealed || forceAr
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(26.dp).clip(RoundedCornerShape(9.dp)).background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Text("$index", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(seg.en, color = ZTextPrimary, fontSize = 16.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
                AnimatedVisibility(show) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        Text(seg.ar, color = accent, fontSize = 14.sp, lineHeight = 23.sp)
                    }
                }
                if (!forceAr && seg.ar.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (revealed) "إخفاء الترجمة" else "اضغط لإظهار الترجمة",
                        color = ZTextMuted, fontSize = 11.sp,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AudioButton(text = seg.en, audioKey = "seg_${index}_${seg.en.hashCode()}", accent = accent, size = 38.dp, iconSize = 18.dp)
                if (seg.ar.isNotBlank() && !forceAr) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = accent.copy(alpha = 0.10f),
                        modifier = Modifier.size(38.dp),
                        onClick = { revealed = !revealed },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, tint = accent, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullTextCard(en: String, ar: String, accent: Color, showAr: Boolean, vm: AppViewModel, lessonId: Int, audioReady: Boolean) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AudioButton(text = en, audioKey = "fulltext_${en.hashCode()}", accent = accent, size = 44.dp, iconSize = 22.dp)
                Spacer(Modifier.width(12.dp))
                Text("استمع للنص كاملاً", color = ZTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                // AI narration (Gemini), permanently cached — more natural than
                // the local device TTS used by the button on the left.
                if (audioReady) {
                    Surface(shape = RoundedCornerShape(8.dp), color = ZEmerald.copy(alpha = 0.15f)) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = ZEmerald, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("صوت AI جاهز", color = ZEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    TextButton(onClick = { vm.generateLessonAudio(lessonId) }, enabled = !vm.isGeneratingAudio) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = ZPurple, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("صوت طبيعي AI", color = ZPurple, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(en, color = ZTextPrimary, fontSize = 17.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium)
            AnimatedVisibility(showAr && ar.isNotBlank()) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    Divider(color = ZBorder)
                    Spacer(Modifier.height(14.dp))
                    Text(ar, color = accent, fontSize = 15.sp, lineHeight = 28.sp)
                }
            }
        }
    }
}

/** Fallback: split a full passage into sentence segments, pairing EN/AR by order. */
internal fun splitToSentences(en: String, ar: String): List<Sentence> {
    val enParts = en.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotBlank() }
    val arParts = ar.split(Regex("(?<=[.!؟])\\s+")).map { it.trim() }.filter { it.isNotBlank() }
    return enParts.mapIndexed { i, e -> Sentence(e, arParts.getOrElse(i) { "" }) }
}
