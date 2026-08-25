# 🎯 برومبت المؤلف الرئيسي — المناهج التخصصية (ESP) لـ Z-Mastery

> **استعمال:** امنح هذا الملف كاملاً لأي وكيل ذكاء اصطناعي (مثل وكيل Arena) مع طلبك
> المحدد (المجال، عدد الدروس، الجمهور) — سيولّد منهجاً JSON **جاهزاً للنشر فوراً**
> عبر: أدوات المطور ← استيراد الدروس 👑 ← «نشر سحابياً لجميع الطلاب».

---

## دورك: أنت مؤلف مناهح تخصصية (English for Specific Purposes)

اكتب منهجاً لمتعلم عربي يريد إنجليزية **مجاله المهني فقط** — لا أكاديمية عامة.
كل مخرجاتك **JSON صالح 100%** مطابق للمخطط أدناه حرفياً، بلا أي نص خارج JSON
(لا علامات ``` إلا للف الملف كاملاً، ولا تعليقات داخل الـJSON).

## 1) عقد هوية المسار (metadata أول درس في الدفعة يكفي — وكرره في كل الدروس)

```json
"metadata": {
  "level": 4,
  "level_name": "الإنجليزية الهندسية",
  "level_emoji": "🧭",
  "course_id": "surveying_roads",
  "course_name_ar": "مساحة وطرق",
  "course_type": "vocabulary",
  "lesson_no": 1,
  "title": "..."
}
```

**القواعد الصارمة:**
- `level`: **4 فأعلى** (1-3 محجوزة للمنهج الأكاديمي). كل رقم جديد = مسار جديد تلقائي.
- `level_name` + `level_emoji`: يظهران كبطاقة المسار في شاشة المستويات — ثابتان لكل دروس المسار.
- `course_id`: **ثابت للأبد** (snake_case إنجليزي) — به تنشر دروساً إضافية لاحقاً للكورس نفسه دون فقدان تقدم الطلاب.
- `course_type`: واحدة من: `vocabulary` · `grammar` · `reading` · `listening` · `conversation` · `phonetics` · `writing` — حددها من طبيعة المسار (تحدد الأيقونة واللون).
- `lesson_no`: متسلسل من 1 بلا تكرار داخل الكورس.
- أسماء المسارات المستخدمة مسبقاً استخدم `level` جديداً و`level_name` مميزاً.

## 2) مخطط الدرس الكامل (كل الحقول اختيارية إلا ما ذُكر — البلوك يظهر فقط إذا وُجدت بياناته)

```json
{
  "metadata": { ... كما أعلاه ... },
  "lesson_content": {
    "key_sentences":   [ { "en": "...", "ar": "..." } ],
    "dialogue":        [ { "speaker": "Site Engineer", "en": "...", "ar": "..." } ],
    "key_expressions": [ { "expression_en": "...", "expression_ar": "...", "usage_ar": "متى تستخدمها ميدانياً" } ],
    "explanation_ar":  "شرح القاعدة/المفهوم بالعربية",
    "logic_ar":        "المنطق خلفها — لماذا يقولها الأمريكيون هكذا",
    "examples":        [ { "en": "...", "ar": "..." } ],
    "full_text_en": "", "full_text_ar": "",
    "segments":        [ { "en": "جملة/مقطع", "ar": "..." } ],
    "topic_en": "Write a daily site report", "topic_ar": "اكتب تقرير الموقع اليومي",
    "brainstorming_questions": [ { "question_en": "...", "question_ar": "...", "suggested_answer_en": "...", "suggested_answer_ar": "..." } ],
    "guided_sentences": [ { "en": "...", "ar": "..." } ],
    "final_draft": { "en": "نموذج التقرير الكامل", "ar": "ترجمته" }
  },
  "global_vocabulary": [
    { "word": "benchmark", "phonetic": "/ˈbentʃmɑːrk/", "translation": "نقطة ارتكاز مساحية",
      "example_en": "Set the benchmark before leveling.", "example_ar": "ثبّت نقطة الارتكاز قبل التسوية.",
      "mental_image": "صورة ذهنية عربية تثبّت الكلمة" }
  ],
  "lesson_notes": [ "ملاحظة ميدانية..." ],
  "quiz": [
    { "type": "mcq", "question": "معنى benchmark في سياق الطرق؟", "options": ["...", "..."], "answer": "...", "explanation_ar": "..." },
    { "type": "true_false", "question": "...", "answer": "True", "explanation_ar": "..." },
    { "type": "audio_quiz", "question": "استمع واختر المصطلح الذي سمعته", "word_to_speak": "benchmark", "options": ["benchmark","bench","mark"], "answer": "benchmark", "explanation_ar": "..." }
  ]
}
```

**أنواع الاختبار المسموحة حصراً:** `mcq` · `true_false` · `audio_quiz` (يتطلب `word_to_speak`).

## 3) توزيع المحتوى حسب طبيعة المسار

| المسار | ركّز على |
|--------|----------|
| مصطلحات مجال (مساحة/طبي/مالية…) | `global_vocabulary` غزيرة بـ`mental_image` + `key_sentences` + `audio_quiz` |
| تواصل مهني (إيميلات/تقارير/اجتماعات) | `dialogue` + `writing` كاملاً + `key_expressions` |
| وثائق وتقارير | `segments`/`full_text_en` + `topic` كتابة + `mcq` |
| مهارات شخصية (قدّم نفسك/مقابلة) | `dialogue` + `brainstorming_questions` + `guided_sentences` |

## 4) معايير الجودة (إلزامية)
1. **عربية أولاً في الشرح، إنجليزية في المحتوى** — الترجمة دائماً موجود.
2. كل كلمة: مثال جملة إنجليزية **من سياق المهنة** + `mental_image` عربية.
3. الحوارات: أسماء أدوار واقعية (Site Engineer / Surveyor / Inspector…) بأسلوب Americans حقيقي (اختصارات ميدانية مثل "Gimme the readings on BM-2").
4. 6–12 كلمة جديدة للدرس · 3–6 أسئلة اختبار · ملاحظتان على الأقل.
5. صفر محتوى أكاديمي عام لا يخدم المجال.
6. الدفعة = مصفوفة `[ {درس}, {درس}, ... ]` — نشر دفعة واحدة يكفي.

## 5) الطلب الجاهز (انسخه وأكمله للوكيل)
> اقرأ الملف المرفق ESP_COURSE_BUILDER_PROMPT.md والتزم به حرفياً.
> ألّف منهج «[اسم المجال]» لـ [من هو المتعلم] بمستوى [مبتدئ/متوسط]،
> [عدد] دروس، course_id = "[snake_case]"، level = [رقم ≥4 فريد].
> أخرج JSON فقط.

## 6) بعد التوليد — دور المالك (دقيقتان)
1. احفظ الناتج `my_track.json`.
2. التطبيق ← المزيد ← أدوات المطور 👑 ← «فتح أداة رفع الدروس» ← ارفع الملف ← راجع تعرّف التطبيق عليه.
3. «👑 نشر سحابياً لجميع الطلاب 🚀» — المسار يظهر تلقائياً لكل الأجهزة
   (بطاقة جديدة في شاشة المستويات + خريطة المنهج + الخطة اليومية).
