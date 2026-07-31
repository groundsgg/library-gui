package gg.grounds.gui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.component.DataComponents
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

/**
 * Builds a GUI item. Names use `ITEM_NAME`, which renders non-italic and is not shown as "renamed"
 * in anvils; lore lines get the client's default italic stripped unless a line sets the decoration
 * explicitly.
 */
inline fun item(material: Material, block: ItemBuilder.() -> Unit = {}): ItemStack =
    ItemBuilder(material).apply(block).build()

class ItemBuilder(private val material: Material) {
    var amount: Int = 1
    var glowing: Boolean = false
    private var name: Component? = null
    private val lore = mutableListOf<Component>()

    fun name(name: Component) {
        this.name = name
    }

    fun lore(vararg lines: Component) {
        lines.mapTo(lore) { line ->
            if (line.decoration(TextDecoration.ITALIC) == TextDecoration.State.NOT_SET) {
                line.decoration(TextDecoration.ITALIC, false)
            } else {
                line
            }
        }
    }

    fun build(): ItemStack {
        var stack = ItemStack.of(material, amount)
        name?.let { stack = stack.with(DataComponents.ITEM_NAME, it) }
        if (lore.isNotEmpty()) stack = stack.withLore(lore.toList())
        if (glowing) stack = stack.withGlowing(true)
        return stack
    }
}
