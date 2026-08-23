package com.zmastery.english.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel

private enum class SettingsSection(val title: String, val subtitle: String, val icon: ImageVector, val colors: List<Color>) {
    AI("الذكاء الاصطناعي", "النماذج · المفاتيح · الأصوات · الشخصيات · المطالبات", Icons.Filled.AutoAwesome, listOf(ZIndigo, ZPurple)),
    APPEARANCE("المظهر", "الوضع النهاري/الليلي والألوان", Icons.Filled.Palette, listOf(ZAmber, Color(0xFFEA580C))),
    LEARNING("التعلم", "الهدف اليومي وطريقة عرض الكلمات", Icons.Filled.School, listOf(ZCyanDeep, ZCyan)),
    REVIEW("محرّك المراجعة", "إعدادات FSRS والتكرار المتباعد", Icons.Filled.Science, listOf(ZEmerald, Color(0xFF059669))),
    BACKUP("النسخ الاحتياطي والبيانات", "التصدير · الاستعادة · المكتبة · حذف المحتوى", Icons.Filled.CloudSync, listOf(ZEmerald, ZCyanDeep)),
    NOTIFICATIONS("الإشعارات والتنبيهات", "التذكير اليومي وتنبيهات الحماسة والأصوات", Icons.Filled.NotificationsActive, listOf(ZAmber, ZRose)),
    HOME("الشاشة الرئيسية", "أداة الشاشة الرئيسية", Icons.Filled.Widgets, listOf(ZPurple, ZIndigo)),
    GENERAL("عام", "اللغة والمزامنة", Icons.Filled.Settings, listOf(Color(0xFF64748B), Color(0xFF475569))),
    ABOUT("حول", "الإصدار والخصوصية", Icons.Filled.Info, listOf(Color(0xFF6B9080), Color(0xFF52796F))),
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onImport: () -> Unit = {}, onBackup: () -> Unit = {}) {
    var section by remember { mutableStateOf<SettingsSection?>(null) }

    if (section == null) {
        SettingsHub(vm) { section = it }
    } else {
        SettingsDetail(vm, section!!, onImport, onBackup) { section = null }
    }
}

@Composable
private fun SettingsHub(vm: AppViewModel, onOpen: (SettingsSection) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Profile header
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(ZIndigo, ZPurple))).padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Text("Z", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("متعلّم Z-Mastery", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
                    Text("المستوى المتوسط • ${vm.xp} نقطة", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                }
            }
        }
        SettingsSection.values().forEach { s ->
            Surface(shape = RoundedCornerShape(18.dp), color = ZCard, shadowElevation = 5.dp, modifier = Modifier.fillMaxWidth(), onClick = { onOpen(s) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(s.colors)), contentAlignment = Alignment.Center) {
                        Icon(s.icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(s.subtitle, color = ZTextSecondary, fontSize = 11.sp)
                    }
                    Icon(Icons.Filled.ChevronLeft, null, tint = ZTextMuted)
                }
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SettingsDetail(vm: AppViewModel, section: SettingsSection, onImport: () -> Unit, onBackup: () -> Unit, onBack: () -> Unit) {
    // AI section is a full dedicated screen
    if (section == SettingsSection.AI) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.padding(16.dp)) { SubHeader(section.title, onBack) }
            Box(Modifier.weight(1f)) { AiSettingsScreen(vm) }
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SubHeader(section.title, onBack)
        when (section) {
            SettingsSection.APPEARANCE -> SettingsGroup("المظهر") { ThemeModeRow(vm) }
            SettingsSection.LEARNING -> SettingsGroup("التعلم") {
                SliderRow(vm)
                RevealModeRow(vm)
                Divider(color = ZBorder, modifier = Modifier.padding(horizontal = 14.dp))
                ToggleRow(
                    Icons.Filled.VolumeUp,
                    "تشغيل النطق تلقائياً",
                    "يُنطق الصوت فور ظهور كل مرحلة في المراجعة",
                    vm.reviewAutoPlay,
                ) { vm.reviewAutoPlay = it; vm.persist() }
                Divider(color = ZBorder, modifier = Modifier.padding(horizontal = 14.dp))
                ToggleRow(
                    Icons.Filled.PowerSettingsNew,
                    "إيقاف توليد الأصوات بالذكاء الاصطناعي نهائياً",
                    "عند التفعيل، لن يُولَّد أي صوت AI إطلاقاً (لا تلقائياً ولا يدوياً) — يبقى النطق الفوري المحلي يعمل كالمعتاد لكل الأزرار. أوقف هذا إذا كنت لا تريد استهلاك حصة Gemini إطلاقاً.",
                    vm.aiAudioEnabled,
                ) { vm.updateAiAudioEnabled(it) }
                Divider(color = ZBorder, modifier = Modifier.padding(horizontal = 14.dp))
                ToggleRow(
                    Icons.Filled.AutoAwesome,
                    "توليد وحفظ الصوت بالذكاء الاصطناعي تلقائياً",
                    "بعد كل استيراد، تُولَّد أصوات طبيعية (Gemini) للكلمات والدروس والقصص وتُحفظ دائماً. عند الإيقاف، تحتاج للضغط على «توليد» يدوياً من الأسفل.",
                    vm.autoGenerateAiAudio,
                    enabled = vm.aiAudioEnabled,
                ) { vm.autoGenerateAiAudio = it; vm.persist() }
                Divider(color = ZBorder, modifier = Modifier.padding(horizontal = 14.dp))
                AudioGenStatusRow(vm)
            }
            SettingsSection.REVIEW -> SettingsGroup("محرّك المراجعة (FSRS)") {
                RetentionRow(vm)
                Divider(color = ZBorder, modifier = Modifier.padding(horizontal = 14.dp))
                IntervalRow(vm)
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Science, null, tint = ZEmerald, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("خوارزمية FSRS-5", color = ZTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("نظام التكرار المتباعد الحديث المبني على نموذج ذاكرة من ثلاثة عوامل: الاستقرار، والصعوبة، وقابلية الاسترجاع — يجدول كل كلمة بدقة لحظة اقتراب نسيانها.", color = ZTextMuted, fontSize = 11.sp, lineHeight = 18.sp)
                }
            }
            SettingsSection.BACKUP -> BackupSection(vm, onImport, onBackup)
            SettingsSection.NOTIFICATIONS -> NotificationSection(vm)
            SettingsSection.HOME -> SettingsGroup("الشاشة الرئيسية") { WidgetRow() }
            SettingsSection.GENERAL -> {
                SettingsGroup("عام") {
                    ActionRow(Icons.Filled.Language, "اللغة", "العربية")
                }
                Spacer(Modifier.height(16.dp))
                CloudSyncGroup(vm)
            }
            SettingsSection.ABOUT -> SettingsGroup("حول") {
                // The mission is the product thesis — always re-readable.
                Column(
                    Modifier.fillMaxWidth().padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Insights, null, tint = ZIndigo, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("رسالتنا", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "لا تعلّم بدون استمرارية. ساعة منتظمة كل يوم تتغلّب على حماسٍ مؤقت يزول بسرعة.\n\n" +
                            "هذا التطبيق منصّة تعليمية لمنهجك: يدمج كل الكورسات والمستويات في مكان واحد، " +
                            "ويمنح كل كورس طريقة دراسة ومراجعة تناسبه — لتسهيل الاستمرارية قبل كل شيء.",
                        color = ZTextMuted, fontSize = 11.sp, lineHeight = 19.sp,
                    )
                }
                Divider(color = ZBorder, modifier = Modifier.padding(horizontal = 14.dp))
                ActionRow(Icons.Filled.Info, "الإصدار", "2.2.0 · محرك FSRS-5 + AI")
                ActionRow(Icons.Filled.Favorite, "قيّم التطبيق", "ساعدنا على التحسين")
                ActionRow(Icons.Filled.Shield, "الخصوصية", "بياناتك محفوظة محلياً")
            }
            else -> {}
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun BackupSection(vm: AppViewModel, onImport: () -> Unit, onBackup: () -> Unit) {
    var step by remember { mutableStateOf(0) }      // 0 idle · 1 warn · 2 final
    var typed by remember { mutableStateOf("") }
    var authFailed by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val secure = remember { com.zmastery.english.ui.components.DeviceAuth.isAvailable(ctx) }

    val authenticate = com.zmastery.english.ui.components.rememberDeviceAuth(
        title = "تأكيد حذف كل المحتوى",
        subtitle = "استخدم بصمتك أو رمز قفل الشاشة للمتابعة",
        onUnavailable = { authFailed = true },
    )

    // ---- Backup / restore is the headline action of this section ----
    Surface(shape = RoundedCornerShape(20.dp), color = Color.Transparent, onClick = onBackup, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(ZEmerald, ZCyanDeep))).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.CloudDownload, null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("النسخ الاحتياطي والاستعادة", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text("صدّر بياناتك بكل الصيغ · استعدها في أي وقت", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                }
                Icon(Icons.Filled.ChevronLeft, null, tint = Color.White)
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    SettingsGroup("المكتبة") {
        ActionRow(Icons.Filled.UploadFile, "استيراد الكورسات", "رفع/لصق JSON أو ZIP", onImport)
        ActionRow(
            Icons.Filled.LibraryBooks, "محتواك الحالي",
            "${vm.courses.size} كورس · ${vm.lessons.size} درس · ${vm.totalWords} كلمة",
        )
    }

    Spacer(Modifier.height(16.dp))

    // ---- Danger zone ----
    Text("منطقة الخطر", color = ZRose, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
    Surface(
        shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 5.dp,
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(1.dp, ZRose.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, null, tint = ZRose, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("حذف كل المحتوى", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("يتطلب تأكيداً بخطوتين + قفل الجهاز", color = ZTextSecondary, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "صدّر نسخة احتياطية أولاً — الحذف نهائي ولا يمكن التراجع عنه.",
                color = ZTextMuted, fontSize = 11.sp, lineHeight = 17.sp,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { step = 1; typed = ""; authFailed = false },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ZRose.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ZRose),
            ) {
                Icon(Icons.Filled.DeleteSweep, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp)); Text("حذف كل المحتوى", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }

    // ── Step 1 · warn + offer backup ──
    if (step == 1) {
        AlertDialog(
            onDismissRequest = { step = 0 },
            containerColor = ZSurface,
            icon = { Icon(Icons.Filled.Warning, null, tint = ZRose) },
            title = { Text("تحذير · خطوة 1 من 2", color = ZTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "سيتم حذف ${vm.courses.size} كورس و${vm.lessons.size} درس و${vm.totalWords} كلمة، " +
                            "مع كل تقدّمك وصور الروابط الذهنية.",
                        color = ZTextSecondary, fontSize = 14.sp, lineHeight = 21.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = ZEmerald.copy(alpha = 0.12f), onClick = onBackup, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CloudDownload, null, tint = ZEmerald, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("صدّر نسخة احتياطية الآن", color = ZEmerald, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { step = 2 }) { Text("متابعة", color = ZRose, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text("إلغاء", color = ZTextSecondary) } },
        )
    }

    // ── Step 2 · type the phrase, then unlock with device credential ──
    if (step == 2) {
        val phrase = "حذف"
        val typedOk = typed.trim() == phrase
        AlertDialog(
            onDismissRequest = { step = 0 },
            containerColor = ZSurface,
            icon = { Icon(Icons.Filled.Fingerprint, null, tint = ZRose) },
            title = { Text("تأكيد نهائي · خطوة 2 من 2", color = ZTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("اكتب كلمة «$phrase» للتأكيد:", color = ZTextSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = typed, onValueChange = { typed = it; authFailed = false },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ZCard, unfocusedContainerColor = ZCard,
                            focusedBorderColor = ZRose, unfocusedBorderColor = ZBorder,
                            focusedTextColor = ZTextPrimary, unfocusedTextColor = ZTextPrimary,
                        ),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (secure) Icons.Filled.Fingerprint else Icons.Filled.Info,
                            null, tint = if (secure) ZCyanDeep else ZAmber, modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (secure) "ستُطلب بصمتك أو رمز القفل بعد الضغط على «احذف»"
                            else "لا يوجد قفل شاشة على جهازك — الكتابة وحدها ستؤكّد الحذف",
                            color = if (secure) ZTextMuted else ZAmber, fontSize = 11.sp, lineHeight = 17.sp,
                        )
                    }
                    if (authFailed) {
                        Spacer(Modifier.height(8.dp))
                        Text("تعذّر فتح قفل الجهاز — تم إلغاء الحذف.", color = ZRose, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = typedOk,
                    onClick = {
                        if (secure) {
                            authenticate {
                                vm.resetAll(); step = 0; typed = ""
                            }
                        } else {
                            vm.resetAll(); step = 0; typed = ""
                        }
                    },
                ) { Text("احذف نهائياً", color = if (typedOk) ZRose else ZTextMuted, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text("إلغاء", color = ZTextSecondary) } },
        )
    }
}

@Composable
private fun SubHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Surface(shape = RoundedCornerShape(12.dp), color = ZCard, shadowElevation = 3.dp, onClick = onBack) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ArrowForward, null, tint = ZIndigo, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("رجوع", color = ZIndigo, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(title, color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp)
    }
}

@Composable
private fun WidgetRow() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val canWidget = remember { com.zmastery.english.widget.HomeShortcuts.canPinWidget(context) }
    val canShortcut = remember { com.zmastery.english.widget.HomeShortcuts.canPinShortcut(context) }

    Column {
        // ---- Widget ----
        Row(
            Modifier.fillMaxWidth()
                .clickable { com.zmastery.english.widget.HomeShortcuts.pinWidget(context) }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(ZIndigo.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Widgets, null, tint = ZIndigo) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("إضافة أداة الشاشة الرئيسية", color = ZTextPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    if (canWidget) "تذكير الدراسة + سلسلة الأيام + عبارة تحفيزية"
                    else "مشغّلك لا يدعم الإضافة التلقائية — أضفها يدوياً",
                    color = if (canWidget) ZTextSecondary else ZAmber, fontSize = 12.sp, lineHeight = 18.sp,
                )
            }
            Icon(Icons.Filled.AddToHomeScreen, null, tint = ZIndigo)
        }

        Divider(color = ZBorder, modifier = Modifier.padding(horizontal = 14.dp))

        // ---- Pinned quick-review shortcut ----
        Row(
            Modifier.fillMaxWidth()
                .clickable { com.zmastery.english.widget.HomeShortcuts.pinReviewShortcut(context) }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(ZEmerald.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Bolt, null, tint = ZEmerald) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("اختصار «مراجعة سريعة»", color = ZTextPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    if (canShortcut) "أيقونة على الشاشة تفتح المراجعة مباشرة"
                    else "غير مدعوم على مشغّلك الحالي",
                    color = if (canShortcut) ZTextSecondary else ZAmber, fontSize = 12.sp, lineHeight = 18.sp,
                )
            }
            Icon(Icons.Filled.AddToHomeScreen, null, tint = ZEmerald)
        }

        Divider(color = ZBorder, modifier = Modifier.padding(horizontal = 14.dp))

        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.TouchApp, null, tint = ZCyanDeep, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("اختصارات الضغط المطوّل", color = ZTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "اضغط مطولاً على أيقونة التطبيق للوصول السريع إلى: المراجعة · القاموس · الاستيراد. " +
                    "هذه الاختصارات مُفعّلة تلقائياً.",
                color = ZTextMuted, fontSize = 11.sp, lineHeight = 18.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "لم تظهر الأداة؟ اضغط مطولاً على شاشة هاتفك ← الأدوات (Widgets) ← Z-Mastery، ثم اسحبها للشاشة.",
                color = ZTextMuted, fontSize = 11.sp, lineHeight = 18.sp,
            )
        }
    }
}

@Composable
internal fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, color = ZTextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 5.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(6.dp), content = content)
        }
    }
}

@Composable
private fun SliderRow(vm: AppViewModel) {
    Column(Modifier.padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Flag, null, tint = ZCyan)
            Spacer(Modifier.width(12.dp))
            Text("الهدف اليومي", color = ZTextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${vm.dailyGoal} مراجعة", color = ZCyan, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = vm.dailyGoal.toFloat(),
            onValueChange = { vm.dailyGoal = it.toInt() },
            valueRange = 10f..100f,
            steps = 17,
            colors = SliderDefaults.colors(thumbColor = ZCyan, activeTrackColor = ZCyan, inactiveTrackColor = ZBorder),
        )
    }
}

@Composable
private fun RevealModeRow(vm: AppViewModel) {
    val mode = vm.revealMode
    Column(Modifier.padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Visibility, null, tint = ZIndigo)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("طريقة عرض الكلمات", color = ZTextPrimary, fontWeight = FontWeight.SemiBold)
                Text(mode.desc, color = ZTextSecondary, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ZSurfaceVariant).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            com.zmastery.english.data.RevealMode.values().forEach { m ->
                val active = m == mode
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                        .background(if (active) ZIndigo else Color.Transparent)
                        .clickable { vm.revealMode = m }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(m.label, color = if (active) Color.White else ZTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ThemeModeRow(vm: AppViewModel) {
    val mode = vm.themeMode
    val icon = when (mode) {
        com.zmastery.english.data.ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
        com.zmastery.english.data.ThemeMode.LIGHT -> Icons.Filled.LightMode
        com.zmastery.english.data.ThemeMode.DARK -> Icons.Filled.DarkMode
    }
    Column(Modifier.padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = ZAmber)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("وضع العرض", color = ZTextPrimary, fontWeight = FontWeight.SemiBold)
                Text("افتراضياً يتبع إعداد النظام (نهاري/ليلي)", color = ZTextSecondary, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ZSurfaceVariant).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            com.zmastery.english.data.ThemeMode.values().forEach { m ->
                val active = m == mode
                val mIcon = when (m) {
                    com.zmastery.english.data.ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                    com.zmastery.english.data.ThemeMode.LIGHT -> Icons.Filled.LightMode
                    com.zmastery.english.data.ThemeMode.DARK -> Icons.Filled.DarkMode
                }
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                        .background(if (active) ZIndigo else Color.Transparent)
                        .clickable { vm.themeMode = m }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(mIcon, null, tint = if (active) Color.White else ZTextSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(m.label, color = if (active) Color.White else ZTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RetentionRow(vm: AppViewModel) {
    Column(Modifier.padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.TrackChanges, null, tint = ZEmerald)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("الاحتفاظ المستهدف", color = ZTextPrimary, fontWeight = FontWeight.SemiBold)
                Text("نسبة تذكّر الكلمات التي يسعى النظام لضمانها", color = ZTextSecondary, fontSize = 11.sp)
            }
            Text("${(vm.desiredRetention * 100).toInt()}%", color = ZEmerald, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = vm.desiredRetention.toFloat(),
            onValueChange = { vm.desiredRetention = (Math.round(it * 100) / 100.0).coerceIn(0.80, 0.97) },
            valueRange = 0.80f..0.97f,
            colors = SliderDefaults.colors(thumbColor = ZEmerald, activeTrackColor = ZEmerald, inactiveTrackColor = ZBorder),
        )
        Text(
            when {
                vm.desiredRetention >= 0.93 -> "مراجعات أكثر تكراراً · تذكّر أقوى"
                vm.desiredRetention <= 0.83 -> "مراجعات أقل · فترات أطول"
                else -> "توازن مثالي بين الجهد والتذكر"
            },
            color = ZTextMuted, fontSize = 11.sp,
        )
    }
}

@Composable
private fun IntervalRow(vm: AppViewModel) {
    Column(Modifier.padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Event, null, tint = ZAmber)
            Spacer(Modifier.width(12.dp))
            Text("أقصى فاصل بين المراجعات", color = ZTextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${vm.maxIntervalDays} يوم", color = ZAmber, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = vm.maxIntervalDays.toFloat(),
            onValueChange = { vm.maxIntervalDays = it.toInt() },
            valueRange = 90f..730f,
            colors = SliderDefaults.colors(thumbColor = ZAmber, activeTrackColor = ZAmber, inactiveTrackColor = ZBorder),
        )
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, sub: String, checked: Boolean, enabled: Boolean = true, onCheck: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(14.dp).alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = ZIndigo)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ZTextPrimary, fontWeight = FontWeight.SemiBold)
            Text(sub, color = ZTextSecondary, fontSize = 12.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onCheck, enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ZIndigo),
        )
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit = {}) {
    Surface(color = Color.Transparent, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = ZTextSecondary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = ZTextPrimary, fontWeight = FontWeight.SemiBold)
                Text(sub, color = ZTextSecondary, fontSize = 12.sp)
            }
            Icon(Icons.Filled.ChevronLeft, null, tint = ZTextMuted)
        }
    }
}

/** Shows current AI-audio generation progress/status + a manual "generate now" button. */
@Composable
private fun AudioGenStatusRow(vm: AppViewModel) {
    Column(Modifier.padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(ZCyanDeep, ZCyan))),
                contentAlignment = Alignment.Center,
            ) {
                if (vm.isGeneratingAudio) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.Filled.GraphicEq, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("حالة الأصوات المولّدة بالذكاء الاصطناعي", color = ZTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(
                    when {
                        vm.isGeneratingAudio -> "${vm.audioGenDone}/${vm.audioGenTotal} — ${vm.audioGenLabel}"
                        vm.hasPendingAudio -> "${vm.pendingAudioCount} عنصر بانتظار التوليد"
                        else -> "كل الأصوات مولّدة ✓"
                    },
                    color = ZTextSecondary, fontSize = 11.sp,
                )
            }
        }
        vm.lastAudioMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = ZCyanDeep, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        if (vm.isGeneratingAudio) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { vm.stopAudioGeneration() },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ZRose),
            ) {
                Icon(Icons.Filled.Stop, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("إيقاف التوليد الحالي", fontWeight = FontWeight.Bold)
            }
        } else {
            if (vm.hasPendingAudio && vm.aiAudioEnabled) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { vm.generateMissingAudio() },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ZCyanDeep),
                ) {
                    Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("توليد الآن (${vm.pendingAudioCount})", fontWeight = FontWeight.Bold)
                }
            }
            if (vm.aiAudioEnabled) {
                Spacer(Modifier.height(8.dp))
                var confirmRegen by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { confirmRegen = true },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ZPurple),
                ) {
                    Icon(Icons.Filled.Autorenew, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("استبدل كل الأصوات بصوت الذكاء الاصطناعي", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                if (confirmRegen) {
                    AlertDialog(
                        onDismissRequest = { confirmRegen = false },
                        containerColor = ZSurface,
                        icon = { Icon(Icons.Filled.Autorenew, null, tint = ZPurple) },
                        title = { Text("استبدال كل الأصوات؟", color = ZTextPrimary, fontWeight = FontWeight.Black) },
                        text = {
                            Text(
                                "سيُعاد توليد أصوات كل الكلمات والأمثلة والدروس والقصص من جديد بصوت الذكاء الاصطناعي الطبيعي، ويُحذف أي صوت محفوظ سابقاً (بما فيه صوت المحرك المحلي القديم إن وُجد). قد يستغرق هذا وقتاً حسب حجم المحتوى.",
                                color = ZTextSecondary, fontSize = 13.sp,
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = { confirmRegen = false; vm.regenerateAllAudioWithAi() },
                                colors = ButtonDefaults.buttonColors(containerColor = ZPurple),
                                shape = RoundedCornerShape(12.dp),
                            ) { Text("استبدال", fontWeight = FontWeight.Bold, color = Color.White) }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmRegen = false }) {
                                Text("إلغاء", color = ZTextSecondary, fontWeight = FontWeight.Bold)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CloudSyncGroup(vm: AppViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var webIdField by remember { mutableStateOf(vm.googleWebClientId) }
    var showWebIdField by remember { mutableStateOf(false) }

    Surface(shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 5.dp, modifier = Modifier.fillMaxWidth()) {
        Column {
            // ---- Status header ----
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(ZEmerald, ZCyanDeep))),
                    contentAlignment = Alignment.Center,
                ) {
                    if (vm.isSyncingCloud) CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(20.dp))
                    else Icon(Icons.Filled.CloudSync, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("المزامنة السحابية", color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        when {
                            vm.cloudUid == null -> "غير متصل بعد"
                            vm.cloudIsAnonymous -> "حساب مجهول · متصل ومزامَن تلقائياً"
                            else -> "متصل بحساب: ${vm.cloudDisplayName ?: vm.cloudEmail ?: "جوجل"}"
                        },
                        color = ZTextSecondary, fontSize = 11.sp,
                    )
                }
            }
            vm.cloudSyncMessage?.let {
                Text(it, color = ZCyanDeep, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp))
                Spacer(Modifier.height(6.dp))
            }
            Divider(color = ZBorder, modifier = Modifier.padding(horizontal = 14.dp))

            ToggleRow(
                Icons.Filled.CloudSync,
                "تفعيل المزامنة السحابية",
                "سحب الدروس الجديدة المضافة خارج التطبيق (Firestore) + نسخ احتياطي لتقدمك",
                vm.cloudSyncEnabled,
            ) { vm.updateCloudSyncEnabled(it) }

            Divider(color = ZBorder, modifier = Modifier.padding(horizontal = 14.dp))

            ActionRow(
                Icons.Filled.Sync,
                "مزامنة الآن",
                if (vm.isSyncingCloud) "جارٍ المزامنة…" else "تحقق من دروس جديدة وارفع تقدمك",
            ) { if (!vm.isSyncingCloud) vm.syncCloudLessons() }

            Divider(color = ZBorder, modifier = Modifier.padding(horizontal = 14.dp))

            // ---- Google Sign-In (optional — needs Web Client ID once configured) ----
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AccountCircle, null, tint = ZIndigo)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("حساب جوجل", color = ZTextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("اربط حسابك لاستعادة تقدمك على أي جهاز آخر", color = ZTextSecondary, fontSize = 11.sp)
                    }
                    TextButton(onClick = { showWebIdField = !showWebIdField }) {
                        Text(if (showWebIdField) "إخفاء" else "إعداد", color = ZCyan, fontSize = 12.sp)
                    }
                }
                if (showWebIdField) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "الصق Web Client ID من Firebase Console → Authentication → Sign-in method → Google (بعد تفعيله)",
                        color = ZTextMuted, fontSize = 10.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = webIdField,
                        onValueChange = { webIdField = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("xxxx.apps.googleusercontent.com", color = ZTextMuted) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ZSurfaceVariant, unfocusedContainerColor = ZSurfaceVariant,
                            focusedBorderColor = ZIndigo, unfocusedBorderColor = ZBorder,
                            focusedTextColor = ZTextPrimary, unfocusedTextColor = ZTextPrimary,
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { vm.updateGoogleWebClientId(webIdField) },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ZIndigo),
                    ) { Text("حفظ المعرّف", fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { vm.signInWithGoogle(ctx) },
                    enabled = vm.cloudIsAnonymous,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ZEmerald, disabledContainerColor = ZBorder),
                ) {
                    Icon(Icons.Filled.Login, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (vm.cloudIsAnonymous) "تسجيل الدخول بحساب Google" else "متصل بحساب Google ✓",
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (vm.googleWebClientId.isBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("أضف Web Client ID أعلاه لتفعيل تسجيل الدخول بجوجل", color = ZTextMuted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun GeminiKeyRow(vm: AppViewModel) {
    var visible by remember { mutableStateOf(false) }
    Column(Modifier.padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Key, null, tint = ZIndigo)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("مفتاح Gemini API", color = ZTextPrimary, fontWeight = FontWeight.SemiBold)
                Text("لتوليد أصوات النطق الطبيعية بنماذج جوجل", color = ZTextSecondary, fontSize = 11.sp)
            }
            Surface(shape = RoundedCornerShape(8.dp), color = (if (vm.geminiApiKey.isNotBlank()) ZEmerald else ZTextMuted).copy(alpha = 0.15f)) {
                Text(
                    if (vm.geminiApiKey.isNotBlank()) "مفعّل" else "غير مفعّل",
                    color = if (vm.geminiApiKey.isNotBlank()) ZEmerald else ZTextMuted,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = vm.geminiApiKey,
            onValueChange = { vm.geminiApiKey = it.trim() },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("AIza...", color = ZTextMuted) },
            singleLine = true,
            visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, tint = ZTextMuted)
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = ZSurfaceVariant, unfocusedContainerColor = ZSurfaceVariant,
                focusedBorderColor = ZIndigo, unfocusedBorderColor = ZBorder,
                focusedTextColor = ZTextPrimary, unfocusedTextColor = ZTextPrimary,
            ),
        )
        Spacer(Modifier.height(6.dp))
        Text("بدون مفتاح، يعمل النطق عبر محرّك أندرويد المدمج (بلا إنترنت).", color = ZTextMuted, fontSize = 11.sp)
    }
}

private val geminiVoices = listOf("Kore", "Puck", "Charon", "Fenrir", "Aoede", "Leda")

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun VoiceRow(vm: AppViewModel) {
    Column(Modifier.padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.RecordVoiceOver, null, tint = ZCyanDeep)
            Spacer(Modifier.width(12.dp))
            Text("صوت النطق", color = ZTextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            geminiVoices.forEach { v ->
                val sel = vm.ttsVoice == v
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (sel) ZCyanDeep else ZSurfaceVariant,
                    onClick = { vm.ttsVoice = v },
                ) {
                    Text(v, color = if (sel) Color.White else ZTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                }
            }
        }
    }
}
