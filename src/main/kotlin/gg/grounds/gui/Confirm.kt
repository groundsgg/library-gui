package gg.grounds.gui

import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import net.minestom.server.item.Material

/**
 * A yes/no dialog. [onCancel] also runs when the player closes the GUI without choosing (or
 * disconnects), so it fires exactly once for every shown dialog. Call [Gui.open] on the result.
 */
fun confirmGui(
    player: Player,
    title: Component,
    confirmItem: net.minestom.server.item.ItemStack =
        item(Material.LIME_DYE) { name(Component.text("Confirm")) },
    cancelItem: net.minestom.server.item.ItemStack =
        item(Material.RED_DYE) { name(Component.text("Cancel")) },
    onCancel: () -> Unit = {},
    onConfirm: () -> Unit,
): Gui =
    gui(player, title, rows = 3) {
        var decided = false
        button(11, confirmItem) {
            onClick {
                decided = true
                close()
                onConfirm()
            }
        }
        button(15, cancelItem) {
            onClick {
                decided = true
                close()
                onCancel()
            }
        }
        onClose { if (!decided) onCancel() }
    }
