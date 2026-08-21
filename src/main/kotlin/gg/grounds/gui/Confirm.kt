package gg.grounds.gui

import gg.grounds.gui.bedrock.BedrockForms
import net.kyori.adventure.text.Component
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.Player
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

/**
 * A yes/no dialog. [onCancel] also runs when the player closes the GUI without choosing (or
 * disconnects), so it fires exactly once for every shown dialog. Call [Gui.open] on the result.
 *
 * A Bedrock player gets a native modal form instead of the chest — same contract, including the
 * "cancel also means dismissed" rule. See [BedrockForms] for why translating the chest is not an
 * option, and call [BedrockForms.install] once at startup or the dialog never answers.
 */
fun confirmGui(
    player: Player,
    title: Component,
    confirmItem: ItemStack = item(Material.LIME_DYE) { name(Component.text("Confirm")) },
    cancelItem: ItemStack = item(Material.RED_DYE) { name(Component.text("Cancel")) },
    onCancel: () -> Unit = {},
    onConfirm: () -> Unit,
): Gui {
    var decided = false

    // A subclass rather than a second entry point: every existing call site keeps working, and the
    // decision about which surface to draw belongs at open() time, not at the caller.
    val dialog =
        object : Gui(player, Inventory(InventoryType.CHEST_3_ROW, title)) {
            override fun open() {
                if (!BedrockForms.isBedrock(player)) {
                    super.open()
                    return
                }
                // The form's own close is the dismissal, so `decided` is not consulted here —
                // BedrockForms answers exactly once per form, including on disconnect.
                BedrockForms.modal(
                    player = player,
                    title = title,
                    content = Component.empty(),
                    button1 = confirmItem.label(fallback = "Confirm"),
                    button2 = cancelItem.label(fallback = "Cancel"),
                ) { answer ->
                    if (answer == true) onConfirm() else onCancel()
                }
            }
        }

    return dialog.apply {
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
}

/**
 * The item's display name, for a surface that renders text rather than items. A form button is a
 * label; an [ItemStack] is a texture that usually carries one.
 */
private fun ItemStack.label(fallback: String): Component =
    get(DataComponents.ITEM_NAME) ?: Component.text(fallback)
