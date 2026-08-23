# 🏗 دليل المعمارية — Z-Mastery

> **آخر تحديث:** 2026-08-23  
> **الحالة:** المرحلة الثانية مكتملة ✅

---

## البنية النهائية

```
com.zmastery.english/
├── domain/
│   ├── usecases/                        ← منطق أعمال نقي (بدون Android)
│   │   ├── ReviewScheduler.kt           ← FSRS + اختبار + معاينة فترات
│   │   ├── StreakManager.kt             ← سلسلة + تصدع + إنقاذ + رسائل
│   │   ├── BackupCoordinator.kt         ← تصدير + استيراد + تحقق
│   │   ├── AiService.kt                 ← مفاتيح + نماذج + حصة + تكلفة
│   │   ├── CloudSyncService.kt          ← مزامنة + تعارض + دمج
│   │   ├── StoryService.kt              ← قصص يومية + بذور + إعادة محاولة
│   │   └── PerformanceUtils.kt          ← throttle + debounce + cache
│   └── repositories/
│       └── StateRepository.kt           ← واجهة التخزين المجردة
├── data/
│   ├── repositories/
│   │   └── DataStoreStateRepository.kt  ← تطبيق واجهة التخزين
│   ├── DataGuard.kt                     ← حماية البيانات (نسخة + استعادة)
│   ├── KeyProtector.kt                  ← إخفاء المفاتيح + تنظيف السجلات
│   ├── Persistence.kt                   ← DataStore I/O
│   ├── BackupManager.kt                 ← تصدير/استيراد (v3 + checksum)
│   ├── Fsrs.kt                          ← خوارزمية FSRS v5
│   ├── EnigmaStreak.kt                  ← محرك السلسلة + مرآة الإدراك
│   └── ...
├── viewmodel/
│   └── AppViewModel.kt                  ← منسق + حالة UI
│       (يستخدم Use Cases عبر delegation)
├── widget/
│   ├── ZMasteryWidget.kt                ← RemoteViews (11 مرحلة + تشخيص)
│   ├── WidgetDiagnostics.kt             ← تسجيل مراحل الودجت
│   └── HomeShortcuts.kt                 ← اختصارات الشاشة الرئيسية
├── ui/
│   ├── screens/                         ← شاشات Compose
│   ├── components/                      ← مكونات مشتركة
│   └── theme/                           ← ألوان + خطوط
└── notify/
    ├── Notifications.kt
    ├── Receivers.kt                     ← BootReceiver يُحدّث الودجت
    └── Scheduler.kt
```

---

## Use Cases — 7 خدمات مستقلة

| # | Use Case | المسؤولية | الاختبارات | الأسطر |
|---|----------|-----------|-----------|--------|
| 1 | `ReviewScheduler` | FSRS + اختبار + معاينة فترات | 19 | 170 |
| 2 | `StreakManager` | سلسلة + تصدع + إنقاذ + رسائل | 17 | 130 |
| 3 | `BackupCoordinator` | تصدير + استيراد + تحقق | 7 | 110 |
| 4 | `AiService` | مفاتيح + نماذج + حصة + تكلفة | 22 | 150 |
| 5 | `CloudSyncService` | مزامنة + تعارض + دمج | 17 | 130 |
| 6 | `StoryService` | قصص يومية + بذور + إعادة محاولة | 20 | 110 |
| 7 | `PerformanceUtils` | throttle + debounce + cache | 20 | 140 |

**المجموع: 122 اختبار + 74 استخدام**

---

## كيف يعمل Delegation في ViewModel

```kotlin
class AppViewModel(app: Application) : AndroidViewModel(app) {

    // ── Use Cases (منطق نقي — قابل للاختبار) ──
    val reviewScheduler = ReviewScheduler()
    val streakManager = StreakManager()
    val backupCoordinator = BackupCoordinator()
    val aiService = AiService()
    val cloudSyncService = CloudSyncService()
    val storyService = StoryService()

    // ── Performance ──
    private val widgetThrottle = PerformanceUtils.Throttle(5 * 60_000L)
    private val statsCache = PerformanceUtils.TimedCache<Any>(30_000L)

    // ── حالة UI (تبقى هنا لأن Compose يراقبها) ──
    var streak by mutableStateOf(0)
    var xp by mutableStateOf(0)
    // ...
    
    // ── دوال ViewModel تستدعي Use Cases ──
    fun syncWidget() {
        ProgressStore.save(...)
        if (widgetThrottle.allow()) {  // ← PerformanceUtils
            ZMasteryWidget.refreshAll(ctx)
        }
    }
}
```

### مبدأ الفصل:
- **Use Cases** = منطق نقي (لا Android، لا Compose، لا Firebase)
- **ViewModel** = حالة UI (`mutableStateOf`) + تنسيق بين Use Cases
- **Repositories** = تجريد I/O (يمكن استبدالها بـ in-memory للاختبار)

---

## الأداء — Throttle و Cache

| المورد | الآلية | الفترة |
|--------|--------|--------|
| تحديث الودجت | `Throttle(5 min)` | لا يُبث أكثر من مرة كل 5 دقائق |
| الإحصائيات المحسوبة | `TimedCache(30 sec)` | لا يُعاد حسابها إلا بعد 30 ثانية |
| الحفظ | `Debounce(400 ms)` | يُؤجَّل الحفظ 400ms لتجميع التغييرات |

---

## الاختبارات — 196 اختبار وحدة

| الملف | الاختبارات | يغطي |
|-------|-----------|------|
| `ReviewSchedulerTest.kt` | 19 | FSRS + مراحل + فترات |
| `StreakManagerTest.kt` | 17 | سلسلة + تصدع + إنقاذ |
| `BackupCoordinatorTest.kt` | 7 | نسخ احتياطي + تحقق |
| `AiServiceTest.kt` | 22 | مفاتيح + نماذج + تكلفة |
| `CloudSyncServiceTest.kt` | 17 | مزامنة + تعارض + رسائل |
| `StoryServiceTest.kt` | 20 | قصص + بذور + إعادة |
| `PerformanceUtilsTest.kt` | 20 | throttle + cache + key |
| `KeyProtectorTest.kt` | 18 | إخفاء + كشف + تنظيف |
| `FsrsTest.kt` | 14 | R + interval + schedule |
| `EnigmaStreakTest.kt` | 12 | decay + messages |
| `ImportEngineTest.kt` | 12 | JSON validation |
| `DataGuardTest.kt` | 10 | health + encode/decode |
| `BackupManagerTest.kt` | 8 | export + import + checksum |
