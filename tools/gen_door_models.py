#!/usr/bin/env python3
"""Write the security door and blast door block models (8 + 16 files) and their item models.

Hand-authoring two dozen near-identical multi-part models invites typos, so they are generated here from
one part list. Every model is authored facing north (front = -Z); the blockstate rotates it.

Looks per part: closed (frame + static panels), frame (frame only: open, or animating so the renderer draws
the moving panels), closed_sealed and frame_sealed (the same plus the lockdown clamp bars).

Blast door quadrants: bottom_left is the controller and the right-hand column sits one block in the
facing.getCounterClockWise() direction, so in the north-authored frame the *left* quadrants have their outer
vertical edge on +X and the *right* quadrants on -X. Each quadrant's panel carries the chevron band toward
the centre seam (top quadrants use the texture's bottom band as is; bottom quadrants flip V).

usage: python tools/gen_door_models.py [--check]
  --check   write nothing; exit 1 if any generated file is missing or differs from what would be written.
"""
import json
import os
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from nerosecurity_target import src_base  # noqa: E402

ASSETS = os.path.join(src_base(), "src/main/resources/assets/nerosecurity")
BLOCK_MODELS = os.path.join(ASSETS, "models/block")
ITEM_MODELS = os.path.join(ASSETS, "models/item")
NS = "nerosecurity:block/"
LOOKS = ("closed", "frame", "closed_sealed", "frame_sealed")
FACES = ("north", "south", "east", "west", "up", "down")


def element(name, frm, to, texture, uv=None, face_uv=None, shade=True, faces=FACES):
    """One element; `uv` applies to north/south when given, `face_uv` overrides per face."""
    out = {"name": name, "from": list(frm), "to": list(to), "faces": {}}
    if not shade:
        out["shade"] = False
    for f in faces:
        face = {"texture": texture}
        if face_uv and f in face_uv:
            face["uv"] = list(face_uv[f])
        elif uv is not None and f in ("north", "south"):
            face["uv"] = list(uv)
        out["faces"][f] = face
    return out


def model(textures, elements, parent="minecraft:block/block"):
    return {"parent": parent, "textures": textures, "elements": elements}


# ---------------- security door (1x2) ----------------

def security_part(part, look):
    lower = part == "lower"
    els = [
        element("jamb_l", (0, 0, 0), (2, 16, 16), "#frame"),
        element("jamb_r", (14, 0, 0), (16, 16, 16), "#frame"),
    ]
    if lower:
        els.append(element("threshold", (2, 0, 0), (14, 2, 16), "#frame",
                           face_uv={"up": (0, 13, 12, 15)}))
        els[-1]["faces"]["up"]["texture"] = "#haz"
    else:
        els.append(element("lintel", (2, 14, 0), (14, 16, 16), "#frame"))
    if look.startswith("closed"):
        # Both blocks share one 32 px panel texture across the seam: lower = bottom half [0,8,6,16], upper =
        # top half [0,0,6,8], on both panels and both faces. The renderer's moving panels use the
        # same UVs, so the closed static model and the renderer's closed frame are pixel-identical.
        uv = (0, 8, 6, 16) if lower else (0, 0, 6, 8)
        els.append(element("panel_l", (2, 0, 6), (8, 16, 10), "#panel", uv=uv))
        els.append(element("panel_r", (8, 0, 6), (14, 16, 10), "#panel", uv=uv))
    if look.endswith("sealed"):
        els.append(element("clamp_top", (2, 11, 5), (14, 13, 11), "#clamp"))
        els.append(element("clamp_bot", (2, 3, 5), (14, 5, 11), "#clamp"))
    tex = {"frame": NS + "security_door_frame", "panel": NS + "security_door_panel",
           "clamp": NS + "security_door_clamp", "haz": NS + "blast_door_panel",
           "particle": NS + "security_door_frame"}
    return model(tex, els)


# ---------------- blast door (2x2) ----------------

def blast_part(part, look):
    top = part.startswith("top")
    left = part.endswith("left")
    els = []
    # outer vertical edge: +X for left quadrants, -X for right quadrants
    if left:
        els.append(element("frame_v", (14, 0, 0), (16, 16, 16), "#frame"))
        px0, px1 = 0, 14
    else:
        els.append(element("frame_v", (0, 0, 0), (2, 16, 16), "#frame"))
        px0, px1 = 2, 16
    # outer horizontal edge: top edge for top quadrants, bottom edge for bottom quadrants
    if top:
        els.append(element("frame_h", (0, 14, 0), (16, 16, 16), "#frame"))
        py0, py1 = 0, 14
    else:
        els.append(element("frame_h", (0, 0, 0), (16, 2, 16), "#frame"))
        py0, py1 = 2, 16
    if look.startswith("closed"):
        # chevron band is the texture's bottom rows: keep for top quadrants (seam below), flip for bottom.
        u0, u1 = (16 - px1, 16 - px0)
        v = (16 - py1, 16 - py0) if top else (py1, py0)
        uv = (u0, v[0], u1, v[1])
        els.append(element("panel", (px0, py0, 5), (px1, py1, 11), "#panel", uv=uv))
    if look.endswith("sealed"):
        els.append(element("clamp", (px0, 7, 4), (px1, 9, 12), "#clamp"))
    tex = {"frame": NS + "blast_door_frame", "panel": NS + "blast_door_panel",
           "clamp": NS + "blast_door_clamp", "particle": NS + "blast_door_frame"}
    return model(tex, els)


# ---------------- item models ----------------

def shifted(els, dx=0, dy=0):
    out = []
    for e in els:
        e = json.loads(json.dumps(e))
        e["from"] = [e["from"][0] + dx, e["from"][1] + dy, e["from"][2]]
        e["to"] = [e["to"][0] + dx, e["to"][1] + dy, e["to"][2]]
        out.append(e)
    return out


def display(scale, tx, ty, gui_rot):
    s = [scale] * 3
    return {
        "gui": {"rotation": list(gui_rot), "translation": [tx, ty, 0], "scale": s},
        "ground": {"rotation": [0, 0, 0], "translation": [tx * 0.6, ty * 0.6 + 2, 0], "scale": [scale * 0.6] * 3},
        "fixed": {"rotation": [0, 0, 0], "translation": [tx, ty, 0], "scale": s},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 1.5, 0],
                                  "scale": [scale * 0.75] * 3},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [scale * 0.8] * 3},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [scale * 0.8] * 3},
    }


def security_item():
    lower = security_part("lower", "closed")
    upper = security_part("upper", "closed")
    els = lower["elements"] + shifted(upper["elements"], dy=16)
    scale = 0.45
    m = model(lower["textures"], els)
    m["display"] = display(scale, 0, round(-8 * scale * 0.966, 2), (15, -30, 0))
    return m


def blast_item():
    els = []
    tex = None
    for part, dx, dy in (("bottom_right", 0, 0), ("bottom_left", 16, 0), ("top_right", 0, 16),
                         ("top_left", 16, 16)):
        m = blast_part(part, "closed")
        tex = m["textures"]
        els += shifted(m["elements"], dx=dx, dy=dy)
    scale = 0.32
    m = model(tex, els)
    m["display"] = display(scale, round(-8 * scale, 2), round(-8 * scale, 2), (0, 0, 0))
    return m


def outputs():
    files = {}
    for part in ("lower", "upper"):
        for look in LOOKS:
            files[os.path.join(BLOCK_MODELS, "security_door_%s_%s.json" % (part, look))] = security_part(part, look)
    for part in ("bottom_left", "bottom_right", "top_left", "top_right"):
        for look in LOOKS:
            files[os.path.join(BLOCK_MODELS, "blast_door_%s_%s.json" % (part, look))] = blast_part(part, look)
    files[os.path.join(ITEM_MODELS, "security_door.json")] = security_item()
    files[os.path.join(ITEM_MODELS, "blast_door.json")] = blast_item()
    return {p: dumps(m) for p, m in files.items()}


def _flat(v):
    if isinstance(v, dict):
        return all(not isinstance(x, (dict, list)) or (isinstance(x, list) and _flat(x)) for x in v.values())
    if isinstance(v, list):
        return all(not isinstance(x, (dict, list)) for x in v)
    return True


def _fmt(v, ind):
    pad = "  " * ind
    if _flat(v) and (not isinstance(v, dict) or len(v) <= 3):
        return json.dumps(v, separators=(", ", ": "))
    if isinstance(v, dict):
        rows = ["%s  %s: %s" % (pad, json.dumps(k), _fmt(x, ind + 1)) for k, x in v.items()]
        return "{\n" + ",\n".join(rows) + "\n" + pad + "}"
    rows = ["%s  %s" % (pad, _fmt(x, ind + 1)) for x in v]
    return "[\n" + ",\n".join(rows) + "\n" + pad + "]"


def dumps(m):
    """Readable JSON: small flat objects and number arrays stay on one line."""
    return _fmt(m, 0) + "\n"


def main(argv):
    out = outputs()
    if "--check" in argv:
        bad = 0
        for path, text in sorted(out.items()):
            if not os.path.isfile(path):
                print("FAIL missing", os.path.relpath(path, ASSETS))
                bad += 1
                continue
            with open(path, encoding="utf-8") as f:
                if f.read().replace("\r\n", "\n") != text:
                    print("FAIL differs", os.path.relpath(path, ASSETS))
                    bad += 1
        print("gen_door_models --check: %d file(s), %d problem(s)" % (len(out), bad))
        return 1 if bad else 0
    os.makedirs(BLOCK_MODELS, exist_ok=True)
    os.makedirs(ITEM_MODELS, exist_ok=True)
    for path, text in sorted(out.items()):
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(text)
        print("wrote", os.path.relpath(path, ASSETS))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
