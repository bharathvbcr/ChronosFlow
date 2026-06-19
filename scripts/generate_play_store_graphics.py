# -*- coding: utf-8 -*-
"""
ChronosFlow Play Store Graphics Generator
Outputs: app icon, feature graphic, 6 phone screenshots, tablet screenshots
"""

import sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

from PIL import Image, ImageDraw, ImageFont, ImageFilter
import os
import math

# ── Color palette ──────────────────────────────────────────────────────────────
PRIMARY       = (114, 87, 184)   # #7257B8
PRIMARY_LT    = (185, 165, 255)  # #B9A5FF
SECONDARY     = (78, 154, 154)   # #4E9A9A
SECONDARY_LT  = (117, 214, 210)  # #75D6D2
BG_DARK       = (15, 17, 26)     # #0F111A
BG_PANEL      = (26, 29, 41)     # #1A1D29
BG_CARD       = (34, 38, 56)     # lighter card
GRAD_TOP      = (27, 37, 54)     # #1B2536
CORAL         = (224, 87, 62)    # #E0573E
WHITE         = (255, 255, 255)
TEXT_MUTED    = (140, 140, 165)
GOLD          = (255, 196, 87)

OUT = r"C:\Users\bhara\Downloads\Code\ChronosFlow\play_store_graphics"
os.makedirs(OUT, exist_ok=True)

# ── Font helpers ───────────────────────────────────────────────────────────────
FONT_PATHS = [
    r"C:\Windows\Fonts\segoeui.ttf",
    r"C:\Windows\Fonts\segoeuib.ttf",
    r"C:\Windows\Fonts\arial.ttf",
    r"C:\Windows\Fonts\arialbd.ttf",
]

def font(size, bold=False):
    candidates = (
        [r"C:\Windows\Fonts\segoeuib.ttf", r"C:\Windows\Fonts\arialbd.ttf"]
        if bold else
        [r"C:\Windows\Fonts\segoeui.ttf", r"C:\Windows\Fonts\arial.ttf"]
    )
    for p in candidates:
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


# ── Gradient helpers ───────────────────────────────────────────────────────────
def v_gradient(img, top_color, bot_color):
    draw = ImageDraw.Draw(img)
    w, h = img.size
    for y in range(h):
        t = y / h
        r = int(top_color[0] + (bot_color[0] - top_color[0]) * t)
        g = int(top_color[1] + (bot_color[1] - top_color[1]) * t)
        b = int(top_color[2] + (bot_color[2] - top_color[2]) * t)
        draw.line([(0, y), (w, y)], fill=(r, g, b))


def radial_glow(img, cx, cy, radius, color, alpha_peak=80):
    overlay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)
    steps = 24
    for i in range(steps, 0, -1):
        r = int(radius * i / steps)
        a = int(alpha_peak * (1 - i / steps))
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(*color, a))
    img.paste(overlay, mask=overlay)


def rounded_rect(draw, xy, radius, fill, stroke=None, stroke_width=2):
    x0, y0, x1, y1 = xy
    draw.rounded_rectangle([x0, y0, x1, y1], radius=radius, fill=fill,
                            outline=stroke, width=stroke_width)


def pill(draw, cx, cy, w, h, fill, text=None, text_fill=WHITE, f=None):
    x0, y0 = cx - w // 2, cy - h // 2
    draw.rounded_rectangle([x0, y0, x0 + w, y0 + h], radius=h // 2, fill=fill)
    if text and f:
        bbox = draw.textbbox((0, 0), text, font=f)
        tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
        draw.text((cx - tw // 2, cy - th // 2), text, font=f, fill=text_fill)


# ── Draw a minimalist phone-frame outline ─────────────────────────────────────
def phone_frame(draw, x, y, w, h, scale=1):
    bw = max(2, int(3 * scale))
    r = int(24 * scale)
    draw.rounded_rectangle([x, y, x + w, y + h], radius=r,
                            outline=(*PRIMARY_LT, 180), width=bw)
    # home-bar
    bw2 = max(2, int(3 * scale))
    bx = x + w // 2 - int(30 * scale)
    by = y + h - int(18 * scale)
    draw.rounded_rectangle([bx, by, bx + int(60 * scale), by + bw2 * 2],
                            radius=4, fill=(*WHITE, 80))


# ══════════════════════════════════════════════════════════════════════════════
# 1. APP ICON  512 × 512
# ══════════════════════════════════════════════════════════════════════════════
def make_icon():
    SIZE = 512
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Rounded-square background gradient
    mask = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, SIZE, SIZE], radius=115, fill=255)

    bg = Image.new("RGB", (SIZE, SIZE))
    v_gradient(bg, GRAD_TOP, BG_DARK)
    img.paste(bg, mask=mask)

    # Subtle radial glow at top-right
    radial_glow(img, cx=380, cy=130, radius=220, color=PRIMARY, alpha_peak=60)
    radial_glow(img, cx=130, cy=380, radius=180, color=SECONDARY, alpha_peak=40)

    draw = ImageDraw.Draw(img)

    # Outer clock ring
    cx, cy, R = 256, 256, 185
    draw.ellipse([cx - R, cy - R, cx + R, cy + R],
                 outline=(*PRIMARY_LT, 200), width=10)

    # Tick marks
    for i in range(12):
        angle = math.radians(i * 30 - 90)
        inner = 168 if i % 3 == 0 else 174
        outer = 185
        draw.line([
            (cx + inner * math.cos(angle), cy + inner * math.sin(angle)),
            (cx + outer * math.cos(angle), cy + outer * math.sin(angle)),
        ], fill=(*PRIMARY_LT, 180), width=4 if i % 3 == 0 else 2)

    # Hands  (10:10 pose)
    def hand(angle_deg, length, width, color):
        a = math.radians(angle_deg - 90)
        draw.line([cx, cy, cx + length * math.cos(a), cy + length * math.sin(a)],
                  fill=color, width=width, joint="curve")

    hand(60,  110, 12, (*PRIMARY_LT, 230))   # hour hand
    hand(180, 140, 8,  (*SECONDARY_LT, 230)) # minute hand

    # Center cap
    draw.ellipse([cx - 12, cy - 12, cx + 12, cy + 12], fill=WHITE)

    # "CF" monogram inside
    # small flowing arc below center
    arc_bbox = [cx - 55, cy + 20, cx + 55, cy + 120]
    draw.arc(arc_bbox, start=200, end=340, fill=(*SECONDARY_LT, 180), width=7)

    # Save with rounded mask applied
    result = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    result.paste(img, mask=mask)
    result.save(os.path.join(OUT, "icon_512.png"))
    print("[OK] icon_512.png")


# ══════════════════════════════════════════════════════════════════════════════
# 2. FEATURE GRAPHIC  1024 × 500
# ══════════════════════════════════════════════════════════════════════════════
def make_feature_graphic():
    W, H = 1024, 500
    img = Image.new("RGB", (W, H))
    v_gradient(img, GRAD_TOP, BG_DARK)

    radial_glow(img, 760, 250, 400, PRIMARY, 55)
    radial_glow(img, 200, 400, 300, SECONDARY, 35)

    draw = ImageDraw.Draw(img)

    # Decorative ring (left side, partially clipped)
    r = 260
    draw.ellipse([-80, H // 2 - r, -80 + 2 * r, H // 2 + r],
                 outline=(*PRIMARY_LT, 50), width=2)
    draw.ellipse([-50, H // 2 - r + 30, -50 + 2 * r - 60, H // 2 + r - 30],
                 outline=(*SECONDARY_LT, 40), width=1)

    # Title
    f_title = font(88, bold=True)
    f_sub   = font(34)
    f_tag   = font(26)

    # App name: "Chronos" (light) + "Flow" (violet bold)
    draw.text((80, 140), "Chronos", font=f_title, fill=WHITE)
    bbox = draw.textbbox((80, 140), "Chronos", font=f_title)
    x_flow = bbox[2] + 8
    draw.text((x_flow, 140), "Flow", font=f_title, fill=PRIMARY_LT)

    # Tagline
    draw.text((82, 250), "Your day, beautifully planned.", font=f_sub, fill=(*WHITE, 200))

    # Three feature chips
    chips = [
        ("⏱  Focus Blocks", SECONDARY),
        ("✦  AI Planning",   PRIMARY),
        ("📓  Journal",       (90, 60, 140)),
    ]
    chip_x = 82
    for label, color in chips:
        f_chip = font(22)
        bbox = draw.textbbox((0, 0), label, font=f_chip)
        cw = bbox[2] - bbox[0] + 36
        draw.rounded_rectangle([chip_x, 320, chip_x + cw, 360],
                                radius=18, fill=(*color, 80))
        draw.rounded_rectangle([chip_x, 320, chip_x + cw, 360],
                                radius=18, outline=(*color, 160), width=1)
        draw.text((chip_x + 18, 328), label, font=f_chip, fill=WHITE)
        chip_x += cw + 16

    # Mini phone silhouette on right side
    px, py, pw, ph = 680, 40, 240, 420
    phone_bg = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    pd = ImageDraw.Draw(phone_bg)
    pd.rounded_rectangle([px, py, px + pw, py + ph], radius=28,
                          fill=(*BG_PANEL, 220), outline=(*PRIMARY_LT, 100), width=2)

    # Inside the phone: mini UI blocks
    bx = px + 14
    by_start = py + 60
    colors_blocks = [PRIMARY, SECONDARY, (130, 100, 200), CORAL, SECONDARY, PRIMARY_LT]
    labels_blocks = ["Morning run", "Deep work", "Lunch", "Review", "Reading", "Wind down"]
    for i, (bc, bl) in enumerate(zip(colors_blocks, labels_blocks)):
        bh = 44 if i == 1 else 36
        if i == 1:
            pd.rounded_rectangle([bx, by_start, bx + pw - 28, by_start + bh],
                                  radius=10, fill=(*bc, 200))
        else:
            pd.rounded_rectangle([bx, by_start, bx + pw - 28, by_start + bh],
                                  radius=8, fill=(*bc, 100))
        f_bl = font(14 if i == 1 else 12, bold=(i == 1))
        pd.text((bx + 12, by_start + bh // 2 - 8), bl, font=f_bl, fill=WHITE)
        by_start += bh + 8

    # Composite the phone panel onto the base image
    base_rgba = img.convert("RGBA")
    base_rgba.paste(phone_bg, mask=phone_bg.split()[3])
    img_final = base_rgba.convert("RGB")

    draw2 = ImageDraw.Draw(img_final)
    draw2.ellipse([-80, H // 2 - 260, -80 + 520, H // 2 + 260],
                  outline=(*PRIMARY_LT, 50), width=2)

    # phone panel
    draw2.rounded_rectangle([px, py, px + pw, py + ph], radius=28,
                             fill=BG_PANEL, outline=(*PRIMARY_LT, 120), width=2)
    # status bar dots
    draw2.ellipse([px + 14, py + 14, px + 22, py + 22], fill=(*SECONDARY_LT, 160))
    # mini blocks inside phone
    by_start = py + 48
    for i, (bc, bl) in enumerate(zip(colors_blocks, labels_blocks)):
        bh = 46 if i == 1 else 36
        draw2.rounded_rectangle([px + 12, by_start, px + pw - 12, by_start + bh],
                                 radius=9, fill=(*bc, 180 if i == 1 else 100))
        f_bl2 = font(13 if i == 1 else 11, bold=(i == 1))
        draw2.text((px + 22, by_start + bh // 2 - 7), bl, font=f_bl2, fill=WHITE)
        by_start += bh + 8

    # title
    draw2.text((80, 130), "Chronos", font=font(88, bold=True), fill=WHITE)
    bbox = draw2.textbbox((80, 130), "Chronos", font=font(88, bold=True))
    draw2.text((bbox[2] + 8, 130), "Flow", font=font(88, bold=True), fill=PRIMARY_LT)
    draw2.text((82, 248), "Your day, beautifully planned.", font=font(34), fill=(*WHITE, 200))

    chip_x2 = 82
    for label, color in chips:
        f_chip2 = font(22)
        bbox2 = draw2.textbbox((0, 0), label, font=f_chip2)
        cw2 = bbox2[2] - bbox2[0] + 36
        draw2.rounded_rectangle([chip_x2, 316, chip_x2 + cw2, 358],
                                 radius=18, fill=(*color, 80))
        draw2.rounded_rectangle([chip_x2, 316, chip_x2 + cw2, 358],
                                 radius=18, outline=(*color, 160), width=1)
        draw2.text((chip_x2 + 18, 326), label, font=f_chip2, fill=WHITE)
        chip_x2 += cw2 + 16

    # bottom tagline
    draw2.text((82, 420), "Blocks · Focus · Journal · AI  —  ChronosFlow",
               font=font(20), fill=(*TEXT_MUTED, 200))

    img_final.save(os.path.join(OUT, "feature_graphic_1024x500.png"))
    print("[OK] feature_graphic_1024x500.png")


# ══════════════════════════════════════════════════════════════════════════════
# 3. PHONE SCREENSHOTS  1080 × 1920
# ══════════════════════════════════════════════════════════════════════════════
def make_screenshot(filename, title, subtitle, draw_content_fn):
    W, H = 1080, 1920
    img = Image.new("RGB", (W, H))
    v_gradient(img, GRAD_TOP, BG_DARK)
    radial_glow(img, W // 2, 300, 500, PRIMARY, 45)

    draw = ImageDraw.Draw(img)

    # Status bar
    draw.text((60, 60), "9:41", font=font(36, bold=True), fill=WHITE)
    for i, x in enumerate([980, 1010, 1040]):
        draw.rectangle([x, 74, x + 12, 84 - i * 3], fill=WHITE)

    # Big headline at top
    draw.text((60, 130), title, font=font(72, bold=True), fill=WHITE)
    draw.text((60, 218), subtitle, font=font(38), fill=(*TEXT_MUTED, 220))

    # Feature area
    draw_content_fn(draw, img, W, H)

    # Bottom nav bar
    nav_y = H - 120
    draw.rectangle([0, nav_y, W, H], fill=BG_PANEL)
    nav_items = [("Plan", 160), ("Today", 380), ("Focus", 540), ("Review", 700), ("More", 920)]
    for label, nx in nav_items:
        active = label == title.split()[0]
        col = PRIMARY_LT if active else TEXT_MUTED
        draw.text((nx, nav_y + 30), label, font=font(30, bold=active), fill=col)
        if active:
            draw.rounded_rectangle([nx - 10, nav_y + 8, nx + 60, nav_y + 14],
                                    radius=3, fill=PRIMARY_LT)

    img.save(os.path.join(OUT, filename))
    print(f"[OK] {filename}")


# ─── Content draw functions ───────────────────────────────────────────────────

def draw_plan(draw, img, W, H):
    """Day-plan timeline with colour blocks"""
    radial_glow(img, W // 2, H // 2, 600, SECONDARY, 30)
    draw = ImageDraw.Draw(img)

    # Date pill
    pill(draw, W // 2, 310, 320, 60, (*PRIMARY, 140),
         text="Thursday, Jun 18", text_fill=WHITE, f=font(28))

    # Timeline blocks
    blocks = [
        ("6:00", "Morning Run",          SECONDARY,  80),
        ("7:30", "Deep Work — Sprint",   PRIMARY,    110),
        ("9:30", "Team Standup",         (130,100,200), 60),
        ("10:00","Deep Work — Sprint",   PRIMARY,    130),
        ("12:00","Lunch + Walk",         CORAL,      70),
        ("13:00","Design Review",        (90,140,80), 60),
        ("14:00","1:1 with Manager",     (130,100,200), 60),
        ("15:30","Reading",              SECONDARY,  90),
        ("17:00","Wind Down",            (100,80,160), 70),
    ]
    by = 380
    tl_x = 160
    for time_str, label, color, height in blocks:
        draw.text((60, by + height // 2 - 14), time_str, font=font(26), fill=TEXT_MUTED)
        draw.rounded_rectangle([tl_x, by, W - 60, by + height],
                                radius=16, fill=(*color, 180))
        draw.text((tl_x + 24, by + height // 2 - 14), label,
                  font=font(32, bold=True), fill=WHITE)
        by += height + 14
        if by > H - 250:
            break


def draw_focus(draw, img, W, H):
    """Focus mode — big ring timer"""
    radial_glow(img, W // 2, 900, 500, PRIMARY, 70)
    draw = ImageDraw.Draw(img)

    cx, cy, R = W // 2, 900, 320
    # Background ring
    draw.ellipse([cx - R, cy - R, cx + R, cy + R],
                 outline=(*BG_CARD, 255), width=28)
    # Progress arc (72 % done)
    draw.arc([cx - R, cy - R, cx + R, cy + R],
             start=-90, end=-90 + int(360 * 0.72),
             fill=PRIMARY_LT, width=28)
    # Inner timer text
    draw.text((cx - 90, cy - 60), "18:32", font=font(96, bold=True), fill=WHITE)
    draw.text((cx - 80, cy + 50), "remaining", font=font(34), fill=TEXT_MUTED)

    # Phase chips
    pill(draw, cx - 140, cy + 160, 200, 56, (*PRIMARY, 160),
         text="Work phase", text_fill=WHITE, f=font(28))
    pill(draw, cx + 100, cy + 160, 180, 56, (*BG_CARD, 220),
         text="3 of 4", text_fill=TEXT_MUTED, f=font(28))

    # Block name
    draw.text((cx - 180, cy - 220), "Deep Work — Sprint",
              font=font(42, bold=True), fill=WHITE)

    # Controls
    for i, (label, col) in enumerate([("⏸ Pause", BG_PANEL), ("⏭ Skip", PRIMARY)]):
        bx = W // 2 - 280 + i * 320
        draw.rounded_rectangle([bx, cy + 250, bx + 260, cy + 320],
                                radius=32, fill=(*col, 220))
        draw.text((bx + 50, cy + 268), label, font=font(34), fill=WHITE)

    # Streak / stats row
    draw.text((100, H - 300), "🔥 12-day streak", font=font(34, bold=True), fill=GOLD)
    draw.text((520, H - 300), "4 sessions today", font=font(34), fill=TEXT_MUTED)


def draw_journal(draw, img, W, H):
    """Journal + mood + streak"""
    radial_glow(img, W // 2, H // 2, 500, (90, 60, 140), 40)
    draw = ImageDraw.Draw(img)

    # Mood row
    draw.text((60, 320), "How are you feeling?", font=font(42, bold=True), fill=WHITE)
    moods = ["😞", "😕", "😐", "🙂", "😄"]
    for i, m in enumerate(moods):
        mx = 100 + i * 185
        selected = i == 3
        draw.ellipse([mx - 42, 390, mx + 42, 474],
                     fill=(*PRIMARY, 160) if selected else (*BG_CARD, 200))
        draw.text((mx - 25, 400), m, font=font(46), fill=WHITE)

    # Prompt card
    draw.rounded_rectangle([60, 510, W - 60, 710],
                            radius=24, fill=BG_CARD)
    draw.text((90, 538), "Today's Prompt", font=font(28), fill=TEXT_MUTED)
    draw.text((90, 578),
              "What's one thing that made\nyou proud today?",
              font=font(40, bold=True), fill=WHITE)

    # Journal text area
    draw.rounded_rectangle([60, 740, W - 60, 1060], radius=24, fill=BG_PANEL)
    draw.text((90, 770), "Write here…", font=font(36), fill=TEXT_MUTED)
    # fake text lines
    for ly, txt in [
        (830, "Had a really productive morning session."),
        (882, "Finished the design review and got great"),
        (934, "feedback from the team. Feeling motivated"),
        (986, "to carry the momentum into tomorrow…"),
    ]:
        draw.text((90, ly), txt, font=font(34), fill=(*WHITE, 200))

    # Streak chip
    pill(draw, 200, 1120, 280, 60, (*GOLD, 60),
         text="🔥  14-day streak", text_fill=GOLD, f=font(28))

    # Mood chart (mini)
    draw.text((60, 1190), "Mood this week", font=font(34, bold=True), fill=WHITE)
    mood_vals = [3, 2, 4, 3, 4, 4, 3]
    bar_w = 80
    for i, v in enumerate(mood_vals):
        bx = 80 + i * (bar_w + 20)
        bh = v * 40
        draw.rounded_rectangle([bx, 1360 - bh, bx + bar_w, 1360],
                                radius=12, fill=(*PRIMARY, 180 if i == 6 else 100))
        days = ["M", "T", "W", "T", "F", "S", "S"]
        draw.text((bx + 28, 1375), days[i], font=font(28), fill=TEXT_MUTED)


def draw_insights(draw, img, W, H):
    """Insights / review tab"""
    radial_glow(img, W // 2, H // 2, 600, SECONDARY, 35)
    draw = ImageDraw.Draw(img)

    # Section filter pills
    pill_labels = ["All", "Sleep", "Focus", "Journal", "Mood"]
    px_pill = 60
    for pl in pill_labels:
        f_p = font(26)
        bb = draw.textbbox((0, 0), pl, font=f_p)
        pw = bb[2] - bb[0] + 32
        active = pl == "Focus"
        draw.rounded_rectangle([px_pill, 310, px_pill + pw, 358],
                                radius=24, fill=(*PRIMARY, 180) if active else BG_CARD)
        draw.text((px_pill + 16, 320), pl, font=f_p,
                  fill=WHITE if active else TEXT_MUTED)
        px_pill += pw + 12

    # Stat cards row
    stats = [("87", "Focus score", PRIMARY), ("6.8h", "Sleep avg", SECONDARY),
             ("72%", "Plan rate", (130, 100, 200))]
    for i, (val, label, col) in enumerate(stats):
        cx2 = 100 + i * 320
        draw.rounded_rectangle([cx2 - 80, 390, cx2 + 200, 530],
                                radius=22, fill=BG_CARD)
        draw.text((cx2 - 50, 410), val, font=font(64, bold=True), fill=(*col, 240))
        draw.text((cx2 - 50, 486), label, font=font(26), fill=TEXT_MUTED)

    # Line chart (focus hours this week)
    draw.text((60, 570), "Focus hours this week", font=font(38, bold=True), fill=WHITE)
    chart_vals = [3.5, 4.2, 2.8, 5.1, 4.6, 3.9, 5.5]
    chart_x, chart_y, chart_w, chart_h = 60, 640, W - 120, 220
    max_v = max(chart_vals)
    pts = []
    for i, v in enumerate(chart_vals):
        px2 = chart_x + int(i * chart_w / (len(chart_vals) - 1))
        py2 = chart_y + chart_h - int(v / max_v * chart_h)
        pts.append((px2, py2))
    # fill under curve
    poly_pts = pts + [(pts[-1][0], chart_y + chart_h), (pts[0][0], chart_y + chart_h)]
    draw.polygon(poly_pts, fill=(*PRIMARY, 40))
    # line
    for i in range(len(pts) - 1):
        draw.line([pts[i], pts[i + 1]], fill=PRIMARY_LT, width=5)
    for pt in pts:
        draw.ellipse([pt[0] - 8, pt[1] - 8, pt[0] + 8, pt[1] + 8], fill=PRIMARY_LT)

    # AI insight card
    draw.rounded_rectangle([60, 900, W - 60, 1080], radius=24, fill=(*PRIMARY, 50))
    draw.rounded_rectangle([60, 900, W - 60, 1080], radius=24,
                            outline=(*PRIMARY_LT, 80), width=1)
    draw.text((90, 930), "✦  AI Insight", font=font(30, bold=True), fill=PRIMARY_LT)
    draw.text((90, 976),
              "Your focus is 18% higher\nafter morning runs. Keep it up!",
              font=font(36), fill=(*WHITE, 210))

    # Sleep card
    draw.rounded_rectangle([60, 1110, W - 60, 1290], radius=24, fill=BG_CARD)
    draw.text((90, 1140), "Sleep quality", font=font(32, bold=True), fill=WHITE)
    for i, (q, col) in enumerate([("Good", SECONDARY), ("Fair", GOLD),
                                   ("Good", SECONDARY), ("Poor", CORAL),
                                   ("Good", SECONDARY), ("Good", SECONDARY),
                                   ("Fair", GOLD)]):
        bx3 = 90 + i * 120
        draw.rounded_rectangle([bx3, 1194, bx3 + 100, 1230],
                                radius=10, fill=(*col, 160))
        draw.text((bx3 + 12, 1196), q[:4], font=font(22), fill=WHITE)


def draw_ai_plan(draw, img, W, H):
    """AI planning assistant"""
    radial_glow(img, W // 2, H // 2, 600, (80, 50, 160), 50)
    draw = ImageDraw.Draw(img)

    # Chat bubbles
    messages = [
        ("user",  "Plan my ideal Thursday for deep work"),
        ("ai",    "Here's an optimised plan based on\nyour focus patterns and energy peaks:"),
        ("ai",    "· 6:00 Morning Run  (routine anchor)\n· 7:30 Deep Work Sprint  (peak energy)\n· 10:00 Second Sprint  (still high)\n· 12:00 Lunch + Walk  (recharge)\n· 13:00 Admin/calls  (low energy)\n· 15:30 Reading  (wind-down)"),
        ("user",  "Add a 30-min break at 9:30"),
        ("ai",    "Done! Break added. Your deep work\nblocks are now 90 min each — optimal\nfor sustained flow."),
    ]
    by = 320
    for role, text in messages:
        is_user = role == "user"
        f_m = font(32 if not is_user else 30)
        lines = text.split("\n")
        max_w = max(draw.textbbox((0, 0), l, font=f_m)[2] for l in lines) + 48
        max_w = min(max_w, W - 140)
        bh = len(lines) * 46 + 24
        bx = W - 60 - max_w if is_user else 60
        col = (*PRIMARY, 200) if is_user else (*BG_CARD, 255)
        draw.rounded_rectangle([bx, by, bx + max_w, by + bh], radius=20, fill=col)
        for li, line in enumerate(lines):
            draw.text((bx + 24, by + 12 + li * 46), line, font=f_m,
                      fill=WHITE if is_user else (*WHITE, 230))
        by += bh + 18
        if by > H - 300:
            break

    # Input bar
    draw.rounded_rectangle([40, H - 250, W - 40, H - 170],
                            radius=30, fill=BG_CARD)
    draw.text((80, H - 225), "Ask ChronosFlow AI…", font=font(34), fill=TEXT_MUTED)
    draw.rounded_rectangle([W - 160, H - 242, W - 60, H - 178],
                            radius=24, fill=PRIMARY)
    draw.text((W - 138, H - 232), "➤", font=font(36, bold=True), fill=WHITE)

    # Model badge
    pill(draw, W // 2, H - 120, 360, 54, (*PRIMARY, 60),
         text="✦  Powered by Gemini Nano", text_fill=(*PRIMARY_LT, 200), f=font(26))


def draw_wear(draw, img, W, H):
    """Wear OS + companion glance"""
    radial_glow(img, W // 2, H // 2, 600, SECONDARY, 40)
    draw = ImageDraw.Draw(img)

    # Watch outline
    wcx, wcy, wr = W // 2, 900, 290
    draw.ellipse([wcx - wr - 18, wcy - wr - 18, wcx + wr + 18, wcy + wr + 18],
                 outline=(*TEXT_MUTED, 80), width=3)
    draw.ellipse([wcx - wr, wcy - wr, wcx + wr, wcy + wr],
                 fill=BG_DARK, outline=(*PRIMARY_LT, 140), width=8)

    # Crown
    draw.rounded_rectangle([wcx + wr + 8, wcy - 20, wcx + wr + 30, wcy + 20],
                            radius=6, fill=(*PRIMARY_LT, 120))

    # Watch face content
    draw.text((wcx - 40, wcy - 160), "9:41", font=font(80, bold=True), fill=WHITE)
    draw.text((wcx - 80, wcy - 70), "Deep Work", font=font(32, bold=True), fill=PRIMARY_LT)

    # Progress bar inside watch
    pw2, ph2 = 420, 22
    draw.rounded_rectangle([wcx - pw2 // 2, wcy - 20, wcx + pw2 // 2, wcy - 20 + ph2],
                            radius=11, fill=(*BG_CARD, 220))
    draw.rounded_rectangle([wcx - pw2 // 2, wcy - 20,
                             wcx - pw2 // 2 + int(pw2 * 0.72), wcy - 20 + ph2],
                            radius=11, fill=(*PRIMARY_LT, 220))
    draw.text((wcx - 60, wcy + 20), "18:32 left", font=font(36), fill=TEXT_MUTED)

    # Live ring
    draw.arc([wcx - wr + 20, wcy - wr + 20, wcx + wr - 20, wcy + wr - 20],
             start=-90, end=-90 + int(360 * 0.72), fill=(*PRIMARY, 180), width=14)

    # Features list below phone
    draw.text((60, 340), "Glanceable. Always synced.", font=font(44, bold=True), fill=WHITE)
    features = [
        ("⌚", "Live focus timer on your wrist"),
        ("🔔", "Gentle haptic nudges"),
        ("📊", "Ongoing Activity display"),
        ("🔗", "Real-time phone sync"),
    ]
    for i, (icon, text) in enumerate(features):
        fy = 420 + i * 90
        draw.text((80, fy), icon, font=font(44), fill=WHITE)
        draw.text((160, fy + 4), text, font=font(36), fill=(*WHITE, 210))


# ══════════════════════════════════════════════════════════════════════════════
# 4. TABLET SCREENSHOTS  (landscape 1600 × 1200)
# ══════════════════════════════════════════════════════════════════════════════
def make_tablet_screenshot(filename, label):
    W, H = 1600, 1200
    img = Image.new("RGB", (W, H))
    v_gradient(img, GRAD_TOP, BG_DARK)
    radial_glow(img, W // 3, H // 2, 600, PRIMARY, 40)
    radial_glow(img, W * 2 // 3, H // 2, 500, SECONDARY, 30)

    draw = ImageDraw.Draw(img)

    # Sidebar
    draw.rectangle([0, 0, 240, H], fill=BG_PANEL)
    nav_items = ["Plan", "Today", "Focus", "Review", "Journal", "Goals", "Settings"]
    for i, item in enumerate(nav_items):
        active = item == label
        if active:
            draw.rounded_rectangle([10, 100 + i * 110, 230, 180 + i * 110],
                                    radius=14, fill=(*PRIMARY, 120))
        draw.text((30, 118 + i * 110), item,
                  font=font(34, bold=active), fill=WHITE if active else TEXT_MUTED)

    # Main area title
    draw.text((280, 60), label, font=font(68, bold=True), fill=WHITE)
    draw.text((280, 148), "Thursday, June 18", font=font(36), fill=TEXT_MUTED)

    # Content grid (3 cards)
    card_w = (W - 320) // 3 - 20
    for ci in range(3):
        cx2 = 280 + ci * (card_w + 20)
        draw.rounded_rectangle([cx2, 210, cx2 + card_w, H - 80],
                                radius=22, fill=BG_CARD)
        col = [PRIMARY, SECONDARY, (130, 100, 200)][ci]
        draw.rounded_rectangle([cx2, 210, cx2 + card_w, 260],
                                radius=22, fill=(*col, 160))
        draw.text((cx2 + 20, 222),
                  ["Timeline", "Stats", "AI Insights"][ci],
                  font=font(30, bold=True), fill=WHITE)
        # Mini blocks inside
        by_t = 280
        for j in range(5):
            bh_t = 70 + j * 10
            c_t = [PRIMARY, SECONDARY, CORAL, (130, 100, 200), SECONDARY][j]
            draw.rounded_rectangle([cx2 + 16, by_t, cx2 + card_w - 16, by_t + bh_t],
                                    radius=12, fill=(*c_t, 120))
            by_t += bh_t + 14

    img.save(os.path.join(OUT, filename))
    print(f"[OK] {filename}")


# ══════════════════════════════════════════════════════════════════════════════
# MAIN
# ══════════════════════════════════════════════════════════════════════════════
if __name__ == "__main__":
    print("Generating ChronosFlow Play Store graphics…\n")

    make_icon()
    make_feature_graphic()

    screenshots = [
        ("phone_01_plan.png",     "Plan",     "Your day, structured",    draw_plan),
        ("phone_02_focus.png",    "Focus",    "Deep work, on demand",    draw_focus),
        ("phone_03_journal.png",  "Journal",  "Reflect & grow",          draw_journal),
        ("phone_04_insights.png", "Insights", "Understand your patterns",draw_insights),
        ("phone_05_ai.png",       "AI",       "Plan with intelligence",  draw_ai_plan),
        ("phone_06_wear.png",     "Wear OS",  "Sync to your wrist",      draw_wear),
    ]
    for fname, title, subtitle, fn in screenshots:
        make_screenshot(fname, title, subtitle, fn)

    make_tablet_screenshot("tablet_7in_plan.png",  "Plan")
    make_tablet_screenshot("tablet_10in_plan.png", "Plan")

    print(f"\nAll done! Files saved to:\n  {OUT}")
