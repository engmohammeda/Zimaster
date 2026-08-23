package com.zmastery.english.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zmastery.english.cloud.CloudAuth
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel

@Composable
fun LoginScreen(vm: AppViewModel, onFinish: () -> Unit) {
    val ctx = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var isSigningIn by remember { mutableStateOf(false) }

    // Observe cloud auth state changes
    val signedIn = vm.cloudUid != null && !vm.cloudIsAnonymous

    // When Google Sign-In succeeds, auto-populate learnerName from Google profile
    LaunchedEffect(signedIn) {
        if (signedIn) {
            // Take the name from Google profile automatically
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
            .background(Brush.verticalGradient(listOf(ZIndigo, ZCyanDeep))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.AutoStories,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp),
                )
            }

            Spacer(Modifier.height(32.dp))

            Text("Z-Mastery", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
            Text("منصتك لتعلم الإنجليزية", color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)

            Spacer(Modifier.height(48.dp))

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("ابدأ رحلتك", color = ZTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    Text("سجّل دخولك بحساب جوجل أو تابع كضيف", color = ZTextSecondary, fontSize = 14.sp)

                    Spacer(Modifier.height(24.dp))

                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = ZRose, fontSize = 12.sp)
                    }

                    // Google Sign-In
                    Button(
                        onClick = {
                            isSigningIn = true
                            error = null
                            vm.signInWithGoogle(ctx)
                        },
                        enabled = !isSigningIn,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4285F4),
                            disabledContainerColor = ZBorder,
                        ),
                    ) {
                        if (isSigningIn && vm.isSyncingCloud) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Login, null)
                            Spacer(Modifier.width(8.dp))
                            Text("تسجيل الدخول بحساب Google", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }

                    if (vm.googleWebClientId.isBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "أضف Web Client ID في الإعدادات لتفعيل تسجيل الدخول بجوجل",
                            color = ZTextMuted, fontSize = 11.sp,
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Continue as guest (anonymous)
                    OutlinedButton(
                        onClick = {
                            // Use a default name for guest mode
                            if (vm.learnerName.isBlank()) {
                                vm.learnerName = "ضيف"
                            }
                            vm.persist()
                            onFinish()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ZTextPrimary),
                    ) {
                        Text("تخطي ← متابعة كضيف", fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "بتسجيلك، توافق على سياسة الخصوصية",
                        color = ZTextMuted, fontSize = 11.sp,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                "الإصدار 1.0 · مبني بـ ❤️",
                color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
            )
        }
    }
}
