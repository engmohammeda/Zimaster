# ☁️ دليل Supabase الكامل — من الصفر حتى التشغيل التلقائي

> **مشروعك السحابي:** `eduobulbcwgcjruzphgc`
> **الرابط:** https://eduobulbcwgcjruzphgc.supabase.co
>
> **هدف هذا الدليل:** بعد تنفيذه مرة واحدة (~20 دقيقة)، يعمل كل شيء تلقائياً:
> التطبيق يتصل بالسحابة، الترحيلات (`supabase/migrations/`) تُنشر نفسها عند الدمج
> في `main`، نسخة احتياطية يومية من القاعدة، وفحص حياة (keepalive) يمنع تجميد
> المشروع المجاني.
>
> **ما تم إنجازه داخل المستودع:** مفاتيح التطبيق في `gradle.properties`، قراءة
> `local.properties` في `app/build.gradle.kts`، ملف `supabase/config.toml`، سير
> GitHub Actions الستة، ومهارات الوكلاء في `.agents/skills/`. ما تبقى عليك هو
> **أسرار GitHub + أوامر CLI + إعداد MCP** كما يلي.

---

## 0) خريطة الدليل — ماذا تفعل بالضبط؟

| # | الخطوة | تكرارها | القسم |
|---|--------|---------|-------|
| 1 | فهم مفاتيح التطبيق (جاهزة — لا تفعل شيئاً) | — | [§1](#1-مفاتيح-التطبيق-جارية-جاهزة) |
| 2 | إضافة 5 أسرار في GitHub | مرة واحدة | [§2](#2-أسرار-github-الخمسة-مطلوبة) |
| 3 | تثبيت Supabase CLI + `login` + `link` | مرة واحدة لكل جهاز | [§3](#3-أوامر-supabase-cli-مطلوبة-مرة-واحدة) |
| 4 | كلمة سر القاعدة + رابط Postgres المباشر | مرة واحدة | [§4](#4-كلمة-سر-القاعدة-ورابط-postgres-المباشر) |
| 5 | إعداد MCP في Antigravity | مرة واحدة | [§5](#5-إعداد-mcp-في-antigravity-مطلوب-مرة-واحدة) |
| 6 | تثبيت Agent Skills (اختياري — موجودة مسبقاً في المستودع) | — | [§6](#6-مهارات-الوكلاء-agent-skills-موجودة-مسبقاً) |
| 7 | سكربتات الإدارة (رفع الدروس/الترحيل من Firebase) | عند الحاجة | [§7](#7-سكربتات-الإدارة) |
| 8 | التحقق من أن كل شيء يعمل | مرة بعد الإعداد | [§8](#8-التحقق-5-دقائق) |
| 9 | حل المشاكل | عند الحاجة | [§9](#9-حل-المشاكل-الشائعة) |

---

## 1) مفاتيح التطبيق (جارية — جاهزة)

التطبيق يقرأ بيانات الاتصال من `BuildConfig` (انظر `SupabaseClientProvider.kt`)،
والقيم محقونة في `app/build.gradle.kts` بهذه الأولوية (أول قيمة غير فارغة تفوز):

1. **متغيرات البيئة** `SUPABASE_URL` / `SUPABASE_ANON_KEY` (الأعلى — للـ CI)
2. **`local.properties`** في جذر المستودع (مُتجاهَل من Git — لتجاوزاتك المحلية)
3. **خصائص Gradle**: ملف `gradle.properties` المودَع أو أعلام `-P` (القيم الافتراضية)
4. قيم وهمية تسمح بالترجمة فقط (`dummy-project…`)

القيم المودعة حالياً في `gradle.properties`:

```properties
SUPABASE_URL=https://eduobulbcwgcjruzphgc.supabase.co
SUPABASE_ANON_KEY=sb_publishable_rc4aCFLVYRfvvWqMfS2VXg_k4MvTP0V
```

> 🔑 المفتاح أعلاه هو **المفتاح العام (publishable)** — مصمم ليُضمَّن في تطبيقات
> العميل (وهو موجود أصلاً داخل أي APK تبنيه). الأمان الحقيقي يأتي من سياسات RLS
> في `supabase/migrations/`. **لا تنشر أبداً** المفتاح السرّي (`sb_secret_…`) أو
> كلمة سر القاعدة — هذان للخادم/الإدارة فقط.

---

## 2) أسرار GitHub الخمسة (مطلوبة)

بدونها تتخطى سير العمل مهامها بصمت (`Skipping … not set`). أضفها من:

**GitHub → المستودع → Settings → Secrets and variables → Actions → New repository secret**

| السر | القيمة / من أين تجلبه | يستخدمه |
|-----|------------------------|---------|
| `SUPABASE_URL` | `https://eduobulbcwgcjruzphgc.supabase.co` | بناء التطبيق + keepalive |
| `SUPABASE_ANON_KEY` | `sb_publishable_rc4aCFLVYRfvvWqMfS2VXg_k4MvTP0V` | بناء التطبيق + keepalive |
| `SUPABASE_PROJECT_REF` | `eduobulbcwgcjruzphgc` | الترحيلات + النسخ الاحتياطي |
| `SUPABASE_DB_PASSWORD` | كلمة سر `postgres` — من Dashboard ← **Project Settings ← Database** (انظر [§4](#4-كلمة-سر-القاعدة-ورابط-postgres-المباشر)) | الترحيلات + النسخ الاحتياطي |
| `SUPABASE_ACCESS_TOKEN` | رمز وصول شخصي من https://supabase.com/dashboard/account/tokens (أنشئ واحداً باسم `zmastery-ci`) | ترحيلات قاعدة البيانات |

> ⚠️ لا تضع قيمة `[YOUR-PASSWORD]` الحرفية — يجب استبدالها بكلمة السر الحقيقية.

---

## 3) أوامر Supabase CLI (مطلوبة — مرة واحدة)

هذه الأوامر تُنفَّذ على **جهازك** (وليس في CI — الـ CI يفعلها تلقائياً):

```bash
# 1) تثبيت الـ CLI (مرة واحدة)
npm install -g supabase        # أو: brew install supabase/tap/supabase

# 2) تسجيل الدخول (يفتح المتصفح مرة واحدة)
supabase login

# 3) الربط بالمشروع — نفّذه من جذر المستودع (مرة واحدة لكل نسخة)
#    (ملف supabase/config.toml يعرّف المشروع مسبقاً — وهو مكافئ supabase init)
supabase link --project-ref eduobulbcwgcjruzphgc
```

الأوامر اليومية بعد الربط:

```bash
supabase db push     # نشر ترحيلات supabase/migrations/ إلى السحابة
supabase db pull     # سحب أي تغيير أُجري من الـ Dashboard كملف ترحيل
supabase migration new <name>  # إنشاء ملف ترحيل جديد
supabase db lint     # فحص جودة الـ SQL قبل النشر
```

> 🤖 **تلقائياً:** أي `push` إلى `main` يمس `supabase/migrations/` يشغّل سير
> `supabase-migrations.yml` الذي ينشر الترحيلات بنفسه (يحتاج سرَّي
> `SUPABASE_ACCESS_TOKEN` و `SUPABASE_PROJECT_REF` من [§2](#2-أسرار-github-الخمسة-مطلوبة)).

---

## 4) كلمة سر القاعدة ورابط Postgres المباشر

رابط الاتصال المباشر بالقاعدة:

```
postgresql://postgres:[YOUR-PASSWORD]@db.eduobulbcwgcjruzphgc.supabase.co:5432/postgres
```

استبدل `[YOUR-PASSWORD]` بكلمة السر الحقيقية — تجدها (أو تعيد تعيينها) من:

**Supabase Dashboard ← Project Settings ← Database ← Database password**

استخداماتها:

- نفس القيمة تُستخدم في سر `SUPABASE_DB_PASSWORD` (سير النسخ الاحتياطي اليومي).
- للاتصال اليدوي بـ `psql`:
  ```bash
  PGPASSWORD='<DB_PASSWORD>' psql \
    -h db.eduobulbcwgcjruzphgc.supabase.co -U postgres -d postgres
  ```

---

## 5) إعداد MCP في Antigravity (مطلوب — مرة واحدة)

هذا الإعداد على **جهازك** (خارج المستودع) ليعطي مساعد Antigravity وصولاً مباشراً
لمشروع Supabase:

1. افتح/أنشئ الملف `~/.gemini/antigravity/mcp_config.json` وضع فيه:
   ```json
   {
     "mcpServers": {
       "supabase": {
         "serverUrl": "https://mcp.supabase.com/mcp?project_ref=eduobulbcwgcjruzphgc&features=docs%2Caccount%2Cdatabase%2Cdebugging%2Cdevelopment%2Cfunctions%2Cbranching"
       }
     }
   }
   ```
   (من داخل Antigravity: قائمة `···` أعلى لوحة الوكيل ← MCP Servers ←
   Manage MCP Servers ← View raw config).
2. أعد تشغيل Antigravity ← سيطلب منك إتمام تسجيل الدخول (OAuth) مع Supabase.
3. إن واجهت مشاكل مصادقة: إعدادات الوكيل (`Ctrl+,`) ← تبويب Customizations ←
   زر Authenticate بجانب خادم Supabase.

---

## 6) مهارات الوكلاء Agent Skills (موجودة مسبقاً)

حزمة `supabase/agent-skills` موّدعة فعلاً في المستودع تحت `.agents/skills/`:

- `supabase` — تعليمات العمل مع Supabase
- `supabase-postgres-best-practices` — أفضل ممارسات Postgres

إن أردت إعادة تثبيتها/تحديثها لأدواتك المحلية:

```bash
npx skills add supabase/agent-skills
```

---

## 7) سكربتات الإدارة

سكربتا البايثون يحتاجان **المفتاح السرّي** (`sb_secret_…`) لأنهما يكتبان متجاوزَين
صلاحيات العميل العادية — احصل عليه من **Dashboard ← Project Settings ← API ←
Secret key** (لا تضعه في Git أبداً):

```bash
cp .env.example .env   # ثم عبّئ القيم الحقيقية في .env
export $(grep -v '^#' .env | xargs)

pip install supabase
python upload_lessons.py                 # رفع الدروس دفعة واحدة إلى جدول lessons
python tools/migrate_firestore_to_supabase.py   # ترحيل البيانات من Firebase
```

---

## 8) التحقق (5 دقائق)

1. **الاتصال من سطر الأوامر** (بنفس مفتاح التطبيق):
   ```bash
   curl -s "https://eduobulbcwgcjruzphgc.supabase.co/rest/v1/lessons?select=doc_id&limit=1" \
     -H "apikey: sb_publishable_rc4aCFLVYRfvvWqMfS2VXg_k4MvTP0V" \
     -H "Authorization: Bearer sb_publishable_rc4aCFLVYRfvvWqMfS2VXg_k4MvTP0V"
   ```
   النجاح = مصفوفة JSON (قد تكون فارغة `[]` إن لم تُرفع دروس بعد) — وليس `401`.
2. **سير Keepalive**: بعد إضافة الأسرار، شغّل `supabase-keepalive.yml` يدوياً من
   تبويب Actions — يجب أن ينجح ويعيد صفاً واحداً.
3. **أول حساب حقيقي = مسؤول**: دالة `handle_new_user()` تجعل **أول حساب غير ضيف
   يُسجَّل في المشروع `admin`** تلقائياً (حسابات الضيوف المجهولة تبقى `student`
   دائماً) — سجّل حسابك (بريد/Google) قبل مشاركة التطبيق.
4. **التطبيق**: ابنِ `assembleDebug` وثبّته — سجّل الدخول (بريد/Google) وراقب جدول
   `profiles` في Dashboard ← Table Editor.

---

## 9) حل المشاكل الشائعة

| العرض | السبب الغالب | الحل |
|-------|--------------|------|
| `401 Invalid API key` من REST | مفتاح خاطئ/مبتور | انسخ `sb_publishable_…` كاملاً من Dashboard ← Project Settings ← API |
| سير العمل يطبع `Skipping … not set` | سر ناقص في GitHub | راجع جدول [§2](#2-أسرار-github-الخمسة-مطلوبة) وأضف الناقص |
| `supabase link` يطلب كلمة سر القاعدة | أول ربط تفاعلي | أدخل كلمة سر القاعدة من [§4](#4-كلمة-سر-القاعدة-ورابط-postgres-المباشر) (تُحفظ محلياً في `supabase/.temp/` المُتجاهَل من Git) |
| `FATAL: password authentication failed` | كلمة سر خاطئة | أعد تعيينها من Project Settings ← Database |
| التطبيق يبني لكن لا يزامن | RLS أو أول مستخدم ليس admin | تحقق من السياسات في الترحيل، وسجّل حساب المسؤول أولاً |
| MCP لا يتصل في Antigravity | OAuth غير مكتمل | زر Authenticate كما في [§5](#5-إعداد-mcp-في-antigravity-مطلوب-مرة-واحدة) ثم Refresh |

---

## 🔐 ملاحظة أمنية أخيرة

| السر | الحكم |
|------|-------|
| `SUPABASE_URL` + `sb_publishable_…` | ✅ عام — في التطبيق و `gradle.properties` و GitHub Secrets |
| `sb_secret_…` (service role) | 🚫 خادم فقط — `.env` محلي + أسرار CI عند الحاجة، **لا يُودَع في Git أبداً** |
| كلمة سر `postgres` | 🚫 إدارة فقط — مدير كلمات السر + سر `SUPABASE_DB_PASSWORD` |
| `SUPABASE_ACCESS_TOKEN` | 🚫 إدارة فقط — سر GitHub، و `supabase login` تفاعلياً محلياً |
