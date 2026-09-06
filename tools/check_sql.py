#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
=============================================================================
Zimaster — Supabase migration linter
=============================================================================
Fast, dependency-light gate for supabase/migrations/*.sql. Runs in CI on every
push and PR (see .github/workflows/quality.yml) so a broken migration is caught
before it is applied to the production database — where mistakes are expensive
and `supabase db push` has no undo.

Checks
  1. Filename convention: <14-digit UTC timestamp>_<snake_case_name>.sql —
     this is exactly what `supabase migration new` produces, and the CLI orders
     migrations by that timestamp.
  2. Timestamps are strictly increasing and unique across files.
  3. Every `create table public.<x>` in the migration set is followed by
     `alter table public.<x> enable row level security`. A public-schema table
     reachable through the Data API without RLS is the single most common
     Supabase security incident.
  4. No secret material (service_role / sb_secret_ / JWT-looking keys /
     passwords) is embedded in SQL.
  5. Full PostgreSQL grammar parse via `pglast` (libpg_query) when available.
     If pglast cannot be installed the check degrades to a warning instead of
     failing the build — CI must not break because of an optional dependency.

Usage:  python3 tools/check_sql.py            (from the repository root)
Exit:   0 = all checks passed, 1 = at least one failure.
"""

from __future__ import annotations

import os
import re
import sys

MIGRATIONS_DIR = os.path.join("supabase", "migrations")
FILENAME_RE = re.compile(r"^(\d{14})_([a-z0-9_]+)\.sql$")

failures: list[str] = []
warnings: list[str] = []


def fail(msg: str) -> None:
    failures.append(msg)
    print(f"  ❌ {msg}")


def warn(msg: str) -> None:
    warnings.append(msg)
    print(f"  ⚠️  {msg}")


def ok(msg: str) -> None:
    print(f"  ✓ {msg}")


def strip_sql_comments(sql: str) -> str:
    """Remove -- line comments and /* */ blocks so pattern checks see real SQL."""
    sql = re.sub(r"/\*.*?\*/", " ", sql, flags=re.DOTALL)
    sql = re.sub(r"--[^\n]*", " ", sql)
    return sql


def read(path: str) -> str:
    with open(path, encoding="utf-8", errors="ignore") as fh:
        return fh.read()


def check_filenames(files: list[str]) -> list[tuple[str, str]]:
    print("\n[1] Filename convention (<timestamp>_<name>.sql)")
    parsed: list[tuple[str, str]] = []
    for name in files:
        m = FILENAME_RE.match(name)
        if not m:
            fail(f"{name}: does not match <14-digit timestamp>_<snake_case>.sql "
                 f"(produce it with `supabase migration new <name>`)")
            continue
        parsed.append((m.group(1), name))
    if not failures:
        ok(f"{len(parsed)} file(s) correctly named")
    return parsed


def check_ordering(parsed: list[tuple[str, str]]) -> None:
    print("\n[2] Timestamp ordering")
    stamps = [ts for ts, _ in parsed]
    if len(set(stamps)) != len(stamps):
        fail("Duplicate migration timestamps — the CLI cannot order them deterministically")
        return
    if stamps != sorted(stamps):
        fail("Migration files are not in ascending timestamp order")
        return
    ok(f"{len(stamps)} unique, ascending timestamps ({stamps[0] if stamps else '-'} … "
       f"{stamps[-1] if stamps else '-'})")


def check_rls(all_sql: str) -> None:
    print("\n[3] Row Level Security enabled on every public table")
    code = strip_sql_comments(all_sql).lower()

    # Tables are tracked in document order: the initial migration deliberately
    # DROPs then re-CREATEs each table so it stays idempotent, so a naive
    # `created - dropped` set difference would wrongly report zero live tables.
    live: set[str] = set()
    table_events = re.finditer(
        r"(create|drop)\s+table\s+(?:if\s+(?:not\s+)?exists\s+)?public\.([a-z0-9_]+)",
        code,
    )
    for event in table_events:
        verb, table = event.group(1), event.group(2)
        live.add(table) if verb == "create" else live.discard(table)

    protected = set(re.findall(
        r"alter\s+table\s+(?:if\s+exists\s+)?(?:only\s+)?public\.([a-z0-9_]+)\s+enable\s+row\s+level\s+security",
        code,
    ))
    unprotected = sorted(live - protected)
    if unprotected:
        fail(f"Tables in `public` without RLS: {', '.join(unprotected)}")
        return
    ok(f"{len(live)} table(s) created, all RLS-protected: {', '.join(sorted(live))}")

    policies = re.findall(r"create\s+policy\s+\"?([a-z0-9_]+)\"?", code)
    if not policies:
        fail("No RLS policies at all — RLS without policies denies every client request")
    else:
        ok(f"{len(policies)} RLS policies defined")


def check_secrets(files: dict[str, str]) -> None:
    print("\n[4] No secret material in SQL")
    patterns = {
        "sb_secret_ (secret API key)": r"sb_secret_[A-Za-z0-9_\-]{10,}",
        "service_role reference": r"service_role",
        "JWT-looking token": r"eyJ[A-Za-z0-9_\-]{20,}\.[A-Za-z0-9_\-]{20,}",
        "postgres password literal": r"password\s*[:=]\s*'[^']{6,}'",
    }
    found_any = False
    for name, raw in files.items():
        code = strip_sql_comments(raw)
        for label, pattern in patterns.items():
            if re.search(pattern, code, re.IGNORECASE):
                fail(f"{name}: contains {label}")
                found_any = True
    if not found_any:
        ok("clean")


def check_parse(files: dict[str, str]) -> None:
    print("\n[5] PostgreSQL grammar parse (pglast / libpg_query)")
    try:
        import pglast  # type: ignore
    except ImportError:
        warn("pglast is not installed — skipping the grammar parse. "
             "Install with: pip install pglast")
        return
    for name, raw in sorted(files.items()):
        try:
            statements = pglast.parse_sql(raw)
        except Exception as exc:  # noqa: BLE001 - report any parser diagnostic
            fail(f"{name}: PostgreSQL parse error → {exc}")
        else:
            ok(f"{name}: {len(statements)} statements parsed")


def main() -> int:
    print("=" * 70)
    print("🔍 Zimaster — Supabase migration lint")
    print("=" * 70)

    if not os.path.isdir(MIGRATIONS_DIR):
        print(f"❌ {MIGRATIONS_DIR} does not exist")
        return 1

    names = sorted(f for f in os.listdir(MIGRATIONS_DIR) if f.endswith(".sql"))
    if not names:
        print(f"❌ no .sql migrations found in {MIGRATIONS_DIR}")
        return 1

    contents = {n: read(os.path.join(MIGRATIONS_DIR, n)) for n in names}
    if any(not c.strip() for c in contents.values()):
        fail("An empty migration file was found")

    parsed = check_filenames(names)
    check_ordering(parsed)
    check_rls("\n".join(contents.values()))
    check_secrets(contents)
    check_parse(contents)

    print("\n" + "=" * 70)
    if failures:
        print(f"❌ {len(failures)} check(s) failed, {len(warnings)} warning(s).")
        return 1
    print(f"🎉 All migration checks passed ({len(names)} file(s), {len(warnings)} warning(s)).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
