package gg.grounds.gui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerSkin
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.player.ResolvableProfile

/**
 * Builds a GUI item. Names use `ITEM_NAME`, which renders non-italic and is not shown as "renamed"
 * in anvils; lore lines get the client's default italic stripped unless a line sets the decoration
 * explicitly.
 */
inline fun item(material: Material, block: ItemBuilder.() -> Unit = {}): ItemStack =
    ItemBuilder(material).apply(block).build()

/** A player head showing [skin] — for lists of players (friends, party, leaderboards). */
fun head(skin: PlayerSkin?, block: ItemBuilder.() -> Unit = {}): ItemStack =
    item(Material.PLAYER_HEAD) {
        this.skin = skin
        block()
    }

/** A player head showing [player]'s current skin (plain head if they have none). */
fun head(player: Player, block: ItemBuilder.() -> Unit = {}): ItemStack = head(player.skin, block)

class ItemBuilder(private val material: Material) {
    var amount: Int = 1
    var glowing: Boolean = false

    /** Skin for PLAYER_HEAD items; ignored by the client on other materials. */
    var skin: PlayerSkin? = null

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
        skin?.let { stack = stack.with(DataComponents.PROFILE, ResolvableProfile(it)) }
        return stack
    }
}
