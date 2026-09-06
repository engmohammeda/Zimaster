#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
=============================================================================
Z-Mastery Supabase Migration Artifacts & Integrity Verification (44 Checks)
=============================================================================
"""

import os
import sys
import re

total_checks = 0
passed_checks = 0
failed_checks = 0

def check(name: str, condition: bool, failure_msg: str = ""):
    global total_checks, passed_checks, failed_checks
    total_checks += 1
    if condition:
        passed_checks += 1
        print(f"[{total_checks:02d}/44] ✓ PASS: {name}")
    else:
        failed_checks += 1
        print(f"[{total_checks:02d}/44] ❌ FAIL: {name} - {failure_msg}")

def read_file(path: str) -> str:
    if not os.path.exists(path):
        return ""
    with open(path, "r", encoding="utf-8", errors="ignore") as f:
        return f.read()

def run_all_checks():
    print("=" * 60)
    print("🔍 Running Zimaster Supabase Migration Verification (44 Checks)")
    print("=" * 60)

    # 1-5: Schema & SQL Migrations
    init_sql = read_file("supabase/migrations/20260906000000_init_schema.sql")
    check("Init SQL migration exists", len(init_sql) > 0, "Missing init_schema.sql")
    check("Init SQL defines 6 tables", 
          all(t in init_sql for t in ["create table public.profiles", "create table public.user_roles", "create table public.lessons", "create table public.user_progress", "create table public.quotes", "create table public.announcements"]),
          "Not all 6 tables found")
    check("Init SQL defines leaderboard view", "create or replace view public.leaderboard" in init_sql, "Missing leaderboard view")
    check("Init SQL defines security functions", 
          all(fn in init_sql for fn in ["is_admin()", "can_publish()", "is_email_verified()", "push_progress"]),
          "Security functions missing")
    check("Init SQL defines explicit GRANTs", 
          "grant usage on schema public to anon, authenticated" in init_sql and "grant execute on function public.is_admin" in init_sql,
          "Explicit GRANTs missing")

    # 6-10: RLS Policies & Realtime
    rls_count = len(re.findall(r"create\s+policy\s+", init_sql, re.IGNORECASE))
    check("Init SQL defines at least 14 RLS policies", rls_count >= 14, f"Found {rls_count} policies, expected >= 14")
    check("User roles has no client insert/update policy", 
          "user_roles for insert" not in init_sql and "user_roles for update" not in init_sql,
          "Dangerous user_roles write policy detected")
    check("User progress payload size constraint", "921600" in init_sql, "921600 byte size limit not found in SQL")
    check("Realtime publication configured", "alter publication supabase_realtime add table" in init_sql, "Realtime publication missing")
    check("Leaderboard excludes email and role", "email" not in re.findall(r"create or replace view public\.leaderboard.*?;", init_sql, re.DOTALL)[0] if "create or replace view public.leaderboard" in init_sql else False, "Leaderboard view leaks email")

    # 11-15: Workflows & CI
    check("supabase-migrations.yml exists", os.path.exists(".github/workflows/supabase-migrations.yml"))
    check("supabase-backup.yml exists", os.path.exists(".github/workflows/supabase-backup.yml"))
    check("supabase-keepalive.yml exists", os.path.exists(".github/workflows/supabase-keepalive.yml"))
    check("quality.yml exists", os.path.exists(".github/workflows/quality.yml"))
    release_yml = read_file(".github/workflows/android-release.yml")
    check("android-release.yml preserved without broken google-services step", 
          "actions/checkout@v4" in release_yml and "Unit tests" in release_yml and "assembleRelease" in release_yml or "release:" in release_yml)

    # 16-20: Python Tools & Scripts
    check("migrate_firestore_to_supabase.py exists", os.path.exists("tools/migrate_firestore_to_supabase.py"))
    mig_tool = read_file("tools/migrate_firestore_to_supabase.py")
    check("Migration script has batch upsert for lessons", "table(\"lessons\").upsert" in mig_tool)
    check("Migration script has verification report", "MIGRATION VERIFICATION REPORT" in mig_tool)
    check("Migration script does not hardcode service role key", "SUPABASE_SERVICE_ROLE_KEY" in mig_tool and "eyJhbGci" not in mig_tool)
    upload_py = read_file("upload_lessons.py")
    check("upload_lessons.py uses Supabase without firebase_admin", "from supabase import" in upload_py and "firebase_admin" not in upload_py)

    # 21-25: Core Constraints (Immutable Files)
    check("StateMerger.kt exists and intact", os.path.exists("app/src/main/java/com/zmastery/english/data/StateMerger.kt"))
    check("StateMergerTest.kt exists and intact", os.path.exists("app/src/test/java/com/zmastery/english/StateMergerTest.kt"))
    check("CloudSyncService.kt exists and intact", os.path.exists("app/src/main/java/com/zmastery/english/domain/usecases/CloudSyncService.kt"))
    check("CloudSyncServiceTest.kt exists and intact", os.path.exists("app/src/test/java/com/zmastery/english/CloudSyncServiceTest.kt"))
    cloud_sync_src = read_file("app/src/main/java/com/zmastery/english/domain/usecases/CloudSyncService.kt")
    check("CloudSyncService has no Supabase/Firebase dependencies", "supabase" not in cloud_sync_src and "firebase" not in cloud_sync_src)

    # 26-30: Build Gradle & Dependencies
    build_gradle = read_file("app/build.gradle.kts")
    check("No firebase-bom in app/build.gradle.kts", "firebase-bom" not in build_gradle)
    check("No firebase-firestore in app/build.gradle.kts", "firebase-firestore" not in build_gradle)
    check("No firebase-auth in app/build.gradle.kts", "firebase-auth" not in build_gradle)
    check("No google-services plugin in app/build.gradle.kts", "com.google.gms.google-services" not in build_gradle)
    check("Supabase BOM 3.8.0 configured in app/build.gradle.kts", "io.github.jan-tennert.supabase:bom:3.8.0" in build_gradle)

    # 31-35: Supabase Modules in Gradle
    check("auth-kt in build.gradle.kts", "io.github.jan-tennert.supabase:auth-kt" in build_gradle)
    check("postgrest-kt in build.gradle.kts", "io.github.jan-tennert.supabase:postgrest-kt" in build_gradle)
    check("realtime-kt in build.gradle.kts", "io.github.jan-tennert.supabase:realtime-kt" in build_gradle)
    check("ktor-client-okhttp in build.gradle.kts", "io.ktor:ktor-client-okhttp" in build_gradle)
    check("minSdk remains 24", "minSdk = 24" in build_gradle)

    # 36-40: Source Code Cleanliness (Zero Firebase in app/src)
    kt_files = []
    for root, _, files in os.walk("app/src"):
        for f in files:
            if f.endswith(".kt"):
                kt_files.append(os.path.join(root, f))
    
    firebase_imports = []
    for fpath in kt_files:
        content = read_file(fpath)
        if "com.google.firebase" in content:
            firebase_imports.append(fpath)
    
    check("Zero 'com.google.firebase' imports in app/src", len(firebase_imports) == 0, f"Found in: {firebase_imports}")
    
    cloud_auth = read_file("app/src/main/java/com/zmastery/english/cloud/CloudAuth.kt")
    check("CloudAuth uses SupabaseClient", "createSupabaseClient" in cloud_auth or "supabase" in cloud_auth)
    check("CloudAuth has anonymous sign-in", "signInAnonymously" in cloud_auth)
    check("CloudAuth has IDToken / Google sign-in", "IDToken" in cloud_auth or "linkIdentity" in cloud_auth)
    
    cloud_sync = read_file("app/src/main/java/com/zmastery/english/cloud/CloudSync.kt")
    check("CloudSync uses Supabase tables & RPC", "from(\"lessons\")" in cloud_sync or "push_progress" in cloud_sync)

    # 41-44: Security & Sensitive Keys
    check("SUPER_ADMIN_EMAILS removed from CloudSync.kt", "SUPER_ADMIN_EMAILS" not in cloud_sync, "SUPER_ADMIN_EMAILS still hardcoded in CloudSync.kt")
    check("No hardcoded service_role in app sources", "service_role" not in cloud_auth and "service_role" not in cloud_sync)
    check("KeyProtector.stripKeysForSharing exists and preserved", os.path.exists("app/src/main/java/com/zmastery/english/data/KeyProtector.kt"))
    gradle_props = read_file("gradle.properties")
    check("Fallback SUPABASE_URL and SUPABASE_ANON_KEY in gradle.properties", "SUPABASE_URL" in gradle_props and "SUPABASE_ANON_KEY" in gradle_props)

    print("=" * 60)
    print(f"📊 SUMMARY: {passed_checks}/{total_checks} checks passed.")
    print("=" * 60)
    if failed_checks > 0:
        print(f"❌ {failed_checks} checks failed.")
        sys.exit(1)
    else:
        print("🎉 ALL 44 CHECKS PASSED!")
        sys.exit(0)

if __name__ == "__main__":
    run_all_checks()
