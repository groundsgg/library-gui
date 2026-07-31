package gg.grounds.gui

import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerAnvilInputEvent
import net.minestom.server.event.trait.EntityEvent
import net.minestom.server.inventory.type.AnvilInventory
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

/**
 * Anvil-based text input. The player types into the rename field and clicks the output slot to
 * confirm.
 *
 * Vanilla-client caveats the server cannot enforce (unverified against a live client): the client
 * only sends input once slot 0 holds an item (pre-filled here), and it sends on roughly every
 * keystroke. Repair cost is pinned to 0 so the output slot never renders as unaffordable.
 */
class AnvilInput(
    player: Player,
    title: Component,
    initial: String = "",
    private val confirmLabel: Component = Component.text("Confirm"),
    private val onConfirm: (String) -> Unit,
) : Gui(player, AnvilInventory(title)) {

    /** Last text the client sent; [initial] until the player types. */
    var input: String = initial
        private set

    private val confirm: GuiButton =
        GuiButton(confirmItem()).also { button ->
            button.onClick {
                close()
                onConfirm(input)
            }
        }

    init {
        // Repair cost stays at AnvilInventory's default of 0, so the output slot
        // never renders as unaffordable. (Sending the property here would reach
        // zero viewers anyway — the GUI is not open yet.)
        // The rename field pre-fills with the slot-0 item's CUSTOM_NAME (the
        // one place where CUSTOM_NAME, not ITEM_NAME, is the point).
        item(0, ItemStack.of(Material.PAPER).withCustomName(Component.text(initial)))
        setButton(2, confirm)
    }

    override fun configureNode(node: EventNode<EntityEvent>) {
        super.configureNode(node)
        node.addListener(PlayerAnvilInputEvent::class.java) { event ->
            if (event.inventory !== inventory) return@addListener
            input = event.input
            confirm.item = confirmItem()
            refreshSlot(2)
        }
    }

    private fun confirmItem(): ItemStack =
        item(Material.LIME_DYE) {
            name(confirmLabel)
            lore(Component.text(input))
        }
}
