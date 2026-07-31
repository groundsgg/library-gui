package gg.grounds.gui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LayoutTest {
    @Test
    fun `chars map to slot indices row by row`() {
        val slots = layoutSlots(listOf("#########", "#.......#"), size = 27)
        assertEquals((0..8).toList() + listOf(9, 17), slots['#'])
        assertEquals((10..16).toList(), slots['.'])
    }

    @Test
    fun `spaces leave slots untouched`() {
        val slots = layoutSlots(listOf("    X    "), size = 9)
        assertEquals(listOf(4), slots['X'])
        assertEquals(setOf('X'), slots.keys)
    }

    @Test
    fun `fewer rows than the inventory are allowed`() {
        val slots = layoutSlots(listOf("XXXXXXXXX"), size = 54)
        assertEquals((0..8).toList(), slots['X'])
    }

    @Test
    fun `too many rows fail`() {
        assertFailsWith<IllegalArgumentException> {
            layoutSlots(listOf("#########", "#########"), size = 9)
        }
    }

    @Test
    fun `wrong row length fails`() {
        assertFailsWith<IllegalArgumentException> { layoutSlots(listOf("########"), size = 9) }
    }

    @Test
    fun `non chest sizes fail`() {
        assertFailsWith<IllegalArgumentException> { layoutSlots(listOf("#####"), size = 5) }
    }
}
