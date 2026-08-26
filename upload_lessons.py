#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
=============================================================================
Z-Mastery Bulk Lessons Cloud Uploader
=============================================================================
هذا السكربت يقوم برفع وقراءة كافة مجلدات الدروس (القراءة، القواعد، الصوتيات،
الكتابة، المحادثة، من الصفر) ورفعها تلقائياً وبشكل دفعي إلى Firebase Firestore.
"""

import os
import sys
import json
import glob
import time

try:
    import firebase_admin
    from firebase_admin import credentials, firestore
except ImportError:
    print("❌ الرجاء تثبيت مكتبة firebase-admin أولاً عبر الأمر:")
    print("   pip install firebase-admin")
    sys.exit(1)

# مسار ملف مفتاح حساب الخدمة من Firebase Console
# (Project Settings -> Service Accounts -> Generate new private key)
SERVICE_ACCOUNT_KEY_PATH = "serviceAccountKey.json"

# المجلد الأساسي الذي يحوي مجلدات الدروس
LESSONS_ROOT_DIR = "./الدروس"  # أو ضع المسار لمجلد الدروس لديك

def init_firebase():
    if not os.path.exists(SERVICE_ACCOUNT_KEY_PATH):
        print(f"❌ لم يتم العثور على ملف المفتاح: {SERVICE_ACCOUNT_KEY_PATH}")
        print("💡 قم بتنزيله من Firebase Console > Project Settings > Service Accounts")
        sys.exit(1)
    
    cred = credentials.Certificate(SERVICE_ACCOUNT_KEY_PATH)
    firebase_admin.initialize_app(cred)
    return firestore.client()

def normalize_course_id(raw_id, folder_name=""):
    raw = (raw_id or "").strip().lower()
    folder = folder_name.strip().lower()
    
    if "read" in raw or "قراء" in raw or "قراء" in folder or "reading" in folder:
        return "reading_l1"
    if "gram" in raw or "قواعد" in raw or "قواعد" in folder or "grammar" in folder:
        return "grammar_l1"
    if "phon" in raw or "صوت" in raw or "صوتيات" in folder or "phonetics" in folder:
        return "phonetics"
    if "writ" in raw or "كتاب" in raw or "كتابة" in folder or "writing" in folder:
        return "writing_l1"
    if "conv" in raw or "محادث" in raw or "محادثة" in folder or "conversation" in folder:
        return "conversation_l1"
    if "zero" in raw or "صفر" in raw or "من الصفر" in folder:
        return "zero_to_hero"
    
    return raw if raw else "general"

def extract_lessons_from_file(file_path):
    """استخراج جميع الدروس من ملف JSON سواء كان درساً مفرداً أو مصفوفة أو كائناً يحتوي قائمة lessons"""
    with open(file_path, "r", encoding="utf-8") as f:
        try:
            data = json.load(f)
        except Exception as e:
            print(f"⚠️ خطأ في قراءة الملف {file_path}: {e}")
            return []

    lessons = []
    folder_name = os.path.basename(os.path.dirname(file_path))

    if isinstance(data, list):
        for item in data:
            if isinstance(item, dict) and "metadata" in item:
                lessons.append(item)
    elif isinstance(data, dict):
        if "lessons" in data and isinstance(data["lessons"], list):
            for item in data["lessons"]:
                if isinstance(item, dict):
                    # لو كان يحوي metadata
                    if "metadata" not in item and "lesson_no" in item:
                        item = {
                            "metadata": {
                                "course_id": data.get("course_id", folder_name),
                                "course_name_ar": data.get("course_name_ar", folder_name),
                                "level": data.get("level", 1),
                                "lesson_no": item.get("lesson_no", 1),
                                "title": item.get("title", item.get("lesson_title", ""))
                            },
                            "lesson_content": item.get("lesson_content", {}),
                            "global_vocabulary": item.get("global_vocabulary", item.get("words", [])),
                            "lesson_notes": item.get("lesson_notes", item.get("notes", [])),
                            "quiz": item.get("quiz", [])
                        }
                    lessons.append(item)
        elif "metadata" in data:
            lessons.append(data)
    
    return lessons

def upload_all_lessons(db, root_dir):
    json_files = glob.glob(os.path.join(root_dir, "**/*.json"), recursive=True)
    print(f"🔍 تم العثور على {len(json_files)} ملف JSON في المجلد...")

    all_lessons = []
    for fpath in json_files:
        items = extract_lessons_from_file(fpath)
        all_lessons.extend(items)

    print(f"📦 إجمالي الدروس المستخرجة: {len(all_lessons)} درس.")
    if not all_lessons:
        print("⚠️ لا توجد دروس لرفعها.")
        return

    batch = db.batch()
    count = 0
    uploaded_total = 0

    for lesson in all_lessons:
        meta = lesson.get("metadata", {})
        raw_course_id = meta.get("course_id", "")
        course_name_ar = meta.get("course_name_ar", "")
        lesson_no = meta.get("lesson_no", 1)
        title = meta.get("title", "بدون عنوان")
        level = meta.get("level", 1)

        course_id = normalize_course_id(raw_course_id, course_name_ar)
        doc_id = f"{course_id}_lesson_{lesson_no}"

        doc_ref = db.collection("lessons").document(doc_id)
        
        # حفظ كود JSON الكامل للدرس
        lesson_json_str = json.dumps(lesson, ensure_ascii=False)
        
        now_millis = int(time.time() * 1000)
        doc_data = {
            "docId": doc_id,
            "courseId": course_id,
            "lessonNo": lesson_no,
            "title": title,
            "level": level,
            "json": lesson_json_str,
            "updated_at": now_millis,
            "client_updated_at": now_millis,
            "updatedAtServer": firestore.SERVER_TIMESTAMP,
        }

        batch.set(doc_ref, doc_data, merge=True)
        count += 1
        uploaded_total += 1

        if count >= 400:
            batch.commit()
            print(f"  ✓ تم رفع {uploaded_total} درس...")
            batch = db.batch()
            count = 0

    if count > 0:
        batch.commit()
        print(f"  ✓ تم رفع {uploaded_total} درس...")

    print(f"\n🎉 اكتمل رفع جميع الدروس بنجاح إلى Firebase Firestore! (الإجمالي: {uploaded_total})")

if __name__ == "__main__":
    db = init_firebase()
    upload_all_lessons(db, LESSONS_ROOT_DIR)
