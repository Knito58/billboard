# Generates the Script Box textures: a cardboard box with a paper label of code lines and one small LED.
# Usage: python3 tools/script_box_texture.py src/main/resources/assets/billboard/textures/block [preview.png]
# Needs Pillow (pip install pillow).
import json, random, sys
from PIL import Image

out = sys.argv[1]

CARD = (198, 160, 110); CARD_L = (212, 176, 126); CARD_D = (184, 146, 98)
FOLD = (120, 82, 46); FOLD_D = (92, 60, 32); FOLD_L = (222, 190, 142)
PAPER = (238, 232, 214); PAPER_D = (214, 206, 186)
INK = (70, 70, 78); INK_KEY = (60, 90, 150)
LED_RIM = (70, 52, 34)
LEDS = {
    "dark": ((58, 50, 42), (44, 38, 32)),
    "green": ((150, 255, 140), (40, 200, 50)),
    "red": ((255, 150, 130), (220, 40, 30)),
}

def cardboard(p, seed):
    # Light brown with faint vertical fibres, the way corrugated board looks.
    rnd = random.Random(seed)
    for x in range(16):
        stripe = CARD_L if x % 4 == 1 else CARD
        for y in range(16):
            n = rnd.random()
            p[x, y] = CARD_D if n < 0.10 else CARD_L if n > 0.93 else stripe

def edges(p):
    # Dark brown folds where the box bends round its edges.
    for i in range(16):
        p[i, 0] = FOLD; p[0, i] = FOLD; p[i, 15] = FOLD_D; p[15, i] = FOLD_D

def side(led):
    im = Image.new("RGB", (16, 16)); p = im.load()
    cardboard(p, 1)
    edges(p)
    # Paper label, x 3..12, y 3..10, with a little shadow.
    for x in range(3, 13):
        for y in range(3, 11):
            p[x, y] = PAPER
    for x in range(4, 14):
        p[x, 11] = CARD_D
    for y in range(4, 12):
        p[13, y] = CARD_D
    # Code lines: a keyword, indented lines, an end.
    lines = [(4, 3, INK_KEY), (6, 5, INK), (6, 4, INK), (4, 2, INK_KEY)]
    for row, (x0, length, color) in enumerate(lines):
        y = 4 + row * 2
        for x in range(x0, x0 + length):
            p[x, y] = color
    # The LED below the label, bottom right, in a dark rim.
    hi, lo = LEDS[led]
    for x in (11, 12):
        p[x, 13] = LED_RIM if led == "dark" else hi if x == 11 else lo
    p[11, 13] = hi; p[12, 13] = lo
    p[10, 13] = LED_RIM; p[13, 13] = LED_RIM
    return im

def top():
    im = Image.new("RGB", (16, 16)); p = im.load()
    cardboard(p, 2)
    edges(p)
    # The two flaps meet in the middle; a strip of tape runs across the seam.
    for x in range(1, 15):
        p[x, 7] = FOLD_D; p[x, 8] = FOLD_L
    for y in range(1, 15):
        for x in (6, 7, 8, 9):
            if y not in (7, 8):
                p[x, y] = (214, 186, 140) if x in (6, 9) else (222, 196, 150)
    return im

def bottom():
    im = Image.new("RGB", (16, 16)); p = im.load()
    cardboard(p, 3)
    edges(p)
    for x in range(1, 15):
        p[x, 7] = FOLD_D; p[x, 8] = FOLD_L
    return im

side("dark").save(out + "/script_box_side.png")
side("red").save(out + "/script_box_side_error.png")
# Running: the LED flickers like a busy disk light.
pattern = [1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 1, 1, 1, 1, 0, 1]
frames = [side("dark"), side("green")]
strip = Image.new("RGB", (16, 32))
strip.paste(frames[0], (0, 0)); strip.paste(frames[1], (0, 16))
strip.save(out + "/script_box_side_running.png")
with open(out + "/script_box_side_running.png.mcmeta", "w") as f:
    json.dump({"animation": {"frametime": 3, "frames": pattern}}, f)
    f.write("\n")
top().save(out + "/script_box_top.png")
bottom().save(out + "/script_box_bottom.png")

if len(sys.argv) > 2:
    # A preview sheet: the box in its three states as a simple iso cube, plus the faces large.
    S = 8
    def face(img, origin, X, Y, f):
        # Paints a 16x16 face where texture x runs along X and texture y along Y (in output pixels per texel).
        a, b, c, d = X[0], Y[0], X[1], Y[1]
        det = a * d - b * c
        inv = (d / det, -b / det, -c / det, a / det)
        ox, oy = origin
        coeffs = (inv[0], inv[1], -(inv[0] * ox + inv[1] * oy), inv[2], inv[3], -(inv[2] * ox + inv[3] * oy))
        shaded = img.point(lambda v: int(v * f)).convert("RGBA")
        return shaded.transform((32 * S, 32 * S), Image.AFFINE, coeffs, Image.NEAREST, fillcolor=(0, 0, 0, 0))
    def iso(sideimg, topimg):
        im = Image.new("RGBA", (32 * S, 32 * S), (0, 0, 0, 0))
        im.alpha_composite(face(topimg, (16 * S, 0), (S, S / 2), (-S, S / 2), 1.0))
        im.alpha_composite(face(sideimg, (0, 8 * S), (S, S / 2), (0, S), 0.85))
        im.alpha_composite(face(sideimg, (16 * S, 16 * S), (S, -S / 2), (0, S), 0.7))
        return im
    sheet = Image.new("RGBA", (5 * (16 * 12 + 30) + 50, 32 * S + 16 * 12 + 120), (40, 44, 52, 255))
    for i, s in enumerate([side("dark"), side("green"), side("red")]):
        sheet.alpha_composite(iso(s, top()), (40 + i * (32 * S + 40), 20))
    for i, face in enumerate([side("dark"), side("green"), side("red"), top(), bottom()]):
        sheet.paste(face.resize((16 * 12, 16 * 12), Image.NEAREST), (40 + i * (16 * 12 + 30), 32 * S + 60))
    sheet.save(sys.argv[2])
