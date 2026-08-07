package gg.grounds.gui.demo

import gg.grounds.gui.pack.writePack
import gg.grounds.gui.pack.zipPack
import gg.grounds.gui.theme.PackFormat
import gg.grounds.gui.theme.TITLE_INSET
import gg.grounds.gui.theme.Theme
import gg.grounds.gui.theme.theme
import java.nio.file.Path
import kotlin.io.path.deleteRecursively

/**
 * The demo's theme, with the three title offsets left mutable so they can be dialled in against a
 * running client — which is the only way to settle them, since they are conventions taken from how
 * vanilla lays a container title out and nothing here has ever been measured.
 *
 * Two of the three are free: [offsetX] and [advance] only change the string the server puts in the
 * window title. [offsetY] is not — it becomes the font glyph's ascent, which lives in the pack, so
 * changing it means rebuilding and re-sending the pack. [rebuild] returns whether that is needed.
 */
object DemoTheme {
    /** The library's own default: a container title sits 8px inside the window's left edge. */
    const val DEFAULT_OFFSET_X: Int = TITLE_INSET

    /** Lifts the artwork's top edge to the window's, six rows above the title text. */
    const val DEFAULT_OFFSET_Y: Int = -6

    /** Horizontal shift from the title's origin. Runtime only. */
    var offsetX: Int = DEFAULT_OFFSET_X

    /** Pixels below the title's top row; negative moves up. Baked into the pack. */
    var offsetY: Int = DEFAULT_OFFSET_Y

    /**
     * Whether the pack replaces the client's slot hover highlight with a glow.
     *
     * Off by default, and that default is the important part: the override is a vanilla sprite, so
     * shipping it changes the hover box in *every* container the player opens, their own inventory
     * included. Leaving it alone is what keeps the rest of the game behaving normally. `/glow`
     * turns it on so the difference can be seen, not because one GUI can opt in.
     */
    var slotGlow: Boolean = false

    // A panel's advance is deliberately not tunable. The generator measures what the client will
    // actually use — it trims fully transparent columns off the right before measuring — and fails
    // the build with the correct number, so an override here could only ever be the wrong one.

    /** Back to the library's defaults. */
    fun reset() {
        offsetX = DEFAULT_OFFSET_X
        offsetY = DEFAULT_OFFSET_Y
    }

    /** Set once the artwork's real size is known, so a resized PNG still fails loudly. */
    private const val PANEL_W = 176
    private const val PANEL_H = 168

    const val NAMESPACE: String = "groundsdemo"
    const val PANEL: String = "shop"
    const val ICON: String = "coin"
    const val TOOLTIP: String = "gold"

    /** A second outline, so two hover styles can be told apart side by side. */
    const val TOOLTIP_TOOL: String = "steel"

    /** The theme as currently tuned. */
    fun current(): Theme =
        theme(NAMESPACE, PackFormat(88, minInclusive = 84, maxInclusive = 88)) {
            description = "library-gui theme demo"
            panel(PANEL, "panels/shop.png", PANEL_W, PANEL_H, offsetX = offsetX, offsetY = offsetY)
            icon(ICON, "icons/coin.png")
            icon(BLANK, "icons/blank.png")
            tooltip(BLANK, "tooltips/blank_bg.png", "tooltips/blank_frame.png")
            tooltip(TOOLTIP, "tooltips/gold_bg.png", "tooltips/gold_frame.png")
            tooltip(TOOLTIP_TOOL, "tooltips/steel_bg.png", "tooltips/steel_frame.png")
            // Global by nature: this replaces the vanilla sprite, so the glow appears in every
            // container, the player's own inventory included. There is no way to scope it to one
            // GUI, which is exactly why it is a switch here rather than a given.
            if (slotGlow) slotHighlight("highlight/back.png", "highlight/front.png")
            // The one hover effect that can be scoped to a region of one GUI — at the price of
            // overriding a vanilla shader.
            frame("square", "frame/square.png")
            frame("triangle", "frame/triangle.png")
        }

    /** Nothing to see: a transparent tooltip skin and a transparent item model. */
    const val BLANK: String = "blank"

    /**
     * A control painted into the panel, and the slots that raise its frame.
     *
     * [slots] are the ones the shape actually covers, which for anything that is not a rectangle is
     * fewer than its block — hovering a corner the triangle does not reach does nothing, and that
     * is the point. [cornerFrom] and [cornerTo] are opposite corners of the block the frame spans.
     */
    data class Shape(
        val name: String,
        val slots: List<Int>,
        val cornerFrom: Int,
        val cornerTo: Int,
    )

    /** The square button covers columns 3..5 of every row; the triangle stands to its left. */
    val SHAPES: List<Shape> =
        listOf(
            Shape("square", listOf(3, 4, 5, 12, 13, 14, 21, 22, 23), 3, 23),
            // Only the slots whose centres fall inside the triangle: one at the apex, one in the
            // middle, three along the base.
            Shape("triangle", listOf(1, 10, 18, 19, 20), 0, 20),
        )

    /**
     * Regenerates the pack from [art] into [out] and returns its zip and SHA-1.
     *
     * [out] is wiped first: the generator refuses a populated directory so nothing from an earlier
     * theme can survive into a pack, and a demo that rebuilds on every tweak would otherwise stop
     * after the first one.
     */
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    fun rebuild(art: Path, out: Path): Pair<Path, String> {
        out.deleteRecursively()
        val pack = out.resolve("pack")
        val zip = out.resolve("theme-demo.zip")
        writePack(current(), art, pack)
        return zip to zipPack(pack, zip)
    }

    /** The tuned values, shaped so they can be pasted straight into a theme declaration. */
    fun snippet(): String =
        "panel(\"$PANEL\", \"panels/shop.png\", $PANEL_W, $PANEL_H, " +
            "offsetX = $offsetX, offsetY = $offsetY)"
}
