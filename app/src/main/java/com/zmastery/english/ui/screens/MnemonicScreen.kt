package com.zmastery.english.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.zmastery.english.data.MnemonicArtStyle
import com.zmastery.english.data.MnemonicModel
import com.zmastery.english.data.MnemonicPersona
import com.zmastery.english.data.MnemonicPrompt
import com.zmastery.english.data.MnemonicSpec
import com.zmastery.english.data.MnemonicStore
import com.zmastery.english.data.VocabWord
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel
import kotlinx.coroutines.launch

/**
 * Mnemonic Studio — the "الرابط الذهني" pipeline.
 *
 *  Step 0 · Setup   : batch size, art style, recurring character, target model.
 *  Step 1 · Prompt  : exact grid map + copyable precision prompt.
 *  Step 2 · Upload  : pick the composite sheet, preview the slices.
 *  Step 3 · Done    : tiles attached to each word, ready for review cards.
 *
 * Only approved dictionary words are eligible; pending lesson words never
 * appear here. Batching keeps the flow constant-cost regardless of dictionary
 * size — 500 words is just 32 sheets, each handled identically.
 */
@Composable
fun MnemonicScreen(vm: AppViewModel, onBack: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // Upload / slice state
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var previews by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var loadingPreview by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<MnemonicStore.SliceResult?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pickedUri = uri
            result = null
            loadingPreview = true
            scope.launch {
                previews = MnemonicStore.previewSlices(ctx, uri, vm.mnemonicBatch.size, vm.mnemonicSpec)
                loadingPreview = false
            }
        }
    }

    fun reset() {
        pickedUri = null
        previews = emptyList()
        result = null
        step = 0
    }

    Column(Modifier.fillMaxSize()) {
        // ---- Header ----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { if (step == 0) onBack() else step-- }) {
                Icon(Icons.Filled.ArrowForward, "رجوع", tint = ZTextPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (step) {
                        0 -> "استوديو الروابط الذهنية"
                        1 -> "الخطوة 1 · المطالبة"
                        2 -> "الخطوة 2 · رفع الصورة"
                        else -> "تم الربط"
                    },
                    color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 19.sp,
                )
                Text(
                    when (step) {
                        0 -> "${vm.mnemonicReadyCount} جاهزة · ${vm.mnemonicMissingCount} بحاجة لصورة"
                        1 -> MnemonicPrompt.summary(vm.mnemonicSpec)
                        2 -> "الشبكة ${vm.mnemonicSpec.cols}×${vm.mnemonicSpec.rows} — سيُقص ${vm.mnemonicBatch.size} تايل"
                        else -> vm.mnemonicMessage ?: ""
                    },
                    color = ZTextSecondary, fontSize = 11.sp, maxLines = 2,
                )
            }
        }
        StepRail(step)

        when (step) {
            0 -> SetupStep(vm) { n ->
                if (n > 0) { reset(); step = 1 }
            }
            1 -> PromptStep(vm) { step = 2 }
            2 -> UploadStep(
                vm = vm,
                pickedUri = pickedUri,
                previews = previews,
                loadingPreview = loadingPreview,
                result = result,
                onPick = { picker.launch("image/*") },
                onConfirm = {
                    val u = pickedUri ?: return@UploadStep
                    vm.sliceMnemonicSheet(u) { r ->
                        result = r
                        if (r.success) step = 3
                    }
                },
            )
            else -> DoneStep(vm, onAnother = { reset() }, onFinish = onBack)
        }
    }
}

/* ─────────────────────────── shared chrome ─────────────────────────── */

@Composable
private fun StepRail(step: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        (0..3).forEach { s ->
            Box(
                Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp))
                    .background(if (s <= step) ZIndigo else ZBorder)
            )
        }
    }
}

@Composable
private fun StudioCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) { Column(Modifier.padding(16.dp), content = content) }
}

@Composable
private fun CardTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun PrimaryButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ZIndigo, disabledContainerColor = ZBorder),
    ) {
        Icon(icon, null); Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

/* ─────────────────────────── step 0 · setup ─────────────────────────── */

@Composable
private fun SetupStep(vm: AppViewModel, onStart: (Int) -> Unit) {
    val missing = vm.mnemonicMissingCount
    val spec = remember(vm.mnemonicBatchSize, missing) {
        MnemonicSpec.forCount(vm.mnemonicBatchSize.coerceAtMost(missing.coerceAtLeast(1)))
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Hero
        Surface(
            shape = RoundedCornerShape(22.dp), color = Color.Transparent,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.background(Brush.linearGradient(listOf(ZIndigo, ZPurple))).padding(20.dp)
            ) {
                Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.22f)) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Bolt, null, tint = Color.White, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("تقنية الذاكرة البصرية", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("الروابط الذهنية", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(6.dp))
                Text(
                    "ولّد صورة واحدة مركّبة تدمج كل كلمة مع مثالها، ثم يقصّها التطبيق تلقائياً " +
                        "ويربط كل جزء بكلمته لتظهر في بطاقات المراجعة.",
                    color = Color.White.copy(alpha = 0.93f), fontSize = 13.sp, lineHeight = 21.sp,
                )
            }
        }

        // Stats
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatBox(Modifier.weight(1f), Icons.Filled.Link, "${vm.mnemonicReadyCount}", "روابط جاهزة", ZEmerald)
            StatBox(Modifier.weight(1f), Icons.Filled.HourglassEmpty, "$missing", "بحاجة لرابط", ZAmber)
            StatBox(Modifier.weight(1f), Icons.Filled.Storage, vm.mnemonicDiskLabel, "المساحة", ZCyanDeep)
        }

        if (missing == 0) {
            StudioCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = ZEmerald, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (vm.totalWords == 0) "القاموس فارغ" else "كل الكلمات لديها روابط ذهنية",
                            color = ZTextPrimary, fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (vm.totalWords == 0) "أضف كلمات للقاموس أولاً"
                            else "أضف كلمات جديدة أو أعد توليد صورة من القاموس",
                            color = ZTextSecondary, fontSize = 12.sp,
                        )
                    }
                }
            }
        } else {
            // Batch size
            StudioCard {
                CardTitle(Icons.Filled.GridView, "عدد الكلمات في هذه الدفعة", ZIndigo)
                Spacer(Modifier.height(4.dp))
                Text(
                    "يُنصح بـ 15–20 كلمة لكل صورة — أعلى دقة مع أقل عدد صور",
                    color = ZTextMuted, fontSize = 11.sp,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${spec.count}", color = ZIndigo, fontSize = 28.sp, fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("كلمة", color = ZTextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Surface(shape = RoundedCornerShape(10.dp), color = ZSurfaceVariant) {
                        Text(
                            "شبكة ${spec.cols}×${spec.rows}",
                            color = ZTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
                val maxBatch = minOf(MnemonicSpec.MAX_BATCH, missing).coerceAtLeast(MnemonicSpec.MIN_BATCH)
                Slider(
                    value = vm.mnemonicBatchSize.coerceIn(MnemonicSpec.MIN_BATCH, maxBatch).toFloat(),
                    onValueChange = { vm.mnemonicBatchSize = it.toInt() },
                    valueRange = MnemonicSpec.MIN_BATCH.toFloat()..maxBatch.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = ZIndigo, activeTrackColor = ZIndigo, inactiveTrackColor = ZBorder),
                )
                Text(
                    "مقاس الصورة الناتجة: ${spec.canvasW}×${spec.canvasH}px · كل خلية ${spec.cell}px مربّعة",
                    color = ZTextMuted, fontSize = 11.sp,
                )
            }

            // Art style
            StudioCard {
                CardTitle(Icons.Filled.Palette, "نمط الرسم", ZAmber)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MnemonicArtStyle.values().forEach { s ->
                        ChoiceChip(s.label, s == vm.mnemonicStyle, ZAmber) {
                            vm.mnemonicStyle = s; vm.refreshMnemonicPrompt(); vm.persist()
                        }
                    }
                }
            }

            // Persona
            StudioCard {
                CardTitle(Icons.Filled.EmojiPeople, "شخصية متكرّرة", ZCyanDeep)
                Spacer(Modifier.height(4.dp))
                Text(
                    "ظهور نفس الشخصية في كل الصور يقوّي الترابط الذهني",
                    color = ZTextMuted, fontSize = 11.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MnemonicPersona.values().forEach { p ->
                        ChoiceChip(p.label, p == vm.mnemonicPersona, ZCyanDeep) {
                            vm.mnemonicPersona = p; vm.refreshMnemonicPrompt(); vm.persist()
                        }
                    }
                }
            }

            // Model
            StudioCard {
                CardTitle(Icons.Filled.AutoAwesome, "مولّد الصور المستهدف", ZPurple)
                Spacer(Modifier.height(10.dp))
                MnemonicModel.values().forEach { m ->
                    val active = m == vm.mnemonicModel
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (active) ZPurple.copy(alpha = 0.14f) else Color.Transparent,
                        onClick = { vm.mnemonicModel = m; vm.refreshMnemonicPrompt(); vm.persist() },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (active) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                                null, tint = if (active) ZPurple else ZTextMuted, modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.label, color = ZTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(m.hint, color = ZTextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
                Divider(color = ZBorder, modifier = Modifier.padding(vertical = 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("أرقام دلالية في الصورة", color = ZTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("يرسم رقم كل خلية في الهامش — يساعد على التحقق من الترتيب", color = ZTextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = vm.mnemonicNumbering,
                        onCheckedChange = { vm.mnemonicNumbering = it; vm.refreshMnemonicPrompt(); vm.persist() },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ZIndigo),
                    )
                }
            }

            // How it works
            StudioCard {
                CardTitle(Icons.Filled.Route, "كيف تعمل؟", ZTextSecondary)
                Spacer(Modifier.height(10.dp))
                HowRow("1", Icons.Filled.EditNote, "ولّد المطالبة الدقيقة بمقاسات مضبوطة", ZIndigo)
                HowRow("2", Icons.Filled.Image, "الصقها في مولّد الصور واحصل على صورة واحدة", ZAmber)
                HowRow("3", Icons.Filled.ContentCut, "ارفعها — يقصّها التطبيق لكل كلمة تلقائياً", ZPurple)
                HowRow("4", Icons.Filled.Style, "تظهر الصور في بطاقات المراجعة فوراً", ZEmerald)
            }

            PrimaryButton("ولّد المطالبة لـ ${spec.count} كلمة", Icons.Filled.AutoAwesome) {
                onStart(vm.startMnemonicBatch(spec.count))
            }
        }

        // NOTE: no "delete all mnemonics" action here on purpose — it is a
        // destructive, easily mis-tapped bulk action. Images are removed one at
        // a time from each word's card in the dictionary.
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
private fun StatBox(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, accent: Color) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = ZCard, shadowElevation = 3.dp) {
        Column(Modifier.padding(12.dp)) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = accent, modifier = Modifier.size(17.dp)) }
            Spacer(Modifier.height(8.dp))
            Text(value, color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1)
            Text(label, color = ZTextSecondary, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ChoiceChip(label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (active) accent else ZSurfaceVariant,
        onClick = onClick,
    ) {
        Text(
            label,
            color = if (active) Color.White else ZTextSecondary,
            fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun HowRow(num: String, icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, accent: Color) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(9.dp)).background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Text(num, color = accent, fontWeight = FontWeight.Black, fontSize = 12.sp) }
        Spacer(Modifier.width(10.dp))
        Icon(icon, null, tint = accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = ZTextSecondary, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
    }
}

/* ─────────────────────────── step 1 · prompt ─────────────────────────── */

@Composable
private fun PromptStep(vm: AppViewModel, onNext: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val spec = vm.mnemonicSpec

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Grid map
        StudioCard {
            CardTitle(Icons.Filled.GridView, "مخطط الشبكة (${spec.cols}×${spec.rows})", ZIndigo)
            Spacer(Modifier.height(4.dp))
            Text(
                "${spec.canvasW}×${spec.canvasH}px · خلية ${spec.cell}px · هامش ${spec.gutter}px أبيض",
                color = ZTextMuted, fontSize = 11.sp,
            )
            Spacer(Modifier.height(12.dp))
            // Visual grid preview, filled left→right / top→bottom
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in 0 until spec.rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        for (c in 0 until spec.cols) {
                            val i = r * spec.cols + c
                            val w = vm.mnemonicBatch.getOrNull(i)
                            Surface(
                                modifier = Modifier.weight(1f).aspectRatio(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = if (w == null) ZSurfaceVariant.copy(alpha = 0.4f) else ZSurfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp, if (w == null) ZBorder.copy(alpha = 0.4f) else ZIndigo.copy(alpha = 0.3f),
                                ),
                            ) {
                                Column(
                                    Modifier.fillMaxSize().padding(3.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    if (w == null) {
                                        Text("فارغ", color = ZTextMuted, fontSize = 8.sp)
                                    } else {
                                        Text("${i + 1}", color = ZIndigo, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                        Text(
                                            w.english, color = ZTextPrimary, fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold, maxLines = 1,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, null, tint = ZTextMuted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "الترتيب من اليسار لليمين ثم للأسفل — سيقصّها التطبيق بنفس الترتيب",
                    color = ZTextMuted, fontSize = 11.sp, lineHeight = 17.sp,
                )
            }
        }

        // Prompt text
        StudioCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardTitle(Icons.Filled.Description, "نص المطالبة", ZPurple)
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (copied) ZEmerald.copy(alpha = 0.16f) else ZIndigo.copy(alpha = 0.14f),
                    onClick = {
                        clipboard.setText(AnnotatedString(vm.mnemonicPromptText))
                        copied = true
                    },
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                            null, tint = if (copied) ZEmerald else ZIndigo, modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (copied) "تم النسخ" else "نسخ",
                            color = if (copied) ZEmerald else ZIndigo,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(14.dp), color = ZSurfaceVariant,
                modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
            ) {
                Text(
                    vm.mnemonicPromptText,
                    color = ZTextSecondary, fontSize = 10.sp, lineHeight = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${vm.mnemonicPromptText.length} حرفاً · انسخها والصقها في مولّد الصور",
                color = ZTextMuted, fontSize = 10.sp,
            )
        }

        // Word list in this batch
        StudioCard {
            CardTitle(Icons.Filled.FormatListNumbered, "كلمات الدفعة (${vm.mnemonicBatch.size})", ZCyanDeep)
            Spacer(Modifier.height(10.dp))
            vm.mnemonicBatch.forEachIndexed { i, w -> BatchWordRow(i, w) }
        }

        PrimaryButton("ولّدت الصورة؟ التالي", Icons.Filled.NavigateBefore) { onNext() }
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
private fun BatchWordRow(index: Int, w: VocabWord) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(ZIndigo.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Text("${index + 1}", color = ZIndigo, fontWeight = FontWeight.Black, fontSize = 11.sp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(w.english, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (w.exampleEn.isNotBlank()) {
                Text(w.exampleEn, color = ZTextMuted, fontSize = 10.sp, maxLines = 1, lineHeight = 15.sp)
            }
        }
        Text(w.arabic, color = ZTextSecondary, fontSize = 12.sp)
    }
}

/* ─────────────────────────── step 2 · upload ─────────────────────────── */

@Composable
private fun UploadStep(
    vm: AppViewModel,
    pickedUri: Uri?,
    previews: List<Bitmap>,
    loadingPreview: Boolean,
    result: MnemonicStore.SliceResult?,
    onPick: () -> Unit,
    onConfirm: () -> Unit,
) {
    val spec = vm.mnemonicSpec

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Drop zone
        StudioCard {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = ZSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
                onClick = onPick,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        if (pickedUri == null) Icons.Filled.CloudUpload else Icons.Filled.CheckCircle,
                        null,
                        tint = if (pickedUri == null) ZIndigo else ZEmerald,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (pickedUri == null) "ارفع الصورة المركّبة" else "تم اختيار الصورة",
                        color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (pickedUri == null) "PNG أو JPG — بشبكة ${spec.cols}×${spec.rows} كما في المخطط"
                        else "اضغط لاختيار صورة أخرى",
                        color = ZTextMuted, fontSize = 12.sp, textAlign = TextAlign.Center,
                    )
                }
            }
            if (pickedUri != null) {
                Spacer(Modifier.height(12.dp))
                AsyncImage(
                    model = pickedUri,
                    contentDescription = "الصورة المركّبة",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(14.dp)),
                )
            }
        }

        // Slice preview — verify alignment BEFORE committing
        if (loadingPreview) {
            StudioCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = ZIndigo, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("جاري تحليل الصورة وتجربة القص…", color = ZTextSecondary, fontSize = 13.sp)
                }
            }
        } else if (previews.isNotEmpty()) {
            StudioCard {
                CardTitle(Icons.Filled.ContentCut, "معاينة القص", ZAmber)
                Spacer(Modifier.height(4.dp))
                Text(
                    "تحقّق أن كل صورة تطابق كلمتها قبل الربط النهائي",
                    color = ZTextMuted, fontSize = 11.sp,
                )
                Spacer(Modifier.height(12.dp))
                previews.forEachIndexed { i, bmp ->
                    val w = vm.mnemonicBatch.getOrNull(i)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = w?.english,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(62.dp).clip(RoundedCornerShape(12.dp))
                                .border(1.dp, ZBorder, RoundedCornerShape(12.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Box(
                            Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(ZIndigo.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) { Text("${i + 1}", color = ZIndigo, fontWeight = FontWeight.Black, fontSize = 10.sp) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(w?.english ?: "—", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(w?.arabic ?: "", color = ZTextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        } else {
            // Target words list before an upload exists
            StudioCard {
                CardTitle(Icons.Filled.FormatListNumbered, "سيتم ربط ${vm.mnemonicBatch.size} كلمة", ZCyanDeep)
                Spacer(Modifier.height(10.dp))
                vm.mnemonicBatch.forEachIndexed { i, w -> BatchWordRow(i, w) }
            }
        }

        result?.let { r ->
            if (!r.success) {
                Surface(shape = RoundedCornerShape(14.dp), color = ZRose.copy(alpha = 0.14f), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ErrorOutline, null, tint = ZRose, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(r.message, color = ZRose, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (vm.isSlicing) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(color = ZIndigo, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("جاري القص والربط…", color = ZTextSecondary, fontSize = 13.sp)
            }
        } else {
            PrimaryButton(
                if (pickedUri == null) "اختر الصورة" else "اقصص واربط ${vm.mnemonicBatch.size} كلمة",
                if (pickedUri == null) Icons.Filled.PhotoLibrary else Icons.Filled.ContentCut,
                enabled = !vm.isSlicing,
            ) { if (pickedUri == null) onPick() else onConfirm() }
        }
        Spacer(Modifier.height(90.dp))
    }
}

/* ─────────────────────────── step 3 · done ─────────────────────────── */

@Composable
private fun DoneStep(vm: AppViewModel, onAnother: () -> Unit, onFinish: () -> Unit) {
    val remaining = vm.mnemonicMissingCount
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(visible, enter = fadeIn(tween(420)), exit = fadeOut()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.size(100.dp).clip(RoundedCornerShape(50.dp))
                        .background(Brush.linearGradient(listOf(ZEmerald, ZCyanDeep))),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Link, null, tint = Color.White, modifier = Modifier.size(54.dp)) }
                Spacer(Modifier.height(18.dp))
                Text("تم ربط الصور!", color = ZTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(6.dp))
                Text(
                    vm.mnemonicMessage ?: "الصور جاهزة في بطاقات المراجعة",
                    color = ZTextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        // Gallery of what was just created
        StudioCard {
            CardTitle(Icons.Filled.PhotoLibrary, "الروابط الجديدة", ZEmerald)
            Spacer(Modifier.height(12.dp))
            vm.mnemonicBatch.chunked(3).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { w ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            val path = vm.mnemonicPath(w.id)
                            if (path != null) {
                                AsyncImage(
                                    model = java.io.File(path),
                                    contentDescription = w.english,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                                )
                            } else {
                                Box(
                                    Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                                        .background(ZSurfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Filled.BrokenImage, null, tint = ZTextMuted, modifier = Modifier.size(20.dp)) }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(w.english, color = ZTextSecondary, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (remaining > 0) {
            PrimaryButton("دفعة أخرى ($remaining متبقية)", Icons.Filled.Refresh) { onAnother() }
            Spacer(Modifier.height(10.dp))
        }
        OutlinedButton(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ZBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ZTextPrimary),
        ) { Text("العودة للقاموس", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        Spacer(Modifier.height(90.dp))
    }
}
