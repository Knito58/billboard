# Generates the mod icon (LED dot-matrix "B" on a round disc) from the mod font.
# Usage: python3 tools/icon.py src/main/java/de/knito/billboard/wall/BillboardFont.java <out.png> <size> [ring px, default 1]
# Needs Pillow (pip install pillow).
import re, sys
from PIL import Image, ImageChops, ImageDraw, ImageFilter
src = open(sys.argv[1]).read()
out, size = sys.argv[2], int(sys.argv[3])
m = re.search(r"glyph\('B', (.*?)\);", src)
cols = [int(x, 16) for x in m.group(1).split(', ')]

K = 4                             # draw 4x larger, scale down for smooth circles
S = K * size
N = 9                             # 9 x 9 LED grid, the 5x7 letter centered
DISC = (22, 46, 84)               # deep blue
RING = (255, 255, 255)
LED_OFF = (40, 62, 98); LED_ON = (255, 168, 24); LED_ON_HI = (255, 236, 150)

ring = K * (int(sys.argv[4]) if len(sys.argv) > 4 else 1)  # white ring, in final pixels
c = S / 2
R = c - ring                      # disc radius inside the white ring
pitch = 2 * R * 0.9 / N
r = pitch * 0.36
x0 = c - N * pitch / 2

on = set()
for cx in range(5):
    for cy in range(7):
        if (cols[cx] >> cy) & 1:
            on.add((cx + 2, cy + 1))

def centre(cx, cy):
    return x0 + (cx + 0.5) * pitch, x0 + (cy + 0.5) * pitch

im = Image.new("RGB", (S, S), DISC)
glow = Image.new("RGB", (S, S), (0, 0, 0))
gd = ImageDraw.Draw(glow)
for (cx, cy) in on:
    x, y = centre(cx, cy)
    gd.ellipse([x - r * 1.6, y - r * 1.6, x + r * 1.6, y + r * 1.6], fill=(150, 70, 0))
im = ImageChops.add(im, glow.filter(ImageFilter.GaussianBlur(pitch * 0.35)))

d = ImageDraw.Draw(im)
for cx in range(N):
    for cy in range(N):
        x, y = centre(cx, cy)
        if (cx, cy) in on:
            d.ellipse([x - r, y - r, x + r, y + r], fill=LED_ON)
            h = r * 0.45
            d.ellipse([x - r * 0.65, y - r * 0.65, x - r * 0.55 + h, y - r * 0.55 + h], fill=LED_ON_HI)
        elif (x - c) ** 2 + (y - c) ** 2 < (R - r * 1.4) ** 2:
            d.ellipse([x - r, y - r, x + r, y + r], fill=LED_OFF)

# white ring outside the disc, transparent beyond it
d.ellipse([0, 0, S - 1, S - 1], outline=RING, width=ring)
mask = Image.new("L", (S, S), 0)
ImageDraw.Draw(mask).ellipse([0, 0, S - 1, S - 1], fill=255)
im = im.convert("RGBA")
im.putalpha(mask)
im.resize((size, size), Image.LANCZOS).save(out)
