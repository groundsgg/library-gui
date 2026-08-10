package gg.grounds.gui.demo.art

import gg.grounds.gui.art.hitSlots
import gg.grounds.gui.art.writeSprite
import java.awt.image.BufferedImage
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow

private const val GOLD = 0xFFFFBE28.toInt()

private const val GOLD_DARK = 0xFF78580A.toInt()

private const val TOOLTIP_BG = 0xF0161028.toInt()

/** A second outline that differs in hue *and* in line structure, so two styles are separable. */
private const val STEEL = 0xFF78E6FF.toInt()

private const val STEEL_DARK = 0xFF12344A.toInt()

private const val STEEL_BG = 0xF00A121E.toInt()

private const val BUTTON_FACE = 0xFFCECECE.toInt()

/** Dark outside, light inside: vanilla's own two-tone edge, which is why a ring reads as chrome. */
private const val RING_DARK = 0xFF373737.toInt()

private const val RING_LIGHT = 0xEBFFFFFF.toInt()

/** Slot i sits at (8 + 18*(i%9), 18 + 18*(i/9)) and is 16x16, so a block of slots covers this. */
private fun block(col0: Int, row0: Int, col1: Int, row1: Int) =
    intArrayOf(8 + 18 * col0, 18 + 18 * row0, 8 + 18 * col1 + 16, 18 + 18 * row1 + 16)

private val SHAPE = block(3, 0, 5, 2)
private val TRI = block(0, 0, 2, 2)

/** The ring stands 2px clear of the shape, so it reads as focus rather than as a glued outline. */
private const val FRAME_OUTSET = 2
private val FRAME_W = SHAPE[2] - SHAPE[0] + 2 * FRAME_OUTSET
private val FRAME_H = SHAPE[3] - SHAPE[1] + 2 * FRAME_OUTSET

/**
 * Python's `round` breaks ties to the even number; Kotlin's rounds them up.
 *
 * That difference is one pixel wide and only on exact halves, which is precisely the kind of thing
 * a port loses silently. `rint` is the JDK's round-half-to-even, so the triangle's slopes land where
 * they always did.
 */
private fun pyRound(value: Double): Int = Math.rint(value).toInt()

/**
 * A glow around the hovered item, offered as an alternative to vanilla's flat box.
 *
 * Blitted 24x24 at (slot.x - 4, slot.y - 4), so the item's own 16x16 sits at offset (4, 4) and the
 * remaining four pixels on each side are the room a glow needs to spill past it.
 *
 * Shipping it is opt-in for a reason: it overrides a vanilla sprite, so it changes the hover box in
 * every container the player opens, their own inventory included.
 */
fun paintSlotHighlight(out: Path) {
    val back = canvas(24, 24)
    for (y in 0 until 24) {
        for (x in 0 until 24) {
            val dx = maxOf(4 - x, x - 19, 0).toDouble()
            val dy = maxOf(4 - y, y - 19, 0).toDouble()
            val distance = hypot(dx, dy)
            val alpha =
                when {
                    distance == 0.0 -> 70
                    distance <= 4.0 -> (200 * (1 - distance / 4).pow(1.6)).toInt()
                    else -> 0
                }
            if (alpha > 0) back.set(x, y, (alpha shl 24) or 0x96E1FF)
        }
    }
    back.writeSprite(out.resolve("highlight/back.png"))

    // Nothing on the front layer: a glow belongs behind the item, not over it.
    canvas(24, 24).writeSprite(out.resolve("highlight/front.png"))

    // Both blank. Shipping these removes vanilla's box everywhere, which is the only way to stop it
    // flashing during the frames a themed screen has no tooltip to hang markers on.
    canvas(24, 24).writeSprite(out.resolve("highlight/blank_back.png"))
    canvas(24, 24).writeSprite(out.resolve("highlight/blank_front.png"))
}

/**
 * A transparent tooltip skin and a transparent item model.
 *
 * Both exist so something can be present without being seen. Markers ride in a tooltip, so the
 * tooltip has to be rendered — nothing says it has to be visible. Same for the items in a button's
 * slots: they must be items for the client to build a tooltip at all, and must not look like items.
 */
fun paintInvisible(out: Path) {
    canvas(24, 24).writeSprite(out.resolve("tooltips/blank_bg.png"))
    canvas(24, 24).writeSprite(out.resolve("tooltips/blank_frame.png"))
    canvas(16, 16).writeSprite(out.resolve("icons/blank.png"))
}

/**
 * Focus rings drawn in each shape's own form, at the size of the region they mark.
 *
 * The pack generator wraps these in the data pixels the shader reads back, so one glyph draws the
 * whole ring at its true dimensions. That is why a triangle gets a triangular ring rather than a
 * box: nothing here is constrained to a rectangle.
 */
fun paintFrames(out: Path) {
    val square = canvas(FRAME_W, FRAME_H)
    square.outline(0, 0, FRAME_W, FRAME_H, RING_DARK)
    square.outline(1, 1, FRAME_W - 2, FRAME_H - 2, RING_LIGHT)
    square.writeSprite(out.resolve("frame/square.png"))

    val tri = canvas(FRAME_W, FRAME_H)
    listOf(0 to RING_DARK, 1 to RING_LIGHT).forEach { (ring, colour) ->
        val apex = (FRAME_W - 1) / 2.0
        for (y in ring until FRAME_H - ring) {
            val span = (y - ring).toDouble() / maxOf(1, FRAME_H - 1 - 2 * ring)
            val half = ((FRAME_W - 1) / 2.0 - ring) * span
            tri.set(pyRound(apex - half), y, colour)
            tri.set(pyRound(apex + half), y, colour)
        }
        tri.rect(ring, FRAME_H - 1 - ring, FRAME_W - 2 * ring, 1, colour)
    }
    tri.writeSprite(out.resolve("frame/triangle.png"))
}

/**
 * A second control that is not a rectangle, so a bounding-box frame is visibly wrong.
 *
 * Apex at the top centre, base along the bottom. Lit on the left slope and along the top, shadowed
 * on the right and at the base — the same light direction as the button's bevel.
 */
private fun paintTriangle(panel: BufferedImage) {
    val apexX = (TRI[0] + TRI[2]) / 2.0
    val height = (TRI[3] - TRI[1]).toDouble()
    for (y in TRI[1] until TRI[3]) {
        val t = (y - TRI[1]) / height
        val half = (TRI[2] - TRI[0]) / 2.0 * t
        for (x in TRI[0] until TRI[2]) {
            val offset = abs(x - apexX)
            if (offset <= half) {
                when {
                    offset > half - 2 -> panel.set(x, y, if (x < apexX) GUI_LIGHT else GUI_SHADOW)
                    y > TRI[3] - 3 -> panel.set(x, y, GUI_SHADOW)
                    else -> panel.set(x, y, BUTTON_FACE)
                }
            }
        }
    }
}

/**
 * A plain vanilla-grey window with two controls in it: no chest tiles, no slot boxes.
 *
 * The panel is opaque across the whole window, so it covers the container texture the client draws
 * underneath. The player's own rows stay, because the client draws real slots there whatever the
 * artwork says.
 */
fun paintShop(out: Path) {
    val height = containerHeight(3)
    val panel = window(PANEL_WIDTH, height)
    panel.playerWells(height)

    // Each shape is drawn onto a transparent mask of its own, then blitted into the panel and
    // measured for the slots it covers. One drawing serves both, so the artwork and the hit area
    // cannot drift — the triangle's slots used to be read off by eye and written down beside it.
    val masks =
        mapOf(
            "square" to
                canvas(PANEL_WIDTH, height).also {
                    // One raised button covering a 3x3 block, drawn ignoring the slot grid the way
                    // a real multi-slot control would.
                    it.bevel(
                        SHAPE[0] - 2,
                        SHAPE[1] - 2,
                        SHAPE[2] - SHAPE[0] + 4,
                        SHAPE[3] - SHAPE[1] + 4,
                        BUTTON_FACE,
                        GUI_LIGHT,
                        GUI_SHADOW,
                    )
                },
            "triangle" to canvas(PANEL_WIDTH, height).also(::paintTriangle),
        )
    masks.values.forEach { panel.blit(it, 0, 0) }
    panel.writeSprite(out.resolve("panels/shop.png"))

    // The measurement travels with the artwork, the way the glyph advances do.
    out.resolve("frame/shapes.properties")
        .also { it.parent.createDirectories() }
        .writeText(
            "# shape=slots it covers, derived from the artwork's own alpha\n" +
                masks.entries.joinToString("") { (name, mask) ->
                    "$name=${hitSlots(mask, rows = 3).joinToString(",")}\n"
                }
        )
}

fun paintCoin(out: Path) {
    val coin = canvas(16, 16)
    for (y in 0 until 16) {
        for (x in 0 until 16) {
            val distance = hypot(x - 7.5, y - 7.5)
            if (distance <= 6.5) coin.set(x, y, if (distance <= 5.0) GOLD else GOLD_DARK)
        }
    }
    coin.writeSprite(out.resolve("icons/coin.png"))
}

/** Two tooltip skins, 24x24 with a 4px border so nine-slice keeps the corners crisp at any size. */
fun paintTooltips(out: Path) {
    canvas(24, 24, TOOLTIP_BG).writeSprite(out.resolve("tooltips/gold_bg.png"))

    val gold = canvas(24, 24)
    gold.rect(0, 0, 24, 4, GOLD_DARK)
    gold.rect(0, 20, 24, 4, GOLD_DARK)
    gold.rect(0, 0, 4, 24, GOLD_DARK)
    gold.rect(20, 0, 4, 24, GOLD_DARK)
    gold.outline(1, 1, 22, 22, GOLD)
    gold.writeSprite(out.resolve("tooltips/gold_frame.png"))

    canvas(24, 24, STEEL_BG).writeSprite(out.resolve("tooltips/steel_bg.png"))

    val steel = canvas(24, 24)
    steel.rect(0, 0, 24, 4, STEEL_DARK)
    steel.rect(0, 20, 24, 4, STEEL_DARK)
    steel.rect(0, 0, 4, 24, STEEL_DARK)
    steel.rect(20, 0, 4, 24, STEEL_DARK)
    steel.outline(0, 0, 24, 24, STEEL)
    // Inset 3 keeps this inside the 4px border region, so nine-slice never stretches it.
    steel.outline(3, 3, 18, 18, WHITE)
    steel.writeSprite(out.resolve("tooltips/steel_frame.png"))
}
