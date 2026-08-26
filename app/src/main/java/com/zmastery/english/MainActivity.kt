package com.zmastery.english

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zmastery.english.data.Telemetry
import com.zmastery.english.ui.components.AppBackground
import com.zmastery.english.ui.screens.*
import com.zmastery.english.ui.theme.*
import com.zmastery.english.viewmodel.AppViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var tts: com.zmastery.english.audio.TtsManager

    /** Route requested by a launcher shortcut / notification (consumed once). */
    private var pendingRoute by androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Shortcuts fired while the app is already running must still navigate.
        intent.getStringExtra("nav_route")?.let { pendingRoute = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        tts = com.zmastery.english.audio.TtsManager(this)
        // Notifications: create channels and (re)schedule daily/streak alarms.
        com.zmastery.english.notify.NotifChannels.ensure(this)
        com.zmastery.english.notify.NotifScheduler.rescheduleAll(this)
        // Long-press-the-icon shortcuts (مراجعة / القاموس / استيراد).
        com.zmastery.english.widget.HomeShortcuts.installDynamic(this)
        // Refresh widget every time the app is opened — ensures it never shows
        // stale data or a "can't load" state after the app is launched.
        com.zmastery.english.widget.ZMasteryWidget.refreshAll(this)
        setContent {
            val vm: AppViewModel = viewModel()
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val dark = when (vm.themeMode) {
                com.zmastery.english.data.ThemeMode.SYSTEM -> systemDark
                com.zmastery.english.data.ThemeMode.LIGHT -> false
                com.zmastery.english.data.ThemeMode.DARK -> true
            }
            // Sync system bar icon appearance with the active theme.
            val view = androidx.compose.ui.platform.LocalView.current
            androidx.compose.runtime.LaunchedEffect(dark) {
                val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
            val startRoute = pendingRoute ?: intent?.getStringExtra("nav_route")
            ZMasteryTheme(darkTheme = dark) {
                // Provide the TTS engine + a telemetry sink so EVERY audio
                // button in the app contributes to listening-time analytics.
                com.zmastery.english.audio.ProvideAudio(
                    tts = tts,
                    onListened = { secs -> vm.trackListening(secs) },
                ) {
                    ZMasteryApp(
                        tts, vm,
                        startRoute = startRoute,
                        onRouteConsumed = { pendingRoute = null },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) tts.shutdown()
        super.onDestroy()
    }
}

private data class NavTab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    NavTab("dashboard", "الرئيسية", Icons.Filled.GridView),
    NavTab("review", "المراجعة", Icons.Filled.Psychology),
    NavTab("levels", "المستويات", Icons.Filled.Layers),
    NavTab("vocab", "القاموس", Icons.Filled.MenuBook),
)

private data class MoreItem(val route: String, val label: String, val desc: String, val icon: ImageVector, val tint: Color)

/** A titled group of destinations — keeps the sheet scannable as it grows. */
private data class MoreGroup(val title: String, val items: List<MoreItem>)

/**
 * هندسة معلومات مدروسة (ترتيب رحلة المتعلم، بلا تكرار مع الشريط السفلي):
 *   مسارك → ثبّت وقيّم → تقدّمك وحماسك → حسابك.
 * «مراجعة الكلمات» في الشريط السفلي فلا تُكرر هنا.
 */
private val moreGroups = listOf(
    MoreGroup("مسارك", listOf(
        MoreItem("stories", "القصص وأهدافي", "قصة اليوم نحو هدفك التطبيقي", Icons.Filled.AutoStories, ZAmberDeep),
        MoreItem("roadmap", "خريطة المنهج", "خطتك وتغطيتك", Icons.Filled.Map, ZEmerald),
        MoreItem("skills", "المهارات الخمس", "قراءة · استماع · كتابة", Icons.Filled.Interests, ZIndigo),
    )),
    MoreGroup("ثبّت وقيّم", listOf(
        MoreItem("lessonReview", "مراجعة الدروس", "ثبّت ما تعلمته قبل الجديد", Icons.Filled.Autorenew, ZCyanDeep),
        MoreItem("mnemonics", "الروابط الذهنية", "صور تثبّت الكلمات", Icons.Filled.Link, ZPurple),
        MoreItem("exams", "الاختبارات", "قيّم مستواك بصدق", Icons.Filled.Quiz, ZCyanDeep),
    )),
    MoreGroup("تقدّمك وحماسك", listOf(
        MoreItem("analytics", "التحليلات", "مدربك الذكي ومرآة إدراكك", Icons.Filled.Analytics, ZPurple),
        MoreItem("momentum", "زخم الالتزام", "سلسلتك وصناديقك ودروعك", Icons.Filled.LocalFireDepartment, ZAmber),
    )),
    MoreGroup("حسابك والنظام", listOf(
        MoreItem("profile", "الملف الشخصي", "بياناتك وإحصائياتك", Icons.Filled.Person, ZIndigo),
        MoreItem("settings", "الإعدادات", "التخصيص والمفاتيح والمزامنة", Icons.Filled.Settings, ZCyanDeep),
        MoreItem("backup", "النسخ الاحتياطي", "صدّر واستعد بأمان", Icons.Filled.CloudSync, ZEmerald),
    )),
)

/** مجموعة المسؤول — تتصدر القائمة لأنه الأكثر استعمالاً للمعلّم. */
private val adminGroup = MoreGroup("النشر والإدارة 👑", listOf(
    MoreItem("devtools", "مركز النشر", "دروس · إعلانات · عبارات · طلاب", Icons.Filled.AdminPanelSettings, ZAmberDeep),
    MoreItem("import", "استيراد ملفات", "JSON / ZIP لمنهج كامل", Icons.Filled.UploadFile, ZIndigo),
))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZMasteryApp(
    tts: com.zmastery.english.audio.TtsManager,
    vm: AppViewModel = viewModel(),
    startRoute: String? = null,
    onRouteConsumed: () -> Unit = {},
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Give the ViewModel the shared TTS engine so it can generate/cache audio.
    LaunchedEffect(Unit) { vm.attachTts(tts) }

    // Silent cloud bootstrap: ensures a Firebase user exists (anonymous by
    // default) and pulls any lessons/progress added from outside the app.
    LaunchedEffect(Unit) { vm.initCloudSync() }

    // Deep-link from a tapped notification (fires once).
    LaunchedEffect(startRoute) {
        if (!startRoute.isNullOrBlank() && startRoute != "dashboard") {
            runCatching { nav.navigate(startRoute) }
            onRouteConsumed()
        }
    }

    // Ask for notification permission once on first launch (Android 13+).
    val permCtx = androidx.compose.ui.platform.LocalContext.current
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) com.zmastery.english.notify.NotifScheduler.rescheduleAll(permCtx)
        }
        LaunchedEffect(Unit) {
            val ok = androidx.core.content.ContextCompat.checkSelfPermission(
                permCtx, android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!ok) permLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Keep the TTS engine in sync with settings (Gemini key + voice).
    LaunchedEffect(vm.geminiApiKey, vm.ttsVoice) {
        tts.apiKey = vm.geminiApiKey.trim()
        tts.voice = vm.ttsVoice
    }

    // Flush unsaved state whenever the app goes to the background, and refresh
    // the widget when the app comes back to the foreground.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val appContext = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP ||
                event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE
            ) vm.flush()
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                com.zmastery.english.widget.ZMasteryWidget.refreshAll(appContext)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ---- First-run gate: splash while loading, then the mission screen ----
    // The mission ("لا تعلّم بدون استمرارية") is the product thesis, so it is the
    // very first thing a new learner reads.
    var splashDone by remember { mutableStateOf(false) }
    if (!splashDone || !vm.isLoaded) {
        SplashScreen(onTimeout = { splashDone = true })
        return
    }
    if (!vm.onboardingDone) {
        OnboardingScreen(onFinish = { vm.completeOnboarding() })
        return
    }
    if (vm.learnerName.isBlank()) {
        LoginScreen(vm, onFinish = { /* learnerName already saved in LoginScreen */ })
        return
    }

    var showMore by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val activity = (androidx.compose.ui.platform.LocalContext.current as? android.app.Activity)

    // Back handling:
    // - On dashboard  -> ask for exit confirmation (prevents accidental close).
    // - Elsewhere     -> return to dashboard as a single instance.
    androidx.activity.compose.BackHandler(enabled = true) {
        when {
            showMore -> showMore = false
            currentRoute == "dashboard" -> showExitDialog = true
            else -> {
                nav.navigate("dashboard") {
                    popUpTo("dashboard") { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    // Note: "mnemonics" is deliberately NOT here — the Mnemonic Studio is a
    // focused step-by-step flow that renders its own header and back button.
    // "dashboard" is ALSO deliberately excluded — it renders its own
    // Duolingo-style StreakTopBar at the very top of the screen, so the
    // generic app bar underneath it would just be redundant duplication.
    val extraTop = listOf("stories", "stories/{focus}", "settings", "profile", "skills", "roadmap", "analytics", "import", "backup", "phonetics_preview", "momentum", "devtools")
    val showBars = currentRoute in (tabs.map { it.route }.filter { it != "dashboard" } + extraTop)

    // Navigate to a top-level destination as a SINGLE instance:
    // clears any stacked copies back to dashboard so tapping any nav item
    // always lands you directly on that screen (no deep back-stack buildup).
    fun goTopLevel(route: String) {
        nav.navigate(route) {
            popUpTo("dashboard") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun closeSheetAndGo(route: String) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            showMore = false
            goTopLevel(route)
        }
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (showBars) TopBar(currentRoute)
            },
            bottomBar = {
                if (currentRoute in tabs.map { it.route } || currentRoute in extraTop) {
                    ZBottomBar(nav, currentRoute, onMore = { showMore = true })
                }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = "dashboard",
                modifier = Modifier.padding(
                    top = if (showBars) padding.calculateTopPadding() else 0.dp,
                    bottom = padding.calculateBottomPadding(),
                ),
                enterTransition = { fadeIn(tween(200)) },
                exitTransition = { fadeOut(tween(200)) },
            ) {
                composable("dashboard") {
                    DashboardScreen(
                        vm,
                        onNavigate = { r -> goTopLevel(r) },
                        onOpenLesson = { lid -> nav.navigate("lesson/$lid") },
                    )
                }
                composable("review") { ReviewScreen(vm) }
                composable("lessonReview") { LessonReviewScreen(vm) }
                composable("levels") { LevelsScreen(vm) { id -> nav.navigate("course/$id") } }
                composable("vocab") {
                    VocabularyScreen(vm, onOpenMnemonics = { nav.navigate("mnemonics") })
                }
                composable("momentum") { MomentumScreen(vm, onNavigate = { r -> goTopLevel(r) }) }
                composable("mnemonics") {
                    MnemonicScreen(vm, onBack = { nav.popBackStack() })
                }
                composable("exams") { ExamsScreen(vm) }
                composable("stories") { StoriesScreen(vm) }
                composable("stories/{focus}") { entry ->
                    val focus = entry.arguments?.getString("focus")?.toIntOrNull()
                    StoriesScreen(vm, focusStoryId = focus)
                }
                composable("skills") { SkillsScreen(vm) }
                composable("roadmap") {
                    RoadmapScreen(
                        vm,
                        onOpenCourse = { id -> nav.navigate("course/$id") },
                        onOpenLesson = { lid -> nav.navigate("lesson/$lid") },
                    )
                }
                composable("analytics") { AnalyticsScreen(vm) }
                composable("import") { ImportScreen(vm) }
                composable("backup") { BackupScreen(vm) }
                composable("devtools") {
                    DeveloperToolsScreen(vm, onOpenImport = { nav.navigate("import") })
                }
                composable("settings") { SettingsScreen(vm, onBackup = { goTopLevel("backup") }) }
                composable("profile") { ProfileScreen(vm, onBack = { nav.popBackStack() }) }
                composable("course/{id}") { entry ->
                    val id = entry.arguments?.getString("id")?.toIntOrNull() ?: 1
                    CourseScreen(
                        vm, id,
                        onOpenLesson = { lid -> nav.navigate("lesson/$lid") },
                        onOpenPhonetics = { nav.navigate("phonetics_preview") },
                    )
                }
                composable("phonetics_preview") {
                    com.zmastery.english.ui.components.TrackStudyTime(vm, "phonetics")
                    val lesson = remember { com.zmastery.english.data.PhoneticsParser.parse(com.zmastery.english.data.SampleData.samplePhoneticsJson) }
                    if (lesson != null) {
                        PhoneticsLessonScreen(
                            lesson,
                            onComplete = {
                                // Log the drill so it feeds the pronunciation skill axis.
                                vm.trackPhoneticsDrill()
                                nav.popBackStack()
                            },
                        )
                    }
                }
                composable("lesson/{id}") { entry ->
                    val id = entry.arguments?.getString("id")?.toIntOrNull() ?: 1
                    LessonScreen(vm, id, onOpenQuiz = { lid -> nav.navigate("lessonQuiz/$lid") })
                }
                composable("lessonQuiz/{id}") { entry ->
                    val id = entry.arguments?.getString("id")?.toIntOrNull() ?: 1
                    LessonQuizScreen(vm, id, onBack = { nav.popBackStack() })
                }
            }
        }

        if (showMore) {
            ModalBottomSheet(
                onDismissRequest = { showMore = false },
                sheetState = sheetState,
                containerColor = ZSurface,
                dragHandle = { BottomSheetDefaults.DragHandle(color = ZBorder) },
            ) {
                MoreSheetContent(
                    vm = vm,
                    onNavigate = { r -> closeSheetAndGo(r) },
                )
            }
        }

        if (showExitDialog) {
            ExitConfirmDialog(
                onConfirm = {
                    showExitDialog = false
                    activity?.finish()
                },
                onDismiss = { showExitDialog = false },
            )
        }
    }
}

@Composable
private fun ExitConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ZSurface,
        icon = {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(ZIndigo, ZPurple))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Logout, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
        },
        title = { Text("الخروج من التطبيق؟", color = ZTextPrimary, fontWeight = FontWeight.Black, fontSize = 19.sp) },
        text = { Text("هل تريد إغلاق Z-Mastery؟ سيتم حفظ تقدمك تلقائياً.", color = ZTextSecondary, fontSize = 14.sp) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = ZRose),
                shape = RoundedCornerShape(12.dp),
            ) { Text("خروج", fontWeight = FontWeight.Bold, color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("البقاء", color = ZTextSecondary, fontWeight = FontWeight.Bold)
            }
        },
    )
}

/**
 * "المزيد" — redesigned as a premium SaaS-style hero sheet:
 *   • A hero header with a soft mesh-gradient backdrop and a real headline.
 *   • ONE spotlight hero card (import) with depth, a glowing icon plate and
 *     a real call-to-action chip — not just a colored rectangle.
 *   • Every destination group rendered as a single grouped card (iOS/macOS
 *     settings pattern) with consistent 16dp icon tiles, hairline dividers,
 *     and a chevron — instead of a grid of disconnected mini-tiles.
 */
@Composable
private fun MoreSheetContent(vm: AppViewModel, onNavigate: (String) -> Unit) {
    val isAdmin = vm.isAdmin
    val name = vm.learnerName.ifBlank { "متعلم Z-Mastery" }
    val (cefr, _) = Telemetry.estimatedCefr(vm.masteredCount, vm.completedLessons, vm.lifetime.examAvg)

    Column(
        Modifier.fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
    ) {
        // ── هوية المتعلم أولاً: اسمك وأرقامك، لا شعار عام ──
        Box(
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(ZIndigo, ZPurple)))
                .padding(18.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(50.dp).clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.20f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(name.take(1), color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1)
                        Text(
                            if (isAdmin) "مسؤول ومطوّر — أدوات النشر بالأسفل"
                            else "دروسك الجديدة تصلك تلقائياً من السحابة",
                            color = Color.White.copy(alpha = 0.88f), fontSize = 11.sp,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HeroChip("🔥 سلسلة ${vm.streak}")
                    HeroChip("⚡ ${vm.xp} XP")
                    HeroChip("🎓 $cefr")
                }
            }
        }

        // ── المسؤول: النشر أولاً لأنه الأكثر استعمالاً للمعلم ──
        if (isAdmin) {
            Spacer(Modifier.height(20.dp))
            MoreGroupCard(adminGroup, onNavigate)
        }

        moreGroups.forEach { g ->
            Spacer(Modifier.height(20.dp))
            MoreGroupCard(g, onNavigate) { route ->
                when (route) {
                    "stories" -> vm.unreadStoryCount.takeIf { it > 0 }
                    else -> null
                }
            }
        }
    }
}

@Composable
private fun HeroChip(text: String) {
    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.18f)) {
        Text(
            text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

/** بطاقة مجموعة بأسلوب الإعدادات: بلاطات هادئة ملوّنة + فواصل شعرية + شيفرون. */
@Composable
private fun MoreGroupCard(
    group: MoreGroup,
    onNavigate: (String) -> Unit,
    badgeFor: (String) -> Int? = { null },
) {
    Column {
        Text(
            group.title, color = ZTextMuted, fontWeight = FontWeight.Black, fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.padding(horizontal = 16.dp)) {
            Surface(
                shape = RoundedCornerShape(20.dp), color = ZCard, shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    group.items.forEachIndexed { idx, item ->
                        Surface(
                            color = Color.Transparent,
                            onClick = { onNavigate(item.route) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                                        .background(item.tint.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(item.icon, null, tint = item.tint, modifier = Modifier.size(20.dp)) }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.label, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(item.desc, color = ZTextSecondary, fontSize = 11.sp, maxLines = 1)
                                }
                                badgeFor(item.route)?.let { n ->
                                    Surface(shape = RoundedCornerShape(50), color = ZRose.copy(alpha = 0.15f)) {
                                        Text(
                                            "$n", color = ZRoseDeep, fontSize = 10.sp, fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                }
                                Icon(Icons.Filled.ChevronLeft, null, tint = ZTextMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                        if (idx < group.items.lastIndex) {
                            HorizontalDivider(color = ZBorder, modifier = Modifier.padding(start = 68.dp, end = 14.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(route: String?) {
    val title = when (route) {
        "dashboard" -> "لوحة القيادة"
        "review" -> "مراجعة الكلمات"
        "lessonReview" -> "مراجعة الدروس"
        "phonetics_preview" -> "درس الصوتيات"
        "levels" -> "المستويات والمناهج"
        "vocab" -> "القاموس"
        "exams" -> "الاختبارات"
        "stories" -> "أرشيف القصص"
        "stories/{focus}" -> "أرشيف القصص"
        "skills" -> "المهارات الخمس"
        "roadmap" -> "خريطة المنهج"
        "analytics" -> "التحليلات"
        "momentum" -> "زخم الالتزام"
        "mnemonics" -> "الروابط الذهنية"
        "import" -> "استيراد الكورسات"
        "devtools" -> "أدوات المطور 👑"
        "backup" -> "النسخ الاحتياطي"
        "settings" -> "الإعدادات"
        else -> "Z-Mastery"
    }
    CenterAlignedTopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
                        .background(Brush.linearGradient(listOf(ZIndigo, ZCyan))),
                    contentAlignment = Alignment.Center,
                ) { Text("Z", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp) }
                Spacer(Modifier.width(12.dp))
                Text(title, color = ZTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        actions = {
            // استيراد الدروس — أداة مسؤول/مطور فقط؛ الطلاب يستقبلون الدروس من السحابة.
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = ZSurface.copy(alpha = 0.85f),
            titleContentColor = ZTextPrimary,
        ),
    )
}

@Composable
private fun ZBottomBar(nav: NavHostController, currentRoute: String?, onMore: () -> Unit) {
    NavigationBar(
        containerColor = ZSurface,
        tonalElevation = 0.dp,
        modifier = Modifier.drawBehind {
            drawLine(
                color = ZBorder,
                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                strokeWidth = 2f,
            )
        },
    ) {
        tabs.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        nav.navigate(tab.route) {
                            popUpTo("dashboard") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(tab.icon, tab.label) },
                label = { Text(tab.label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = ZCyan,
                    unselectedIconColor = ZTextMuted,
                    unselectedTextColor = ZTextMuted,
                    indicatorColor = ZIndigo,
                ),
            )
        }
        // More button — opens the sheet with all secondary screens (incl. importer)
        val moreActive = currentRoute in listOf("exams", "roadmap", "skills", "stories", "stories/{focus}", "analytics", "settings", "import", "lessonReview")
        NavigationBarItem(
            selected = moreActive,
            onClick = onMore,
            icon = { Icon(Icons.Filled.MoreHoriz, "المزيد") },
            label = { Text("المزيد", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = ZCyan,
                unselectedIconColor = ZTextMuted,
                unselectedTextColor = ZTextMuted,
                indicatorColor = ZIndigo,
            ),
        )
    }
}
