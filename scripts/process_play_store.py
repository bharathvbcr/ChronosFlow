# -*- coding: utf-8 -*-
"""
Process live ChronosFlow screenshots into Play Store assets.
  - Extracts + upscales real app icon from App Info screenshot
  - Crops all 8 screens to 9:16 (1344x2392) with label banners
  - Regenerates feature graphic using composited real screenshots
"""
import sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageEnhance
import os, math

SRC  = r"C:\Users\bhara\Downloads\Code\ChronosFlow\play_store_graphics"
OUT  = os.path.join(SRC, "final")
os.makedirs(OUT, exist_ok=True)

# -- palette ------------------------------------------------------------------
PRIMARY    = (114, 87, 184)
PRIMARY_LT = (185, 165, 255)
SECONDARY  = (78, 154, 154)
SECONDARY_LT = (117, 214, 210)
BG_DARK    = (15, 17, 26)
BG_PANEL   = (26, 29, 41)
GRAD_TOP   = (27, 37, 54)
CORAL      = (224, 87, 62)
WHITE      = (255, 255, 255)
TEXT_MUTED = (140, 140, 165)
GOLD       = (255, 196, 87)

# -- fonts --------------------------------------------------------------------
def font(size, bold=False):
    candidates = (
        [r"C:\Windows\Fonts\segoeuib.ttf", r"C:\Windows\Fonts\arialbd.ttf"] if bold
        else [r"C:\Windows\Fonts\segoeui.ttf", r"C:\Windows\Fonts\arial.ttf"]
    )
    for p in candidates:
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()

# -- gradient -----------------------------------------------------------------
def v_gradient(img, top, bot):
    d = ImageDraw.Draw(img)
    w, h = img.size
    for y in range(h):
        t = y / h
        d.line([(0,y),(w,y)], fill=tuple(int(top[i]+(bot[i]-top[i])*t) for i in range(3)))

def radial_glow(img, cx, cy, radius, color, alpha=60):
    ov = Image.new("RGBA", img.size, (0,0,0,0))
    d  = ImageDraw.Draw(ov)
    for i in range(20,0,-1):
        r = int(radius*i/20)
        a = int(alpha*(1-i/20))
        d.ellipse([cx-r,cy-r,cx+r,cy+r], fill=(*color,a))
    img.paste(ov, mask=ov)


# =============================================================================
# 1.  ICON  512x512  — rendered directly from ic_chronosflow_logo.xml paths
#     Vector viewport: 108x108dp
#     Elements: track ring, teal progress arc (210°), yellow hand + glow + dots
# =============================================================================
def make_icon():
    SIZE  = 512
    SS    = 4           # supersampling factor for smooth edges
    RS    = SIZE * SS   # render size = 2048

    # Scale factor: RS pixels = 108dp
    S = RS / 108.0

    # -- colours --
    TEAL   = (24,  184, 166)
    VIOLET = (108,  99, 255)
    YELLOW = (247, 201,  72)

    # -- background gradient --
    bg = Image.new("RGB", (RS, RS))
    for y in range(RS):
        t = y / RS
        bg.putpixel((0, 0), (0,0,0))  # ensure RGB mode
    v_gradient(bg, GRAD_TOP, BG_DARK)
    canvas = bg.convert("RGBA")

    # circle geometry (from vector: center 54,54 radius 26)
    cx  = 54 * S
    cy  = 54 * S
    R   = 26 * S
    SW  = int(round(8 * S))   # stroke width for ring / arc

    def arc_layer(start_deg, end_deg, color, alpha, width):
        layer = Image.new("RGBA", (RS, RS), (0,0,0,0))
        d = ImageDraw.Draw(layer)
        bbox = [cx - R, cy - R, cx + R, cy + R]
        d.arc(bbox, start=start_deg, end=end_deg,
              fill=(*color, alpha), width=width)
        return layer

    # 1. Track ring — full circle, violet 35% alpha
    canvas = Image.alpha_composite(canvas, arc_layer(0, 360, VIOLET, int(0.35*255), SW))

    # 2. Teal progress arc — 210° clockwise from top (270°) to lower-left (120°)
    #    In Pillow (0=right, 90=bottom, clockwise): top=270, arc goes 270→360→0→120
    teal_layer = arc_layer(270, 120, TEAL, 255, SW)
    # Round end-caps: circles at arc endpoints
    td = ImageDraw.Draw(teal_layer)
    cap_r = SW // 2
    # Start cap at (54, 28)
    sx, sy = 54*S, 28*S
    # End cap at (41, 76.5)
    ex, ey = 41*S, 76.5*S
    for pt in [(sx, sy), (ex, ey)]:
        td.ellipse([pt[0]-cap_r, pt[1]-cap_r, pt[0]+cap_r, pt[1]+cap_r],
                   fill=(*TEAL, 255))
    canvas = Image.alpha_composite(canvas, teal_layer)

    # 3. Hand glow — thick blurred yellow stroke (alpha 30%)
    glow_layer = Image.new("RGBA", (RS, RS), (0,0,0,0))
    gd = ImageDraw.Draw(glow_layer)
    gd.line([(cx, cy), (ex, ey)],
            fill=(*YELLOW, int(0.30*255)), width=int(7*S))
    glow_layer = glow_layer.filter(ImageFilter.GaussianBlur(int(3*S)))
    canvas = Image.alpha_composite(canvas, glow_layer)

    # 4. Hand — yellow line
    hand_layer = Image.new("RGBA", (RS, RS), (0,0,0,0))
    hd = ImageDraw.Draw(hand_layer)
    hw = max(2, int(3.5*S))
    hd.line([(cx, cy), (ex, ey)], fill=(*YELLOW, 255), width=hw)
    canvas = Image.alpha_composite(canvas, hand_layer)

    # 5. Tip halo — semi-transparent circle at (41, 76.5) r=8
    halo_layer = Image.new("RGBA", (RS, RS), (0,0,0,0))
    hald = ImageDraw.Draw(halo_layer)
    hr = int(8*S)
    hald.ellipse([ex-hr, ey-hr, ex+hr, ey+hr], fill=(*YELLOW, int(0.25*255)))
    canvas = Image.alpha_composite(canvas, halo_layer)

    # 6. Tip dot — solid circle at (41, 76.5) r=5
    dot_layer = Image.new("RGBA", (RS, RS), (0,0,0,0))
    dd = ImageDraw.Draw(dot_layer)
    dr = int(5*S)
    dd.ellipse([ex-dr, ey-dr, ex+dr, ey+dr], fill=(*YELLOW, 255))
    canvas = Image.alpha_composite(canvas, dot_layer)

    # 7. Center hub — solid circle at (54,54) r=4
    hub_layer = Image.new("RGBA", (RS, RS), (0,0,0,0))
    hubd = ImageDraw.Draw(hub_layer)
    hubr = int(4*S)
    hubd.ellipse([cx-hubr, cy-hubr, cx+hubr, cy+hubr], fill=(*YELLOW, 255))
    canvas = Image.alpha_composite(canvas, hub_layer)

    # Downsample to 512 (SS×SSAA) → smooth edges
    flat = canvas.convert("RGB").resize((SIZE, SIZE), Image.LANCZOS)

    # Rounded-square clip mask
    mask = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, SIZE, SIZE], radius=115, fill=255)

    result = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    result.paste(flat, mask=mask)
    result.convert("RGB").save(os.path.join(OUT, "icon_512.png"))
    print("[OK] icon_512.png  (rendered from vector paths)")


# =============================================================================
# 2.  SCREENSHOTS  — crop to 9:16, add label banner
# =============================================================================
SCREENS = [
    ("plan_try.png",   "Plan",     "Schedule your day, your way"),
    ("ss_02_today.png","Today",    "Live view of what's next"),
    ("ss_04_focus.png","Focus",    "Run sessions with protection"),
    ("ss_03_journal.png","Journal","Reflect, track mood & grow"),
    ("ss_05_review.png","Insights","Understand your patterns"),
    ("ss_06_tasks.png", "Tasks",   "Capture & prioritise what matters"),
    ("ss_07_habits.png","Habits",  "Build streaks that stick"),
    ("ss_08_goals.png", "Goals",   "Link tasks to long-term goals"),
]

TARGET_W, TARGET_H = 1344, 2392   # 9:16 at device width

def add_banner(img, title, subtitle):
    """Overlay a gradient caption banner at the bottom."""
    w, h = img.size
    draw = ImageDraw.Draw(img)

    # Semi-transparent gradient strip at the bottom
    banner_h = 200
    banner = Image.new("RGBA", (w, banner_h), (0,0,0,0))
    bd = ImageDraw.Draw(banner)
    for y in range(banner_h):
        a = int(220 * y / banner_h)
        bd.line([(0,y),(w,y)], fill=(*BG_DARK, a))

    img_rgba = img.convert("RGBA")
    img_rgba.paste(banner, (0, h-banner_h), mask=banner.split()[3])

    # Draw text on composited image
    draw2 = ImageDraw.Draw(img_rgba)
    draw2.text((60, h-banner_h+30), title,    font=font(68, bold=True), fill=WHITE)
    draw2.text((60, h-banner_h+110), subtitle, font=font(36),            fill=(*TEXT_MUTED, 220))

    return img_rgba.convert("RGB")


def process_screenshots():
    for fname, title, subtitle in SCREENS:
        src_path = os.path.join(SRC, fname)
        if not os.path.exists(src_path):
            print(f"[SKIP] {fname} not found")
            continue

        img = Image.open(src_path)
        ow, oh = img.size

        # Crop to 9:16
        need_h = int(ow * 16 / 9)
        if oh > need_h:
            top = (oh - need_h) // 4   # keep more of the top
            img = img.crop((0, top, ow, top + need_h))
        elif oh < need_h:
            # Add padding
            pad = Image.new("RGB", (ow, need_h), BG_DARK)
            pad.paste(img, (0, 0))
            img = pad

        # Resize to exactly 1344x2392 if needed
        if img.size != (TARGET_W, TARGET_H):
            img = img.resize((TARGET_W, TARGET_H), Image.LANCZOS)

        # Add label banner
        img = add_banner(img, title, subtitle)

        out_name = f"ss_{title.lower().replace(' ','_')}.png"
        img.save(os.path.join(OUT, out_name))
        print(f"[OK] {out_name}")


# =============================================================================
# 3.  FEATURE GRAPHIC  1024x500  — real screenshots composited
# =============================================================================
def make_feature_graphic():
    W, H = 1024, 500
    img = Image.new("RGB", (W, H))
    v_gradient(img, GRAD_TOP, BG_DARK)
    radial_glow(img, 720, 250, 380, PRIMARY, 55)
    radial_glow(img, 180, 380, 280, SECONDARY, 35)

    draw = ImageDraw.Draw(img)

    # Decorative ring
    draw.ellipse([-60, H//2-240, -60+480, H//2+240], outline=(*PRIMARY_LT,40), width=2)

    # --- Inset phone screenshots (right side) ---
    screens_to_use = [
        ("plan_try.png",    1.0),
        ("ss_02_today.png", 0.85),
    ]
    phone_x_start = 550
    for i, (fname, opacity) in enumerate(screens_to_use):
        p = os.path.join(SRC, fname)
        if not os.path.exists(p):
            continue
        sc = Image.open(p).convert("RGBA")
        # Crop to 9:16 centre strip
        ow, oh = sc.size
        need_h = int(ow * 16 / 9)
        top = (oh - need_h) // 4
        sc = sc.crop((0, top, ow, top + need_h))
        # Resize to fit phone height in feature graphic
        ph = H - 20
        pw = int(ph * ow / need_h)
        sc = sc.resize((pw, ph), Image.LANCZOS)
        # Darken back screenshot
        if opacity < 1.0:
            sc = ImageEnhance.Brightness(sc).enhance(opacity)
        px = phone_x_start + i * (pw - 60)
        py = (H - ph) // 2
        if i > 0:
            # Rounded frame for background phone
            frame = Image.new("RGBA", (pw+8, ph+8), (0,0,0,0))
            ImageDraw.Draw(frame).rounded_rectangle([0,0,pw+8,ph+8], radius=22, fill=(*BG_PANEL,200))
            img.paste(frame.convert("RGB"), (px-4, py-4),
                      mask=frame.split()[3])
        img.paste(sc.convert("RGB"), (px, py),
                  mask=sc.split()[3] if sc.mode=="RGBA" else None)
        # Phone bezel
        ImageDraw.Draw(img).rounded_rectangle(
            [px-3, py-3, px+pw+3, py+ph+3], radius=22,
            outline=(*PRIMARY_LT, 80 if i==0 else 40), width=2)

    # --- Text (left side) ---
    f_big = font(82, bold=True)
    draw.text((52, 110), "Chronos", font=f_big, fill=WHITE)
    bx = draw.textbbox((52,110),"Chronos",font=f_big)
    draw.text((bx[2]+6, 110), "Flow", font=f_big, fill=PRIMARY_LT)
    draw.text((54, 216), "Your day, beautifully planned.", font=font(32), fill=(*WHITE,190))

    # Feature pills
    chips = [("Plan", SECONDARY), ("Focus", PRIMARY), ("AI", (130,100,200)), ("Journal", CORAL)]
    cx2 = 54
    for label, col in chips:
        f_c = font(24)
        bb = draw.textbbox((0,0), label, font=f_c)
        cw = bb[2]-bb[0]+32
        draw.rounded_rectangle([cx2, 298, cx2+cw, 338], radius=16, fill=(*col, 90))
        draw.rounded_rectangle([cx2, 298, cx2+cw, 338], radius=16, outline=(*col,180), width=1)
        draw.text((cx2+16, 306), label, font=f_c, fill=WHITE)
        cx2 += cw + 12

    draw.text((54, 420), "ChronosFlow  |  Time management, reimagined",
              font=font(18), fill=(*TEXT_MUTED, 180))

    img.save(os.path.join(OUT, "feature_graphic_1024x500.png"))
    print("[OK] feature_graphic_1024x500.png")


# =============================================================================
# MAIN
# =============================================================================
if __name__ == "__main__":
    print("Processing ChronosFlow Play Store assets from live screenshots...\n")
    make_icon()
    process_screenshots()
    make_feature_graphic()

    print(f"\nAll assets saved to: {OUT}")
    files = os.listdir(OUT)
    for f in sorted(files):
        sz = os.path.getsize(os.path.join(OUT, f))
        print(f"  {f:<45} {sz//1024:>5} KB")
