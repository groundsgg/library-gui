package gg.grounds.gui.demo

import gg.grounds.gui.gui
import gg.grounds.gui.item
import gg.grounds.gui.theme.title
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player
import net.minestom.server.item.Material

/**
 * The GUI under test: one themed button next to a plain one, so the difference the pack makes is
 * visible in a single screen rather than from memory.
 *
 * The theme is rebuilt on every open because the demo's offsets are mutable — that is the whole
 * point of it. Rebuilding is cheap and the glyph allocation is deterministic, so the codepoints do
 * not move between opens.
 */
fun openDemoGui(player: Player) {
    val theme = DemoTheme.current()

    gui(player, theme.title(DemoTheme.PANEL, Component.text("theme demo")), rows = 3) {
        button(
            11,
            item(Material.PAPER) {
                name(Component.text("Themed", NamedTextColor.GOLD))
                lore(
                    Component.text("item_model replaces the icon", NamedTextColor.GRAY),
                    Component.text("tooltip_style skins this box", NamedTextColor.GRAY),
                )
                itemModel = theme.itemModel(DemoTheme.ICON)
                tooltipStyle = theme.tooltipStyle(DemoTheme.TOOLTIP)
            },
        ) {
            onClick {
                player.sendMessage(
                    Component.text("Themed button clicked — the pack changed how it looks, not what it does.", NamedTextColor.GOLD),
                )
            }
        }

        button(
            15,
            item(Material.PAPER) {
                name(Component.text("Plain", NamedTextColor.GRAY))
                lore(Component.text("same material, no theme applied", NamedTextColor.DARK_GRAY))
            },
        ) {
            onClick { player.sendMessage(Component.text("Plain button clicked.", NamedTextColor.GRAY)) }
        }
    }.open()
}
