package com.zmastery.english.ui.screens.lessons.blocks

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.zmastery.english.data.RevealMode
import com.zmastery.english.data.VocabWord
import com.zmastery.english.ui.components.SoftCard
import com.zmastery.english.ui.theme.*

// ==========================================================================
// Vocabulary block — the polished flip/reveal word cards, now shared by
// EVERY lesson that carries words (vocab, grammar, conversation, phonetics…).
// ==========================================================================

internal fun LazyListScope.vocabBlock(
    words: List<VocabWord>,
    accent: Color,
    revealMode: RevealMode,
    onRevealMode: (RevealMode) -> Unit,
) {
    item { RevealModeToggle(revealMode, accent, onRevealMode) }
    item { SectionHeader(Icons.Filled.Style, "المفردات (${words.size})", accent) }
    items(words.size, key = { "wb_${words[it].id}" }) { idx ->
        WordRevealCard(words[idx], accent, revealMode)
    }
}

/** Toggle at the top of the lesson to switch reveal behaviour. */
@Composable
private fun RevealModeToggle(mode: RevealMode, accent: Color, onChange: (RevealMode) -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth(), radius = 16.dp) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Visibility, null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("العرض:", color = ZTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).background(ZSurfaceVariant).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                RevealSeg("كامل", mode == RevealMode.FULL, accent) { onChange(RevealMode.FULL) }
                RevealSeg("الكلمة فقط", mode == RevealMode.WORD_ONLY, accent) { onChange(RevealMode.WORD_ONLY) }
            }
        }
    }
}

@Composable
private fun RevealSeg(label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (active) accent else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (active) Color.White else ZTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Vocab card — word + translation + example sentence (EN+AR) together,
 * with pronunciation audio. In WORD_ONLY mode the details hide until tapped.
 */
@Composable
private fun WordRevealCard(word: VocabWord, accent: Color, mode: RevealMode) {
    var revealed by remember(mode, word.id) { mutableStateOf(mode == RevealMode.FULL) }
    val clickable = mode == RevealMode.WORD_ONLY

    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (clickable) ({ revealed = !revealed }) else null,
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.zmastery.english.audio.AudioButton(
                    text = if (word.exampleEn.isNotBlank()) "${word.english}. ${word.exampleEn}" else word.english,
                    audioKey = "wb_${word.id}", accent = accent, size = 40.dp, iconSize = 20.dp,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(word.english, color = ZTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    if (word.phonetic.isNotBlank()) Text(word.phonetic, color = accent, fontSize = 12.sp)
                }
                if (revealed) {
                    Text(word.arabic, color = ZAmber, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }

            AnimatedContent(
                targetState = revealed,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                label = "reveal",
            ) { show ->
                if (show && word.exampleEn.isNotBlank()) {
                    Column {
                        Spacer(Modifier.height(14.dp))
                        Divider(color = accent.copy(alpha = 0.35f))
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Text("💡", fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(word.exampleEn, color = ZTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
                                Spacer(Modifier.height(6.dp))
                                Text(word.exampleAr, color = ZTextSecondary, fontSize = 14.sp, lineHeight = 22.sp)
                            }
                        }
                        if (word.mentalImage.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Image, null, tint = accent, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(word.mentalImage, color = ZTextMuted, fontSize = 12.sp)
                            }
                        }
                    }
                } else if (!show) {
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.TouchApp, null, tint = ZTextMuted, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("اضغط لكشف المعنى والجملة", color = ZTextMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
