#!/usr/bin/env python3
"""Generate the 32x32 graphite-and-signal texture set for NeroSecurity.

Ported from NeroTech's tools/gen_textures.py (same primitives: rng_for, new_img, noise_fill, 2px bevel,
rivets, recess, led, haz_band, additive-only save with --force). Differences:

* No Pillow. PNGs are written (and read back by --check) with the standard library only (zlib + struct),
  so the generator and the CI check run on any Python 3.8+ with nothing installed.
* One script also writes the .png.mcmeta files for the animated strips (screens 8 frames, fields 16).
* Deterministic: every painter seeds its RNG from the texture name, so re-runs are byte-identical.
* ADDITIVE-ONLY: save() skips any PNG that already exists; pass --force to replace the set (the one-shot
  initial run). Afterwards additive-only protects hand edits.

All textures produced here are procedurally generated; none are hand-painted. They are readable and
consistent but not final art.

usage: python tools/gen_security_textures.py [--force] [--only NAME[,NAME...]] [--check]
  --force   overwrite existing PNGs
  --only    paint a subset (names as in MANIFEST, without folder)
  --check   paint nothing; verify every texture in MANIFEST exists, is 32 px wide, has >= 6 distinct
            colours and, for animated names, has height = 32 x frames and a sibling .mcmeta (static
            names: height 32). Exit 1 on any miss. Also lists PNGs no MANIFEST entry owns (dead files).
"""
import hashlib
import json
import math
import os
import random
import struct
import sys
import zlib

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from nerosecurity_target import src_base, target_label  # noqa: E402

ROOT = src_base()
TEX_ROOT = os.path.join(ROOT, "src/main/resources/assets/nerosecurity/textures")
DIRS = {"block": os.path.join(TEX_ROOT, "block"), "item": os.path.join(TEX_ROOT, "item")}

S = 32  # texture size
MIN_COLOURS = 6

# ---- Palette. Mirrors client/SecurityColours + AccessLevel.colour byte-for-byte; a unit test parses this
# ---- dict (one "NAME": "#RRGGBB" entry per line) and compares it with the Java constants.
PALETTE = {
    "G_DARK": "#1A1D22",
    "GRAPHITE": "#2C3137",
    "GRAPHITE_2": "#33393F",
    "GRAPHITE_3": "#262B30",
    "GRAPHITE_4": "#3A4047",
    "G_LIGHT": "#8E98A4",
    "TRIM": "#5B6670",
    "HAZ_Y": "#FFCE2C",
    "HAZ_K": "#18181E",
    "GLASS": "#0B1119",
    "INK_CYAN": "#7FD7FF",
    "REC": "#FF2020",
    "LOCK": "#E04545",
    "BEAM": "#FF3B3B",
    "BEAM_GLOW": "#FF8A8A",
    "FIELD": "#3FA9F5",
    "FIELD_HEX": "#9ADCFF",
    "FIELD_LOCK_HEX": "#FF9A9A",
    "ENC": "#B07CFF",
    "L_PUBLIC": "#A0A0A0",
    "L_MEMBER": "#55D55A",
    "L_OFFICER": "#E0A526",
    "L_OWNER": "#E04545",
    "L_ADMIN": "#FFFFFF",
    "OK": "#55D55A",
    "WARN": "#E0A526",
    "OFF": "#6A7280",
    "GOLD": "#D9B24C",
}


def _rgba(hexstr, a=255):
    h = hexstr.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


P = {k: _rgba(v) for k, v in PALETTE.items()}
CLEAR = (0, 0, 0, 0)
G_DARK = P["G_DARK"]
GRAPHITE = [P["GRAPHITE"], P["GRAPHITE_2"], P["GRAPHITE_3"], P["GRAPHITE_4"]]
G_LIGHT = P["G_LIGHT"]
TRIM = P["TRIM"]
HAZ_Y = P["HAZ_Y"]
HAZ_K = P["HAZ_K"]
GLASS = P["GLASS"]
INK_CYAN = P["INK_CYAN"]
REC = P["REC"]
LOCK = P["LOCK"]
FIELD = P["FIELD"]
FIELD_HEX = P["FIELD_HEX"]
FIELD_LOCK_HEX = P["FIELD_LOCK_HEX"]
ENC = P["ENC"]
GOLD = P["GOLD"]
WHITE = (255, 255, 255, 255)
CARD = _rgba("#E8ECF0")
REC_OFF = _rgba("#5A1414")


# ---------------- tiny image + PNG codec (stdlib only) ----------------

class Img:
    """RGBA image with Pillow-style px[x, y] access (out-of-range writes are ignored)."""

    def __init__(self, w=S, h=S, fill=CLEAR):
        self.w = w
        self.h = h
        self.data = [fill] * (w * h)

    def load(self):
        return self

    def __getitem__(self, xy):
        x, y = xy
        return self.data[y * self.w + x]

    def __setitem__(self, xy, col):
        x, y = xy
        if 0 <= x < self.w and 0 <= y < self.h:
            self.data[y * self.w + x] = tuple(col)

    def inside(self, x, y):
        return 0 <= x < self.w and 0 <= y < self.h

    def paste(self, other, ox, oy):
        for y in range(other.h):
            for x in range(other.w):
                self[ox + x, oy + y] = other[x, y]


def png_bytes(img):
    raw = bytearray()
    for y in range(img.h):
        raw.append(0)
        for x in range(img.w):
            raw.extend(bytes(img[x, y]))

    def chunk(kind, payload):
        c = struct.pack(">I", len(payload)) + kind + payload
        return c + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", img.w, img.h, 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


def read_png(path):
    """Decode an 8-bit non-interlaced PNG to (w, h, list of RGBA tuples). Raises ValueError otherwise."""
    with open(path, "rb") as f:
        data = f.read()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG")
    pos = 8
    idat = bytearray()
    plte = None
    trns = None
    w = h = depth = ctype = interlace = None
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        kind = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            w, h, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", body)
        elif kind == b"PLTE":
            plte = [tuple(body[i:i + 3]) for i in range(0, len(body), 3)]
        elif kind == b"tRNS":
            trns = body
        elif kind == b"IDAT":
            idat.extend(body)
        elif kind == b"IEND":
            break
    if depth != 8 or interlace != 0:
        raise ValueError("unsupported PNG (bit depth %s, interlace %s)" % (depth, interlace))
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    raw = zlib.decompress(bytes(idat))
    stride = w * channels
    out = []
    prev = bytearray(stride)
    i = 0
    for _ in range(h):
        ft = raw[i]
        i += 1
        line = bytearray(raw[i:i + stride])
        i += stride
        for x in range(stride):
            a = line[x - channels] if x >= channels else 0
            b = prev[x]
            c = prev[x - channels] if x >= channels else 0
            if ft == 1:
                line[x] = (line[x] + a) & 0xFF
            elif ft == 2:
                line[x] = (line[x] + b) & 0xFF
            elif ft == 3:
                line[x] = (line[x] + ((a + b) >> 1)) & 0xFF
            elif ft == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[x] = (line[x] + pr) & 0xFF
        for x in range(w):
            px = line[x * channels:(x + 1) * channels]
            if ctype == 6:
                out.append(tuple(px))
            elif ctype == 2:
                out.append((px[0], px[1], px[2], 255))
            elif ctype == 4:
                out.append((px[0], px[0], px[0], px[1]))
            elif ctype == 0:
                out.append((px[0], px[0], px[0], 255))
            else:
                idx = px[0]
                r, g, bb = plte[idx]
                alpha = trns[idx] if trns is not None and idx < len(trns) else 255
                out.append((r, g, bb, alpha))
        prev = line
    return w, h, out


# ---------------- helpers (NeroTech primitives) ----------------

def rng_for(name):
    """Deterministic per-name seed: stable across runs and machines."""
    return random.Random(int(hashlib.md5(name.encode()).hexdigest(), 16) & 0xffffffff)


def new_img():
    return Img(S, S, CLEAR)


def _mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def _bright(c, f):
    """Scale a colour's brightness by f (f > 1 brightens toward white)."""
    if f >= 1.0:
        return _mix(c, WHITE, min(1.0, f - 1.0))
    return tuple(int(round(c[i] * f)) for i in range(3)) + (c[3],)


def _alpha(c, a):
    return (c[0], c[1], c[2], int(round(max(0, min(255, a)))))


def blend(px, x, y, col, alpha):
    """Alpha-blend col over the existing pixel (alpha 0..1); keeps the destination alpha (max of both)."""
    if not px.inside(x, y):
        return
    d = px[x, y]
    a = max(0.0, min(1.0, alpha))
    if d[3] == 0:
        px[x, y] = (col[0], col[1], col[2], int(round(255 * a)))
        return
    r = tuple(int(round(d[i] + (col[i] - d[i]) * a)) for i in range(3))
    px[x, y] = r + (max(d[3], int(round(255 * a))),)


def noise_fill(img, palette, rng):
    px = img.load()
    for y in range(img.h):
        for x in range(img.w):
            px[x, y] = rng.choice(palette)
    return img


def bevel(img, light=G_LIGHT, dark=G_DARK):
    """2px bevel: highlight top/left, shadow bottom/right."""
    px = img.load()
    light2 = _mix(light, GRAPHITE[0], 0.45)
    dark2 = _mix(dark, GRAPHITE[0], 0.35)
    for i in range(S):
        px[i, 0] = light
        px[0, i] = light
        px[i, S - 1] = dark
        px[S - 1, i] = dark
    for i in range(1, S - 1):
        px[i, 1] = light2
        px[1, i] = light2
        px[i, S - 2] = dark2
        px[S - 2, i] = dark2


def rivets(img, pts=((4, 4), (26, 4), (4, 26), (26, 26)), col=TRIM):
    """2x2 rivets: lit top-left, shadowed bottom-right."""
    px = img.load()
    for (rx, ry) in pts:
        px[rx, ry] = G_LIGHT
        px[rx + 1, ry] = col
        px[rx, ry + 1] = col
        px[rx + 1, ry + 1] = G_DARK


def recess(px, x0, y0, x1, y1, fill=GLASS):
    """Sunken dark panel: fill + inner shadow (top/left) + catch-light on the lower lip."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px[x, y] = fill
    shadow = _bright(fill, 0.55)
    for x in range(x0, x1 + 1):
        px[x, y0] = shadow
    for y in range(y0, y1 + 1):
        px[x0, y] = shadow
    for x in range(x0 + 1, x1 + 1):
        px[x, y1] = _mix(G_LIGHT, G_DARK, 0.55)


def led(px, x, y, col=REC, core=None):
    """4x4 dark socket with a 2x2 lit core at (x, y)."""
    for yy in range(y - 1, y + 3):
        for xx in range(x - 1, x + 3):
            px[xx, yy] = G_DARK
    for yy in range(y, y + 2):
        for xx in range(x, x + 2):
            px[xx, yy] = col
    if core:
        px[x, y] = core


def haz_band(px, y0, y1, x0=0, x1=S - 1, chevron=True):
    """Hazard striping (doors only). chevron=True mirrors the stripes about the vertical centre (45 deg V)."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            k = (x + y) if (not chevron or x < S // 2) else ((S - 1 - x) + y)
            px[x, y] = HAZ_Y if (k // 3) % 2 == 0 else HAZ_K


def brushed(img, rng, every=6):
    """Horizontal brushed lines every `every` px at 12% G_LIGHT (with a little jitter in length)."""
    px = img.load()
    for y in range(3, S - 2, every):
        x0 = 2 + rng.randint(0, 3)
        x1 = S - 3 - rng.randint(0, 3)
        for x in range(x0, x1):
            blend(px, x, y, G_LIGHT, 0.12)


def circle(px, cx, cy, r, col, ring=None):
    """Filled disc (ring=None) or ring of thickness `ring` about (cx, cy), radius r (pixel centres)."""
    for y in range(int(cy - r - 2), int(cy + r + 3)):
        for x in range(int(cx - r - 2), int(cx + r + 3)):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if ring is None:
                if d <= r:
                    px[x, y] = col
            elif r - ring < d <= r:
                px[x, y] = col


def line(px, x0, y0, x1, y1, col, width=1):
    n = int(max(abs(x1 - x0), abs(y1 - y0))) * 2 + 1
    for i in range(n + 1):
        t = i / float(n)
        x = x0 + (x1 - x0) * t
        y = y0 + (y1 - y0) * t
        for dx in range(width):
            for dy in range(width):
                px[int(round(x)) + dx - width // 2, int(round(y)) + dy - width // 2] = col


def rect(px, x0, y0, x1, y1, col):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px[x, y] = col


def outline(px, x0, y0, x1, y1, col):
    for x in range(x0, x1 + 1):
        px[x, y0] = col
        px[x, y1] = col
    for y in range(y0, y1 + 1):
        px[x0, y] = col
        px[x1, y] = col


# ---------------- painter recipes ----------------

def plate(name):
    """noise_fill(GRAPHITE) + brushed lines + 2px bevel + 4 corner rivets."""
    img = new_img()
    rng = rng_for(name)
    noise_fill(img, GRAPHITE, rng)
    brushed(img, rng)
    bevel(img)
    rivets(img)
    return img


def glyph(px, kind, ink, frame):
    dim = _mix(ink, GLASS, 0.62)
    if kind == "shield":
        for y in range(9, 23):
            if y <= 16:
                xl, xr = 10, 21
            else:
                k = y - 16
                xl, xr = 10 + k, 21 - k
            if xl > xr:
                break
            for x in range(xl, xr + 1):
                edge = x in (xl, xr) or y == 9
                px[x, y] = ink if edge else dim
        rect(px, 15, 12, 16, 17, ink)          # keyhole stem
        rect(px, 14, 12, 17, 13, ink)
    elif kind == "lock":
        outline(px, 12, 8, 19, 14, ink)        # shackle
        outline(px, 13, 9, 18, 14, dim)
        rect(px, 10, 14, 21, 22, ink)          # body
        rect(px, 11, 15, 20, 15, _bright(ink, 1.25))
        rect(px, 15, 17, 16, 20, GLASS)        # keyhole
        px[14, 17] = GLASS
        px[17, 17] = GLASS
    elif kind == "eye":
        for x in range(9, 23):
            u = (x + 0.5 - 16.0) / 7.0
            h = 5.0 * math.sqrt(max(0.0, 1.0 - u * u))
            for y in range(int(round(16 - h)), int(round(16 + h))):
                px[x, y] = dim
            px[x, int(round(16 - h))] = ink
            px[x, int(round(16 + h)) - 1] = ink
        look = (frame % 8) - 4 if frame % 8 < 8 else 0
        cx = 16.0 + (1 if look > 1 else (-1 if look < -2 else 0))
        circle(px, cx, 16.0, 3.0, ink)
        circle(px, cx, 16.0, 1.2, GLASS)
        px[int(cx) + 1, 14] = WHITE
    elif kind == "wave":
        for x in range(7, 25):
            px[x, 16] = dim
        for x in range(8, 24):
            y = 16 + int(round(4.5 * math.sin((x + frame * 2) / 16.0 * 2.0 * math.pi)))
            px[x, y] = ink
            px[x, y + 1] = dim
    elif kind == "bars":
        for i in range(4):
            h = 3 + ((i * 5 + frame * 3) % 10)
            x0 = 9 + i * 4
            for y in range(23 - h, 24):
                for x in range(x0, x0 + 3):
                    px[x, y] = ink if y == 23 - h else dim
        for x in range(8, 24):
            px[x, 24] = _bright(ink, 0.8)


def screen_frame(name, kind, ink, frame, frames):
    img = plate(name)
    px = img.load()
    recess(px, 6, 6, 25, 25, GLASS)
    rng = rng_for(name + "#glass")
    for _ in range(10):                         # faint glass speckle
        blend(px, rng.randint(7, 24), rng.randint(7, 24), ink, 0.08)
    glyph(px, kind, ink, frame)
    sy = 6 + (frame * 19 // frames)
    for x in range(7, 25):
        p = px[x, sy]
        px[x, sy] = _bright(p, 1.4) if p != GLASS else _mix(GLASS, ink, 0.25)
    if frame <= 5:
        rect(px, 23, 8, 24, 9, ink)
    return img


def panel_door(name, haz):
    img = plate(name)
    px = img.load()
    for y in range(S):
        px[15, y] = TRIM
        px[16, y] = _mix(TRIM, G_DARK, 0.5)
    for (bx, by) in ((7, 7), (24, 24), (7, 24), (24, 7)):
        rect(px, bx - 1, by - 1, bx + 1, by + 1, G_LIGHT)
        px[bx + 1, by + 1] = G_DARK
    if haz:
        haz_band(px, 26, 31)
        for x in range(S):
            px[x, 25] = G_DARK
    return img


def frame_tex(name):
    img = plate(name)
    px = img.load()
    for i in range(S):
        for k in range(4):
            col = _mix(TRIM, G_LIGHT, 0.2) if k == 0 else TRIM
            px[i, k] = col if px[i, k] not in (G_LIGHT,) else px[i, k]
            px[i, S - 1 - k] = TRIM if k else G_DARK
            px[k, i] = col if k else G_LIGHT
            px[S - 1 - k, i] = TRIM if k else G_DARK
    outline(px, 4, 4, S - 5, S - 5, G_DARK)
    rivets(img, pts=((1, 1), (29, 1), (1, 29), (29, 29)))
    return img


def clamp_tex(name, heavy):
    img = new_img()
    rng = rng_for(name)
    lock_ramp = [LOCK, _bright(LOCK, 0.82), _bright(LOCK, 0.9), _mix(LOCK, WHITE, 0.1)]
    noise_fill(img, lock_ramp, rng)
    px = img.load()
    step = 6 if heavy else 8
    for y in range(S):
        for x in range(S):
            if ((x + y) // step) % 2 == 1:
                px[x, y] = _bright(px[x, y], 0.72)
    bevel(img, _mix(LOCK, WHITE, 0.45), _bright(LOCK, 0.35))
    for x0 in (0, S - 6):                       # steel end caps
        for y in range(S):
            for x in range(x0, x0 + 6):
                px[x, y] = TRIM if 1 < y < S - 2 else G_DARK
        rivets(img, pts=((x0 + 2, 6), (x0 + 2, 24)))
    return img


def lens(name, on, camera=False):
    img = new_img()
    noise_fill(img, [G_DARK, GRAPHITE[2]], rng_for(name))
    px = img.load()
    c = 16.0
    circle(px, c, c, 11.0, TRIM)
    circle(px, c, c, 9.0, GLASS)
    if camera:
        circle(px, c, c, 11.0, REC, ring=1.6)
    circle(px, c, c, 6.0, TRIM, ring=1.6)
    circle(px, c, c, 4.4, _mix(GLASS, TRIM, 0.35))
    if camera:
        circle(px, c, c, 3.0, _rgba("#1C3550"))
        circle(px, c, c, 1.6, _rgba("#2E5A80"))
    else:
        circle(px, c, c, 3.0, REC if on else REC_OFF)
        circle(px, c, c, 1.5, _mix(REC, WHITE, 0.35) if on else _bright(REC_OFF, 1.2))
    if on or camera:
        rect(px, 13, 13, 14, 14, WHITE)
    px[18, 18] = _mix(G_LIGHT, GLASS, 0.5)
    return img


def beam_tex(name):
    """Near-white soft beam: v (rows) across the width, alpha falls to 0 at the edges; faint streaks along u.
    The BER colours it via the vertex colour (BEAM core, BEAM_GLOW halo, white trip flash)."""
    img = new_img()
    px = img.load()
    rng = rng_for(name)
    streak = [rng.random() for _ in range(S)]
    for y in range(S):
        d = abs(y + 0.5 - 16.0) / 16.0
        a = max(0.0, 1.0 - d) ** 1.6
        for x in range(S):
            s = 0.88 + 0.12 * math.sin((x + streak[y] * 8) / S * 2 * math.pi * 2)
            core = _mix(_rgba("#FFD8D8"), WHITE, max(0.0, 1.0 - d * 3.0))
            col = _bright(core, s)
            px[x, y] = _alpha(col, 255 * a)
    return img


def field_frame(name, lockdown, frame, frames):
    img = new_img()
    px = img.load()
    fill = LOCK if lockdown else FIELD
    hexc = FIELD_LOCK_HEX if lockdown else FIELD_HEX
    # hex lattice: centres every 8 px, odd rows offset 4 px; Voronoi boundaries = hex cells (tileable).
    centres = []
    for row in range(-1, 6):
        for col in range(-1, 6):
            centres.append((col * 8 + (4 if row % 2 else 0) + 4, row * 8 + 4))
    ripple_row = (frame * 3) % 16
    for y in range(S):
        for x in range(S):
            ds = sorted(math.hypot(x + 0.5 - cx, y + 0.5 - cy) for (cx, cy) in centres)
            edge = (ds[1] - ds[0]) < 1.7
            if edge:
                c = hexc
                if ripple_row * 2 <= y < ripple_row * 2 + 2:
                    c = _bright(hexc, 1.25)
                px[x, y] = _alpha(c, 110)
            else:
                shade = 1.0 - 0.18 * (ds[0] / 5.0)
                px[x, y] = _alpha(_bright(fill, shade), 55)
    lines = [frame * 2]
    if lockdown:
        lines.append(frame * 2 + 16)
    for base in lines:
        for k in range(3):
            y = (base + k) % S
            for x in range(S):
                c = px[x, y]
                px[x, y] = _alpha(_mix(c, hexc, 0.6), 160 if k == 1 else 130)
    return img


def chip(name, notch_colour):
    img = new_img()
    px = img.load()
    rng = rng_for(name)
    body = [_rgba("#1E2228"), _rgba("#23282F"), _rgba("#1A1E23")]
    for y in range(9, 23):
        for x in range(6, 26):
            px[x, y] = rng.choice(body)
    outline(px, 6, 9, 25, 22, G_DARK)
    for x in range(7, 25):
        px[x, 10] = _mix(G_LIGHT, body[0], 0.6)
    for i in range(4):                          # 8 gold legs, 4 top + 4 bottom
        lx = 8 + i * 5
        for (y0, y1) in ((6, 8), (23, 25)):
            for y in range(y0, y1 + 1):
                px[lx, y] = GOLD
                px[lx + 1, y] = _bright(GOLD, 0.75)
    rect(px, 13, 14, 18, 16, notch_colour)
    rect(px, 13, 14, 18, 14, _mix(notch_colour, WHITE, 0.3))
    px[8, 20] = G_LIGHT                         # pin-1 dot
    return img


def card(name, band):
    img = new_img()
    px = img.load()
    shade = _mix(CARD, G_DARK, 0.12)
    hi = _mix(CARD, WHITE, 0.6)
    for y in range(7, 25):
        for x in range(3, 29):
            corner = (x in (3, 28)) and (y in (7, 24))
            if corner:
                continue
            px[x, y] = CARD if (x + y) % 9 else shade
    for x in range(4, 28):                      # 2px TRIM edge
        px[x, 7] = TRIM
        px[x, 24] = _mix(TRIM, G_DARK, 0.4)
        px[x, 8] = hi
        px[x, 23] = _mix(TRIM, CARD, 0.5)
    for y in range(8, 24):
        px[3, y] = TRIM
        px[28, y] = _mix(TRIM, G_DARK, 0.4)
        px[4, y] = hi
        px[27, y] = _mix(TRIM, CARD, 0.5)
    rect(px, 14, 12, 19, 15, GOLD)              # contact chip
    px[16, 12] = _bright(GOLD, 0.7)
    px[16, 15] = _bright(GOLD, 0.7)
    rect(px, 14, 13, 19, 13, _bright(GOLD, 0.85))
    for (y, x1) in ((18, 25), (20, 22)):        # printed lines
        for x in range(14, x1):
            px[x, y] = _mix(CARD, TRIM, 0.45)
    if band is not None:
        band_layer(px, band, base=True)
    return img


def band_layer(px, colour, base=False):
    """The 6x18 band at x 5..10 over the full card height; shaded so tinted results keep relief."""
    for y in range(7, 25):
        for x in range(5, 11):
            if x in (5,):
                c = _bright(colour, 0.80)
            elif x == 10:
                c = _bright(colour, 0.70)
            elif y in (7, 24):
                c = _bright(colour, 0.86)
            elif y == 8:
                c = _mix(colour, WHITE, 0.25) if colour != WHITE else colour
            else:
                c = colour if (y // 3) % 2 == 0 else _bright(colour, 0.93)
            px[x, y] = c
    for y in (12, 16, 20):                      # notches
        px[7, y] = _bright(colour, 0.62)
        px[8, y] = _bright(colour, 0.62)


def keycard_band(name, colour):
    img = new_img()
    band_layer(img.load(), colour)
    return img


def tool(name, tip_only=False):
    img = new_img()
    px = img.load()
    tip = [(26, 4), (27, 4), (26, 5), (27, 5)]
    if tip_only:
        for (x, y) in tip:
            px[x, y] = INK_CYAN
        px[26, 4] = _mix(INK_CYAN, WHITE, 0.5)
        for (x, y, a) in ((25, 4, 120), (25, 5, 120), (26, 3, 120), (27, 3, 120), (28, 4, 120),
                          (28, 5, 120), (26, 6, 120), (27, 6, 120), (25, 3, 50), (28, 3, 50),
                          (25, 6, 50), (28, 6, 50), (24, 5, 30), (27, 7, 30)):
            px[x, y] = _alpha(_mix(INK_CYAN, WHITE, 0.15), a)
        return img
    # shaft: 3 px from (6,26) to (22,10) with dark outline
    for i in range(0, 17):
        x = 6 + i
        y = 26 - i
        for k in (-2, -1, 0, 1, 2):
            px[x + k, y] = G_DARK if abs(k) == 2 else (TRIM if k <= 0 else _mix(TRIM, G_DARK, 0.4))
        px[x - 1, y] = G_LIGHT if i % 4 else TRIM
    for i in range(0, 5):                       # grip wrap near the end
        x = 6 + i
        y = 26 - i
        for k in (-1, 0, 1):
            px[x + k, y] = _rgba("#20242A") if i % 2 else _rgba("#30363E")
    # head 6x6 at the top right
    rect(px, 20, 5, 25, 10, GRAPHITE[0])
    outline(px, 20, 5, 25, 10, G_DARK)
    for x in range(21, 25):
        px[x, 6] = G_LIGHT
    rect(px, 22, 8, 23, 9, GRAPHITE[3])
    for (x, y) in tip:
        px[x, y] = _mix(TRIM, GLASS, 0.3)
    return img


def strip(painter, frames):
    out = Img(S, S * frames, CLEAR)
    for f in range(frames):
        out.paste(painter(f), 0, f * S)
    return out


# ---------------- per-texture painters ----------------

def hub_top():
    img = plate("security_hub_top")
    px = img.load()
    for x in range(8, 24):                      # vent slots
        for y in (10, 14, 18, 22):
            px[x, y] = G_DARK
            px[x, y + 1] = _mix(G_LIGHT, G_DARK, 0.5)
    return img


def hub_side():
    img = plate("security_hub_side")
    px = img.load()
    recess(px, 8, 8, 23, 23, GRAPHITE[2])
    for y in range(10, 22, 3):
        for x in range(10, 22):
            px[x, y] = G_DARK
    return img


def hub_back():
    img = plate("security_hub_back")
    px = img.load()
    for (cx, cy) in ((10, 12), (21, 12), (10, 21), (21, 21)):   # cable ports
        circle(px, cx, cy, 3.0, G_DARK)
        circle(px, cx, cy, 1.5, TRIM)
    rect(px, 13, 24, 18, 26, TRIM)
    return img


def programmer_desk():
    img = plate("keycard_programmer_desk")
    px = img.load()
    recess(px, 9, 4, 22, 9, G_DARK)             # card slot
    for x in range(11, 21):
        px[x, 6] = HAZ_K
    led(px, 24, 6, P["OK"])
    return img


def monitor_bezel():
    img = frame_tex("security_monitor_bezel")
    px = img.load()
    for x in range(12, 20):
        px[x, 28] = G_LIGHT
    return img


def chest_body():
    img = plate("secure_chest_body")
    px = img.load()
    for x0 in (6, 24):                          # vertical straps
        for y in range(S):
            px[x0, y] = TRIM
            px[x0 + 1, y] = _mix(TRIM, G_DARK, 0.4)
    rivets(img, pts=((6, 8), (24, 8), (6, 22), (24, 22)))
    return img


def chest_lid():
    img = plate("secure_chest_lid")
    px = img.load()
    for x in range(4, 28):
        px[x, 14] = G_DARK
        px[x, 15] = _mix(G_LIGHT, G_DARK, 0.4)
    rect(px, 12, 20, 19, 22, TRIM)              # handle
    outline(px, 12, 20, 19, 22, G_DARK)
    return img


def chest_plate():
    """Greyscale lock plate; the BER multiplies the level colour on the vertices."""
    img = new_img()
    rng = rng_for("secure_chest_plate")
    noise_fill(img, [_rgba("#D2D2D2"), _rgba("#C8C8C8"), _rgba("#DCDCDC")], rng)
    bevel(img, WHITE, _rgba("#7A7A7A"))
    px = img.load()
    circle(px, 16.0, 13.0, 4.0, _rgba("#4A4A4A"))   # keyhole
    rect(px, 15, 13, 16, 22, _rgba("#4A4A4A"))
    circle(px, 16.0, 13.0, 2.0, _rgba("#2A2A2A"))
    rivets(img, pts=((4, 4), (26, 4), (4, 26), (26, 26)), col=_rgba("#9A9A9A"))
    return img


def laser_housing():
    img = plate("laser_grid_node_housing")
    px = img.load()
    circle(px, 16.0, 16.0, 8.0, G_DARK, ring=1.2)
    circle(px, 16.0, 16.0, 6.0, TRIM, ring=1.0)
    return img


def camera_mount(armed):
    name = "security_camera_mount_armed" if armed else "security_camera_mount"
    img = plate(name)
    px = img.load()
    circle(px, 16.0, 16.0, 9.0, G_DARK)
    circle(px, 16.0, 16.0, 7.0, TRIM)
    if armed:
        circle(px, 16.0, 16.0, 11.5, REC, ring=2.0)
    circle(px, 16.0, 16.0, 3.0, GRAPHITE[3])
    return img


def camera_body():
    img = plate("security_camera_body")
    px = img.load()
    for y in range(8, 25, 3):                   # cooling fins
        for x in range(6, 26):
            px[x, y] = G_DARK
            px[x, y + 1] = _mix(G_LIGHT, GRAPHITE[0], 0.5)
    return img


def alarm_base():
    img = plate("alarm_base")
    px = img.load()
    for x in range(6, 26, 4):
        px[x, 16] = G_DARK
        px[x + 1, 16] = G_DARK
    return img


def alarm_dome(on):
    name = "alarm_dome_on" if on else "alarm_dome_off"
    img = new_img()
    rng = rng_for(name)
    base = LOCK if on else _rgba("#5A1A1A")
    ramp = [base, _bright(base, 0.9), _bright(base, 0.82), _bright(base, 1.06)]
    noise_fill(img, ramp, rng)
    px = img.load()
    for y in range(S):                          # fresnel ribs
        for x in range(0, S, 8):
            px[x, y] = _bright(base, 0.7)
    hi = _mix(base, WHITE, 0.55 if on else 0.25)
    for y in range(3, 12):
        px[6, y] = hi
        px[7, y] = _mix(hi, base, 0.5)
    for x in range(S):
        px[x, 0] = _bright(base, 0.6)
        px[x, S - 1] = _bright(base, 0.5)
    if on:                                      # see-through while active: the BER blades turn inside it
        for y in range(S):
            for x in range(S):
                px[x, y] = _alpha(px[x, y], 165)
    return img


def alarm_blade():
    """Greyscale blade, alpha ramping from the hub (left) to the tip (right); tinted LOCK by the BER."""
    img = new_img()
    px = img.load()
    for y in range(S):
        dy = abs(y + 0.5 - 16.0) / 16.0
        for x in range(S):
            a = (1.0 - x / float(S)) * (1.0 - dy * 0.6)
            g = int(round(255 - 70 * dy - 30 * (x / float(S))))
            px[x, y] = (g, g, g, int(round(255 * max(0.0, a))))
    return img


def emitter_housing(active):
    name = "force_field_emitter_housing_active" if active else "force_field_emitter_housing"
    img = plate(name)
    px = img.load()
    for (cx, cy) in ((10, 10), (21, 10), (10, 21), (21, 21)):
        circle(px, cx, cy, 2.0, G_DARK)
    if active:
        circle(px, 16.0, 16.0, 12.0, FIELD, ring=1.0)
    else:
        circle(px, 16.0, 16.0, 12.0, _mix(FIELD, G_DARK, 0.75), ring=1.0)
    return img


def emitter_core():
    """Light faceted crystal in greys; the BER tints it FIELD / LOCK / WARN / dormant grey."""
    img = new_img()
    px = img.load()
    rng = rng_for("force_field_emitter_core")
    for y in range(S):
        for x in range(S):
            facet = ((x // 8) + (y // 8)) % 3
            g = (236, 214, 192)[facet] + rng.randint(-6, 6)
            px[x, y] = (g, g, g, 255)
    for i in range(S):
        px[i, i] = WHITE
        px[S - 1 - i, i] = _rgba("#F4F4F4")
    bevel(img, WHITE, _rgba("#9A9A9A"))
    return img


def security_led():
    """Opaque near-white LED sprite (centre 255 falling to ~226 at the corners) for LED and bloom quads; the
    renderers tint it by vertex colour, so it stays white enough that the tint reads true."""
    img = new_img()
    px = img.load()
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - 16.0, y + 0.5 - 16.0) / 22.7
            g = int(round(255 - 29 * min(1.0, d)))
            px[x, y] = (g, g, g, 255)
    return img


# ---------------- manifest ----------------
# (folder, name, painter(frame) -> Img, frames). frames == 1 means static (the painter ignores frame).

def _screen(name, kind, ink, frames=8):
    return lambda f: screen_frame(name, kind, ink, f, frames)


def _field(name, lockdown, frames=16):
    return lambda f: field_frame(name, lockdown, f, frames)


def _static(fn, *args):
    return lambda f: fn(*args)


MANIFEST = [
    ("block", "security_hub_top", _static(hub_top), 1),
    ("block", "security_hub_side", _static(hub_side), 1),
    ("block", "security_hub_back", _static(hub_back), 1),
    ("block", "security_hub_front", _screen("security_hub_front", "shield", INK_CYAN), 8),
    ("block", "security_hub_front_lockdown", _screen("security_hub_front_lockdown", "lock", LOCK), 8),
    ("block", "keycard_programmer_base", _static(plate, "keycard_programmer_base"), 1),
    ("block", "keycard_programmer_desk", _static(programmer_desk), 1),
    ("block", "keycard_programmer_screen", _screen("keycard_programmer_screen", "bars", INK_CYAN), 8),
    ("block", "security_monitor_bezel", _static(monitor_bezel), 1),
    ("block", "security_monitor_screen", _screen("security_monitor_screen", "eye", INK_CYAN), 8),
    ("block", "security_monitor_screen_lockdown", _screen("security_monitor_screen_lockdown", "lock", LOCK), 8),
    ("block", "secure_chest_body", _static(chest_body), 1),
    ("block", "secure_chest_lid", _static(chest_lid), 1),
    ("block", "secure_chest_plate", _static(chest_plate), 1),
    ("block", "security_door_frame", _static(frame_tex, "security_door_frame"), 1),
    ("block", "security_door_panel", _static(panel_door, "security_door_panel", False), 1),
    ("block", "security_door_clamp", _static(clamp_tex, "security_door_clamp", False), 1),
    ("block", "blast_door_frame", _static(frame_tex, "blast_door_frame"), 1),
    ("block", "blast_door_panel", _static(panel_door, "blast_door_panel", True), 1),
    ("block", "blast_door_clamp", _static(clamp_tex, "blast_door_clamp", True), 1),
    ("block", "laser_grid_node_housing", _static(laser_housing), 1),
    ("block", "laser_grid_node_lens_off", _static(lens, "laser_grid_node_lens_off", False), 1),
    ("block", "laser_grid_node_lens_on", _static(lens, "laser_grid_node_lens_on", True), 1),
    ("block", "laser_beam", _static(beam_tex, "laser_beam"), 1),
    ("block", "security_camera_mount", _static(camera_mount, False), 1),
    ("block", "security_camera_mount_armed", _static(camera_mount, True), 1),
    ("block", "security_camera_body", _static(camera_body), 1),
    ("block", "security_camera_lens", _static(lens, "security_camera_lens", True, True), 1),
    ("block", "alarm_base", _static(alarm_base), 1),
    ("block", "alarm_dome_off", _static(alarm_dome, False), 1),
    ("block", "alarm_dome_on", _static(alarm_dome, True), 1),
    ("block", "alarm_blade", _static(alarm_blade), 1),
    ("block", "force_field_emitter_housing", _static(emitter_housing, False), 1),
    ("block", "force_field_emitter_housing_active", _static(emitter_housing, True), 1),
    ("block", "force_field_emitter_core", _static(emitter_core), 1),
    ("block", "force_field", _field("force_field", False), 16),
    ("block", "force_field_lockdown", _field("force_field_lockdown", True), 16),
    ("block", "security_led", _static(security_led), 1),
    ("item", "keycard_base", _static(card, "keycard_base", None), 1),
    ("item", "keycard_band", _static(keycard_band, "keycard_band", WHITE), 1),
    ("item", "keycard_band_encrypted", _static(keycard_band, "keycard_band_encrypted", ENC), 1),
    ("item", "linking_tool", _static(tool, "linking_tool"), 1),
    ("item", "linking_tool_tip_on", _static(tool, "linking_tool_tip_on", True), 1),
    ("item", "encryption_module", _static(chip, "encryption_module", ENC), 1),
    ("item", "hardness_module", _static(chip, "hardness_module", TRIM), 1),
    ("item", "open_speed_module", _static(chip, "open_speed_module", P["L_MEMBER"]), 1),
    ("item", "range_module", _static(chip, "range_module", FIELD), 1),
    ("item", "strength_module", _static(chip, "strength_module", P["L_OFFICER"]), 1),
]

MCMETA = {
    8: {"animation": {"frametime": 3}},
    16: {"animation": {"frametime": 2, "interpolate": True}},
}


def _path(folder, name):
    return os.path.join(DIRS[folder], name + ".png")


def _mcmeta_text(frames):
    return json.dumps(MCMETA[frames], indent=2) + "\n"


def paint(force, only):
    for d in DIRS.values():
        os.makedirs(d, exist_ok=True)
    wrote = skipped = 0
    for (folder, name, painter, frames) in MANIFEST:
        if only and name not in only:
            continue
        path = _path(folder, name)
        if os.path.exists(path) and not force:
            print("skip (exists)", os.path.relpath(path, ROOT))
            skipped += 1
        else:
            img = painter(0) if frames == 1 else strip(painter, frames)
            with open(path, "wb") as f:      # truncate in place (no unlink)
                f.write(png_bytes(img))
            print("wrote", os.path.relpath(path, ROOT))
            wrote += 1
        if frames > 1:
            meta = path + ".mcmeta"
            if force or not os.path.exists(meta):
                with open(meta, "w", newline="\n") as f:
                    f.write(_mcmeta_text(frames))
    print("gen_security_textures: %d written, %d skipped (target = %s)" % (wrote, skipped, target_label()))
    return 0


def check():
    problems = []
    owned = set()
    for (folder, name, _painter, frames) in MANIFEST:
        path = _path(folder, name)
        rel = "%s/%s.png" % (folder, name)
        owned.add(rel)
        if not os.path.isfile(path):
            problems.append("%s: missing" % rel)
            continue
        try:
            w, h, pixels = read_png(path)
        except (ValueError, KeyError, zlib.error, struct.error) as e:
            problems.append("%s: unreadable (%s)" % (rel, e))
            continue
        if w != S:
            problems.append("%s: width %d, expected %d" % (rel, w, S))
        if h != S * frames:
            problems.append("%s: height %d, expected %d (%d frame%s)" % (rel, h, S * frames, frames,
                                                                        "" if frames == 1 else "s"))
        colours = len(set(pixels))
        if colours < MIN_COLOURS:
            problems.append("%s: %d distinct colours, need >= %d" % (rel, colours, MIN_COLOURS))
        meta = path + ".mcmeta"
        if frames > 1:
            if not os.path.isfile(meta):
                problems.append("%s: animated strip without .mcmeta" % rel)
            else:
                try:
                    with open(meta, encoding="utf-8") as f:
                        anim = json.load(f).get("animation")
                    if not isinstance(anim, dict):
                        problems.append("%s.mcmeta: no animation object" % rel)
                except ValueError as e:
                    problems.append("%s.mcmeta: invalid JSON (%s)" % (rel, e))
    unowned = []
    for folder, d in sorted(DIRS.items()):
        if os.path.isdir(d):
            for fn in sorted(os.listdir(d)):
                if fn.endswith(".png") and "%s/%s" % (folder, fn) not in owned:
                    unowned.append("%s/%s" % (folder, fn))
    # A PNG the generator does not own would ship unchecked (wrong size, too few colours), so it fails the check.
    problems.extend("%s: not owned by the generator (delete it or add it to MANIFEST)" % u for u in unowned)
    for p in problems:
        print("FAIL", p)
    print("gen_security_textures --check: %d texture(s), %d problem(s)" % (len(MANIFEST), len(problems)))
    return 1 if problems else 0


def main(argv):
    if "--check" in argv:
        return check()
    only = None
    if "--only" in argv:
        i = argv.index("--only")
        if i + 1 >= len(argv):
            print("--only needs NAME[,NAME...]")
            return 2
        only = set(n.strip() for n in argv[i + 1].split(",") if n.strip())
        unknown = only - set(m[1] for m in MANIFEST)
        if unknown:
            print("unknown texture name(s): %s" % ", ".join(sorted(unknown)))
            return 2
    return paint("--force" in argv, only)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
