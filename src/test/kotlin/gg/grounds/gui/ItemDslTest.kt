package gg.grounds.gui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.minestom.server.MinecraftServer
import net.minestom.server.component.DataComponents
import net.minestom.server.item.Material
import org.junit.jupiter.api.BeforeAll

class ItemDslTest {
    companion object {
        private var registriesReady = false

        /**
         * Building an ItemStack reads the material registry, which does not exist until Minestom's
         * process has been created — without this every assertion here dies on "Should have been
         * bound". `init` populates the registries and binds no port; only `start` would.
         */
        @JvmStatic
        @BeforeAll
        fun bootRegistries() {
            if (!registriesReady) {
                MinecraftServer.init()
                registriesReady = true
            }
        }
    }

    @Test
    fun `a plain item keeps its material's own model and a visible tooltip`() {
        val stack = item(Material.PAPER)
        // Items inherit defaults from their material prototype, so "unset" is never null here: the
        // model defaults to the material's own id. That default is exactly why a client without the
        // pack still renders a themed button — it falls back to the material's model.
        assertEquals("minecraft:paper", stack.get(DataComponents.ITEM_MODEL))
        assertFalse(stack.get(DataComponents.TOOLTIP_DISPLAY)!!.hideTooltip())
        assertNull(stack.get(DataComponents.TOOLTIP_STYLE))
    }

    @Test
    fun `itemModel and tooltipStyle land on the stack verbatim`() {
        val stack =
            item(Material.PAPER) {
                itemModel = "grounds:coin"
                tooltipStyle = "grounds:gold"
            }
        assertEquals("grounds:coin", stack.get(DataComponents.ITEM_MODEL))
        assertEquals("grounds:gold", stack.get(DataComponents.TOOLTIP_STYLE))
    }

    @Test
    fun `hideTooltip suppresses the tooltip entirely`() {
        val stack = item(Material.IRON_AXE) { hideTooltip = true }
        assertTrue(stack.get(DataComponents.TOOLTIP_DISPLAY)!!.hideTooltip())
    }

    @Test
    fun `a hidden tooltip and a styled one are independent`() {
        // Isolating a hover effect needs both at once: one item styled, its neighbours silent.
        val silent = item(Material.IRON_AXE) { hideTooltip = true }
        val styled = item(Material.DIAMOND_PICKAXE) { tooltipStyle = "grounds:steel" }

        assertTrue(silent.get(DataComponents.TOOLTIP_DISPLAY)!!.hideTooltip())
        assertNull(silent.get(DataComponents.TOOLTIP_STYLE))
        assertFalse(styled.get(DataComponents.TOOLTIP_DISPLAY)!!.hideTooltip())
        assertEquals("grounds:steel", styled.get(DataComponents.TOOLTIP_STYLE))
    }

    @Test
    fun `leaving hideTooltip alone keeps the tooltip visible`() {
        val stack = item(Material.PAPER) { hideTooltip = false }
        assertFalse(stack.get(DataComponents.TOOLTIP_DISPLAY)!!.hideTooltip())
    }
}
