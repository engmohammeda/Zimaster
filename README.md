# 🌟 Zimaster (Z-Mastery) — إتقان الإنجليزية بالتكرار المتباعد والذكاء الاصطناعي

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" alt="Zimaster Logo" width="100" height="100" />
</p>

<p align="center">
  <b>تطبيق أندرويد متقدم وشامل لتعلّم وإتقان اللغة الإنجليزية مبني بأحدث تقنيات Jetpack Compose وخوارزمية FSRS المتقدمة للذاكرة طويلة المدى، مدعوماً بنماذج الذكاء الاصطناعي التوليدي.</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green.svg" alt="Platform" />
  <img src="https://img.shields.io/badge/Kotlin-2.2.21-purple.svg" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-blue.svg" alt="Compose" />
  <img src="https://img.shields.io/badge/Min_SDK-24-orange.svg" alt="Min SDK" />
  <img src="https://img.shields.io/badge/Target_SDK-36-red.svg" alt="Target SDK" />
  <img src="https://img.shields.io/badge/CI%2FCD-GitHub_Actions-2088FF.svg" alt="GitHub Actions" />
</p>

---

## 📑 جدول المحتويات
1. [المميزات الرئيسية](#-المميزات-الرئيسية)
2. [الهندسة المعمارية والتقنيات المستخدمة](#-الهندسة-المعمارية-والتقنيات-المستخدمة)
3. [خوارزمية التكرار المتباعد (FSRS)](#-خوارزمية-التكرار-المتباعد-fsrs)
4. [تكامل الذكاء الاصطناعي (Multi-Provider AI)](#-تكامل-الذكاء-الاصطناعي-multi-provider-ai)
5. [تطبيقات الشاشة المصغرة (App Widgets)](#-تطبيقات-الشاشة-المصغرة-app-widgets)
6. [البناء التلقائي والتثبيت (CI/CD)](#-البناء-التلقائي-والتثبيت-cicd)
7. [طريقة البناء محلياً](#-طريقة-البناء-محليا)
8. [الأمان والخصوصية](#-الأمان-والخصوصية)

---

## 🚀 المميزات الرئيسية

- 🧠 **نظام مراجعة ذكي (FSRS v4.5)**: يعتمد على أحدث خوارزميات التكرار المتباعد الرياضية لحساب فترات النسيان وقوة الذاكرة بدقة فائقة.
- 🤖 **معلم ذكاء اصطناعي متعدد النماذج**: يدعم Gemini، OpenAI، Anthropic Claude، Groq، DeepSeek، Cerebras، و OpenRouter لشرح القواعد وتوليد الأمثلة والتصحيح اللغوي.
- 🎙️ **التدريب الصوتي والمحاكاة (Shadowing)**: استماع بدقة عالية لنطق المتحدثين الأصليين واختبار التحدث والتكرار.
- 📱 **ودجت شاشة رئيسية تفاعلي (Home Widget)**: يعرض مقولات تحفيزية يومية، نسبة التقدم، وحالة سلسلة التعلم (Streak) بتصميم Material 3 متوافق مع كافة المشغلات.
- 💾 **قاعدة بيانات محلية غير متصلة (Offline-First)**: إمكانية التعلم والمراجعة بدون إنترنت مع دعم كامل لتصدير واستيراد النسخ الاحتياطية (Backup/Restore).
- 🔒 **حماية وأمان المفاتيح (Key Protector)**: نظام تشفير متقدم لحماية مفاتيح الـ API الشخصية وعدم تسريبها في السجلات أو المشاركة.

---

## 🛠 الهندسة المعمارية والتقنيات المستخدمة

تم بناء التطبيق باتباع أفضل الممارسات والمعايير الحديثة لتطوير تطبيقات أندرويد (Modern Android Architecture):

```
app/
├── src/main/java/com/zmastery/english/
│   ├── data/             # إدارة البيانات، النماذج، FSRS، التخزين المشفر، والمستودعات
│   ├── domain/           # حالات الاستخدام (Use Cases)، الجدولة، ومعالجة المنطق
│   ├── viewmodel/        # إدارة الحالة (State Management) عبر StateFlow و MVVM
│   ├── ui/               # واجهات Jetpack Compose، النمط والسمات (Material 3 Theme)
│   ├── widget/           # مزودي الودجت للشاشة الرئيسية (AppWidgetProvider & RemoteViews)
│   └── notify/           # إدارة الإشعارات والتنبيهات المجدولة
```

### التقنيات الأساسية:
- **UI Framework**: Jetpack Compose مع Material Design 3.
- **Language**: Kotlin 2.2.21 مع Coroutines و StateFlow للتفاعل الحي للبيانات.
- **Database & Persistence**: Room Database و SharedPreferences مع تشفير البيانات الحساسة.
- **Cloud & Auth**: Firebase / Google Sign-In عبر CredentialManager.
- **Networking**: Ktor / OkHttp مع Serialization لمعالجة استجابات الذكاء الاصطناعي.

---

## 📐 خوارزمية التكرار المتباعد (FSRS)

يستخدم التطبيق خوارزمية **Free Spaced Repetition Scheduler (FSRS)** بدلاً من خوارزميات SM-2 التقليدية:
- حساب دقيق لقوة الاسترجاع (Retrievability $R$).
- تحديث مستوى الصعوبة (Difficulty $D$) واستقرار الذاكرة (Stability $S$) لكل بطاقة/جملة.
- جدولة الفواصل الزمنية وفقاً لنسبة الحفظ المستهدفة (Desired Retention = 90%).

---

## 🤖 تكامل الذكاء الاصطناعي (Multi-Provider AI)

يتيح التطبيق للمستخدم استخدام مفتاحه الخاص للاتصال بالعديد من مزودي الذكاء الاصطناعي:
- **Google Gemini** (Gemini 2.5 / 1.5 Pro & Flash)
- **Anthropic Claude** (Claude 3.5 Sonnet / Haiku)
- **OpenAI** (GPT-4o / GPT-4o-mini)
- **Groq & Cerebras** (استجابات فائقة السرعة)
- **DeepSeek & Mistral & OpenRouter**

---

## 📱 تطبيقات الشاشة المصغرة (App Widgets)

يتضمن التطبيق ويدجت أنيقاً للشاشة الرئيسية:
- يعرض الاقتباس اليومي وسلسلة الانضباط الحالية.
- شريط تقدم تفاعلي يوضح الإنجاز اليومي.
- متوافق مع كافة واجهات أندرويد ومصمم لتجنب أخطاء `RemoteViews` على أجهزة Xiaomi و Samsung و Pixel.

---

## ⚙️ البناء التلقائي والتثبيت (CI/CD)

يحتوي المستودع على سير عمل مؤتمت بالكامل عبر **GitHub Actions** (`.github/workflows/android-release.yml`):

### عند كل Push أو تشغيل يدوي:
1. **بناء نسخة التجربة والتطوير (`Zimaster-v...-debug.apk`)**: جاهزة للتثبيت المباشر على أي هاتف بدون قيود.
2. **بناء نسخة الإصدار النهائي (`Zimaster-v...-release.apk`)**: نسخة محسنة ومضغوطة عبر ProGuard.
3. **بناء حزمة المتجر (`Zimaster-v...-release.aab`)**: جاهزة للنشر على Google Play Console.
4. **النشر التلقائي**: رفع جميع الملفات تلقائياً إلى صفحة **Releases** وقسم **Artifacts** في المستودع.

---

## 💻 طريقة البناء محلياً

لبناء المشروع على جهازك باستخدام Android Studio أو سطر الأوامر:

```bash
# استنساخ المستودع
git clone https://github.com/engmohammedalbkhyty-star/Zimaster.git
cd Zimaster

# تشغيل الفحوصات والاختبارات الأحادية
./gradlew testDebugUnitTest

# بناء نسخة الـ Debug
./gradlew assembleDebug

# بناء نسخة الـ Release وحزمة AAB
./gradlew assembleRelease bundleRelease
```

---

## 🔐 الأمان والخصوصية

- مفاتيح الـ API يتم تخزينها مشفرة على جهاز المستخدم فقط ولا تُرسل لأي خادم وسيط.
- التطبيق يدعم العمل الكامل Offline لحماية خصوصية بيانات التعلم والملاحظات.

---

<p align="center">
  صُنِع بـ ❤️ بواسطة <b>محمد البخيتي</b>
</p>
