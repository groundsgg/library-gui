package gg.grounds.gui.demo

import gg.grounds.gui.gui
import gg.grounds.gui.item
import gg.grounds.gui.theme.Theme
import gg.grounds.gui.theme.chestAnchor
import gg.grounds.gui.theme.containerHeight
import gg.grounds.gui.theme.frameMarker
import gg.grounds.gui.theme.text
import gg.grounds.gui.theme.textWidth
import gg.grounds.gui.theme.title
import java.util.UUID
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player
import net.minestom.server.item.Material

private const val ROWS = 6
private val CLICK = Sound.sound(Key.key("ui.button.click"), Sound.Source.MASTER, 0.4f, 1f)

/**
 * The card's geometry, matching what the generator drew.
 *
 * Every one of these is a pixel in the panel too, which is the awkward part of painting a layout
 * into artwork: the numbers exist twice, in Python and here, and they have to agree. What keeps
 * them honest is that a disagreement is instantly visible — text lands off its own rule.
 */
private const val CARD_INNER_X = 10
private const val CARD_INNER_Y = 79
private const val PREVIEW_WELL_X = 11
private const val PREVIEW_WELL_Y = 83
private const val PREVIEW_X = PREVIEW_WELL_X + 2
private const val PREVIEW_Y = PREVIEW_WELL_Y + 2
private const val TEXT_X = 54
private const val TEXT_RIGHT = 163

private const val NAME_Y = 83
private const val RULE_Y = 94
private const val NOTE_Y = 98
private const val COIN_Y = 105
private const val PRICE_X = TEXT_X + 17
private const val PRICE_Y = 109

/** Eight columns wide, so the grid ends on the same margins as the card under it. */
private val GRID_SLOTS: List<Int> = (0..2).flatMap { row -> (0..7).map { col -> row * 9 + col } }
private const val SEARCH_SLOT = 8
private const val CLEAR_SLOT = 17
private const val HELP_SLOT = 26

/**
 * One row of the shop.
 *
 * [texture] is the name of the item's own client texture, which is also the id of the preview frame
 * cut from it. Only items whose icon is a flat texture are in here: a block is rendered from a 3D
 * model in the GUI, and there is no single image to enlarge.
 */
private class Offer(
    val material: Material,
    val texture: String,
    val name: String,
    val price: Int,
    val note: String,
)

/** Twenty-one offers in a twenty-four slot grid, so the trailing tiles show the empty state too. */
private val CATALOGUE =
    listOf(
        Offer(Material.DIAMOND_SWORD, "diamond_sword", "Diamond Sword", 240, "Sharpness III"),
        Offer(Material.DIAMOND_PICKAXE, "diamond_pickaxe", "Diamond Pickaxe", 220, "Efficiency IV"),
        Offer(Material.DIAMOND_AXE, "diamond_axe", "Diamond Axe", 210, "Unbreaking II"),
        Offer(Material.IRON_SWORD, "iron_sword", "Iron Sword", 60, "No enchantments"),
        Offer(Material.IRON_PICKAXE, "iron_pickaxe", "Iron Pickaxe", 55, "No enchantments"),
        Offer(Material.BOW, "bow", "Bow", 90, "Power II"),
        Offer(Material.ARROW, "arrow", "Arrow", 2, "Sold by the stack"),
        Offer(Material.GOLDEN_APPLE, "golden_apple", "Golden Apple", 120, "Absorption for 2m"),
        Offer(Material.APPLE, "apple", "Apple", 4, "Restores 4 hunger"),
        Offer(Material.BREAD, "bread", "Bread", 3, "Restores 5 hunger"),
        Offer(Material.COOKED_BEEF, "cooked_beef", "Cooked Beef", 8, "Restores 8 hunger"),
        Offer(Material.ENDER_PEARL, "ender_pearl", "Ender Pearl", 150, "Throw to travel"),
        Offer(Material.ELYTRA, "elytra", "Elytra", 900, "Fireworks sold apart"),
        Offer(Material.SADDLE, "saddle", "Saddle", 80, "Fits most mounts"),
        Offer(Material.NAME_TAG, "name_tag", "Name Tag", 130, "Renames one mob"),
        Offer(Material.EMERALD, "emerald", "Emerald", 30, "Villager currency"),
        Offer(Material.DIAMOND, "diamond", "Diamond", 90, "Crafting material"),
        Offer(Material.BOOK, "book", "Book", 12, "For the enchanter"),
        Offer(Material.PAPER, "paper", "Paper", 2, "For maps and books"),
        Offer(Material.FEATHER, "feather", "Feather", 3, "For arrows"),
        Offer(Material.STICK, "stick", "Stick", 1, "For everything else"),
    )

/** What each player is currently filtering by. Empty means the whole catalogue. */
private val queries = mutableMapOf<UUID, String>()

/** The key the search dialog reports back under, routed by the listener in Main. */
const val MARKET_SEARCH: String = "market_search"

/**
 * A shop whose detail card is part of the window, and whose search comes from a dialog.
 *
 * Three things the library could not do before meet on this screen.
 *
 * **Text at runtime.** Every line in the card is composed from glyph frames, one marker per
 * character. A label baked into the panel is decided when the pack is built, which is fine for a
 * button that always says "Shop" and useless for a price.
 *
 * **A detail card instead of a tooltip.** Vanilla's tooltip follows the cursor and is sized by its
 * own text; a marker goes wherever the layout says. So the description lands in the same place
 * every time, under the grid, and the item's own tooltip is suppressed entirely.
 *
 * **Keyboard input.** A container screen cannot take any — there is no packet for it. A dialog can,
 * and gives back a typed payload. Neither half is new; pointing one at the other is.
 *
 * The card is anchored inside the window on purpose. A marker carries its position as two signed
 * bytes from the window's centre, so anything it draws has to lie within ±128px of that centre — a
 * pane floating beside the window is not reachable, and would fail the range check rather than
 * quietly landing somewhere else.
 */
fun openMarket(player: Player, query: String = queries[player.uuid].orEmpty()) {
    val theme = DemoTheme.current()
    val height = containerHeight(ROWS)
    queries[player.uuid] = query

    val matches =
        if (query.isBlank()) CATALOGUE
        else CATALOGUE.filter { it.name.contains(query.trim(), ignoreCase = true) }

    fun marker(id: String, x: Int, y: Int) = theme.frameMarker(id, x, y, imageHeight = height)

    fun line(set: String, x: Int, y: Int, body: String) =
        theme.text(set, x, y, theme.fit(set, body, TEXT_RIGHT - x), imageHeight = height)

    /**
     * The card, rewritten from scratch on every hover.
     *
     * The blanking patch comes first and the preview well goes back on top of it, rather than the
     * patch being shaped to spare the well: markers draw in the order they are appended, and one
     * rectangle plus one redraw is far easier to keep correct than a patch with a hole in it.
     */
    fun card(vararg parts: Component): Component =
        parts.fold(
            marker("market_card", CARD_INNER_X, CARD_INNER_Y)
                .append(marker("market_well", PREVIEW_WELL_X, PREVIEW_WELL_Y))
        ) { acc, part -> acc.append(part) }

    /** A heading and a supporting line, the shape every card on this screen uses. */
    fun heading(title: String, note: String) =
        Component.empty()
            .append(line(GLYPHS, TEXT_X, NAME_Y, title))
            .append(marker("market_rule", TEXT_X, RULE_Y))
            .append(line(GLYPHS_DIM, TEXT_X, NOTE_Y, note))

    gui(player, theme.title("market", Component.empty(), chestAnchor(ROWS)), rows = ROWS) {
        matches.take(GRID_SLOTS.size).forEachIndexed { index, offer ->
            val slot = GRID_SLOTS[index]
            button(
                slot,
                item(offer.material) {
                    // No readable tooltip at all: the description is the card, and a box chasing
                    // the cursor on top of it would be the very thing this replaces.
                    name(
                        card(
                            marker("market_item_${offer.texture}", PREVIEW_X, PREVIEW_Y),
                            heading(offer.name, offer.note),
                            marker("market_coin", TEXT_X - 1, COIN_Y),
                            line(GLYPHS, PRICE_X, PRICE_Y, offer.price.toString()),
                        )
                    )
                    tooltipStyle = theme.tooltipStyle(DemoTheme.BLANK)
                },
            ) {
                onClick {
                    player.playSound(CLICK)
                    player.sendMessage(
                        Component.text("Bought ", NamedTextColor.GRAY)
                            .append(Component.text(offer.name, NamedTextColor.WHITE))
                            .append(Component.text(" for ${offer.price}", NamedTextColor.GOLD))
                    )
                }
            }
        }

        // The search control. A container cannot read a keystroke, so the button hands off to a
        // dialog and the dialog hands back a string.
        button(
            SEARCH_SLOT,
            control(theme, height, "search", 0, card(heading("Search", "Type part of a name"))),
        ) {
            onClick {
                player.playSound(CLICK)
                player.closeInventory()
                player.showDialog(marketSearchDialog(query))
            }
        }

        val filterNote =
            if (query.isBlank()) "Nothing filtered" else "${matches.size} of ${CATALOGUE.size} shown"
        button(
            CLEAR_SLOT,
            control(theme, height, "close", 1, card(heading("Clear", filterNote))),
        ) {
            onClick {
                player.playSound(CLICK)
                openMarket(player, "")
            }
        }

        button(
            HELP_SLOT,
            control(theme, height, "question", 2, card(heading("How this works", "Hover writes here"))),
        ) {
            onClick {
                player.playSound(CLICK)
                player.sendMessage(
                    Component.text("The card is part of the window, not a tooltip: ", NamedTextColor.GRAY)
                        .append(
                            Component.text(
                                "every line is drawn by markers the hovered item carries.",
                                NamedTextColor.WHITE,
                            )
                        )
                )
            }
        }

        // A filtered catalogue leaves holes in the grid; they behave like every other empty tile.
        val used = GRID_SLOTS.take(matches.size) + listOf(SEARCH_SLOT, CLEAR_SLOT, HELP_SLOT)
        (0 until ROWS * 9).filterNot(used::contains).forEach { slot ->
            button(slot, blankTile(theme, height, slot)) {}
        }
    }
        .open()
}

/**
 * A control in the spare column: the icon is painted into the panel, the hover traces its contour
 * and writes its own explanation into the card.
 */
private fun control(theme: Theme, height: Int, icon: String, row: Int, explanation: Component) =
    item(Material.BUNDLE) {
        name(
            Component.empty()
                .append(
                    theme.frameMarker(
                        "market_outline_$icon",
                        8 + 18 * 8 - 2,
                        18 + 18 * row - 2,
                        imageHeight = height,
                    )
                )
                .append(explanation)
        )
        itemModel = theme.itemModel(DemoTheme.BLANK)
        tooltipStyle = theme.tooltipStyle(DemoTheme.BLANK)
    }

/** An empty tile: blanks vanilla's box with the panel's own pixels and draws nothing else. */
private fun blankTile(theme: Theme, height: Int, slot: Int) =
    item(Material.BUNDLE) {
        name(
            theme.frameMarker(
                "mk_cover_$slot",
                8 + 18 * (slot % 9),
                18 + 18 * (slot / 9),
                imageHeight = height,
            )
        )
        itemModel = theme.itemModel(DemoTheme.BLANK)
        tooltipStyle = theme.tooltipStyle(DemoTheme.BLANK)
    }

/**
 * Trims a line to what the glyph set can draw, and to the width the card has for it.
 *
 * Two different failures, both worth swallowing here. The set covers printable ASCII and a player
 * can type anything into the dialog — rendering text is the one place where throwing is wrong,
 * because the string belongs to the player and an umlaut in a search box should narrow a list
 * rather than crash the screen it came from. And a line wider than the card would march out of it
 * and, far enough out, past the signed byte a marker's position fits in.
 */
private fun Theme.fit(set: String, body: String, available: Int): String {
    var out = body.filter { it.code in 32..126 }
    while (out.isNotEmpty() && textWidth(set, out) > available) {
        out = out.dropLast(1)
    }
    return out
}
