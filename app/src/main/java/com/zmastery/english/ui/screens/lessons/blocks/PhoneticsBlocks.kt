package com.zmastery.english.ui.screens.lessons.blocks

import androidx.compose.foundation.background
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
import com.zmastery.english.data.PhContent
import com.zmastery.english.ui.components.SoftCard
import com.zmastery.english.ui.theme.*

// ==========================================================================
// Phonetics block — focus sounds · minimal pairs · shadowing practice.
// Fed from the lesson's verbatim rawJson via PhoneticsParser, so nothing the
// author uploaded is ever dropped. Rendered for ANY lesson that carries
// phonetics content, whatever its course style.
// ==========================================================================

internal fun LazyListScope.phoneticsBlock(content: PhContent) {
    if (content.focusSounds.isNotEmpty()) {
        item { FocusSoundsCard(content) }
    }
    if (content.minimalPairs.isNotEmpty()) {
        item { MinimalPairsCard(content) }
    }
    if (content.practiceScripts.isNotEmpty()) {
        item { PracticeScriptsCard(content.practiceScripts) }
    }
}

@Composable
private fun PhCard(title: String, icon: ImageVector, accent: Color, content: @Composable ColumnScope.() -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = accent, modifier = Modifier.size(19.dp)) }
                Spacer(Modifier.width(10.dp))
                Text(title, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun FocusSoundsCard(content: PhContent) {
    PhCard("الأصوات المستهدفة", Icons.Filled.RecordVoiceOver, ZAmber) {
        content.focusSounds.forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Box(
                    Modifier.size(62.dp).clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(ZAmber, Color(0xFFD98324)))),
                    contentAlignment = Alignment.Center,
                ) { Text(s.symbol, color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.description, color = ZTextSecondary, fontSize = 13.sp, lineHeight = 22.sp)
                }
            }
            if (i < content.focusSounds.size - 1) {
                Divider(color = ZBorder, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun MinimalPairsCard(content: PhContent) {
    PhCard("الأزواج الصغرى (Minimal Pairs)", Icons.Filled.CompareArrows, ZCyan) {
        Text("قارن النطق بين كل كلمتين — الفرق في صوت واحد فقط", color = ZTextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        content.minimalPairs.forEach { p ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PairChip(p.word1, Modifier.weight(1f))
                Icon(Icons.Filled.SwapHoriz, null, tint = ZTextMuted, modifier = Modifier.size(18.dp))
                PairChip(p.word2, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PairChip(word: String, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(12.dp), color = ZSurfaceVariant, modifier = modifier) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(word, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.VolumeUp, null, tint = ZCyanDeep, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun PracticeScriptsCard(scripts: List<String>) {
    PhCard("جُمل التدريب (Shadowing)", Icons.Filled.Repeat, ZEmerald) {
        scripts.forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(26.dp).clip(RoundedCornerShape(9.dp)).background(ZEmerald.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) { Text("${i + 1}", color = ZEmerald, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                Spacer(Modifier.width(12.dp))
                Text(s, color = ZTextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.VolumeUp, null, tint = ZEmerald, modifier = Modifier.size(18.dp))
            }
        }
    }
}
