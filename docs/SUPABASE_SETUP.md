<div dir="rtl">

# ☁️ إعداد Supabase لمشروع Zimaster — الدليل الكامل

> **المشروع:** `eduobulbcwgcjruzphgc`
> **الرابط:** <https://eduobulbcwgcjruzphgc.supabase.co>
> **لوحة التحكم:** <https://supabase.com/dashboard/project/eduobulbcwgcjruzphgc>
> **المفتاح العام:** `sb_publishable_rc4aCFLVYRfvvWqMfS2VXg_k4MvTP0V` *(مضمّن في `gradle.properties` — عام بطبيعته)*

هذا الدليل يشرح كل ما يلزم لربط التطبيق بسحابة Supabase: الأسرار، أوامر CLI،
إعداد MCP لوكلاء الذكاء الاصطناعي، النشر، التحقق، وحل المشاكل.

---

## جدول المحتويات

1. [كيف يعمل ضخّ المفاتيح في البناء](#1-كيف-يعمل-ضخّ-المفاتيح-في-البناء)
2. [أسرار GitHub Actions](#2-أسرار-github-actions)
3. [الإعداد على جهازك (local.properties)](#3-الإعداد-على-جهازك-localproperties)
4. [Supabase CLI — الأوامر الكاملة](#4-supabase-cli--الأوامر-الكاملة)
5. [إعداد MCP لوكلاء الذكاء الاصطناعي](#5-إعداد-mcp-لوكلاء-الذكاء-الاصطناعي)
6. [نشر الترحيلات (إنشاء الجداول)](#6-نشر-الترحيلات-إنشاء-الجداول)
7. [التحقق من أن كل شيء يعمل](#7-التحقق-من-أن-كل-شيء-يعمل)
8. [ما الذي تم إصلاحه في هذه الجولة](#8-ما-الذي-تم-إصلاحه-في-هذه-الجولة)
9. [حل المشاكل](#9-حل-المشاكل)
10. [قواعد أمنية لا تُكسر](#10-قواعد-أمنية-لا-تُكسر)

---

## 1. كيف يعمل ضخّ المفاتيح في البناء

`app/build.gradle.kts` يقرأ `SUPABASE_URL` و `SUPABASE_ANON_KEY` ويحوّلهما إلى
حقول في `BuildConfig`، ثم تقرأها `SupabaseClientProvider` عند تشغيل التطبيق.

**ترتيب الأولوية (الأول غير الفارغ يفوز):**

| # | المصدر | متى يُستخدم |
|---|--------|-------------|
| 1 | متغير بيئة `SUPABASE_URL` / `SUPABASE_ANON_KEY` | GitHub Actions (من الأسرار) |
| 2 | `local.properties` | جهازك الشخصي + مسار CI الاحتياطي *(مستثنى من Git)* |
| 3 | `gradle.properties` | القيم العامة المودعة في المستودع |
| 4 | قيمة وهمية `dummy-…` | بناء بلا أي بيانات اعتماد — يفشل بوضوح عند التشغيل |

> 🐛 **خلل تم إصلاحه:** كان الـ CI يكتب الأسرار في `local.properties` لكن Gradle
> لا يقرأ هذا الملف إطلاقًا (`project.findProperty` يرى `gradle.properties`
> ووسائط `-P` فقط). النتيجة: كل بناءات CI كانت تشحن القيم الوهمية بصمت،
> والتطبيق الصادر لا يستطيع الوصول إلى السحابة. الآن يُقرأ الملف، ويُصدَّر
> المتغيّران كمتغيرات بيئة أيضًا، ويطبع Gradle سطرًا في السجل يوضح المصدر:
>
> ```
> Supabase → url=https://eduobulbcwgcjruzphgc.supabase.co | key source=environment | provisioned=true
> ```

---

## 2. أسرار GitHub Actions

**المسار:** المستودع ← `Settings` ← `Secrets and variables` ← `Actions` ← `New repository secret`

> ⚠️ الاسم يجب أن يطابق **بالحرف** (أحرف كبيرة و `_`)، والقيمة بلا مسافات
> ولا علامات تنصيص حولها.

### 🔴 إلزامية

| Name | القيمة | من أين |
|------|--------|--------|
| `SUPABASE_URL` | `https://eduobulbcwgcjruzphgc.supabase.co` | جاهزة |
| `SUPABASE_ANON_KEY` | `sb_publishable_rc4aCFLVYRfvvWqMfS2VXg_k4MvTP0V` | جاهزة |
| `SUPABASE_PROJECT_REF` | `eduobulbcwgcjruzphgc` | جاهزة *(اختيارية الآن — تُقرأ من `supabase/config.toml` عند غيابها)* |
| `SUPABASE_DB_PASSWORD` | كلمة سر المستخدم `postgres` | **نفس كلمة السر التي أدخلتها عند إنشاء المشروع.** لو نسيتها: `Project Settings` ← `Database` ← `Database password` ← `Reset` |
| `SUPABASE_ACCESS_TOKEN` | رمز شخصي بصيغة `sbp_…` | <https://supabase.com/dashboard/account/tokens> ← `Generate new token` ← سمّه `zimaster-ci` *(يظهر مرة واحدة فقط)* |

### 🟡 اختيارية

| Name | القيمة / المصدر | الفائدة |
|------|-----------------|---------|
| `SUPABASE_DB_REGION` | مثال: `eu-central-1` — من `Project Settings` ← `Database` ← Connection string ← **Session pooler** | يُستخدم كخطة بديلة لأخذ النسخة الاحتياطية إن كان المشروع IPv6-only |
| `SUPABASE_SERVICE_ROLE_KEY` | `Project Settings` ← `API` ← `service_role` | لسكربتات الإدارة فقط (`upload_lessons.py`) — **لا تصل للتطبيق أبدًا** |
| `TELEGRAM_BOT_TOKEN` | من `@BotFather` | إرسال نسخ الإصدارات إلى تلجرام |
| `TELEGRAM_CHAT_ID` | من `@userinfobot` | نفس ما سبق |
| `KEYSTORE_BASE64` | `base64 -w0 app.keystore` | توقيع نسخة Release للنشر على Google Play |
| `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` | بيانات مفتاح التوقيع | نفس ما سبق |
| `WIF_PROVIDER` / `WIF_SERVICE_ACCOUNT` | Google Cloud (Workload Identity) | نشر Firebase فقط — تجاهلهما إن لم تستخدم Firebase |

> `GITHUB_TOKEN` يولّده GitHub تلقائيًا — لا تضفه بنفسك.

### أي سير يستهلك ماذا

| السير | الأسرار المستخدمة |
|-------|-------------------|
| `android-release.yml` | `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `TELEGRAM_*`, `KEYSTORE_*` |
| `supabase-migrations.yml` | `SUPABASE_ACCESS_TOKEN`, `SUPABASE_DB_PASSWORD`, `SUPABASE_PROJECT_REF` |
| `supabase-backup.yml` | `SUPABASE_DB_PASSWORD`, `SUPABASE_PROJECT_REF`, `SUPABASE_DB_REGION` |
| `supabase-keepalive.yml` | `SUPABASE_URL`, `SUPABASE_ANON_KEY` |
| `quality.yml` | `GITHUB_TOKEN` فقط |

---

## 3. الإعداد على جهازك (local.properties)

الملف مستثنى من Git تلقائيًا. أنشئه في **جذر المستودع**:

```properties
# local.properties — never committed
SUPABASE_URL=https://eduobulbcwgcjruzphgc.supabase.co
SUPABASE_ANON_KEY=sb_publishable_rc4aCFLVYRfvvWqMfS2VXg_k4MvTP0V
```

أو انسخ القالب المخصّص للسكربتات:

```bash
cp .env.example .env      # ثم املأ القيم السرية
```

> القيم العامة موجودة أصلًا في `gradle.properties`، فالخطوة هذه اختيارية لبناء
> التطبيق. لكنها **إلزامية** لتشغيل سكربتات الإدارة (`upload_lessons.py` تحتاج
> `SUPABASE_SERVICE_ROLE_KEY`).

---

## 4. Supabase CLI — الأوامر الكاملة

### 4.1 التثبيت

```bash
# macOS
brew install supabase/tap/supabase

# Windows (Scoop)
scoop install supabase

# أي نظام عبر npm
npm install -g supabase
```

```bash
supabase --version    # المطلوب v2.79.0 أو أحدث
```

### 4.2 تسجيل الدخول والربط

```bash
supabase login
# يفتح المتصفح → وافق → يعود الرمز إلى الطرفية تلقائيًا

cd /path/to/Zimaster          # ← مهم: من جذر المستودع
supabase link --project-ref eduobulbcwgcjruzphgc
# سيطلب كلمة سر القاعدة: أدخل SUPABASE_DB_PASSWORD
```

> ✅ **`supabase init` لم يعد ضروريًا** — ملف `supabase/config.toml` مودع في
> المستودع ومربوط مسبقًا بمعرّف مشروعك، وهذا بالضبط ما ينتجه `supabase init`.
> الربط يكتب في `supabase/.temp/` (مستثنى من Git).

### 4.3 الأوامر اليومية

```bash
supabase migration new add_course_tags      # إنشاء ملف ترحيل فارغ بالاسم الصحيح
supabase db push --dry-run                  # ماذا سيُطبَّق؟ (بلا تنفيذ)
supabase db push                            # طبّق على المشروع السحابي
supabase db pull                            # اسحب أي تغيير عُمل من لوحة التحكم
supabase db advisors                        # فحص أمني وأداء (RLS ناقصة، فهارس…)
supabase db lint                            # فحص SQL محليًا
```

> ⚠️ **لا تعدّل محتوى ترحيل طُبِّق فعلًا.** أنشئ ترحيلًا جديدًا دائمًا —
> CLI يتتبّع الإصدارات في `supabase_migrations.schema_migrations` ولن يعيد
> تطبيق ملف سابق.

---

## 5. إعداد MCP لوكلاء الذكاء الاصطناعي

خادم **Supabase MCP** يمنح وكيل الذكاء الاصطناعي وصولًا مباشرًا وآمنًا لقاعدة
بيانات مشروعك (قراءة المخطط، تنفيذ SQL، جلب المستندات، تشخيص الأخطاء) بدل
الاعتماد على التخمين. المصادقة عبر **OAuth 2.1** — لا يوجد أي توكن داخل الملفات.

### 5.1 Antigravity (Gemini)

**الخطوة 1** — أنشئ/عدّل الملف:

```
~/.gemini/antigravity/mcp_config.json
```

**الخطوة 2** — الصق هذا المحتوى (نسخة جاهزة مودعة في
[`docs/mcp/antigravity.mcp_config.json`](mcp/antigravity.mcp_config.json)):

```json
{
  "mcpServers": {
    "supabase": {
      "serverUrl": "https://mcp.supabase.com/mcp?project_ref=eduobulbcwgcjruzphgc&features=docs%2Caccount%2Cdatabase%2Cdebugging%2Cdevelopment%2Cfunctions%2Cbranching"
    }
  }
}
```

**الخطوة 3** — أعد تشغيل Antigravity. سيطلب منك إكمال تدفق OAuth للمصادقة مع Supabase.

**الوصول للإعدادات من داخل Antigravity:**
قائمة `···` أعلى لوحة Agent ← `MCP Servers` ← `Manage MCP Servers` ← `View raw config`.
ومن نفس الصفحة يمكنك `Refresh server configs` وتفعيل/تعطيل الخوادم.

**لو فشلت المصادقة:**
افتح Agent Settings بـ `Cmd + ,` (Mac) أو `Ctrl + ,` (Windows/Linux) ←
تبويب `Customizations` ← زر `Authenticate` بجانب خادم Supabase.

### 5.2 بقية الوكلاء (Claude Code · Amp · Codex · Cursor…)

ملف [`.mcp.json`](../.mcp.json) **مودع بالفعل في جذر المستودع** ومربوط بمشروعك،
فتلتقطه هذه الأدوات تلقائيًا عند فتح المستودع — لا إعداد يدوي مطلوب:

```json
{
  "mcpServers": {
    "supabase": {
      "type": "http",
      "url": "https://mcp.supabase.com/mcp?project_ref=eduobulbcwgcjruzphgc&features=docs%2Caccount%2Cdatabase%2Cdebugging%2Cdevelopment%2Cfunctions%2Cbranching"
    }
  }
}
```

### 5.3 Agent Skills (اختياري لكن مفيد)

مهارات جاهزة تعلّم الوكيل أفضل ممارسات Supabase و Postgres:

```bash
npx skills add supabase/agent-skills
```

> ✅ **مثبّتة مسبقًا في هذا المستودع** داخل `.agents/skills/`:
> - `.agents/skills/supabase/` — مهارات Supabase (CLI، MCP، الترحيلات، الأمان)
> - `.agents/skills/supabase-postgres-best-practices/` — 30+ مرجعًا لأفضل ممارسات Postgres

### 5.4 ماذا تعني `features=` في الرابط؟

| الميزة | ماذا تتيح للوكيل |
|--------|------------------|
| `docs` | البحث في وثائق Supabase الرسمية |
| `account` | معلومات الحساب والاشتراك |
| `database` | قراءة المخطط والجداول والفهارس |
| `debugging` | سجلات الأخطاء والتقارير التشخيصية |
| `development` | إنشاء الترحيلات وتعديل المخطط |
| `functions` | إدارة Edge Functions |
| `branching` | إنشاء فروع قاعدة بيانات للتجربة |

### 5.5 فحص الاتصال

```bash
curl -so /dev/null -w "%{http_code}\n" https://mcp.supabase.com/mcp
```

`401` = الخادم يعمل (النتيجة المتوقعة بلا توكن).
`000` أو مهلة = الخادم غير متاح أو الشبكة محجوبة.

---

## 6. نشر الترحيلات (إنشاء الجداول)

الترحيلات مودعة في `supabase/migrations/`:

| الملف | المحتوى |
|-------|---------|
| `20260906000000_init_schema.sql` | 6 جداول، عرض `leaderboard`، 4 دوال، 12 سياسة RLS، Realtime، الصلاحيات |
| `20260907000000_harden_rls_realtime_and_admin.sql` | تقوية أمنية: سدّ ثغرة تصعيد admin، إصلاح Realtime، 5 سياسات إضافية، فهارس |

### الطريقة الأولى — تلقائية (موصى بها)

1. تبويب **Actions** في المستودع
2. من القائمة اليسرى: **Supabase Migrations**
3. **Run workflow** ← **Run workflow**
4. انتظر ✅ (~دقيقة)

### الطريقة الثانية — من جهازك

```bash
supabase link --project-ref eduobulbcwgcjruzphgc
supabase db push --dry-run     # راجع ما سيحدث أولًا
supabase db push
supabase db advisors           # تأكد أنه لا توجد ثغرات RLS
```

### الطريقة الثالثة — SQL مباشر

`Dashboard` ← `SQL Editor` ← الصق محتوى الملفين بالترتيب ← `Run`.

---

## 7. التحقق من أن كل شيء يعمل

### 7.1 من أي طرفية

```bash
export SB_URL="https://eduobulbcwgcjruzphgc.supabase.co"
export SB_KEY="sb_publishable_rc4aCFLVYRfvvWqMfS2VXg_k4MvTP0V"

# 1) المفتاح صالح وجدول lessons موجود و RLS تسمح بالقراءة العامة
curl -s -o /dev/null -w "lessons → %{http_code}\n" \
  -H "apikey: $SB_KEY" -H "Authorization: Bearer $SB_KEY" \
  "$SB_URL/rest/v1/lessons?select=doc_id&limit=1"
#   200 = ✅    401 = المفتاح مرفوض    404 = الترحيلات لم تُنشر بعد

# 2) خدمة Auth تعمل
curl -s -o /dev/null -w "auth → %{http_code}\n" \
  -H "apikey: $SB_KEY" "$SB_URL/auth/v1/settings"

# 3) دالة is_admin() منشورة
curl -s -o /dev/null -w "rpc is_admin → %{http_code}\n" \
  -X POST -H "apikey: $SB_KEY" -H "Authorization: Bearer $SB_KEY" \
  -H "Content-Type: application/json" -d '{}' \
  "$SB_URL/rest/v1/rpc/is_admin"
```

### 7.2 من GitHub Actions

| السير | ماذا يثبت نجاحه |
|-------|-----------------|
| **Supabase Keepalive** ← Run workflow | الرابط + المفتاح + `lessons` + RLS + Auth + مزوّد Google |
| **Supabase Migrations** ← Run workflow | الترحيلات طُبِّقت + تقرير `db advisors` |
| **Code Quality & Security** | 59 فحص سلامة + فحص SQL بمحلّل PostgreSQL حقيقي |
| **Supabase Daily Backup** ← Run workflow | كلمة سر القاعدة صحيحة والاتصال المباشر يعمل |

### 7.3 فحوصات محلية

```bash
python3 check_artifacts.py          # 59 فحصًا لسلامة تكامل Supabase
python3 tools/check_sql.py          # أسماء الترحيلات + RLS + أسرار + تحليل نحوي
```

---

## 8. ما الذي تم إصلاحه في هذه الجولة

### 🐛 أخطاء برمجية

| الملف | المشكلة | الإصلاح |
|-------|---------|---------|
| `app/build.gradle.kts` | أسرار CI تُكتب في `local.properties` ولا تُقرأ أبدًا → كل النسخ تُشحن بمفاتيح وهمية | دالة `supabaseProperty()` بترتيب أولوية واضح + سطر سجل يوضح المصدر + تحذير صريح |
| `gradle.properties` | مفتاح وهمي `eyJ…dummy_anon_key` | المفتاح العام الحقيقي `sb_publishable_…` |
| `app/build.gradle.kts` | BOM `3.1.1` مبني على Kotlin 2.1.10 بينما المشروع على 2.2.21 | ترقية إلى `3.2.6` (آخر إصدار مبني على Kotlin 2.2.21 / AGP 8.10.1) + Ktor `3.3.1` المطابق |
| `SupabaseClientProvider.kt` | يستخدم قيمًا وهمية بصمت | راية `isConfigured` + تحذير في Logcat + رفض مفاتيح `sb_secret_` |
| `.github/workflows/supabase-keepalive.yml` | يطبع رمز HTTP ويخرج بنجاح حتى لو `401` | يفشل على أي رمز غير `2xx` مع تشخيص دقيق + فحص Auth ومزوّد Google |
| `.github/workflows/supabase-backup.yml` | `db.<ref>.supabase.co` غالبًا IPv6-only → `pg_dump` يفشل من GitHub runners؛ وملف فارغ يُرفع كـ«نسخة احتياطية» | محاولة الاتصال المباشر ثم الارتداد إلى Session Pooler + رفض الملفات الأصغر من 2KB + `set -uo pipefail` |
| `.github/workflows/supabase-migrations.yml` | التشغيل اليدوي بلا أسرار يطبع «Skipping» ويخرج ✅ أخضر | التشغيل اليدوي **يفشل** بصراحة؛ ومعرّف المشروع يُقرأ من `config.toml` عند غياب السرّ؛ و`concurrency` يمنع تشغيلين متزامنين |
| `.github/workflows/quality.yml` | وظيفة `./gradlew` بلا Android SDK (فشل بيئي) ومكرّرة مع `android-release.yml` | استُبدلت ببوابات سريعة: Gitleaks + فحص الترحيلات + 59 فحص سلامة |

### 🔐 ثغرات في مخطط القاعدة (`20260907000000_harden_rls_realtime_and_admin.sql`)

| # | الثغرة | الخطورة | الإصلاح |
|---|--------|---------|---------|
| 1 | **تصعيد admin:** `handle_new_user()` كان يمنح admin لأول صف في `auth.users`. والتطبيق يسجّل كل متعلّم **مجهولًا** عند أول تشغيل (`CloudAuth.ensureSignedIn`) → **أول جهاز يفتح التطبيق — ربما غريب — يصبح مدير المنصة كلها**: ينشر الدروس، يبث إعلانات عامة، ويقرأ بريد كل المستخدمين | 🔴 حرجة | admin يُمنح فقط لحساب **حقيقي غير مجهول**، وعند ترقية هوية مجهولة إلى Google/بريد يُرقّى صاحبها إن لم يوجد admin بعد |
| 2 | **Realtime لا يعمل:** `CloudSync.subscribeToRealtimeProgress` يشترك بفلتر `user_id = uid`، وSupabase Realtime لا يستطيع تقييم فلتر على أحداث UPDATE/DELETE إلا إذا كان `REPLICA IDENTITY FULL` → الاشتراك ينجح ثم لا يُطلق أي حدث أبدًا | 🟠 عالية | `replica identity full` على `user_progress` و `announcements` |
| 3 | `lessons_write_admin` بصيغة `FOR ALL` — قاعدة واحدة غامضة تجمع INSERT/UPDATE/DELETE | 🟡 متوسطة | ثلاث سياسات صريحة لكل أمر (أقل صلاحية + أسهل تدقيقًا) |
| 4 | جدول `profiles` بلا سياسة DELETE إطلاقًا → المتعلّم لا يستطيع حذف بياناته | 🟡 متوسطة | `profiles_delete_owner` (المالك فقط) |
| 5 | `profiles_update_owner` تقارن `role = (select …)` فتُرجع `NULL` — أي **رفض** — عندما لا يكون هناك صف بعد | 🟡 متوسطة | تغليف بـ `coalesce(…, 'student')` |
| 6 | عرض `leaderboard` يعتمد ضمنيًا على مالكه لتجاوز RLS؛ وفهارس ناقصة لمسارات القراءة الساخنة | 🟢 منخفضة | تثبيت المالك على `postgres` صراحةً + 3 فهارس (`last_active_millis`, `xp`, `user_roles.role`) |

> ⚠️ **لو كان الترحيل الأول قد طُبِّق فعلًا قبل هذا الإصلاح**، فقد يكون أول
> مستخدم مجهول قد حصل على admin. تحقّق:
> ```sql
> select r.user_id, r.role, p.email, p.is_anonymous
> from public.user_roles r left join public.profiles p on p.user_id = r.user_id
> where r.role = 'admin';
> ```
> ولإصلاحه يدويًا من `SQL Editor`:
> ```sql
> delete from public.user_roles where role = 'admin' and user_id = '<uid المجهول>';
> insert into public.user_roles (user_id, role) values ('<uid حسابك>', 'admin')
>   on conflict do nothing;
> update public.profiles set role = 'admin' where user_id = '<uid حسابك>';
> ```

---

## 9. حل المشاكل

| العَرَض | السبب المرجّح | الحل |
|---------|---------------|------|
| `Skipping keepalive: … not set` | الأسرار غير مضافة | §2 |
| keepalive يعيد `401` | المفتاح خاطئ أو منتهي | أعد نسخه من `Project Settings` ← `API` |
| keepalive يعيد `404` | الترحيلات لم تُنشر | §6 |
| `supabase link` يطلب كلمة سر | طبيعية | `SUPABASE_DB_PASSWORD` |
| `pg_dump: could not translate host … IPv6` | المشروع IPv6-only | أضف `SUPABASE_DB_REGION` (§2) |
| التطبيق يعمل لكن لا يزامن | بناء بمفاتيح وهمية | ابحث في سجل Gradle عن `provisioned=false` |
| `Logcat: Supabase credentials are placeholders` | نفس ما سبق | §1 و §3 |
| أدوات MCP غير ظاهرة في الوكيل | لم تتم المصادقة | §5.1 الخطوة 3 أو زر `Authenticate` |
| `curl https://mcp.supabase.com/mcp` يعيد `000` | الشبكة محجوبة | جرّب شبكة أخرى / VPN |
| تسجيل الدخول بـ Google يفشل | مزوّد Google غير مفعّل أو `Web Client ID` خاطئ | `Authentication` ← `Providers` ← Google؛ و`CloudAuth.DEFAULT_WEB_CLIENT_ID` |
| لوحة الصدارة لا تعرض المستخدمين | حسابك ليس admin | §8 (استعلام `user_roles`) |
| Realtime لا يوصل تحديثات | `REPLICA IDENTITY` | طُبِّق في الترحيل الثاني — تأكد من نشره |

---

## 10. قواعد أمنية لا تُكسر

1. **`sb_publishable_…` عام بطبيعته** — يُشحن داخل الـ APK. هذا مقصود وآمن
   لأن كل طلب يبقى مُصفّى بسياسات RLS.
2. **`sb_secret_…` / `service_role` لا يدخلان المستودع ولا التطبيق أبدًا** —
   يتجاوزان RLS بالكامل. مكانهما: أسرار GitHub أو `.env` المحلي فقط.
3. **`SUPABASE_DB_PASSWORD` و `SUPABASE_ACCESS_TOKEN`** سرّيان — لا يُلصقان في
   محادثة ولا في ملف مودع.
4. **كل جدول في `public` يجب أن يكون RLS مفعّلًا عليه** — بوابة
   `tools/check_sql.py` تفرض هذا تلقائيًا في CI.
5. **أي رمز `sbp_…` ظهر في محادثة أو لقطة شاشة يجب إلغاؤه فورًا** من
   <https://supabase.com/dashboard/account/tokens> وإنشاء بديل.
6. **لا تعدّل ترحيلًا طُبِّق** — أنشئ ترحيلًا جديدًا.

---

## ملفات ذات صلة

| الملف | الدور |
|-------|-------|
| [`supabase/config.toml`](../supabase/config.toml) | إعداد المشروع لـ CLI (مكافئ `supabase init`) |
| [`supabase/migrations/`](../supabase/migrations) | مخطط القاعدة كاملًا |
| [`.mcp.json`](../.mcp.json) | خادم MCP لوكلاء الذكاء الاصطناعي (جذر المستودع) |
| [`docs/mcp/antigravity.mcp_config.json`](mcp/antigravity.mcp_config.json) | قالب Antigravity — يُنسخ إلى `~/.gemini/antigravity/` |
| [`.env.example`](../.env.example) | قالب أسرار السكربتات المحلية |
| [`check_artifacts.py`](../check_artifacts.py) | 59 فحصًا لسلامة التكامل |
| [`tools/check_sql.py`](../tools/check_sql.py) | فحص الترحيلات (أسماء، ترتيب، RLS، أسرار، نحو SQL) |
| [`docs/ARCHITECTURE.md`](ARCHITECTURE.md) | معمارية التطبيق |
| [`docs/FIREBASE_SETUP.md`](FIREBASE_SETUP.md) | إرث Firebase (لم يعد مستخدمًا في التطبيق) |

</div>
