package gg.grounds.gui.demo

import gg.grounds.gui.layout.ITEM_AREA
import gg.grounds.gui.art.contour
import gg.grounds.gui.art.readSprite
import gg.grounds.gui.art.slotPatches
import java.awt.image.BufferedImage
import kotlin.io.path.exists
import kotlin.io.path.inputStream
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
    fun `every slot's patch is the panel's own pixels, whichever file it was folded onto`() {
        val panel = ART.resolve("panels/market.png")
        assertTrue(panel.exists(), "run :examples:theme-demo:paintArt first")

        // Identical patches share a file, so the index says which one a slot uses. Following it is
        // the point of the check: a wrong entry would put another slot's pixels on this one, and
        // that is exactly the failure folding them introduces.
        val index = java.util.Properties()
        ART.resolve("frame/market_patches.properties").inputStream().use(index::load)

        val produced = slotPatches(readSprite(panel), rows = 6)
        assertEquals(54, produced.size)
        // One entry per slot, but fewer files than entries — the saving is in the values.
        val files = index.values.map(Any::toString).toSet()
        assertTrue(
            files.size < produced.size,
            "folding has to save something, or it is only indirection: ${files.size} of ${produced.size}",
        )
        produced.forEach { (slot, patch) ->
            val file = index.getProperty("mk_cover.$slot")
            assertTrue(file != null, "no sprite indexed for slot $slot")
            val expected = ART.resolve("frame/$file.png")
            assertTrue(expected.exists(), "slot $slot points at a missing $file")
            assertEquals(0, differences(readSprite(expected), patch), "slot $slot")
            assertEquals(ITEM_AREA, patch.width)
        }
    }
}
