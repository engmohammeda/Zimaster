#!/usr/bin/env python3
"""Render Z-Mastery palette proposals as a precise comparison image (no AI, exact hex)."""
from PIL import Image, ImageDraw, ImageFont

F = "/usr/share/fonts/truetype/dejavu/"
def font(sz, bold=False):
    p = F + ("DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf")
    return ImageFont.truetype(p, sz)

F_TITLE = font(26, True)
F_LABEL = font(15, True)
F_HEX = font(13)
F_SMALL = font(12)

def hx(c): return "#%02X%02X%02X" % c[0:3] if isinstance(c, tuple) else c

def lerp(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

def rounded(d, box, r, fill=None, outline=None, width=1):
    d.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)

def alpha(c, a, bg):
    """fake alpha over bg"""
    return lerp(bg, c, a)

# ---------------- Palettes ----------------
CURRENT = dict(name="CURRENT (Warm Terracotta)",
    light=dict(bg=(0xF3,0xEC,0xE1), surface=(0xFF,0xFF,0xFF), sv=(0xED,0xE3,0xD5), border=(0xED,0xE4,0xD6),
        primary=(0xE0,0x78,0x56), deep=(0xCB,0x5F,0x41), secondary=(0x6B,0x90,0x80), gold=(0xE0,0xA3,0x4E),
        danger=(0xD9,0x77,0x6A), tp=(0x33,0x30,0x2C), ts=(0x6F,0x6A,0x62)),
    dark=dict(bg=(0x1A,0x16,0x13), surface=(0x24,0x1F,0x1A), sv=(0x2E,0x28,0x22), border=(0x3A,0x32,0x2A),
        primary=(0xE8,0x89,0x68), deep=(0xD4,0x6F,0x4F), secondary=(0x87,0xAA,0x9A), gold=(0xE9,0xB3,0x6A),
        danger=(0xE3,0x8C,0x80), tp=(0xF3,0xED,0xE4), ts=(0xB6,0xAB,0xA0)))

A = dict(name="A  Warm Terracotta v2",
    light=dict(bg=(0xF6,0xF0,0xE8), surface=(0xFF,0xFF,0xFF), sv=(0xEF,0xE7,0xDA), border=(0xE8,0xDE,0xCF),
        primary=(0xD9,0x6C,0x4A), deep=(0xB5,0x4F,0x33), secondary=(0x5E,0x8F,0x7C), gold=(0xD9,0x9A,0x3D),
        danger=(0xCC,0x5A,0x4E), tp=(0x33,0x30,0x2C), ts=(0x6F,0x6A,0x62)),
    dark=dict(bg=(0x17,0x13,0x10), surface=(0x21,0x1B,0x16), sv=(0x2C,0x25,0x1E), border=(0x3A,0x31,0x28),
        primary=(0xE9,0x89,0x66), deep=(0xD4,0x6F,0x4F), secondary=(0x8F,0xB3,0x9F), gold=(0xE9,0xB3,0x6A),
        danger=(0xE3,0x8C,0x80), tp=(0xF2,0xEB,0xE1), ts=(0xB4,0xA9,0x9C)))

B = dict(name="B  Dusk Indigo",
    light=dict(bg=(0xF4,0xF5,0xF9), surface=(0xFF,0xFF,0xFF), sv=(0xE9,0xEB,0xF2), border=(0xE2,0xE4,0xEC),
        primary=(0x5B,0x62,0xD6), deep=(0x41,0x48,0xB8), secondary=(0x2E,0x9E,0x8F), gold=(0xE8,0xA2,0x3D),
        danger=(0xD6,0x60,0x60), tp=(0x2A,0x2C,0x38), ts=(0x5F,0x62,0x72)),
    dark=dict(bg=(0x12,0x13,0x1C), surface=(0x1B,0x1D,0x2A), sv=(0x26,0x29,0x38), border=(0x32,0x36,0x48),
        primary=(0x8E,0x94,0xF0), deep=(0x6A,0x70,0xE0), secondary=(0x5B,0xC2,0xB0), gold=(0xE8,0xB2,0x6A),
        danger=(0xE0,0x82,0x82), tp=(0xED,0xEE,0xF4), ts=(0xA8,0xAB,0xC2)))

C = dict(name="C  Deep Focus Teal",
    light=dict(bg=(0xF3,0xF5,0xF4), surface=(0xFF,0xFF,0xFF), sv=(0xE7,0xEC,0xEA), border=(0xDD,0xE4,0xE1),
        primary=(0x1F,0x7A,0x6D), deep=(0x14,0x59,0x4F), secondary=(0xD9,0x7B,0x4F), gold=(0xD9,0x95,0x2F),
        danger=(0xC9,0x58,0x4C), tp=(0x23,0x28,0x2B), ts=(0x59,0x62,0x66)),
    dark=dict(bg=(0x10,0x15,0x14), surface=(0x18,0x20,0x19), sv=(0x21,0x2B,0x25), border=(0x2C,0x38,0x31),
        primary=(0x4F,0xB3,0xA1), deep=(0x3A,0x94,0x84), secondary=(0xE8,0x9A,0x73), gold=(0xE9,0xB3,0x6A),
        danger=(0xE3,0x8C,0x80), tp=(0xE9,0xEF,0xEB), ts=(0xA6,0xB3,0xAC)))

def mockup(d, x0, y0, w, m, mode_label):
    """draws a mini app mockup of palette dict m at x0,y0 width w; height ~300"""
    p = m
    bg, surf, sv, border = p["bg"], p["surface"], p["sv"], p["border"]
    prim, deep, sec, gold = p["primary"], p["deep"], p["secondary"], p["gold"]
    tp, ts = p["tp"], p["ts"]
    # canvas
    rounded(d, (x0, y0, x0+w, y0+292), 18, fill=bg, outline=border, width=2)
    d.text((x0+14, y0+8), mode_label, font=F_SMALL, fill=ts)
    # top pill (gradient approximated by 3 bands)
    py = y0+30
    ph = 40
    for i in range(w-28):
        t = i/(w-28)
        col = lerp(prim, deep, t)
        d.line((x0+14+i, py, x0+14+i, py+ph), fill=col)
    rounded(d, (x0+14, py, x0+w-14, py+ph), 20)
    d.text((x0+30, py+12), "Z-Mastery", font=F_LABEL, fill=(255,255,255))
    d.text((x0+w-92, py+12), "12  XP", font=F_HEX, fill=alpha((255,255,255),0.9,(0,0,0)) if False else (255,255,255))
    # card 1: lesson hero
    cy = py+ph+14
    rounded(d, (x0+14, cy, x0+w-14, cy+86), 16, fill=surf, outline=border)
    # icon tile
    rounded(d, (x0+26, cy+14, x0+62, cy+50), 10, fill=alpha(prim,0.16,surf))
    d.rectangle((x0+37, cy+22, x0+51, cy+42), fill=prim)
    d.text((x0+74, cy+16), "Lesson · Vocabulary", font=F_LABEL, fill=tp)
    d.text((x0+74, cy+38), "25 words  ·  4 stages", font=F_HEX, fill=ts)
    # progress
    rounded(d, (x0+26, cy+62, x0+w-70, cy+70), 4, fill=sv)
    rounded(d, (x0+26, cy+62, x0+26+int((w-96)*0.62), cy+70), 4, fill=sec)
    d.text((x0+w-60, cy+58), "62%", font=F_HEX, fill=sec)
    # card 2: buttons row
    by = cy+98
    rounded(d, (x0+14, by, x0+14+(w-28)//2-6, by+40), 12, fill=prim)
    d.text((x0+14+(w-28)//4-28, by+12), "Review now", font=F_LABEL, fill=(255,255,255))
    rounded(d, (x0+14+(w-28)//2+6, by, x0+w-14, by+40), 12, fill=alpha(gold,0.18,surf), outline=alpha(gold,0.5,sv))
    d.text((x0+14+(w-28)//2+16, by+12), "5 streak", font=F_LABEL, fill=gold)
    # swatches
    sy = by+56
    keys = [("bg","bg"),("surf","surface"),("sv","sv"),("prim","primary"),("deep","deep"),("sec","secondary"),("gold","gold"),("txt","tp"),("sub","ts")]
    sw = (w-28)//len(keys)
    for i,(lbl,k) in enumerate(keys):
        sx = x0+14+i*sw
        rounded(d, (sx, sy, sx+sw-6, sy+34), 8, fill=p[k], outline=border)
        d.text((sx+2, sy+38), lbl, font=F_SMALL, fill=ts)
    # hex line
    d.text((x0+16, sy+58), "prim %s  sec %s  bg %s" % (hx(prim), hx(sec), hx(bg)), font=F_HEX, fill=ts)

COLW, GAP, TOP = 430, 26, 64
W = GAP + 4*(COLW+GAP)
H = 760
img = Image.new("RGB", (W, H), (0xFA,0xFA,0xF7))
d = ImageDraw.Draw(img)
d.text((GAP+4, 18), "Z-Mastery — Palette Proposals (exact colors, light & dark)", font=F_TITLE, fill=(30,30,30))

cols = [CURRENT, A, B, C]
for i, pal in enumerate(cols):
    x = GAP + i*(COLW+GAP)
    d.text((x+4, TOP-26), pal["name"], font=F_LABEL, fill=(40,40,40))
    mockup(d, x, TOP, COLW, pal["light"], "LIGHT MODE")
    mockup(d, x, TOP+306, COLW, pal["dark"], "DARK MODE")

img.save("docs/design/palette_proposals.png")
print("saved", img.size)
