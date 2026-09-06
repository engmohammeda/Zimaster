#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
=============================================================================
Z-Mastery: Firestore to Supabase Migration Script
=============================================================================
Exports existing data from Cloud Firestore and imports it cleanly into Supabase:
1. Lessons -> public.lessons (batch upsert)
2. Users -> auth.users + public.profiles (using Supabase Admin API to preserve UUIDs)
3. User Progress -> public.user_progress (preserving full JSON state)
4. Quotes -> public.quotes
5. Announcements -> public.announcements

Requires:
  pip install firebase-admin supabase
Environment Variables:
  SUPABASE_URL: "https://<project-ref>.supabase.co"
  SUPABASE_SERVICE_ROLE_KEY: "eyJhbG..." (admin role key - NEVER check into git)
  FIREBASE_PROJECT_ID: "zmastery" (optional, default: "zmastery")
"""

import os
import sys
import json
import time

try:
    import firebase_admin
    from firebase_admin import credentials, firestore
except ImportError:
    print("❌ Please install firebase-admin: pip install firebase-admin")
    sys.exit(1)

try:
    from supabase import create_client, Client
except ImportError:
    print("❌ Please install supabase: pip install supabase")
    sys.exit(1)

FIREBASE_PROJECT_ID = os.environ.get("FIREBASE_PROJECT_ID", "zmastery")
SUPABASE_URL = os.environ.get("SUPABASE_URL", "")
SUPABASE_SERVICE_ROLE_KEY = os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "")

def init_firestore():
    service_key_path = "serviceAccountKey.json"
    if os.path.exists(service_key_path):
        cred = credentials.Certificate(service_key_path)
        firebase_admin.initialize_app(cred)
        print("✓ Firebase initialized with service account key")
    else:
        firebase_admin.initialize_app(options={"projectId": FIREBASE_PROJECT_ID})
        print("✓ Firebase initialized with Application Default Credentials (ADC)")
    return firestore.client()

def init_supabase() -> Client:
    if not SUPABASE_URL or not SUPABASE_SERVICE_ROLE_KEY:
        print("❌ Missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY environment variables.")
        print("   Usage: SUPABASE_URL=... SUPABASE_SERVICE_ROLE_KEY=... python tools/migrate_firestore_to_supabase.py")
        sys.exit(1)
    return create_client(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)

def migrate_lessons(fs_db, sb: Client, report: dict):
    print("\n📦 Migrating Lessons...")
    docs = list(fs_db.collection("lessons").stream())
    report["lessons_firestore"] = len(docs)
    print(f"Found {len(docs)} lessons in Firestore.")

    rows = []
    for doc in docs:
        d = doc.to_dict()
        doc_id = doc.id
        course_id = d.get("courseId") or d.get("course_id") or "general"
        lesson_no = int(d.get("lessonNo") or d.get("lesson_no") or 1)
        title = d.get("title") or "Lesson"
        level = str(d.get("level") or "1")
        content_json = d.get("json") or "{}"
        updated_at = int(d.get("updated_at") or time.time() * 1000)

        rows.append({
            "doc_id": doc_id,
            "course_id": course_id,
            "lesson_no": lesson_no,
            "title": title,
            "level": level,
            "json": content_json,
            "updated_at": updated_at
        })

    # Batch upsert in chunks of 100
    chunk_size = 100
    inserted = 0
    for i in range(0, len(rows), chunk_size):
        chunk = rows[i:i + chunk_size]
        res = sb.table("lessons").upsert(chunk).execute()
        inserted += len(chunk)
        print(f"  ✓ Upserted {inserted}/{len(rows)} lessons...")

    report["lessons_supabase"] = inserted

def migrate_users_and_progress(fs_db, sb: Client, report: dict):
    print("\n👤 Migrating Users, Profiles, and Progress...")
    user_docs = list(fs_db.collection("users").stream())
    report["users_firestore"] = len(user_docs)
    print(f"Found {len(user_docs)} users in Firestore.")

    users_migrated = 0
    progress_migrated = 0

    for udoc in user_docs:
        u_data = udoc.to_dict()
        uid = udoc.id
        email = u_data.get("email")
        display_name = u_data.get("displayName") or "Learner"
        photo_url = u_data.get("photoUrl")
        is_anonymous = bool(u_data.get("isAnonymous", False))
        role = u_data.get("role") or "student"

        # Create or update user in Supabase Auth via Admin API
        try:
            # Check if user already exists
            user_res = None
            try:
                user_res = sb.auth.admin.get_user_by_id(uid)
            except Exception:
                pass

            if not user_res:
                create_params = {
                    "uid": uid,
                    "email": email,
                    "email_confirm": True,
                    "user_metadata": {
                        "full_name": display_name,
                        "avatar_url": photo_url
                    }
                }
                if email:
                    create_params["password"] = "SupabaseTempPass2026!"
                sb.auth.admin.create_user(create_params)
        except Exception as e:
            print(f"  ⚠️ Warning creating auth user {uid}: {e}")

        # Upsert Profile
        profile_row = {
            "user_id": uid,
            "email": email,
            "display_name": display_name,
            "photo_url": photo_url,
            "is_anonymous": is_anonymous,
            "streak": int(u_data.get("streak") or 0),
            "xp": int(u_data.get("xp") or 0),
            "completed_lessons_count": int(u_data.get("completedLessonsCount") or 0),
            "words_learned_count": int(u_data.get("wordsLearnedCount") or 0),
            "accuracy": float(u_data.get("accuracy") or 0.0),
            "last_active_millis": int(u_data.get("lastActiveMillis") or time.time() * 1000),
            "device_model": u_data.get("deviceModel"),
            "android_version": u_data.get("androidVersion"),
            "app_version": u_data.get("appVersion"),
            "platform": u_data.get("platform") or "android",
            "role": role,
            "created_at_millis": int(u_data.get("createdAtMillis") or time.time() * 1000),
        }
        sb.table("profiles").upsert(profile_row).execute()
        users_migrated += 1

        # Migrate user progress
        try:
            pdoc = fs_db.collection("users").document(uid).collection("progress").document("state").get()
            if pdoc.exists:
                p_data = pdoc.to_dict()
                state_json = p_data.get("json")
                if state_json:
                    parsed_payload = json.loads(state_json) if isinstance(state_json, str) else state_json
                    client_ts = int(p_data.get("client_updated_at") or p_data.get("updated_at") or time.time() * 1000)
                    sb.table("user_progress").upsert({
                        "user_id": uid,
                        "payload": parsed_payload,
                        "client_ts": client_ts
                    }).execute()
                    progress_migrated += 1
        except Exception as pe:
            print(f"  ⚠️ Warning migrating progress for {uid}: {pe}")

    report["users_supabase"] = users_migrated
    report["progress_supabase"] = progress_migrated

def migrate_quotes(fs_db, sb: Client, report: dict):
    print("\n💬 Migrating Quotes...")
    docs = list(fs_db.collection("quotes").stream())
    report["quotes_firestore"] = len(docs)
    rows = []
    for doc in docs:
        d = doc.to_dict()
        rows.append({
            "id": doc.id,
            "text": d.get("text") or "",
            "author": d.get("author") or "",
            "is_active": bool(d.get("active", True)),
            "created_at_millis": int(d.get("createdAtMillis") or time.time() * 1000),
        })
    if rows:
        sb.table("quotes").upsert(rows).execute()
    report["quotes_supabase"] = len(rows)

def migrate_announcements(fs_db, sb: Client, report: dict):
    print("\n📢 Migrating Announcements...")
    docs = list(fs_db.collection("announcements").stream())
    report["announcements_firestore"] = len(docs)
    rows = []
    for doc in docs:
        d = doc.to_dict()
        rows.append({
            "id": doc.id,
            "title": d.get("title") or "Announcement",
            "message": d.get("message") or "",
            "type": d.get("type") or "info",
            "created_at_millis": int(d.get("createdAtMillis") or time.time() * 1000),
            "is_active": bool(d.get("isActive", True)),
        })
    if rows:
        sb.table("announcements").upsert(rows).execute()
    report["announcements_supabase"] = len(rows)

def print_migration_report(report: dict):
    print("\n" + "=" * 60)
    print("📋 MIGRATION VERIFICATION REPORT")
    print("=" * 60)
    print(f"Lessons:        Firestore: {report.get('lessons_firestore', 0):>5}  -->  Supabase: {report.get('lessons_supabase', 0):>5}")
    print(f"Users:          Firestore: {report.get('users_firestore', 0):>5}  -->  Supabase: {report.get('users_supabase', 0):>5}")
    print(f"Progress:       Firestore: {report.get('progress_firestore', 'N/A'):>5}  -->  Supabase: {report.get('progress_supabase', 0):>5}")
    print(f"Quotes:         Firestore: {report.get('quotes_firestore', 0):>5}  -->  Supabase: {report.get('quotes_supabase', 0):>5}")
    print(f"Announcements:  Firestore: {report.get('announcements_firestore', 0):>5}  -->  Supabase: {report.get('announcements_supabase', 0):>5}")
    print("=" * 60)
    print("✓ Migration execution finished.")

def main():
    report = {}
    fs_db = init_firestore()
    sb = init_supabase()

    migrate_lessons(fs_db, sb, report)
    migrate_users_and_progress(fs_db, sb, report)
    migrate_quotes(fs_db, sb, report)
    migrate_announcements(fs_db, sb, report)

    print_migration_report(report)

if __name__ == "__main__":
    main()
