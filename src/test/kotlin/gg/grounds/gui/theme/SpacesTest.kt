package gg.grounds.gui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SpacesTest {
    private val advances = Spaces.advances()

    private fun width(glyphs: String): Int =
        glyphs.codePoints().toArray().sumOf { advances.getValue(String(Character.toChars(it))) }

    @Test
    fun `a zero offset produces no glyphs`() {
        assertEquals("", Spaces.of(0))
    }

    @Test
    fun `every offset in range moves the cursor exactly that far`() {
        for (px in -Spaces.MAX_OFFSET..Spaces.MAX_OFFSET) {
            assertEquals(px, width(Spaces.of(px)), "offset $px")
        }
    }

    @Test
    fun `offsets are written with as few glyphs as a binary decomposition allows`() {
        assertEquals(1, Spaces.of(-8).length)
        assertEquals(2, Spaces.of(-9).length)
        assertEquals(11, Spaces.of(Spaces.MAX_OFFSET).length)
    }

    @Test
    fun `offsets beyond the ladder fail instead of silently truncating`() {
        assertFailsWith<IllegalArgumentException> { Spaces.of(Spaces.MAX_OFFSET + 1) }
        assertFailsWith<IllegalArgumentException> { Spaces.of(-Spaces.MAX_OFFSET - 1) }
    }

    @Test
    fun `the ladder sits inside the private use area and leaves room after it`() {
        val used = advances.keys.map { it.codePointAt(0) }
        assertTrue(used.all { it in Spaces.PUA_START..Spaces.PUA_END })
        assertEquals(used.max() + 1, Spaces.firstFreeCodepoint)
    }
}
