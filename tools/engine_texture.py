# Generates the animated LED engine texture from the mod font.
# Usage: python3 tools/engine_texture.py src/main/java/de/knito/billboard/wall/BillboardFont.java src/main/resources/assets/billboard/textures/block
# Needs Pillow (pip install pillow).
import re, sys
from PIL import Image
src = open(sys.argv[1]).read()
out = sys.argv[2]
g = {}
for m in re.finditer(r"glyph\('(\\\\|\\'|.)', (.*?)\);", src):
    c = m.group(1); c = {'\\\\': '\\', "\\'": "'"}.get(c, c)
    g[c] = [int(x, 16) for x in m.group(2).split(', ')]

TEXT = "BILLBOARD  "
cols = []
for ch in TEXT:
    cols += g[ch] + [0]
N = len(cols)

FRAME_D = (58, 60, 66); FRAME_L = (120, 124, 132); FRAME_M = (88, 92, 100)
BG = (14, 12, 12)
LED_OFF = (52, 26, 18); LED_OFF_HI = (66, 34, 24)
LED_ON = (255, 168, 24); LED_ON_HI = (255, 236, 150); LED_ON_LO = (200, 104, 10)

def frame_border(p):
    for i in range(16):
        for j in range(16):
            if i == 0 or j == 0:
                p[i, j] = FRAME_L
            elif i == 15 or j == 15:
                p[i, j] = FRAME_D
            else:
                p[i, j] = BG
    for (x, y) in [(0, 0), (15, 0), (0, 15), (15, 15)]:
        p[x, y] = FRAME_M

def side_frame(offset):
    im = Image.new("RGB", (16, 16)); p = im.load()
    frame_border(p)
    # 7x7 LEDs, 2px pitch, inside x/y 1..14
    for c in range(7):
        col = cols[(offset + c) % N]
        for r in range(7):
            on = (col >> r) & 1
            x, y = 1 + c * 2, 1 + r * 2
            if on:
                p[x, y] = LED_ON_HI; p[x + 1, y] = LED_ON; p[x, y + 1] = LED_ON; p[x + 1, y + 1] = LED_ON_LO
            else:
                p[x, y] = LED_OFF_HI; p[x + 1, y] = LED_OFF; p[x, y + 1] = LED_OFF; p[x + 1, y + 1] = BG
    return im

strip = Image.new("RGB", (16, 16 * N))
for f in range(N):
    strip.paste(side_frame(f), (0, 16 * f))
strip.save(out + "/billboard_engine_side.png")

# top/bottom: steel plate, rivets, vent slots
im = Image.new("RGB", (16, 16)); p = im.load()
for i in range(16):
    for j in range(16):
        base = (96, 100, 108) if (i + j) % 7 else (102, 106, 114)
        p[i, j] = base
for i in range(16):
    p[i, 0] = FRAME_L; p[0, i] = FRAME_L; p[i, 15] = FRAME_D; p[15, i] = FRAME_D
for (x, y) in [(2, 2), (13, 2), (2, 13), (13, 13)]:
    p[x, y] = (150, 154, 162); p[x + 1, y + 1] = (60, 62, 68)
for y in (5, 7, 9, 11):
    for x in range(4, 12):
        p[x, y] = (30, 30, 34)
    p[4, y + 1] = (130, 134, 142)
    for x in range(5, 12):
        p[x, y + 1] = (120, 124, 132)
im.save(out + "/billboard_engine_top.png")
print(N)
