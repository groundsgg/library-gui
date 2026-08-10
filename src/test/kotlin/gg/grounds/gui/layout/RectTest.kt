package gg.grounds.gui.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RectTest {
    @Test
    fun `slot geometry matches what a container actually uses`() {
        // A well at (7 + 18c, 17 + 18r) with the item area one pixel inside it. Every layout in
        // this library is pinned to these two numbers, so they are worth stating once.
        assertEquals(7, slotWellX(0))
        assertEquals(151, slotWellX(8))
        assertEquals(8, slotItemX(0))
        assertEquals(18 + 18 * 5, slotItemY(5))
    }

    @Test
    fun `a block of wells is the rectangle a multi-slot control has to align with`() {
        val row = Rect.wells(1, 0, 7, 0)
        assertEquals(slotWellX(1), row.x)
        assertEquals(7 * SLOT_PITCH, row.width)
        assertEquals(SLOT_PITCH, row.height)
        // Nine columns span the full content width, which is why an eight-column grid and a
        // full-width card can share their edges.
        assertEquals(slotWellX(8) + SLOT_PITCH, Rect.wells(0, 0, 8, 0).right)
    }

    @Test
    fun `item areas are inset from the wells and sized to what the client draws`() {
        val one = Rect.items(2, 1, 2, 1)
        assertEquals(ITEM_AREA, one.width)
        assertEquals(ITEM_AREA, one.height)
        assertEquals(Rect.slot(1 * 9 + 2), one, "a slot index and its column/row must agree")
        // A run of three is two pitches plus the last item's own width, not three pitches.
        assertEquals(2 * SLOT_PITCH + ITEM_AREA, Rect.items(0, 0, 2, 0).width)
    }

    @Test
    fun `inset and offset compose the way a card's interior is described`() {
        val card = Rect(7, 76, 162, 50)
        assertEquals(Rect(10, 79, 156, 44), card.inset(3))
        assertEquals(Rect(7, 80, 162, 50), card.offset(0, 4))
        assertEquals(169, card.right)
        assertEquals(126, card.bottom)
        assertTrue(card.inset(3) in card)
        assertFalse(card.offset(1, 0) in card)
    }

    @Test
    fun `rows step by a pitch, so a column of lines is described once`() {
        val column = Rect(54, 79, 109, 44)
        assertEquals(Rect(54, 79, 109, 7), column.row(0, 7, pitch = 12))
        assertEquals(Rect(54, 103, 109, 7), column.row(2, 7, pitch = 12))
        // Without a pitch the rows sit flush, which is what a stack of bars wants.
        assertEquals(Rect(54, 93, 109, 7), column.row(2, 7))
    }

    @Test
    fun `centring a thing inside a rect is the one measurement worth not retyping`() {
        assertEquals(7 + (162 - 80) / 2, Rect(7, 76, 162, 50).centredX(80))
    }

    @Test
    fun `a negative size is refused rather than drawn`() {
        assertFailsWith<IllegalArgumentException> { Rect(0, 0, -1, 4) }
        assertFailsWith<IllegalArgumentException> { Rect(0, 0, 10, 4).inset(6) }
    }
}
