package com.zmastery.english.ui.screens.lessons.blocks

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zmastery.english.data.BrainstormQ
import com.zmastery.english.data.Lesson
import com.zmastery.english.data.Sentence
import com.zmastery.english.ui.components.SoftCard
import com.zmastery.english.ui.theme.*
import kotlinx.coroutines.flow.first

// ==========================================================================
// Writing block — renders the writing-course content that previously existed
// in the data model but had NO UI at all:
//   topic → brainstorming (tap-to-reveal suggested answers) → guided sentences
//   → the learner's own draft (auto-saved per lesson) → the model final draft.
// ==========================================================================

/** Drafts are stored per-lesson in their own tiny DataStore. */
private val Context.writingStore: DataStore<Preferences> by preferencesDataStore(name = "writing_drafts")

internal fun LazyListScope.writingBlock(lesson: Lesson, accent: Color) {
    // ---- The topic ----
    if (lesson.topicEn.isNotBlank() || lesson.topicAr.isNotBlank()) {
        item { SectionHeader(Icons.Filled.Edit, "موضوع الكتابة", accent) }
        item { TopicCard(lesson.topicEn, lesson.topicAr, accent) }
    }

    // ---- Brainstorming questions with hidden suggested answers ----
    if (lesson.brainstorming.isNotEmpty()) {
        item { SectionHeader(Icons.Filled.Psychology, "عصف ذهني (${lesson.brainstorming.size})", accent) }
        items(lesson.brainstorming.size, key = { "brain_$it" }) { i ->
            BrainstormCard(lesson.brainstorming[i], i + 1, accent)
        }
    }

    // ---- Guided sentences ----
    if (lesson.guidedSentences.isNotEmpty()) {
        item { SectionHeader(Icons.Filled.FormatListNumbered, "جمل موجّهة (${lesson.guidedSentences.size})", accent) }
        item {
            SoftCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp)) {
                    lesson.guidedSentences.forEachIndexed { i, s ->
                        GuidedSentenceRow(s, i + 1, accent)
                        if (i != lesson.guidedSentences.lastIndex) {
                            HorizontalDivider(color = ZBorder, modifier = Modifier.padding(horizontal = 12.dp))
                        }
                    }
                }
            }
        }
    }

    // ---- The learner's own draft (auto-saved locally) ----
    item { SectionHeader(Icons.Filled.Draw, "مسودّتك", accent) }
    item { DraftEditor(lessonId = lesson.id, accent = accent) }

    // ---- The model final draft ----
    lesson.finalDraft?.let { final ->
        item { SectionHeader(Icons.Filled.CheckCircle, "المسودة النموذجية", ZEmerald) }
        item { FinalDraftCard(final, accent) }
    }
}

@Composable
private fun TopicCard(topicEn: String, topicAr: String, accent: Color) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            if (topicEn.isNotBlank()) {
                Text(topicEn, color = ZTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp)
            }
            if (topicAr.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(topicAr, color = ZTextSecondary, fontSize = 15.sp, lineHeight = 24.sp)
            }
        }
    }
}

@Composable
private fun BrainstormCard(q: BrainstormQ, index: Int, accent: Color) {
    var revealed by remember { mutableStateOf(false) }
    SoftCard(modifier = Modifier.fillMaxWidth(), radius = 16.dp, onClick = { revealed = !revealed }) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) { Text("$index", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(q.questionEn, color = ZTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 23.sp)
                    if (q.questionAr.isNotBlank()) {
                        Text(q.questionAr, color = ZTextSecondary, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                }
                Icon(
                    if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    null, tint = ZTextMuted, modifier = Modifier.size(18.dp),
                )
            }
            if (q.suggestedAnswerEn.isNotBlank()) {
                AnimatedVisibility(revealed) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(accent.copy(alpha = 0.08f)).padding(12.dp),
                        ) {
                            Column {
                                Text("إجابة مقترحة", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text(q.suggestedAnswerEn, color = ZTextPrimary, fontSize = 14.sp, lineHeight = 22.sp)
                                if (q.suggestedAnswerAr.isNotBlank()) {
                                    Text(q.suggestedAnswerAr, color = ZTextSecondary, fontSize = 13.sp, lineHeight = 20.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuidedSentenceRow(s: Sentence, index: Int, accent: Color) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Text("$index", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.en, color = ZTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 23.sp)
            if (s.ar.isNotBlank()) {
                Text(s.ar, color = ZTextSecondary, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        com.zmastery.english.audio.AudioButton(
            text = s.en, audioKey = "gs_${s.en.hashCode()}", accent = accent, size = 36.dp, iconSize = 18.dp,
        )
    }
}

/**
 * The learner writes their own draft here. Auto-saved to a local DataStore
 * keyed by lesson id — nothing they type is ever lost on exit.
 */
@Composable
private fun DraftEditor(lessonId: Int, accent: Color) {
    val ctx = LocalContext.current
    val key = remember(lessonId) { stringPreferencesKey("draft_$lessonId") }
    // null = still loading; avoids overwriting the saved draft with "" on entry.
    var draft by remember(lessonId) { mutableStateOf<String?>(null) }
    LaunchedEffect(lessonId) {
        draft = ctx.writingStore.data.first()[key] ?: ""
    }
    LaunchedEffect(draft) {
        draft?.let { ctx.writingStore.edit { prefs -> prefs[key] = it } }
    }

    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = draft ?: "",
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                placeholder = { Text("اكتب مسودتك هنا بالإنجليزية…", color = ZTextMuted, fontSize = 14.sp) },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = ZBorder,
                    focusedTextColor = ZTextPrimary,
                    unfocusedTextColor = ZTextPrimary,
                    cursorColor = accent,
                ),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "تُحفظ تلقائياً على جهازك · قارنها بالمسودة النموذجية بعد الإنهاء",
                color = ZTextMuted, fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun FinalDraftCard(final: Sentence, accent: Color) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.zmastery.english.audio.AudioButton(
                    text = final.en, audioKey = "fd_${final.en.hashCode()}", accent = ZEmerald, size = 40.dp, iconSize = 20.dp,
                )
                Spacer(Modifier.width(12.dp))
                Text("اقرأها بصوت، ثم قارنها بمسودّتك", color = ZTextSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text(final.en, color = ZTextPrimary, fontSize = 16.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
            if (final.ar.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = ZBorder)
                Spacer(Modifier.height(12.dp))
                Text(final.ar, color = ZTextSecondary, fontSize = 14.sp, lineHeight = 24.sp)
            }
        }
    }
}
