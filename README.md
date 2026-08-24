# 🌟 Zimaster (Z-Mastery) — إتقان الإنجليزية بالتكرار المتباعد والذكاء الاصطناعي

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" alt="Zimaster Logo" width="110" height="110" />
</p>

<p align="center">
  <b>تطبيق أندرويد متقدّم لتعلّم وإتقان اللغة الإنجليزية، مبنيّ بـ Jetpack Compose
  وخوارزمية FSRS للذاكرة طويلة المدى، مدعوماً بنماذج الذكاء الاصطناعي التوليدي،
  مع معماريّة نظيفة قائمة على وحدات (Controllers) منفصلة قابلة للاختبار.</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green.svg" alt="Platform" />
  <img src="https://img.shields.io/badge/Kotlin-2.2.21-purple.svg" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-blue.svg" alt="Compose" />
  <img src="https://img.shields.io/badge/Min_SDK-24-orange.svg" alt="Min SDK" />
  <img src="https://img.shields.io/badge/Target_SDK-36-red.svg" alt="Target SDK" />
  <img src="https://img.shields.io/badge/FSRS-v4.5-teal.svg" alt="FSRS" />
  <img src="https://img.shields.io/badge/CI%2FCD-GitHub_Actions-2088FF.svg" alt="GitHub Actions" />
</p>

---

## 📑 جدول المحتويات
1. [المميزات الرئيسية](#-المميزات-الرئيسية)
2. [الهندسة المعمارية](#-الهندسة-المعمارية)
3. [خوارزمية التكرار المتباعد (FSRS)](#-خوارزمية-التكرار-المتباعد-fsrs)
4. [تكامل الذكاء الاصطناعي](#-تكامل-الذكاء-الاصطناعي-multi-provider-ai)
5. [الودجت وعبارات التحفيز](#-الودجت-وعبارات-التحفيز)
6. [البناء والتثبيت (CI/CD)](#-البناء-والتثبيت-cicd)
7. [طريقة البناء محلياً](#-طريقة-البناء-محليا)
8. [الأمان والخصوصية](#-الأمان-والخصوصية)

---

## 🚀 المميزات الرئيسية

- 🧠 **نظام مراجعة ذكي (FSRS v4.5)**: حساب دقيق لقوة الاسترجاع والصعوبة واستقرار الذاكرة، بهدف احتفاظ 90%.
- 🤖 **معلم ذكاء اصطناعي متعدّد المزوّدين**: Gemini، OpenAI، Anthropic Claude، Groq، DeepSeek، Cerebras، OpenRouter.
- 🎙️ **تدريب صوتي وتوليد أصوات دائمة (Gemini TTS)**: مخزَّنة محلياً بصفّ متوازٍ لا يجمد الواجهة.
- 📱 **ودجت شاشة رئيسية بتصميم محوره «يوم الحماسة»**: شعلة السلسلة + حالة اليوم (مؤمَّن/في خطر) + شريط هدف يومي + عبارة تحفيزية يومية.
- 💬 **عبارات تحفيزية ديناميكية سحابية**: عبارة عشوائية واحدة يومياً لكل متعلم (من مزيج مكتبة مدمجة + عبارات يضيفها المسؤول)، تتزامن عبر الأجهزة.
- 🧩 **شاشة درس موحّدة قائمة على البُلوكات (Block-Based)**: Hero، مفردات، قواعد، قراءة، حوار، كتابة، صوتيات.
- 💾 **Offline-First + مزامنة سحابية**: قاعدة بيانات محلية + Firestore + نسخ احتياطي/استعادة كامل.
- 🔒 **تشفير مفاتيح الـ API**: لا تُرسل لأي خادم وسيط، ولا تُسجَّل ولا تُشارَك.

---

## 🏗 الهندسة المعمارية

التطبيق يتبع معايير أندرويد الحديثة، مع **فصل صارم بين الحالة والمنطق**:
`AppViewModel` هو محور الحالة + التنسيق + الواجهة (façade)، بينما كل منطق الميزات
موزّع في **15 وحدة Controller مستقلّة قابلة للاختبار**.

```
app/src/main/java/com/zmastery/english/
├── data/              # النماذج، FSRS، التخزين، الاستيراد، التشفير، العبارات (QuoteStore)
├── domain/usecases/   # حالات الاستخدام المستقلّة (AiService, StreakManager, BackupCoordinator…)
├── viewmodel/         # AppViewModel + 15 Controller (منطق مُفكَّك لكل ميزة)
├── ui/                # Jetpack Compose + شاشات الدروس (blocks/) + السمات
├── audio/             # محرك TTS والتشغيل الفوري
├── cloud/             # Firebase Auth + Firestore (دروس/عبارات/مستخدمون/إشعارات)
├── widget/            # ودجت الشاشة الرئيسية (RemoteViews)
└── notify/            # الإشعارات والتنبيهات المجدولة
```

### وحدات الـ Controllers (المنطق المُفكَّك)
`ExamsController` · `StoryController` · `AudioController` · `CloudController` ·
`AiConfigController` · `CurriculumController` · `StudyPlanController` ·
`CoachController` · `MnemonicController` · `ImportController` ·
`LessonReviewController` · `WordReviewController` · `TelemetryController` ·
`GamificationController` · `DailyPlanController`

> كل وحدة تستقبل مرجعاً للـ `AppViewModel` وتصل للحالة عبر أسماء مستعارة محلية،
> مع إبقاء الواجهة العامة للـ ViewModel كما هي (لا شاشة تحتاج تعديلاً عند إعادة الهيكلة).

### التقنيات الأساسية
- **UI**: Jetpack Compose + Material Design 3.
- **اللغة**: Kotlin 2.2.21 + Coroutines/StateFlow.
- **الاستمرارية**: ملف حالة محلي مشفّر + SharedPreferences + kotlinx.serialization.
- **السحابة**: Firebase (Auth + Firestore) عبر CredentialManager و Google Sign-In.
- **الشبكة**: Ktor / OkHttp لاستجابات الـ AI.

---

## 📐 خوارزمية التكرار المتباعد (FSRS)

يستخدم التطبيق **Free Spaced Repetition Scheduler** بدلاً من SM-2 التقليدية:
- حساب قوة الاسترجاع $R$ بدلّة الوقت المنقضي واستقرار الذاكرة $S$.
- تحديث الصعوبة $D$ والاستقرار $S$ بعد كل مراجعة.
- جدولة الفواصل وفق نسبة الحفظ المستهدفة (90%)، وتقييم مُكيّف بـ«مرحلة الكشف»
  (صوت ↔ صورة ذهنية ↔ نص ↔ كشف كامل) لالتقاط صعوبة الاسترجاع بدقّة.

---

## 🤖 تكامل الذكاء الاصطناعي (Multi-Provider AI)

يستخدم المتعلّم مفتاحه الخاص للاتصال بأي من: **Google Gemini** · **OpenAI** ·
**Anthropic Claude** · **Groq** · **Cerebras** · **DeepSeek** · **OpenRouter**،
لشرح القواعد، توليد الأمثلة، التصحيح اللغوي، وتحليل أداء المتعلّم (المدرب الذكي).

---

## 📱 الودجت وعبارات التحفيز

### الودجت
ودجت Material 3 متوافق مع كل المشغّلات (Samsung/Xiaomi/Pixel/…). تصميمه محوره
**يوم الحماسة**: شعلة 🔥 كبيرة + حالة اليوم (مؤمَّن ✓ / في خطر ⚠️ / ابدأ)، شريط الهدف
اليومي، وعبارة تحفيزية. الخلفية تتغيّر تلقائياً عند خطر السلسلة (تصدّع/إنقاذ).

### العبارات الديناميكية
- عبارة **عشوائية واحدة يومياً لكل جهاز** (بذرة فريدة لكل جهاز + رقم اليوم).
- تُدمج مع **عبارات سحابية يضيفها المسؤول** (مجموعة Firestore `/quotes`) وتتزامن
  تلقائياً عبر الأجهزة وتظهر في الودجت والشاشة الرئيسية.
- يعمل الودجت **دون اتصال** (العبارات السحابية تُخزَّن محلياً).

---

## ⚙️ البناء والتثبيت (CI/CD)

سير عمل **GitHub Actions** (`.github/workflows/android-release.yml`) يبني عند كل Push:
1. **Debug APK** (`Zimaster-v...-debug.apk`) للتثبيت المباشر.
2. **Release APK** (`Zimaster-v...-release.apk`) — **مُحسَّنة ومضغوطة عبر R8/ProGuard**.
3. **Release AAB** (`Zimaster-v...-release.aab`) جاهزة للنشر على Google Play.
4. رفع كل الملفات تلقائياً إلى **Releases** و **Artifacts**.

> **R8 مفعّل** على نسخة الإصدار (`isMinifyEnabled` + `isShrinkResources`) مع قواعد
> ProGuard تحافظ على سطح الانعكاس (نماذج التسلسل، بناء `AppViewModel`، الودجت،
> Firestore). إن حدث انهيار في الـ release فقط، فهو نقص قاعدة keep يُعالج بسهولة.

---

## 💻 طريقة البناء محلياً

```bash
# استنساخ المستودع
git clone https://github.com/engmohammeda/Zimaster.git
cd Zimaster

# الاختبارات الأحادية
./gradlew testDebugUnitTest

# بناء الـ Debug
./gradlew assembleDebug

# بناء الـ Release (مع R8) + حزمة AAB
./gradlew assembleRelease bundleRelease
```

> يتطلّب Android Studio أو JDK 17 + Android SDK (compileSdk 36).

---

## 🔐 الأمان والخصوصية

- مفاتيح الـ API **مشفّرة على الجهاز فقط** ولا تُرسل لأي خادم وسيط.
- نسخة الإصدار **مُعتمِدة على العمل Offline** بالكامل لحماية بيانات التعلم.
- قواعد `firestore.rules` تضمن: قراءة المحتوى العام للمستخدمين فقط، وكتابة
  العبارات/الدروس للمسؤول فقط، وتقييد كل مستخدم ببياناته الخاصة.

---

<p align="center">صُنِع بـ ❤️ بواسطة <b>محمد البخيتي</b></p>
