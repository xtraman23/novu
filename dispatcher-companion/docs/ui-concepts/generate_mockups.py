#!/usr/bin/env python3
"""Generates the five UI concept mockups (PNG) for RingCentral Dispatcher Companion.

Each PNG shows two phone screens: the Live Call dashboard and the floating
copilot overlay rendered on top of a simulated RingCentral call screen.
"""
from PIL import Image, ImageDraw, ImageFont

FONT_DIR = "/usr/share/fonts/truetype/dejavu"
PHONE_W, PHONE_H = 720, 1520
PAD = 70
HEADER_H = 150


def font(size, bold=False, mono=False):
    name = ("DejaVuSansMono" if mono else "DejaVuSans") + ("-Bold" if bold else "")
    return ImageFont.truetype(f"{FONT_DIR}/{name}.ttf", size)


def rrect(d, box, r, fill=None, outline=None, width=1):
    d.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)


def text_w(d, s, f):
    return d.textbbox((0, 0), s, font=f)[2]


STYLES = {
    "A": dict(
        name="Concept A — Modern SaaS",
        bg="#F4F5FB", surface="#FFFFFF", surface2="#EEF0FA", border="#E2E5F1",
        text="#1A1D2E", muted="#6E7390", accent="#6366F1", accent_dim="#E4E5FB",
        ok="#16A34A", warn="#D97706", danger="#DC2626", broker="#DC2626",
        dispatcher="#6366F1", radius=26, mono=False, dark=False,
        appbar="#FFFFFF", appbar_text="#1A1D2E",
    ),
    "B": dict(
        name="Concept B — Freight Dispatcher Pro",
        bg="#E9EDF2", surface="#FFFFFF", surface2="#DDE4EC", border="#C3CDD9",
        text="#15233B", muted="#5A6B82", accent="#F97316", accent_dim="#FDE8D8",
        ok="#15803D", warn="#C2700C", danger="#B91C1C", broker="#B91C1C",
        dispatcher="#1D4ED8", radius=10, mono=False, dark=False,
        appbar="#1B2A4A", appbar_text="#FFFFFF",
    ),
    "C": dict(
        name="Concept C — Dark Trading Terminal",
        bg="#0A0E14", surface="#10151E", surface2="#161D29", border="#222B3A",
        text="#D7E0EC", muted="#5E6B80", accent="#22C55E", accent_dim="#0E2A1A",
        ok="#22C55E", warn="#EAB308", danger="#EF4444", broker="#EF4444",
        dispatcher="#22C55E", radius=8, mono=True, dark=True,
        appbar="#0A0E14", appbar_text="#22C55E",
    ),
    "D": dict(
        name="Concept D — Operations Command Center",
        bg="#0F1B2D", surface="#15273E", surface2="#1B324E", border="#28415F",
        text="#E3EDF7", muted="#7B93AD", accent="#22D3EE", accent_dim="#0D3A46",
        ok="#34D399", warn="#FBBF24", danger="#F87171", broker="#F87171",
        dispatcher="#22D3EE", radius=14, mono=False, dark=True,
        appbar="#0B1422", appbar_text="#22D3EE",
    ),
    "E": dict(
        name="Concept E — Ultra Minimal Fast Workflow",
        bg="#FFFFFF", surface="#FFFFFF", surface2="#F5F5F5", border="#E5E5E5",
        text="#111111", muted="#8A8A8A", accent="#16A34A", accent_dim="#E8F6EC",
        ok="#16A34A", warn="#CA8A04", danger="#DC2626", broker="#DC2626",
        dispatcher="#111111", radius=18, mono=False, dark=False,
        appbar="#FFFFFF", appbar_text="#111111",
    ),
}

PDWCR = [
    ("P", "PICKUP", "Dallas, TX 75201", 0.95),
    ("D", "DELIVERY", "Atlanta, GA 30303", 0.92),
    ("W", "WEIGHT", "42,000 lbs", 0.88),
    ("C", "COMMODITY", "Dry Goods", 0.84),
    ("R", "RATE", "$1,900", 0.97),
]

TRANSCRIPT = [
    ("Broker", "I can do nineteen hundred on that Dallas to Atlanta, picks tomorrow at 8."),
    ("Dispatcher", "Forty-two thousand of dry goods on a dry van, correct?"),
    ("Broker", "Correct, 53-foot van. I only have $1,900 in it."),
]


def status_bar(d, x, y, S):
    f = font(26, mono=S["mono"])
    d.text((x + 36, y + 18), "9:41", font=f, fill=S["text"])
    bx = x + PHONE_W - 60
    d.rectangle([bx, y + 24, bx + 40, y + 44], outline=S["text"], width=3)
    d.rectangle([bx + 4, y + 28, bx + 28, y + 40], fill=S["accent"])
    for i in range(4):
        h = 8 + i * 7
        d.rectangle([bx - 80 + i * 14, y + 44 - h, bx - 72 + i * 14, y + 44], fill=S["text"])


def chip(d, x, y, label, S, fill, fg, f=None, pad=18):
    f = f or font(24, bold=True, mono=S["mono"])
    w = text_w(d, label, f) + pad * 2
    rrect(d, [x, y, x + w, y + 46], 23, fill=fill)
    d.text((x + pad, y + 9), label, font=f, fill=fg)
    return w


def card(d, x, y, w, h, S, title=None, title_color=None):
    rrect(d, [x, y, x + w, y + h], S["radius"], fill=S["surface"], outline=S["border"], width=2)
    if title:
        d.text((x + 28, y + 20), title, font=font(24, bold=True, mono=S["mono"]),
               fill=title_color or S["muted"])


def conf_dot(d, x, y, conf, S):
    c = S["ok"] if conf > 0.9 else (S["warn"] if conf > 0.8 else S["muted"])
    d.ellipse([x, y, x + 16, y + 16], fill=c)


def draw_dashboard(img, d, ox, oy, S, key):
    rrect(d, [ox, oy, ox + PHONE_W, oy + PHONE_H], 56, fill=S["bg"], outline="#3A3A3A", width=6)
    status_bar(d, ox, oy + 8, S)
    y = oy + 70

    # App bar
    d.rectangle([ox + 6, y, ox + PHONE_W - 6, y + 86], fill=S["appbar"])
    d.text((ox + 36, y + 22), "DISPATCHER COMPANION" if key != "E" else "DISPATCH",
           font=font(30, bold=True, mono=S["mono"]), fill=S["appbar_text"])
    # capture status chip
    chip(d, ox + PHONE_W - 240, y + 20, "● AUDIO: GOOD", S, S["accent_dim"], S["ok"],
         f=font(22, bold=True, mono=S["mono"]))
    y += 110

    inner_x, inner_w = ox + 28, PHONE_W - 56

    if key == "E":
        # Ultra-minimal: huge rate, PDWCR as plain rows, one giant button
        d.text((inner_x, y), "RATE ON TABLE", font=font(26, bold=True), fill=S["muted"])
        d.text((inner_x, y + 36), "$1,900", font=font(110, bold=True), fill=S["text"])
        d.text((inner_x + 415, y + 100), "→ counter\n   $2,100", font=font(30, bold=True), fill=S["accent"])
        y += 190
        for tag, label, value, conf in PDWCR:
            d.line([inner_x, y, inner_x + inner_w, y], fill=S["border"], width=2)
            d.text((inner_x, y + 18), label, font=font(24, bold=True), fill=S["muted"])
            d.text((inner_x + 260, y + 14), value, font=font(32, bold=True), fill=S["text"])
            conf_dot(d, inner_x + inner_w - 24, y + 26, conf, S)
            y += 72
        d.line([inner_x, y, inner_x + inner_w, y], fill=S["border"], width=2)
        y += 26
        d.text((inner_x, y), "LIVE", font=font(24, bold=True), fill=S["danger"])
        ty = y + 40
        for who, line in TRANSCRIPT[-2:]:
            c = S["broker"] if who == "Broker" else S["dispatcher"]
            d.text((inner_x, ty), who.upper(), font=font(22, bold=True), fill=c)
            d.text((inner_x, ty + 30), line[:52] + ("…" if len(line) > 52 else ""),
                   font=font(26), fill=S["text"])
            ty += 84
        by = oy + PHONE_H - 190
        rrect(d, [inner_x, by, inner_x + inner_w, by + 120], 60, fill=S["accent"])
        s = "STOP DISPATCH MODE"
        f = font(36, bold=True)
        d.text((inner_x + (inner_w - text_w(d, s, f)) / 2, by + 40), s, font=f, fill="#FFFFFF")
        return

    # PDWCR card
    ch = 348
    card(d, inner_x, y, inner_w, ch, S, "PDWCR — LIVE EXTRACTION",
         S["accent"] if S["dark"] else None)
    ry = y + 62
    for tag, label, value, conf in PDWCR:
        rrect(d, [inner_x + 24, ry, inner_x + 70, ry + 44], 10 if key == "B" else 22,
              fill=S["accent_dim"])
        d.text((inner_x + 38, ry + 7), tag, font=font(26, bold=True, mono=S["mono"]), fill=S["accent"])
        d.text((inner_x + 92, ry + 9), label, font=font(20, bold=True, mono=S["mono"]), fill=S["muted"])
        f = font(28, bold=True, mono=S["mono"])
        d.text((inner_x + 250, ry + 6), value, font=f, fill=S["text"])
        conf_dot(d, inner_x + inner_w - 48, ry + 14, conf, S)
        ry += 56
    y += ch + 22

    # Negotiation card
    ch = 240
    card(d, inner_x, y, inner_w, ch, S, "NEGOTIATION COPILOT",
         S["accent"] if S["dark"] else None)
    d.text((inner_x + 28, y + 58), "COUNTER", font=font(20, bold=True, mono=S["mono"]), fill=S["muted"])
    d.text((inner_x + 28, y + 84), "$2,100", font=font(56, bold=True, mono=S["mono"]), fill=S["accent"])
    cols = [("FLOOR", "$1,900", S["text"]), ("CEILING", "$2,250", S["text"]),
            ("ACCEPT", "74%", S["ok"])]
    cx = inner_x + 280
    for label, val, c in cols:
        d.text((cx, y + 62), label, font=font(19, bold=True, mono=S["mono"]), fill=S["muted"])
        d.text((cx, y + 92), val, font=font(29, bold=True, mono=S["mono"]), fill=c)
        cx += 145
    rrect(d, [inner_x + 24, y + 162, inner_x + inner_w - 24, y + 216], 12, fill=S["surface2"])
    quote = "“I can move it today for $2,100 — truck is 30 mi out.”"
    if S["mono"]:
        quote = "“$2,100 moves it today — truck is 30 mi out.”"
    d.text((inner_x + 40, y + 175), quote, font=font(23, mono=S["mono"]), fill=S["text"])
    y += ch + 22

    # Transcript card
    ch = 300
    card(d, inner_x, y, inner_w, ch, S, "LIVE TRANSCRIPT  •  REC 04:32",
         S["danger"] if S["dark"] else None)
    ty = y + 60
    maxc = 42 if S["mono"] else 52
    for who, line in TRANSCRIPT:
        c = S["broker"] if who == "Broker" else S["dispatcher"]
        d.text((inner_x + 28, ty), who + ":", font=font(23, bold=True, mono=S["mono"]), fill=c)
        s = line[:maxc] + ("…" if len(line) > maxc else "")
        d.text((inner_x + 28, ty + 30), s, font=font(24, mono=S["mono"]), fill=S["text"])
        ty += 78
    y += ch + 22

    # Bottom metric strip + stop button
    mh = 96
    card(d, inner_x, y, inner_w, mh, S)
    metrics = [("RPM", "$2.62", S["ok"]), ("MILES", "781", S["text"]),
               ("DEADHEAD", "34", S["warn"]), ("PROFIT", "$612", S["ok"])]
    mx = inner_x + 30
    for label, val, c in metrics:
        d.text((mx, y + 16), label, font=font(19, bold=True, mono=S["mono"]), fill=S["muted"])
        d.text((mx, y + 44), val, font=font(30, bold=True, mono=S["mono"]), fill=c)
        mx += inner_w // 4 - 8
    by = y + mh + 20
    rrect(d, [inner_x, by, inner_x + inner_w, by + 96], S["radius"] + 10, fill=S["danger"])
    s = "■  STOP DISPATCH MODE"
    f = font(32, bold=True, mono=S["mono"])
    d.text((inner_x + (inner_w - text_w(d, s, f)) / 2, by + 30), s, font=f, fill="#FFFFFF")


def draw_overlay_screen(img, d, ox, oy, S, key):
    # Simulated RingCentral in-call screen (dark)
    rrect(d, [ox, oy, ox + PHONE_W, oy + PHONE_H], 56, fill="#10131A", outline="#3A3A3A", width=6)
    rc = dict(S, text="#E8EAF0", muted="#8B93A5", mono=False)
    status_bar(d, ox, oy + 8, rc)
    d.text((ox + 250, oy + 96), "RingCentral", font=font(28, bold=True), fill="#FF8800")
    d.ellipse([ox + 270, oy + 170, ox + 450, oy + 350], fill="#26304A")
    d.text((ox + 330, oy + 230), "MR", font=font(60, bold=True), fill="#9FB0D0")
    for s, f, c, dy in [("Mark Reynolds", font(40, bold=True), "#FFFFFF", 380),
                        ("TQL  •  MC 322734", font(26), "#8B93A5", 436),
                        ("04:32", font(30, bold=True), "#22C55E", 480)]:
        d.text((ox + (PHONE_W - text_w(d, s, f)) / 2, oy + dy), s, font=f, fill=c)
    # call buttons
    labels = ["MUTE", "KEYPAD", "SPEAKER"]
    bx = ox + 110
    for i, lab in enumerate(labels):
        fill = "#22C55E" if lab == "SPEAKER" else "#222A3C"
        d.ellipse([bx, oy + 1230, bx + 120, oy + 1350], fill=fill)
        d.text((bx + 60 - text_w(d, lab, font(20)) / 2, oy + 1364), lab, font=font(20), fill="#8B93A5")
        bx += 190
    d.ellipse([ox + 290, oy + 1390, ox + 430, oy + 1470], fill="#DC2626")
    d.text((ox + 332, oy + 1412), "END", font=font(30, bold=True), fill="#FFFFFF")

    # Floating copilot panel
    px, py, pw, ph = ox + 40, oy + 560, PHONE_W - 80, 600
    shadow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.rounded_rectangle([px + 10, py + 14, px + pw + 10, py + ph + 14],
                         radius=S["radius"] + 8, fill=(0, 0, 0, 110))
    img.alpha_composite(shadow)
    rrect(d, [px, py, px + pw, py + ph], S["radius"] + 8, fill=S["surface"],
          outline=S["accent"], width=3)
    # drag handle
    d.rounded_rectangle([px + pw / 2 - 40, py + 12, px + pw / 2 + 40, py + 22], 5, fill=S["muted"])
    d.text((px + 26, py + 34), "COPILOT", font=font(24, bold=True, mono=S["mono"]), fill=S["accent"])
    chip(d, px + pw - 200, py + 28, "● GOOD", S, S["accent_dim"], S["ok"],
         f=font(20, bold=True, mono=S["mono"]))
    ry = py + 86
    for tag, label, value, conf in PDWCR:
        d.text((px + 26, ry), tag, font=font(24, bold=True, mono=S["mono"]), fill=S["accent"])
        d.text((px + 70, ry + 2), value, font=font(25, bold=True, mono=S["mono"]), fill=S["text"])
        conf_dot(d, px + pw - 46, ry + 6, conf, S)
        ry += 46
    d.line([px + 24, ry + 4, px + pw - 24, ry + 4], fill=S["border"], width=2)
    d.text((px + 26, ry + 18), "COUNTER", font=font(20, bold=True, mono=S["mono"]), fill=S["muted"])
    d.text((px + 26, ry + 44), "$2,100", font=font(46, bold=True, mono=S["mono"]), fill=S["accent"])
    d.text((px + 250, ry + 30), "floor $1,900", font=font(22, mono=S["mono"]), fill=S["muted"])
    d.text((px + 250, ry + 60), "accept 74%", font=font(22, mono=S["mono"]), fill=S["ok"])
    ry += 110
    who, line = TRANSCRIPT[-1]
    d.text((px + 26, ry), "Broker:", font=font(21, bold=True, mono=S["mono"]), fill=S["broker"])
    omax = 38 if S["mono"] else 44
    d.text((px + 26, ry + 28), line[:omax] + "…", font=font(22, mono=S["mono"]), fill=S["text"])
    ry += 76
    for i, lab in enumerate(["NOTES", "CALC", "EXPAND"]):
        bx0 = px + 26 + i * ((pw - 52) // 3 + 6)
        rrect(d, [bx0, ry, bx0 + (pw - 64) // 3, ry + 50], 12, fill=S["surface2"])
        d.text((bx0 + 22, ry + 13), lab, font=font(21, bold=True, mono=S["mono"]), fill=S["text"])


def render(key):
    S = STYLES[key]
    W = PAD * 3 + PHONE_W * 2
    H = HEADER_H + PHONE_H + PAD * 2
    img = Image.new("RGBA", (W, H), "#1E1E24")
    d = ImageDraw.Draw(img)
    d.text((PAD, 44), S["name"], font=font(44, bold=True), fill="#FFFFFF")
    d.text((PAD, 100), "Live Call Dashboard", font=font(26), fill="#9A9AA8")
    d.text((PAD * 2 + PHONE_W, 100), "Floating Copilot over RingCentral", font=font(26), fill="#9A9AA8")
    draw_dashboard(img, d, PAD, HEADER_H, S, key)
    draw_overlay_screen(img, d, PAD * 2 + PHONE_W, HEADER_H, S, key)
    out = f"concept-{key.lower()}.png"
    img.convert("RGB").save(out, optimize=True)
    print("wrote", out)


if __name__ == "__main__":
    for k in STYLES:
        render(k)
