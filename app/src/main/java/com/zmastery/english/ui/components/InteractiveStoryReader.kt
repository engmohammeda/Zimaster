package com.zmastery.english.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zmastery.english.audio.LocalTts
import com.zmastery.english.data.ArchivedStory
import com.zmastery.english.data.VocabWord
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel
import kotlinx.coroutines.launch

/**
 * Estoria-Style Interactive Story Reader.
 *
 * Offers an immersive, readable interface where:
 *  • Every single word is clickable for instant translation, IPA pronunciation & TTS.
 *  • Words can be added directly to the learner's vocabulary & FSRS spaced repetition queue.
 *  • Interactive AI word inspector provides instant deep contextual nuances on demand.
 *  • Dedicated Play and STOP buttons give complete audio playback control.
 *  • Configurable Arabic visibility (inline, sheet, or tap-to-reveal).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveStoryDialog(
    story: ArchivedStory,
    vm: AppViewModel,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = {
            vm.tts?.stop()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ZBackground,
        ) {
            InteractiveStoryReader(
                story = story,
                vm = vm,
                onClose = {
                    vm.tts?.stop()
                    onDismiss()
                }
            )
        }
    }
}

@Composable
fun InteractiveStoryReader(
    story: ArchivedStory,
    vm: AppViewModel,
    onClose: () -> Unit,
) {
    val tts = LocalTts.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var fontSizeSp by remember { mutableStateOf(19f) }
    var showFullArabic by remember { mutableStateOf(false) }
    var selectedWord by remember { mutableStateOf<String?>(null) }
    var wordExplanationLoading by remember { mutableStateOf(false) }
    var wordAiExplanation by remember { mutableStateOf<String?>(null) }

    val isPlaying = tts?.speakingKey == "story_${story.id}"
    val accent = ZIndigo

    // Split text into tokens (words + punctuation/whitespace)
    val tokens = remember(story.en) {
        val regex = Regex("([a-zA-Z0-9'-]+|[^a-zA-Z0-9'-]+)")
        regex.findAll(story.en).map { it.value }.toList()
    }

    val userVocabSet = remember(vm.vocab.size) {
        vm.vocab.map { it.english.trim().lowercase() }.toSet()
    }

    Scaffold(
        topBar = {
            Surface(
                color = ZSurface,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            tts?.stop()
                            onClose()
                        }) {
                            Icon(Icons.Filled.Close, "إغلاق", tint = ZTextPrimary)
                        }

                        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            Text(
                                story.title,
                                color = ZTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                "${story.level.label} · ${story.wordCount} كلمة · اضغط على أي كلمة للترجمة",
                                color = ZTextMuted,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }

                        // Text Size Controls
                        IconButton(
                            onClick = { if (fontSizeSp > 15f) fontSizeSp -= 2f },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("A-", color = ZTextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        IconButton(
                            onClick = { if (fontSizeSp < 28f) fontSizeSp += 2f },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("A+", color = ZTextSecondary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        // Favorite button
                        IconButton(
                            onClick = { vm.toggleStoryFavorite(story.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                if (story.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                "مفضلة",
                                tint = if (story.isFavorite) ZAmber else ZTextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Persistent Audio & Mode Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ZCard)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Audio Controls (Play & Dedicated STOP)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    if (isPlaying) {
                                        tts?.stop()
                                    } else {
                                        scope.launch {
                                            tts?.speakInstant(story.en, "story_${story.id}")
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (isPlaying) ZRose.copy(alpha = 0.2f) else accent.copy(alpha = 0.15f),
                                    contentColor = if (isPlaying) ZRose else accent
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (isPlaying) "إيقاف مؤقت" else "استماع للقصة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            // Dedicated STOP button
                            if (isPlaying) {
                                IconButton(
                                    onClick = { tts?.stop() },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(ZRose.copy(alpha = 0.2f))
                                ) {
                                    Icon(Icons.Filled.Stop, "إيقاف تام", tint = ZRose, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        // Arabic translation switcher
                        FilterChip(
                            selected = showFullArabic,
                            onClick = { showFullArabic = !showFullArabic },
                            label = { Text("الترجمة الكاملة", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(
                                    if (showFullArabic) Icons.Filled.Visibility else Icons.Filled.Translate,
                                    null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ZCyanDeep.copy(alpha = 0.2f),
                                selectedLabelColor = ZCyanDeep,
                                selectedLeadingIconColor = ZCyanDeep
                            )
                        )
                    }
                }
            }
        },
        bottomBar = {
            // Interactive Word Inspector Bottom Card
            AnimatedVisibility(
                visible = selectedWord != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                selectedWord?.let { rawWord ->
                    val cleanWord = rawWord.trim().filter { it.isLetter() }
                    val isAlreadySaved = userVocabSet.contains(cleanWord.lowercase())

                    Surface(
                        color = ZCard,
                        shadowElevation = 16.dp,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, ZBorder, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    ) {
                        Column(
                            Modifier
                                .padding(16.dp)
                                .navigationBarsPadding()
                        ) {
                            // Header: Word + Pronunciation + Close
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        cleanWord,
                                        color = ZTextPrimary,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    IconButton(
                                        onClick = {
                                            scope.launch { tts?.speakInstant(cleanWord, "word_$cleanWord") }
                                        },
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(accent.copy(alpha = 0.15f))
                                    ) {
                                        Icon(Icons.Filled.VolumeUp, "نطق", tint = accent, modifier = Modifier.size(18.dp))
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        selectedWord = null
                                        wordAiExplanation = null
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Filled.Close, "إغلاق", tint = ZTextMuted, modifier = Modifier.size(18.dp))
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Meaning in story context
                            val matchingVocab = vm.vocab.firstOrNull { it.english.equals(cleanWord, ignoreCase = true) }
                            val quickTranslation = matchingVocab?.arabic ?: getContextualTranslation(cleanWord)

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ZSurface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Translate, null, tint = ZCyan, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("المعنى:", color = ZTextMuted, fontSize = 12.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Text(quickTranslation, color = ZTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    }

                                    if (matchingVocab?.phonetic?.isNotBlank() == true) {
                                        Spacer(Modifier.height(4.dp))
                                        Text("الصوتيات: ${matchingVocab.phonetic}", color = ZPurple, fontSize = 12.sp)
                                    }
                                }
                            }

                            // AI Extended Explanation if loaded
                            if (wordExplanationLoading) {
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = accent)
                                    Spacer(Modifier.width(8.dp))
                                    Text("جارٍ تحليل الكلمة بالذكاء الاصطناعي…", color = ZTextMuted, fontSize = 12.sp)
                                }
                            } else if (wordAiExplanation != null) {
                                Spacer(Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = ZAmber.copy(alpha = 0.08f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        wordAiExplanation ?: "",
                                        color = ZTextPrimary,
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // Action buttons: Add to Vocabulary + Deep AI Explaining
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Add to Dictionary Button
                                Button(
                                    onClick = {
                                        if (!isAlreadySaved) {
                                            vm.addWord(
                                                english = cleanWord,
                                                arabic = quickTranslation,
                                                phonetic = "",
                                                exampleEn = "Found in story: ${story.title}",
                                                exampleAr = "وردت في قصة: ${story.title}",
                                                mentalImage = ""
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isAlreadySaved) ZEmerald else accent,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        if (isAlreadySaved) Icons.Filled.Check else Icons.Filled.BookmarkAdd,
                                        null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (isAlreadySaved) "في القاموس ✓" else "إضافة للقاموس",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Deep AI Coach Explanation Button
                                OutlinedButton(
                                    onClick = {
                                        wordExplanationLoading = true
                                        wordAiExplanation = null
                                        scope.launch {
                                            val key = vm.activeKey
                                            if (key != null) {
                                                val explainer = vm.aiAgents.firstOrNull { it.id == "word_explainer" }
                                                val system = com.zmastery.english.data.AiPrompts.fill(
                                                    explainer?.prompt.orEmpty().ifBlank {
                                                        "You are an expert English teacher. Explain words briefly and deeply for an Arabic-speaking learner."
                                                    },
                                                    mapOf(
                                                        "LEVEL" to vm.cefrEstimate.first,
                                                        "CONTEXT" to story.en.take(400),
                                                        "WORD" to cleanWord,
                                                    ),
                                                )
                                                val prompt = "Explain the English word \"$cleanWord\" in this story. Two tight sentences only."
                                                val reply = vm.aiComplete(
                                                    system = system,
                                                    user = prompt,
                                                    agentId = "word_explainer",
                                                )
                                                wordAiExplanation = if (reply.ok) reply.text.trim() else "تعذر جلب الشرح حالياً."
                                            } else {
                                                wordAiExplanation = "أضف مفتاح API في الإعدادات لتفعيل التحليل الذكي للكلمات."
                                            }
                                            wordExplanationLoading = false
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(ZPurple, ZIndigo)))
                                ) {
                                    Icon(Icons.Filled.AutoAwesome, null, tint = ZPurple, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("شرح الذكاء الاصطناعي", color = ZPurple, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(20.dp)
        ) {
            // Interactive Story Body (Clickable word tokens)
            StoryTokensLayout(
                tokens = tokens,
                userVocabSet = userVocabSet,
                fontSizeSp = fontSizeSp,
                selectedWord = selectedWord,
                onWordClick = { word ->
                    selectedWord = word
                    wordAiExplanation = null
                }
            )

            // Optional Full Arabic Translation Accordion
            AnimatedVisibility(visible = showFullArabic && story.ar.isNotBlank()) {
                Column {
                    Spacer(Modifier.height(24.dp))
                    HorizontalDivider(color = ZBorder)
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Translate, null, tint = ZCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("الترجمة الكاملة للقصة", color = ZCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        color = ZCard,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            story.ar,
                            color = ZTextSecondary,
                            fontSize = (fontSizeSp - 2).sp,
                            lineHeight = ((fontSizeSp - 2) * 1.7).sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            // Key words row
            if (story.words.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text("المفردات المستهدفة بالقصة:", color = ZTextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    story.words.forEach { w ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = accent.copy(alpha = 0.12f),
                            modifier = Modifier.clickable {
                                selectedWord = w
                                wordAiExplanation = null
                            }
                        ) {
                            Text(
                                w,
                                color = accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(100.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoryTokensLayout(
    tokens: List<String>,
    userVocabSet: Set<String>,
    fontSizeSp: Float,
    selectedWord: String?,
    onWordClick: (String) -> Unit
) {
    Surface(
        color = ZCard,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        FlowRow(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.Start,
            verticalArrangement = Arrangement.Center
        ) {
            tokens.forEach { token ->
                val isWord = token.any { it.isLetter() }
                if (isWord) {
                    val clean = token.trim().filter { it.isLetter() }
                    val isKnownInVocab = userVocabSet.contains(clean.lowercase())
                    val isSelected = selectedWord?.equals(clean, ignoreCase = true) == true

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    isSelected -> ZIndigo.copy(alpha = 0.35f)
                                    isKnownInVocab -> ZEmerald.copy(alpha = 0.12f)
                                    else -> Color.Transparent
                                }
                            )
                            .clickable { onWordClick(clean) }
                            .padding(horizontal = 2.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = token,
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * 1.65).sp,
                            fontWeight = if (isSelected || isKnownInVocab) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                isSelected -> ZIndigo
                                isKnownInVocab -> ZEmerald
                                else -> ZTextPrimary
                            }
                        )
                    }
                } else {
                    // Punctuation or whitespace
                    Text(
                        text = token,
                        fontSize = fontSizeSp.sp,
                        lineHeight = (fontSizeSp * 1.65).sp,
                        color = ZTextSecondary
                    )
                }
            }
        }
    }
}

/**
 * Basic offline contextual dictionary lookup for immediate feedback.
 */
private fun getContextualTranslation(word: String): String {
    val map = mapOf(
        "the" to "الـ", "a" to "أداة تنكير", "an" to "أداة تنكير",
        "and" to "و", "or" to "أو", "but" to "لكن", "because" to "لأن",
        "he" to "هو", "she" to "هي", "it" to "هو/هي لغير العاقل",
        "they" to "هم", "we" to "نحن", "you" to "أنت / أنتم", "i" to "أنا",
        "is" to "يكون", "are" to "يكونون", "was" to "كان", "were" to "كانوا",
        "have" to "يملك / لديه", "has" to "يملك", "had" to "امتلك",
        "do" to "يفعل", "does" to "يفعل", "did" to "فعل",
        "say" to "يقول", "said" to "قال", "go" to "يذهب", "went" to "ذهب",
        "make" to "يصنع", "made" to "صنع", "know" to "يعرف", "knew" to "عرف",
        "think" to "يفكر / يعتقد", "thought" to "فكر", "take" to "يأخذ", "took" to "أخذ",
        "see" to "يرى", "saw" to "رأى", "come" to "يأتي", "came" to "أتى",
        "want" to "يريد", "look" to "ينظر", "use" to "يستخدم", "find" to "يجد",
        "tell" to "يخبر", "told" to "أخبر", "ask" to "يسأل", "work" to "يعمل",
        "seem" to "يبدو", "feel" to "يشعر", "try" to "يحاول", "leave" to "يغادر",
        "call" to "يتصل / ينادي", "good" to "جيد", "new" to "جديد", "first" to "أول",
        "last" to "أخير", "long" to "طويل", "great" to "عظيم / رائع", "little" to "صغير / قليل",
        "own" to "خاص به", "other" to "آخر", "old" to "قديم / مسن", "right" to "صحيح / يمين",
        "big" to "كبير", "high" to "عالي", "different" to "مختلف", "small" to "صغير",
        "large" to "ضخم", "next" to "التالي", "early" to "مبكر", "young" to "شاب",
        "important" to "مهم", "few" to "قليل", "public" to "عام", "bad" to "سيء",
        "same" to "نفس الشيء", "able" to "قادر", "story" to "قصة", "learn" to "يتعلم",
        "word" to "كلمة", "english" to "الإنجليزية", "life" to "حياة", "day" to "يوم",
        "time" to "وقت", "year" to "سنة", "people" to "ناس", "way" to "طريقة / طريق",
        "man" to "رجل", "woman" to "امرأة", "child" to "طفل", "world" to "عالم",
        "school" to "مدرسة", "family" to "عائلة", "student" to "طالب", "group" to "مجموعة",
        "country" to "بلد", "problem" to "مشكلة", "hand" to "يد", "part" to "جزء",
        "place" to "مكان", "case" to "حالة", "week" to "أسبوع", "company" to "شركة",
        "system" to "نظام", "program" to "برنامج", "question" to "سؤال", "work" to "عمل",
        "night" to "ليل", "point" to "نقطة", "home" to "منزل", "water" to "ماء",
        "room" to "غرفة", "mother" to "أم", "area" to "منطقة", "money" to "مال",
        "story" to "قصة", "fact" to "حقيقة", "month" to "شهر", "lot" to "كثير",
        "right" to "حق / صواب", "study" to "دراسة / يدرس", "book" to "كتاب", "eye" to "عين",
        "job" to "وظيفة", "word" to "كلمة", "business" to "أعمال", "issue" to "قضية",
        "side" to "جانب", "kind" to "نوع / لطيف", "head" to "رأس", "house" to "بيت",
        "service" to "خدمة", "friend" to "صديق", "father" to "أب", "power" to "قوة",
        "hour" to "ساعة", "game" to "لعبة", "line" to "خط", "end" to "نهاية",
        "member" to "عضو", "law" to "قانون", "car" to "سيارة", "city" to "مدينة",
        "community" to "مجتمع", "name" to "اسم", "president" to "رئيس", "team" to "فريق"
    )
    return map[word.lowercase()] ?: "انقر 'شرح الذكاء الاصطناعي' لجلب المعنى الدقيق بالسياق"
}
