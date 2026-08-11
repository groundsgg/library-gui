package gg.grounds.gui

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import net.minestom.server.MinecraftServer
import net.minestom.server.component.DataComponents
import net.minestom.server.item.Material

class ComponentDslTest {
    @BeforeTest
    fun registries() {
        runCatching { MinecraftServer.init() }
    }

    @Test
    fun `an arbitrary component reaches the stack`() {
        // The named properties cover what a themed GUI reaches for constantly; everything else in
        // the game's component set used to mean dropping out of the DSL to set one. MAP_ID is the
        // case that forced this: without it a filled map is a blank sheet.
        val stack = item(Material.FILLED_MAP) { component(DataComponents.MAP_ID, 42) }
        assertEquals(42, stack.get(DataComponents.MAP_ID))
    }

    @Test
    fun `it composes with the named properties rather than replacing them`() {
        val stack =
            item(Material.FILLED_MAP) {
                component(DataComponents.MAP_ID, 7)
                itemModel = "grounds:blank"
                amount = 3
            }
        assertEquals(7, stack.get(DataComponents.MAP_ID))
        assertEquals("grounds:blank", stack.get(DataComponents.ITEM_MODEL).toString())
        assertEquals(3, stack.amount())
    }

    @Test
    fun `the last value for a component wins, the way a builder should read`() {
        val stack =
            item(Material.FILLED_MAP) {
                component(DataComponents.MAP_ID, 1)
                component(DataComponents.MAP_ID, 2)
            }
        assertEquals(2, stack.get(DataComponents.MAP_ID))
    }
}
