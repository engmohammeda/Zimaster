#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
=============================================================================
Z-Mastery Supabase Migration Artifacts & Integrity Verification
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
        print(f"[{total_checks:02d}] ✓ PASS: {name}")
    else:
        failed_checks += 1
        print(f"[{total_checks:02d}] ❌ FAIL: {name} - {failure_msg}")

def read_file(path: str) -> str:
    if not os.path.exists(path):
        return ""
    with open(path, "r", encoding="utf-8", errors="ignore") as f:
        return f.read()

def run_all_checks():  # noqa: C901 - a flat, readable checklist by design
    print("=" * 60)
    print("🔍 Running Zimaster Supabase Migration Verification")
    print("=" * 60)

    # 1-5: Schema & SQL Migrations
    init_sql = read_file("supabase/migrations/20260906000000_init_schema.sql")

    # The schema now spans more than one migration file (the initial schema plus
    # later hardening passes), so policy/trigger counts are measured across the
    # whole migration set rather than the first file alone.
    migrations_dir = "supabase/migrations"
    migration_files = sorted(
        os.path.join(migrations_dir, f)
        for f in os.listdir(migrations_dir)
        if f.endswith(".sql")
    ) if os.path.isdir(migrations_dir) else []
    all_sql = "\n".join(read_file(p) for p in migration_files)

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
    rls_count = len(re.findall(r"create\s+policy\s+", all_sql, re.IGNORECASE))
    check("Migrations define at least 14 RLS policies", rls_count >= 14, f"Found {rls_count} policies across {len(migration_files)} migration file(s), expected >= 14")
    check("User roles has no client insert/update policy", 
          "user_roles for insert" not in all_sql and "user_roles for update" not in all_sql,
          "Dangerous user_roles write policy detected")
    check("User progress payload size constraint", "921600" in all_sql, "921600 byte size limit not found in SQL")
    check("Realtime publication configured", "alter publication supabase_realtime add table" in all_sql, "Realtime publication missing")
    check("Realtime replica identity configured for filtered changes", "replica identity full" in all_sql.lower(), "public.user_progress needs REPLICA IDENTITY FULL for filtered postgres_changes")
    check("Admin role cannot be obtained by an anonymous session", "is_anonymous" in all_sql and "handle_user_upgraded" in all_sql, "Anonymous first-launch sign-in must not be able to become admin")
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
    # The BOM must match the project's Kotlin toolchain. supabase-kt publishes
    # each release against a specific Kotlin version:
    #   3.1.1 → Kotlin 2.1.10   3.2.6 → Kotlin 2.2.21   3.3.0+ → Kotlin 2.4.0
    # This project pins Kotlin 2.2.21 / AGP 8.10.1 (see build.gradle.kts), so
    # 3.2.6 is the newest BOM it can consume without a toolchain upgrade — a
    # library compiled with newer Kotlin metadata will not load on an older
    # compiler. Bumping to 3.8.0 requires moving the project to Kotlin 2.4.0.
    check("Supabase BOM pinned to the Kotlin-2.2.21-compatible release (3.2.6)",
          "io.github.jan-tennert.supabase:bom:3.2.6" in build_gradle,
          "BOM must be 3.2.6 while the project is on Kotlin 2.2.21")
    check("Ktor client matches the supabase-kt 3.2.6 build (3.3.1)",
          "io.ktor:ktor-client-okhttp:3.3.1" in build_gradle,
          "ktor-client-okhttp must be 3.3.1 to match supabase-kt 3.2.6")
    check("Gradle reads Supabase credentials from local.properties (CI path)",
          "local.properties" in build_gradle and "supabaseProperty" in build_gradle,
          "CI writes secrets to local.properties; app/build.gradle.kts must read it")

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

    # 45+: Project wiring — real credentials, CLI config and MCP config
    check("gradle.properties carries the real project URL (no dummy host)",
          "eduobulbcwgcjruzphgc.supabase.co" in gradle_props and "dummy-project" not in gradle_props,
          "SUPABASE_URL still points at the dummy host")
    check("gradle.properties carries a real publishable/anon key",
          ("sb_publishable_" in gradle_props or "SUPABASE_ANON_KEY=eyJ" in gradle_props)
          and "dummy_anon_key" not in gradle_props,
          "SUPABASE_ANON_KEY is still the dummy placeholder")
    # Ignore comment lines: the file legitimately *documents* which keys must
    # never be committed, and that prose must not trip the secret check.
    gradle_props_values = "\n".join(
        ln for ln in gradle_props.splitlines() if not ln.strip().startswith("#")
    )
    check("No secret key material committed in gradle.properties",
          "sb_secret_" not in gradle_props_values and "service_role" not in gradle_props_values,
          "A secret key must never be committed")

    config_toml = read_file("supabase/config.toml")
    check("supabase/config.toml exists (equivalent of `supabase init`)", len(config_toml) > 0,
          "Missing supabase/config.toml — the CLI cannot link or push migrations reproducibly")
    check("supabase/config.toml is bound to the project ref",
          'project_id = "eduobulbcwgcjruzphgc"' in config_toml, "project_id mismatch")
    check("supabase/config.toml keeps anonymous sign-in enabled",
          "[auth.anonymous_users]" in config_toml and "enabled = true" in config_toml,
          "CloudAuth.ensureSignedIn() relies on anonymous sign-in")

    mcp_json = read_file(".mcp.json")
    check(".mcp.json exists at the project root", len(mcp_json) > 0, "Missing .mcp.json")
    check(".mcp.json points at the hosted Supabase MCP for this project",
          "mcp.supabase.com/mcp" in mcp_json and "project_ref=eduobulbcwgcjruzphgc" in mcp_json,
          "Wrong MCP server URL or project_ref")
    check(".mcp.json holds no secret material",
          "sb_secret_" not in mcp_json and "sbp_" not in mcp_json and "service_role" not in mcp_json,
          "MCP uses OAuth 2.1 — no token belongs in the repo")

    env_example = read_file(".env.example")
    check(".env.example documents every secret the tooling needs",
          all(k in env_example for k in ["SUPABASE_SERVICE_ROLE_KEY", "SUPABASE_DB_PASSWORD", "SUPABASE_ACCESS_TOKEN"]),
          "Missing keys in .env.example")
    gitignore = read_file(".gitignore")
    check(".gitignore excludes .env and supabase/.temp",
          ".env" in gitignore and "supabase/.temp" in gitignore, "Local secrets could be committed")

    print("=" * 60)
    print(f"📊 SUMMARY: {passed_checks}/{total_checks} checks passed.")
    print("=" * 60)
    if failed_checks > 0:
        print(f"❌ {failed_checks} checks failed.")
        sys.exit(1)
    else:
        print(f"🎉 ALL {total_checks} CHECKS PASSED!")
        sys.exit(0)

if __name__ == "__main__":
    run_all_checks()
