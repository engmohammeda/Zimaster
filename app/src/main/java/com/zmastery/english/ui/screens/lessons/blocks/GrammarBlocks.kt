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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.data.Sentence
import com.zmastery.english.ui.components.SoftCard
import com.zmastery.english.ui.theme.*

// ==========================================================================
// Grammar blocks — the rule (explanation), why it works (logic), examples.
// ==========================================================================

/** بلوك القاعدة: الشرح + لماذا؟ منطق القاعدة. */
internal fun LazyListScope.grammarRuleBlock(explanationAr: String, logicAr: String, accent: Color) {
    if (explanationAr.isNotBlank()) {
        item { SectionHeader(Icons.Filled.MenuBook, "القاعدة", accent) }
        item {
            SoftCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        explanationAr,
                        color = ZTextPrimary,
                        fontSize = 16.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
    if (logicAr.isNotBlank()) {
        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(accent.copy(alpha = 0.08f))
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(accent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Psychology, null, tint = accent, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("لماذا؟ منطق القاعدة", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(logicAr, color = ZTextPrimary, fontSize = 14.sp, lineHeight = 24.sp)
                    }
                }
            }
        }
    }
}

/** بلوك الأمثلة — جملة إنجليزية بصوت + الترجمة. */
internal fun LazyListScope.examplesBlock(examples: List<Sentence>, accent: Color) {
    item { SectionHeader(Icons.Filled.FormatListBulleted, "أمثلة (${examples.size})", accent) }
    items(examples.size, key = { "exb_$it" }) { i ->
        ExampleRow(examples[i], accent)
    }
}

@Composable
private fun ExampleRow(ex: Sentence, accent: Color) {
    SoftCard(modifier = Modifier.fillMaxWidth(), radius = 16.dp) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            com.zmastery.english.audio.AudioButton(
                text = ex.en, audioKey = "gex_${ex.en.hashCode()}", accent = accent, size = 40.dp, iconSize = 20.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ex.en, color = ZTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
                if (ex.ar.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(ex.ar, color = ZTextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
        }
    }
}
