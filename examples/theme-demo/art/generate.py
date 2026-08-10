#!/usr/bin/env python3
"""Regenerates the demo's placeholder artwork.

The panel is deliberately a calibration target rather than a pretty backdrop: the
library's title offsets are conventions that have never been checked against a
running client, so the demo's job is to make an error *readable*. Ticks every 8px
along the top and left edge, plus a slot grid at the positions a 3-row container
uses, turn "it looks off" into "it is 3px too far left".

Run: python3 generate.py
"""

from pathlib import Path
import zlib
import struct

HERE = Path(__file__).parent

# A generic container is 114px of chrome plus 18px per row; 3 rows -> 168.
# Container slots start at (7, 17) and step 18px. Both are conventions taken from
# vanilla's layout, and both are exactly what the demo exists to verify.
PANEL_W, PANEL_H = 176, 168
SLOT_ORIGIN = (7, 17)
SLOT_PITCH = 18
SLOT_COLS, SLOT_ROWS = 9, 3

BG = (40, 44, 52, 255)
EDGE = (255, 0, 255, 255)
SLOT = (118, 118, 130, 255)
TICK = (0, 220, 220, 255)
TICK_MAJOR = (255, 255, 255, 255)
GOLD = (255, 190, 40, 255)
GOLD_DARK = (120, 88, 10, 255)
TOOLTIP_BG = (22, 16, 40, 240)

# A second, deliberately unmistakable outline: the point of the hover demo is telling two
# tooltip styles apart at a glance, so this one differs in hue *and* in line structure.
STEEL = (120, 230, 255, 255)
STEEL_DARK = (18, 52, 74, 255)
STEEL_BG = (10, 18, 30, 240)
WHITE = (255, 255, 255, 255)


class Canvas:
    def __init__(self, width, height, fill=(0, 0, 0, 0)):
        self.w, self.h = width, height
        self.px = [[fill for _ in range(width)] for _ in range(height)]

    def set(self, x, y, color):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = color

    def rect(self, x, y, w, h, color):
        for dy in range(h):
            for dx in range(w):
                self.set(x + dx, y + dy, color)

    def outline(self, x, y, w, h, color):
        for dx in range(w):
            self.set(x + dx, y, color)
            self.set(x + dx, y + h - 1, color)
        for dy in range(h):
            self.set(x, y + dy, color)
            self.set(x + w - 1, y + dy, color)

    def write(self, path):
        raw = b"".join(
            b"\x00" + b"".join(struct.pack("BBBB", *p) for p in row) for row in self.px
        )

        def chunk(tag, data):
            body = tag + data
            return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))

        png = (
            b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", self.w, self.h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9))
            + chunk(b"IEND", b"")
        )
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(png)
        print(f"{path.relative_to(HERE)}  {self.w}x{self.h}")


def load_rgba(name):
    """Vanilla's own widget sprite, dumped to raw RGBA by art/vanilla/Dump.java.

    Reading the client's actual pixels rather than approximating them is the whole reason
    these buttons look native instead of merely Minecraft-ish.
    """
    b = (HERE / "vanilla" / f"{name}.rgba").read_bytes()
    w = int.from_bytes(b[0:4], "big")
    h = int.from_bytes(b[4:8], "big")
    px = []
    for i in range(w * h):
        argb = int.from_bytes(b[8 + i * 4 : 12 + i * 4], "big")
        px.append(((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >> 24) & 255))
    return w, h, px


def nine_slice(canvas, sprite, x, y, w, h, border):
    """Draws a vanilla sprite at an arbitrary size, the way the client's own nine-slice does.

    Corners are copied 1:1, edges are tiled along their axis and the centre fills the rest —
    so a button stretched across five slots keeps its 3px bevel crisp instead of smearing it.
    """
    sw, sh, px = sprite

    def at(sx, sy):
        return px[sy * sw + sx]

    def source(i, span, size):
        # Corner: straight through. Edge/centre: walk the sprite's middle band and wrap.
        if i < border:
            return i
        if i >= span - border:
            return size - (span - i)
        middle = size - 2 * border
        return border + ((i - border) % middle)

    for dy in range(h):
        for dx in range(w):
            colour = at(source(dx, w, sw), source(dy, h, sh))
            if colour[3]:
                canvas.set(x + dx, y + dy, colour)


BUTTON = None
BUTTON_HL = None


def bevel(canvas, x, y, w, h, face, light, shadow, thickness=2):
    """A raised vanilla-style panel: lit from the top-left, shadowed bottom-right."""
    canvas.rect(x, y, w, h, face)
    for t in range(thickness):
        canvas.rect(x + t, y + t, w - 2 * t, 1, light)
        canvas.rect(x + t, y + t, 1, h - 2 * t, light)
        canvas.rect(x + t, y + h - 1 - t, w - 2 * t, 1, shadow)
        canvas.rect(x + w - 1 - t, y + t, 1, h - 2 * t, shadow)


def well(canvas, x, y, size=18):
    """A sunken slot, drawn exactly the way vanilla draws one.

    Inverse of a raised panel — shadow on the top and left, light on the bottom and right —
    and the two opposite corners stay the interior colour rather than joining either edge.
    That corner detail is small and it is most of why a vanilla slot reads as a hole.
    """
    canvas.rect(x, y, size, size, WELL_FACE)
    canvas.rect(x, y, size - 1, 1, WELL_DARK)
    canvas.rect(x, y, 1, size - 1, WELL_DARK)
    canvas.rect(x + 1, y + size - 1, size - 1, 1, WELL_LIGHT)
    canvas.rect(x + size - 1, y + 1, 1, size - 1, WELL_LIGHT)


def window(w, h):
    """The window chrome every themed panel starts from."""
    c = Canvas(w, h, GUI_FACE)
    bevel(c, 0, 0, w, h, GUI_FACE, GUI_LIGHT, GUI_SHADOW, thickness=3)
    c.outline(0, 0, w, h, GUI_EDGE)
    return c


def player_wells(c, image_height):
    """The three inventory rows and the hotbar, at the offsets every container shares.

    Derived rather than guessed: ChestMenu puts the player inventory at
    `inventoryTop = 18 + rows*18 + 13` and the hotbar at `inventoryTop + 58`, while
    `imageHeight = 114 + rows*18` — so inventoryTop is `imageHeight - 83`. A well sits one
    pixel up and left of the slot it holds, which is the pixel this was previously missing:
    every row sat one low against the real slots.
    """
    top = image_height - 84
    for row in range(3):
        for col in range(9):
            well(c, 7 + 18 * col, top + 18 * row)
    for col in range(9):
        well(c, 7 + 18 * col, top + 58)


def panel():
    """A plain vanilla-grey window: no chest tiles, no slot boxes, nothing but chrome.

    The panel is opaque across the whole window, so it covers the container texture the
    client draws underneath. What is left is an empty grey frame with one button in it.
    """
    c = window(PANEL_W, PANEL_H)
    # No container wells here on purpose: this screen is meant to read as an empty window with
    # two controls in it, not as an inventory. The player's own rows stay, because the client
    # draws real slots there whatever the artwork says.
    player_wells(c, PANEL_H)
    shape(c)
    c.write(HERE / "panels" / "shop.png")


def slot_highlight():
    """A glow around the hovered item, offered as an alternative to vanilla's flat box.

    Blitted 24x24 at (slot.x - 4, slot.y - 4), so the item's own 16x16 area sits at offset
    (4, 4) and the remaining 4px on each side is the room a glow needs to spill past it.

    Shipping this is opt-in for a reason: it overrides a vanilla sprite, so it changes the
    hover box in every container the player opens, their own inventory included. There is
    no way to scope it to one GUI, so leaving it out is what keeps every other inventory
    behaving exactly as the player expects.
    """
    back = Canvas(24, 24)
    for y in range(24):
        for x in range(24):
            dx = max(4 - x, x - 19, 0)
            dy = max(4 - y, y - 19, 0)
            distance = (dx * dx + dy * dy) ** 0.5
            if distance == 0:
                alpha = 70
            elif distance <= 4:
                alpha = int(200 * (1 - distance / 4) ** 1.6)
            else:
                alpha = 0
            if alpha > 0:
                back.set(x, y, (150, 225, 255, alpha))
    back.write(HERE / "highlight" / "back.png")

    # Nothing on the front layer: a glow belongs behind the item, not over it.
    Canvas(24, 24).write(HERE / "highlight" / "front.png")

    # Both layers blank. Shipping these removes vanilla's hover box everywhere, which is the only
    # way to stop it flashing during the frames a themed screen has no tooltip to hang markers on —
    # the client predicts a pickup, every marker disappears at once, and whatever vanilla draws
    # underneath becomes visible. Nothing drawn, nothing to flash.
    Canvas(24, 24).write(HERE / "highlight" / "blank_back.png")
    Canvas(24, 24).write(HERE / "highlight" / "blank_front.png")


def invisible():
    """A transparent tooltip skin and a transparent item model.

    Both exist so something can be present without being seen. The frame markers ride in
    a tooltip, so the tooltip has to be rendered — but nothing says it has to be visible.
    Same for the items in the button's nine slots: they must be items for the client to
    build a tooltip at all, and they must not look like items.
    """
    Canvas(24, 24).write(HERE / "tooltips" / "blank_bg.png")
    Canvas(24, 24).write(HERE / "tooltips" / "blank_frame.png")
    Canvas(16, 16).write(HERE / "icons" / "blank.png")


# Slot i sits at (8 + 18*(i%9), 18 + 18*(i/9)) and is 16x16, so a block of slots covers this.
def block(col0, row0, col1, row1):
    return (8 + 18 * col0, 18 + 18 * row0, 8 + 18 * col1 + 16, 18 + 18 * row1 + 16)


# The square button spans columns 3..5 of every row; the triangle sits to its left, columns 0..2.
SHAPE_LEFT, SHAPE_TOP, SHAPE_RIGHT, SHAPE_BOTTOM = block(3, 0, 5, 2)
TRI_LEFT, TRI_TOP, TRI_RIGHT, TRI_BOTTOM = block(0, 0, 2, 2)

# Sampled out of the client's own generic_54.png rather than invented, so a themed window sits
# in the game instead of next to it.
GUI_FACE = (198, 198, 198, 255)     # C6C6C6 — the window's face
GUI_LIGHT = (255, 255, 255, 255)    # FFFFFF — lit edge, top and left
GUI_SHADOW = (85, 85, 85, 255)      # 555555 — shadowed edge, bottom and right
GUI_EDGE = (0, 0, 0, 255)           # 000000 — the 1px outline around everything
WELL_DARK = (55, 55, 55, 255)       # 373737 — a slot's shadowed top-left
WELL_FACE = (139, 139, 139, 255)    # 8B8B8B — a slot's interior
HOVER_TINT = 70
"""How far a hovered tile is lifted toward white, as an alpha out of 255."""

WELL_LIGHT = (255, 255, 255, 255)   # FFFFFF — a slot's lit bottom-right
BUTTON_FACE = (206, 206, 206, 255)


# The ring stands 2px clear of the shape on every side, so it reads as focus rather than as an
# outline glued to the edge.
FRAME_OUTSET = 2
FRAME_W = SHAPE_RIGHT - SHAPE_LEFT + 2 * FRAME_OUTSET
FRAME_H = SHAPE_BOTTOM - SHAPE_TOP + 2 * FRAME_OUTSET

# Dark outside, light inside: the same two-tone edge vanilla uses for its own chrome, which is what
# makes the ring look like part of the game instead of an overlay on top of it.
RING_DARK = (55, 55, 55, 255)
RING_LIGHT = (255, 255, 255, 235)


def frames():
    """Focus rings drawn in each shape's own form, at the size of the region they mark.

    The generator wraps these in data pixels the shader reads back, so one glyph draws the
    whole ring at its true dimensions. That is why a triangle gets a triangular ring rather
    than a box: nothing here is constrained to a rectangle.
    """
    square = Canvas(FRAME_W, FRAME_H)
    square.outline(0, 0, FRAME_W, FRAME_H, RING_DARK)
    square.outline(1, 1, FRAME_W - 2, FRAME_H - 2, RING_LIGHT)
    square.write(HERE / "frame" / "square.png")

    # Same geometry as the triangle in the panel, grown by the outset so the ring sits outside
    # the shape's own edges instead of on them.
    tri = Canvas(FRAME_W, FRAME_H)
    for ring, color in ((0, RING_DARK), (1, RING_LIGHT)):
        apex = (FRAME_W - 1) / 2
        for y in range(ring, FRAME_H - ring):
            span = (y - ring) / max(1, FRAME_H - 1 - 2 * ring)
            half = ((FRAME_W - 1) / 2 - ring) * span
            tri.set(int(round(apex - half)), y, color)
            tri.set(int(round(apex + half)), y, color)
        tri.rect(ring, FRAME_H - 1 - ring, FRAME_W - 2 * ring, 1, color)
    tri.write(HERE / "frame" / "triangle.png")


def shape(canvas):
    """One raised button covering a 3x3 block of slots.

    Drawn entirely in the panel, so it ignores the slot grid the way a real multi-slot
    control would — the nine slots underneath hold invisible items whose only job is to
    give the client a tooltip to hang the frame markers on.
    """
    bevel(
        canvas,
        SHAPE_LEFT - 2,
        SHAPE_TOP - 2,
        SHAPE_RIGHT - SHAPE_LEFT + 4,
        SHAPE_BOTTOM - SHAPE_TOP + 4,
        BUTTON_FACE,
        GUI_LIGHT,
        GUI_SHADOW,
    )
    triangle(canvas)


def triangle(canvas):
    """A second control that is not a rectangle, so the frame is visibly a bounding box.

    Apex at the top centre, base along the bottom. Its hover region is the slots the
    triangle actually covers, not its whole block — which is why hovering the top-left
    slot does nothing while the slot below it lights the shape up.
    """
    apex_x = (TRI_LEFT + TRI_RIGHT) / 2
    height = TRI_BOTTOM - TRI_TOP
    for y in range(TRI_TOP, TRI_BOTTOM):
        t = (y - TRI_TOP) / height
        half = (TRI_RIGHT - TRI_LEFT) / 2 * t
        for x in range(TRI_LEFT, TRI_RIGHT):
            offset = abs(x - apex_x)
            if offset <= half:
                # Lit on the left slope and along the top, shadowed on the right and at the base —
                # the same light direction as the button's bevel.
                if offset > half - 2:
                    canvas.set(x, y, GUI_LIGHT if x < apex_x else GUI_SHADOW)
                elif y > TRI_BOTTOM - 3:
                    canvas.set(x, y, GUI_SHADOW)
                else:
                    canvas.set(x, y, BUTTON_FACE)


def coin():
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            d = (dx * dx + dy * dy) ** 0.5
            if d <= 6.5:
                c.set(x, y, GOLD if d <= 5.0 else GOLD_DARK)
    c.write(HERE / "icons" / "coin.png")


def tooltip():
    # 24x24 with a 4px border, so nine-slice keeps the corners crisp at any size.
    bg = Canvas(24, 24, TOOLTIP_BG)
    bg.write(HERE / "tooltips" / "gold_bg.png")

    frame = Canvas(24, 24)
    frame.rect(0, 0, 24, 4, GOLD_DARK)
    frame.rect(0, 20, 24, 4, GOLD_DARK)
    frame.rect(0, 0, 4, 24, GOLD_DARK)
    frame.rect(20, 0, 4, 24, GOLD_DARK)
    frame.outline(1, 1, 22, 22, GOLD)
    frame.write(HERE / "tooltips" / "gold_frame.png")

    # Steel: same 4px nine-slice border, different colour and a doubled inner line, so which
    # style an item carries is obvious without comparing screenshots.
    steel_bg = Canvas(24, 24, STEEL_BG)
    steel_bg.write(HERE / "tooltips" / "steel_bg.png")

    steel = Canvas(24, 24)
    steel.rect(0, 0, 24, 4, STEEL_DARK)
    steel.rect(0, 20, 24, 4, STEEL_DARK)
    steel.rect(0, 0, 4, 24, STEEL_DARK)
    steel.rect(20, 0, 4, 24, STEEL_DARK)
    steel.outline(0, 0, 24, 24, STEEL)
    # Inset 3 keeps this inside the 4px border region, so nine-slice never stretches it.
    steel.outline(3, 3, 18, 18, WHITE)
    steel.write(HERE / "tooltips" / "steel_frame.png")


# A button occupies whole slots, so its artwork and its hit area cannot disagree. Slot (c, r)
# owns the 18x18 well at (7 + 18c, 17 + 18r); a button spanning columns c0..c1 of row r is
# exactly that rectangle widened.
MENU_BUTTONS = [
    ("shop", "Shop", "small", 1, 0, 3, 0),
    ("kits", "Kits", "small", 5, 0, 7, 0),
    ("play", "Play now", "wide", 1, 1, 7, 1),
    ("settings", "Settings", "small", 1, 2, 3, 2),
    ("profile", "Profile", "small", 5, 2, 7, 2),
]


def centre_slot(c0, r0, c1, r1):
    """The slot a button's icon sits in — the middle of the run, as the runtime also computes."""
    slots = [(c, r) for r in range(r0, r1 + 1) for c in range(c0, c1 + 1)]
    return slots[len(slots) // 2]


def button_rect(c0, r0, c1, r1):
    return (7 + 18 * c0, 17 + 18 * r0, 18 * (c1 - c0 + 1), 18 * (r1 - r0 + 1))


def slot_cover():
    """A patch that hides vanilla's hover box on a slot whose panel area is plain face.

    The client blits its highlight into a 24x24 box, but only the inner 16x16 of that sprite is
    ever opaque — measured, both layers, (4,4) to (19,19). So the patch is 16x16 on the slot's own
    item area: exactly the highlight's visible extent, and incapable of reaching a neighbour. At
    24x24 it bled four pixels in every direction and painted over the buttons next door.

    Only drawn while that slot is hovered, so at rest it adds nothing to the screen.
    """
    Canvas(16, 16, GUI_FACE).write(HERE / "frame" / "slot_cover.png")

    # The same patch with a hover tint already blended in.
    #
    # A marker cannot be a translucent hover on its own: vanilla's box is drawn before any tooltip,
    # so a see-through layer lands on top of it and you get the white box *plus* the tint. Replacing
    # it means covering it opaquely first — and once the cover is opaque, a second translucent layer
    # over it is just a colour that can be computed here instead.
    #
    # That only holds because the background is known: an empty tile in this menu is flat panel
    # face. Over a well or artwork the blend would have to be per-region, and at that point the
    # honest route is to blank vanilla's sprite pack-wide and hand each slot a real alpha.
    blended = tuple(v + (255 - v) * HOVER_TINT // 255 for v in GUI_FACE[:3]) + (255,)
    Canvas(16, 16, blended).write(HERE / "frame" / "slot_hover.png")


# The vanilla ASCII sheet is 16x16 cells of 8x8. Composing labels from the client's own glyphs
# means a button's text is the game's text, not a lookalike.
ASCII_ORDER = (
    "\x00" * 32
    + " !\"#$%&'()*+,-./0123456789:;<=>?"
    + "@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_"
    + "`abcdefghijklmnopqrstuvwxyz{|}~"
)


def ascii_glyph(sheet, ch):
    """One glyph, trimmed to its real width — which is how the client measures text too."""
    w, h, px = sheet
    index = ASCII_ORDER.find(ch)
    if index < 0:
        return [], 4
    cell = w // 16
    ox, oy = (index % 16) * cell, (index // 16) * cell
    pixels, right = [], 0
    for y in range(cell):
        for x in range(cell):
            colour = px[(oy + y) * w + ox + x]
            if colour[3]:
                pixels.append((x, y, colour))
                right = max(right, x)
    return pixels, (right + 2) if pixels else 4


def text_width(sheet, text):
    return sum(ascii_glyph(sheet, ch)[1] for ch in text)


def draw_text(canvas, sheet, text, x, y, colour=(255, 255, 255, 255), shadow=True):
    """Vanilla letterforms with vanilla's drop shadow, one pixel down and right."""
    pen = x
    for ch in text:
        pixels, advance = ascii_glyph(sheet, ch)
        if shadow:
            for gx, gy, _ in pixels:
                canvas.set(pen + gx + 1, y + gy + 1, (60, 60, 60, 255))
        for gx, gy, _ in pixels:
            canvas.set(pen + gx, y + gy, colour)
        pen += advance


def menu():
    """A menu whose buttons span several slots each, painted with vanilla's own button sprite.

    The sprite is nine-sliced exactly as the client does it, so a button stretched across
    seven slots keeps a crisp 3px bevel rather than a smeared one. Hover is not an outline
    here: the shader draws vanilla's `button_highlighted` over the same rectangle, which is
    what the game itself does when the cursor is on a button.
    """
    face = load_rgba("button")
    highlighted = load_rgba("button_highlighted")

    sheet = load_rgba("ascii")
    c = window(PANEL_W, PANEL_H)
    player_wells(c, PANEL_H)
    for _, text, _, c0, r0, c1, r1 in MENU_BUTTONS:
        x, y, w, h = button_rect(c0, r0, c1, r1)
        nine_slice(c, face, x, y, w, h, 3)
        # A vanilla button carries its label centred on its face, so ours does too — drawn from
        # the client's own ASCII sheet, so the letterforms are the game's rather than a lookalike.
        tw = text_width(sheet, text)
        draw_text(c, sheet, text, x + (w - tw) // 2, y + (h - 8) // 2)
    c.write(HERE / "panels" / "menu.png")

    # Layer 1 of the hover stack: the highlighted button, fully opaque, so it covers vanilla's
    # single-slot highlight box wherever the cursor lands inside a multi-slot button.
    for name, size in (("small", (54, 18)), ("wide", (126, 18))):
        lit = Canvas(size[0], size[1])
        nine_slice(lit, highlighted, 0, 0, size[0], size[1], 3)
        lit.write(HERE / "frame" / f"menu_face_{name}.png")

    # Layer 2: the label again, because layer 1 just painted over it. Same glyphs, same place.
    for name, text, _, c0, r0, c1, r1 in MENU_BUTTONS:
        tw = text_width(sheet, text)
        label = Canvas(tw + 1, 10)
        draw_text(label, sheet, text, 0, 0)
        label.write(HERE / "frame" / f"menu_label_{name}.png")


# A sectioned screen: two labelled groups, a small toolbar between them, one row each.
OV_H = 114 + 6 * 18
ACTIVE = (58, 118, 212, 255)
ACTIVE_LIGHT = (120, 174, 245, 255)
ACTIVE_DARK = (26, 66, 130, 255)
GROUP_EDGE = (139, 139, 139, 255)


def group_frame(c, col0, row0, col1, row1):
    """A hairline around a block of slots, the way a grouped inventory marks its sections."""
    x0, y0 = 7 + 18 * col0 - 1, 17 + 18 * row0 - 1
    x1, y1 = 7 + 18 * col1 + 18, 17 + 18 * row1 + 18
    c.outline(x0, y0, x1 - x0, y1 - y0, GROUP_EDGE)


# The grounds icon set: 16x16 with alpha, artwork centred within it, so it drops straight onto
# an 18x18 button face at a one pixel inset.
TOOLBAR_ICONS = ["arrow_left", "search", "plus", "minus", "refresh", "lock_closed", "settings"]


def blit(canvas, sprite, x, y):
    w, h, px = sprite
    for dy in range(h):
        for dx in range(w):
            colour = px[dy * w + dx]
            if colour[3]:
                canvas.set(x + dx, y + dy, colour)


def overview():
    sheet = load_rgba("ascii")
    c = window(PANEL_W, OV_H)

    def centred(text, y):
        draw_text(c, sheet, text, (PANEL_W - text_width(sheet, text)) // 2, y, (64, 64, 64, 255), shadow=False)

    # Section one: title on the sacrificed top row, two rows of wells under it. Seven columns
    # wide, inset one on each side, so the groups read as panels inside the window rather than
    # as the window's full width.
    centred("Spielübersicht", 22)
    for row in (1, 2):
        for col in range(1, 8):
            well(c, 7 + 18 * col, 17 + 18 * row)
    group_frame(c, 1, 1, 7, 2)

    # Toolbar: bare icons on the panel face. No button underneath — the icon is the button, and
    # its own silhouette is what the hover outlines.
    for i, name in enumerate(TOOLBAR_ICONS):
        blit(c, load_rgba(f"gicon_{name}"), 8 + 18 * (1 + i), 18 + 18 * 3)

    # Section two.
    centred("Teleport", 94)
    for col in range(1, 8):
        well(c, 7 + 18 * col, 17 + 18 * 5)
    group_frame(c, 1, 5, 7, 5)

    player_wells(c, OV_H)
    c.write(HERE / "panels" / "overview.png")

    # Vanilla's own hover value, measured off its sprite: white at alpha 96 over the item area.
    Canvas(16, 16, (255, 255, 255, 96)).write(HERE / "frame" / "ov_slot.png")

    # A patch of bare panel face, to blank vanilla's hover box on the toolbar. It wipes the icon
    # painted underneath as well, which is why the hover redraws it.
    Canvas(16, 16, GUI_FACE).write(HERE / "frame" / "ov_cover.png")

    # A hover patch per slot, cut out of the panel that was just drawn.
    #
    # The menu gets away with a single sprite because every empty tile there sits on flat face. This
    # screen does not: its empty tiles sit on face, on well grey, and on the section headings, whose
    # text runs straight through the top slot row. One flat patch would blank vanilla's box and take
    # "Spielübersicht" with it. Cutting each slot's own 16x16 out of the panel and tinting that
    # keeps whatever is underneath and still replaces the box.
    #
    # Cut for every slot rather than for the ones the layout leaves empty, so the generator holds no
    # opinion about the layout — that lives in Kotlin, and duplicating it here is how the two drift.
    #
    # Two families of the same cut-out: `ov_cover` at no tint at all, which makes a hovered tile
    # look exactly like an unhovered one, and `ov_hover` lifted by HOVER_TINT. Shipping both is what
    # makes /tint instant — the choice is which glyph the server names, not what the pack contains,
    # so flipping it costs no rebuild and no re-download.
    for slot in range(6 * 9):
        sx, sy = 8 + 18 * (slot % 9), 18 + 18 * (slot // 9)
        for name, tint in (("ov_cover", 0), ("ov_hover", HOVER_TINT)):
            patch = Canvas(16, 16)
            for dy in range(16):
                for dx in range(16):
                    r, g, b, alpha = c.px[sy + dy][sx + dx]
                    patch.set(dx, dy, tuple(v + (255 - v) * tint // 255 for v in (r, g, b)) + (alpha,))
            patch.write(HERE / "frame" / f"{name}_{slot}.png")

    # The hover: the icon's own outer contour. Dilating the alpha mask by one pixel and taking
    # away the mask itself leaves exactly the ring of empty pixels that touch the artwork, so the
    # outline follows the shape rather than boxing it. A second ring at a lower alpha softens it.
    # A 20x20 canvas with the icon at (2, 2) is what keeps the outer ring off the edge: `refresh`
    # reaches to within one pixel of its own bounds, so anything tighter clips its halo.
    for name in TOOLBAR_ICONS:
        w, h, px = load_rgba(f"gicon_{name}")
        mask = {(x + 2, y + 2) for y in range(h) for x in range(w) if px[y * w + x][3]}
        ring = Canvas(20, 20)
        for radius, colour in ((2, (87, 214, 236, 110)), (1, (27, 95, 190, 255))):
            for x, y in mask:
                for dy in range(-radius, radius + 1):
                    for dx in range(-radius, radius + 1):
                        at = (x + dx, y + dy)
                        if at not in mask and 0 <= at[0] < 20 and 0 <= at[1] < 20:
                            ring.set(at[0], at[1], colour)
        ring.write(HERE / "frame" / f"ov_outline_{name}.png")

    # Each icon again, for redrawing over the cover.
    for name in TOOLBAR_ICONS:
        cv = Canvas(16, 16)
        blit(cv, load_rgba(f"gicon_{name}"), 0, 0)
        cv.write(HERE / "frame" / f"ov_icon_{name}.png")


def storybook():
    """The storybook index, given the same window chrome as everything else.

    Five rows of wells for the entries and a button face under each navigation slot, so the
    index stops looking like a bare chest and starts looking like a screen.
    """
    face = load_rgba("button")
    h = 114 + 6 * 18
    c = window(PANEL_W, h)
    for row in range(5):
        for col in range(9):
            well(c, 7 + 18 * col, 17 + 18 * row)
    for col in (0, 8):
        nine_slice(c, face, 7 + 18 * col, 17 + 18 * 5, 18, 18, 3)
    player_wells(c, h)
    c.write(HERE / "panels" / "story.png")


def screens():
    """One panel per demo screen, sized to that screen's own window.

    Four screens with four different title anchors: a six-row chest and a hopper keep the
    container default of x=8, an anvil starts at 60, and a dispenser centres its title.
    Each panel is opaque to its right edge so the client's advance stays the full width.
    """
    for name, w, h, accent in (
        ("screen_shop", 176, 222, (86, 120, 78)),
        ("screen_toolbar", 176, 133, (120, 96, 60)),
        ("screen_forge", 176, 166, (110, 84, 84)),
        ("screen_centred", 176, 166, (78, 96, 124)),
    ):
        c = window(w, h)
        # A thin accent rule under the title, the one place this theme departs from vanilla.
        c.rect(7, 15, w - 14, 1, accent + (255,))
        if name == "screen_shop":
            for row in range(6):
                for col in range(9):
                    well(c, 7 + 18 * col, 17 + 18 * row)
        elif name == "screen_toolbar":
            for i in range(5):
                well(c, 43 + 18 * i, 19)
        player_wells(c, h)
        c.write(HERE / "panels" / f"{name}.png")


if __name__ == "__main__":
    panel()
    coin()
    tooltip()
    slot_highlight()
    frames()
    invisible()
    screens()
    menu()
    storybook()
    overview()
    slot_cover()
