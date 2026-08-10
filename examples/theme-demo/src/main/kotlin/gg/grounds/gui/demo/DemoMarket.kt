package gg.grounds.gui.demo

import gg.grounds.gui.Gui
import gg.grounds.gui.gui
import gg.grounds.gui.item
import gg.grounds.gui.theme.Theme
import gg.grounds.gui.theme.chestAnchor
import gg.grounds.gui.theme.containerHeight
import gg.grounds.gui.layout.Rect
import gg.grounds.gui.theme.frameMarker
import gg.grounds.gui.theme.meterMarker
import gg.grounds.gui.theme.text
import gg.grounds.gui.theme.textWidth
import gg.grounds.gui.theme.title
import java.util.UUID
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player
import net.minestom.server.inventory.click.Click
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.timer.TaskSchedule

private const val ROWS = 6
private val CLICK = Sound.sound(Key.key("ui.button.click"), Sound.Source.MASTER, 0.4f, 1f)

// The card's geometry is not stated here. It lives in MarketLayout, which the painter reads too —
// these used to be a second copy of those numbers, kept honest only by a mismatch being visible.

/** Eight columns wide, so the grid ends on the same margins as the card under it. */
private val GRID_SLOTS: List<Int> = (0..2).flatMap { row -> (0..7).map { col -> row * 9 + col } }
/**
 * The controls in the spare column, and the one list that names them.
 *
 * Three rows, because two buttons beside a three-row grid left a notch. The list lives here and
 * [DemoTheme] registers from it: the first cut spelled the icons out in both places, the third
 * button reached only one of them, and the screen threw on open with a frame it had never
 * declared.
 */
internal val MARKET_CONTROLS = listOf("search" to 8, "arrow_left" to 17, "arrow_right" to 26)

private const val SEARCH_SLOT = 8
private const val PREV_SLOT = 17
private const val NEXT_SLOT = 26

/** A page is the whole grid. Clearing a filter folded into search: an empty query shows everything. */
private val PAGE_SIZE = GRID_SLOTS.size

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
) {
    /**
     * How much of it is left, as a fraction.
     *
     * Derived rather than declared, so the catalogue stays a list of offers rather than a list of
     * offers and their fixtures. The exact values do not matter; that they differ does.
     */
    val stock: Double
        get() = ((name.length * 37 + price) % 101) / 100.0
}

/** More offers than a page holds, which is the only way paging demonstrates anything. */
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
        Offer(Material.GOLD_INGOT, "gold_ingot", "Gold Ingot", 45, "Soft but pretty"),
        Offer(Material.IRON_INGOT, "iron_ingot", "Iron Ingot", 20, "The workhorse"),
        Offer(Material.COAL, "coal", "Coal", 5, "Smelts eight"),
        Offer(Material.REDSTONE, "redstone", "Redstone", 9, "For contraptions"),
        Offer(Material.LAPIS_LAZULI, "lapis_lazuli", "Lapis Lazuli", 14, "For enchanting"),
        Offer(Material.QUARTZ, "quartz", "Quartz", 11, "From the Nether"),
        Offer(Material.AMETHYST_SHARD, "amethyst_shard", "Amethyst Shard", 22, "Grows in geodes"),
        Offer(Material.COPPER_INGOT, "copper_ingot", "Copper Ingot", 7, "Weathers over time"),
        Offer(Material.FLINT, "flint", "Flint", 4, "Struck from gravel"),
        Offer(Material.CLAY_BALL, "clay_ball", "Clay Ball", 3, "Fires into brick"),
        Offer(Material.BRICK, "brick", "Brick", 6, "Fired clay"),
        Offer(Material.LEATHER, "leather", "Leather", 10, "Armour and books"),
        Offer(Material.SLIME_BALL, "slime_ball", "Slime Ball", 16, "Sticky by nature"),
        Offer(Material.HONEYCOMB, "honeycomb", "Honeycomb", 18, "Waxes copper"),
        Offer(Material.BLAZE_ROD, "blaze_rod", "Blaze Rod", 95, "Fuel for brewing"),
        Offer(Material.GHAST_TEAR, "ghast_tear", "Ghast Tear", 140, "Regeneration"),
        Offer(Material.MAGMA_CREAM, "magma_cream", "Magma Cream", 60, "Fire resistance"),
        Offer(Material.NETHER_STAR, "nether_star", "Nether Star", 1200, "One per wither"),
        Offer(Material.PHANTOM_MEMBRANE, "phantom_membrane", "Phantom Membrane", 75, "Mends elytra"),
        Offer(Material.PRISMARINE_SHARD, "prismarine_shard", "Prismarine Shard", 13, "From the deep"),
        Offer(Material.NAUTILUS_SHELL, "nautilus_shell", "Nautilus Shell", 260, "Builds a conduit"),
        Offer(Material.ECHO_SHARD, "echo_shard", "Echo Shard", 320, "From the deep dark"),
        Offer(Material.RABBIT_HIDE, "rabbit_hide", "Rabbit Hide", 8, "Four make leather"),
        Offer(Material.PRISMARINE_CRYSTALS, "prismarine_crystals", "Prismarine Crystals", 21, "Glows faintly"),
    )

/** The preview frames the catalogue can ask for, so a test can check they all exist. */
internal val MARKET_TEXTURES: List<String> = CATALOGUE.map { it.texture }

/** Every string the card can write, so a test can lay each one out where it will really go. */
internal val MARKET_LINES: List<String> =
    CATALOGUE.flatMap { listOf(it.name, it.note, it.price.toString(), "99 x ${it.price}") } +
        listOf("Search", "Type part of a name", "Previous", "Next", "Page 1 of 2", "45 of 45")

/** The fixed pieces of the card, named once. */
internal val MARKET_CARD_PARTS: List<String> = listOf("card", "well", "rule", "coin")

/**
 * The colour a price is written in.
 *
 * Bands rather than a gradient: the eye separates four steps at a glance and reads a continuum as
 * noise. Cheap deliberately recedes into the same weight as the note beside it — a stick costing
 * one coin is not information worth shouting.
 */
internal fun priceTint(price: Int): String =
    when {
        price < 10 -> DIM
        price < 100 -> STANDARD
        price < 300 -> GOLD
        else -> PREMIUM
    }

/**
 * What each player is filtering by, and which page they are on.
 *
 * State a container GUI holds between renders, which is the point of this screen's second half: a
 * chest has no memory of its own, so anything that persists across a click lives here and is
 * re-rendered from scratch.
 */
private val queries = mutableMapOf<UUID, String>()

private val pages = mutableMapOf<UUID, Int>()

/** How many of each offer a player has put in the basket, keyed by texture since that is unique. */
private val baskets = mutableMapOf<UUID, MutableMap<String, Int>>()

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

    // A filter that leaves fewer pages than the one being read has to pull the reader back, or the
    // screen opens on a page that no longer exists and looks empty for no stated reason.
    val pageCount = maxOf(1, (matches.size + PAGE_SIZE - 1) / PAGE_SIZE)
    val page = (pages[player.uuid] ?: 0).coerceIn(0, pageCount - 1)
    pages[player.uuid] = page
    val shown = matches.drop(page * PAGE_SIZE).take(PAGE_SIZE)
    val basket = baskets.getOrPut(player.uuid) { mutableMapOf() }

    fun marker(id: String, where: Rect, tint: String? = null) =
        theme.frameMarker(id, where.x, where.y, imageHeight = height, tint = tint)

    fun line(where: Rect, body: String, tint: String? = null) =
        theme.text(
            GLYPHS,
            where.x,
            where.y,
            theme.fit(GLYPHS, body, where.width),
            imageHeight = height,
            tint = tint,
        )

    /**
     * The card, rewritten from scratch on every hover.
     *
     * The blanking patch comes first and the preview well goes back on top of it, rather than the
     * patch being shaped to spare the well: markers draw in the order they are appended, and one
     * rectangle plus one redraw is far easier to keep correct than a patch with a hole in it.
     */
    fun card(vararg parts: Component): Component =
        parts.fold(
            marker("market_card", MarketLayout.CARD_INNER)
                .append(marker("market_well", MarketLayout.PREVIEW_WELL))
        ) { acc, part -> acc.append(part) }

    /** A heading and a supporting line, the shape every card on this screen uses. */
    fun heading(title: String, note: String) =
        Component.empty()
            .append(line(MarketLayout.NAME, title))
            .append(marker("market_rule", MarketLayout.RULE))
            .append(line(MarketLayout.NOTE, note, tint = DIM))

    gui(player, theme.title("market", Component.empty(), chestAnchor(ROWS)), rows = ROWS) {
        shown.forEachIndexed { index, offer ->
            val slot = GRID_SLOTS[index]
            button(
                slot,
                item(offer.material) {
                    // No readable tooltip at all: the description is the card, and a box chasing
                    // the cursor on top of it would be the very thing this replaces.
                    name(
                        card(
                            marker("market_item_${offer.texture}", MarketLayout.PREVIEW),
                            heading(
                                offer.name,
                                basket[offer.texture]?.takeIf { it > 0 }?.let { "$it x ${offer.price}" }
                                    ?: offer.note,
                            ),
                            marker("market_coin", MarketLayout.COIN),
                            // With something in the basket the card shows the running total rather
                            // than the unit price, and says how it got there on the line above.
                            line(
                                MarketLayout.PRICE,
                                (offer.price * maxOf(1, basket[offer.texture] ?: 0)).toString(),
                                tint = priceTint(offer.price),
                            ),
                            // The groove first, then the bar clipped to what is left. One sprite
                            // serves every value — a frame per step would be sixty-eight of them.
                            marker("market_track", MarketLayout.TRACK),
                            theme.meterMarker(
                                "market_bar",
                                MarketLayout.BAR.x,
                                MarketLayout.BAR.y,
                                offer.stock,
                                imageHeight = height,
                            ),
                        )
                    )
                    tooltipStyle = theme.tooltipStyle(DemoTheme.BLANK)
                },
            ) {
                // No extra controls for a stepper: the click already says which way. Left adds one,
                // right takes one back, and the card is rebuilt from the basket either way.
                onClick {
                    player.playSound(CLICK)
                    val held = basket[offer.texture] ?: 0
                    val next = if (click is Click.Right) held - 1 else held + 1
                    if (next <= 0) basket.remove(offer.texture) else basket[offer.texture] = next
                    openMarket(player, query)
                }
            }
        }

        // A container cannot read a keystroke, so search hands off to a dialog and the dialog hands
        // back a string. All three controls are built in one place so the animation below can
        // rebuild them without restating what they are.
        controls(theme, height, query, matches.size, page) { slot, itemStack ->
            button(slot, itemStack) { onClick { onControl(player, slot, query) } }
        }

        // The controls breathe while hovered, which is the whole of "animation" here: a marker is
        // part of an item's tooltip, so re-sending the slot changes what is drawn. Nothing about
        // the pack moves — the pulse walks three declared colours, and the sprite is the same white
        // contour at every step.
        //
        // Only the player pointing at a control ever sees it. A marker draws inside a tooltip and a
        // tooltip renders for one slot at a time, so there is no ambient motion to be had; the
        // panel itself rides in the window title, which cannot change without reopening the window.
        var tick = 0L
        every(TaskSchedule.tick(1)) {
            tick++
            controls(theme, height, query, matches.size, page, tick) { slot, itemStack ->
                button(slot, itemStack) { onClick { onControl(player, slot, query) } }
            }
        }

        // A filtered catalogue leaves holes in the grid; they behave like every other empty tile.
        val used = GRID_SLOTS.take(shown.size) + listOf(SEARCH_SLOT, PREV_SLOT, NEXT_SLOT)
        (0 until ROWS * 9).filterNot(used::contains).forEach { slot ->
            button(slot, blankTile(theme, height, slot)) {}
        }
    }
        .open()
}

/** The card's fixed frame plus a heading and its supporting line, for a control's explanation. */
private fun cardOf(theme: Theme, height: Int, title: String, note: String): Component {
    fun marker(id: String, where: Rect, tint: String? = null) =
        theme.frameMarker(id, where.x, where.y, imageHeight = height, tint = tint)

    fun line(where: Rect, body: String, tint: String? = null) =
        theme.text(
            GLYPHS,
            where.x,
            where.y,
            theme.fit(GLYPHS, body, where.width),
            imageHeight = height,
            tint = tint,
        )

    return marker("market_card", MarketLayout.CARD_INNER)
        .append(marker("market_well", MarketLayout.PREVIEW_WELL))
        .append(line(MarketLayout.NAME, title))
        .append(marker("market_rule", MarketLayout.RULE))
        .append(line(MarketLayout.NOTE, note, tint = DIM))
}

/** What a control does when pressed. Search leaves the container; paging stays in it. */
private fun onControl(player: Player, slot: Int, query: String) {
    player.playSound(CLICK)
    when (slot) {
        SEARCH_SLOT -> {
            player.closeInventory()
            player.showDialog(marketSearchDialog(query))
        }
        PREV_SLOT -> turn(player, -1, query)
        else -> turn(player, 1, query)
    }
}

/** Turns the page, wrapping, so neither end of a catalogue is a dead button. */
private fun turn(player: Player, by: Int, query: String) {
    val matches =
        if (query.isBlank()) CATALOGUE
        else CATALOGUE.filter { it.name.contains(query.trim(), ignoreCase = true) }
    val pageCount = maxOf(1, (matches.size + PAGE_SIZE - 1) / PAGE_SIZE)
    pages[player.uuid] = ((pages[player.uuid] ?: 0) + by).mod(pageCount)
    openMarket(player, query)
}

/**
 * Builds all three controls at [tick]'s point in the pulse and hands each to [place].
 *
 * One place, because the animation rebuilds them every tick and a second description of what a
 * control is would drift from this one within a day.
 */
private fun Gui.controls(
    theme: Theme,
    height: Int,
    query: String,
    matches: Int,
    page: Int = 0,
    tick: Long = 0,
    place: (Int, ItemStack) -> Unit,
) {
    val tint = ACCENT_PULSE.pingPong(tick)
    val pageCount = maxOf(1, (matches + PAGE_SIZE - 1) / PAGE_SIZE)
    val cards =
        listOf(
            "Search" to if (query.isBlank()) "Type part of a name" else "$matches of ${CATALOGUE.size}",
            "Previous" to "Page ${page + 1} of $pageCount",
            "Next" to "Page ${page + 1} of $pageCount",
        )
    MARKET_CONTROLS.forEachIndexed { row, (icon, slot) ->
        val (title, note) = cards[row]
        place(slot, control(theme, height, icon, row, cardOf(theme, height, title, note), tint))
    }
}

/**
 * A control in the spare column: the icon is painted into the panel, the hover traces its contour
 * and writes its own explanation into the card.
 */
private fun control(
    theme: Theme,
    height: Int,
    icon: String,
    row: Int,
    explanation: Component,
    tint: String = ACCENT,
) =
    item(Material.BUNDLE) {
        name(
            Component.empty()
                .append(
                    theme.frameMarker(
                        "market_outline_$icon",
                        8 + 18 * 8 - 2,
                        18 + 18 * row - 2,
                        imageHeight = height,
                        tint = tint,
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
