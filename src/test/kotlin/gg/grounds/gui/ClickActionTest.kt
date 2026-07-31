package gg.grounds.gui

import kotlin.test.Test
import kotlin.test.assertEquals
import net.minestom.server.inventory.click.Click

class ClickActionTest {
    @Test
    fun `single clicks map to their action`() {
        assertEquals(ClickAction.LEFT, Click.Left(3).action())
        assertEquals(ClickAction.RIGHT, Click.Right(3).action())
        assertEquals(ClickAction.SHIFT, Click.LeftShift(3).action())
        assertEquals(ClickAction.SHIFT, Click.RightShift(3).action())
        assertEquals(ClickAction.DOUBLE, Click.Double(3).action())
        assertEquals(ClickAction.MIDDLE, Click.Middle(3).action())
    }

    @Test
    fun `item-moving clicks are never actionable`() {
        // These can move items out of GUI slots without any cursor involvement
        // (hotbar/offhand swaps) or span both inventories (drags) — they must
        // stay blocked instead of activating buttons.
        assertEquals(ClickAction.OTHER, Click.HotbarSwap(0, 3).action())
        assertEquals(ClickAction.OTHER, Click.OffhandSwap(3).action())
        assertEquals(ClickAction.OTHER, Click.LeftDrag(listOf(1, 2)).action())
        assertEquals(ClickAction.OTHER, Click.RightDrag(listOf(1)).action())
        assertEquals(ClickAction.OTHER, Click.MiddleDrag(listOf(1)).action())
        assertEquals(ClickAction.OTHER, Click.DropSlot(3, false).action())
        assertEquals(ClickAction.OTHER, Click.LeftDropCursor().action())
    }
}
