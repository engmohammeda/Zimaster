package com.zmastery.english.ui.screens.lessons.blocks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.data.Sentence
import com.zmastery.english.ui.components.SoftCard
import com.zmastery.english.ui.theme.ZBorder
import com.zmastery.english.ui.theme.ZTextPrimary
import com.zmastery.english.ui.theme.ZTextSecondary

// ==========================================================================
// Key-sentences block — EN / AR sentence rows with pronunciation audio.
// ==========================================================================

internal fun LazyListScope.keySentencesBlock(sentences: List<Sentence>, accent: Color) {
    item { SectionHeader(Icons.Filled.FormatQuote, "الجمل الأساسية", accent) }
    item {
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp)) {
                sentences.forEachIndexed { i, s ->
                    KeySentenceRow(s, accent)
                    if (i != sentences.lastIndex) {
                        HorizontalDivider(color = ZBorder, modifier = Modifier.padding(horizontal = 12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun KeySentenceRow(s: Sentence, accent: Color) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(s.en, color = ZTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
            Spacer(Modifier.height(4.dp))
            Text(s.ar, color = ZTextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
        }
        Spacer(Modifier.width(8.dp))
        com.zmastery.english.audio.AudioButton(
            text = s.en, audioKey = "ks_${s.en.hashCode()}", accent = accent, size = 38.dp, iconSize = 20.dp,
        )
    }
}
