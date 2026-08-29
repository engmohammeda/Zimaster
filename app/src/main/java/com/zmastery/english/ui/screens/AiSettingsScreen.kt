package com.zmastery.english.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.data.*
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel

@Composable
fun AiSettingsScreen(vm: AppViewModel) {
    var editing by remember { mutableStateOf<String?>(null) }
    if (editing != null) {
        val agent = vm.aiAgents.firstOrNull { it.id == editing }
        if (agent != null) {
            AgentEditor(vm, agent) { editing = null }
            return
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { AiHeader() }
        item { ApiKeysCard(vm) }
        item { FetchModelsCard(vm) }
        item {
            Text("عملاء الذكاء الاصطناعي", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 17.sp, modifier = Modifier.padding(top = 8.dp))
            Text("لكل ميزة إعداداتها: النموذج · الشخصية · الصوت · الأسلوب · المطالبة", color = ZTextSecondary, fontSize = 12.sp)
        }
        items(vm.aiAgents, key = { it.id }) { agent ->
            AgentRow(vm, agent) { editing = agent.id }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun AiHeader() {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(ZIndigo, ZPurple))).padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Text("مركز الذكاء الاصطناعي", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(8.dp))
            Text("أدر النماذج والمفاتيح والأصوات وشخصيات ومطالبات كل ميزة من مكان واحد", color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun ApiKeysCard(vm: AppViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ApiKeyEntry?>(null) }

    Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 5.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Key, null, tint = ZAmber)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("مفاتيح API", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        if (vm.apiKeys.isEmpty()) "لا توجد مفاتيح بعد"
                        else "${vm.apiKeys.size} مفتاح · النشط: ${vm.activeKey?.label ?: "—"}",
                        color = ZTextSecondary, fontSize = 11.sp,
                    )
                }
                TextButton(onClick = { showAdd = true }) {
                    Icon(Icons.Filled.Add, null, tint = ZIndigo, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp)); Text("إضافة", color = ZIndigo)
                }
            }
            Spacer(Modifier.height(8.dp))

            vm.apiKeys.forEach { key ->
                val busy = vm.verifyingKeyId == key.id
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (key.active) ZIndigo.copy(alpha = 0.10f) else ZSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    onClick = { vm.activateKey(key.id) },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (key.active) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                null, tint = if (key.active) ZEmerald else ZTextMuted, modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(key.label, color = ZTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    "${key.providerEnum.label} · ${key.maskedKey}",
                                    color = ZTextSecondary, fontSize = 11.sp,
                                )
                            }
                            if (busy) {
                                CircularProgressIndicator(color = ZIndigo, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            } else {
                                IconButton(onClick = { vm.verifyKey(key.id) }) {
                                    Icon(Icons.Filled.NetworkCheck, "تحقّق", tint = ZCyanDeep, modifier = Modifier.size(18.dp))
                                }
                            }
                            IconButton(onClick = { pendingDelete = key }) {
                                Icon(Icons.Filled.DeleteOutline, "حذف", tint = ZRose, modifier = Modifier.size(18.dp))
                            }
                        }
                        // Verification badge
                        if (key.verified || key.hasError) {
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = (if (key.verified) ZEmerald else ZRose).copy(alpha = 0.14f),
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        if (key.verified) Icons.Filled.VerifiedUser else Icons.Filled.ErrorOutline,
                                        null, tint = if (key.verified) ZEmerald else ZRose,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        if (key.verified) "تم التحقق — يعمل" else key.status,
                                        color = if (key.verified) ZEmerald else ZRose,
                                        fontSize = 10.sp, lineHeight = 15.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (vm.apiKeys.isEmpty()) {
                Surface(shape = RoundedCornerShape(12.dp), color = ZAmber.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, null, tint = ZAmber, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "أضف مفتاحاً لتفعيل كل ميزات الذكاء الاصطناعي. يدعم التطبيق Gemini وأي مزوّد متوافق مع OpenAI.",
                            color = ZTextSecondary, fontSize = 11.sp, lineHeight = 17.sp,
                        )
                    }
                }
            }

            vm.keyMessage?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Text(msg, color = ZTextMuted, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
    }

    if (showAdd) AddKeyDialog(vm) { showAdd = false }

    pendingDelete?.let { key ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = ZSurface,
            icon = { Icon(Icons.Filled.Warning, null, tint = ZRose) },
            title = { Text("حذف المفتاح؟", color = ZTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "سيتم حذف «${key.label}» نهائياً من هذا الجهاز.",
                        color = ZTextSecondary, fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = ZSurfaceVariant) {
                        Text(
                            "${key.providerEnum.label} · ${key.maskedKey}",
                            color = ZTextMuted, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    if (key.active && vm.apiKeys.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "هذا هو المفتاح النشط — سيتم تفعيل مفتاح آخر تلقائياً.",
                            color = ZAmber, fontSize = 11.sp, lineHeight = 17.sp,
                        )
                    } else if (key.active) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "هذا مفتاحك الوحيد — ستتوقّف ميزات الذكاء الاصطناعي بعد حذفه.",
                            color = ZRose, fontSize = 11.sp, lineHeight = 17.sp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.removeKey(key.id); pendingDelete = null }) {
                    Text("نعم، احذف", color = ZRose, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("إلغاء", color = ZTextSecondary) }
            },
        )
    }
}

/** Add-key sheet with provider presets + custom endpoint support. */
@Composable
private fun AddKeyDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf("") }
    var raw by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf(AiProvider.GEMINI) }
    var baseUrl by remember { mutableStateOf("") }
    var touchedProvider by remember { mutableStateOf(false) }

    // Auto-detect the provider from the key shape until the user overrides it.
    LaunchedEffect(raw) {
        if (!touchedProvider && raw.length > 6) provider = AiProvider.detect(raw)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ZSurface,
        icon = { Icon(Icons.Filled.Key, null, tint = ZIndigo) },
        title = { Text("إضافة مفتاح API", color = ZTextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("المزوّد", color = ZTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                AiProvider.values().forEach { p ->
                    val active = p == provider
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (active) ZIndigo.copy(alpha = 0.13f) else Color.Transparent,
                        onClick = { provider = p; touchedProvider = true },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (active) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                                null, tint = if (active) ZIndigo else ZTextMuted, modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(p.label, color = ZTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    if (p.freeTier) {
                                        Spacer(Modifier.width(8.dp))
                                        Surface(shape = RoundedCornerShape(8.dp), color = ZEmerald.copy(alpha = 0.16f)) {
                                            Text(
                                                "مجاني", color = ZEmerald, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            )
                                        }
                                    }
                                }
                                Text(p.hint, color = ZTextMuted, fontSize = 10.sp, lineHeight = 15.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = raw, onValueChange = { raw = it },
                    label = { Text("المفتاح", color = ZTextMuted, fontSize = 12.sp) },
                    placeholder = { Text(provider.keyPrefix.ifBlank { "sk-..." } + "…", color = ZTextMuted) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp), colors = fieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text("اسم وصفي (اختياري)", color = ZTextMuted, fontSize = 12.sp) },
                    placeholder = { Text(provider.label, color = ZTextMuted) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp), colors = fieldColors(),
                )
                if (provider == AiProvider.CUSTOM) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = baseUrl, onValueChange = { baseUrl = it },
                        label = { Text("رابط الواجهة (Base URL)", color = ZTextMuted, fontSize = 12.sp) },
                        placeholder = { Text("https://host/v1", color = ZTextMuted) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp), colors = fieldColors(),
                    )
                }
                if (provider.keyUrl.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = ZSurfaceVariant) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.OpenInNew, null, tint = ZCyanDeep, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "احصل على مفتاحك من:\n${provider.keyUrl}",
                                color = ZTextMuted, fontSize = 10.sp, lineHeight = 15.sp,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = raw.isNotBlank() && (provider != AiProvider.CUSTOM || baseUrl.isNotBlank()),
                onClick = { vm.addApiKey(label, raw, provider, baseUrl); onDismiss() },
            ) { Text("حفظ وتحقّق", color = ZEmerald, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = ZTextSecondary) } },
    )
}

@Composable
private fun FetchModelsCard(vm: AppViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val grouped = vm.modelsGrouped()
    val total = vm.aiModels.size
    val fetchedCount = vm.aiModels.count { it.fetched }

    Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 5.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CloudSync, null, tint = ZCyanDeep)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("النماذج المتاحة", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        if (fetchedCount > 0)
                            "$total نموذج محفوظ على الجهاز · ${vm.freeModelCount} ضمن الحصة المجانية"
                        else "$total نموذج مبدئي · اجلب القائمة مرة واحدة وتُحفظ تلقائياً",
                        color = ZTextSecondary, fontSize = 11.sp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { vm.fetchModels() },
                enabled = !vm.isFetchingModels,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ZCyanDeep, disabledContainerColor = ZBorder),
            ) {
                if (vm.isFetchingModels) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("جارٍ جلب كل النماذج...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Filled.Download, null); Spacer(Modifier.width(8.dp))
                    Text("جلب كل النماذج (v1beta + v1alpha)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "يجلب كل نموذج يسمح به مفتاحك دون استثناء — بما فيها التجريبية والمعاينة (TTS · Live · صور · فيديو).",
                color = ZTextMuted, fontSize = 10.sp, lineHeight = 16.sp,
            )
            vm.fetchModelsMessage?.let {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = (if (vm.fetchModelsFailed) ZRose else ZEmerald).copy(alpha = 0.13f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (vm.fetchModelsFailed) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
                            null, tint = if (vm.fetchModelsFailed) ZRose else ZEmerald, modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                it, color = if (vm.fetchModelsFailed) ZRose else ZEmerald,
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp,
                            )
                            vm.fetchModelsDetail?.takeIf { d -> d.isNotBlank() }?.let { d ->
                                Text(d, color = ZTextMuted, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            // Free-only filter
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MoneyOff, null, tint = ZEmerald, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("المجانية فقط", color = ZTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("أظهر النماذج التي لها حصة مجانية موثّقة", color = ZTextSecondary, fontSize = 10.sp)
                }
                Switch(
                    checked = vm.showFreeModelsOnly,
                    onCheckedChange = { vm.setShowFreeModelsOnly(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ZEmerald),
                )
            }

            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp), color = ZSurfaceVariant,
                modifier = Modifier.fillMaxWidth(), onClick = { expanded = !expanded },
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FormatListBulleted, null, tint = ZIndigo, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (expanded) "إخفاء قائمة النماذج" else "عرض كل النماذج (${grouped.sumOf { it.second.size }})",
                        color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        null, tint = ZTextMuted,
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                grouped.forEach { (kind, list) ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(kindColor(kind)))
                        Spacer(Modifier.width(8.dp))
                        Text(kind.label, color = ZTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.width(8.dp))
                        Text("(${list.size})", color = ZTextMuted, fontSize = 11.sp)
                    }
                    list.forEach { m -> ModelInfoRow(m) }
                }
                if (grouped.isEmpty()) {
                    Text(
                        "لا توجد نماذج مطابقة — أوقف فلتر «المجانية فقط» أو اجلب القائمة.",
                        color = ZTextMuted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}

/** One model row with id, capabilities and its documented free-tier limits. */
@Composable
private fun ModelInfoRow(m: AiModel) {
    var open by remember { mutableStateOf(false) }
    val quota = GeminiQuotas.forModel(m.id)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ZSurfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        onClick = { open = !open },
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    m.displayName, color = ZTextPrimary, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1,
                )
                if (m.isPreview) {
                    TinyTag("تجريبي", ZAmber)
                    Spacer(Modifier.width(4.dp))
                }
                when {
                    quota == null -> TinyTag("غير موثّق", ZTextMuted)
                    quota.free -> TinyTag("مجاني", ZEmerald)
                    else -> TinyTag("مدفوع", ZRose)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(m.id, color = ZTextMuted, fontSize = 9.sp, maxLines = 1)
            if (quota != null && quota.free) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (quota.rpm > 0) TinyTag(quota.rpmLabel, ZCyanDeep)
                    if (quota.rpd > 0) TinyTag(quota.rpdLabel, ZIndigo)
                    if (quota.tpm > 0) TinyTag(quota.tpmLabel, ZPurple)
                }
            }
            if (open) {
                Spacer(Modifier.height(8.dp))
                if (m.description.isNotBlank()) {
                    Text(m.description, color = ZTextSecondary, fontSize = 10.sp, lineHeight = 16.sp)
                    Spacer(Modifier.height(4.dp))
                }
                if (m.tokenLabel.isNotBlank()) {
                    Text(m.tokenLabel, color = ZTextMuted, fontSize = 10.sp)
                }
                if (m.apiVersions.isNotEmpty()) {
                    Text("الإصدار: ${m.apiVersions.joinToString(" · ")}", color = ZTextMuted, fontSize = 9.sp)
                }
                if (m.methods.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(m.methods.joinToString(" · "), color = ZTextMuted, fontSize = 9.sp, lineHeight = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun TinyTag(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.15f)) {
        Text(
            text, color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

private fun kindColor(kind: ModelKind): Color = when (kind) {
    ModelKind.TEXT -> ZIndigo
    ModelKind.TTS -> ZCyanDeep
    ModelKind.LIVE -> ZPurple
    ModelKind.IMAGE -> ZAmber
    ModelKind.VIDEO -> ZRose
    ModelKind.EMBEDDING -> ZEmerald
    ModelKind.OTHER -> ZTextMuted
}

@Composable
private fun AgentRow(vm: AppViewModel, agent: AiAgent, onEdit: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = ZCard, shadowElevation = 5.dp, modifier = Modifier.fillMaxWidth(), onClick = onEdit) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(agentColor(agent.kind).copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(agentIcon(agent.icon), null, tint = agentColor(agent.kind), modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(agent.feature, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(agent.description, color = ZTextSecondary, fontSize = 11.sp, maxLines = 2)
                }
                Icon(Icons.Filled.Tune, null, tint = ZTextMuted)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(Icons.Filled.Memory, vm.modelName(agent.modelId))
                if (agent.kind.usesVoice) Chip(Icons.Filled.RecordVoiceOver, vm.voiceName(agent.voiceId))
            }
        }
    }
}

@Composable
private fun Chip(icon: ImageVector, label: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = ZSurfaceVariant) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = ZTextSecondary, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = ZTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AgentEditor(vm: AppViewModel, agent: AiAgent, onBack: () -> Unit) {
    var character by remember { mutableStateOf(agent.character) }
    var style by remember { mutableStateOf(agent.style) }
    var prompt by remember { mutableStateOf(agent.prompt) }
    var modelId by remember { mutableStateOf(agent.modelId) }
    var voiceId by remember { mutableStateOf(agent.voiceId) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack) { Icon(Icons.Filled.ArrowForward, null, tint = ZIndigo); Spacer(Modifier.width(8.dp)); Text("رجوع", color = ZIndigo) }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(ZIndigo, ZPurple))).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(agentIcon(agent.icon), null, tint = Color.White, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(agent.feature, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text(agent.kind.label, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                }
            }
        }

        // ---- Model selector: ONLY the kinds this persona actually uses ----
        // Voice teachers see TTS, image artists see Imagen, live partners see
        // Live (+ text as a turn-based fallback). Nothing else leaks in.
        var modelQuery by remember { mutableStateOf("") }
        val groups = remember(vm.aiModels.toList(), vm.showFreeModelsOnly, agent.id, agent.kind) {
            vm.modelChoicesFor(agent)
        }
        val filteredGroups = groups.mapNotNull { (kind, list) ->
            val f = if (modelQuery.isBlank()) list else list.filter {
                it.id.contains(modelQuery, true) || it.displayName.contains(modelQuery, true)
            }
            if (f.isEmpty()) null else kind to f
        }

        EditorCard("النموذج", Icons.Filled.Memory) {
            Text(
                when (agent.kind) {
                    ModelKind.TTS -> "النماذج الصوتية فقط — هذه الشخصية تنطق ولا تكتب"
                    ModelKind.IMAGE -> "نماذج الصور فقط — هذه الشخصية ترسم ولا تتحدث"
                    ModelKind.VIDEO -> "نماذج الفيديو فقط"
                    ModelKind.LIVE -> "النماذج الحيّة أولاً، ثم النصية كبديل للمحادثة"
                    ModelKind.EMBEDDING -> "نماذج التضمين فقط"
                    else -> "النماذج النصية فقط — هذه الشخصية تكتب ولا تنطق"
                },
                color = ZTextMuted, fontSize = 10.sp, lineHeight = 16.sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (vm.showFreeModelsOnly) ZEmerald.copy(alpha = 0.15f) else ZSurfaceVariant,
                    onClick = { vm.setShowFreeModelsOnly(!vm.showFreeModelsOnly) },
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (vm.showFreeModelsOnly) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                            null, tint = if (vm.showFreeModelsOnly) ZEmerald else ZTextMuted,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "المجانية فقط",
                            color = if (vm.showFreeModelsOnly) ZEmerald else ZTextSecondary,
                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "${filteredGroups.sumOf { it.second.size }} نموذج",
                    color = ZTextMuted, fontSize = 10.sp,
                )
                Spacer(Modifier.weight(1f))
                if (vm.isFetchingModels) {
                    CircularProgressIndicator(color = ZIndigo, strokeWidth = 2.dp, modifier = Modifier.size(15.dp))
                } else {
                    TextButton(onClick = { vm.fetchModels() }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(Icons.Filled.Refresh, null, tint = ZIndigo, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("تحديث", color = ZIndigo, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = modelQuery,
                onValueChange = { modelQuery = it },
                placeholder = { Text("ابحث عن نموذج...", color = ZTextMuted, fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = ZTextSecondary, modifier = Modifier.size(17.dp)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors(),
            )
            Spacer(Modifier.height(8.dp))

            // Currently selected model always stays visible, even if filtered out.
            val selectedModel = vm.modelById(modelId)
            if (selectedModel == null && modelId.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp), color = ZAmber.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, tint = ZAmber, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(modelId, color = ZTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("مختار حالياً · غير موجود في القائمة المجلوبة", color = ZTextMuted, fontSize = 10.sp)
                        }
                    }
                }
            }

            filteredGroups.forEach { (kind, list) ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).clip(RoundedCornerShape(4.dp)).background(kindColor(kind)))
                    Spacer(Modifier.width(8.dp))
                    Text(kind.label, color = ZTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(4.dp))
                    Text("(${list.size})", color = ZTextMuted, fontSize = 9.sp)
                    if (kind == agent.kind) {
                        Spacer(Modifier.width(8.dp))
                        TinyTag("موصى به", ZEmerald)
                    }
                }
                list.forEach { m ->
                    val sel = m.id == modelId
                    val quota = GeminiQuotas.forModel(m.id)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (sel) ZIndigo.copy(alpha = 0.13f) else ZSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        onClick = { modelId = m.id },
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (sel) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                null, tint = if (sel) ZEmerald else ZTextMuted, modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        m.displayName, color = ZTextPrimary,
                                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                        modifier = Modifier.weight(1f, fill = false), maxLines = 1,
                                    )
                                    if (m.isPreview) { Spacer(Modifier.width(4.dp)); TinyTag("تجريبي", ZAmber) }
                                    when {
                                        quota == null -> {}
                                        quota.free -> { Spacer(Modifier.width(4.dp)); TinyTag("مجاني", ZEmerald) }
                                        else -> { Spacer(Modifier.width(4.dp)); TinyTag("مدفوع", ZRose) }
                                    }
                                }
                                Text(m.id, color = ZTextMuted, fontSize = 9.sp, maxLines = 1)
                                if (quota != null && quota.free) {
                                    Text(quota.short, color = ZCyanDeep, fontSize = 9.sp)
                                } else if (m.tokenLabel.isNotBlank()) {
                                    Text(m.tokenLabel, color = ZTextMuted, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }
            if (filteredGroups.isEmpty()) {
                Text(
                    "لا توجد نماذج من نوع هذه الشخصية — اجلب القائمة أو أوقف فلتر «المجانية فقط».",
                    color = ZTextMuted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }

        // Voice selector — TTS teachers and live conversation partners both speak.
        if (agent.kind.usesVoice) {
            EditorCard("الصوت / الشخصية الصوتية", Icons.Filled.RecordVoiceOver) {
                vm.aiVoices.forEach { v ->
                    val sel = v.id == voiceId
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (sel) ZCyanDeep.copy(alpha = 0.10f) else ZSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        onClick = { voiceId = v.id },
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (sel) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null, tint = if (sel) ZEmerald else ZTextMuted, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(v.displayName, color = ZTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("${v.gender} · ${v.accent} · ${v.sample}", color = ZTextSecondary, fontSize = 10.sp)
                            }
                            Icon(Icons.Filled.PlayCircleOutline, null, tint = ZCyanDeep)
                        }
                    }
                }
            }
        }

        EditorCard("الشخصية / الهوية", Icons.Filled.Face) {
            EditorField(character, { character = it }, "صف شخصية هذا العميل...", minLines = 2)
        }
        EditorCard("الأسلوب", Icons.Filled.Brush) {
            EditorField(style, { style = it }, "صف أسلوب الأداء (النبرة/السرعة/اللهجة)...", minLines = 2)
        }
        EditorCard("المطالبة (System Prompt)", Icons.Filled.Terminal) {
            EditorField(prompt, { prompt = it }, "اكتب المطالبة التي توجّه التوليد...", minLines = 5)
            Spacer(Modifier.height(8.dp))
            Text("يمكنك استخدام متغيرات مثل {WORDS} و {LEVEL} و {DIALOGUE} و {SOUND} و {STATS}.", color = ZTextMuted, fontSize = 10.sp)
        }

        Button(
            onClick = {
                vm.updateAgent(agent.copy(character = character, style = style, prompt = prompt, modelId = modelId, voiceId = voiceId))
                onBack()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ZIndigo),
        ) {
            Icon(Icons.Filled.Save, null); Spacer(Modifier.width(8.dp)); Text("حفظ الإعدادات", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(60.dp))
    }
}

@Composable
private fun EditorCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = ZCard, shadowElevation = 5.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = ZIndigo, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun EditorField(value: String, onChange: (String) -> Unit, placeholder: String, minLines: Int = 1) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = ZTextMuted, fontSize = 13.sp) },
        minLines = minLines,
        shape = RoundedCornerShape(12.dp),
        colors = fieldColors(),
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = ZSurfaceVariant, unfocusedContainerColor = ZSurfaceVariant,
    focusedBorderColor = ZIndigo, unfocusedBorderColor = ZBorder,
    focusedTextColor = ZTextPrimary, unfocusedTextColor = ZTextPrimary,
)

private fun agentColor(kind: ModelKind): Color = kindColor(kind)

private fun agentIcon(name: String): ImageVector = when (name) {
    "translate" -> Icons.Filled.Translate
    "book" -> Icons.Filled.MenuBook
    "headphones" -> Icons.Filled.Headphones
    "talk" -> Icons.Filled.Forum
    "sound" -> Icons.Filled.GraphicEq
    "image" -> Icons.Filled.Image
    "coach" -> Icons.Filled.Insights
    "quiz" -> Icons.Filled.Quiz
    else -> Icons.Filled.AutoAwesome
}
