# 🔥 دليل Firebase الكامل — من الصفر حتى نشر تلقائي بلا تدخل يدوي

> **هدف هذا الدليل:** بعد اتباعه حرفياً مرة واحدة، يصبح كل شيء يعمل تلقائياً إلى الأبد:
> تسجّل دخول كمسؤول، تنشر درساً من داخل التطبيق، يصل فوراً لكل طالب، تقدّم كل طالب
> يُحفظ بأمان في السحابة، وأي تعديل تدفعه على `firestore.rules` أو
> `firestore.indexes.json` **ينشر نفسه تلقائياً** عبر GitHub Actions دون أن تكتب
> أمراً واحداً بيدك.
>
> **كل الأكواد جاهزة داخل المستودع** — ما تحتاجه هو تنفيذ إعداد Firebase نفسه
> (مرة واحدة، ~15 دقيقة) وربط سرّين في GitHub (~5 دقائق).

---

## 0) خريطة الدليل — ماذا تفعل بالضبط؟

| # | الخطوة | تكرارها | القسم |
|---|--------|---------|-------|
| 1 | إنشاء/ربط مشروع Firebase باسم `zmastery` | مرة واحدة | [§1](#1-إنشاء-مشروع-firebase) |
| 2 | تفعيل Firestore + طرق الدخول (بريد/Google/ضيف) | مرة واحدة | [§2](#2-تفعيل-firestore-وطرق-الدخول) |
| 3 | ربط تطبيق أندرويد + تنزيل `google-services.json` | مرة واحدة | [§3](#3-ربط-تطبيق-أندرويد) |
| 4 | **تفعيل النشر التلقائي للقواعد/الفهارس عبر GitHub Actions** (نسخ قالب جاهز + سرّ واحد) | مرة واحدة للإعداد، بعدها تلقائي للأبد | [§4](#4-النشر-التلقائي-للقواعد-والفهارس-عبر-github-actions) |
| 5 | تفعيل بريدك كمسؤول (توثيق البريد) | مرة واحدة | [§5](#5-أن-تصبح-مسؤولاً-فعلياً-موثّق-البريد) |
| 6 | نشر الدروس من التطبيق | يومياً | [§6](#6-نشر-الدروس-لطلابك-سير-العمل-اليومي) |
| 7 | تجربة القبول الكاملة | مرة بعد كل إعداد | [§7](#7-تجربة-المسار-كاملاً-5-دقائق) |
| 8 | حل المشاكل | عند الحاجة | [§9](#9-حل-المشاكل-الشائعة) |

```
👑 أنت (المسؤول)                          📱 الطالب
─────────────────                        ─────────────────
GitHub: تعديل firestore.rules
       │
       ▼ push إلى main
       │
   🤖 GitHub Actions ينشر تلقائياً       (لا شيء — يعمل بصمت)
   القواعد + الفهارس إلى Firebase
       │
   أدوات المطور ← نشر الدروس
       │
       ▼ «👑 نشر سحابياً لجميع الطلاب»
       │
   ☁️ Firestore                          يفتح التطبيق ← مزامنة صامتة
   /lessons/{id}              ────────►  الدروس تظهر فوراً
```

---

## 1) إنشاء مشروع Firebase

> إن كان المشروع `zmastery` موجوداً بالفعل (تحقق في [console.firebase.google.com](https://console.firebase.google.com))
> تخطَّ لـ[القسم 2](#2-تفعيل-firestore-وطرق-الدخول).

1. افتح [console.firebase.google.com](https://console.firebase.google.com) وسجّل بحساب Google
   الذي سيكون **مالك التطبيق النهائي** (بريده هو المسجَّل في الكود كسوبر-أدمن —
   راجع [القسم 5](#5-أن-تصبح-مسؤولاً-فعلياً-موثّق-البريد)).
2. **Add project** → اسم المشروع `zmastery` (أو أي اسم؛ إن اخترت اسماً مختلفاً
   حدّث `.firebaserc` في جذر المستودع بنفس المعرّف).
3. عطّل Google Analytics إن لم تحتجه (اختياري، لا يؤثر على شيء في هذا الدليل).
4. انتظر إنشاء المشروع.

---

## 2) تفعيل Firestore وطرق الدخول

### 2.1 إنشاء قاعدة بيانات Firestore
**Console ← Build ← Firestore Database ← Create database**
- الوضع: **Production mode** (القواعد الأمنية الجاهزة في المستودع ستحل محل الافتراضي فوراً).
- الموقع: أي منطقة قريبة (لا يمكن تغييرها لاحقاً، لكنها لا تؤثر على وظائف هذا الدليل).

### 2.2 تفعيل طرق الدخول
**Console ← Build ← Authentication ← Sign-in method**

| الطريقة | فعّلها | لماذا |
|---------|--------|-------|
| Email/Password | ✅ Enable | تسجيل المسؤول والطلاب ببريد |
| Google | ✅ Enable | تسجيل دخول بضغطة واحدة (Web Client ID تلقائي من `google-services.json`) |
| Anonymous | ✅ **إلزامي** | بدونه لا يُنشأ حساب ضيف، فلا تصل الدروس ولا يُحفظ التقدم لأي مستخدم لم يسجّل بعد |

> ⚠️ **Anonymous تحديداً حرج** — التطبيق يستدعي `signInAnonymously()` عند أول
> فتح ليضمن وجود حساب دائماً (`CloudAuth.ensureSignedIn`)؛ تعطيله يعطّل المزامنة كاملة.

---

## 3) ربط تطبيق أندرويد

1. **Console ← Project settings ← Your apps ← Add app ← Android**
2. Package name: **`com.zmastery.english`** (يجب أن يطابق `applicationId` في
   `app/build.gradle.kts` تماماً).
3. أضف بصمة SHA-1 (لازمة لدخول Google تحديداً):
   ```bash
   # من جذر المستودع، إن كان لديك debug.keystore محلي:
   keytool -list -v -keystore debug.keystore -alias androiddebugkey -storepass android | grep SHA1
   ```
   ألصقها في **Project settings ← Your apps ← Android ← Add fingerprint**.
4. نزّل `google-services.json` وضعه في `app/google-services.json`
   (هذا الملف **لا يحتوي أسراراً حقيقية** — فقط معرّفات عامة، لذلك هو مودَع
   بالفعل في المستودع بقيم صالحة؛ استبدله فقط إن أنشأت مشروعاً مختلفاً).

---

## 4) النشر التلقائي للقواعد والفهارس عبر GitHub Actions

هذا هو الجزء الذي يجعل الصيانة **بلا أوامر يدوية بعد اليوم**. المستودع يحوي
جاهزاً في **`docs/ci-templates/firebase-deploy.yml.template`** سير عمل كامل
ينشر `firestore.rules` و `firestore.indexes.json` تلقائياً في كل مرة تُدفع
فيها تعديلات عليهما إلى `main` — **بلا أي مفتاح JSON لحساب خدمة إطلاقاً**،
عبر آلية اسمها **Workload Identity Federation (WIF)**.

> ⚠️ **لماذا هو "قالب" في `docs/` وليس مفعّلاً مباشرة في `.github/workflows/`؟**
> إنشاء/تعديل ملفات داخل `.github/workflows/` يتطلب صلاحية `workflows` خاصة
> على أداة الأتمتة التي أنشأت هذا الفرع، وهي غير ممنوحة افتراضياً لأسباب أمنية
> (نفس القيد الذي يمنع أي أداة خارجية من حقن أوامر تُشغَّل تلقائياً بصلاحياتك).
> **الخطوة التالية (مرة واحدة، دقيقة واحدة):** انسخ الملف يدوياً من مكانه في
> `docs/` إلى مكانه الحقيقي:
> ```bash
> mkdir -p .github/workflows
> cp docs/ci-templates/firebase-deploy.yml.template .github/workflows/firebase-deploy.yml
> git add .github/workflows/firebase-deploy.yml
> git commit -m "ci: تفعيل النشر التلقائي لقواعد Firestore"
> git push
> ```
> بعد هذه الخطوة الواحدة فقط، يعمل كل شيء تلقائياً للأبد كما هو موصوف أدناه.

> 🔒 **لماذا WIF وليس مفتاح JSON تقليدي؟**
> إذا حاولت اتّباع دليل قديم يطلب منك "نزّل مفتاح JSON من Service Accounts"،
> فستجد Google Cloud **ترفض العملية بخطأ**:
> ```
> تم تطبيق سياسة تنظيمية تمنع إنشاء مفاتيح حسابات الخدمة على مؤسستك.
> iam.disableServiceAccountKeyCreation
> ```
> هذه سياسة أمان تُفعَّل **تلقائياً على كل مؤسسة (Organization) جديدة على
> Google Cloud منذ مايو 2024** (أي حساب Google بنطاق بريد مؤسسي وليس
> `@gmail.com` عادةً يقع تحت "مؤسسة"). لا علاقة لها بخطأ في إعداد مشروعك؛
> هي إجراء أمان افتراضي من Google نفسها لمنع تسريب مفاتيح دائمة الصلاحية.
> **الحل الصحيح ليس تعطيل السياسة (يتطلب صلاحية مسؤول مؤسسة عالية وغير
> مستحسن)، بل تجاوز الحاجة للمفتاح أصلاً** عبر WIF: بدل ملف JSON طويل الأمد
> يمكن تسريبه، يُبادِل GitHub Actions رمز هوية مؤقت (OIDC) صادر عنه هو نفسه
> بتوكن Google Cloud صالح لدقائق معدودة فقط، دون أي سرّ دائم يُخزَّن في أي مكان.

### 4.1 الإعداد لمرة واحدة (بلا أي مفتاح JSON) — من Cloud Shell

افتح [Cloud Shell](https://console.cloud.google.com/?cloudshell=true) من نفس
حساب Google المالك لمشروع `zmastery` (أيقونة `>_` أعلى يمين الكونسول)، وشغّل
الأوامر التالية بالترتيب — استبدل `OWNER/REPO` باسم مستودعك الفعلي
(`engmohammeda/Zimaster`):

```bash
# 0) متغيرات تحتاجها كل الأوامر التالية
export PROJECT_ID="zmastery"
export PROJECT_NUMBER=$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')
export REPO="engmohammeda/Zimaster"
export SA_NAME="github-actions-deploy"
export SA_EMAIL="${SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
export POOL_ID="github-pool"
export PROVIDER_ID="github-provider"

# 1) إنشاء حساب الخدمة (إنشاء الحساب نفسه غير ممنوع — الممنوع فقط تنزيل مفتاح JSON له)
gcloud iam service-accounts create "$SA_NAME"   --project="$PROJECT_ID"   --display-name="GitHub Actions – نشر قواعد Firestore"

# 2) منحه أقل صلاحيات كافية لنشر القواعد والفهارس فقط
gcloud projects add-iam-policy-binding "$PROJECT_ID"   --member="serviceAccount:${SA_EMAIL}"   --role="roles/firebaserules.admin"
gcloud projects add-iam-policy-binding "$PROJECT_ID"   --member="serviceAccount:${SA_EMAIL}"   --role="roles/datastore.indexAdmin"
gcloud projects add-iam-policy-binding "$PROJECT_ID"   --member="serviceAccount:${SA_EMAIL}"   --role="roles/serviceusage.serviceUsageConsumer"

# 3) إنشاء Workload Identity Pool مخصّص لـ GitHub Actions
gcloud iam workload-identity-pools create "$POOL_ID"   --project="$PROJECT_ID" --location="global"   --display-name="GitHub Actions Pool"

# 4) إنشاء Provider داخل الـ Pool، مقيّد بمستودعك تحديداً (شرط أمان مهم)
gcloud iam workload-identity-pools providers create-oidc "$PROVIDER_ID"   --project="$PROJECT_ID" --location="global"   --workload-identity-pool="$POOL_ID"   --issuer-uri="https://token.actions.githubusercontent.com"   --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository"   --attribute-condition="assertion.repository=='${REPO}'"

# 5) السماح لهذا المستودع تحديداً بانتحال صفة حساب الخدمة (بلا أي كلمة سرّ)
gcloud iam service-accounts add-iam-policy-binding "$SA_EMAIL"   --project="$PROJECT_ID"   --role="roles/iam.workloadIdentityUser"   --member="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL_ID}/attribute.repository/${REPO}"

# 6) اطبع القيمتين اللتين ستضعهما كأسرار GitHub في الخطوة التالية
echo "WIF_PROVIDER=projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL_ID}/providers/${PROVIDER_ID}"
echo "WIF_SERVICE_ACCOUNT=${SA_EMAIL}"
```

لا يُنتج أي أمر من هذه الأوامر ملف JSON ولا يخالف سياسة
`iam.disableServiceAccountKeyCreation` — لأننا لا ننشئ مفتاحاً إطلاقاً، بل
علاقة ثقة (trust) بين GitHub وGoogle Cloud مقيّدة بمستودعك فقط.

### 4.2 ربط القيمتين في GitHub — مرة واحدة

1. **GitHub ← مستودع Zimaster ← Settings ← Secrets and variables ← Actions
   ← New repository secret**.
2. أضف سرّاً باسم **`WIF_PROVIDER`** وقيمته السطر الذي طُبع أمام
   `WIF_PROVIDER=` في الخطوة السابقة (بدون `WIF_PROVIDER=` نفسها).
3. أضف سرّاً ثانياً باسم **`WIF_SERVICE_ACCOUNT`** وقيمته بريد حساب الخدمة
   المطبوع أمام `WIF_SERVICE_ACCOUNT=`.
4. احفظ. (هاتان القيمتان مجرد معرّفات موارد — لا تحملان صلاحيات بذاتهما، على
   عكس مفتاح JSON، لذا لا خطورة أمنية مماثلة في تسريبهما بالخطأ، رغم أنه
   يُفضَّل دوماً إبقاء أسرار المستودع خاصة.)

### 4.3 من الآن فصاعداً — تلقائي بالكامل

أي `push` إلى `main` يعدّل أياً من:
- `firestore.rules`
- `firestore.indexes.json`
- `firebase.json`
- `.firebaserc`

يُشغّل تلقائياً سير عمل **Firebase Deploy** الذي:
1. يصادق على Google Cloud عبر WIF (بلا أي مفتاح).
2. يتحقق من صحة القواعد (`--dry-run`) أولاً.
3. ثم ينشرها فعلياً إلى مشروع `zmastery`.
4. يُظهر النتيجة في تبويب **Actions** من المستودع.

**لا حاجة لتثبيت `firebase-tools` على جهازك ولا لتسجيل دخول يدوي بعد اليوم،**
**ولا لإدارة أي مفتاح سرّي دائم الصلاحية.**
إن أردت تشغيله يدوياً لأي سبب:
**Actions ← Firebase Deploy (Firestore Rules & Indexes) ← Run workflow**
(يوجد خيار `dry_run` لفحص القواعد فقط بلا نشر فعلي).

### 4.4 (اختياري) نشر يدوي من جهازك أيضاً

إن كنت تفضّل النشر من جهازك أحياناً (تطوير محلي/تجربة سريعة)، ما زال ذلك ممكناً
عبر تسجيل الدخول التفاعلي العادي (لا علاقة له بسياسة منع المفاتيح، لأنه لا
يُنشئ مفتاح حساب خدمة، بل جلسة دخول شخصية مؤقتة):
```bash
npm install -g firebase-tools && firebase login
cd Zimaster && firebase deploy --only firestore:rules,firestore:indexes
```
هذا **لا يتعارض** مع النشر التلقائي؛ كلاهما ينشر نفس الملفين لنفس المشروع.

---

## 5) أن تصبح مسؤولاً فعلياً (موثّق البريد)

الكود في `firestore.rules` (`isSuperAdminToken()`) وفي `CloudController.kt`
(`isVerifiedOwnerAccount`) يمنحان صلاحيات المالك الكاملة **فقط** لحساب ببريد:

```
mohammedalbkhyty@gmail.com
```

**وبشرط أن يكون هذا البريد موثّقاً (`email_verified = true`)** — هذا الشرط
أساسي أمنياً: بدونه يستطيع أي شخص إنشاء حساب بريد/كلمة مرور بهذا الإيميل بالذات
(Firebase Auth لا يمنع التسجيل بإيميل لا تملكه) وانتحال صلاحيات المالك الكاملة.

> إن كان مشروعك يستخدم بريداً مختلفاً، حدّث القيمة في **كلا** الملفين معاً:
> `firestore.rules` (دالة `isSuperAdminToken`) و
> `app/src/main/java/com/zmastery/english/viewmodel/CloudController.kt`
> (`OWNER_EMAIL`) و `CloudSync.kt` (`SUPER_ADMIN_EMAILS`).

### كيف توثّق بريدك؟

| طريقة الدخول | التوثيق |
|--------------|---------|
| **Google Sign-In** | موثّق تلقائياً وفوراً — لا خطوة إضافية. **الطريقة الموصى بها.** |
| **بريد + كلمة مرور** | عند إنشاء الحساب يُرسَل رابط تأكيد تلقائياً إلى بريدك. افتح بريدك واضغط الرابط، ثم في التطبيق: **أدوات المطور ← «تحققت — أعد الفحص»** (يظهر تلقائياً إن كان بريدك مطابقاً للمالك وغير موثّق بعد). |

### خطوات التفعيل الكاملة

1. ثبّت نسخة التطبيق (من GitHub Releases أو ابنِها محلياً).
2. سجّل الدخول بحساب `mohammedalbkhyty@gmail.com` — **يُفضَّل بـ Google** لتفادي
   خطوة التوثيق يدوياً.
3. إن سجّلت ببريد/كلمة مرور: افتح بريدك، اضغط رابط التوثيق المُرسَل تلقائياً،
   ثم من داخل التطبيق: **الإعدادات ← أدوات المطور 👑 ← «تحققت — أعد الفحص»**.
4. عند أول دخول موثّق يكتب التطبيق تلقائياً `role = "admin"` في `/users/{uid}` —
   القواعد تثق بهذا لأن الإيميل والتوثيق كلاهما من Firebase Auth الموثّق، لا من العميل.
5. **تحقق:** الإعدادات ← سترى قسم «👑 صلاحيات المطور والمسؤول» + قائمة الطلاب +
   الإعلانات، وسيظهر زر الاستيراد في كل مكان.

> 💡 كود `ADMIN2026` يفتح **وضع مطور محلي** فقط (أدوات الواجهة تظهر لك). صلاحيات
> **الكتابة السحابية الفعلية** (نشر درس، بث إعلان) تُرفض من القواعد ما لم يكن
> حسابك هو حساب المالك الموثّق أو بدور `admin` مكتوب فعلاً في `/users/{uid}`.
> هذا تصميم أمني مقصود — لا يمكن تجاوزه من العميل.

---

## 6) نشر الدروس لطلابك (سير العمل اليومي)

1. افتح **المزيد ← أدوات المطور 👑 ← رفع ونشر الدروس** (يظهر لك وحدك).
2. ارفع ملفات `JSON` (أو `ZIP` بعدة ملفات) أو الصق كود JSON مباشرة.
3. يتعرف التطبيق على الدروس ويعرض عددها ومحتواها — **لن يُرفع شيء بعد**.
4. راجع، ثم اضغط **«👑 نشر N درس سحابياً لجميع الطلاب 🚀»**.
   (زر «إضافة إلى المكتبة المحلية» يضيفها على جهازك فقط.)
5. بعد ثوانٍ: رسالة نجاح تعني أنها في `/lessons` — **كل طالب يفتح تطبيقه يسحبها فوراً**.

### تحديث درس منشور مسبقاً
أعد نشر نفس الدرس بنفس `course_id` ورقم `lesson_no` — التطبيق يكتب فوق نفس
المستند بتوقيت أحدث (`updated_at`) فتسحبه أجهزة الطلاب في أول فتح كتحديث.

### صيغة الدرس (JSON)
استخدم نفس الصيغة الموحدة (`UNIFIED_LESSON_EXTRACTOR_MASTER_PROMPT.md` في جذر
المشروع): `metadata.course_id` + `metadata.lesson_no` هما هوية الدرس السحابية.

### النشر بسكربت بايثون (رفع دفعي خارج التطبيق)
`upload_lessons.py` في جذر المستودع يرفع مجلدات دروس كاملة دفعة واحدة عبر
Firebase Admin SDK (يتجاوز قواعد Firestore لأنه يعمل بصلاحية خادم موثوقة).
**لا يحتاج أي مفتاح JSON** — يستخدم بيانات اعتمادك الشخصية المؤقتة (ADC)،
وهذا يعمل حتى مع سياسة `iam.disableServiceAccountKeyCreation` المفعّلة على
مؤسستك (راجع صندوق التنبيه في §4 أعلاه لشرح هذه السياسة):
```bash
pip install firebase-admin
gcloud auth application-default login \
  --scopes=https://www.googleapis.com/auth/cloud-platform
python3 upload_lessons.py
```
أول أمر يفتح متصفحاً لتسجيل الدخول بحسابك (مرة واحدة فقط، الجلسة تُخزَّن
محلياً على جهازك ويمكن إلغاؤها لاحقاً بـ `gcloud auth application-default revoke`).
السكربت نفسه يكتشف تلقائياً غياب `serviceAccountKey.json` ويستخدم هذه
الجلسة بدلاً منه — لا حاجة لأي تعديل يدوي على الكود.

> ⚠️ إن كانت مؤسستك من الاستثناءات النادرة التي لا تزال تسمح بتنزيل مفتاح
> حساب خدمة تقليدي، يدعم السكربت ذلك أيضاً: احفظ الملف باسم
> `serviceAccountKey.json` بجانب السكربت — ولا تدفعه إلى git أبداً (مضاف
> بالفعل إلى `.gitignore`).

---

## 7) تجربة المسار كاملاً (5 دقائق)

| # | الخطوة | النتيجة المتوقعة |
|---|--------|------------------|
| 1 | سجّل دخولك بحساب المالك (Google أو بريد موثّق) | قسم 👑 ظاهر في الإعدادات، بلا تحذير «غير موثّق» |
| 2 | انشر درساً واحداً | رسالة نجاح + ظهوره في Firestore ← `/lessons` |
| 3 | على جهاز آخر (أو بعد مسح بيانات التطبيق) سجّل كطالب بريد جديد | **لا زر استيراد في أي مكان** |
| 4 | أغلق التطبيق وافتحه من جديد | الدرس المنشور يظهر تلقائياً في المستويات |
| 5 | أكمل الطالب درساً وأغلق وفتح | التقدم باقٍ + `/users/{uid}/progress` محدّث |
| 6 | من جهازك: الإعدادات ← قائمة الطلاب | الطالب ظاهر بإحصائياته |
| 7 | عدّل `firestore.rules` (تعليق مثلاً) وادفعه إلى `main` | تبويب Actions يُظهر تشغيلة «Firebase Deploy» ناجحة تلقائياً |

---

## 8) أين أرى البيانات في الكونسول؟

| المسار | المحتوى |
|--------|---------|
| `/lessons` | دروسك المنشورة (حقل `json` يحوي الدرس كاملاً) |
| `/users/{uid}` | ملف كل مستخدم (اسم، دور، إحصائيات، آخر نشاط) |
| `/users/{uid}/progress/state` | نسخة تقدمه الاحتياطية (JSON بلا مفاتيح API) |
| `/leaderboard/{uid}` | مرآة إحصائيات عامة بلا إيميل (للوحة الصدارة) |
| `/quotes` | عبارات التحفيز التي تضيفها (تظهر بالودجت) |
| `/announcements` | إعلاناتك العامة |

---

## 9) حل المشاكل الشائعة

| المشكلة | السبب | الحل |
|---------|-------|------|
| «فشل النشر السحابي: PERMISSION_DENIED» | القواعد القديمة ما زالت منشورة، أو نشرك التلقائي لم يعمل بعد | تحقق من تبويب **Actions** في GitHub — إن فشلت التشغيلة راجع سجلّها؛ الأغلب أن `WIF_PROVIDER`/`WIF_SERVICE_ACCOUNT` غير مضبوطين أو صلاحيات حساب الخدمة ناقصة (§4) |
| «تم تطبيق سياسة تنظيمية تمنع إنشاء مفاتيح حسابات الخدمة... iam.disableServiceAccountKeyCreation» | حاولت تنزيل مفتاح JSON تقليدي من Service Accounts، لكن مؤسستك تمنع ذلك افتراضياً | هذا متوقّع وليس عطلاً — لا تحاول تعطيل السياسة؛ اتّبع §4.1 (Workload Identity Federation، بلا أي مفتاح) للنشر التلقائي، و§6 (`gcloud auth application-default login`) لسكربت `upload_lessons.py` |
| النشر يفشل رغم القواعد الجديدة، والرسالة تذكر «غير موثّق» | بريدك مطابق للمالك لكن `email_verified = false` | افتح بريدك واضغط رابط التوثيق، ثم اضغط «تحققت — أعد الفحص» في أدوات المطور (§5) |
| النشر يفشل ولا تظهر أي إشارة للتوثيق | لم تسجّل الدخول بحساب المالك أصلاً | سجّل بـ `mohammedalbkhyty@gmail.com` ثم أعد المحاولة |
| الطالب لا يستلم الدروس | Anonymous معطّل، أو المزامنة متوقفة من إعداداته | فعّل Anonymous (§2.2) |
| دخول Google يعطي رمز 10 | SHA-1 غير مسجل في Firebase | أضف بصمة SHA-1 (§3.3) |
| تقدم الطالب ضاع بعد تغيير الجهاز | لم يسجّل دخولاً (جلسة مجهولة لا تنتقل) | سجّل دخوله بنفس البريد/حساب Google |
| لوحة الصدارة فارغة | تُبنى تلقائياً عند أول مزامنة لكل مستخدم | افتح التطبيق مرة على الأقل ككل مستخدم |
| سير عمل «Firebase Deploy» يفشل بخطأ مصادقة WIF (`unable to generate access token`/`permission denied on resource`) | ربط انتحال الصفة (§4.1 خطوة 5) غير مضبوط، أو `attribute-condition` لا يطابق اسم مستودعك فعلياً | تأكد أن `REPO` في أوامر §4.1 مطابق حرفياً لاسم مستودعك (`OWNER/REPO`) وأن خطوة `add-iam-policy-binding` نُفّذت بنجاح |
| سير عمل «Firebase Deploy» يفشل بخطأ صلاحيات (403/`PERMISSION_DENIED`) | حساب الخدمة ينقصه دور | أضف له `roles/firebaserules.admin` + `roles/datastore.indexAdmin` من IAM (§4.1) |

---

## 10) الأوامر الجاهزة (نسخ ولصق — نشر يدوي اختياري)

```bash
# إعداد أول مرة (فقط إن أردت النشر من جهازك أيضاً)
npm install -g firebase-tools && firebase login
cd Zimaster && firebase use zmastery

# نشر القواعد والفهارس يدوياً (بعد أي تعديل عليها)
firebase deploy --only firestore:rules,firestore:indexes

# فحص بلا نشر فعلي (يتحقق من صحة القواعد فقط)
firebase deploy --only firestore:rules,firestore:indexes --dry-run

# التحقق من القواعد المنشورة فعلياً حالياً
firebase firestore:rules --project zmastery
```

---

## 11) مرجع سريع — كل ملفات Firebase في المستودع

| الملف | الدور |
|-------|-------|
| `firebase.json` | يشير إلى ملفي القواعد والفهارس + إعداد المحاكيات المحلية |
| `.firebaserc` | يربط المستودع بمعرّف مشروع Firebase الافتراضي (`zmastery`) |
| `firestore.rules` | قواعد الأمان الكاملة — القسم الوحيد الذي يحدد من يقرأ/يكتب ماذا |
| `firestore.indexes.json` | الفهارس المركّبة اللازمة لاستعلامات `announcements` و`lessons` |
| `docs/ci-templates/firebase-deploy.yml.template` | قالب سير عمل ينشر القواعد/الفهارس تلقائياً — انسخه إلى `.github/workflows/firebase-deploy.yml` مرة واحدة (§4) ليصبح فعّالاً |
| `.github/workflows/android-release.yml` | يبني APK/AAB ويصدرها كـ Release (لا علاقة له بـ Firestore) |
| `app/google-services.json` | معرّفات مشروع Firebase العامة للتطبيق (لا أسرار حقيقية) |
| `upload_lessons.py` | رفع دفعي للدروس بصلاحية خادم (Admin SDK) — لسيناريوهات الاستيراد الضخم |

*القواعد والفهارس والسير الآلي كلها جاهزة في المستودع — إعدادك الوحيد المطلوب
هو §1 إلى §5 أعلاه، مرة واحدة فقط، ثم كل شيء يعمل تلقائياً للأبد.*
