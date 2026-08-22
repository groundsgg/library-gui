package gg.grounds.gui.menu

import gg.grounds.gui.GuiButton
import gg.grounds.gui.bedrock.BedrockForms
import gg.grounds.gui.button
import gg.grounds.gui.item
import gg.grounds.gui.pagedGui
import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

/**
 * A menu described by what it offers rather than by where it draws.
 *
 * The inventory DSL states a coordinate and an item: `button(13, item(DIAMOND))` says *where*,
 * never *which*. That is enough for a chest and not enough for anything else — a Bedrock form has
 * no slots, only an ordered list of labelled buttons, so a renderer targeting one cannot recover
 * the order or the text from a slot map.
 *
 * Every menu in the network turned out to be the same shape underneath: an ordered list of entries
 * carrying a label, a description, an icon and a state. Saying that directly is what lets the same
 * definition become a chest for a Java player and a native form for a Bedrock one.
 *
 * A menu whose positions genuinely mean something — a grid, a map picker — is not this. Keep using
 * [gg.grounds.gui.gui]; forms have no geometry and translating one is worse than leaving it.
 */
class Menu
internal constructor(
    val player: Player,
    val title: Component,
    val entries: List<MenuEntry>,
    private val rows: Int,
) {

    /**
     * Draws the menu on whichever surface fits the player, and hands control to it.
     *
     * Nothing about the choice is visible to the caller — which is the point of describing the menu
     * instead of drawing it.
     */
    fun open() {
        if (BedrockForms.isBedrock(player)) openForm() else openInventory()
    }

    private fun openInventory() {
        pagedGui(
                player = player,
                title = { _, _ -> title },
                items = entries,
                rows = rows,
                render = ::inventoryButton,
            ) {
                navigation()
            }
            .open()
    }

    private fun openForm() {
        BedrockForms.simple(
            player = player,
            title = title,
            content = Component.empty(),
            buttons = entries.map(::formLabel),
        ) { index ->
            // A dismissed form answers null, and so does an index the client made up. Either way
            // nothing was chosen, which is exactly what closing a chest without clicking means.
            index?.let { select(entries[it]) }
        }
    }

    private fun select(entry: MenuEntry) {
        if (entry.state == EntryState.UNAVAILABLE) return
        entry.onSelect()
    }

    internal fun inventoryButton(entry: MenuEntry): GuiButton =
        button(itemFor(entry)) { onClick { select(entry) } }

    internal companion object {
        /**
         * The item a Java player sees. The label is the name and the description is the lore, so an
         * entry reads the same on both surfaces; a selected entry glows, which is the one piece of
         * state a chest can show without a second icon.
         */
        fun itemFor(entry: MenuEntry): ItemStack =
            item(entry.icon) {
                name(entry.label)
                entry.description?.let { lore(it) }
                glowing = entry.state == EntryState.SELECTED
            }

        /**
         * The button text a Bedrock player sees. A form button is one label, so the description
         * rides on a second line rather than being dropped — there is no lore to put it in.
         */
        fun formLabel(entry: MenuEntry): Component =
            entry.description?.let { entry.label.append(Component.newline()).append(it) }
                ?: entry.label
    }
}

/** What an entry currently is, in the only three flavours the network's menus actually use. */
enum class EntryState {
    AVAILABLE,

    /** Shown, but choosing it does nothing — a mode with no server, a ware nobody can afford. */
    UNAVAILABLE,

    /** The one already in effect: the open category, the equipped kit, the current region. */
    SELECTED,
}

/**
 * One offer in a [Menu].
 *
 * [onSelect] is deliberately singular. `GuiButton` distinguishes left, right and shift clicks; a
 * form button has one action and Bedrock has no modifier click at all, so a secondary action here
 * would be a feature only Java players could ever reach. Model it as an entry that opens a menu of
 * its own instead.
 */
class MenuEntry
internal constructor(
    val id: String,
    val label: Component,
    val description: Component?,
    val icon: Material,
    val state: EntryState,
    internal val onSelect: () -> Unit,
)

class MenuEntryBuilder internal constructor(private val id: String) {
    var label: Component = Component.text(id)
    var description: Component? = null
    var icon: Material = Material.PAPER
    var state: EntryState = EntryState.AVAILABLE
    private var onSelect: () -> Unit = {}

    /**
     * Runs when the player chooses this entry. Ignored while the entry is [EntryState.UNAVAILABLE].
     */
    fun onSelect(handler: () -> Unit) {
        onSelect = handler
    }

    internal fun build(): MenuEntry = MenuEntry(id, label, description, icon, state, onSelect)
}

class MenuBuilder internal constructor() {
    private val entries = mutableListOf<MenuEntry>()

    /** Adds an entry. Order is the order they are declared in, on both surfaces. */
    fun entry(id: String, block: MenuEntryBuilder.() -> Unit = {}) {
        entries += MenuEntryBuilder(id).apply(block).build()
    }

    internal fun build(): List<MenuEntry> = entries.toList()
}

/**
 * Describes a menu. Call [Menu.open] to show it.
 *
 * [rows] sizes the chest for Java players and is ignored on Bedrock, where a form scrolls and the
 * list has no page to fit into.
 */
fun menu(player: Player, title: Component, rows: Int = 6, block: MenuBuilder.() -> Unit): Menu =
    Menu(player, title, MenuBuilder().apply(block).build(), rows)
