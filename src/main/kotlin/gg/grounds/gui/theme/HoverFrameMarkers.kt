package gg.grounds.gui.theme

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration

/** Width of every vanilla container window. */
const val CONTAINER_WIDTH: Int = 176

/** Height of a generic container window with [rows] rows of storage. */
fun containerHeight(rows: Int): Int = 114 + rows * 18

/**
 * The marker glyph that draws frame [id] with its top-left corner at ([x], [y]), measured in pixels
 * from the GUI window's own top-left.
 *
 * Append this to a line of an item's lore, or use it as the item's name. The client renders a
 * tooltip only for the slot under the cursor, so the outline appears exactly while that item is
 * hovered and never otherwise — and putting the same marker on several items makes all of them
 * raise the same outline, which is how a shape spanning multiple slots behaves as one control.
 *
 * The glyph is invisible where it sits: a space after it cancels its advance, so the tooltip is not
 * a pixel wider for carrying it, and the shader moves the glyph itself out of the tooltip entirely.
 */
fun Theme.frameMarker(
    id: String,
    x: Int,
    y: Int,
    imageWidth: Int = CONTAINER_WIDTH,
    imageHeight: Int,
    tint: String? = null,
    tintIndex: Int = paletteIndex(tint),
): Component {
    val index = frames.map { it.id }.sorted().indexOf(id)
    require(index >= 0) { "no frame '$id' in theme '$namespace'" }
    require(imageWidth % 2 == 0 && imageHeight % 2 == 0) {
        "window is ${imageWidth}x$imageHeight; the shader halves those, so both must be even"
    }

    // The shader places the sprite's top-left at (screen centre + offset). The centre is the one
    // thing the server cannot know — the window is placed against a screen only the client has
    // measured — so the shader supplies it and the server supplies everything else.
    val offsetX = x - imageWidth / 2
    val offsetY = y - imageHeight / 2
    require(offsetX in -128..127 && offsetY in -128..127) {
        "frame '$id' at ($x, $y) is ($offsetX, $offsetY) from the centre of a " +
            "${imageWidth}x$imageHeight window, outside the signed byte a marker can carry"
    }

    val font = Key.key(namespace, "hoverframe")
    // Identity comes from pixels inside the sprite, so the whole colour is free to carry the
    // payload: red and green the offset, blue a palette index. Zero leaves the sprite's own colours
    // alone, which is what every marker did before tinting existed.
    require(tintIndex in 0..255) { "payload byte is 0..255, got $tintIndex" }
    val payload = ((offsetX + 128) shl 16) or ((offsetY + 128) shl 8) or tintIndex
    val glyph =
        Component.text(
            String(Character.toChars(FRAME_GLYPH_BASE + index)),
            Style.style()
                .font(font)
                .color(TextColor.color(payload))
                // A drop shadow would emit a second quad carrying a quarter-brightness colour —
                // same sprite, so the shader would relocate it too, to the wrong place.
                .shadowColor(ShadowColor.none())
                // Bold emits the glyph twice at one position, doubling the outline's opacity.
                .decoration(TextDecoration.BOLD, false)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.OBFUSCATED, false)
                .build(),
        )
    val cancel =
        Component.text(
            String(Character.toChars(FRAME_SPACE_BASE + index)),
            Style.style().font(font).shadowColor(ShadowColor.none()).build(),
        )
    return Component.empty().append(glyph).append(cancel)
}

/**
 * The marker for frame [id] positioned over the block of slots between [fromSlot] and [toSlot] in a
 * chest of [rows] rows — the two slots are opposite corners of the region.
 *
 * The artwork decides what is actually drawn there: a rectangle outlines the block, a triangle
 * outlines a triangle. Only the block's top-left corner is communicated.
 *
 * [outset] moves the artwork out by that many pixels on every side, for a ring that stands clear of
 * the shape rather than sitting on its edge. The artwork has to be correspondingly larger.
 */
fun Theme.chestFrame(
    id: String,
    fromSlot: Int,
    toSlot: Int,
    rows: Int,
    outset: Int = 0,
): Component {
    require(rows in 1..6) { "a chest has 1..6 rows, got $rows" }
    val slots = rows * 9
    require(fromSlot in 0 until slots && toSlot in 0 until slots) {
        "slots $fromSlot and $toSlot must lie inside a $rows-row chest"
    }
    val columns = listOf(fromSlot % 9, toSlot % 9)
    val lines = listOf(fromSlot / 9, toSlot / 9)
    return frameMarker(
        id = id,
        x = 8 + 18 * columns.min() - outset,
        y = 18 + 18 * lines.min() - outset,
        imageHeight = containerHeight(rows),
    )
}

/** Position of [tint] in the theme's palette, one-based; 0 when no tint was asked for. */
fun Theme.paletteIndex(tint: String?): Int {
    if (tint == null) return 0
    val index = colours.map { it.name }.sorted().indexOf(tint)
    // Sorted, for the reason frames are: declaring a colour must not renumber the ones already in
    // packs clients have cached.
    require(index >= 0) { "no colour '$tint' in theme '$namespace'" }
    return index + 1
}

/**
 * A marker for the meter [id], filled to [fill] of its length.
 *
 * One sprite serves every value. The alternative is a frame per step, and a bar worth looking at
 * has more steps than a pack wants to carry — a 100px bar would be a hundred sprites to say what a
 * byte says.
 *
 * A meter cannot be tinted: the payload's low byte carries the fill, which is the same byte a tint
 * would have used. Colour it in the sprite instead, which is where a bar's colour usually belongs
 * anyway — a gradient along its length costs nothing there and is impossible from a palette index.
 *
 * Zero draws nothing at all rather than a stray line of pixels.
 *
 * @param fill 0.0 to 1.0; values outside are clamped, because a percentage arriving at 1.02 from
 *   arithmetic upstream should show a full bar rather than throw at whoever opened the screen
 */
fun Theme.meterMarker(
    id: String,
    x: Int,
    y: Int,
    fill: Double,
    imageWidth: Int = CONTAINER_WIDTH,
    imageHeight: Int,
): Component {
    val frame = frames.firstOrNull { it.id == id }
    require(frame != null) { "no frame '$id' in theme '$namespace'" }
    require(frame.meter != null) {
        "frame '$id' is not a meter; declare it with frame(\"$id\", ..., meter = ...)"
    }
    val step = Math.round(fill.coerceIn(0.0, 1.0) * 255.0).toInt()
    return frameMarker(id, x, y, imageWidth, imageHeight, tintIndex = step)
}
