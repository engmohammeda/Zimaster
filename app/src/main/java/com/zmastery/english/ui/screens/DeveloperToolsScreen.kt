package com.zmastery.english.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
 * مبادئ التصميم في هذه الشاشة:
 *  ١) **الحالة قبل الفعل** — أول ما يراه المسؤول هو هل الاتصال والصلاحية يعملان،
 *     لأن «البث لا يعمل» كان في الحقيقة «السحابة رفضت الكتابة» بلا سبب ظاهر.
 *  ٢) **علامة رفع حقيقية** — كل درس يعرف هل هو في السحابة أم لا، مع عدّاد
 *     لكل كورس وزر «تحقق من السحابة» يطابق المحلي بما هو منشور فعلاً.
 *  ٣) **نتيجة ملوّنة** — النجاح أخضر والفشل أحمر مع السبب والحل، فلا يختلط
 *     الخطأ بالنجاح كما كان (كانت كل الرسائل خضراء).
 *
 * الشاشة محمية: غير المسؤول يرى بطاقة قفل فقط.
 */
@Composable
fun DeveloperToolsScreen(vm: AppViewModel, onOpenImport: () -> Unit) {
    com.zmastery.english.ui.components.TrackStudyTime(vm, "devtools")

    // مطابقة تلقائية مرة واحدة في الجلسة حتى تكون شارة «تم الرفع» صادقة من
    // أول فتح للشاشة (تتضمن الدروس التي رفعها سكربت البايثون خارج التطبيق).
    LaunchedEffect(Unit) {
        if (vm.isAdmin && vm.lessons.isNotEmpty() && vm.lastCloudVerifyMillis == 0L) {
            vm.verifyCloudLessons()
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { DevHero(vm) }

        if (vm.isAdmin) item { StatusStrip(vm) }

        if (!vm.isAdmin) {
            item { DevLockedCard() }
            item { Spacer(Modifier.height(80.dp)) }
            return@LazyColumn
        }

        item { CloudHealthCard(vm) }
        item { PublishLessonsCard(vm, onOpenImport) }
        item { AuthoringPromptsCard(vm) }
        item { AnnouncementCard(vm) }
        item { QuoteCard(vm) }
        item { StudentsCard(vm) }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

/* ═══════════════════════════ الرأس ═══════════════════════════ */

@Composable
private fun DevHero(vm: AppViewModel) {
    val cloudOn = vm.cloudSyncEnabled
    val signedIn = vm.cloudUid != null
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(ZAmberDeep, ZAmber)))
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(50.dp).clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.AdminPanelSettings, null,
                        tint = Color.White, modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "أدوات المطور والمسؤول 👑",
                        color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (vm.isAdmin) "مركز النشر — كل أداة محتوى في مكان واحد"
                        else "هذه المنطقة للمسؤول فقط",
                        color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp, lineHeight = 17.sp,
                    )
                }
            }

            if (vm.isAdmin) {
                Spacer(Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                ) {
                    DevChip(
                        if (vm.userRole == "admin") "دور سحابي: مسؤول" else "دور سحابي: ${vm.userRole}",
                        if (vm.userRole == "admin") Icons.Filled.Verified else Icons.Filled.Person,
                    )
                    DevChip(
                        when {
                            signedIn && vm.cloudIsAnonymous -> "حساب سحابي ضيف"
                            signedIn -> vm.cloudEmail ?: "حساب سحابي"
                            else -> "لا يوجد حساب"
                        },
                        if (signedIn) Icons.Filled.Person else Icons.Filled.CloudOff,
                    )
                    DevChip(
                        if (cloudOn) "المزامنة مفعّلة" else "المزامنة متوقفة",
                        if (cloudOn) Icons.Filled.CloudSync else Icons.Filled.CloudOff,
                    )
                }

                // تحذير صريح: صلاحية محلية لا تكفي للكتابة في السحابة.
                if (vm.isLocalOnlyAdmin) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = 0.18f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Warning, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "وضع المطور مفتوح محلياً فقط — السحابة لن تقبل النشر إلا من حساب المالك.",
                            color = Color.White, fontSize = 10.sp, lineHeight = 15.sp,
                        )
                    }
                }

                // بريدك مطابق للمالك لكنه غير موثّق بعد — أقرب سبب وأسهل حل.
                if (vm.ownerEmailUnverified) {
                    Spacer(Modifier.height(10.dp))
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = 0.18f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.MarkEmailUnread, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "بريدك مطابق لحساب المالك لكنه غير موثّق — وثّقه لتفعيل النشر السحابي.",
                                color = Color.White, fontSize = 10.sp, lineHeight = 15.sp,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        Row {
                            TextButton(onClick = {
                                vm.resendEmailVerification { _, msg ->
                                    android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
                                }
                            }) { Text("إعادة إرسال رابط التوثيق", color = Color.White, fontSize = 11.sp) }
                            TextButton(onClick = {
                                vm.refreshEmailVerification { verified ->
                                    val msg = if (verified) "تم التوثيق ✓ — صلاحيات المالك مفعّلة الآن" else "لم يُوثَّق البريد بعد"
                                    android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
                                }
                            }) { Text("تحققت — أعد الفحص", color = Color.White, fontSize = 11.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DevChip(label: String, icon: ImageVector) {
    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.20f)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DevLockedCard() {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(ZSurfaceVariant),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Lock, null, tint = ZTextMuted, modifier = Modifier.size(30.dp)) }
            Spacer(Modifier.height(14.dp))
            Text("محتوى مخصص للمسؤول", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "إن كنت مالك التطبيق فعّل وضع المطور من الإعدادات، أو سجّل الدخول بحسابك المالك.",
                color = ZTextSecondary, fontSize = 12.sp, lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

/* ═══════════════════ شريط الحالة السريع (٣ مؤشرات) ═══════════════════ */

@Composable
private fun StatusStrip(vm: AppViewModel) {
    val published = vm.publishedLessonsCount
    val total = vm.lessons.size
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile(
            modifier = Modifier.weight(1f),
            value = "$published/$total",
            label = "درس مرفوع",
            icon = if (total > 0 && published == total) Icons.Filled.TaskAlt else Icons.Filled.CloudUpload,
            tint = if (total > 0 && published == total) ZEmerald else ZIndigo,
        )
        StatTile(
            modifier = Modifier.weight(1f),
            value = "${vm.cloudQuoteCount}",
            label = "عبارة منشورة",
            icon = Icons.Filled.FormatQuote,
            tint = ZAmberDeep,
        )
        StatTile(
            modifier = Modifier.weight(1f),
            value = "${vm.registeredUsersList.size}",
            label = "طالب مسجل",
            icon = Icons.Filled.Group,
            tint = ZCyanDeep,
        )
    }
}

@Composable
private fun StatTile(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    icon: ImageVector,
    tint: Color,
) {
    SoftCard(modifier = modifier) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp)) }
            Spacer(Modifier.height(8.dp))
            Text(value, color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 17.sp)
            Text(
                label, color = ZTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* ═══════════════ 0) تشخيص الاتصال والصلاحية ═══════════════ */

@Composable
private fun CloudHealthCard(vm: AppViewModel) {
    var probeResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SectionHeader(
                icon = Icons.Filled.NetworkCheck,
                tint = ZCyanDeep,
                title = "تشخيص الاتصال والنشر",
                subtitle = "اعرف فوراً هل السحابة تقبل النشر منك ولماذا",
            )
            Spacer(Modifier.height(12.dp))

            HealthRow("الحساب السحابي", accountLabel(vm), vm.cloudUid != null)
            HealthRow("المزامنة السحابية", if (vm.cloudSyncEnabled) "مفعّلة" else "متوقفة من الإعدادات", vm.cloudSyncEnabled)
            HealthRow(
                "الدور في السحابة",
                when (vm.cloudRoleDoc) {
                    "admin" -> "مسؤول (admin)"
                    "student" -> "طالب (student)"
                    "no-doc" -> "لا يوجد مستند لهذا الحساب"
                    null -> "لم يُفحص بعد — اضغط «فحص الآن»"
                    else -> vm.cloudRoleDoc ?: "غير معروف"
                },
                vm.cloudRoleDoc == "admin" || vm.userRole == "admin",
            )
            HealthRow(
                "صلاحية الكتابة",
                if (vm.isProbingCloud) "جارٍ الفحص…"
                else probeResult?.let { if (it.first) "تعمل" else "مرفوضة" } ?: "لم تُختبر بعد",
                probeResult?.first ?: false,
            )

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    probeResult = null
                    vm.probePublishPermission { ok, msg -> probeResult = ok to msg }
                },
                enabled = !vm.isProbingCloud,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ZCyanDeep),
            ) {
                if (vm.isProbingCloud) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Icon(Icons.Filled.Radar, null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (vm.isProbingCloud) "جارٍ الفحص…" else "فحص الآن (كتابة تجريبية)",
                    fontWeight = FontWeight.Bold, fontSize = 13.sp,
                )
            }

            probeResult?.let { (ok, msg) ->
                Spacer(Modifier.height(10.dp))
                StatusLine(ok = ok, text = msg)
            }
        }
    }
}

private fun accountLabel(vm: AppViewModel): String = when {
    vm.cloudUid == null -> "غير متصل"
    vm.cloudIsAnonymous -> "حساب ضيف (بلا بريد) — لا يملك صلاحية النشر"
    else -> vm.cloudEmail ?: "حساب مرتبط"
}

@Composable
private fun HealthRow(label: String, value: String, ok: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            null, tint = if (ok) ZEmerald else ZTextMuted, modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = ZTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text(
            value, color = ZTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f), textAlign = TextAlign.End,
        )
    }
}

/* ═══════════════ 1) رفع ونشر الدروس + علامة «تم الرفع» ═══════════════ */

@Composable
private fun PublishLessonsCard(vm: AppViewModel, onOpenImport: () -> Unit) {
    var isPublishingAll by remember { mutableStateOf(false) }
    var publishStatus by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    val total = vm.lessons.size
    val published = vm.publishedLessonsCount
    val progress = if (total == 0) 0f else published.toFloat() / total.toFloat()
    val allDone = total > 0 && published == total

    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SectionHeader(
                icon = Icons.Filled.CloudUpload,
                tint = ZIndigo,
                title = "رفع ونشر الدروس",
                subtitle = "ملفات JSON/ZIP أو مزامنة دروسك المحلية ($total درس)",
                trailing = {
                    // ✨ علامة «تم الرفع» العامة — خضراء عندما يكتمل الرفع.
                    PublishedPill(published = published, total = total)
                },
            )

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = if (allDone) ZEmerald else ZIndigo,
                    trackColor = ZBorder,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "${(progress * 100).toInt()}%",
                    color = if (allDone) ZEmerald else ZIndigo,
                    fontSize = 12.sp, fontWeight = FontWeight.Black,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    total == 0 -> "لا توجد دروس محلية بعد — ابدأ باستيراد ملف"
                    allDone -> "كل الدروس موجودة في السحابة ✓ — آخر تحديث ${stampLabel(vm.lastLessonPublishMillis)}"
                    else -> "مرفوع $published من $total · يتبقى ${total - published} درساً"
                },
                color = if (allDone) ZEmeraldDeep else ZTextSecondary,
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            )

            // تفصيل حسب الكورس: أي كورس مكتمل الرفع وأيها ناقص.
            val perCourse = vm.courses
                .map { c -> c to vm.lessons.filter { it.courseId == c.id } }
                .filter { it.second.isNotEmpty() }
            if (perCourse.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = ZSurfaceVariant.copy(alpha = 0.6f)) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        perCourse.forEach { (course, lessons) ->
                            val done = lessons.count { it.isPublishedToCloud }
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(8.dp).clip(CircleShape)
                                        .background(if (done == lessons.size) ZEmerald else ZAmber),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    course.name, color = ZTextPrimary, fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    if (done == lessons.size) Icons.Filled.TaskAlt else Icons.Filled.CloudSync,
                                    null,
                                    tint = if (done == lessons.size) ZEmerald else ZTextMuted,
                                    modifier = Modifier.size(13.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "$done/${lessons.size}",
                                    color = if (done == lessons.size) ZEmeraldDeep else ZTextSecondary,
                                    fontSize = 11.sp, fontWeight = FontWeight.Black,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onOpenImport,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ZIndigo),
                ) {
                    Icon(Icons.Filled.UploadFile, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("استيراد ملفات", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        isPublishingAll = true
                        publishStatus = null
                        vm.publishAllLocalLessonsToCloud { success, msg ->
                            isPublishingAll = false
                            publishStatus = success to msg
                        }
                    },
                    enabled = !isPublishingAll && !vm.isVerifyingCloudLessons && total > 0,
                    modifier = Modifier.weight(1.2f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ZAmber),
                ) {
                    if (isPublishingAll) {
                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.RocketLaunch, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isPublishingAll) "جارٍ الرفع…" else "🚀 نشر الكل ($total)",
                        color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            // «تحقق من السحابة»: يطابق معرّفات الدروس بما هو منشور فعلاً —
            // يكشف أيضاً ما رفعه سكربت البايثون خارج التطبيق.
            TextButton(
                onClick = {
                    publishStatus = null
                    vm.verifyCloudLessons { ok, msg -> publishStatus = ok to msg }
                },
                enabled = !vm.isVerifyingCloudLessons && !isPublishingAll,
                modifier = Modifier.fillMaxWidth().height(38.dp),
            ) {
                if (vm.isVerifyingCloudLessons) {
                    CircularProgressIndicator(color = ZCyan, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                } else {
                    Icon(Icons.Filled.FactCheck, null, tint = ZCyan, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    if (vm.isVerifyingCloudLessons) "جارٍ مطابقة دروس السحابة…"
                    else "تحقق من السحابة (تحديث علامة الرفع)",
                    color = ZCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                )
            }

            // رسالة الحالة الدائمة من آخر عملية نشر/فحص — وتُخفى أثناء العمل الجاري
            // حتى لا تظهر رسالة حمراء وسط «جارٍ الرفع…».
            val live = publishStatus
                ?: if (isPublishingAll || vm.isVerifyingCloudLessons) null
                   else vm.cloudPublishMessage?.let { (published == total) to it }
            live?.let { (ok, msg) ->
                Spacer(Modifier.height(4.dp))
                StatusLine(ok = ok, text = msg)
            }
        }
    }
}

/** شارة «تم الرفع» — خضراء مكتملة أو كهرمانية ناقصة. */
@Composable
private fun PublishedPill(published: Int, total: Int) {
    val done = total > 0 && published == total
    val tint = if (done) ZEmerald else ZAmber
    Surface(shape = RoundedCornerShape(50), color = tint.copy(alpha = 0.14f)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Icon(
                if (done) Icons.Filled.CheckCircle else Icons.Filled.CloudSync,
                null, tint = tint, modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                when {
                    total == 0 -> "لا دروس"
                    done -> "مرفوع ✓"
                    else -> "$published/$total مرفوع"
                },
                color = if (done) ZEmeraldDeep else ZAmberDeep,
                fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1,
            )
        }
    }
}

/* ═══════════════════════ 2) بث إعلان ═══════════════════════ */

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
    var status by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SectionHeader(
                icon = Icons.Filled.Campaign,
                tint = ZCyanDeep,
                title = "بث إعلان لجميع الطلاب",
                subtitle = "يظهر أعلى لوحة القيادة في كل الأجهزة",
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = title, onValueChange = { if (it.length <= 60) title = it },
                label = { Text("عنوان الإعلان") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                supportingText = { Text("${title.length}/60", fontSize = 10.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ZSurfaceVariant, unfocusedContainerColor = ZSurfaceVariant,
                    focusedBorderColor = ZCyanDeep, unfocusedBorderColor = ZBorder,
                    focusedTextColor = ZTextPrimary, unfocusedTextColor = ZTextPrimary,
                ),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = message, onValueChange = { if (it.length <= 400) message = it },
                label = { Text("نص الإعلان") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                supportingText = { Text("${message.length}/400", fontSize = 10.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ZSurfaceVariant, unfocusedContainerColor = ZSurfaceVariant,
                    focusedBorderColor = ZCyanDeep, unfocusedBorderColor = ZBorder,
                    focusedTextColor = ZTextPrimary, unfocusedTextColor = ZTextPrimary,
                ),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                AnnouncementTypes.forEach { (key, label) ->
                    val selected = type == key
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (selected) ZCyanDeep else ZSurfaceVariant,
                        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, ZBorder),
                        onClick = { type = key },
                    ) {
                        Text(
                            label,
                            color = if (selected) Color.White else ZTextSecondary,
                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }
            }

            // معاينة حيّة: كما سيراه الطالب بالضبط — بلا تخمين.
            if (title.isNotBlank() || message.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text("هكذا سيظهر للطالب:", color = ZTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                AnnouncementPreview(
                    title = title.ifBlank { "إشعار عام" },
                    message = message.ifBlank { "…" },
                    type = type,
                )
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    isSending = true; status = null
                    vm.postAnnouncement(title, message, type) { success, msg ->
                        isSending = false
                        status = success to msg
                        if (success) { title = ""; message = "" }
                    }
                },
                enabled = !isSending && title.isNotBlank() && message.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ZCyanDeep),
            ) {
                if (isSending) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Icon(Icons.Filled.Send, null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isSending) "جارٍ البث…" else "بث الإعلان لكل الطلاب",
                    fontWeight = FontWeight.Bold, fontSize = 13.sp,
                )
            }

            status?.let { (ok, msg) ->
                Spacer(Modifier.height(10.dp))
                StatusLine(ok = ok, text = msg)
            }
        }
    }
}

/** نسخة مصغّرة من بطاقة الإعلان في لوحة القيادة — للمعاينة قبل البث. */
@Composable
private fun AnnouncementPreview(title: String, message: String, type: String) {
    val (bgColor, accentColor, icon) = when (type) {
        "alert" -> Triple(ZRose.copy(alpha = 0.15f), ZRose, Icons.Filled.Warning)
        "update" -> Triple(ZEmerald.copy(alpha = 0.15f), ZEmerald, Icons.Filled.Celebration)
        "challenge" -> Triple(ZAmber.copy(alpha = 0.15f), ZAmber, Icons.Filled.EmojiEvents)
        else -> Triple(ZCyan.copy(alpha = 0.15f), ZCyan, Icons.Filled.Campaign)
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ZSurface,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, accentColor.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.background(bgColor).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = accentColor, modifier = Modifier.size(16.dp)) }
                Spacer(Modifier.width(10.dp))
                Text(
                    title, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                message, color = ZTextSecondary, fontSize = 12.sp, lineHeight = 18.sp,
                maxLines = 4, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* ═══════════════════════ 3) عبارات التحفيز ═══════════════════════ */

@Composable
private fun QuoteCard(vm: AppViewModel) {
    var text by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SectionHeader(
                icon = Icons.Filled.FormatQuote,
                tint = ZAmberDeep,
                title = "عبارات التحفيز اليومية",
                subtitle = "عبارة عشوائية يومياً في ودجت كل متعلم",
                trailing = {
                    TextButton(onClick = { vm.syncQuotes() }) {
                        Text("${vm.cloudQuoteCount} عبارة 🔄", color = ZCyan, fontSize = 11.sp)
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text, onValueChange = { if (it.length <= 300) text = it },
                label = { Text("نص العبارة") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                supportingText = { Text("${text.length}/300", fontSize = 10.sp) },
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
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    status = null
                    vm.addQuote(text, author) { success, msg ->
                        status = success to msg
                        if (success) { text = ""; author = "" }
                    }
                },
                enabled = !vm.isAddingQuote && text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ZAmber),
            ) {
                if (vm.isAddingQuote) {
                    CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Icon(Icons.Filled.AutoAwesome, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (vm.isAddingQuote) "جارٍ النشر…" else "نشر العبارة لكل الأجهزة",
                    color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                )
            }
            status?.let { (ok, msg) ->
                Spacer(Modifier.height(10.dp))
                StatusLine(ok = ok, text = msg)
            }
        }
    }
}

/* ═══════════════════════ 4) الطلاب المسجلون ═══════════════════════ */

@Composable
private fun StudentsCard(vm: AppViewModel) {
    var query by remember { mutableStateOf("") }
    val all = vm.registeredUsersList
    val shown = if (query.isBlank()) all else all.filter {
        it.displayName?.contains(query, true) == true || it.email?.contains(query, true) == true
    }

    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SectionHeader(
                icon = Icons.Filled.Group,
                tint = ZEmerald,
                title = "الطلاب المسجلون سحابياً",
                subtitle = "${all.size} مسجل · آخرهم أعلى القائمة",
                trailing = {
                    TextButton(onClick = { vm.loadRegisteredUsers() }, enabled = !vm.isLoadingUsers) {
                        if (vm.isLoadingUsers) {
                            CircularProgressIndicator(color = ZCyan, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                        } else {
                            Icon(Icons.Filled.Refresh, null, tint = ZCyan, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("تحديث", color = ZCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
            )
            Spacer(Modifier.height(10.dp))

            if (all.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.PersonOutline, null, tint = ZTextMuted, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("لا توجد قائمة بعد", color = ZTextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(
                        "اضغط «تحديث» لسحب المستخدمين من السحابة.",
                        color = ZTextMuted, fontSize = 11.sp,
                    )
                }
            } else {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    label = { Text("ابحث بالاسم أو البريد") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = ZTextMuted, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ZSurfaceVariant, unfocusedContainerColor = ZSurfaceVariant,
                        focusedBorderColor = ZEmerald, unfocusedBorderColor = ZBorder,
                        focusedTextColor = ZTextPrimary, unfocusedTextColor = ZTextPrimary,
                    ),
                )
                Spacer(Modifier.height(6.dp))
                if (shown.isEmpty()) {
                    Text(
                        "لا نتائج لـ «$query»",
                        color = ZTextMuted, fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    )
                } else {
                    shown.forEach { user ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(36.dp).clip(CircleShape)
                                    .background(
                                        if (user.role == "admin") ZAmber.copy(alpha = 0.18f)
                                        else ZSurfaceVariant
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    (user.displayName?.take(1) ?: "U").uppercase(),
                                    color = if (user.role == "admin") ZAmberDeep else ZTextPrimary,
                                    fontWeight = FontWeight.Black, fontSize = 13.sp,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        user.displayName ?: "مستخدم",
                                        color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    if (user.role == "admin") {
                                        Spacer(Modifier.width(4.dp))
                                        Text("👑", fontSize = 10.sp)
                                    }
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
}

/* ═══════════════════ مكوّنات مشتركة في الشاشة ═══════════════════ */

/** رأس موحّد لكل البطاقات: أيقونة ملوّنة + عنوان + وصف + عنصر إضافي اختياري. */
@Composable
private fun SectionHeader(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
            Text(
                subtitle, color = ZTextSecondary, fontSize = 11.sp, lineHeight = 15.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

/**
 * سطر النتيجة الموحّد: أخضر للنجاح وأحمر للفشل.
 *
 * قبل هذا كانت رسائل النجاح والفشل تُعرض بلون أخضر واحد — فكان فشل النشر يبدو
 * وكأنه تم، وهذا بالضبط ما جعل «نشر إعلان/عبارة» يبدو معطلاً بلا سبب.
 */
@Composable
private fun StatusLine(ok: Boolean, text: String) {
    val tint = if (ok) ZEmeraldDeep else ZRoseDeep
    val bg = if (ok) ZEmerald.copy(alpha = 0.10f) else ZRose.copy(alpha = 0.10f)
    Surface(shape = RoundedCornerShape(12.dp), color = bg) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                if (ok) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                null, tint = tint, modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text, color = tint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp,
            )
        }
    }
}

/** طابع زمني مختصر بالعربية: «26 أغسطس · 14:05». */
private fun stampLabel(millis: Long): String {
    if (millis <= 0L) return "—"
    return runCatching {
        java.text.SimpleDateFormat("dd MMMM · HH:mm", java.util.Locale.forLanguageTag("ar"))
            .format(java.util.Date(millis))
    }.getOrDefault("—")
}

/* ─────────────────────── ٥) مطالبات تأليف المناهج ─────────────────────── */

/**
 * مطالبتا التأليف (إنشاء المنهج التخصصي + استخراج الدروس الموحّد) مضمّنتان
 * كـ assets وتُعرضان للنسخ هنا — للمسؤول فقط، لأن الشاشة كلها محمية بـ isAdmin.
 * المصدر الوحيد للمحتوى: ملفا *.md في جذر المستودع (يُنسخان إلى assets).
 */
@Composable
private fun AuthoringPromptsCard(vm: AppViewModel) {
    val clipboard = LocalClipboardManager.current
    val ctx = vm.app
    val builder = remember {
        runCatching { ctx.assets.open("prompts/ESP_COURSE_BUILDER_PROMPT.md").bufferedReader().readText() }
            .getOrDefault("")
    }
    val extractor = remember {
        runCatching { ctx.assets.open("prompts/UNIFIED_LESSON_EXTRACTOR_MASTER_PROMPT.md").bufferedReader().readText() }
            .getOrDefault("")
    }
    var preview by remember { mutableStateOf<Pair<String, String>?>(null) }
    var copied by remember { mutableStateOf<String?>(null) }

    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SectionHeader(
                icon = Icons.Filled.ContentCopy,
                tint = ZPurple,
                title = "مطالبات تأليف المناهج 🤖",
                subtitle = "انسخها لأي وكيل AI فيكتب منهجاً أو دروساً بمخطط التطبيق تماماً",
            )
            Spacer(Modifier.height(12.dp))

            PromptRow(
                name = "مُنشئ المناهج التخصصية (ESP)",
                desc = "منهج كامل لمجال مهني → JSON جاهز للصق في «لصق JSON من AI»",
                chars = builder.length,
                copied = copied == "builder",
                onCopy = { clipboard.setText(AnnotatedString(builder)); copied = "builder" },
                onPreview = { preview = "مُنشئ المناهج التخصصية (ESP)" to builder },
            )
            Spacer(Modifier.height(8.dp))
            PromptRow(
                name = "مستخرج الدروس الموحّد (ZAmerican)",
                desc = "تحويل نص حلقة كورس ذا أمريكان → درس JSON بمخطط البلوكات",
                chars = extractor.length,
                copied = copied == "extractor",
                onCopy = { clipboard.setText(AnnotatedString(extractor)); copied = "extractor" },
                onPreview = { preview = "مستخرج الدروس الموحّد (ZAmerican)" to extractor },
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    clipboard.setText(
                        AnnotatedString(
                            "# ١) مطالبة إنشاء المنهج\n$builder\n\n═══ الفاصل ═══\n\n# ٢) مطالبة استخراج الدروس\n$extractor"
                        )
                    )
                    copied = "merged"
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ZPurple),
            ) {
                Icon(
                    if (copied == "merged") Icons.Filled.CheckCircle else Icons.Filled.ContentCopy,
                    null, modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (copied == "merged") "نُسخت المطالبتان معاً ✓" else "نسخ المطالبتين معاً (مدموج)",
                    fontWeight = FontWeight.Bold, fontSize = 12.sp,
                )
            }
        }
    }

    preview?.let { (title, text) ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(title, color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 14.sp) },
            text = {
                Column {
                    Text(
                        text, color = ZTextSecondary, fontSize = 10.sp, lineHeight = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(text)); copied = title; preview = null
                }) { Text("نسخ الكل", color = ZPurple, fontWeight = FontWeight.Black) }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text("إغلاق", color = ZTextSecondary) } },
        )
    }
}

@Composable
private fun PromptRow(
    name: String,
    desc: String,
    chars: Int,
    copied: Boolean,
    onCopy: () -> Unit,
    onPreview: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ZSurfaceVariant.copy(alpha = 0.6f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(desc, color = ZTextSecondary, fontSize = 10.sp, lineHeight = 15.sp)
            Text("الحجم: $chars حرفاً", color = ZTextMuted, fontSize = 9.sp)
        }
        TextButton(onClick = onPreview) { Text("عرض", color = ZTextSecondary, fontSize = 11.sp) }
        TextButton(onClick = onCopy) {
            Text(
                if (copied) "نُسخ ✓" else "نسخ",
                color = if (copied) ZEmeraldDeep else ZPurple, fontSize = 11.sp, fontWeight = FontWeight.Black,
            )
        }
    }
}
