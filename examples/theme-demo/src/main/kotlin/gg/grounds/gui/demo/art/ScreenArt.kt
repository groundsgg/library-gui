package gg.grounds.gui.demo.art

import gg.grounds.gui.art.Ring
import gg.grounds.gui.art.contour
import gg.grounds.gui.art.cutGlyphs
import gg.grounds.gui.art.lightened
import gg.grounds.gui.art.scaled
import gg.grounds.gui.layout.slotItemX
import gg.grounds.gui.layout.slotItemY
import gg.grounds.gui.layout.slotWellX
import gg.grounds.gui.layout.slotWellY
import gg.grounds.gui.art.slotPatches
import gg.grounds.gui.art.writeSprite
import gg.grounds.gui.demo.MarketLayout
import java.awt.image.BufferedImage
import java.nio.file.Path
import kotlin.io.path.writeText

/** The overview and market are six-row screens. */
private val TALL = containerHeight(6)

private const val TITLE_GREY = 0xFF404040.toInt()

/** The preview well is sunk deeper than a slot, so it reads as a display inside a display. */
private const val WELL_INK = 0xFF141414.toInt()

private const val WELL_RIM = 0xFF4E4E4E.toInt()

/** The icons the overview's toolbar and the market's controls are cut from. */
val TOOLBAR_ICONS: List<String> =
    listOf("arrow_left", "search", "plus", "minus", "refresh", "lock_closed", "settings")

val MARKET_CONTROL_ICONS: List<String> = listOf("search", "close", "question")

val MARKET_ITEMS: List<String> =
    listOf(
        "diamond_sword", "diamond_pickaxe", "diamond_axe", "iron_sword", "iron_pickaxe", "bow",
        "arrow", "golden_apple", "apple", "bread", "cooked_beef", "ender_pearl", "elytra", "saddle",
        "name_tag", "emerald", "diamond", "book", "paper", "feather", "stick",
    )

private fun BufferedImage.centredText(sheet: BufferedImage, text: String, y: Int) {
    drawText(sheet, text, (PANEL_WIDTH - textWidth(sheet, text)) / 2, y, TITLE_GREY, shadow = false)
}

/** The storybook index, given the same window chrome as everything else. */
fun paintStorybook(dumps: Path, out: Path) {
    val face = loadDump(dumps, "button")
    val panel = window(PANEL_WIDTH, TALL)
    for (row in 0 until 5) {
        for (column in 0 until 9) {
            panel.well(slotWellX(column), slotWellY(row))
        }
    }
    listOf(0, 8).forEach { column ->
        panel.blitNineSlice(face, slotWellX(column), slotWellY(5), 18, 18, 3)
    }
    panel.playerWells(TALL)
    panel.writeSprite(out.resolve("panels/story.png"))
}

/** A themed screen and the window it has to fill. */
data class DemoScreen(val name: String, val width: Int, val height: Int, val accent: Int)

val DEMO_SCREENS: List<DemoScreen> =
    listOf(
        DemoScreen("screen_shop", 176, 222, 0xFF56784E.toInt()),
        DemoScreen("screen_toolbar", 176, 133, 0xFF78603C.toInt()),
        DemoScreen("screen_forge", 176, 166, 0xFF6E5454.toInt()),
        DemoScreen("screen_centred", 176, 166, 0xFF4E607C.toInt()),
    )

/**
 * One panel per demo screen, sized to that screen's own window.
 *
 * Four screens with four different title anchors, which is the point: a six-row chest and a hopper
 * keep the container default of x=8, an anvil starts at 60, and a dispenser centres its title.
 */
fun paintScreens(out: Path) {
    DEMO_SCREENS.forEach { screen ->
        val panel = window(screen.width, screen.height)
        // A thin accent rule under the title, the one place this theme departs from vanilla.
        panel.rect(7, 15, screen.width - 14, 1, screen.accent)
        when (screen.name) {
            "screen_shop" ->
                for (row in 0 until 6) {
                    for (column in 0 until 9) {
                        panel.well(slotWellX(column), slotWellY(row))
                    }
                }
            "screen_toolbar" -> for (i in 0 until 5) panel.well(43 + 18 * i, 19)
        }
        panel.playerWells(screen.height)
        panel.writeSprite(out.resolve("panels/${screen.name}.png"))
    }
}

/**
 * A patch that hides vanilla's hover box on a slot whose panel area is plain face.
 *
 * The client blits its highlight into a 24x24 box, but only the inner 16x16 of that sprite is ever
 * opaque — measured, both layers. So the patch is exactly the highlight's visible extent, and
 * incapable of reaching a neighbour.
 */
fun paintSlotCovers(out: Path) {
    canvas(16, 16, GUI_FACE).writeSprite(out.resolve("frame/slot_cover.png"))
    canvas(16, 16, GUI_FACE).lightened(HOVER_TINT).writeSprite(out.resolve("frame/slot_hover.png"))
}

/**
 * One frame per printable character, plus the advance table that positions them.
 *
 * The advances have to travel with the sprites, because only this side measures them — the vanilla
 * sheet is proportional, and a second copy of those numbers is a second thing to get wrong.
 */
fun paintGlyphs(dumps: Path, out: Path) {
    val cut = cutGlyphs(loadDump(dumps, "ascii"), ASCII_ORDER)
    cut.sprites.forEach { (codepoint, sprite) ->
        sprite.writeSprite(out.resolve("frame/glyph_$codepoint.png"))
    }
    out.resolve("frame/glyphs.properties")
        .writeText(
            "# codepoint=advance, measured off the client's own ascii sheet\n" +
                cut.advances.toSortedMap().entries.joinToString("") { (code, advance) ->
                    "$code=$advance\n"
                }
        )
}

/** A sectioned screen: two labelled groups with a toolbar of bare icons between them. */
fun paintOverview(dumps: Path, out: Path) {
    val sheet = loadDump(dumps, "ascii")
    val panel = window(PANEL_WIDTH, TALL)

    panel.centredText(sheet, "Spielübersicht", 22)
    for (row in 1..2) {
        for (column in 1..7) {
            panel.well(slotWellX(column), slotWellY(row))
        }
    }
    panel.groupFrame(1, 1, 7, 2)

    // Bare icons on the panel face. No button underneath — the icon is the button, and its own
    // silhouette is what the hover outlines.
    TOOLBAR_ICONS.forEachIndexed { index, name ->
        panel.blit(loadDump(dumps, "gicon_$name"), slotItemX(1 + index), slotItemY(3))
    }

    panel.centredText(sheet, "Teleport", 94)
    for (column in 1..7) {
        panel.well(slotWellX(column), slotWellY(5))
    }
    panel.groupFrame(1, 5, 7, 5)

    panel.playerWells(TALL)
    panel.writeSprite(out.resolve("panels/overview.png"))

    // Vanilla's own hover value, measured off its sprite: white at alpha 96 over the item area.
    canvas(16, 16, 0x60FFFFFF).writeSprite(out.resolve("frame/ov_slot.png"))
    canvas(16, 16, GUI_FACE).writeSprite(out.resolve("frame/ov_cover.png"))

    // Two families of the same cut-out, so /tint costs no rebuild: the choice is which glyph the
    // server names, not what the pack contains.
    slotPatches(panel, rows = 6).forEach { (slot, patch) ->
        patch.writeSprite(out.resolve("frame/ov_cover_$slot.png"))
    }
    slotPatches(panel, rows = 6, lift = HOVER_TINT).forEach { (slot, patch) ->
        patch.writeSprite(out.resolve("frame/ov_hover_$slot.png"))
    }

    TOOLBAR_ICONS.forEach { name ->
        val icon = loadDump(dumps, "gicon_$name")
        icon.contour().writeSprite(out.resolve("frame/ov_outline_$name.png"))
        canvas(16, 16).also { it.blit(icon, 0, 0) }.writeSprite(out.resolve("frame/ov_icon_$name.png"))
    }
}

/**
 * A shop whose detail card is part of the window rather than a box chasing the cursor.
 *
 * The card is dark on purpose. It is the one region of this window that is a display rather than a
 * surface you click, and white text on vanilla's mid grey has barely any contrast.
 */
fun paintMarket(dumps: Path, out: Path) {
    val sheet = loadDump(dumps, "ascii")
    val panel = window(PANEL_WIDTH, TALL)

    // A title, where a container's own would sit. The panel occupies the title glyph, so the words
    // have to be painted rather than sent.
    panel.drawText(sheet, "Market", 8, 6, TITLE_GREY, shadow = false)

    // Eight columns, not seven: slot columns sit at a fixed 7 + 18c, so columns 0..8 span exactly
    // the margins the card uses.
    for (row in 0 until 3) {
        for (column in 0 until 8) {
            panel.well(slotWellX(column), slotWellY(row))
        }
    }
    panel.groupFrame(0, 0, 7, 2)

    MARKET_CONTROL_ICONS.forEachIndexed { row, name ->
        panel.blit(loadDump(dumps, "gicon_$name"), slotItemX(8), slotItemY(row))
    }
    panel.groupFrame(8, 0, 8, 2)
    require(MarketLayout.GRID.right == MarketLayout.CARD.right - 18) { "grid and card must share a right edge" }

    panel.sunken(MarketLayout.CARD, CARD_FILL, CARD_DARK, CARD_LIGHT)
    panel.sunken(MarketLayout.PREVIEW_WELL, CARD_DARK, WELL_INK, WELL_RIM)
    panel.drawText(
        sheet,
        "Point at an item",
        MarketLayout.HINT.x,
        MarketLayout.HINT.y,
        TEXT_DIM,
        shadow = false,
    )

    panel.playerWells(TALL)
    panel.writeSprite(out.resolve("panels/market.png"))

    // Every sprite is sized from the layout, so a card that moves cannot leave a patch behind.
    canvas(MarketLayout.CARD_INNER.width, MarketLayout.CARD_INNER.height, CARD_FILL)
        .writeSprite(out.resolve("frame/market_card.png"))

    val well = MarketLayout.PREVIEW_WELL
    val wellSprite = canvas(well.width, well.height)
    wellSprite.sunken(0, 0, well.width, well.height, CARD_DARK, WELL_INK, WELL_RIM)
    wellSprite.writeSprite(out.resolve("frame/market_well.png"))

    // Not tinted at runtime — unlike the contours and the dialog rule — so this carries its colour.
    canvas(MarketLayout.RULE.width, MarketLayout.RULE.height, CARD_RULE)
        .writeSprite(out.resolve("frame/market_rule.png"))

    // One preview per offer, doubled so it reads as a display rather than as another inventory icon.
    MARKET_ITEMS.forEach { name ->
        loadDump(dumps, "mcitem_$name").scaled(2).writeSprite(out.resolve("frame/market_item_$name.png"))
    }

    canvas(16, 16).also { it.blit(loadDump(dumps, "gicon_coins"), 0, 0) }
        .writeSprite(out.resolve("frame/market_coin.png"))

    // The groove, and the bar that fills it. The bar's colour runs along its length rather than
    // being one tint, which is the point: a meter spends the payload byte a tint would have used,
    // so its colour has to live in the sprite — and there it can be a gradient for free.
    val bar = MarketLayout.BAR
    canvas(bar.width, bar.height).also {
        it.sunken(0, 0, bar.width, bar.height, 0xFF2A2A2A.toInt(), WELL_INK, WELL_RIM)
    }
        .writeSprite(out.resolve("frame/market_track.png"))

    val fill = canvas(bar.width, bar.height)
    for (column in 0 until bar.width) {
        val along = column.toDouble() / (bar.width - 1)
        val red = (0xD8 + (0x5A - 0xD8) * along).toInt()
        val green = (0x40 + (0xC8 - 0x40) * along).toInt()
        fill.rect(column, 1, 1, bar.height - 2, 0xFF000000.toInt() or (red shl 16) or (green shl 8) or 0x40)
    }
    fill.writeSprite(out.resolve("frame/market_bar.png"))

    slotPatches(panel, rows = 6).forEach { (slot, patch) ->
        patch.writeSprite(out.resolve("frame/mk_cover_$slot.png"))
    }

    MARKET_CONTROL_ICONS.forEach { name ->
        loadDump(dumps, "gicon_$name")
            .contour()
            .writeSprite(out.resolve("frame/market_outline_$name.png"))
    }
}

/**
 * Artwork for a dialog, which the pack format otherwise cannot reach at all.
 *
 * Sized to the reach rather than to taste: a marker's position is a signed byte from the screen's
 * centre, so this is about the largest plate that can be placed as one piece.
 */
fun paintDialogArt(dumps: Path, out: Path) {
    val width = 224
    val height = 96
    val plate = canvas(width, height)
    plate.sunken(0, 0, width, height, CARD_FILL, CARD_DARK, CARD_LIGHT)
    plate.rect(3, 3, width - 6, 1, CARD_RULE)
    plate.rect(3, height - 4, width - 6, 1, CARD_RULE)
    plate.writeSprite(out.resolve("frame/dialog_plate.png"))

    canvas(160, 1, WHITE).writeSprite(out.resolve("frame/dialog_rule.png"))

    canvas(32, 32)
        .also { it.blit(loadDump(dumps, "gicon_coins").scaled(2), 0, 0) }
        .writeSprite(out.resolve("frame/dialog_badge.png"))
}

/** Everything, in the order the Python generator ran it. */
fun paintAll(dumps: Path, out: Path) {
    paintShop(out)
    paintCoin(out)
    paintTooltips(out)
    paintSlotHighlight(out)
    paintFrames(out)
    paintInvisible(out)
    paintScreens(out)
    paintMenu(dumps, out)
    paintStorybook(dumps, out)
    paintOverview(dumps, out)
    paintSlotCovers(out)
    paintGlyphs(dumps, out)
    paintMarket(dumps, out)
    paintDialogArt(dumps, out)
}

/** Unused, but named so the contour's default rings are stated once rather than assumed. */
val DEFAULT_CONTOUR: List<Ring> = listOf(Ring(2, 110), Ring(1, 255))
