#!/usr/bin/env python3
"""Generates the Lottie assets bundled in app/src/main/assets/lottie/.

All artwork derives from a single accent hue (default #6750A4) expressed at
varying opacities; every fill/stroke item is named "accent" so QcyLottie can
recolor it to the theme's primary color at runtime. Never name a GROUP
"accent": lottie KeyPath resolution ("**", "accent") terminates on the group
and never reaches the color items inside it.

Usage: python3 tools/generate_lottie.py  (writes the JSONs in place)
"""
import json, math, os

OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "lottie")

ACCENT = [0.4039, 0.3137, 0.6431]  # #6750A4, overridden at runtime by theme primary
WHITE = [1.0, 1.0, 1.0]

# Easings as (o.x, o.y, i.x, i.y) — control points living on the START keyframe.
DECEL = (0.05, 0.7, 0.1, 1.0)      # emphasized decelerate — entrances
ACCEL = (0.3, 0.0, 0.8, 0.15)      # emphasized accelerate — exits
STANDARD = (0.2, 0.0, 0.0, 1.0)
LINEAR = (0.167, 0.167, 0.833, 0.833)
OVERSHOOT = (0.34, 1.56, 0.64, 1.0)  # spring-ish back-out


def static(v):
    return {"a": 0, "k": v}


def anim(pairs, dims):
    """pairs: [(t, value, easing|None), ...]; easing lives on the start keyframe."""
    ks = []
    for idx, (t, s, e) in enumerate(pairs):
        k = {"t": t, "s": s}
        if e is not None and idx < len(pairs) - 1:
            ox, oy, ix, iy = e
            k["o"] = {"x": [ox] * dims, "y": [oy] * dims}
            k["i"] = {"x": [ix] * dims, "y": [iy] * dims}
        ks.append(k)
    return {"a": 1, "k": ks}


def ellipse(w, h=None):
    return {"ty": "el", "p": static([0, 0]), "s": static([w, h or w]), "d": 1, "nm": "el"}


def rect(w, h, r):
    return {"ty": "rc", "p": static([0, 0]), "s": static([w, h]), "r": static(r), "d": 1, "nm": "rc"}


def fill(color=ACCENT, opacity=100):
    c = list(color) + [1.0]
    return {"ty": "fl", "c": static(c), "o": static(opacity), "nm": "accent"}


def stroke(w, color=ACCENT, opacity=100, dashes=None, dash_offset=None):
    st = {"ty": "st", "c": static(list(color) + [1.0]), "o": static(opacity),
          "w": static(w), "lc": 2, "lj": 2, "nm": "accent"}
    if dashes:
        st["d"] = [
            {"n": "d", "nm": "dash", "v": static(dashes[0])},
            {"n": "g", "nm": "gap", "v": static(dashes[1])},
            {"n": "o", "nm": "offset",
             "v": static(dash_offset) if dash_offset is not None else static(0)},
        ]
    return st


def gradient_fill(s_pt, e_pt, c1, c2, radial=False):
    """Two-stop gradient; s_pt/e_pt are the gradient endpoints in shape coords.

    Stops MUST be opaque: QcyLottie remaps gradient colors with ARGB ints and
    would flatten per-stop alphas. Translucency goes on the group's opacity.
    """
    k = [0] + list(c1) + [1] + list(c2) + [0, 1.0, 1, 1.0]
    gf = {"ty": "gf", "o": static(100), "r": 1, "bm": 0, "g": {"p": 2, "k": static(k)},
          "s": static(list(s_pt)), "e": static(list(e_pt)), "t": 1 if radial else 2,
          "h": static(0), "a": static(0), "nm": "accent"}
    return gf


def path(v, tangents=None, closed=False):
    """v: vertices; tangents: (i, o) lists of [x, y] or None for straight."""
    n = len(v)
    if tangents:
        i_t, o_t = tangents
    else:
        i_t = [[0, 0]] * n
        o_t = [[0, 0]] * n
    return {"ty": "sh", "d": 1,
            "ks": {"a": 0, "k": {"i": i_t, "o": o_t, "v": v, "c": closed}}, "nm": "sh"}


def trim(e_pairs, s=0, o=0):
    return {"ty": "tm", "s": static(s), "e": anim(e_pairs, 1), "o": static(o), "m": 1, "nm": "tm"}


def group(items, nm="g", p=None, s=None, r=None, o=None):
    tr = {"ty": "tr", "p": static(p or [0, 0]), "a": static([0, 0]),
          "s": static(s or [100, 100]), "r": static(r or 0), "o": static(o if o is not None else 100),
          "sk": static(0), "sa": static(0), "nm": "tr"}
    if isinstance(p, dict): tr["p"] = p
    if isinstance(s, dict): tr["s"] = s
    if isinstance(r, dict): tr["r"] = r
    if isinstance(o, dict): tr["o"] = o
    return {"ty": "gr", "it": items + [tr], "nm": nm, "np": len(items) + 1,
            "cix": 2, "bm": 0, "ix": 1, "hd": False}


def layer(ind, nm, shapes, op, o=static(100), p=static([0, 0, 0]),
          s=static([100, 100, 100]), r=static(0)):
    return {"ddd": 0, "ind": ind, "ty": 4, "nm": nm, "sr": 1,
            "ks": {"o": o, "r": r, "p": p, "a": static([0, 0, 0]), "s": s},
            "ao": 0, "shapes": shapes, "ip": 0, "op": op, "st": 0, "bm": 0}


def root(nm, W, H, op, layers):
    return {"v": "5.7.4", "fr": 60, "ip": 0, "op": op, "w": W, "h": H, "nm": nm,
            "ddd": 0, "assets": [], "layers": layers, "markers": []}


def dump(name, doc):
    p = os.path.join(OUT, name)
    json.dump(doc, open(p, "w"), separators=(",", ":"))
    print(f"{name:26s} {os.path.getsize(p):6d} bytes")


def glow(cx, cy, d, opacities=(8, 14, 22)):
    """Renderer-safe glow: concentric translucent discs (no blur effect)."""
    items = []
    for i, op in enumerate(opacities):
        items.append(group([ellipse(d * (1 + 0.55 * (len(opacities) - 1 - i))), fill(opacity=op)],
                           nm=f"glow{i}", p=[cx, cy]))
    return items


def ring_layer(ind, cx, cy, d, sw, cycle, start, op=70, grow=(30, 118)):
    e = start + cycle
    return layer(ind, f"ring{ind}", [group([ellipse(d), stroke(sw)], nm="g")], op=e,
                 o=anim([(start, [op], DECEL), (e, [0], None)], 1),
                 p=static([cx, cy, 0]),
                 s=anim([(start, [grow[0], grow[0], 100], DECEL),
                         (e, [grow[1], grow[1], 100], None)], 3))


def wedge(cx, cy, r, half_deg, steps=7, r0=0):
    """Pie/annular sector pointing up, spanning ±half_deg."""
    vs = [[0, 0]] if r0 == 0 else []
    if r0:
        for k in range(steps + 1):
            a = math.radians(-90 - half_deg + (2 * half_deg) * k / steps)
            vs.append([r0 * math.cos(a), r0 * math.sin(a)])
    for k in range(steps + 1):
        a = math.radians(-90 + half_deg - (2 * half_deg) * k / steps)
        vs.append([r * math.cos(a), r * math.sin(a)])
    return path(vs, closed=True)


def music_note():
    """Eighth note: filled head (rotated), vertical stem off its right edge,
    filled pennant flag. Sized to stay legible at ~50% render scale."""
    head = group([ellipse(22, 16), fill()], nm="head", r=static(-20), p=[-9.5, 25])
    stem = group([path([[0, 24], [0, -14]]), stroke(4)], nm="stem")
    flag = group([path([[0, -14], [14, -8], [3, 8]], closed=True), fill()], nm="flag")
    return group([head, stem, flag], nm="note")


def earbud_glyph(scale=1.0, waves=True):
    """Earbud head + angled stem; optional sound-wave arcs at the right."""
    head = group([ellipse(34 * scale), stroke(5 * scale)], nm="head")
    stem = group([path([[0, 0], [9 * scale, 22 * scale]]), stroke(5 * scale)], nm="stem")
    stem["it"][-1]["p"] = static([10 * scale, 12 * scale])
    items = [head, stem]
    if waves:
        for i, (dx, h) in enumerate([(20, 8), (28, 12)]):
            arc = path([[0, -h], [0, h]], tangents=([[10, -h * 0.6], [10, h * 0.6]], [[10, -h * 0.6], [10, h * 0.6]]))
            items.append(group([arc, stroke(4 * scale, opacity=70 - 25 * i)], nm=f"wave{i}", p=[dx * scale, 6 * scale]))
    return group(items, nm="bud")


# ---------------------------------------------------------------- sonar (find)
def gen_find_sonar():
    OP = 240
    cx = cy = 200
    layers = []
    # expanding pings — two per loop
    for i, st in enumerate((0, 40, 80)):
        layers.append(ring_layer(i + 1, cx, cy, 240, 3, 120, st, op=60))
    # rotating sweep arc (thin annular sector)
    sweep = layer(4, "sweep", [group([wedge(0, 0, 150, 16, r0=54), fill(opacity=20)], nm="g")], op=OP,
                  p=static([cx, cy, 0]), r=anim([(0, [0], LINEAR), (240, [360], None)], 1))
    layers.append(sweep)
    # blips that light up as the sweep passes
    for i, theta in enumerate((30, 100, 190, 285)):
        t_hit = int(theta / 360 * OP)
        a = math.radians(theta - 90)
        bx, by = cx + 118 * math.cos(a), cy + 118 * math.sin(a)
        core = group([ellipse(9), fill(color=WHITE, opacity=90)], nm="core")
        halo = group([ellipse(18), fill(opacity=45)], nm="halo")
        g = group([halo, core], nm=f"blip{i}")
        g["it"][-1]["p"] = static([bx, by])
        g["it"][-1]["s"] = anim([(t_hit, [40, 40], OVERSHOOT), (t_hit + 10, [110, 110], STANDARD),
                                 (t_hit + 46, [90, 90], None)], 2)
        g["it"][-1]["o"] = anim([(max(0, t_hit - 14), [0], DECEL), (t_hit + 4, [95], DECEL),
                                 (t_hit + 52, [0], None)], 1)
        layers.append(layer(5 + i, f"blip{i}", [g], op=OP))
    # center glow + earbud glyph with per-ping pulse
    glyph = group([earbud_glyph(1.15)], nm="glyph")
    layers.append(layer(9, "glyph", glow(cx, cy, 46) + [glyph], op=OP,
                        p=static([cx, cy, 0]),
                        s=anim([(0, [128, 128, 100], OVERSHOOT), (24, [108, 108, 100], STANDARD),
                                (120, [128, 128, 100], OVERSHOOT), (144, [108, 108, 100], None)], 3)))
    # outer dashed tick ring
    ticks = group([ellipse(330), stroke(2, opacity=18, dashes=(2, 22))], nm="ticks")
    layers.append(layer(10, "ticks", [ticks], op=OP, p=static([cx, cy, 0])))
    dump("find_sonar.json", root("find_sonar", 400, 400, OP, layers))


def gen_find_idle():
    OP = 240
    cx = cy = 200
    glyph = group([earbud_glyph(1.15)], nm="glyph")
    glyph_g = group([glyph], nm="glyphG")
    glyph_g["it"][-1]["r"] = anim([(0, [-2], STANDARD), (120, [2], STANDARD), (240, [-2], None)], 1)
    breathe = layer(1, "glyph", glow(cx, cy, 42, (6, 10, 15)) + [glyph_g], op=OP,
                    p=static([cx, cy, 0]),
                    o=anim([(0, [70], STANDARD), (120, [100], STANDARD), (240, [70], None)], 1))
    faint = group([ellipse(200), stroke(2, opacity=22)], nm="g")
    layers = [
        layer(2, "haloRing", [faint], op=OP, p=static([cx, cy, 0]),
              s=anim([(0, [100, 100, 100], STANDARD), (120, [104, 104, 100], STANDARD),
                      (240, [100, 100, 100], None)], 3)),
        breathe,
    ]
    # one slow sparkle orbit
    orb = group([ellipse(12), fill(opacity=60)], nm="g")
    orb_g = group([orb], nm="orbit")
    orb_g["it"][-1]["p"] = static([0, -118])
    orbit_l = layer(3, "orbit", [orb_g], op=OP, p=static([cx, cy, 0]),
                    r=anim([(0, [0], LINEAR), (240, [360], None)], 1))
    layers.append(orbit_l)
    dump("find_idle.json", root("find_idle", 400, 400, OP, layers))


# ------------------------------------------------------------------ scan radar
def gen_scan_radar():
    OP = 240
    cx = cy = 200
    layers = []
    for i, st in enumerate((0, 40, 80, 120)):
        layers.append(ring_layer(i + 1, cx, cy, 230, 3, 160, st, op=55))
    sweep = layer(5, "sweep", [group([wedge(0, 0, 148, 14, r0=40), fill(opacity=16)], nm="g")], op=OP,
                  p=static([cx, cy, 0]), r=anim([(0, [0], LINEAR), (240, [360], None)], 1))
    layers.append(sweep)
    for i, theta in enumerate((20, 130, 250)):
        t_hit = int(theta / 360 * OP)
        a = math.radians(theta - 90)
        bx, by = cx + 108 * math.cos(a), cy + 108 * math.sin(a)
        core = group([ellipse(8), fill(color=WHITE, opacity=90)], nm="core")
        halo = group([ellipse(16), fill(opacity=40)], nm="halo")
        g = group([halo, core], nm=f"blip{i}")
        g["it"][-1]["p"] = static([bx, by])
        g["it"][-1]["s"] = anim([(t_hit, [40, 40], OVERSHOOT), (t_hit + 10, [110, 110], STANDARD),
                                 (t_hit + 44, [90, 90], None)], 2)
        g["it"][-1]["o"] = anim([(max(0, t_hit - 12), [0], DECEL), (t_hit + 4, [90], DECEL),
                                 (t_hit + 48, [0], None)], 1)
        layers.append(layer(6 + i, f"blip{i}", [g], op=OP))
    # Bluetooth glyph from the Material Design icon (Apache-2.0), filled — a
    # stroked polyline turns to mush at scan-screen sizes. Coordinates: the
    # 24dp icon path scaled ×4.5 about (12,12); holes reversed against the
    # outer contour for non-zero winding.
    glyph_outer = path([
        (25.7, -19.3), (0, -45), (-4.5, -45), (-4.5, -10.8), (-25.2, -31.5),
        (-31.5, -25.2), (-6.3, 0), (-31.5, 25.2), (-25.2, 31.5), (-4.5, 10.8),
        (-4.5, 45), (0, 45), (25.7, 19.3), (6.3, 0)], closed=True)
    glyph_hole_top = path([(4.5, -27.8), (13, -19.3), (4.5, -10.8)], closed=True)
    glyph_hole_bottom = path([(13, 19.3), (4.5, 27.8), (4.5, 10.8)], closed=True)
    bt = group([glyph_outer, glyph_hole_top, glyph_hole_bottom, fill()], nm="bt")
    layers.append(layer(9, "glyph", glow(cx, cy, 40) + [bt], op=OP,
                        p=static([cx, cy, 0]),
                        s=anim([(0, [160, 160, 100], STANDARD), (120, [172, 172, 100], STANDARD),
                                (240, [160, 160, 100], None)], 3)))
    ticks = group([ellipse(330), stroke(2, opacity=15, dashes=(2, 24))], nm="ticks")
    layers.append(layer(10, "ticks", [ticks], op=OP, p=static([cx, cy, 0])))
    dump("scan_radar.json", root("scan_radar", 400, 400, OP, layers))


# ----------------------------------------------------------------- empty state
def gen_empty_earbuds():
    OP = 240
    layers = []

    def bud(nm, ind, x, y, tilt, phase):
        head = group([ellipse(52), gradient_fill([-26, -26], [26, 26], ACCENT, ACCENT)], nm="head", o=72)
        glint = group([ellipse(9), fill(color=WHITE, opacity=50)], nm="glint", p=[-9, -10])
        stem = group([rect(20, 52, 10), gradient_fill([-10, -26], [10, 26], ACCENT, ACCENT)], nm="stem", o=70)
        stem["it"][-1]["p"] = static([10, 44])
        stem["it"][-1]["r"] = static(tilt)
        body = group([head, glint, stem], nm="bud")
        return layer(ind, nm, [body], op=OP,
                     p=anim([(phase, [x, y, 0], STANDARD), (phase + 120, [x, y + 13, 0], STANDARD),
                             (phase + 240, [x, y, 0], None)], 3),
                     r=anim([(phase, [-2.5], STANDARD), (phase + 120, [2.5], STANDARD),
                             (phase + 240, [-2.5], None)], 1))

    layers.append(bud("budL", 1, 148, 168, 24, 0))
    layers.append(bud("budR", 2, 252, 168, -24, 60))

    # ground shadow — shrinks/lightens as the buds rise
    shadow = group([ellipse(190, 34), fill(opacity=16)], nm="g")
    layers.append(layer(3, "shadow", [shadow], op=OP, p=static([200, 352, 0]),
                        s=anim([(0, [104, 100, 100], STANDARD), (120, [88, 88, 100], STANDARD),
                                (240, [104, 100, 100], None)], 3),
                        o=anim([(0, [18], STANDARD), (120, [11], STANDARD), (240, [18], None)], 1)))

    case_body = group([rect(170, 118, 52),
                       gradient_fill([0, -59], [0, 59], ACCENT, ACCENT)], nm="case", o=20)
    seam = group([rect(170, 6, 3), fill(opacity=40)], nm="seam")
    led = group([rect(26, 6, 3), fill(opacity=70)], nm="led")
    led["it"][-1]["p"] = static([0, -22])
    led["it"][-1]["o"] = anim([(0, [25], STANDARD), (60, [90], STANDARD), (120, [25], STANDARD),
                               (180, [90], STANDARD), (240, [25], None)], 1)
    layers.append(layer(4, "case", [case_body, seam, led], op=OP, p=static([200, 286, 0])))

    # sparkles
    for i, (sx, sy, sz, phase) in enumerate([(92, 118, 11, 30), (308, 102, 8, 110), (286, 250, 9, 190)]):
        sp = group([ellipse(sz * 2), fill()], nm="g")
        layers.append(layer(7 + i, f"spark{i}", [sp], op=OP,
                            p=anim([(phase, [sx, sy, 0], STANDARD), (phase + 120, [sx, sy - 16, 0], STANDARD),
                                    (phase + 240, [sx, sy, 0], None)], 3),
                            o=anim([(phase, [0], DECEL), (phase + 55, [80], DECEL), (phase + 120, [0], None)], 1),
                            s=anim([(phase, [50, 50, 100], OVERSHOOT), (phase + 55, [110, 110, 100], STANDARD),
                                    (phase + 120, [60, 60, 100], None)], 3)))

    # music notes — appended last = backmost: they rise from behind the case
    # through the gap between the buds instead of crossing the artwork
    for i, phase in enumerate((0, 120)):
        x0 = 194 + i * 12
        drift = 8
        note_g = group([music_note()], nm=f"note{i}")
        note_g["it"][-1]["p"] = anim([(phase, [x0, 240], STANDARD), (phase + 60, [x0 + drift // 2, 178], STANDARD),
                                      (phase + 120, [x0 + drift, 122], None)], 2)
        note_g["it"][-1]["r"] = anim([(phase, [-6], STANDARD), (phase + 120, [8], None)], 1)
        note_g["it"][-1]["o"] = anim([(phase, [0], DECEL), (phase + 26, [80], STANDARD),
                                      (phase + 100, [80], STANDARD), (phase + 120, [0], None)], 1)
        layers.append(layer(5 + i, f"noteLayer{i}", [note_g], op=OP))
    dump("empty_earbuds.json", root("empty_earbuds", 400, 400, OP, layers))


# ------------------------------------------------------------------ connecting
def gen_connecting():
    OP = 160
    W, H = 480, 240
    layers = []

    def capsule(nm, ind, x, phase):
        head = group([ellipse(56), gradient_fill([-28, -28], [28, 28], ACCENT, ACCENT)], nm="head", o=76)
        glint = group([ellipse(10), fill(color=WHITE, opacity=45)], nm="glint", p=[-10, -11])
        stem = group([rect(22, 54, 11), gradient_fill([-11, -27], [11, 27], ACCENT, ACCENT)], nm="stem", o=72)
        stem["it"][-1]["p"] = static([0, 46])
        body = group([head, glint, stem], nm="bud")
        return layer(ind, nm, [body], op=OP,
                     p=anim([(phase, [x, 118, 0], STANDARD), (phase + 80, [x, 108, 0], STANDARD),
                             (phase + 160, [x, 118, 0], None)], 3),
                     s=anim([(phase, [100, 100, 100], STANDARD), (phase + 80, [110, 110, 100], STANDARD),
                             (phase + 160, [100, 100, 100], None)], 3))

    layers.append(capsule("budL", 1, 92, 0))
    layers.append(capsule("budR", 2, 388, 80))

    # curved dashed link with marching dashes (period 28 -> 2 cycles per loop)
    link = group([path([[-104, 26], [104, 26]], tangents=([[0, -52], [0, -52]], [[0, -52], [0, -52]])),
                  stroke(6, dashes=(16, 12), dash_offset=0)], nm="g")
    link["it"][1]["d"][2]["v"] = anim([(0, [0], LINEAR), (OP, [-56], None)], 1)
    layers.append(layer(3, "link", [link], op=OP, p=static([240, 110, 0])))

    # radiating rings from the target (right) bud
    for i, st in enumerate((0, 40)):
        layers.append(ring_layer(4 + i, 388, 112, 120, 3, 80, st, op=45, grow=(80, 150)))

    # orbiting particles around the source (left) bud
    for i, phase in enumerate((0, 80)):
        orb = group([ellipse(11), fill(opacity=55)], nm="g")
        orb_g = group([orb], nm=f"orbit{i}")
        orb_g["it"][-1]["p"] = static([0, -52])
        layers.append(layer(6 + i, f"orbit{i}", [orb_g], op=OP, p=static([92, 112, 0]),
                            r=anim([(phase, [0], LINEAR), (phase + 160, [360], None)], 1)))
    dump("connecting.json", root("connecting", W, H, OP, layers))


# -------------------------------------------------------------- success (once)
def gen_connected_success():
    OP = 110
    S = 360
    cx = cy = S // 2
    layers = []

    # confetti: mixed circles / rounded rects / triangles, staggered, gravity dip
    palette_op = (100, 75, 60)
    for i in range(12):
        th = i * (math.pi * 2 / 12) + 0.26
        r0, r1 = 58, 148 + (14 if i % 3 == 0 else 0)
        x0, y0 = r0 * math.cos(th), r0 * math.sin(th)
        x1, y1 = r1 * math.cos(th), r1 * math.sin(th) + 14  # slight sag
        kind = i % 3
        if kind == 0:
            shape = ellipse(13)
        elif kind == 1:
            shape = rect(16, 10, 4)
        else:
            shape = path([[-8, 6], [0, -9], [8, 6]], closed=True)
        dot_tr = {"ty": "tr", "p": anim([(16, [x0, y0], DECEL), (52, [x1, y1], None)], 2),
                  "a": static([0, 0]), "s": static([100, 100]),
                  "r": anim([(16, [0], LINEAR), (60, [200 if i % 2 else -200], None)], 1),
                  "o": static(100), "sk": static(0), "sa": static(0), "nm": "tr"}
        fl = fill(opacity=palette_op[i % 3])
        dot = {"ty": "gr", "it": [shape, fl, dot_tr], "nm": f"cf{i}", "np": 3,
               "cix": 2, "bm": 0, "ix": 1, "hd": False}
        fade = {"ty": "tr", "p": static([cx, cy]), "a": static([0, 0]), "s": static([100, 100]),
                "r": static(0), "o": anim([(16, [100], DECEL), (44, [100], DECEL), (66, [0], None)], 1),
                "sk": static(0), "sa": static(0), "nm": "tr"}
        layers.append({"ddd": 0, "ind": i + 1, "ty": 4, "nm": f"confetti{i}", "sr": 1,
                       "ks": {"o": static(100), "r": static(0), "p": static([cx, cy, 0]),
                              "a": static([0, 0, 0]), "s": static([100, 100, 100])},
                       "ao": 0,
                       "shapes": [{"ty": "gr", "it": [dot, fade], "nm": f"cfg{i}", "np": 2,
                                   "cix": 2, "bm": 0, "ix": 1, "hd": False}],
                       "ip": 0, "op": OP, "st": 0, "bm": 0})

    # radiant expanding ring
    layers.append(ring_layer(20, cx, cy, 200, 4, 50, 18, op=55, grow=(36, 120)))
    # circle stroke drawn + overshoot scale
    circle = group([ellipse(120), trim([(0, [0], DECEL), (22, [100], None)]), stroke(8)], nm="g")
    layers.append(layer(21, "circle", [circle], op=OP, p=static([cx, cy, 0]),
                        s=anim([(0, [55, 55, 100], OVERSHOOT), (20, [113, 113, 100], STANDARD),
                                (32, [100, 100, 100], None)], 3)))
    # check draw-in with tiny pop
    check = group([path([[-36, 2], [-10, 28], [38, -24]]),
                   trim([(14, [0], DECEL), (44, [100], None)]), stroke(11)], nm="check")
    check_g = group([check], nm="checkG")
    check_g["it"][-1]["s"] = anim([(14, [86, 86], OVERSHOOT), (40, [103, 103], STANDARD),
                                   (52, [100, 100], None)], 2)
    layers.append(layer(22, "check", glow(cx, cy, 44, (8, 13, 20)) + [check_g], op=OP,
                        p=static([cx, cy + 2, 0])))
    dump("connected_success.json", root("connected_success", S, S, OP, layers))


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    gen_scan_radar()
    gen_empty_earbuds()
    gen_connecting()
    gen_connected_success()
    gen_find_sonar()
    gen_find_idle()
    print("done")
