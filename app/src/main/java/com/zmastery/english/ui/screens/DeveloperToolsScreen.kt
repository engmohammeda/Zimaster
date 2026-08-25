package com.zmastery.english.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.ui.components.SoftCard
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel

/**
 * 🏗 أدوات المطور والمسؤول — المركز الواحد لكل أدوات النشر:
 *
 *   📤 رفع ونشر الدروس   📢 بث إعلان للطلاب   💬 عبارات التحفيز   👥 الطلاب المسجلون
 *
 * هذا هو المكان الوحيد لرفع المحتوى (مع خطوة «استورد كورساً» في لوحة القيادة
 * كبوابة ثانية للترحيب). الشاشة محمية: غير المسؤول يرى بطاقة قفل فقط.
 */
@Composable
fun DeveloperToolsScreen(vm: AppViewModel, onOpenImport: () -> Unit) {
    com.zmastery.english.ui.components.TrackStudyTime(vm, "devtools")

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { DevHero(vm) }

        if (!vm.isAdmin) {
            item { DevLockedCard() }
            return@LazyColumn
        }

        item { PublishLessonsCard(onOpenImport) }
        item { AnnouncementCard(vm) }
        item { QuoteCard(vm) }
        item { StudentsCard(vm) }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

/* ─────────────────────────── الرأس ─────────────────────────── */

@Composable
private fun DevHero(vm: AppViewModel) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(ZAmberDeep, ZAmber)))
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.AdminPanelSettings, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("أدوات المطور والمسؤول 👑", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text(
                        if (vm.isAdmin) "مركز النشر — كل أداة المحتوى في مكان واحد"
                        else "هذه المنطقة للمسؤول فقط",
                        color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp,
                    )
                }
            }
            if (vm.isAdmin) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DevChip(
                        if (vm.userRole == "admin") "صلاحيات سحابية كاملة ✓" else "وضع مطور محلي",
                        Icons.Filled.Verified,
                    )
                    vm.cloudEmail?.takeIf { it.isNotBlank() }?.let {
                        DevChip(it, Icons.Filled.Person)
                    }
                }
            }
        }
    }
}

@Composable
private fun DevChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.20f)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DevLockedCard() {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Lock, null, tint = ZTextMuted, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(12.dp))
            Text("محتوى مخصص للمسؤول", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "إن كنت مالك التطبيق فعّل وضع المطور من الإعدادات أو سجّل الدخول بحسابك المالك.",
                color = ZTextSecondary, fontSize = 12.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

/* ─────────────────────────── 1) رفع ونشر الدروس ─────────────────────────── */

@Composable
private fun PublishLessonsCard(onOpenImport: () -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(ZIndigo, ZPurple))),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.CloudUpload, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("رفع ونشر الدروس", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Text("رفع ملفات JSON/ZIP ← التعرّف ← نشر لكل الطلاب", color = ZTextSecondary, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onOpenImport,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ZIndigo),
            ) {
                Icon(Icons.Filled.UploadFile, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("فتح أداة رفع الدروس", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

/* ─────────────────────────── 2) بث إعلان ─────────────────────────── */

private val AnnouncementTypes = listOf(
    "info" to "معلومة",
    "update" to "تحديث",
    "challenge" to "تحدٍّ",
    "alert" to "تنبيه",
)

@Composable
private fun AnnouncementCard(vm: AppViewModel) {
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("info") }
    var isSending by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(ZCyanDeep.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Campaign, null, tint = ZCyanDeep, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("بث إعلان لجميع الطلاب", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Text("يظهر في أعلى لوحة القيادة لدى الجميع", color = ZTextSecondary, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("عنوان الإعلان") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ZSurfaceVariant, unfocusedContainerColor = ZSurfaceVariant,
                    focusedBorderColor = ZCyanDeep, unfocusedBorderColor = ZBorder,
                    focusedTextColor = ZTextPrimary, unfocusedTextColor = ZTextPrimary,
                ),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = message, onValueChange = { message = it },
                label = { Text("نص الإعلان") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ZSurfaceVariant, unfocusedContainerColor = ZSurfaceVariant,
                    focusedBorderColor = ZCyanDeep, unfocusedBorderColor = ZBorder,
                    focusedTextColor = ZTextPrimary, unfocusedTextColor = ZTextPrimary,
                ),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                AnnouncementTypes.forEach { (key, label) ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (type == key) ZCyanDeep else ZSurfaceVariant,
                        onClick = { type = key },
                    ) {
                        Text(
                            label,
                            color = if (type == key) Color.White else ZTextSecondary,
                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    isSending = true; status = null
                    vm.postAnnouncement(title, message, type) { success, msg ->
                        isSending = false
                        status = msg
                        if (success) { title = ""; message = "" }
                    }
                },
                enabled = !isSending && title.isNotBlank() && message.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ZCyanDeep),
            ) {
                if (isSending) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Icon(Icons.Filled.Send, null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(if (isSending) "جارٍ البث…" else "بث الإعلان لكل الطلاب", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            status?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = ZEmeraldDeep, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/* ─────────────────────────── 3) عبارات التحفيز ─────────────────────────── */

@Composable
private fun QuoteCard(vm: AppViewModel) {
    var text by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(ZAmber.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.FormatQuote, null, tint = ZAmber, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("عبارات التحفيز اليومية", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Text("عبارة عشوائية يومياً في ودجت كل متعلم", color = ZTextSecondary, fontSize = 11.sp)
                }
                TextButton(onClick = { vm.syncQuotes() }) {
                    Text("${vm.cloudQuoteCount} عبارة 🔄", color = ZCyan, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text("نص العبارة") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ZSurfaceVariant, unfocusedContainerColor = ZSurfaceVariant,
                    focusedBorderColor = ZAmber, unfocusedBorderColor = ZBorder,
                    focusedTextColor = ZTextPrimary, unfocusedTextColor = ZTextPrimary,
                ),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = author, onValueChange = { author = it },
                label = { Text("المصدر/الكاتب (اختياري)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ZSurfaceVariant, unfocusedContainerColor = ZSurfaceVariant,
                    focusedBorderColor = ZAmber, unfocusedBorderColor = ZBorder,
                    focusedTextColor = ZTextPrimary, unfocusedTextColor = ZTextPrimary,
                ),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    status = null
                    vm.addQuote(text, author) { success, msg ->
                        status = msg
                        if (success) { text = ""; author = "" }
                    }
                },
                enabled = !vm.isAddingQuote && text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ZAmber),
            ) {
                Text(
                    if (vm.isAddingQuote) "جارٍ النشر…" else "نشر العبارة لكل الأجهزة",
                    color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                )
            }
            status?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = ZEmeraldDeep, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/* ─────────────────────────── 4) الطلاب المسجلون ─────────────────────────── */

@Composable
private fun StudentsCard(vm: AppViewModel) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(ZEmerald.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Group, null, tint = ZEmerald, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("الطلاب المسجلون سحابياً", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Text("${vm.registeredUsersList.size} مسجل", color = ZTextSecondary, fontSize = 11.sp)
                }
                TextButton(onClick = { vm.loadRegisteredUsers() }, enabled = !vm.isLoadingUsers) {
                    if (vm.isLoadingUsers) {
                        CircularProgressIndicator(color = ZCyan, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                    } else {
                        Text("تحديث 🔄", color = ZCyan, fontSize = 11.sp)
                    }
                }
            }
            if (vm.registeredUsersList.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "اضغط «تحديث» لسحب قائمة المستخدمين من السحابة.",
                    color = ZTextMuted, fontSize = 11.sp,
                )
            } else {
                Spacer(Modifier.height(8.dp))
                vm.registeredUsersList.forEach { user ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(ZSurfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                (user.displayName?.take(1) ?: "U").uppercase(),
                                color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    user.displayName ?: "مستخدم",
                                    color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                                )
                                if (user.role == "admin") { Spacer(Modifier.width(4.dp)); Text("👑", fontSize = 10.sp) }
                            }
                            Text(
                                user.email ?: "حساب مجهول",
                                color = ZTextSecondary, fontSize = 10.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "🔥 ${user.streak} · ⚡ ${user.xp} XP · 📚 ${user.completedLessonsCount} درس",
                                color = ZTextMuted, fontSize = 9.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
