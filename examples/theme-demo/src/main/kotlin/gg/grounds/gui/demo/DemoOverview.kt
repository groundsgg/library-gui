package gg.grounds.gui.demo

import gg.grounds.gui.gui
import gg.grounds.gui.item
import gg.grounds.gui.theme.Theme
import gg.grounds.gui.theme.chestAnchor
import gg.grounds.gui.theme.containerHeight
import gg.grounds.gui.theme.frameMarker
import gg.grounds.gui.theme.title
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player
import net.minestom.server.item.Material

private const val ROWS = 6
private val CLICK = Sound.sound(Key.key("ui.button.click"), Sound.Source.MASTER, 0.4f, 1f)

/** Row 0 and row 4 carry section titles instead of items, so their slots stay empty. */
private const val TITLE_ROW_ONE = 0
private const val TITLE_ROW_TWO = 4
private const val TOOLBAR_ROW = 3

/** The groups are seven wide, inset one column on each side. */
private val CONTENT_SLOTS: List<Int> = listOf(1, 2).flatMap { row -> (1..7).map { col -> row * 9 + col } }
private val TELEPORT_SLOTS: List<Int> = (1..7).map { 45 + it }
private val TOOLBAR_SLOTS: List<Int> = (1..7).map { TOOLBAR_ROW * 9 + it }

private val OVERVIEW =
    listOf(
        Material.OAK_SAPLING to "Farming",
        Material.PAINTING to "Cosmetics",
        Material.SLIME_BLOCK to "Parkour",
        Material.FISHING_ROD to "Fishing",
        Material.BOOK to "Quests",
        Material.GOLDEN_PICKAXE to "Mining",
        Material.CRAFTING_TABLE to "Workshop",
        Material.WHITE_BANNER to "Clans",
        Material.RED_BED to "Home",
        Material.CHEST to "Storage",
        Material.SHULKER_BOX to "Vault",
    )

private val TELEPORT =
    listOf(
        Material.ENDER_PEARL to "Spawn",
        Material.CAKE to "Market",
        Material.ANVIL to "Forge",
        Material.BRICKS to "City",
    )

/**
 * The toolbar, one button per icon from the grounds set.
 *
 * There is no button under them: the icon is the button. Each is 16x16 with its artwork centred,
 * which is exactly a slot's item area, so it lands on the panel at the slot's own position with no
 * scaling and no resampling — the pixel art stays pixel art.
 */
internal val TOOLBAR_ICONS =
    listOf(
        "arrow_left" to "Back",
        "search" to "Search",
        "plus" to "Add",
        "minus" to "Remove",
        "refresh" to "Refresh",
        "lock_closed" to "Locked",
        "settings" to "Settings",
    )

/**
 * A sectioned screen: two labelled groups with a small toolbar between them.
 *
 * The structure only fits because two of the six rows are spent on the section titles rather than
 * on items — a container's rows are a fixed 18px apart, so a heading needs a row of its own. Those
 * rows hold invisible items so the panel below them stays uncovered.
 *
 * The interesting part is the hover, and it differs per row rather than per screen. The content
 * slots get vanilla's own box, the toolbar gets its icons outlined along their silhouette, and the
 * title rows get nothing at all. None of that is reachable while the client's single global sprite
 * is doing the drawing — a marker per slot is what makes highlighting selective.
 */
fun openOverview(player: Player) {
    val theme = DemoTheme.current()
    val height = containerHeight(ROWS)

    // Only when vanilla's own box is blanked — otherwise the two would stack and the slot would
    // read twice as bright as every other slot on screen.
    fun slotHighlight(slot: Int) =
        if (!DemoTheme.blankHighlight) {
            Component.empty()
        } else {
            theme.frameMarker(
                "ov_slot",
                8 + 18 * (slot % 9),
                18 + 18 * (slot / 9),
                imageHeight = height,
            )
        }

    gui(player, theme.title("overview", Component.empty(), chestAnchor(ROWS)), rows = ROWS) {
        // Rows 1 and 2 hold the overview entries; each hands itself a highlight on hover.
        OVERVIEW.forEachIndexed { index, (material, label) ->
            val slot = CONTENT_SLOTS[index]
            button(slot, entry(theme, material, label, slotHighlight(slot))) {
                onClick {
                    player.playSound(CLICK)
                    player.sendMessage(Component.text(label, NamedTextColor.AQUA))
                }
            }
        }

        TELEPORT.forEachIndexed { index, (material, label) ->
            val slot = TELEPORT_SLOTS[index]
            button(slot, entry(theme, material, label, slotHighlight(slot))) {
                onClick {
                    player.playSound(CLICK)
                    player.sendMessage(Component.text("Teleport: $label", NamedTextColor.LIGHT_PURPLE))
                }
            }
        }

        // The toolbar has no button faces: each icon is the button, sitting on bare panel, and the
        // hover traces its outer contour. Three layers, and the order is why it works — the client
        // walks a tooltip's glyphs in sequence, so markers stack in the order they are appended:
        //
        //  1. a patch of bare panel face over the slot, which blanks vanilla's own hover box. That
        //     box is the reason there is a stack at all; without a button face to hide it, it would
        //     sit as a white square behind an outline that is trying to trace a shape.
        //  2. the contour, one pixel outside the icon's silhouette. It goes under the icon rather
        //     than over it so the ring can never eat into the artwork it is outlining.
        //  3. the icon, because layer 1 wiped the one painted into the panel.
        TOOLBAR_ICONS.forEachIndexed { index, (icon, label) ->
            val slot = TOOLBAR_SLOTS[index]
            val x = 8 + 18 * (slot % 9)
            val y = 18 + 18 * TOOLBAR_ROW
            fun marker(id: String, dx: Int, dy: Int, tint: String? = null) =
                theme.frameMarker(id, x + dx, y + dy, imageHeight = height, tint = tint)

            val hover =
                Component.empty()
                    .append(marker("ov_cover", 0, 0))
                    // The contour sprite is 20x20 with the icon's mask at (2, 2), so both rings
                    // clear the edges; that inset is what the -2 undoes.
                    .append(marker("ov_outline_$icon", -2, -2, tint = ACCENT))
                    .append(marker("ov_icon_$icon", 0, 0))

            button(slot, blank(theme, hover)) {
                onClick {
                    player.playSound(CLICK)
                    player.sendMessage(Component.text(label, NamedTextColor.GRAY))
                }
            }
        }

        // Everything else stays empty and invisible so the artwork underneath shows through — and
        // hovers to a soft tint rather than to vanilla's box.
        //
        // The menu can do this with a single sprite because every empty tile there sits on flat
        // panel face. Here they sit on face, on well grey, and on the section headings, whose text
        // runs through the top slot row: ten different backgrounds under thirty-two tiles. So the
        // patch is not one colour but each slot's own 16x16 lifted out of the panel, which blanks
        // the box while keeping whatever was underneath — heading text included.
        //
        // By default the cut-out is untinted, so a hovered empty tile looks exactly like an
        // unhovered one: an empty tile is not a control and should not answer the cursor. `/tint`
        // swaps in the same cut-out lifted toward white, for when it should.
        val used =
            CONTENT_SLOTS.take(OVERVIEW.size) +
                TELEPORT_SLOTS.take(TELEPORT.size) +
                TOOLBAR_SLOTS
        (0 until ROWS * 9).filterNot(used::contains).forEach { slot ->
            val tint =
                theme.frameMarker(
                    DemoTheme.overviewTile(slot),
                    8 + 18 * (slot % 9),
                    18 + 18 * (slot / 9),
                    imageHeight = height,
                )
            button(slot, blank(theme, tint)) {}
        }
    }
        .open()
}

private fun entry(theme: Theme, material: Material, label: String, hover: Component) =
    item(material) {
        name(Component.text(label, NamedTextColor.WHITE).append(hover))
        tooltipStyle = theme.tooltipStyle(DemoTheme.TOOLTIP_TOOL)
    }

private fun blank(theme: Theme, hover: Component) =
    item(Material.BUNDLE) {
        name(hover)
        itemModel = theme.itemModel(DemoTheme.BLANK)
        tooltipStyle = theme.tooltipStyle(DemoTheme.BLANK)
    }
