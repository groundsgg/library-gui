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
    actions: List<MenuEntry>,
    private val rows: Int,
    private val backLabel: Component?,
    private val staysOpen: Boolean,
    private val onGroupSelect: (String) -> Unit,
) {

    var entries: List<MenuEntry> = entries
        private set

    /** The tabs, when the menu has any. Empty for a flat menu. */
    var groups: List<MenuGroup> = groups
        private set

    /**
     * What sits beside the list rather than in it: a way back, a way to a second screen, a line
     * about where the player currently is.
     */
    var actions: List<MenuEntry> = actions
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
                .also {
                    drawActions(it)
                    it.open()
                }
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
                    drawActions(it)
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
                    // After the redraw: a consumer that rebuilds its groups here (prices that moved
                    // while the shop was open, say) calls setGroups, which redraws again.
                    onGroupSelect(group.id)
                }
            }
        }
    }

    private fun openGroupForm() {
        val shownGroups = groups
        val shownActions = actions
        BedrockForms.simple(
            player = player,
            title = title,
            content = Component.empty(),
            buttons = shownGroups.map(::groupLabel) + shownActions.map(::formLabel),
        ) { index ->
            val chosen = index ?: return@simple
            if (chosen >= shownGroups.size) {
                val action = shownActions[chosen - shownGroups.size]
                if (!select(action)) openGroupForm()
                return@simple
            }
            // A tab row has no equivalent on a form, so a group becomes its own screen. Dismissing
            // the first one is the same "chose nothing" the chest answers when it is closed.
            val group = shownGroups[chosen]
            onGroupSelect(group.id)
            openEntryForm(group)
        }
    }

    /**
     * One group as its own screen.
     *
     * A form is a screen, not a window: the tap that chooses is also the tap that closes it. So a
     * group needs a way back the tab row never did, and a menu whose entries are meant to be used
     * more than once — a shop — has to be sent again after each one.
     */
    private fun openEntryForm(group: MenuGroup) {
        val shown = group.entries
        val back = backLabel
        val buttons = shown.map(::formLabel) + listOfNotNull(back)
        BedrockForms.simple(
            player = player,
            title = group.label,
            content = Component.empty(),
            buttons = buttons,
        ) { index ->
            val chosen = index ?: return@simple
            if (back != null && chosen == shown.size) return@simple openGroupForm()
            if (!select(shown[chosen]) || staysOpen) openEntryForm(group)
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
     * Replaces what sits beside the list — a "you are here" line that only becomes true once the
     * proxy has answered, say.
     */
    fun setActions(actions: List<MenuEntry>) {
        this.actions = actions
        paged?.let(::drawActions)
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

    /**
     * Actions live on the navigation row, which is the one row a page flip never redraws — so they
     * stay put while the list moves under them. [PagedGui.navigation] owns its two ends; these fill
     * inwards from the left of what is left.
     */
    private fun drawActions(gui: PagedGui<MenuEntry>) {
        actions.forEachIndexed { index, action ->
            val slot = gui.size - ROW_WIDTH + 1 + index
            if (slot >= gui.size - 1) return@forEachIndexed
            gui.setButton(slot, inventoryButton(action))
        }
    }

    private fun openForm() {
        // Bound once, on purpose. The properties are replaced by setEntries/setActions, and the
        // consumers do exactly that a tick after opening — so reading them again when the answer
        // arrives would resolve the tap against a list the player never saw. The rendering is
        // already frozen on the device; the resolution has to be frozen with it.
        val shown = entries + actions
        BedrockForms.simple(
            player = player,
            title = title,
            content = Component.empty(),
            buttons = shown.map(::formLabel),
        ) { index ->
            // A dismissed form answers null, and so does an index the client made up. Either way
            // nothing was chosen, which is exactly what closing a chest without clicking means.
            val chosen = index?.let { shown[it] } ?: return@simple
            if (!select(chosen) || staysOpen) openForm()
        }
    }

    /**
     * Runs the entry, and says whether anything happened.
     *
     * The answer matters only on Bedrock. A chest survives a click that does nothing — the window
     * is still there and the player tries another slot. A form is *consumed* by the tap: "did
     * nothing" and "the menu is gone" are the same event, with no sound and no message, which reads
     * as a crash. So the form path re-sends itself when this returns false.
     */
    private fun select(entry: MenuEntry): Boolean {
        if (entry.state == EntryState.UNAVAILABLE || !entry.hasAction) return false
        entry.onSelect()
        return true
    }

    internal fun inventoryButton(entry: MenuEntry): GuiButton =
        button(itemFor(entry)) { onClick { select(entry) } }

    internal companion object {
        /** A chest row, which is what makes the top row a tab row. */
        private const val ROW_WIDTH = 9

        /** A tab: the group's own label and icon, glowing while it is the open one. */
        fun tabItem(group: MenuGroup, selected: Boolean): ItemStack =
            item(group.icon) {
                name(if (selected) group.selectedLabel ?: group.label else group.label)
                if (group.description.isNotEmpty()) lore(*group.description.toTypedArray())
                glowing = selected
            }

        /**
         * The item a Java player sees. The label is the name and the description is the lore, so an
         * entry reads the same on both surfaces; a selected entry glows, which is the one piece of
         * state a chest can show without a second icon.
         */
        fun itemFor(entry: MenuEntry): ItemStack =
            item(entry.icon) {
                amount = entry.amount
                name(entry.label)
                if (entry.description.isNotEmpty()) lore(*entry.description.toTypedArray())
                glowing = entry.state == EntryState.SELECTED
            }

        /**
         * The parent-screen button for a group: its name, then whatever the tab would have said.
         */
        fun groupLabel(group: MenuGroup): Component =
            group.description.fold(group.label) { text, line ->
                text.append(Component.newline()).append(line)
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
    /**
     * Stack size on the icon — a shop sells eight wool, not wool. Java only; a form has no item.
     */
    val amount: Int,
    val state: EntryState,
    internal val onSelect: () -> Unit,
    /**
     * Whether [onSelect] was declared at all. A chest can afford to treat "no handler" as "click
     * does nothing"; a form cannot, because the tap consumes the screen either way.
     */
    internal val hasAction: Boolean,
)

class MenuEntryBuilder internal constructor(private val id: String) {
    var label: Component = Component.text(id)
    var icon: Material = Material.PAPER

    /** Stack size on the icon. Ignored on Bedrock, where the button is text. */
    var amount: Int = 1
    var state: EntryState = EntryState.AVAILABLE
    private var description: List<Component> = emptyList()
    private var onSelect: () -> Unit = {}
    private var hasAction = false

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
        hasAction = true
    }

    internal fun build(): MenuEntry =
        MenuEntry(id, label, description, icon, amount, state, onSelect, hasAction)
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
    /** What the tab reads while it is the open one. Falls back to [label]; Java only. */
    val selectedLabel: Component?,
    /** Lore on the tab, and the lines under the button on Bedrock. Empty when there is none. */
    val description: List<Component>,
    val icon: Material,
    val entries: List<MenuEntry>,
)

class MenuGroupBuilder internal constructor(private val id: String) {
    var label: Component = Component.text(id)

    /**
     * What the tab reads while it is open, when that is more than a highlight — a menu that had a
     * separate wording for the open side before it had a glint.
     */
    var selectedLabel: Component? = null
    var icon: Material = Material.PAPER
    private var description: List<Component> = emptyList()
    private val entries = MenuBuilder()

    /** What the tab says under its name — a total, a hint, whatever the group is worth saying. */
    fun description(vararg lines: Component) {
        description = lines.toList()
    }

    /** The same, for a list that was built rather than spelled out. */
    fun description(lines: List<Component>) {
        description = lines.toList()
    }

    /** Adds an entry to this group. */
    fun entry(id: String, block: MenuEntryBuilder.() -> Unit = {}) = entries.entry(id, block)

    internal fun build(): MenuGroup =
        MenuGroup(id, label, selectedLabel, description, icon, entries.build())
}

class MenuBuilder internal constructor() {
    private val entries = mutableListOf<MenuEntry>()
    private val groups = mutableListOf<MenuGroup>()
    private val actions = mutableListOf<MenuEntry>()
    private var onGroupSelect: (String) -> Unit = {}

    /**
     * The way back out of a group, on Bedrock only.
     *
     * A chest keeps its tab row while the player looks at a shelf; a form replaces the screen, so
     * without this a mistapped category is a one-way door. Needs a caller-supplied Component
     * because the library has no translations of its own. Null leaves the screen without one.
     */
    var backLabel: Component? = null

    /**
     * Whether choosing something keeps the menu open, on Bedrock only.
     *
     * A shop is used several times per visit; a queue button is used once. On Java the chest stays
     * open either way, which is why this has no effect there.
     */
    var staysOpen: Boolean = false

    /** Runs when the player opens a different group — a chance to rebuild it from fresher data. */
    fun onGroupSelect(handler: (String) -> Unit) {
        onGroupSelect = handler
    }

    internal fun groupSelectHandler(): (String) -> Unit = onGroupSelect

    /** Adds an entry. Order is the order they are declared in, on both surfaces. */
    fun entry(id: String, block: MenuEntryBuilder.() -> Unit = {}) {
        entries += MenuEntryBuilder(id).apply(block).build()
    }

    /**
     * Adds something beside the list rather than in it — a way back, a way to another screen.
     *
     * On Java it sits on the navigation row and survives a page flip. On Bedrock, where there is no
     * row to sit on, it follows the entries as an ordinary button.
     */
    fun action(id: String, block: MenuEntryBuilder.() -> Unit = {}) {
        actions += MenuEntryBuilder(id).apply(block).build()
    }

    /** Adds a tab. A menu is either grouped or flat; mixing the two has no rendering. */
    fun group(id: String, block: MenuGroupBuilder.() -> Unit = {}) {
        groups += MenuGroupBuilder(id).apply(block).build()
    }

    internal fun build(): List<MenuEntry> = entries.toList()

    internal fun buildGroups(): List<MenuGroup> = groups.toList()

    internal fun buildActions(): List<MenuEntry> = actions.toList()

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
    return Menu(
        player,
        title,
        builder.build(),
        builder.buildGroups(),
        builder.buildActions(),
        rows,
        builder.backLabel,
        builder.staysOpen,
        builder.groupSelectHandler(),
    )
}

/**
 * Builds entries without a menu around them — for [Menu.setEntries], where the same declarations
 * have to be made a second time from fresher data.
 */
fun menuEntries(block: MenuBuilder.() -> Unit): List<MenuEntry> = MenuBuilder().apply(block).build()

/** The same for actions, feeding [Menu.setActions]. */
fun menuActions(block: MenuBuilder.() -> Unit): List<MenuEntry> =
    MenuBuilder().apply(block).buildActions()

/** The same for groups, feeding [Menu.setGroups]. */
fun menuGroups(block: MenuBuilder.() -> Unit): List<MenuGroup> =
    MenuBuilder().apply(block).buildGroups()
