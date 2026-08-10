package gg.grounds.gui.demo

import gg.grounds.gui.layout.ITEM_AREA
import gg.grounds.gui.art.contour
import gg.grounds.gui.art.readSprite
import gg.grounds.gui.art.slotPatches
import java.awt.image.BufferedImage
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The library's art toolkit against the generator that already produced the demo's PNGs.
 *
 * Moving artwork generation out of the example and into the library is only safe if the two agree
 * pixel for pixel — otherwise the port is a redesign wearing a refactor's clothes, and every screen
 * shifts by a pixel somewhere nobody looks.
 *
 * Only the operations whose sources are checked in are compared. The nine-slice and the glyph cut
 * read raw dumps of client assets, which this repository deliberately does not carry.
 */
class ArtParityTest {
    private fun differences(expected: BufferedImage, actual: BufferedImage): Int {
        if (expected.width != actual.width || expected.height != actual.height) return -1
        var count = 0
        for (row in 0 until expected.height) {
            for (column in 0 until expected.width) {
                if (expected.getRGB(column, row) != actual.getRGB(column, row)) count++
            }
        }
        return count
    }

    @Test
    fun `the toolkit's contour is the generator's contour`() {
        val icon = ART.resolve("frame/ov_icon_search.png")
        val expected = ART.resolve("frame/ov_outline_search.png")
        assertTrue(icon.exists() && expected.exists(), "run art/generate.py first")

        val produced = readSprite(icon).contour()
        assertEquals(0, differences(readSprite(expected), produced), "contour must match pixel for pixel")
        assertEquals(20, produced.width, "a 16x16 icon inset by two rings")
    }

    @Test
    fun `the toolkit's slot patches are the generator's slot patches`() {
        val panel = ART.resolve("panels/market.png")
        assertTrue(panel.exists(), "run art/generate.py first")

        val produced = slotPatches(readSprite(panel), rows = 6)
        assertEquals(54, produced.size)
        // Every slot, not a sample: the interesting ones are the tiles sitting on the heading text
        // and on the card, and picking a few would be picking the easy ones.
        produced.forEach { (slot, patch) ->
            val expected = ART.resolve("frame/mk_cover_$slot.png")
            assertTrue(expected.exists(), "missing generated patch for slot $slot")
            assertEquals(0, differences(readSprite(expected), patch), "slot $slot")
            assertEquals(ITEM_AREA, patch.width)
        }
    }
}
