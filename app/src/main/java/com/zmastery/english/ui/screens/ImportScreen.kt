package com.zmastery.english.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.data.FileImport
import com.zmastery.english.data.ImportEngine
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel

@Composable
fun ImportScreen(vm: AppViewModel) {
    var text by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<ImportEngine.ImportResult?>(null) }
    var lessonResult by remember { mutableStateOf<ImportEngine.LessonImportResult?>(null) }
    var multiResult by remember { mutableStateOf<ImportEngine.MultiLessonImportResult?>(null) }
    var imported by remember { mutableStateOf<String?>(null) }
    var fileInfo by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    fun clearResults() { result = null; lessonResult = null; multiResult = null; fileInfo = null }
    fun verify() {
        // Auto-detect: batch of lessons > single lesson (metadata) > course package.
        when {
            ImportEngine.looksLikeMultiLesson(text) -> {
                multiResult = ImportEngine.parseMultiLesson(text); result = null; lessonResult = null
            }
            ImportEngine.looksLikeLesson(text) -> {
                lessonResult = ImportEngine.parseLesson(text); result = null; multiResult = null
            }
            else -> {
                result = ImportEngine.parse(text); lessonResult = null; multiResult = null
            }
        }
    }

    // File picker: multiple JSON files and/or ZIP archives at once.
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        val res = FileImport.readUris(context, uris)
        if (res.error != null && res.jsons.isEmpty()) {
            multiResult = ImportEngine.MultiLessonImportResult(false, res.error)
            result = null; lessonResult = null; fileInfo = null
            return@rememberLauncherForActivityResult
        }
        fileInfo = "تم قراءة ${res.fileCount} ملف · ${res.jsonCount} درس/كائن JSON"
        multiResult = ImportEngine.parseRawList(res.jsons)
        result = null; lessonResult = null
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(ZIndigo, ZCyanDeep))).padding(20.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.UploadFile, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("استيراد الكورسات", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(6.dp))
                Text("الصق درساً أو عدة دروس، أو ارفع ملفات JSON متعددة أو ملف ZIP دفعة واحدة", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
            }
        }

        // Upload files card (multiple JSON + ZIP)
        Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 5.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(ZIndigo, ZPurple))),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.DriveFolderUpload, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("رفع ملفات", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("عدة ملفات JSON أو ملف ZIP دفعة واحدة", color = ZTextSecondary, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { clearResults(); filePicker.launch("*/*") },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ZIndigo),
                ) {
                    Icon(Icons.Filled.UploadFile, null); Spacer(Modifier.width(8.dp))
                    Text("اختر ملفات JSON أو ZIP", fontWeight = FontWeight.Bold)
                }
                fileInfo?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = ZCyanDeep, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Paste card
        Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 5.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("لصق كود JSON", color = ZTextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { clipboard.getText()?.let { text = it.text; clearResults() } }) {
                        Icon(Icons.Filled.ContentPaste, null, tint = ZCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp)); Text("لصق", color = ZCyan)
                    }
                }
                Text("درس واحد، أو عدة دروس كمصفوفة [ ... ] في نفس الحقل", color = ZTextMuted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; clearResults() },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    placeholder = { Text("[ { \"metadata\": { \"course_id\": ... } } ]", color = ZTextMuted, fontFamily = FontFamily.Monospace) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ZSurfaceVariant, unfocusedContainerColor = ZSurfaceVariant,
                        focusedBorderColor = ZIndigo, unfocusedBorderColor = ZBorder,
                        focusedTextColor = ZTextPrimary, unfocusedTextColor = ZTextPrimary,
                    ),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { verify() },
                        enabled = text.isNotBlank(),
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ZIndigo, disabledContainerColor = ZBorder),
                    ) {
                        Icon(Icons.Filled.FactCheck, null); Spacer(Modifier.width(8.dp)); Text("تحقق", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = { text = ""; clearResults() }, modifier = Modifier.height(50.dp), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Filled.Clear, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("مسح")
                    }
                }
            }
        }

        // Multi-lesson (batch) import result
        multiResult?.let { r ->
            Surface(shape = RoundedCornerShape(16.dp), color = if (r.success) ZEmerald.copy(alpha = 0.12f) else ZRose.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(if (r.success) Icons.Filled.LibraryBooks else Icons.Filled.Error, null, tint = if (r.success) ZEmerald else ZRose)
                        Spacer(Modifier.width(10.dp))
                        Text(r.message, color = if (r.success) ZEmerald else ZRose, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                    if (r.success && r.packages.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                imported = vm.importLessons(r.packages)
                                text = ""; clearResults()
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ZEmerald),
                        ) {
                            Icon(Icons.Filled.LibraryAdd, null); Spacer(Modifier.width(8.dp))
                            Text("إضافة ${r.packages.size} درس إلى المكتبة المحلية", fontWeight = FontWeight.Bold)
                        }

                        // Admin Only: Publish directly to Firebase Cloud for all students
                        if (vm.isAdmin) {
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    vm.publishLessonsBatchToCloud(r.packages) { success, msg ->
                                        imported = msg
                                        if (success) {
                                            text = ""; clearResults()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ZAmber),
                            ) {
                                Icon(Icons.Filled.CloudUpload, null, tint = Color.Black)
                                Spacer(Modifier.width(8.dp))
                                Text("👑 نشر ${r.packages.size} درس سحابياً لجميع الطلاب 🚀", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Per-lesson import result
        lessonResult?.let { r ->
            Surface(shape = RoundedCornerShape(16.dp), color = if (r.success) ZEmerald.copy(alpha = 0.12f) else ZRose.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(if (r.success) Icons.Filled.CheckCircle else Icons.Filled.Error, null, tint = if (r.success) ZEmerald else ZRose)
                        Spacer(Modifier.width(10.dp))
                        Text(r.message, color = if (r.success) ZEmerald else ZRose, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                    if (r.success && r.pkg != null) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                imported = vm.importLesson(r.pkg, text)
                                text = ""; clearResults()
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ZEmerald),
                        ) {
                            Icon(Icons.Filled.LibraryAdd, null); Spacer(Modifier.width(8.dp)); Text("إضافة الدرس إلى المكتبة المحلية", fontWeight = FontWeight.Bold)
                        }

                        // Admin Only: Publish directly to Firebase Cloud for all students
                        if (vm.isAdmin) {
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    vm.publishLessonToCloud(r.pkg) { success, msg ->
                                        imported = msg
                                        if (success) {
                                            text = ""; clearResults()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ZAmber),
                            ) {
                                Icon(Icons.Filled.CloudUpload, null, tint = Color.Black)
                                Spacer(Modifier.width(8.dp))
                                Text("👑 نشر الدرس سحابياً لجميع الطلاب 🚀", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        result?.let { r ->
            Surface(shape = RoundedCornerShape(16.dp), color = if (r.success) ZEmerald.copy(alpha = 0.12f) else ZRose.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (r.success) Icons.Filled.CheckCircle else Icons.Filled.Error, null, tint = if (r.success) ZEmerald else ZRose)
                        Spacer(Modifier.width(10.dp))
                        Text(r.message, color = if (r.success) ZEmerald else ZRose, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    if (r.success && r.pkg != null) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                imported = vm.importPackage(r.pkg)
                                text = ""; result = null
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ZEmerald),
                        ) {
                            Icon(Icons.Filled.LibraryAdd, null); Spacer(Modifier.width(8.dp)); Text("إضافة إلى المكتبة", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        imported?.let { msg ->
            Surface(shape = RoundedCornerShape(16.dp), color = ZCard, shadowElevation = 5.dp, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Celebration, null, tint = ZAmber)
                    Spacer(Modifier.width(10.dp))
                    Text(msg, color = ZTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }

        // Import methods info
        Surface(shape = RoundedCornerShape(16.dp), color = ZCard, shadowElevation = 5.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("طرق الاستيراد المدعومة", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                MethodRow(Icons.Filled.ContentPaste, "لصق درس واحد أو عدة دروس (مصفوفة)", "مدعوم")
                MethodRow(Icons.Filled.InsertDriveFile, "رفع عدة ملفات JSON دفعة واحدة", "مدعوم")
                MethodRow(Icons.Filled.FolderZip, "رفع ملف ZIP يحوي كورسات كاملة", "مدعوم")
                MethodRow(Icons.Filled.SmartDisplay, "تحويل فيديو يوتيوب إلى درس عبر مطالبتك", "مدعوم")
                Spacer(Modifier.height(8.dp))
                Text("يتم التحقق من صحة المخطط ثم التخزين المحلي مع التحديث التدريجي دون مسح القديم", color = ZTextMuted, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun MethodRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, badge: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = ZCyan, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, color = ZTextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Surface(shape = RoundedCornerShape(8.dp), color = ZEmerald.copy(alpha = 0.15f)) {
            Text(badge, color = ZEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
        }
    }
}

