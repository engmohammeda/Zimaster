#!/usr/bin/env python3
"""Normalize Z-Mastery UI dimensions onto the Z-Design token scales.

Safe by construction:
  * only touches explicit syntactic contexts (RoundedCornerShape / padding /
    Spacer height-width / spacedBy / PaddingValues / fractional fontSize)
  * never touches .size() / .height() element sizes / offsets
  * only maps the values listed in the tables; everything else is untouched
"""
import re, pathlib, collections

ROOT = pathlib.Path("app/src/main/java/com/zmastery/english")
FILES = [p for p in ROOT.rglob("*.kt") if "/ui/" in str(p) or p.name == "MainActivity.kt"]

# ---- corner radii: 30 ad-hoc values -> {XS=8, S=12, M=16, L=20, XL=24} ----
RADIUS = {2:4,3:4,5:4,6:8,7:8,9:8,10:12,11:12,13:12,14:16,15:16,17:16,18:16,
          19:20,21:20,22:24,23:24,25:24,26:24,28:24,30:24}

# ---- spacing: nearest 4, ties up ----
def sp(v):
    if v < 2 or v > 28 or v % 4 == 0:
        return None
    import math
    return 4 * ((v + 2) // 4)          # nearest, ties up

FONT_FIX = lambda m: f"fontSize = {int(round(float(m.group(1))))}.sp"

rx_radius  = re.compile(r"RoundedCornerShape\((\d+)\.dp\)")
rx_pad     = re.compile(r"\.padding\((\d+)\.dp\)")
rx_ph      = re.compile(r"(padding\(\s*horizontal\s*=\s*)(\d+)(\.dp)")
rx_pv      = re.compile(r"(padding\(\s*vertical\s*=\s*)(\d+)(\.dp)")
rx_ps      = re.compile(r"(padding\(\s*start\s*=\s*)(\d+)(\.dp)")
rx_pe      = re.compile(r"(padding\(\s*end\s*=\s*)(\d+)(\.dp)")
rx_pt      = re.compile(r"(padding\(\s*top\s*=\s*)(\d+)(\.dp)")
rx_pb      = re.compile(r"(padding\(\s*bottom\s*=\s*)(\d+)(\.dp)")
rx_spacer  = re.compile(r"(Spacer\(Modifier\.(?:height|width)\()(\d+)(\.dp\)\))")
rx_gap     = re.compile(r"(spacedBy\()(\d+)(\.dp\))")
rx_pc      = re.compile(r"(PaddingValues\()(\d+)(\.dp\))")
rx_font    = re.compile(r"fontSize = (\d+\.5)\.sp")

changed = collections.Counter()
def sub_count(rx, repl, text, tag, fname):
    def r(m):
        changed[f"{fname}:{tag}"] += 1
        return repl(m)
    return rx.sub(r, text)

for f in FILES:
    src = f.read_text()
    out = src

    def radius_repl(m):
        v = int(m.group(1))
        n = RADIUS.get(v)
        changed[f"{f.name}:radius"] += 1 if n and n != v else 0
        return f"RoundedCornerShape({n if n else v}.dp)"
    out = rx_radius.sub(radius_repl, out)

    def pad_repl(m):
        v = int(m.group(1)); n = sp(v)
        changed[f"{f.name}:pad"] += 1 if n and n != v else 0
        return f".padding({n if n else v}.dp)"
    out = rx_pad.sub(pad_repl, out)

    for rx, tag in ((rx_ph,"padH"),(rx_pv,"padV"),(rx_ps,"padS"),(rx_pe,"padE"),(rx_pt,"padT"),(rx_pb,"padB")):
        def mk(rx=rx, tag=tag):
            def r(m):
                v = int(m.group(2)); n = sp(v)
                if n and n != v:
                    changed[f"{f.name}:{tag}"] += 1
                    return f"{m.group(1)}{n}{m.group(3)}"
                return m.group(0)
            return r
        out = rx.sub(mk(), out)

    def spacer_repl(m):
        v = int(m.group(2)); n = sp(v)
        if n and n != v:
            changed[f"{f.name}:spacer"] += 1
            return f"{m.group(1)}{n}{m.group(3)}"
        return m.group(0)
    out = rx_spacer.sub(spacer_repl, out)

    def gap_repl(m):
        v = int(m.group(2)); n = sp(v)
        if n and n != v:
            changed[f"{f.name}:gap"] += 1
            return f"{m.group(1)}{n}{m.group(3)}"
        return m.group(0)
    out = rx_gap.sub(gap_repl, out)
    out = rx_pc.sub(gap_repl, out)

    def font_repl(m):
        changed[f"{f.name}:font"] += 1
        return FONT_FIX(m)
    out = rx_font.sub(font_repl, out)

    if out != src:
        f.write_text(out)

total = sum(changed.values())
print(f"files scanned: {len(FILES)}, replacements: {total}")
by_tag = collections.Counter()
for k, v in changed.items():
    by_tag[k.split(":")[1]] += v
print("by kind:", dict(by_tag))
print("top files:", changed.most_common(12))
