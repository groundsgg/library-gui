package gg.grounds.gui.demo.art

import gg.grounds.gui.art.crop
import gg.grounds.gui.art.nineSlice
import gg.grounds.gui.layout.slotWellX
import gg.grounds.gui.layout.slotWellY
import gg.grounds.gui.art.writeSprite
import java.awt.image.BufferedImage
import java.nio.file.Path

/**
 * A button occupying whole slots, so its artwork and its hit area cannot disagree.
 *
 * Slot (c, r) owns the 18x18 well at (7 + 18c, 17 + 18r); a button spanning columns c0..c1 of row r
 * is exactly that rectangle widened. Anything that does not align leaves a button you can see but
 * not press at its edges.
 */
data class MenuButton(
    val id: String,
    val label: String,
    val face: String,
    val c0: Int,
    val r0: Int,
    val c1: Int,
    val r1: Int,
) {
    val x: Int = slotWellX(c0)
    val y: Int = slotWellY(r0)
    val width: Int = 18 * (c1 - c0 + 1)
    val height: Int = 18 * (r1 - r0 + 1)
}

/** Vanilla's button border, and a middle wide enough for the widest button a container fits. */
const val MENU_CAP: Int = 3

const val MENU_MIDDLE: Int = 170

val MENU_BUTTONS: List<MenuButton> =
    listOf(
        MenuButton("shop", "Shop", "small", 1, 0, 3, 0),
        MenuButton("kits", "Kits", "small", 5, 0, 7, 0),
        MenuButton("play", "Play now", "wide", 1, 1, 7, 1),
        MenuButton("settings", "Settings", "small", 1, 2, 3, 2),
        MenuButton("profile", "Profile", "small", 5, 2, 7, 2),
    )

/**
 * A menu whose buttons span several slots each, painted with vanilla's own button sprite.
 *
 * The sprite is nine-sliced exactly as the client does it, so a button stretched across seven slots
 * keeps a crisp 3px bevel rather than a smeared one. Hover is not an outline here: the shader draws
 * vanilla's `button_highlighted` over the same rectangle, which is what the game itself does when
 * the cursor is on a button.
 */
fun paintMenu(dumps: Path, out: Path) {
    val face = loadDump(dumps, "button")
    val highlighted = loadDump(dumps, "button_highlighted")
    val sheet = loadDump(dumps, "ascii")

    val height = containerHeight(3)
    val panel = window(PANEL_WIDTH, height)
    panel.playerWells(height)
    MENU_BUTTONS.forEach { button ->
        panel.blitNineSlice(face, button.x, button.y, button.width, button.height, 3)
        // A vanilla button carries its label centred on its face, so ours does too — drawn from the
        // client's own sheet, so the letterforms are the game's rather than a lookalike.
        val labelWidth = textWidth(sheet, button.label)
        panel.drawText(
            sheet,
            button.label,
            button.x + (button.width - labelWidth) / 2,
            button.y + (button.height - 8) / 2,
        )
    }
    panel.writeSprite(out.resolve("panels/menu.png"))

    // Layer 1 of the hover stack: the highlighted button, fully opaque, so it covers vanilla's
    // single-slot highlight box wherever the cursor lands inside a multi-slot button.
    //
    // Three sprites rather than one per width. They are cut from a single nine-sliced render, so
    // the caps and the middle band are exactly what the client's own nine-slice would have
    // produced — the runtime then clips the middle to whatever span a button needs.
    val lit = highlighted.nineSlice(2 * MENU_CAP + MENU_MIDDLE, 18, 3)

    // A cap is three pixels wide and a frame needs four for its own data row, so each is padded
    // with a transparent column on the right. The visible edge stays three wide and the middle
    // still starts one cap in — the padding overlaps it and draws nothing.
    fun cap(from: Int) =
        canvas(MENU_CAP + 1, 18).also { it.blit(lit.crop(from, 0, MENU_CAP, 18), 0, 0) }

    cap(0).writeSprite(out.resolve("frame/menu_face_left.png"))
    lit.crop(MENU_CAP, 0, MENU_MIDDLE, 18).writeSprite(out.resolve("frame/menu_face_middle.png"))
    cap(MENU_CAP + MENU_MIDDLE).writeSprite(out.resolve("frame/menu_face_right.png"))

    // Layer 2: the label again, because layer 1 just painted over it. Same glyphs, same place.
    MENU_BUTTONS.forEach { button ->
        val labelWidth = textWidth(sheet, button.label)
        val label: BufferedImage = canvas(labelWidth + 1, 10)
        label.drawText(sheet, button.label, 0, 0)
        label.writeSprite(out.resolve("frame/menu_label_${button.id}.png"))
    }
}
