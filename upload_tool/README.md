# Z-Mastery Lesson Uploader

أداة بسيطة لرفع دروس JSON إلى تطبيق Z-Mastery.

## التثبيت

```bash
pip install flask
python app.py
```

## الاستخدام

1. فتح المتصفح: http://localhost:5000
2. اختر ملفات JSON (يمكن متعددة)
3. اضغط "رفع الدروس"

## API

```bash
curl -X POST http://localhost:5000/api/upload \
  -H "Content-Type: application/json" \
  -d @lesson.json
```

## ملاحظة

- الدروس تحفظ محليًا في مجلد `uploaded_lessons/`
- في المرحلة الثانية سيتم الربط بـ Firebase Firestore
