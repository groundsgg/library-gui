package gg.grounds.gui.demo

import gg.grounds.gui.button
import gg.grounds.gui.gui
import gg.grounds.gui.item
import gg.grounds.gui.pagedGui
import gg.grounds.gui.theme.anchorOf
import gg.grounds.gui.theme.title
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.minestom.server.entity.Player
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.Material

private val LABEL = TextColor.color(0x404040)

private fun label(text: String) = Component.text(text, LABEL)

/**
 * Four themed screens, chosen because each one anchors its title differently.
 *
 * This is the demo for a fix rather than for a feature: a panel is positioned relative to the
 * title, and the title is not in the same place on every screen. A six-row chest and a hopper keep
 * the container default of x=8, an anvil starts at 60, and a dispenser centres its title on the
 * label's rendered width. Assuming 8 everywhere slides the artwork sideways on six screen types,
 * and each panel here carries a tinted strip under the title row so a slip is obvious.
 */
fun openScreenGallery(player: Player) {
    pagedGui(
        player,
        title = { _, _ -> Component.text("Themed screens", NamedTextColor.DARK_GRAY) },
        items = SCREENS,
        rows = 3,
        render = { screen ->
            button(
                item(screen.icon) {
                    name(Component.text(screen.name, NamedTextColor.WHITE))
                    lore(Component.text(screen.note, NamedTextColor.DARK_GRAY))
                }
            ) {
                onClick { screen.open(player) }
            }
        },
    ) {
        navigation()
    }
        .open()
}

private class Screen(
    val name: String,
    val icon: Material,
    val note: String,
    val open: (Player) -> Unit,
)

private val SCREENS: List<Screen> =
    listOf(
        Screen("shop · 6-row chest", Material.CHEST, "title x=8, window 176×222") { player ->
            val theme = DemoTheme.current()
            gui(
                player,
                theme.title("screen_shop", label("Shop"), anchorOf(InventoryType.CHEST_6_ROW)),
                rows = 6,
            ) {
                listOf(Material.DIAMOND, Material.EMERALD, Material.GOLD_INGOT).forEachIndexed {
                    index,
                    material ->
                    button(11 + index * 2, item(material) { name(label(material.name())) }) {
                        onClick { player.sendMessage(Component.text("bought", NamedTextColor.GREEN)) }
                    }
                }
                button(49, backButton(player)) { onClick { openScreenGallery(player) } }
            }
                .open()
        },
        Screen("toolbar · hopper", Material.HOPPER, "title x=8, window 176×133") { player ->
            val theme = DemoTheme.current()
            gui(
                player,
                theme.title("screen_toolbar", label("Tools"), anchorOf(InventoryType.HOPPER)),
                InventoryType.HOPPER,
            ) {
                listOf(Material.IRON_PICKAXE, Material.IRON_AXE, Material.IRON_SHOVEL)
                    .forEachIndexed { index, material ->
                        button(index + 1, item(material) { name(label(material.name())) }) {
                            onClick { openScreenGallery(player) }
                        }
                    }
            }
                .open()
        },
        Screen("forge · anvil", Material.ANVIL, "title x=60 — not 8") { player ->
            val theme = DemoTheme.current()
            gui(
                player,
                theme.title("screen_forge", label("Forge"), anchorOf(InventoryType.ANVIL)),
                InventoryType.ANVIL,
            ) {
                button(0, item(Material.IRON_SWORD) { name(label("input")) }) {
                    onClick { openScreenGallery(player) }
                }
            }
                .open()
        },
        Screen("centred · dispenser", Material.DISPENSER, "title centred — label must be empty") {
            player ->
            val theme = DemoTheme.current()
            // No label on purpose. The client centres this title on the label's rendered width,
            // and a themed glyph run measures zero, so an empty label puts the panel at exactly
            // half the window. Anything else and title() refuses rather than guessing.
            gui(
                player,
                theme.title("screen_centred", anchor = anchorOf(InventoryType.WINDOW_3X3)),
                InventoryType.WINDOW_3X3,
            ) {
                (0 until 9).forEach { slot ->
                    button(slot, item(Material.LIGHT_GRAY_STAINED_GLASS_PANE) { name(label(" ")) }) {
                        onClick { openScreenGallery(player) }
                    }
                }
            }
                .open()
        },
    )

private fun backButton(player: Player) =
    item(Material.ARROW) { name(Component.text("Back", NamedTextColor.YELLOW)) }
