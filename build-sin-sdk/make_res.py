"""Genera AndroidManifest.xml binario (AXML), resources.arsc mínimo e icono PNG,
sin aapt2. Uso: make_res.py <outdir>"""
import struct, sys, os, math
from PIL import Image, ImageDraw

OUT = sys.argv[1]
PKG = "dev.scratchwheel"
ANDROID_NS = "http://schemas.android.com/apk/res/android"

ATTR = {  # ids de android.R.attr
    "theme": 0x01010000, "label": 0x01010001, "icon": 0x01010002, "name": 0x01010003,
    "exported": 0x01010010, "screenOrientation": 0x0101001e, "minSdkVersion": 0x0101020c,
    "versionCode": 0x0101021b, "versionName": 0x0101021c, "targetSdkVersion": 0x01010270,
    "allowBackup": 0x01010280, "largeHeap": 0x0101035a,
}
THEME_MATERIAL_NOACTIONBAR = 16974390  # Theme.Material.Wallpaper.NoTitleBar
ICON_ID = 0x7F010000

T_REF, T_STR, T_INT, T_BOOL = 0x01, 0x03, 0x10, 0x12


def string_pool(strings):
    data = b""
    offsets = []
    for s in strings:
        offsets.append(len(data))
        enc = s.encode("utf-16-le")
        n = len(s)
        assert n < 0x8000
        data += struct.pack("<H", n) + enc + b"\x00\x00"
    while len(data) % 4:
        data += b"\x00"
    header_size = 28
    strings_start = header_size + 4 * len(strings)
    size = strings_start + len(data)
    hdr = struct.pack("<HHIIIIII", 0x0001, header_size, size, len(strings), 0, 0, strings_start, 0)
    return hdr + b"".join(struct.pack("<I", o) for o in offsets) + data


manifest = ("manifest", [
    (False, "package", T_STR, PKG),
    (True, "versionCode", T_INT, 5),
    (True, "versionName", T_STR, "0.5"),
], [
    ("uses-sdk", [(True, "minSdkVersion", T_INT, 24), (True, "targetSdkVersion", T_INT, 28)], []),
    ("uses-permission", [(True, "name", T_STR, "android.permission.FOREGROUND_SERVICE")], []),
    ("uses-permission", [(True, "name", T_STR, "android.permission.WAKE_LOCK")], []),
    ("application", [
        (True, "theme", T_REF, THEME_MATERIAL_NOACTIONBAR),
        (True, "label", T_STR, "Scratch"),
        (True, "icon", T_REF, ICON_ID),
        (True, "allowBackup", T_BOOL, False),
        (True, "largeHeap", T_BOOL, True),
    ], [
        ("activity", [
            (True, "name", T_STR, PKG + ".MainActivity"),
            (True, "exported", T_BOOL, True),
            (True, "screenOrientation", T_INT, 1),
        ], [
            ("intent-filter", [], [
                ("action", [(True, "name", T_STR, "android.intent.action.MAIN")], []),
                ("category", [(True, "name", T_STR, "android.intent.category.LAUNCHER")], []),
            ]),
        ]),
        ("activity", [
            (True, "name", T_STR, PKG + ".ScratchActivity"),
            (True, "exported", T_BOOL, False),
            (True, "screenOrientation", T_INT, 1),
        ], []),
        ("activity", [
            (True, "name", T_STR, PKG + ".QueueActivity"),
            (True, "exported", T_BOOL, False),
            (True, "screenOrientation", T_INT, 1),
        ], []),
        ("activity", [
            (True, "name", T_STR, PKG + ".SettingsActivity"),
            (True, "exported", T_BOOL, False),
            (True, "screenOrientation", T_INT, 1),
        ], []),
        ("service", [
            (True, "name", T_STR, PKG + ".PlayerService"),
            (True, "exported", T_BOOL, False),
        ], []),
    ]),
])


def walk(node, fn):
    fn(node)
    for c in node[2]:
        walk(c, fn)


attr_names = []


def collect_attrs(n):
    for ns, name, t, v in n[1]:
        if ns and name not in attr_names:
            attr_names.append(name)


walk(manifest, collect_attrs)
attr_names.sort(key=lambda a: ATTR[a])
pool = list(attr_names)


def add(s):
    if s not in pool:
        pool.append(s)
    return pool.index(s)


add("android")
add(ANDROID_NS)


def collect_rest(n):
    add(n[0])
    for ns, name, t, v in n[1]:
        add(name)
        if t == T_STR:
            add(v)


walk(manifest, collect_rest)
idx = pool.index
line = [1]


def start_ns():
    return struct.pack("<HHIIIII", 0x0100, 16, 24, 1, 0xFFFFFFFF, idx("android"), idx(ANDROID_NS))


def end_ns():
    return struct.pack("<HHIIIII", 0x0101, 16, 24, 1, 0xFFFFFFFF, idx("android"), idx(ANDROID_NS))


def element(n):
    tag, attrs, kids = n
    attrs = sorted(attrs, key=lambda a: (ATTR.get(a[1], 0xFFFFFFFF) if a[0] else 0xFFFFFFFF))
    ab = b""
    for ns, name, t, v in attrs:
        ns_i = idx(ANDROID_NS) if ns else 0xFFFFFFFF
        if t == T_STR:
            raw, data = idx(v), idx(v)
        elif t == T_BOOL:
            raw, data = 0xFFFFFFFF, (0xFFFFFFFF if v else 0)
        else:
            raw, data = 0xFFFFFFFF, v
        ab += struct.pack("<IIIHBBI", ns_i, idx(name), raw, 8, 0, t, data)
    l = line[0]
    line[0] += 1
    body = struct.pack("<IIHHHHHH", 0xFFFFFFFF, idx(tag), 20, 20, len(attrs), 0, 0, 0)
    out = struct.pack("<HHIII", 0x0102, 16, 16 + len(body) + len(ab), l, 0xFFFFFFFF) + body + ab
    for k in kids:
        out += element(k)
    out += struct.pack("<HHIIIII", 0x0103, 16, 24, line[0], 0xFFFFFFFF, 0xFFFFFFFF, idx(tag))
    line[0] += 1
    return out


sp = string_pool(pool)
rmap = struct.pack("<HHI", 0x0180, 8, 8 + 4 * len(attr_names)) + b"".join(struct.pack("<I", ATTR[a]) for a in attr_names)
body = sp + rmap + start_ns() + element(manifest) + end_ns()
axml = struct.pack("<HHI", 0x0003, 8, 8 + len(body)) + body
open(os.path.join(OUT, "AndroidManifest.xml"), "wb").write(axml)

# ---------------- resources.arsc ----------------
ICON_PATH = "res/drawable/ic_launcher.png"
gpool = string_pool([ICON_PATH])
type_pool = string_pool(["drawable"])
key_pool = string_pool(["ic_launcher"])

type_spec = struct.pack("<HHIBBHI", 0x0202, 16, 16 + 4, 1, 0, 0, 1) + struct.pack("<I", 0)
config = struct.pack("<I", 64) + b"\x00" * 60
type_hdr_size = 8 + 4 + 4 + 4 + len(config)
entries = struct.pack("<HHI", 8, 0, 0) + struct.pack("<HBBI", 8, 0, T_STR, 0)
entries_start = type_hdr_size + 4
type_chunk = struct.pack("<HHIBBHII", 0x0201, type_hdr_size, entries_start + len(entries),
                         1, 0, 0, 1, entries_start) + config + struct.pack("<I", 0) + entries

pkg_hdr_size = 288
name = PKG.encode("utf-16-le").ljust(256, b"\x00")
type_off = pkg_hdr_size
key_off = type_off + len(type_pool)
pkg_body = type_pool + key_pool + type_spec + type_chunk
pkg = struct.pack("<HHII", 0x0200, pkg_hdr_size, pkg_hdr_size + len(pkg_body), 0x7F) + name + \
      struct.pack("<IIIII", type_off, 1, key_off, 1, 0) + pkg_body
arsc_body = gpool + pkg
arsc = struct.pack("<HHII", 0x0002, 12, 12 + len(arsc_body), 1) + arsc_body
open(os.path.join(OUT, "resources.arsc"), "wb").write(arsc)

# ---------------- icono 192x192: plato de puntos negro con marca roja ----------------
S = 192 * 4
base = Image.new("RGBA", (S, S), (0, 0, 0, 255))
d = ImageDraw.Draw(base)
cx = cy = S / 2
for k in range(5):
    rad = S * (0.16 + 0.085 * k)
    n = max(6, int(2 * math.pi * rad / (S * 0.075)))
    for i in range(n):
        a = -math.pi / 2 + i * 2 * math.pi / n + (k % 2) * math.pi / n
        x, y = cx + math.cos(a) * rad, cy + math.sin(a) * rad
        d.ellipse([x - 7, y - 7, x + 7, y + 7], fill=(255, 255, 255, 235))
mx, my = cx, cy - S * 0.40
d.ellipse([mx - 20, my - 20, mx + 20, my + 20], fill=(215, 25, 33, 255))
d.ellipse([cx - 12, cy - 12, cx + 12, cy + 12], fill=(215, 25, 33, 255))
mask = Image.new("L", (S, S), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, S, S], radius=S * 0.22, fill=255)
out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
out.paste(base, (0, 0), mask)
out = out.resize((192, 192), Image.LANCZOS)
os.makedirs(os.path.join(OUT, "res/drawable"), exist_ok=True)
out.save(os.path.join(OUT, ICON_PATH), optimize=True)
print("ok", len(axml), len(arsc))
