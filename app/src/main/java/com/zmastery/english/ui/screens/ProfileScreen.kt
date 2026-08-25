package com.zmastery.english.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(vm: AppViewModel, onBack: () -> Unit) {
    var name by remember { mutableStateOf(vm.learnerName) }
    var email by remember { mutableStateOf(vm.learnerEmail) }
    var editing by remember { mutableStateOf(false) }

    val joinedDate = remember {
        SimpleDateFormat("dd MMM yyyy", Locale("ar")).format(Date())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الملف الشخصي", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        containerColor = Color.Transparent,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Avatar + Name header
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = ZCard,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(ZIndigo, ZCyanDeep))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Person,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(56.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        name.ifBlank { "مستخدم" },
                        color = ZTextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                    )
                    if (email.isNotBlank()) {
                        Text(
                            email,
                            color = ZTextSecondary,
                            fontSize = 14.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ZEmerald.copy(alpha = 0.15f),
                    ) {
                        Text(
                            "انضم $joinedDate",
                            color = ZEmerald,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            // Stats cards
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard(
                    icon = Icons.Filled.LocalFireDepartment,
                    label = "السلسلة",
                    value = "${vm.streak} يوم",
                    color = ZAmber,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    icon = Icons.Filled.Star,
                    label = "النقاط",
                    value = "${vm.xp}",
                    color = ZPurple,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    icon = Icons.Filled.MenuBook,
                    label = "الكلمات",
                    value = "${vm.vocab.size}",
                    color = ZCyanDeep,
                    modifier = Modifier.weight(1f),
                )
            }

            // Edit profile card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = ZCard,
                shadowElevation = 5.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Edit, null, tint = ZIndigo)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "تعديل البيانات",
                            color = ZTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { editing = !editing }) {
                            Text(
                                if (editing) "إلغاء" else "تعديل",
                                color = ZCyan,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("الاسم") },
                        leadingIcon = { Icon(Icons.Filled.Person, null) },
                        enabled = editing,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ZIndigo,
                            unfocusedBorderColor = ZBorder,
                        ),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("البريد الإلكتروني") },
                        leadingIcon = { Icon(Icons.Filled.Email, null) },
                        enabled = editing,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ZIndigo,
                            unfocusedBorderColor = ZBorder,
                        ),
                    )

                    if (editing) {
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                vm.learnerName = name
                                vm.learnerEmail = email
                                vm.persist()
                                editing = false
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ZEmerald),
                        ) {
                            Icon(Icons.Filled.Check, null)
                            Spacer(Modifier.width(8.dp))
                            Text("حفظ التغييرات", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Account info
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ZCard,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "معلومات الحساب والمزامنة",
                            color = ZTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                        if (vm.isAdmin) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ZAmber.copy(alpha = 0.2f),
                            ) {
                                Text(
                                    "👑 مدير النظام",
                                    color = ZAmber,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    InfoRow(Icons.Filled.Storage, "التخزين المحلي", "محلي دائم (Room DB + DataStore)")
                    InfoRow(
                        Icons.Filled.CloudSync,
                        "المزامنة السحابية",
                        if (vm.cloudSyncEnabled) "مفعّلة (Firebase Firestore) ✓" else "معطلة",
                    )
                    InfoRow(
                        Icons.Filled.Security,
                        "المصادقة وحالة الحساب",
                        if (!vm.cloudIsAnonymous && vm.cloudUid != null) {
                            "حساب Google (${vm.cloudEmail ?: vm.learnerEmail})"
                        } else {
                            "حساب ضيف (غير مربوط بجوجل)"
                        },
                    )
                    if (vm.cloudIsAnonymous) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "💡 يمكنك ربط حسابك بجوجل من الإعدادات لمزامنة تقدمك والظهور في لوحة الشرف.",
                            color = ZTextMuted,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ZCard,
        shadowElevation = 4.dp,
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(label, color = ZTextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = ZTextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = ZTextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = ZTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
