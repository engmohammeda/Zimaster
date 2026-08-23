package com.zmastery.english.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.zmastery.english.cloud.CloudAuth
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel

@Composable
fun LoginScreen(vm: AppViewModel, onFinish: () -> Unit) {
    val ctx = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var isSigningIn by remember { mutableStateOf(false) }
    var showAdminDialog by remember { mutableStateOf(false) }
    var adminCodeInput by remember { mutableStateOf("") }
    var adminError by remember { mutableStateOf<String?>(null) }

    // Observe cloud auth state changes
    val signedIn = vm.cloudUid != null && !vm.cloudIsAnonymous

    // Google Sign-In launcher using standard Google Play Services Intent
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isSigningIn = false
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (!idToken.isNullOrBlank()) {
                    vm.signInWithGoogleIdToken(idToken, account.displayName, account.email)
                } else {
                    error = "تعذّر استلام رمز مصادقة Google (ID Token)"
                }
            } catch (e: ApiException) {
                val msg = when (e.statusCode) {
                    12500 -> "خطأ في تهيئة خدمات Google Play على الجهاز"
                    12501 -> "تم إلغاء اختيار الحساب"
                    12502 -> "حدثت مشكلة أثناء الاتصال بخدمات Google"
                    7 -> "لا يوجد اتصال بالإنترنت"
                    else -> "تعذّر تسجيل الدخول (رمز: ${e.statusCode})"
                }
                if (e.statusCode != 12501) {
                    error = msg
                }
            } catch (e: Exception) {
                error = e.message ?: "حدث خطأ غير متوقع"
            }
        }
    }

    // Auto-advance when signed in
    LaunchedEffect(signedIn) {
        if (signedIn) {
            val googleName = CloudAuth.displayName
            if (!googleName.isNullOrBlank() && vm.learnerName.isBlank()) {
                vm.learnerName = googleName
                vm.persist()
            }
            onFinish()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0F172A), // Slate 900
                        Color(0xFF1E1B4B), // Indigo 950
                        Color(0xFF022C22), // Emerald 950 deep
                    )
                )
            ),
    ) {
        // Decorative glowing circles
        Box(
            Modifier
                .size(280.dp)
                .align(Alignment.TopEnd)
                .offset(x = 80.dp, y = (-40).dp)
                .clip(CircleShape)
                .background(Color(0xFF38BDF8).copy(alpha = 0.08f))
        )
        Box(
            Modifier
                .size(240.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-60).dp, y = 60.dp)
                .clip(CircleShape)
                .background(Color(0xFF10B981).copy(alpha = 0.08f))
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Header / Brand
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 28.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier.size(90.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.AutoStories,
                            contentDescription = "Logo",
                            tint = Color(0xFF67E8F9),
                            modifier = Modifier.size(46.dp),
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    "Z-Mastery",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "منصتك الذكية لتعلم وإتقان الإنجليزية",
                    color = Color(0xFFCBD5E1),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                )
            }

            // Central Action Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.85f), // Rich slate card
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            ) {
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "ابدأ رحلتك الآن",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "سجّل دخولك بحساب Google لمزامنة تقدمك السحابي",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(20.dp))

                    AnimatedVisibility(
                        visible = error != null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        error?.let {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFEF4444).copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 14.dp),
                            ) {
                                Row(
                                    Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Filled.ErrorOutline, null, tint = Color(0xFFF87171), modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(it, color = Color(0xFFFCA5A5), fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Official Google Sign-In Button
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable(enabled = !isSigningIn) {
                                isSigningIn = true
                                error = null
                                try {
                                    val intent = CloudAuth.getGoogleSignInIntent(ctx)
                                    googleSignInLauncher.launch(intent)
                                } catch (e: Exception) {
                                    // Fallback to CredentialManager
                                    vm.signInWithGoogle(ctx)
                                    isSigningIn = false
                                }
                            },
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            if (isSigningIn || vm.isSyncingCloud) {
                                CircularProgressIndicator(
                                    color = Color(0xFF4285F4),
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "جارٍ فتح Google…",
                                    color = Color(0xFF1E293B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                )
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF4285F4).copy(alpha = 0.15f),
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Black, fontSize = 16.sp)
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "تسجيل الدخول بحساب Google",
                                    color = Color(0xFF1E293B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Guest Button
                    OutlinedButton(
                        onClick = {
                            if (vm.learnerName.isBlank()) {
                                vm.learnerName = "ضيف"
                            }
                            vm.persist()
                            onFinish()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFCBD5E1),
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    ) {
                        Icon(Icons.Filled.PersonOutline, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "متابعة كضيف بدون تسجيل",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "بتسجيلك توافق على شروط الخدمة وسياسة الخصوصية",
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Footer & Developer Admin Unlock
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showAdminDialog = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(
                        Icons.Filled.AdminPanelSettings,
                        contentDescription = "Admin",
                        tint = if (vm.isAdmin) Color(0xFFFBBF24) else Color(0xFF64748B),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (vm.isAdmin) "👑 وضع المطور والمسؤول مفعّل" else "الدخول كمسؤول / مطور",
                        color = if (vm.isAdmin) Color(0xFFFBBF24) else Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = if (vm.isAdmin) FontWeight.Bold else FontWeight.Normal,
                    )
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    "Z-Mastery v1.1.0 · منصة تعليمية احترافية",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                )
            }
        }
    }

    // Developer Admin Code Dialog
    if (showAdminDialog) {
        AlertDialog(
            onDismissRequest = {
                showAdminDialog = false
                adminError = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Security, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("صلاحيات المطور والمسؤول 👑", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column {
                    Text(
                        "إذا كنت مطور التطبيق، يتم التعرف عليك تلقائياً بمجرد تسجيل الدخول بإيميلك (mohammedalbkhyty@gmail.com)، أو يمكنك تفعيل الصلاحيات مباشرة بإدخال الرمز السري أدناه:",
                        fontSize = 12.sp,
                        color = ZTextSecondary,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = adminCodeInput,
                        onValueChange = {
                            adminCodeInput = it
                            adminError = null
                        },
                        label = { Text("رمز المطور السري (مثال: ADMIN2026)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    )
                    adminError?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = ZRose, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (vm.unlockDeveloperAdmin(adminCodeInput)) {
                            showAdminDialog = false
                            Toast.makeText(ctx, "مرحباً بك يا مطور التطبيق! 👑", Toast.LENGTH_SHORT).show()
                        } else {
                            adminError = "الرمز السري غير صحيح. يمكنك استخدام ADMIN2026"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                ) {
                    Text("تفعيل صلاحيات المطور", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdminDialog = false }) {
                    Text("إلغاء")
                }
            },
        )
    }
}
