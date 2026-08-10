package gg.grounds.gui.art

import gg.grounds.gui.layout.Rect
import gg.grounds.gui.layout.slotItemX
import gg.grounds.gui.layout.slotItemY
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HitSlotsTest {
    private fun mask(block: BufferedImage.() -> Unit): BufferedImage =
        BufferedImage(176, 176, BufferedImage.TYPE_INT_ARGB).apply(block)

    private fun BufferedImage.fill(rect: Rect) {
        for (row in 0 until rect.height) {
            for (column in 0 until rect.width) {
                setRGB(rect.x + column, rect.y + row, -1)
            }
        }
    }

    @Test
    fun `a filled slot counts and an untouched one does not`() {
        val subject = mask { fill(Rect.slot(4)) }
        assertEquals(listOf(4), hitSlots(subject, rows = 3))
    }

    @Test
    fun `coverage decides the edge, and half means mostly over it`() {
        // Exactly a quarter of slot 0's item area.
        val subject = mask { fill(Rect(slotItemX(0), slotItemY(0), 8, 8)) }
        assertEquals(emptyList(), hitSlots(subject, rows = 1))
        assertEquals(listOf(0), hitSlots(subject, rows = 1, coverage = 0.25))
    }

    @Test
    fun `a shape's hit area is smaller than its block`() {
        // A triangle over the first three columns: apex at the top middle, base along the bottom.
        val subject = mask {
            val left = slotItemX(0)
            val right = slotItemX(2) + 16
            val top = slotItemY(0)
            val bottom = slotItemY(2) + 16
            for (y in top until bottom) {
                val half = (right - left) / 2.0 * (y - top) / (bottom - top)
                val apex = (left + right) / 2.0
                for (x in left until right) {
                    if (Math.abs(x - apex) <= half) setRGB(x, y, -1)
                }
            }
        }
        val hit = hitSlots(subject, rows = 3)
        assertTrue(hit.isNotEmpty())
        // The apex reaches the middle column only, and the base spreads across all three.
        assertTrue(1 in hit, "the apex slot")
        assertTrue(0 !in hit && 2 !in hit, "the top corners the triangle never reaches")
        assertTrue(listOf(18, 19, 20).all { it in hit }, "the base row")
        assertTrue(hit.size < 9, "a hit area smaller than its block is the whole point")
    }

    @Test
    fun `a mask smaller than the container simply has no slots out there`() {
        val small = BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB).apply { fill(Rect.slot(0)) }
        assertEquals(listOf(0), hitSlots(small, rows = 3))
    }

    @Test
    fun `a nonsensical coverage is refused`() {
        assertFailsWith<IllegalArgumentException> { hitSlots(mask {}, rows = 1, coverage = 0.0) }
        assertFailsWith<IllegalArgumentException> { hitSlots(mask {}, rows = 1, coverage = 1.5) }
        assertFailsWith<IllegalArgumentException> { hitSlots(mask {}, rows = 7) }
    }
}
