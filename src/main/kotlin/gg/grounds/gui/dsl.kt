package gg.grounds.gui

import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack

private fun chestType(rows: Int): InventoryType =
    when (rows) {
        1 -> InventoryType.CHEST_1_ROW
        2 -> InventoryType.CHEST_2_ROW
        3 -> InventoryType.CHEST_3_ROW
        4 -> InventoryType.CHEST_4_ROW
        5 -> InventoryType.CHEST_5_ROW
        6 -> InventoryType.CHEST_6_ROW
        else -> throw IllegalArgumentException("rows must be 1..6, got $rows")
    }

/** Builds a chest GUI. Call [Gui.open] to show it. One instance serves one player. */
fun gui(player: Player, title: Component, rows: Int = 6, block: Gui.() -> Unit = {}): Gui =
    Gui(player, Inventory(chestType(rows), title)).apply(block)

/** Builds a GUI backed by an arbitrary container type. */
fun gui(player: Player, title: Component, type: InventoryType, block: Gui.() -> Unit = {}): Gui =
    Gui(player, Inventory(type, title)).apply(block)

/**
 * Builds a paged chest GUI. [items] render into every slot except the bottom row, which stays free
 * for navigation — wire buttons there in [block] that call
 * [PagedGui.nextPage]/[PagedGui.previousPage].
 */
fun <T> pagedGui(
    player: Player,
    title: (page: Int, pageCount: Int) -> Component,
    items: List<T>,
    rows: Int = 6,
    render: (T) -> GuiButton,
    block: PagedGui<T>.() -> Unit = {},
): PagedGui<T> {
    require(rows >= 2) { "paged GUI needs at least 2 rows (content + navigation)" }
    val contentSlots = (0 until (rows - 1) * 9).toList()
    val inventory = Inventory(chestType(rows), title(0, 1))
    return PagedGui(player, inventory, items, contentSlots, title, render).apply(block)
}

/** A standalone clickable button, e.g. for [pagedGui]'s render lambda. */
fun button(item: ItemStack, block: GuiButton.() -> Unit = {}): GuiButton =
    GuiButton(item).apply(block)

/** Opens an anvil text prompt; [onConfirm] runs when the player clicks the output slot. */
fun anvilInput(
    player: Player,
    title: Component,
    initial: String = "",
    onConfirm: (String) -> Unit,
): AnvilInput = AnvilInput(player, title, initial, onConfirm = onConfirm).also { it.open() }
