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

/** The card the generator sunk into the panel, and the interior a hover writes into. */
private const val CARD_X = 8
private const val CARD_Y = 68
private const val CARD_W = 160
private const val CARD_INNER_X = CARD_X + 3
private const val CARD_INNER_Y = CARD_Y + 3

/** Four lines fit; twelve pixels apart is vanilla's own line height plus breathing room. */
private const val LINE_HEIGHT = 12

private val GRID_SLOTS: List<Int> = (0..2).flatMap { row -> (1..7).map { col -> row * 9 + col } }
private const val SEARCH_SLOT = 8
private const val CLEAR_SLOT = 17

private class Offer(
    val material: Material,
    val name: String,
    val price: Int,
    val note: String,
)

/** Deliberately more than fits, so the search has something to narrow down. */
private val CATALOGUE =
    listOf(
        Offer(Material.DIAMOND_SWORD, "Diamond Sword", 240, "Sharpness III"),
        Offer(Material.DIAMOND_PICKAXE, "Diamond Pickaxe", 220, "Efficiency IV"),
        Offer(Material.DIAMOND_AXE, "Diamond Axe", 210, "Unbreaking II"),
        Offer(Material.IRON_SWORD, "Iron Sword", 60, "Plain steel"),
        Offer(Material.IRON_PICKAXE, "Iron Pickaxe", 55, "Plain steel"),
        Offer(Material.BOW, "Bow", 90, "Power II"),
        Offer(Material.ARROW, "Arrow", 2, "Sold in stacks"),
        Offer(Material.GOLDEN_APPLE, "Golden Apple", 120, "Absorption"),
        Offer(Material.COOKED_BEEF, "Cooked Beef", 8, "Restores 8"),
        Offer(Material.BREAD, "Bread", 3, "Restores 5"),
        Offer(Material.OAK_LOG, "Oak Log", 4, "Building block"),
        Offer(Material.STONE, "Stone", 2, "Building block"),
        Offer(Material.GLASS, "Glass", 6, "Building block"),
        Offer(Material.TORCH, "Torch", 1, "Light level 14"),
        Offer(Material.ENDER_PEARL, "Ender Pearl", 150, "Throwable"),
        Offer(Material.SHIELD, "Shield", 70, "Blocks melee"),
        Offer(Material.ELYTRA, "Elytra", 900, "Requires fireworks"),
        Offer(Material.TNT, "TNT", 45, "Handle with care"),
        Offer(Material.LAVA_BUCKET, "Lava Bucket", 65, "One use"),
        Offer(Material.SADDLE, "Saddle", 80, "For horses"),
        Offer(Material.NAME_TAG, "Name Tag", 130, "Renames a mob"),
        Offer(Material.EMERALD, "Emerald", 30, "Villager currency"),
        Offer(Material.BOOK, "Book", 12, "For enchanting"),
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

    /** A line of the card, centred or left-aligned, drawn over the blanked interior. */
    fun line(row: Int, body: String, centred: Boolean = false): Component {
        val safe = renderable(body)
        val x =
            if (centred) CARD_X + (CARD_W - theme.textWidth(GLYPHS, safe)) / 2
            else CARD_INNER_X + 3
        return theme.text(GLYPHS, x, CARD_INNER_Y + 4 + row * LINE_HEIGHT, safe, imageHeight = height)
    }

    /**
     * The card, written from scratch on every hover.
     *
     * The blanking patch comes first because the panel already carries a resting hint, and markers
     * draw in the order they are appended. Without it the two would overlap into mush.
     */
    fun card(vararg lines: Component): Component =
        lines.fold(marker("market_card", CARD_INNER_X, CARD_INNER_Y)) { acc, l -> acc.append(l) }

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
                            line(0, offer.name, centred = true),
                            line(1, "Price  ${offer.price} coins"),
                            line(2, offer.note),
                            line(3, "Left click to buy"),
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
        button(SEARCH_SLOT, control(theme, height, "search", card(line(0, "Search the catalogue", centred = true)))) {
            onClick {
                player.playSound(CLICK)
                player.closeInventory()
                player.showDialog(marketSearchDialog(query))
            }
        }

        button(
            CLEAR_SLOT,
            control(
                theme,
                height,
                "close",
                card(
                    line(0, "Clear the filter", centred = true),
                    line(1, if (query.isBlank()) "Nothing filtered" else "Showing ${matches.size} of ${CATALOGUE.size}"),
                ),
            ),
        ) {
            onClick {
                player.playSound(CLICK)
                openMarket(player, "")
            }
        }

        // A filtered catalogue leaves holes in the grid; they behave like every other empty tile.
        val used = GRID_SLOTS.take(matches.size) + listOf(SEARCH_SLOT, CLEAR_SLOT)
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
private fun control(theme: Theme, height: Int, icon: String, explanation: Component) =
    item(Material.BUNDLE) {
        name(
            Component.empty()
                .append(
                    theme.frameMarker(
                        "market_outline_$icon",
                        8 + 18 * 8 - 2,
                        18 + 18 * (if (icon == "search") 0 else 1) - 2,
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
 * Drops what the glyph set cannot draw.
 *
 * The set covers printable ASCII, and a player can type anything into the dialog. Rendering text is
 * the one place where failing loudly is wrong: the string is the player's, not the programmer's, and
 * an umlaut in a search box should narrow a list rather than throw an exception at whoever opened
 * the screen. Everything the server itself writes stays inside the set, so this only ever trims
 * input.
 */
private fun renderable(text: String): String = text.filter { it.code in 32..126 }
