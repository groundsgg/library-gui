package gg.grounds.gui.menu

import gg.grounds.gui.GuiButton
import gg.grounds.gui.PagedGui
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
    entries: List<MenuEntry>,
    groups: List<MenuGroup>,
    private val rows: Int,
) {

    var entries: List<MenuEntry> = entries
        private set

    /** The tabs, when the menu has any. Empty for a flat menu. */
    var groups: List<MenuGroup> = groups
        private set

    private var paged: PagedGui<MenuEntry>? = null
    private var selectedGroup: Int = 0

    /**
     * Draws the menu on whichever surface fits the player, and hands control to it.
     *
     * Nothing about the choice is visible to the caller — which is the point of describing the menu
     * instead of drawing it.
     */
    fun open() {
        val bedrock = BedrockForms.isBedrock(player)
        when {
            groups.isEmpty() && bedrock -> openForm()
            groups.isEmpty() -> openInventory()
            bedrock -> openGroupForm()
            else -> openGroupedInventory()
        }
    }

    private fun openInventory() {
        paged =
            pagedGui(
                    player = player,
                    title = { _, _ -> title },
                    items = entries,
                    rows = rows,
                    render = ::inventoryButton,
                ) {
                    navigation()
                }
                .also { it.open() }
    }

    /**
     * The tab row is the top row, centred — a menu with three tabs should not sit hard against the
     * left edge — and the content sits under it. The selected tab glows, because once the row is
     * centred its position tells the player nothing about which one is open.
     */
    private fun openGroupedInventory() {
        require(rows >= 3) { "a grouped menu needs at least 3 rows (tabs + content + navigation)" }
        val contentSlots = (ROW_WIDTH until (rows - 1) * ROW_WIDTH).toList()
        paged =
            pagedGui(
                    player = player,
                    title = { _, _ -> title },
                    items = groups[selectedGroup].entries,
                    rows = rows,
                    contentSlots = contentSlots,
                    render = ::inventoryButton,
                ) {
                    navigation()
                }
                .also {
                    drawTabs(it)
                    it.open()
                }
    }

    private fun drawTabs(gui: PagedGui<MenuEntry>) {
        val offset = (ROW_WIDTH - groups.size).coerceAtLeast(0) / 2
        groups.forEachIndexed { index, group ->
            val slot = offset + index
            if (slot >= ROW_WIDTH) return@forEachIndexed
            gui.button(slot, tabItem(group, selected = index == selectedGroup)) {
                onClick {
                    if (index == selectedGroup) return@onClick
                    selectedGroup = index
                    gui.setItems(group.entries)
                    drawTabs(gui)
                }
            }
        }
    }

    private fun openGroupForm() {
        BedrockForms.simple(
            player = player,
            title = title,
            content = Component.empty(),
            buttons = groups.map { it.label },
        ) { index ->
            // A tab row has no equivalent on a form, so a group becomes its own screen. Dismissing
            // the first one is the same "chose nothing" the chest answers when it is closed.
            index?.let { openEntryForm(groups[it]) }
        }
    }

    private fun openEntryForm(group: MenuGroup) {
        BedrockForms.simple(
            player = player,
            title = group.label,
            content = Component.empty(),
            buttons = group.entries.map(::formLabel),
        ) { index ->
            index?.let { select(group.entries[it]) }
        }
    }

    /**
     * Replaces what the menu offers — for a list that arrives after the menu was opened, which is
     * the normal case when the data comes from another service.
     *
     * A chest re-renders in place. A form does not: it is a snapshot the moment it reaches the
     * device, and the server cannot alter it afterwards. Re-sending one to fake an update would
     * yank the screen out from under the player's thumb, so a Bedrock player keeps what they were
     * shown and sees the new list the next time they open the menu.
     */
    fun setEntries(entries: List<MenuEntry>) {
        this.entries = entries
        paged?.setItems(entries)
    }

    /**
     * The same for a grouped menu. The open tab stays open when it still exists — a player who was
     * looking at "weapons" when the prices changed should still be looking at "weapons".
     */
    fun setGroups(groups: List<MenuGroup>) {
        require(groups.isNotEmpty()) { "a grouped menu needs at least one group" }
        this.groups = groups
        selectedGroup = selectedGroup.coerceIn(0, groups.size - 1)
        paged?.let {
            it.setItems(groups[selectedGroup].entries)
            drawTabs(it)
        }
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
        /** A chest row, which is what makes the top row a tab row. */
        private const val ROW_WIDTH = 9

        /** A tab: the group's own label and icon, glowing while it is the open one. */
        fun tabItem(group: MenuGroup, selected: Boolean): ItemStack =
            item(group.icon) {
                name(group.label)
                glowing = selected
            }

        /**
         * The item a Java player sees. The label is the name and the description is the lore, so an
         * entry reads the same on both surfaces; a selected entry glows, which is the one piece of
         * state a chest can show without a second icon.
         */
        fun itemFor(entry: MenuEntry): ItemStack =
            item(entry.icon) {
                name(entry.label)
                if (entry.description.isNotEmpty()) lore(*entry.description.toTypedArray())
                glowing = entry.state == EntryState.SELECTED
            }

        /**
         * The button text a Bedrock player sees. A form button is one label, so the description
         * rides on a second line rather than being dropped — there is no lore to put it in.
         */
        fun formLabel(entry: MenuEntry): Component =
            entry.description.fold(entry.label) { text, line ->
                text.append(Component.newline()).append(line)
            }
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
    /** Lore on Java, extra lines under the button text on Bedrock. Empty when there is none. */
    val description: List<Component>,
    val icon: Material,
    val state: EntryState,
    internal val onSelect: () -> Unit,
)

class MenuEntryBuilder internal constructor(private val id: String) {
    var label: Component = Component.text(id)
    var icon: Material = Material.PAPER
    var state: EntryState = EntryState.AVAILABLE
    private var description: List<Component> = emptyList()
    private var onSelect: () -> Unit = {}

    /** What the entry says under its label. Several lines, because menus here have several. */
    fun description(vararg lines: Component) {
        description = lines.toList()
    }

    /** The same, for a list that was built rather than spelled out. */
    fun description(lines: List<Component>) {
        description = lines.toList()
    }

    /**
     * Runs when the player chooses this entry. Ignored while the entry is [EntryState.UNAVAILABLE].
     */
    fun onSelect(handler: () -> Unit) {
        onSelect = handler
    }

    internal fun build(): MenuEntry = MenuEntry(id, label, description, icon, state, onSelect)
}

/**
 * A named set of entries — the tab row `ShopScreen` and `DuelMenu` each build by hand today.
 *
 * On Bedrock a tab row has no equivalent, so a group becomes its own form screen reached from a
 * parent one. That is why a group carries a label and an icon of its own rather than being a bare
 * partition of the list.
 */
class MenuGroup
internal constructor(
    val id: String,
    val label: Component,
    val icon: Material,
    val entries: List<MenuEntry>,
)

class MenuGroupBuilder internal constructor(private val id: String) {
    var label: Component = Component.text(id)
    var icon: Material = Material.PAPER
    private val entries = MenuBuilder()

    /** Adds an entry to this group. */
    fun entry(id: String, block: MenuEntryBuilder.() -> Unit = {}) = entries.entry(id, block)

    internal fun build(): MenuGroup = MenuGroup(id, label, icon, entries.build())
}

class MenuBuilder internal constructor() {
    private val entries = mutableListOf<MenuEntry>()
    private val groups = mutableListOf<MenuGroup>()

    /** Adds an entry. Order is the order they are declared in, on both surfaces. */
    fun entry(id: String, block: MenuEntryBuilder.() -> Unit = {}) {
        entries += MenuEntryBuilder(id).apply(block).build()
    }

    /** Adds a tab. A menu is either grouped or flat; mixing the two has no rendering. */
    fun group(id: String, block: MenuGroupBuilder.() -> Unit = {}) {
        groups += MenuGroupBuilder(id).apply(block).build()
    }

    internal fun build(): List<MenuEntry> = entries.toList()

    internal fun buildGroups(): List<MenuGroup> = groups.toList()

    internal fun validate() {
        require(entries.isEmpty() || groups.isEmpty()) {
            "a menu is either grouped or flat: entries outside a group have nowhere to go once the " +
                "top row is tabs"
        }
    }
}

/**
 * Describes a menu. Call [Menu.open] to show it.
 *
 * [rows] sizes the chest for Java players and is ignored on Bedrock, where a form scrolls and the
 * list has no page to fit into.
 */
fun menu(player: Player, title: Component, rows: Int = 6, block: MenuBuilder.() -> Unit): Menu {
    val builder = MenuBuilder().apply(block)
    builder.validate()
    return Menu(player, title, builder.build(), builder.buildGroups(), rows)
}

/**
 * Builds entries without a menu around them — for [Menu.setEntries], where the same declarations
 * have to be made a second time from fresher data.
 */
fun menuEntries(block: MenuBuilder.() -> Unit): List<MenuEntry> = MenuBuilder().apply(block).build()

/** The same for groups, feeding [Menu.setGroups]. */
fun menuGroups(block: MenuBuilder.() -> Unit): List<MenuGroup> =
    MenuBuilder().apply(block).buildGroups()
