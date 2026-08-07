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


def bevel(canvas, x, y, w, h, face, light, shadow, thickness=2):
    """A raised vanilla-style panel: lit from the top-left, shadowed bottom-right."""
    canvas.rect(x, y, w, h, face)
    for t in range(thickness):
        canvas.rect(x + t, y + t, w - 2 * t, 1, light)
        canvas.rect(x + t, y + t, 1, h - 2 * t, light)
        canvas.rect(x + t, y + h - 1 - t, w - 2 * t, 1, shadow)
        canvas.rect(x + w - 1 - t, y + t, 1, h - 2 * t, shadow)


def panel():
    """A plain vanilla-grey window: no chest tiles, no slot boxes, nothing but chrome.

    The panel is opaque across the whole window, so it covers the container texture the
    client draws underneath. What is left is an empty grey frame with one button in it.
    """
    c = Canvas(PANEL_W, PANEL_H, GUI_FACE)
    bevel(c, 0, 0, PANEL_W, PANEL_H, GUI_FACE, GUI_LIGHT, GUI_SHADOW, thickness=3)
    c.outline(0, 0, PANEL_W, PANEL_H, GUI_EDGE)

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

# Vanilla's inventory chrome, so a themed window sits in the game rather than on top of it.
GUI_FACE = (198, 198, 198, 255)
GUI_LIGHT = (255, 255, 255, 255)
GUI_SHADOW = (85, 85, 85, 255)
GUI_EDGE = (0, 0, 0, 255)
BUTTON_FACE = (208, 208, 208, 255)


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


if __name__ == "__main__":
    panel()
    coin()
    tooltip()
    slot_highlight()
    frames()
    invisible()
