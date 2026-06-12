#!/usr/bin/env python3
"""Generates the truck launcher icon for Dispatcher Companion.

Outputs:
  - legacy mipmap PNGs (navy rounded square + orange semi truck)
  - adaptive-icon foreground PNGs (truck only, transparent, in the 66% safe zone)
Run from dispatcher-companion/: python3 scripts/generate_icon.py
"""
from PIL import Image, ImageDraw

NAVY = "#1B2A4A"
ORANGE = "#F97316"
ORANGE_DARK = "#C2570C"
WHITE = "#FFFFFF"
STEEL = "#9FB0D0"


def draw_truck(d: ImageDraw.ImageDraw, x0, y0, w):
    """Semi truck (cab + trailer), side view facing right, width w, anchored at (x0, y0) top-left."""
    s = w / 100.0  # all coords in a 100x62 design grid
    def box(a, b, c, e, fill, r=0):
        d.rounded_rectangle([x0 + a * s, y0 + b * s, x0 + c * s, y0 + e * s], radius=r * s, fill=fill)

    # trailer
    box(0, 4, 62, 38, WHITE, r=3)
    # cab body
    box(64, 14, 96, 46, ORANGE, r=4)
    # cab hood (lower front)
    box(82, 26, 100, 46, ORANGE, r=4)
    # windshield
    box(68, 18, 80, 28, STEEL, r=2)
    # bumper
    box(94, 40, 100, 46, STEEL, r=2)
    # chassis line
    box(0, 38, 96, 44, ORANGE_DARK, r=2)

    def wheel(cx, cy, r):
        d.ellipse([x0 + (cx - r) * s, y0 + (cy - r) * s, x0 + (cx + r) * s, y0 + (cy + r) * s],
                  fill="#0E1626")
        hub = r * 0.45
        d.ellipse([x0 + (cx - hub) * s, y0 + (cy - hub) * s, x0 + (cx + hub) * s, y0 + (cy + hub) * s],
                  fill=STEEL)

    for cx in (12, 26, 74, 90):
        wheel(cx, 50, 9)


def legacy_icon(size: int) -> Image.Image:
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    m = size * 0.04
    d.rounded_rectangle([m, m, size - m, size - m], radius=size * 0.20, fill=NAVY)
    tw = size * 0.74
    draw_truck(d, (size - tw) / 2, size * 0.30, tw)
    return img


def foreground_icon(size: int) -> Image.Image:
    """Adaptive foreground: transparent canvas, truck inside the central 56%."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    tw = size * 0.56
    draw_truck(d, (size - tw) / 2, size * 0.34, tw)
    return img


DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}

if __name__ == "__main__":
    import os
    res = "app/src/main/res"
    for name, scale in DENSITIES.items():
        folder = f"{res}/mipmap-{name}"
        os.makedirs(folder, exist_ok=True)
        legacy_icon(int(48 * scale)).save(f"{folder}/ic_launcher.png")
        foreground_icon(int(108 * scale)).save(f"{folder}/ic_launcher_foreground.png")
        print(f"{folder}: legacy {int(48*scale)}px, foreground {int(108*scale)}px")
    # preview for review
    legacy_icon(512).save("/tmp/icon_preview.png")
