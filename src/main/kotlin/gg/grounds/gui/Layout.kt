package gg.grounds.gui

import net.minestom.server.item.ItemStack

/**
 * Maps layout rows to slot indices per character. Rows must be exactly 9 characters (chest
 * windows); spaces mean "leave the slot alone".
 */
internal fun layoutSlots(rows: List<String>, size: Int): Map<Char, List<Int>> {
    require(size % 9 == 0) { "layout() needs a chest-shaped inventory, size was $size" }
    require(rows.size <= size / 9) {
        "${rows.size} layout rows but only ${size / 9} inventory rows"
    }
    val slots = mutableMapOf<Char, MutableList<Int>>()
    rows.forEachIndexed { rowIndex, row ->
        require(row.length == 9) { "layout row \"$row\" has ${row.length} chars, expected 9" }
        row.forEachIndexed { column, char ->
            if (char != ' ') slots.getOrPut(char) { mutableListOf() } += rowIndex * 9 + column
        }
    }
    return slots
}

/** Places items and buttons by layout character; see [Gui.layout]. */
class LayoutScope
internal constructor(private val gui: Gui, private val slots: Map<Char, List<Int>>) {
    /** The slots a character covers — e.g. a content region to hand to `pagedGui`. */
    fun slots(char: Char): List<Int> =
        requireNotNull(slots[char]) { "layout has no '$char' — typo in the rows?" }

    /**
     * Places [item] on every slot marked [char] — a decoration without [block], a button with one.
     * Each slot gets its own button; handlers see the clicked slot via context.
     */
    fun place(char: Char, item: ItemStack, block: GuiButton.() -> Unit = {}) {
        slots(char).forEach { gui.button(it, item, block) }
    }
}

/**
 * Declares the GUI's shape as rows of characters, then binds characters to items:
 * ```kotlin
 * gui(player, title, rows = 3) {
 *     layout(
 *         "#########",
 *         "#       #",
 *         "####X####",
 *     ) {
 *         place('#', item(Material.GRAY_STAINED_GLASS_PANE))
 *         place('X', item(Material.BARRIER) { name(text(CLOSE)) }) { onClick { close() } }
 *     }
 * }
 * ```
 *
 * Rows are 9 characters; spaces leave slots untouched. Unbound characters are markers only — read
 * their positions with [LayoutScope.slots].
 */
fun Gui.layout(vararg rows: String, block: LayoutScope.() -> Unit = {}): LayoutScope =
    LayoutScope(this, layoutSlots(rows.toList(), size)).apply(block)
