package com.zmastery.english.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.window.Dialog
import com.zmastery.english.data.VocabWord
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun VocabularyScreen(vm: AppViewModel, onOpenMnemonics: () -> Unit = {}) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(0) } // 0 all, 1 due, 2 mastered, 3 hard, 4 no-image
    var showAdd by remember { mutableStateOf(false) }
    val dueIds = remember(vm.vocab.toList()) { vm.dueWords.map { it.id }.toSet() }
    val list = vm.activeVocab.filter {
        (query.isBlank() || it.english.contains(query, true) || it.arabic.contains(query)) &&
        when (filter) {
            1 -> it.id in dueIds && !it.mastered
            2 -> it.mastered
            3 -> it.lapses >= 2 || (it.difficulty >= 6.5 && it.totalReviews > 0)
            4 -> !vm.hasMnemonic(it.id)
            else -> true
        }
    }
    val hardCount = vm.activeVocab.count { it.lapses >= 2 || (it.difficulty >= 6.5 && it.totalReviews > 0) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("ابحث عن كلمة...", color = ZTextMuted) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = ZTextSecondary) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ZCard, unfocusedContainerColor = ZCard,
                    focusedBorderColor = ZIndigo, unfocusedBorderColor = ZBorder,
                    focusedTextColor = ZTextPrimary, unfocusedTextColor = ZTextPrimary,
                ),
            )
            Row(
                Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(filter == 0, "الكل (${vm.totalWords})") { filter = 0 }
                FilterChip(filter == 1, "مستحقة (${vm.dueWords.size})") { filter = 1 }
                FilterChip(filter == 2, "متقنة (${vm.masteredCount})") { filter = 2 }
                FilterChip(filter == 3, "صعبة ($hardCount)") { filter = 3 }
                FilterChip(filter == 4, "بلا صورة (${vm.mnemonicMissingCount})") { filter = 4 }
            }
            Spacer(Modifier.height(8.dp))
            if (list.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.MenuBook, null, tint = ZTextMuted, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(if (vm.totalWords == 0) "القاموس فارغ" else "لا توجد نتائج", color = ZTextSecondary, fontWeight = FontWeight.Bold)
                    Text(if (vm.totalWords == 0) "أضف كلمة أو أكمل درساً لتظهر هنا" else "جرّب فلتراً أو بحثاً آخر", color = ZTextMuted, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(list, key = { it.id }) { WordCard(it, vm) }
                    item { Spacer(Modifier.height(90.dp)) }
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomEnd).padding(20.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Mnemonic studio — badge shows how many words still need an image.
            ExtendedFloatingActionButton(
                onClick = onOpenMnemonics,
                containerColor = ZPurple,
                contentColor = Color.White,
            ) {
                Icon(Icons.Filled.Link, null)
                Spacer(Modifier.width(8.dp))
                Text("الروابط الذهنية", fontWeight = FontWeight.Bold)
                if (vm.mnemonicMissingCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.26f)) {
                        Text(
                            "${vm.mnemonicMissingCount}", color = Color.White,
                            fontSize = 11.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                containerColor = ZIndigo,
                contentColor = Color.White,
            ) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("إضافة كلمة", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showAdd) {
        AddWordDialog(vm) { showAdd = false }
    }
}

@Composable
private fun AddWordDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var aiTab by remember { mutableStateOf(true) } // true = Smart AI, false = Manual
    val scope = rememberCoroutineScope()

    // shared / manual fields
    var english by remember { mutableStateOf("") }
    var arabic by remember { mutableStateOf("") }
    var exampleEn by remember { mutableStateOf("") }
    var exampleAr by remember { mutableStateOf("") }
    // AI fields
    var context by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(26.dp), color = ZCard, shadowElevation = 8.dp) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("إضافة كلمة جديدة", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 19.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, null, tint = ZTextMuted)
                    }
                }
                Spacer(Modifier.height(16.dp))

                // Tab switch
                Surface(shape = RoundedCornerShape(14.dp), color = ZSurfaceVariant) {
                    Row(Modifier.padding(4.dp)) {
                        TabButton("Smart AI", aiTab, Modifier.weight(1f), Icons.Filled.AutoAwesome) { aiTab = true; error = null; success = null }
                        TabButton("يدوي", !aiTab, Modifier.weight(1f), Icons.Filled.EditNote) { aiTab = false; error = null; success = null }
                    }
                }
                Spacer(Modifier.height(16.dp))

                if (aiTab) {
                    DialogField(english, { english = it; error = null }, "الكلمة الإنجليزية", enabled = !loading)
                    Spacer(Modifier.height(12.dp))
                    DialogField(context, { context = it }, "سياقك المخصص / جملتك (اختياري)", enabled = !loading, minLines = 2)
                } else {
                    DialogField(english, { english = it }, "الكلمة الإنجليزية")
                    Spacer(Modifier.height(12.dp))
                    DialogField(arabic, { arabic = it }, "الترجمة العربية")
                    Spacer(Modifier.height(12.dp))
                    DialogField(exampleEn, { exampleEn = it }, "مثال بالإنجليزية")
                    Spacer(Modifier.height(12.dp))
                    DialogField(exampleAr, { exampleAr = it }, "ترجمة المثال")
                }

                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ErrorOutline, null, tint = ZRose, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(it, color = ZRose, fontSize = 12.sp)
                    }
                }
                success?.let {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, tint = ZEmerald, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(it, color = ZEmerald, fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(18.dp))

                if (aiTab) {
                    Button(
                        onClick = {
                            error = null; success = null
                            if (english.isBlank()) { error = "اكتب الكلمة الإنجليزية"; return@Button }
                            loading = true
                            scope.launch {
                                val agentModel = vm.aiAgents.firstOrNull { it.id == "translator" }?.modelId.orEmpty()
                                when (val r = com.zmastery.english.data.GeminiWordService.generate(english, context, vm.activeKey, agentModel)) {
                                    is com.zmastery.english.data.GeminiWordService.Result.Success -> {
                                        val w = r.word
                                        vm.addWord(w.english, w.arabic, w.exampleEn, w.exampleAr, w.phonetic, w.mentalImage)
                                        success = "تمت إضافة \"${w.english}\" بالترجمة والمثال والصوت"
                                        english = ""; context = ""
                                    }
                                    is com.zmastery.english.data.GeminiWordService.Result.Error -> error = r.message
                                }
                                loading = false
                            }
                        },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ZCyanDeep, disabledContainerColor = ZBorder),
                    ) {
                        if (loading) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("جارٍ التوليد...", fontWeight = FontWeight.Bold, color = Color.White)
                        } else {
                            Icon(Icons.Filled.AutoAwesome, null); Spacer(Modifier.width(8.dp))
                            Text("حفظ AI ذكي (ترجمة وتوليد جملة)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (vm.geminiApiKey.isBlank()) "يتطلب مفتاح Gemini API من الإعدادات" else "سيتم توليد الترجمة والمثال والنطق تلقائياً",
                        color = ZTextMuted, fontSize = 11.sp,
                    )
                } else {
                    Button(
                        onClick = {
                            if (english.isBlank()) { error = "اكتب الكلمة الإنجليزية"; return@Button }
                            vm.addWord(english, arabic, exampleEn, exampleAr)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ZIndigo),
                    ) {
                        Icon(Icons.Filled.Save, null); Spacer(Modifier.width(8.dp))
                        Text("حفظ يدوي", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TabButton(label: String, active: Boolean, modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = if (active) ZIndigo else Color.Transparent,
        onClick = onClick,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = if (active) Color.White else ZTextSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = if (active) Color.White else ZTextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DialogField(value: String, onChange: (String) -> Unit, placeholder: String, enabled: Boolean = true, minLines: Int = 1) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder, color = ZTextMuted, fontSize = 14.sp) },
        enabled = enabled,
        singleLine = minLines == 1,
        minLines = minLines,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = ZSurfaceVariant, unfocusedContainerColor = ZSurfaceVariant,
            disabledContainerColor = ZSurfaceVariant,
            focusedBorderColor = ZIndigo, unfocusedBorderColor = ZBorder,
            focusedTextColor = ZTextPrimary, unfocusedTextColor = ZTextPrimary,
        ),
    )
}

@Composable
private fun FilterChip(selected: Boolean, label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) ZIndigo else ZCard,
        onClick = onClick,
    ) {
        Text(label, color = if (selected) Color.White else ZTextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

@Composable
private fun WordCard(word: VocabWord, vm: AppViewModel) {
    var expanded by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val isForgotten = word.lapses >= 2 && word.stability < 7.0
    val isHard = word.difficulty >= 6.5 && word.totalReviews > 0

    // State color drives a soft gradient wash across the card field
    val stColor = when {
        isForgotten -> ZRose
        word.mastered -> ZEmerald
        word.state == com.zmastery.english.data.ReviewState.REVIEWING || isHard -> ZAmber
        else -> ZCyanDeep
    }
    val stateLabel = when (word.state) {
        com.zmastery.english.data.ReviewState.NEW -> ZCyanDeep
        com.zmastery.english.data.ReviewState.REVIEWING -> ZAmber
        com.zmastery.english.data.ReviewState.SAVED -> ZEmerald
    }
    val cardShape = RoundedCornerShape(18.dp)

    Surface(shape = cardShape, color = ZCard, shadowElevation = 5.dp, modifier = Modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
        Column(
            Modifier
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(stColor.copy(alpha = 0.16f), stColor.copy(alpha = 0.04f), Color.Transparent)
                    )
                )
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val tilePath = vm.mnemonicPath(word.id)
                if (tilePath != null) {
                    coil3.compose.AsyncImage(
                        model = java.io.File(tilePath),
                        contentDescription = word.english,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                com.zmastery.english.audio.AudioButton(
                    text = if (word.exampleEn.isNotBlank()) "${word.english}. ${word.exampleEn}" else word.english,
                    audioKey = "dict_${word.id}", accent = stColor, size = 40.dp, iconSize = 20.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(word.english, color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        if (word.mastered) Icon(Icons.Filled.Verified, null, tint = ZEmerald, modifier = Modifier.size(18.dp))
                    }
                    if (word.phonetic.isNotBlank()) Text(word.phonetic, color = stColor, fontSize = 12.sp)
                }
                Text(word.arabic, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatePill(word.state.label, stateLabel)
                if (isForgotten) StatePill("منسية", ZRose)
                else if (isHard) StatePill("صعبة", ZAmber)
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                Divider(color = ZBorder)
                Spacer(Modifier.height(12.dp))
                Text(word.exampleEn, color = ZTextSecondary, fontSize = 14.sp)
                Text(word.exampleAr, color = ZTextMuted, fontSize = 13.sp)
                if (word.mentalImage.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Image, null, tint = ZPurple, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(word.mentalImage, color = ZPurple, fontSize = 12.sp)
                    }
                }
                // ----- Mnemonic image (الرابط الذهني) -----
                Spacer(Modifier.height(12.dp))
                val mnemoPath = vm.mnemonicPath(word.id)
                if (mnemoPath != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        coil3.compose.AsyncImage(
                            model = java.io.File(mnemoPath),
                            contentDescription = word.english,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Link, null, tint = ZPurple, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("الرابط الذهني", color = ZPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("مرتبط ويظهر في المراجعة", color = ZTextMuted, fontSize = 10.sp)
                            Spacer(Modifier.height(8.dp))
                            TextButton(
                                onClick = { vm.clearMnemonic(word.id) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Icon(Icons.Filled.DeleteOutline, null, tint = ZRose, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("إزالة الصورة", color = ZRose, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Surface(shape = RoundedCornerShape(12.dp), color = ZSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LinkOff, null, tint = ZTextMuted, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "لا يوجد رابط ذهني — ولّده من زر «الروابط الذهنية»",
                                color = ZTextMuted, fontSize = 11.sp, lineHeight = 17.sp,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Memory strength bar (FSRS stability based)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Bolt, null, tint = ZEmerald, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("قوة الذاكرة", color = ZTextSecondary, fontSize = 11.sp)
                    Spacer(Modifier.width(8.dp))
                    LinearProgressIndicator(
                        progress = { word.strength },
                        modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(4.dp)),
                        color = ZEmerald, trackColor = ZBorder,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("${(word.strength * 100).toInt()}%", color = ZEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniStat("الاستقرار", if (word.stability >= 1) "${word.stability.toInt()}ي" else "<1ي")
                    MiniStat("الصعوبة", String.format("%.1f", word.difficulty))
                    MiniStat("المراجعات", "${word.totalReviews}")
                    MiniStat("التالي", if (word.dueInDays <= 0) "الآن" else "${word.dueInDays}ي")
                }
                Spacer(Modifier.height(14.dp))
                // Edit / delete actions
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { showEdit = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ZBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ZIndigo),
                    ) {
                        Icon(Icons.Filled.Edit, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp)); Text("تعديل", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = { showDelete = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ZRose.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ZRose),
                    ) {
                        Icon(Icons.Filled.DeleteOutline, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp)); Text("حذف", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    if (showEdit) {
        EditWordDialog(word, vm) { showEdit = false }
    }
    if (showDelete) {
        DeleteWordDialog(word, onConfirm = { vm.deleteWord(word.id); showDelete = false }, onDismiss = { showDelete = false })
    }
}

@Composable
private fun EditWordDialog(word: VocabWord, vm: AppViewModel, onDismiss: () -> Unit) {
    var english by remember { mutableStateOf(word.english) }
    var arabic by remember { mutableStateOf(if (word.arabic == "—") "" else word.arabic) }
    var exampleEn by remember { mutableStateOf(word.exampleEn) }
    var exampleAr by remember { mutableStateOf(word.exampleAr) }
    var phonetic by remember { mutableStateOf(word.phonetic) }
    var mentalImage by remember { mutableStateOf(word.mentalImage) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(26.dp), color = ZCard, shadowElevation = 8.dp) {
            Column(
                Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Edit, null, tint = ZIndigo)
                    Spacer(Modifier.width(8.dp))
                    Text("تعديل الكلمة", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 19.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, null, tint = ZTextMuted)
                    }
                }
                Spacer(Modifier.height(16.dp))
                DialogField(english, { english = it; error = null }, "الكلمة الإنجليزية")
                Spacer(Modifier.height(12.dp))
                DialogField(arabic, { arabic = it }, "الترجمة العربية")
                Spacer(Modifier.height(12.dp))
                DialogField(phonetic, { phonetic = it }, "النطق (اختياري)")
                Spacer(Modifier.height(12.dp))
                DialogField(exampleEn, { exampleEn = it }, "المثال بالإنجليزية", minLines = 2)
                Spacer(Modifier.height(12.dp))
                DialogField(exampleAr, { exampleAr = it }, "ترجمة المثال", minLines = 2)
                Spacer(Modifier.height(12.dp))
                DialogField(mentalImage, { mentalImage = it }, "الصورة الذهنية (اختياري)")

                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ErrorOutline, null, tint = ZRose, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(it, color = ZRose, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        if (english.isBlank()) { error = "اكتب الكلمة الإنجليزية"; return@Button }
                        vm.updateWord(word.id, english, arabic, exampleEn, exampleAr, phonetic, mentalImage)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ZIndigo),
                ) {
                    Icon(Icons.Filled.Save, null); Spacer(Modifier.width(8.dp))
                    Text("حفظ التعديلات", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun DeleteWordDialog(word: VocabWord, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(26.dp), color = ZCard, shadowElevation = 8.dp) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(60.dp).clip(RoundedCornerShape(20.dp)).background(ZRose.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.DeleteOutline, null, tint = ZRose, modifier = Modifier.size(32.dp)) }
                Spacer(Modifier.height(14.dp))
                Text("حذف الكلمة؟", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "سيتم حذف \"${word.english}\" مع المثال الخاص بها نهائياً من القاموس.",
                    color = ZTextSecondary, fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ZBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ZTextSecondary),
                    ) { Text("إلغاء", fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ZRose),
                    ) {
                        Icon(Icons.Filled.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("حذف", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatePill(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.15f)) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Surface(shape = RoundedCornerShape(10.dp), color = ZSurfaceVariant) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = ZCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(label, color = ZTextMuted, fontSize = 10.sp)
        }
    }
}
