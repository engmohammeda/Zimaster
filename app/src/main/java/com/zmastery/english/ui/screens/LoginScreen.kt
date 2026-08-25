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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
    var successMsg by remember { mutableStateOf<String?>(null) }
    var isSigningIn by remember { mutableStateOf(false) }

    // Tab state: 0 = Google / Quick, 1 = Email & Password
    var selectedAuthTab by remember { mutableIntStateOf(0) }
    var isSignUpMode by remember { mutableStateOf(false) }

    // Email / Password Form Fields
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    // Dialogs
    var showAdminDialog by remember { mutableStateOf(false) }
    var adminCodeInput by remember { mutableStateOf("") }
    var adminError by remember { mutableStateOf<String?>(null) }

    var showForgotPassDialog by remember { mutableStateOf(false) }
    var resetEmailInput by remember { mutableStateOf("") }
    var resetMessage by remember { mutableStateOf<String?>(null) }

    var showWebClientConfigDialog by remember { mutableStateOf(false) }
    var customWebClientId by remember { mutableStateOf(vm.googleWebClientId) }

    // Observe cloud auth state changes
    val signedIn = vm.cloudUid != null && !vm.cloudIsAnonymous

    // Google Sign-In launcher using standard Google Play Services Intent
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (!idToken.isNullOrBlank()) {
                    vm.signInWithGoogleIdToken(
                        idToken = idToken,
                        displayName = account.displayName,
                        email = account.email,
                    ) { success, err ->
                        isSigningIn = false
                        if (success) {
                            Toast.makeText(ctx, "مرحباً بك ${account.displayName ?: ""} 🎉", Toast.LENGTH_SHORT).show()
                            onFinish()
                        } else {
                            error = err ?: "فشل تسجيل الدخول بحساب Google لدى Firebase"
                        }
                    }
                } else {
                    isSigningIn = false
                    error = "لم يرجع حساب Google رمز المصادقة (ID Token). يرجى التأكد من إضافة Web Client ID الصحيح"
                }
            } catch (e: ApiException) {
                isSigningIn = false
                val msg = when (e.statusCode) {
                    12500 -> "خطأ في تهيئة خدمات Google Play على الجهاز (رمز 12500)"
                    12501 -> "تم إلغاء اختيار الحساب"
                    12502 -> "حدثت مشكلة أثناء الاتصال بخدمات Google"
                    10 -> "خطأ في إعدادات التطبيق (Developer Error: تأكد من إضافة SHA-1 و Web Client ID في Firebase)"
                    7 -> "لا يوجد اتصال بالإنترنت"
                    else -> "تعذّر تسجيل الدخول (رمز: ${e.statusCode})"
                }
                if (e.statusCode != 12501) {
                    error = msg
                }
            } catch (e: Exception) {
                isSigningIn = false
                error = e.message ?: "حدث خطأ غير متوقع"
            }
        } else {
            isSigningIn = false
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
                        // خلفية البطل تتكيّف مع الثيم: ورق نيلي ناعم نهاراً / كحلي عميق ليلاً
                        if (ZThemeState.isDark) Color(0xFF12131C) else Color(0xFFEDF0FA),
                        if (ZThemeState.isDark) Color(0xFF1B1D2A) else Color(0xFFE4E8F6),
                        if (ZThemeState.isDark) Color(0xFF12131C) else Color(0xFFEDF0FA),
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
                .background(ZIndigo.copy(alpha = 0.08f))
        )
        Box(
            Modifier
                .size(240.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-60).dp, y = 60.dp)
                .clip(CircleShape)
                .background(ZCyan.copy(alpha = 0.08f))
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Header / Brand
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = ZIndigo.copy(alpha = 0.10f),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, ZIndigo.copy(alpha = 0.25f)),
                    modifier = Modifier.size(76.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.AutoStories,
                            contentDescription = "Logo",
                            tint = ZIndigo,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    "Z-Mastery",
                    color = ZTextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "منصتك الذكية لتعلم وإتقان الإنجليزية",
                    color = ZTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Central Action Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = ZCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, ZBorder),
                shadowElevation = 16.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Auth Method Tabs
                    TabRow(
                        selectedTabIndex = selectedAuthTab,
                        containerColor = ZSurfaceVariant.copy(alpha = 0.6f),
                        contentColor = ZTextPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .fillMaxWidth(),
                        indicator = { tabPositions ->
                            if (selectedAuthTab < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedAuthTab]),
                                    color = ZIndigo,
                                    height = 3.dp,
                                )
                            }
                        },
                    ) {
                        Tab(
                            selected = selectedAuthTab == 0,
                            onClick = {
                                selectedAuthTab = 0
                                error = null
                            },
                            text = { Text("حساب Google", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                            icon = { Icon(Icons.Filled.AccountCircle, null, modifier = Modifier.size(18.dp)) },
                        )
                        Tab(
                            selected = selectedAuthTab == 1,
                            onClick = {
                                selectedAuthTab = 1
                                error = null
                            },
                            text = { Text("البريد الإلكتروني", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                            icon = { Icon(Icons.Filled.Email, null, modifier = Modifier.size(18.dp)) },
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // Error Message Display
                    AnimatedVisibility(
                        visible = error != null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        error?.let {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ZRose.copy(alpha = 0.14f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ZRose.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                            ) {
                                Row(
                                    Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Filled.ErrorOutline, null, tint = ZRose, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(it, color = ZRoseDeep, fontSize = 12.sp, lineHeight = 16.sp)
                                }
                            }
                        }
                    }

                    // Success Message Display
                    AnimatedVisibility(
                        visible = successMsg != null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        successMsg?.let {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ZEmerald.copy(alpha = 0.18f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ZEmerald.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                            ) {
                                Row(
                                    Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Filled.CheckCircle, null, tint = ZEmerald, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(it, color = ZEmeraldDeep, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    if (selectedAuthTab == 0) {
                        // ── TAB 0: GOOGLE SIGN-IN ──
                        Text(
                            "سجّل دخولك بلمسة واحدة بحساب Google لمزامنة دروسك وتقدمك",
                            color = ZTextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                        )

                        Spacer(Modifier.height(20.dp))

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clickable(enabled = !isSigningIn) {
                                    isSigningIn = true
                                    error = null
                                    successMsg = null
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
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "جارٍ تسجيل الدخول…",
                                        color = Color(0xFF2A2C38),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                    )
                                } else {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF4285F4).copy(alpha = 0.15f),
                                        modifier = Modifier.size(26.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Black, fontSize = 15.sp)
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "تسجيل الدخول بحساب Google",
                                        color = Color(0xFF2A2C38),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        TextButton(onClick = { showWebClientConfigDialog = true }) {
                            Icon(Icons.Filled.Settings, null, tint = ZTextMuted, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("إعدادات معرّف Web Client ID", color = ZTextSecondary, fontSize = 12.sp)
                        }
                    } else {
                        // ── TAB 1: EMAIL & PASSWORD ──
                        if (isSignUpMode) {
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it; error = null },
                                label = { Text("الاسم الكامل") },
                                leadingIcon = { Icon(Icons.Filled.Person, null, tint = ZTextSecondary) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                        }

                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it; error = null },
                            label = { Text("البريد الإلكتروني") },
                            leadingIcon = { Icon(Icons.Filled.Email, null, tint = ZTextSecondary) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it; error = null },
                            label = { Text("كلمة المرور") },
                            leadingIcon = { Icon(Icons.Filled.Lock, null, tint = ZTextSecondary) },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        null,
                                        tint = ZTextSecondary,
                                    )
                                }
                            },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        )

                        if (!isSignUpMode) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(onClick = {
                                    resetEmailInput = emailInput
                                    showForgotPassDialog = true
                                }) {
                                    Text("نسيت كلمة المرور؟", color = ZIndigo, fontSize = 12.sp)
                                }
                            }
                        } else {
                            Spacer(Modifier.height(12.dp))
                            // تأكيد كلمة المرور — يمنع أخطاء الطباعة قبل إنشاء الحساب
                            OutlinedTextField(
                                value = confirmPasswordInput,
                                onValueChange = { confirmPasswordInput = it; error = null },
                                label = { Text("تأكيد كلمة المرور") },
                                leadingIcon = { Icon(Icons.Filled.Lock, null, tint = ZTextSecondary) },
                                singleLine = true,
                                isError = confirmPasswordInput.isNotBlank() && confirmPasswordInput != passwordInput,
                                supportingText = {
                                    if (confirmPasswordInput.isNotBlank() && confirmPasswordInput != passwordInput) {
                                        Text("كلمتا المرور غير متطابقتين", color = ZRose, fontSize = 11.sp)
                                    }
                                },
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                        }

                        Button(
                            onClick = {
                                isSigningIn = true
                                error = null
                                successMsg = null
                                if (isSignUpMode) {
                                    if (passwordInput != confirmPasswordInput) {
                                        isSigningIn = false
                                        error = "كلمتا المرور غير متطابقتين"
                                        return@Button
                                    }
                                    vm.signUpWithEmail(emailInput, passwordInput, nameInput) { ok, err ->
                                        isSigningIn = false
                                        if (ok) {
                                            Toast.makeText(ctx, "تم إنشاء الحساب بنجاح 🎉", Toast.LENGTH_SHORT).show()
                                            onFinish()
                                        } else {
                                            error = err ?: "فشل إنشاء الحساب"
                                        }
                                    }
                                } else {
                                    vm.signInWithEmail(emailInput, passwordInput) { ok, err ->
                                        isSigningIn = false
                                        if (ok) {
                                            Toast.makeText(ctx, "تم تسجيل الدخول بنجاح ✓", Toast.LENGTH_SHORT).show()
                                            onFinish()
                                        } else {
                                            error = err ?: "فشل تسجيل الدخول"
                                        }
                                    }
                                }
                            },
                            enabled = !isSigningIn && emailInput.isNotBlank() && passwordInput.isNotBlank()
                                && (!isSignUpMode || confirmPasswordInput == passwordInput),
                            colors = ButtonDefaults.buttonColors(containerColor = ZIndigo),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        ) {
                            if (isSigningIn) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            } else {
                                Text(
                                    if (isSignUpMode) "إنشاء حساب جديد" else "تسجيل الدخول",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        TextButton(onClick = {
                            isSignUpMode = !isSignUpMode
                            confirmPasswordInput = ""
                            error = null
                        }) {
                            Text(
                                if (isSignUpMode) "لديك حساب بالفعل؟ تسجيل الدخول" else "ليس لديك حساب؟ إنشاء حساب جديد",
                                color = ZTextSecondary,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(ZBorder))
                    Spacer(Modifier.height(16.dp))

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
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ZTextSecondary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ZBorder),
                    ) {
                        Icon(Icons.Filled.PersonOutline, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("متابعة كضيف بدون تسجيل", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

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
                        tint = if (vm.isAdmin) ZAmber else ZTextMuted,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (vm.isAdmin) "👑 وضع المطور والمسؤول مفعّل" else "الدخول كمسؤول / مطور",
                        color = if (vm.isAdmin) ZAmber else ZTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (vm.isAdmin) FontWeight.Bold else FontWeight.Normal,
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    "Z-Mastery v1.1.0 · منصة تعليمية متكاملة",
                    color = ZTextMuted,
                    fontSize = 11.sp,
                )
            }
        }
    }

    // Developer Admin Code Dialog
    var showAdminPassword by remember { mutableStateOf(false) }
    if (showAdminDialog) {
        AlertDialog(
            onDismissRequest = {
                showAdminDialog = false
                adminError = null
                adminCodeInput = ""
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Security, null, tint = ZAmber, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("صلاحيات المطور والمسؤول 👑", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column {
                    Text(
                        "أدخل الرمز السري الخاص بإدارة المنصة والمطور لتفعيل كافة الصلاحيات المتقدمة:",
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
                        label = { Text("رمز المطور السري") },
                        placeholder = { Text("••••••••") },
                        singleLine = true,
                        visualTransformation = if (showAdminPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        trailingIcon = {
                            IconButton(onClick = { showAdminPassword = !showAdminPassword }) {
                                Icon(
                                    if (showAdminPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (showAdminPassword) "إخفاء الرمز" else "إظهار الرمز",
                                    tint = ZTextSecondary,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    adminError?.let {
                        Spacer(Modifier.height(8.dp))
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
                            onFinish()
                        } else {
                            adminError = "الرمز السري غير صحيح، يرجى التحقق والمحاولة مجدداً"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ZAmber),
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

    // Forgot Password Dialog
    if (showForgotPassDialog) {
        AlertDialog(
            onDismissRequest = { showForgotPassDialog = false },
            title = { Text("استعادة كلمة المرور 🔑", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column {
                    Text(
                        "أدخل بريدك الإلكتروني وسنرسل لك رابطاً لإعادة تعيين كلمة المرور فوراً عبر Firebase:",
                        fontSize = 12.sp,
                        color = ZTextSecondary,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = resetEmailInput,
                        onValueChange = { resetEmailInput = it },
                        label = { Text("البريد الإلكتروني") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    resetMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = ZCyan, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.sendPasswordResetEmail(resetEmailInput) { ok, msg ->
                            resetMessage = msg
                            if (ok) {
                                Toast.makeText(ctx, msg ?: "تم الإرسال", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ZIndigo),
                ) {
                    Text("إرسال رابط الاستعادة", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotPassDialog = false }) {
                    Text("إغلاق")
                }
            },
        )
    }

    // Web Client ID Config Dialog
    if (showWebClientConfigDialog) {
        AlertDialog(
            onDismissRequest = { showWebClientConfigDialog = false },
            title = { Text("إعدادات Google OAuth Client ID", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column {
                    Text(
                        "لربط Google Sign-In بمشروع Firebase الخاص بك:\n1. افتح Firebase Console > Authentication > Sign-in method > Google.\n2. انسخ Web Client ID وضعه هنا إذا كان يختلف عن الافتراضي:",
                        fontSize = 12.sp,
                        color = ZTextSecondary,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customWebClientId,
                        onValueChange = { customWebClientId = it },
                        label = { Text("Web Client ID") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.updateGoogleWebClientId(customWebClientId)
                        showWebClientConfigDialog = false
                        Toast.makeText(ctx, "تم تحديث Web Client ID بنجاح", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWebClientConfigDialog = false }) {
                    Text("إلغاء")
                }
            },
        )
    }
}

