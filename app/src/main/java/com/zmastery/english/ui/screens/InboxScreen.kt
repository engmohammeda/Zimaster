package com.zmastery.english.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.data.AlertKind
import com.zmastery.english.data.AppAlert
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-app diagnostic inbox — every quota, key, TTS and model failure across
 * the app lands here so the learner can see *what* broke and *why*.
 */
@Composable
fun InboxScreen(vm: AppViewModel, onOpenRoute: (String) -> Unit = {}) {
    var filter by remember { mutableStateOf(0) } // 0 all · 1 errors · 2 quota · 3 unread
    val list = vm.appAlerts.filter { a ->
        when (filter) {
            1 -> a.kindEnum == AlertKind.ERROR || a.kindEnum == AlertKind.WARNING
            2 -> a.kindEnum == AlertKind.QUOTA
            3 -> !a.read
            else -> true
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text("سجل الأعطال والإشعارات", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text(
                "كل ما يتوقف في التطبيق يظهر هنا: الحصص، المفاتيح، الأصوات، النماذج، المحادثة.",
                color = ZTextSecondary, fontSize = 12.sp, lineHeight = 18.sp,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                InboxChip(filter == 0, "الكل (${vm.appAlerts.size})") { filter = 0 }
                InboxChip(filter == 1, "أعطال") { filter = 1 }
                InboxChip(filter == 2, "حصص") { filter = 2 }
                InboxChip(filter == 3, "غير مقروء (${vm.unreadAlertCount})") { filter = 3 }
            }
        }
        if (vm.appAlerts.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { vm.markAllAlertsRead() },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ZIndigo),
                    ) { Text("تعليم الكل كمقروء", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    TextButton(onClick = { vm.clearAlerts() }) {
                        Text("مسح السجل", color = ZRose, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (list.isEmpty()) {
            item { EmptyInbox(vm.appAlerts.isEmpty()) }
        } else {
            items(list, key = { it.id }) { alert ->
                AlertCard(alert, onOpen = {
                    vm.markAlertRead(alert.id)
                    alert.route?.let(onOpenRoute)
                })
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

@Composable
private fun EmptyInbox(totallyEmpty: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.NotificationsNone, null, tint = ZTextMuted, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            if (totallyEmpty) "لا إشعارات بعد" else "لا نتائج في هذا الفلتر",
            color = ZTextSecondary, fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "عند نفاد حصة أو فشل صوت أو مفتاح، يظهر السبب هنا مباشرة.",
            color = ZTextMuted, fontSize = 12.sp,
        )
    }
}

@Composable
private fun InboxChip(active: Boolean, label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (active) ZIndigo else ZCard,
        onClick = onClick,
        shadowElevation = if (active) 0.dp else 2.dp,
    ) {
        Text(
            label,
            color = if (active) Color.White else ZTextSecondary,
            fontSize = 12.sp, fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun AlertCard(alert: AppAlert, onOpen: () -> Unit) {
    val accent = kindColor(alert.kindEnum)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ZCard,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(kindIcon(alert.kindEnum), null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(alert.source, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(8.dp))
                    Text(formatAlertTime(alert.atMillis), color = ZTextMuted, fontSize = 10.sp)
                    if (!alert.read) {
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(ZRose))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(alert.title, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(alert.detail, color = ZTextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                if (!alert.route.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("اضغط للانتقال إلى مكان الإصلاح ←", color = ZIndigo, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun kindColor(kind: AlertKind): Color = when (kind) {
    AlertKind.ERROR -> ZRose
    AlertKind.WARNING -> ZAmber
    AlertKind.QUOTA -> ZPurple
    AlertKind.SUCCESS -> ZEmerald
    AlertKind.INFO -> ZCyanDeep
}

private fun kindIcon(kind: AlertKind): ImageVector = when (kind) {
    AlertKind.ERROR -> Icons.Filled.ErrorOutline
    AlertKind.WARNING -> Icons.Filled.Warning
    AlertKind.QUOTA -> Icons.Filled.MoneyOff
    AlertKind.SUCCESS -> Icons.Filled.CheckCircle
    AlertKind.INFO -> Icons.Filled.Info
}

private fun formatAlertTime(ms: Long): String {
    val fmt = SimpleDateFormat("dd/MM · h:mm a", Locale("ar"))
    return fmt.format(Date(ms))
}
