package gg.grounds.gui.art

import gg.grounds.gui.layout.ITEM_AREA
import gg.grounds.gui.layout.slotItemX
import gg.grounds.gui.layout.slotItemY
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SpritesTest {
    private fun solid(width: Int, height: Int, argb: Int = -1): BufferedImage =
        BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { image ->
            for (row in 0 until height) {
                for (column in 0 until width) {
                    image.setRGB(column, row, argb)
                }
            }
        }

    /** A sprite whose every pixel states where it came from, so a remap is readable. */
    private fun numbered(size: Int): BufferedImage =
        BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB).also { image ->
            for (row in 0 until size) {
                for (column in 0 until size) {
                    image.setRGB(column, row, (0xFF shl 24) or (row shl 8) or column)
                }
            }
        }

    @Test
    fun `nine slice keeps its corners and tiles the middle`() {
        val source = numbered(9)
        val out = source.nineSlice(width = 40, height = 9, border = 3)
        assertEquals(40, out.width)
        // Corners come through untouched, which is the whole reason not to scale.
        assertEquals(source.getRGB(0, 0), out.getRGB(0, 0))
        assertEquals(source.getRGB(8, 0), out.getRGB(39, 0))
        assertEquals(source.getRGB(8, 8), out.getRGB(39, 8))
        // And the middle wraps the source's own middle band rather than stretching one column.
        val band = (3..5).map { source.getRGB(it, 4) }.toSet()
        (3 until 37).forEach { column ->
            assertTrue(out.getRGB(column, 4) in band, "column $column")
        }
    }

    @Test
    fun `nine slice refuses a sprite with no middle band`() {
        assertFailsWith<IllegalArgumentException> { solid(6, 6).nineSlice(20, 6, border = 3) }
        assertFailsWith<IllegalArgumentException> { solid(9, 9).nineSlice(20, 9, border = 0) }
    }

    @Test
    fun `a contour traces the shape and never covers it`() {
        // A plus, so the outline has to follow something a rectangle would not.
        val plus = BufferedImage(5, 5, BufferedImage.TYPE_INT_ARGB)
        (0 until 5).forEach { i ->
            plus.setRGB(2, i, -1)
            plus.setRGB(i, 2, -1)
        }
        val ring = plus.contour(listOf(Ring(1, 255)))
        assertEquals(7, ring.width)
        assertEquals(7, ring.height)
        // Nothing lands on the artwork itself: the outline goes under the icon precisely so it can
        // never eat into what it is outlining.
        (0 until 5).forEach { row ->
            (0 until 5).forEach { column ->
                if (plus.getRGB(column, row) ushr 24 != 0) {
                    assertEquals(0, ring.getRGB(column + 1, row + 1) ushr 24, "($column, $row)")
                }
            }
        }
        // A pixel touching the shape is lit, one two steps away is not.
        assertTrue(ring.getRGB(3, 0) ushr 24 != 0, "directly above the stem")
        assertEquals(0, ring.getRGB(0, 0) ushr 24, "the far corner")
    }

    @Test
    fun `contour rings are painted widest first, so the bright core survives`() {
        val dot = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).also { it.setRGB(0, 0, -1) }
        val ring = dot.contour(listOf(Ring(2, 110), Ring(1, 255)))
        // The neighbouring pixel belongs to the inner ring even though the outer one also reaches
        // it. Declaring them in the other order must not change that.
        assertEquals(255, ring.getRGB(3, 2) ushr 24)
        assertEquals(110, ring.getRGB(4, 2) ushr 24)
        assertEquals(0xFFFFFF, ring.getRGB(3, 2) and 0xFFFFFF, "white, so a palette can colour it")
    }

    @Test
    fun `slot patches carry each slot's own pixels`() {
        val panel = numbered(176)
        val patches = slotPatches(panel, rows = 3)
        assertEquals(27, patches.size)
        val slot = 2 * 9 + 4
        assertEquals(
            panel.getRGB(slotItemX(4), slotItemY(2)),
            patches.getValue(slot).getRGB(0, 0),
            "patch for slot $slot must be cut where that slot is",
        )
        assertEquals(ITEM_AREA, patches.getValue(slot).width)
    }

    @Test
    fun `an unlifted patch is the panel exactly, which is what makes hovering do nothing`() {
        val panel = numbered(176)
        val plain = slotPatches(panel, rows = 1).getValue(0)
        val lifted = slotPatches(panel, rows = 1, lift = 70).getValue(0)
        assertEquals(panel.getRGB(slotItemX(0), slotItemY(0)), plain.getRGB(0, 0))
        assertTrue(lifted.getRGB(0, 0) != plain.getRGB(0, 0), "a lift has to change something")
        assertEquals(
            plain.getRGB(0, 0) ushr 24,
            lifted.getRGB(0, 0) ushr 24,
            "alpha survives a lift",
        )
    }

    @Test
    fun `cut glyphs are trimmed, padded and measured`() {
        // A 16x16 sheet of 1x1 cells is the smallest thing with the right shape; cell 65 is 'A'.
        val sheet = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val order = (0 until 256).joinToString("") { it.toChar().toString() }
        sheet.setRGB(65 % 16, 65 / 16, -1)
        val cut = cutGlyphs(sheet, order, codepoints = listOf(65, 32))

        // Drawn one pixel wide, padded to the four a marker's data row needs, ink left in place.
        assertEquals(4, cut.sprites.getValue(65).width)
        assertTrue(cut.sprites.getValue(65).getRGB(0, 0) ushr 24 != 0)
        assertEquals(2, cut.advances.getValue(65))
        // A blank advances and draws nothing, or every space in every string closes up.
        assertTrue(32 !in cut.sprites)
        assertEquals(4, cut.advances.getValue(32))
    }

    @Test
    fun `scaling is nearest neighbour, so pixel art stays pixel art`() {
        val source = numbered(2)
        val out = source.scaled(3)
        assertEquals(6, out.width)
        assertEquals(source.getRGB(0, 0), out.getRGB(2, 2))
        assertEquals(source.getRGB(1, 1), out.getRGB(3, 3))
        assertFailsWith<IllegalArgumentException> { source.scaled(0) }
    }
}
