package com.zmastery.english.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.R
import com.zmastery.english.notify.NotifPrefs
import com.zmastery.english.notify.NotifScheduler
import com.zmastery.english.notify.Notifier
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel

@Composable
fun NotificationSection(vm: AppViewModel) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity

    var enabled by remember { mutableStateOf(NotifPrefs.enabled(ctx)) }
    var streakAlerts by remember { mutableStateOf(NotifPrefs.streakAlerts(ctx)) }
    var hour by remember { mutableStateOf(NotifPrefs.hour(ctx)) }
    var minute by remember { mutableStateOf(NotifPrefs.minute(ctx)) }
    var granted by remember { mutableStateOf(hasPermission(ctx)) }
    var showTimePicker by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (ok) {
            NotifPrefs.setEnabled(ctx, true)
            enabled = true
            NotifScheduler.rescheduleAll(ctx)
            vm.syncNotifState()
        }
    }

    fun requestPerm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            granted = true
            NotifPrefs.setEnabled(ctx, true); enabled = true
            NotifScheduler.rescheduleAll(ctx); vm.syncNotifState()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // ===== Hero preview =====
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(ZAmber, ZRose))).padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.NotificationsActive, null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("ابقَ على المسار الصحيح", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text("تذكيرات ذكية بأصوات مميزة تحافظ على حماستك", color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp)
                    }
                }
            }
        }

        // ===== Permission gate =====
        if (!granted) {
            SettingsGroup("الإذن مطلوب") {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, null, tint = ZRose)
                        Spacer(Modifier.width(12.dp))
                        Text("فعّل إذن الإشعارات لتلقّي التذكيرات", color = ZTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { requestPerm() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ZIndigo),
                    ) {
                        Icon(Icons.Filled.NotificationsActive, null); Spacer(Modifier.width(8.dp))
                        Text("السماح بالإشعارات", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ===== Master toggle =====
        SettingsGroup("الإعدادات العامة") {
            ToggleRowN(
                Icons.Filled.Notifications, "تفعيل الإشعارات", "التذكيرات وتنبيهات الحماسة",
                enabled && granted,
            ) { v ->
                if (v && !granted) { requestPerm(); return@ToggleRowN }
                enabled = v
                NotifPrefs.setEnabled(ctx, v)
                if (v) NotifScheduler.rescheduleAll(ctx) else NotifScheduler.cancelAll(ctx)
                vm.syncNotifState()
            }
        }

        // ===== Daily reminder time =====
        SettingsGroup("التذكير اليومي") {
            Surface(color = Color.Transparent, onClick = { if (enabled && granted) showTimePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, null, tint = ZCyanDeep)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("وقت الدراسة الثابت", color = ZTextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("يُذكّرك يومياً في هذا الوقت", color = ZTextSecondary, fontSize = 12.sp)
                    }
                    Surface(shape = RoundedCornerShape(12.dp), color = ZSurfaceVariant) {
                        Text(formatTime(hour, minute), color = ZIndigo, fontWeight = FontWeight.Black, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
            SoundPreviewRow("صوت التذكير اليومي", R.raw.notify_daily, ZCyanDeep)
        }

        // ===== Streak / motivation alerts =====
        SettingsGroup("تنبيهات الحماسة") {
            ToggleRowN(
                Icons.Filled.LocalFireDepartment, "الإزعاج الإيجابي", "تنبيه مسائي إذا لم تُكمل مهامك",
                streakAlerts && enabled && granted,
            ) { v ->
                streakAlerts = v
                NotifPrefs.setStreakAlerts(ctx, v)
                NotifScheduler.rescheduleAll(ctx)
            }
            SoundPreviewRow("صوت تنبيه الحماسة", R.raw.notify_streak, ZRose)
            SoundPreviewRow("صوت الإنجاز", R.raw.notify_success, ZEmerald)
        }

        // ===== Test + system settings =====
        SettingsGroup("اختبار") {
            Surface(color = Color.Transparent, onClick = {
                if (!granted) requestPerm() else NotifScheduler.fireTest(ctx)
            }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Send, null, tint = ZIndigo)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("إرسال إشعار تجريبي", color = ZTextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("عايِن التصميم والصوت الآن", color = ZTextSecondary, fontSize = 12.sp)
                    }
                    Icon(Icons.Filled.ChevronLeft, null, tint = ZTextMuted)
                }
            }
            Surface(color = Color.Transparent, onClick = {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                runCatching { ctx.startActivity(intent) }
            }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Tune, null, tint = ZTextSecondary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("إعدادات النظام", color = ZTextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("تحكم دقيق بالقنوات والأصوات", color = ZTextSecondary, fontSize = 12.sp)
                    }
                    Icon(Icons.Filled.OpenInNew, null, tint = ZTextMuted, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(60.dp))
    }

    if (showTimePicker) {
        TimePickerDialogN(
            initialHour = hour, initialMinute = minute,
            onConfirm = { h, m ->
                hour = h; minute = m
                NotifPrefs.setTime(ctx, h, m)
                NotifScheduler.rescheduleAll(ctx)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialogN(initialHour: Int, initialMinute: Int, onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ZSurface,
        title = { Text("وقت التذكير اليومي", color = ZTextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        selectorColor = ZIndigo,
                        timeSelectorSelectedContainerColor = ZIndigo.copy(alpha = 0.15f),
                        timeSelectorSelectedContentColor = ZIndigo,
                        periodSelectorSelectedContainerColor = ZIndigo.copy(alpha = 0.15f),
                        periodSelectorSelectedContentColor = ZIndigo,
                        clockDialColor = ZSurfaceVariant,
                    ),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("حفظ", color = ZIndigo, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = ZTextSecondary) } },
    )
}

@Composable
private fun ToggleRowN(icon: ImageVector, title: String, sub: String, checked: Boolean, onCheck: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = ZIndigo)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ZTextPrimary, fontWeight = FontWeight.SemiBold)
            Text(sub, color = ZTextSecondary, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheck, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ZIndigo))
    }
}

@Composable
private fun SoundPreviewRow(label: String, soundRes: Int, tint: Color) {
    val ctx = LocalContext.current
    Surface(color = Color.Transparent, onClick = {
        runCatching {
            val uri = Uri.parse("android.resource://${ctx.packageName}/$soundRes")
            MediaPlayer.create(ctx, uri)?.apply {
                setOnCompletionListener { it.release() }
                start()
            }
        }
    }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.VolumeUp, null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(label, color = ZTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.PlayArrow, null, tint = tint)
        }
    }
}

private fun hasPermission(ctx: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return androidx.core.content.ContextCompat.checkSelfPermission(
        ctx, Manifest.permission.POST_NOTIFICATIONS,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

private fun formatTime(h: Int, m: Int): String {
    val period = if (h < 12) "ص" else "م"
    val hr = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
    val mm = m.toString().padStart(2, '0')
    return "$hr:$mm $period"
}
