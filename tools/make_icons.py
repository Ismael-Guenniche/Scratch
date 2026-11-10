"""Genera los iconos de Scratch: rueda de puntos (estilo TP-7 / Nothing).

Salidas:
  drawable/ic_iconN.png      vista previa del selector de Ajustes (256 px, fondo incluido)
  drawable/ic_launcher.png   icono base heredado (432 px)
  mipmap-nodpi/ic_fgN.png    capa frontal del icono adaptable (432 px, transparente)
  mipmap-nodpi/ic_monoN.png  capa monocroma (themed icons / paquete de iconos de Nothing)
  mipmap-xxxhdpi/ic_aN.png   icono heredado (API 24-25)
  values/ic_colors.xml       colores de fondo
  mipmap-anydpi-v26/ic_aN.xml icono adaptable (fondo + frontal + monocromo)
"""
import math, os, sys
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


def render(size, v, layer, k):
    """layer: 'full' (con fondo), 'fg' (transparente, colores) o 'mono' (transparente, un solo color)."""
    bg, dots, acc, hub, hole, mode = v
    S = 4
    N = size * S
    if layer == 'full':
        im = Image.new('RGBA', (N, N), bg + (255,))
    else:
        im = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = N / 2
    rings = [(0.15, 12), (0.25, 18), (0.35, 26), (0.45, 34)]
    if mode == 'rings2':
        rings = [(0.30, 20), (0.44, 32)]
    MONO = (0, 0, 0, 255)
    for ri, (rr, n) in enumerate(rings):
        R = rr * k * N
        dr = N * k * (0.012 + 0.0035 * ri) * (1.35 if mode == 'rings2' else 1) * 1.06
        for i in range(n):
            a = 2 * math.pi * i / n - math.pi / 2
            x, y = c + R * math.cos(a), c + R * math.sin(a)
            col = dots
            lit = False
            if mode == 'arc' and ri >= 1 and 0.0 <= (a + math.pi / 2) % (2 * math.pi) <= 1.05:
                col, lit = acc, True
            if mode == 'all' and ri == len(rings) - 1 and i < 5:
                col, lit = acc, True
            if mode == 'rings2' and ri == 1 and i < 7:
                col, lit = acc, True
            r = dr
            if layer == 'mono':
                col = MONO
                if lit:
                    r = dr * 1.45  # el "cabezal" sigue distinguiéndose sin color
            d.ellipse([x - r, y - r, x + r, y + r], fill=col)
    hr = N * k * (0.085 if mode != 'rings2' else 0.12) * 1.05
    ir = hr * 0.36
    if layer == 'mono':
        d.ellipse([c - hr, c - hr, c + hr, c + hr], fill=MONO)
        # agujero real (transparente) para que se lea como anillo
        hole_layer = Image.new('L', (N, N), 255)
        ImageDraw.Draw(hole_layer).ellipse([c - ir, c - ir, c + ir, c + ir], fill=0)
        a = im.split()[3]
        a = Image.composite(a, Image.new('L', (N, N), 0), hole_layer)
        im.putalpha(a)
    else:
        d.ellipse([c - hr, c - hr, c + hr, c + hr], fill=hub)
        d.ellipse([c - ir, c - ir, c + ir, c + ir], fill=hole)
    return im.resize((size, size), Image.LANCZOS)


def main(res):
    for sub in ('drawable', 'mipmap-nodpi', 'mipmap-xxxhdpi', 'mipmap-anydpi-v26', 'values'):
        os.makedirs(os.path.join(res, sub), exist_ok=True)
    colors = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
    for i, v in enumerate(VARIANTS, 1):
        render(256, v, 'full', 0.80).save('%s/drawable/ic_icon%d.png' % (res, i))
        # adaptable: la zona segura es el círculo central de 66 dp sobre 108 dp
        render(432, v, 'fg', 0.66).save('%s/mipmap-nodpi/ic_fg%d.png' % (res, i))
        render(432, v, 'mono', 0.66).save('%s/mipmap-nodpi/ic_mono%d.png' % (res, i))
        render(192, v, 'full', 0.80).save('%s/mipmap-xxxhdpi/ic_a%d.png' % (res, i))
        colors.append('    <color name="ic_bg%d">#%02X%02X%02X</color>' % ((i,) + v[0]))
        open('%s/mipmap-anydpi-v26/ic_a%d.xml' % (res, i), 'w').write(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@color/ic_bg%d" />\n'
            '    <foreground android:drawable="@mipmap/ic_fg%d" />\n'
            '    <monochrome android:drawable="@mipmap/ic_mono%d" />\n'
            '</adaptive-icon>\n' % (i, i, i))
    colors.append('</resources>')
    open('%s/values/ic_colors.xml' % res, 'w').write('\n'.join(colors) + '\n')
    render(432, VARIANTS[0], 'full', 0.80).save('%s/drawable/ic_launcher.png' % res)


main(sys.argv[1])
