package gg.grounds.gui.demo

import gg.grounds.gui.pack.writePack
import gg.grounds.gui.pack.zipPack
import gg.grounds.gui.demo.art.MENU_CAP
import gg.grounds.gui.demo.art.MENU_MIDDLE
import gg.grounds.gui.theme.MeterAxis
import gg.grounds.gui.theme.PackFormat
import gg.grounds.gui.theme.TITLE_INSET
import gg.grounds.gui.theme.Sequence
import gg.grounds.gui.theme.Theme
import gg.grounds.gui.theme.theme
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.inputStream

/** Where the demo's artwork lives, relative to the module the server is started from. */
internal val ART: Path = Path.of("art")

/** The one character set. Colour is chosen per marker, so one family covers every weight. */
internal const val GLYPHS: String = "ascii"

/** The menu's button face, drawable at any width from three sprites. */
internal const val MENU_FACE: String = "menu_face"

/** Supporting text on the card: present, clearly not the headline. */
internal const val DIM: String = "dim"

/**
 * The price bands.
 *
 * A number alone makes the grid something to read item by item; a colour makes the cheap and the
 * ruinous separable at a glance. Four names rather than four sprite families, which is the whole
 * point of the payload byte.
 */
internal const val GOLD: String = "gold"

internal const val STANDARD: String = "standard"

internal const val PREMIUM: String = "premium"

/**
 * The outline colour, so the theme's accent lives here instead of inside ten PNGs.
 *
 * Three steps of it, because animating a marker means naming a different colour rather than
 * blending one: the palette is a constant array in the shader, so what a pulse walks through is
 * declared entries. Costs no sprites at all.
 */
internal const val ACCENT: String = "accent"

internal const val ACCENT_MID: String = "accent_mid"

internal const val ACCENT_LOW: String = "accent_low"

/** The pulse a hovered control's contour breathes with, six ticks out and six back. */
internal val ACCENT_PULSE: Sequence<String> =
    Sequence(listOf(ACCENT_LOW, ACCENT_MID, ACCENT), ticksPerStep = 3)

/**
 * Codepoint to pen advance, read back from what the generator measured.
 *
 * The numbers belong to the sprites: vanilla's sheet is proportional, so `1` is not as wide as `W`,
 * and only the side that cut the glyphs knows by how much. Reading the table it wrote is what keeps
 * a second copy of those widths — the kind that drifts one pixel at a time — from existing here.
 */
internal val GLYPH_ADVANCES: Map<Int, Int> by lazy {
    val table = Properties()
    ART.resolve("frame/glyphs.properties").inputStream().use(table::load)
    table.entries.associate { (key, value) -> key.toString().toInt() to value.toString().toInt() }
}

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

    /**
     * Whether the pack blanks vanilla's slot hover box.
     *
     * On by default, and it is the only thing that stops the click flicker. A left click makes the
     * client predict a pickup; the slot goes empty, its tooltip vanishes, and every marker riding
     * in that tooltip vanishes with it — verified as unavoidable, since only `Slot.mayPickup` gates
     * the pickup and no item property reaches it. What was visible in those frames was vanilla's
     * own hover box appearing underneath. Blank it and there is nothing left to appear.
     *
     * Off by default, because the cost lands outside the screen it helps: it is a vanilla sprite,
     * so blanking it removes the box in every container, the player's own inventory included.
     * `/highlight` flips it, and the demo hands the highlight back per slot where it is blanked —
     * which is the only way a themed screen gets to choose.
     */
    var blankHighlight: Boolean = false

    /**
     * Whether a hovered empty tile is tinted or left looking untouched.
     *
     * Off by default: an empty tile is not a control, so pointing at it should do nothing, and the
     * patch that hides vanilla's box is cut at no tint at all. On, the same patch is lifted toward
     * white and the tile gets a hover of its own.
     *
     * Both sets ship in the pack, so `/tint` only changes which glyph the server names — no
     * rebuild, no re-download, and the two can be compared without leaving the screen.
     */
    var tintEmpty: Boolean = false

    /** The overview's patch for the empty tile in [slot], in whichever family is selected. */
    fun overviewTile(slot: Int): String = if (tintEmpty) "ov_hover_$slot" else "ov_cover_$slot"

    /** The menu's patch for an empty tile — one sprite serves all of them, they are all flat face. */
    fun menuTile(): String = if (tintEmpty) "slot_hover" else "slot_cover"

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
            listOf("screen_shop" to (176 to 222), "screen_toolbar" to (176 to 133),
                    "screen_forge" to (176 to 166), "screen_centred" to (176 to 166))
                .forEach { (id, size) -> panel(id, "panels/$id.png", size.first, size.second) }
            icon(ICON, "icons/coin.png")
            emptyIcon(BLANK)
            bundleFiller()
            tooltip(BLANK, "tooltips/blank_bg.png", "tooltips/blank_frame.png")
            tooltip(TOOLTIP, "tooltips/gold_bg.png", "tooltips/gold_frame.png")
            tooltip(TOOLTIP_TOOL, "tooltips/steel_bg.png", "tooltips/steel_frame.png")
            // Global by nature: this replaces the vanilla sprite, so the glow appears in every
            // container, the player's own inventory included. There is no way to scope it to one
            // GUI, which is exactly why it is a switch here rather than a given.
            when {
                slotGlow -> slotHighlight("highlight/back.png", "highlight/front.png")
                blankHighlight -> slotHighlight("highlight/blank_back.png", "highlight/blank_front.png")
            }
            // The one hover effect that can be scoped to a region of one GUI — at the price of
            // overriding a vanilla shader.
            panel("menu", "panels/menu.png", 176, 168)
            panel("story", "panels/story.png", 176, 222)
            panel("overview", "panels/overview.png", 176, 222)
            panel("market", "panels/market.png", 176, 222)
            // One frame per drawable character, so text can be composed at runtime. A codepoint
            // with an advance but no PNG is a blank — the space — and gets no frame on purpose.
            colour(DIM, 0x969AA4)
            colour(STANDARD, 0xE6E6E6)
            colour(GOLD, 0xFFC24A)
            colour(PREMIUM, 0xFF7A45)
            colour(ACCENT, 0x2F7FD6)
            colour(ACCENT_MID, 0x2361A2)
            colour(ACCENT_LOW, 0x17436E)
            glyphs(GLYPHS, "glyph_", GLYPH_ADVANCES)
            GLYPH_ADVANCES.keys
                .filter { code -> ART.resolve("frame/glyph_$code.png").exists() }
                .forEach { code -> frame("glyph_$code", "frame/glyph_$code.png") }
            listOf("card", "well", "rule", "coin", "track").forEach { part ->
                frame("market_$part", "frame/market_$part.png")
            }
            frame("market_bar", "frame/market_bar.png", meter = MeterAxis.HORIZONTAL)
            listOf("plate", "rule", "badge").forEach { part ->
                frame("dialog_$part", "frame/dialog_$part.png")
            }
            MARKET_CONTROLS.forEach { (icon, _) ->
                frame("market_outline_$icon", "frame/market_outline_$icon.png")
            }
            ART.resolve("frame").toFile().list { _, n -> n.startsWith("market_item_") }
                ?.sorted()
                ?.forEach { file -> frame(file.removeSuffix(".png"), "frame/$file") }
            (0 until 54).forEach { slot -> frame("mk_cover_$slot", "frame/mk_cover_$slot.png") }
            frame("slot_cover", "frame/slot_cover.png")
            frame("slot_hover", "frame/slot_hover.png")
            frame("ov_slot", "frame/ov_slot.png")
            frame("ov_cover", "frame/ov_cover.png")
            // Two per slot, each cut out of the overview panel itself. See openOverview.
            (0 until 54).forEach { slot ->
                frame("ov_cover_$slot", "frame/ov_cover_$slot.png")
                frame("ov_hover_$slot", "frame/ov_hover_$slot.png")
            }
            TOOLBAR_ICONS.forEach { (name, _) ->
                frame("ov_icon_$name", "frame/ov_icon_$name.png")
                frame("ov_outline_$name", "frame/ov_outline_$name.png")
            }
            listOf("left", "right").forEach { end -> frame("menu_face_$end", "frame/menu_face_$end.png") }
            frame("menu_face_middle", "frame/menu_face_middle.png", meter = MeterAxis.HORIZONTAL)
            slice(
                MENU_FACE,
                left = "menu_face_left",
                middle = "menu_face_middle",
                right = "menu_face_right",
                capWidth = MENU_CAP,
                middleWidth = MENU_MIDDLE,
            )
            listOf("shop", "kits", "play", "settings", "profile").forEach { id ->
                frame("menu_label_$id", "frame/menu_label_$id.png")
            }
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
