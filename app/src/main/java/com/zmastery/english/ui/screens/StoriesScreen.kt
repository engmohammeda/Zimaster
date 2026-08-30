package com.zmastery.english.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.zmastery.english.data.ArchivedStory
import com.zmastery.english.data.StoryKind
import com.zmastery.english.data.StoryLevel
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel

/**
 * Unified story archive.
 *
 * Everything readable the learner has ever produced or studied lives here:
 *  • the DAILY story generated from their own due words, and
 *  • the story/text of every reading-course lesson.
 *
 * One archive = one habit loop. There is a single place to come back and
 * re-read, which is what turns reading into actual review.
 */
@Composable
fun StoriesScreen(vm: AppViewModel, focusStoryId: Int? = null) {
    com.zmastery.english.ui.components.TrackStudyTime(vm, "stories")
    var filter by remember { mutableStateOf(0) } // 0 all · 1 daily · 2 lesson · 3 unread · 4 favorites
    var query by remember { mutableStateOf("") }
    var expandedId by remember { mutableStateOf(focusStoryId) }
    var interactiveStory by remember { mutableStateOf<ArchivedStory?>(null) }

    // Keep lesson stories in sync whenever the archive screen is opened.
    LaunchedEffect(Unit) { vm.syncLessonStories() }

    // Interactive Estoria-style story dialog
    interactiveStory?.let { story ->
        com.zmastery.english.ui.components.InteractiveStoryDialog(
            story = story,
            vm = vm,
            onDismiss = { interactiveStory = null }
        )
    }

    val all = vm.storiesSorted
    val list = all.filter { s ->
        val matchQuery = query.isBlank() ||
            s.title.contains(query, true) ||
            s.en.contains(query, true) ||
            s.words.any { it.contains(query, true) }
        val matchFilter = when (filter) {
            1 -> s.kind == StoryKind.DAILY
            2 -> s.kind == StoryKind.LESSON
            3 -> !s.isRead
            4 -> s.isFavorite
            else -> true
        }
        matchQuery && matchFilter
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { ArchiveHeader(vm) }
        item {
            TodayStoryCard(
                vm = vm,
                onOpen = { expandedId = it },
                onOpenInteractive = { s -> interactiveStory = s }
            )
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("ابحث في الأرشيف...", color = ZTextMuted) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = ZTextSecondary) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ZCard, unfocusedContainerColor = ZCard,
                    focusedBorderColor = ZAmber, unfocusedBorderColor = ZBorder,
                    focusedTextColor = ZTextPrimary, unfocusedTextColor = ZTextPrimary,
                ),
            )
        }

        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ArchiveChip(filter == 0, "الكل (${all.size})", ZAmber) { filter = 0 }
                ArchiveChip(filter == 1, "يومية (${vm.dailyStoryCount})", ZIndigo) { filter = 1 }
                ArchiveChip(filter == 2, "دروس (${vm.lessonStoryCount})", ZCyanDeep) { filter = 2 }
                ArchiveChip(filter == 3, "غير مقروءة (${vm.unreadStoryCount})", ZRose) { filter = 3 }
                ArchiveChip(filter == 4, "مفضّلة", ZEmerald) { filter = 4 }
            }
        }

        if (list.isEmpty()) {
            item { EmptyArchive(all.isEmpty(), vm) }
        } else {
            items(list, key = { it.id }) { story ->
                StoryCard(
                    story = story,
                    vm = vm,
                    expanded = expandedId == story.id,
                    onToggle = { expandedId = if (expandedId == story.id) null else story.id },
                    onOpenInteractive = { interactiveStory = story }
                )
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

@Composable
private fun ArchiveHeader(vm: AppViewModel) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(ZAmber, ZRoseDeep)))
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoStories, null, tint = Color.White, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Text("أرشيف القصص", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "قصة اليوم من كلماتك، وقصص كل درس قراءة — في مكان واحد تعود إليه دائماً.",
                color = Color.White.copy(alpha = 0.93f), fontSize = 13.sp, lineHeight = 21.sp,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderPill(Icons.Filled.Today, "${vm.dailyStoryCount} يومية")
                HeaderPill(Icons.Filled.MenuBook, "${vm.lessonStoryCount} درس")
                if (vm.unreadStoryCount > 0) HeaderPill(Icons.Filled.MarkEmailUnread, "${vm.unreadStoryCount} جديدة")
            }
        }
    }
}

@Composable
private fun HeaderPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.22f)) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** The daily ritual card: generate today's story, or jump straight into it. */
@Composable
private fun TodayStoryCard(
    vm: AppViewModel,
    onOpen: (Int) -> Unit,
    onOpenInteractive: (ArchivedStory) -> Unit
) {
    val today = vm.todayStory
    Surface(
        shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 5.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(ZIndigo, ZPurple))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (today == null) Icons.Filled.AutoAwesome else Icons.Filled.MenuBook,
                        null, tint = Color.White, modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (today == null) "قصة اليوم" else "قصة اليوم جاهزة",
                        color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp,
                    )
                    Text(
                        if (today == null) "تُبنى من كلماتك المستحقة للمراجعة"
                        else "${today.wordCount} كلمة · ${today.readMinutes} دقيقة قراءة",
                        color = ZTextSecondary, fontSize = 11.sp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (vm.isMakingStory) {
                // ---- Live AI progress, including the "waiting for network" park ----
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = (if (vm.isWaitingForAi) ZAmber else ZIndigo).copy(alpha = 0.10f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (vm.isWaitingForAi) {
                                Icon(Icons.Filled.CloudOff, null, tint = ZAmber, modifier = Modifier.size(19.dp))
                            } else {
                                CircularProgressIndicator(color = ZIndigo, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (vm.isWaitingForAi) "بانتظار الاتصال بالنموذج"
                                    else "الذكاء الاصطناعي ينسج قصتك…",
                                    color = if (vm.isWaitingForAi) ZAmber else ZTextPrimary,
                                    fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                )
                                vm.storyMessage?.let {
                                    Text(it, color = ZTextSecondary, fontSize = 11.sp, lineHeight = 17.sp)
                                }
                            }
                        }
                        if (vm.isWaitingForAi) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (vm.storyRetryIn > 0) {
                                    Icon(Icons.Filled.Timer, null, tint = ZTextMuted, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "إعادة المحاولة بعد ${vm.storyRetryIn} ثانية",
                                        color = ZTextMuted, fontSize = 11.sp,
                                    )
                                } else {
                                    CircularProgressIndicator(color = ZAmber, strokeWidth = 2.dp, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("جارٍ إعادة المحاولة…", color = ZTextMuted, fontSize = 11.sp)
                                }
                                Spacer(Modifier.weight(1f))
                                Text("محاولة ${vm.storyAttempt}", color = ZTextMuted, fontSize = 10.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "قصة اليوم تُكتب بالذكاء الاصطناعي فقط — لن نولّد نصاً جاهزاً بدون إنترنت.",
                                color = ZTextMuted, fontSize = 10.sp, lineHeight = 16.sp,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { vm.cancelStoryGeneration() },
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ZBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ZTextSecondary),
                        ) {
                            Icon(Icons.Filled.Close, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("إلغاء", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            } else if (today == null) {
                val canGenerate = vm.storySeedCount >= 2 && vm.storyAiReady
                Button(
                    onClick = { vm.generateTodayStory { s -> s?.let { onOpen(it.id) } } },
                    enabled = canGenerate,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ZIndigo, disabledContainerColor = ZBorder),
                ) {
                    Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("ولّد قصة اليوم بالذكاء الاصطناعي", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                if (!vm.storyAiReady) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Key, null, tint = ZAmber, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "أضف مفتاح Gemini من إعدادات الذكاء الاصطناعي لتفعيل التوليد",
                            color = ZAmber, fontSize = 11.sp, lineHeight = 17.sp,
                        )
                    }
                } else if (vm.storySeedCount < 2) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "تحتاج كلمتين على الأقل لها أمثلة في القاموس",
                        color = ZTextMuted, fontSize = 11.sp,
                    )
                } else {
                    vm.storyMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = ZRose, fontSize = 11.sp, lineHeight = 17.sp)
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onOpenInteractive(today) },
                        modifier = Modifier.weight(1.3f).height(46.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ZIndigo),
                    ) {
                        Icon(Icons.Filled.TouchApp, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("قراءة تفاعلية (ايستوريا)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { vm.generateTodayStory(force = true) { s -> s?.let { onOpenInteractive(it) } } },
                        modifier = Modifier.weight(0.9f).height(46.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ZBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ZTextSecondary),
                    ) {
                        Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("تجديد", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveChip(active: Boolean, label: String, accent: Color, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (active) accent else ZCard,
        onClick = onClick,
        shadowElevation = if (active) 0.dp else 2.dp,
    ) {
        Text(
            label, color = if (active) Color.White else ZTextSecondary,
            fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoryCard(
    story: ArchivedStory,
    vm: AppViewModel,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenInteractive: () -> Unit,
) {
    var showAr by remember { mutableStateOf(false) }
    val accent = if (story.kind == StoryKind.DAILY) ZIndigo else ZCyanDeep
    val levelColor = when (story.level) {
        StoryLevel.EASY -> ZEmerald
        StoryLevel.MEDIUM -> ZAmber
        StoryLevel.HARD -> ZRose
    }

    Surface(
        shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(), onClick = onToggle,
    ) {
        Column(
            Modifier.background(
                Brush.horizontalGradient(
                    listOf(accent.copy(alpha = 0.10f), accent.copy(alpha = 0.03f), Color.Transparent)
                )
            ).padding(16.dp)
        ) {
            // ---- Header row ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (story.kind == StoryKind.DAILY) Icons.Filled.AutoAwesome else Icons.Filled.MenuBook,
                        null, tint = accent, modifier = Modifier.size(19.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        story.title, color = ZTextPrimary, fontWeight = FontWeight.Black,
                        fontSize = 16.sp, maxLines = 2, lineHeight = 22.sp,
                    )
                    Text(
                        buildString {
                            append(story.kind.short)
                            if (story.courseName.isNotBlank()) append(" · ${story.courseName}")
                            if (story.dateLabel.isNotBlank()) append(" · ${story.dateLabel}")
                        },
                        color = ZTextMuted, fontSize = 10.sp, maxLines = 1,
                    )
                }
                IconButton(onClick = { vm.toggleStoryFavorite(story.id) }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        if (story.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        "مفضّلة",
                        tint = if (story.isFavorite) ZAmber else ZTextMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---- Meta pills ----
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MetaPill(story.level.label, levelColor)
                MetaPill("${story.readMinutes} د", ZTextSecondary)
                MetaPill("${story.wordCount} كلمة", ZTextSecondary)
                if (story.isRead) MetaPill("مقروءة", ZEmerald)
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accent.copy(alpha = 0.15f),
                    onClick = onOpenInteractive
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.TouchApp, null, tint = accent, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("قراءة تفاعلية", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---- Body (clamped when collapsed) — force LTR so English
            // punctuation never lands at the start of a line in an RTL screen.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    story.en,
                    color = ZTextPrimary, fontSize = 15.sp, lineHeight = 26.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            AnimatedVisibility(expanded && showAr && story.ar.isNotBlank(), enter = fadeIn(tween(220)), exit = fadeOut()) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = ZBorder)
                    Spacer(Modifier.height(12.dp))
                    Text(story.ar, color = ZTextSecondary, fontSize = 14.sp, lineHeight = 25.sp)
                }
            }

            // ---- Featured words ----
            if (story.words.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(story.words) { w ->
                        Surface(shape = RoundedCornerShape(12.dp), color = accent.copy(alpha = 0.14f)) {
                            Text(
                                w, color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }

            // ---- Actions (expanded only) — FlowRow so chips never wrap letter-by-letter.
            AnimatedVisibility(expanded, enter = fadeIn(tween(220)), exit = fadeOut()) {
                val scope = rememberCoroutineScope()
                val speaking = vm.tts?.speakingKey == "story_${story.id}"
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = ZBorder)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = {
                                if (speaking) vm.tts?.stop()
                                else scope.launch { vm.tts?.speakInstant(story.en, "story_${story.id}") }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (speaking) ZRose.copy(alpha = 0.18f) else accent.copy(alpha = 0.14f),
                                contentColor = if (speaking) ZRose else accent,
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                if (speaking) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                                if (speaking) "إيقاف" else "استماع",
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (speaking) "إيقاف" else "استماع",
                                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, softWrap = false,
                            )
                        }
                        StoryActionChip(
                            icon = Icons.Filled.AutoStories,
                            label = "قراءة تفاعلية",
                            tint = accent,
                            onClick = onOpenInteractive,
                        )
                        if (story.ar.isNotBlank()) {
                            StoryActionChip(
                                icon = if (showAr) Icons.Filled.VisibilityOff else Icons.Filled.Translate,
                                label = if (showAr) "إخفاء الترجمة" else "الترجمة",
                                tint = ZCyan,
                                onClick = { showAr = !showAr },
                            )
                        }
                        StoryActionChip(
                            icon = if (story.isRead) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            label = if (story.isRead) "مقروءة" else "علّم كمقروءة",
                            tint = if (story.isRead) ZEmerald else ZTextSecondary,
                            onClick = { vm.toggleStoryRead(story.id) },
                        )
                    }
                }
            }

            if (!expanded) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ExpandMore, null, tint = ZTextMuted, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("اضغط للقراءة الكاملة", color = ZTextMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun StoryActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = tint.copy(alpha = 0.12f),
        onClick = onClick,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                color = tint,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun MetaPill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.13f)) {
        Text(
            text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun EmptyArchive(totallyEmpty: Boolean, vm: AppViewModel) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.AutoStories, null, tint = ZTextMuted, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            if (totallyEmpty) "الأرشيف فارغ" else "لا توجد نتائج",
            color = ZTextSecondary, fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (totallyEmpty)
                "ولّد قصة اليوم من كلماتك، أو استورد كورس قراءة لتظهر قصص دروسه هنا"
            else "جرّب فلتراً أو بحثاً آخر",
            color = ZTextMuted, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 19.sp,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}
