package com.zmastery.english.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.data.FileIo
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel

private enum class PendingExport { FULL, LESSONS, HARD_JSON, HARD_CSV, ALL_CSV }
private enum class PendingImport { FULL, LESSONS, WORDS }

@Composable
fun BackupScreen(vm: AppViewModel) {
    val context = LocalContext.current
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    var pendingExport by remember { mutableStateOf<PendingExport?>(null) }
    var pendingImport by remember { mutableStateOf<PendingImport?>(null) }
    var confirmFullRestore by remember { mutableStateOf<String?>(null) }

    // ---- SAF: create document (export) ----
    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val kind = pendingExport
        pendingExport = null
        if (uri == null || kind == null) return@rememberLauncherForActivityResult
        val content = when (kind) {
            PendingExport.FULL -> vm.exportFullBackup()
            PendingExport.LESSONS -> vm.exportLessonsOnly()
            PendingExport.HARD_JSON -> vm.exportHardWordsJson()
            PendingExport.HARD_CSV -> vm.exportHardWordsCsv()
            PendingExport.ALL_CSV -> vm.exportAllWordsCsv()
        }
        val ok = FileIo.writeText(context, uri, content)
        toast(if (ok) "تم حفظ النسخة بنجاح ✓" else "تعذّر حفظ الملف")
    }

    // ---- SAF: open document (import) ----
    val openDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val kind = pendingImport
        pendingImport = null
        if (uri == null || kind == null) return@rememberLauncherForActivityResult
        val raw = FileIo.readText(context, uri)
        if (raw == null) { toast("تعذّر قراءة الملف"); return@rememberLauncherForActivityResult }
        when (kind) {
            PendingImport.FULL -> confirmFullRestore = raw
            PendingImport.LESSONS -> vm.importLessonsBackup(raw).let { toast(if (it.ok) it.message else it.message) }
            PendingImport.WORDS -> vm.importWordsBackup(raw).let { toast(it.message) }
        }
    }

    fun startExport(kind: PendingExport, suggestedName: String) {
        pendingExport = kind
        createDoc.launch(suggestedName)
    }
    fun startImport(kind: PendingImport) {
        pendingImport = kind
        openDoc.launch(arrayOf("application/json", "application/octet-stream", "text/*", "*/*"))
    }

    val stamp = remember { java.time.LocalDate.now().toString() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Hero
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(ZIndigo, ZPurple))).padding(22.dp)
        ) {
            Column {
                Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.18f)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Shield, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("حماية بياناتك", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("النسخ الاحتياطي والاستعادة", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(6.dp))
                Text(
                    "احفظ كل بياناتك في ملف واحد واستعدها في أي وقت. أصوات النطق تُولّد تلقائياً من النصوص المحفوظة — لا شيء يُفقد.",
                    color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, lineHeight = 20.sp,
                )
            }
        }

        // Snapshot summary
        SnapshotCard(vm)

        // ---- Full backup ----
        SectionLabel("النسخة الكاملة")
        BackupCard(
            icon = Icons.Filled.Backup,
            accent = ZIndigo,
            title = "تصدير نسخة كاملة",
            desc = "كل الكورسات والدروس والكلمات والتقدم والإعدادات في ملف واحد (.zmastery)",
            actionText = "تصدير",
            onClick = { startExport(PendingExport.FULL, "zmastery-backup-$stamp.zmastery") },
        )
        BackupCard(
            icon = Icons.Filled.Restore,
            accent = ZEmerald,
            title = "استعادة نسخة كاملة",
            desc = "استرجع كل بياناتك من ملف نسخة احتياطية سابق (سيحل محل البيانات الحالية)",
            actionText = "استعادة",
            onClick = { startImport(PendingImport.FULL) },
        )

        // ---- Lessons only ----
        SectionLabel("الدروس فقط")
        BackupCard(
            icon = Icons.Filled.MenuBook,
            accent = ZCyanDeep,
            title = "تصدير الدروس والكورسات",
            desc = "شارك منهجك مع غيرك — دون تقدمك الشخصي (${vm.lessons.size} درس)",
            actionText = "تصدير",
            onClick = { startExport(PendingExport.LESSONS, "zmastery-lessons-$stamp.json") },
        )
        BackupCard(
            icon = Icons.Filled.LibraryAdd,
            accent = ZCyan,
            title = "استيراد الدروس",
            desc = "أضف دروساً وكورسات من ملف إلى مكتبتك (تحديث تدريجي دون حذف)",
            actionText = "استيراد",
            onClick = { startImport(PendingImport.LESSONS) },
        )

        // ---- Hard words ----
        SectionLabel("الكلمات الصعبة")
        BackupCard(
            icon = Icons.Filled.Whatshot,
            accent = ZRose,
            title = "تصدير الكلمات الصعبة (JSON)",
            desc = "الكلمات التي تنساها كثيراً — ${vm.hardWordsCount} كلمة، للمراجعة أو النقل",
            actionText = "تصدير",
            enabled = vm.hardWordsCount > 0,
            onClick = { startExport(PendingExport.HARD_JSON, "zmastery-hard-words-$stamp.json") },
        )
        BackupCard(
            icon = Icons.Filled.TableChart,
            accent = ZAmber,
            title = "تصدير الكلمات الصعبة (CSV)",
            desc = "لجداول البيانات أو تطبيقات مثل Anki",
            actionText = "CSV",
            enabled = vm.hardWordsCount > 0,
            onClick = { startExport(PendingExport.HARD_CSV, "zmastery-hard-words-$stamp.csv") },
        )
        BackupCard(
            icon = Icons.Filled.GridOn,
            accent = ZCyanDeep,
            title = "تصدير كل القاموس (CSV)",
            desc = "جميع الكلمات المحفوظة (${vm.totalWords} كلمة) كجدول",
            actionText = "CSV",
            enabled = vm.totalWords > 0,
            onClick = { startExport(PendingExport.ALL_CSV, "zmastery-vocabulary-$stamp.csv") },
        )
        BackupCard(
            icon = Icons.Filled.PostAdd,
            accent = ZEmerald,
            title = "استيراد كلمات",
            desc = "أضف كلمات من ملف JSON إلى قاموسك",
            actionText = "استيراد",
            onClick = { startImport(PendingImport.WORDS) },
        )

        Spacer(Modifier.height(80.dp))
    }

    // Confirm destructive full restore
    confirmFullRestore?.let { raw ->
        AlertDialog(
            onDismissRequest = { confirmFullRestore = null },
            containerColor = ZSurface,
            icon = { Icon(Icons.Filled.Restore, null, tint = ZEmerald) },
            title = { Text("استعادة النسخة الكاملة؟", color = ZTextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("سيتم استبدال جميع بياناتك الحالية ببيانات الملف. تأكد أنك صدّرت نسخة من بياناتك الحالية إن أردت الاحتفاظ بها.", color = ZTextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    val res = vm.restoreFullBackup(raw)
                    confirmFullRestore = null
                    toast(res.message)
                }) { Text("استعادة", color = ZEmerald, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmFullRestore = null }) { Text("إلغاء", color = ZTextSecondary) } },
        )
    }
}

@Composable
private fun SnapshotCard(vm: AppViewModel) {
    Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 5.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            SnapStat("${vm.courses.count { c -> vm.lessons.any { it.courseId == c.id } }}", "كورس", ZIndigo)
            Divider(Modifier.height(36.dp).width(1.dp), color = ZBorder)
            SnapStat("${vm.lessons.size}", "درس", ZCyanDeep)
            Divider(Modifier.height(36.dp).width(1.dp), color = ZBorder)
            SnapStat("${vm.totalWords}", "كلمة", ZEmerald)
            Divider(Modifier.height(36.dp).width(1.dp), color = ZBorder)
            SnapStat("${vm.hardWordsCount}", "صعبة", ZRose)
        }
    }
}

@Composable
private fun SnapStat(value: String, label: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = accent, fontWeight = FontWeight.Black, fontSize = 20.sp)
        Text(label, color = ZTextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = ZTextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
}

@Composable
private fun BackupCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    desc: String,
    actionText: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ZCard,
        shadowElevation = if (enabled) 5.dp else 0.dp,
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f),
        onClick = { if (enabled) onClick() },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(desc, color = ZTextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
            }
            Spacer(Modifier.width(10.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = accent.copy(alpha = 0.14f)) {
                Text(actionText, color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
            }
        }
    }
}
