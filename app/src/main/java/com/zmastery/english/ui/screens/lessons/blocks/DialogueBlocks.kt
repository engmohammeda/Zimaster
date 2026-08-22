package com.zmastery.english.ui.screens.lessons.blocks

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.data.Dialogue
import com.zmastery.english.data.KeyExpression
import com.zmastery.english.ui.components.SoftCard
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel

// ==========================================================================
// Dialogue blocks — chat bubbles with per-line audio + play-all, and the
// key-expressions cards used by conversation-style lessons.
// ==========================================================================

internal fun LazyListScope.dialogueBlock(
    dialogues: List<Dialogue>,
    accent: Color,
    showTranslations: Boolean,
    onToggleTranslations: () -> Unit,
    vm: AppViewModel,
) {
    val firstSpeaker = dialogues.firstOrNull()?.speaker
    item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(Icons.Filled.Forum, "الحوار", accent)
            Spacer(Modifier.weight(1f))
            TranslationToggle(showTranslations, onToggleTranslations)
        }
    }
    item { PlayAllDialogueCard(dialogues, accent, vm) }
    items(dialogues.size, key = { "dlgb_$it" }) { i ->
        val d = dialogues[i]
        ChatBubble(d, d.speaker == firstSpeaker, accent, showTranslations, vm)
    }
}

@Composable
private fun TranslationToggle(showing: Boolean, onToggle: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (showing) ZCyanDeep.copy(alpha = 0.12f) else ZSurfaceVariant,
        onClick = onToggle,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (showing) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, null, tint = ZCyanDeep, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text(if (showing) "الترجمة ظاهرة" else "الترجمة مخفية", color = ZCyanDeep, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Play the whole dialogue as one narration. */
@Composable
private fun PlayAllDialogueCard(dialogue: List<Dialogue>, accent: Color, vm: AppViewModel) {
    val fullText = dialogue.joinToString(". ") { it.en }
    SoftCard(modifier = Modifier.fillMaxWidth(), radius = 16.dp) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            com.zmastery.english.audio.AudioButton(
                text = fullText, audioKey = "dlg_all_${fullText.hashCode()}", accent = accent, size = 44.dp, iconSize = 22.dp,
                // Listening to the whole dialogue counts as every turn.
                onPlayed = { vm.trackConversationTurn(dialogue.size) },
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("استمع للحوار كاملاً", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("تشغيل متتابع لكل الأسطر", color = ZTextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ChatBubble(d: Dialogue, isRight: Boolean, accent: Color, showTranslation: Boolean, vm: AppViewModel) {
    val bubbleColor = if (isRight) accent else ZSurfaceVariant
    val textColor = if (isRight) Color.White else ZTextPrimary
    val subColor = if (isRight) Color.White.copy(alpha = 0.85f) else ZTextSecondary

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isRight) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier.fillMaxWidth(0.86f),
            horizontalAlignment = if (isRight) Alignment.End else Alignment.Start,
        ) {
            Text(
                d.speaker,
                color = if (isRight) accent else ZTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp, topEnd = 18.dp,
                    bottomStart = if (isRight) 18.dp else 4.dp,
                    bottomEnd = if (isRight) 4.dp else 18.dp,
                ),
                color = bubbleColor,
                shadowElevation = 3.dp,
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!isRight) {
                        com.zmastery.english.audio.AudioButton(
                            text = d.en, audioKey = "dlg_${d.hashCode()}", accent = accent, size = 34.dp, iconSize = 17.dp,
                            onPlayed = { vm.trackConversationTurn() },
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(d.en, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp)
                        AnimatedVisibility(showTranslation && d.ar.isNotBlank()) {
                            Text(d.ar, color = subColor, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                    if (isRight) {
                        Spacer(Modifier.width(10.dp))
                        com.zmastery.english.audio.AudioButton(
                            text = d.en, audioKey = "dlg_${d.hashCode()}", accent = Color.White.copy(alpha = 0.25f), size = 34.dp, iconSize = 17.dp,
                            onPlayed = { vm.trackConversationTurn() },
                        )
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------------
// Key expressions
// --------------------------------------------------------------------------

internal fun LazyListScope.expressionsBlock(expressions: List<KeyExpression>, accent: Color) {
    item { SectionHeader(Icons.Filled.Bookmarks, "تعبيرات مهمة (${expressions.size})", ZAmber) }
    items(expressions.size, key = { "expb_$it" }) { i ->
        ExpressionRow(expressions[i], accent)
    }
}

@Composable
private fun ExpressionRow(exp: KeyExpression, accent: Color) {
    SoftCard(modifier = Modifier.fillMaxWidth(), radius = 16.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.zmastery.english.audio.AudioButton(
                    text = exp.expressionEn, audioKey = "exp_${exp.expressionEn.hashCode()}", accent = ZAmber, size = 38.dp, iconSize = 18.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(exp.expressionEn, color = ZTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Text(exp.expressionAr, color = ZAmber, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (exp.usageAr.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ZAmber.copy(alpha = 0.08f)).padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Info, null, tint = ZAmber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(exp.usageAr, color = ZTextSecondary, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                }
            }
        }
    }
}
