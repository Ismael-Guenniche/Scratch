"""Genera los iconos de Scratch: rueda de puntos (estilo TP-7 / Nothing)."""
import math, sys
from PIL import Image, ImageDraw

OR = (251, 71, 31)
BK = (10, 10, 11)
GR = (28, 28, 30)
WH = (245, 245, 247)
BONE = (236, 232, 224)

# (fondo, puntos, arco/acento, hub, hub-centro, modo)
VARIANTS = [
    (BK, (120, 120, 126), OR, OR, BK, 'arc'),
    (OR, (10, 10, 11), WH, BK, OR, 'arc'),
    (BONE, (60, 60, 64), OR, OR, BONE, 'arc'),
    (BK, OR, WH, WH, BK, 'all'),
    (GR, WH, OR, OR, GR, 'arc'),
    (BK, WH, OR, WH, BK, 'rings2'),
    (OR, WH, BK, WH, OR, 'all'),
    (BONE, OR, BK, BK, BONE, 'all'),
]

def draw(size, v):
    bg, dots, acc, hub, hole, mode = v
    S = 4
    N = size * S
    im = Image.new('RGB', (N, N), bg)
    d = ImageDraw.Draw(im)
    c = N / 2
    rings = [(0.15, 12), (0.25, 18), (0.35, 26), (0.45, 34)]
    if mode == 'rings2':
        rings = [(0.30, 20), (0.44, 32)]
    for ri, (rr, n) in enumerate(rings):
        R = rr * 0.80 * N
        dr = N * 0.85 * (0.012 + 0.0035 * ri) * (1.35 if mode == 'rings2' else 1)
        for i in range(n):
            a = 2 * math.pi * i / n - math.pi / 2
            x, y = c + R * math.cos(a), c + R * math.sin(a)
            col = dots
            if mode == 'arc' and ri >= 1 and 0.0 <= (a + math.pi / 2) % (2 * math.pi) <= 1.05:
                col = acc
            if mode == 'all' and ri == len(rings) - 1 and i < 5:
                col = acc
            if mode == 'rings2' and ri == 1 and i < 7:
                col = acc
            d.ellipse([x - dr, y - dr, x + dr, y + dr], fill=col)
    hr = N * 0.8 * (0.085 if mode != 'rings2' else 0.12)
    d.ellipse([c - hr, c - hr, c + hr, c + hr], fill=hub)
    ir = hr * 0.36
    d.ellipse([c - ir, c - ir, c + ir, c + ir], fill=hole)
    return im.resize((size, size), Image.LANCZOS)

out = sys.argv[1]
for i, v in enumerate(VARIANTS, 1):
    draw(256, v).convert('RGBA').save('%s/ic_icon%d.png' % (out, i))
draw(432, VARIANTS[0]).convert('RGBA').save('%s/ic_launcher.png' % out)
