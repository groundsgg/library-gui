package gg.grounds.gui.demo

import gg.grounds.gui.art.readSprite
import gg.grounds.gui.demo.art.paintMenu
import java.awt.image.BufferedImage
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Kotlin painter against the Python generator, for the panel already in the repository.
 *
 * This is what makes porting nine hundred lines of drawing code a mechanical job rather than a
 * gamble: every producer is moved, then held against the PNG its predecessor wrote. Anything that
 * differs by a pixel differs for a reason, and the reason is found now rather than in a screenshot.
 *
 * Skipped where the raw dumps are absent, since the repository deliberately does not carry them.
 */
class MenuArtParityTest {
    private val dumps = ART.resolve("vanilla")

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
    fun `the painted menu is the generated menu, pixel for pixel`() {
        if (!dumps.resolve("button.rgba").exists()) return

        val produced = Files.createTempDirectory("menu-art")
        paintMenu(dumps, produced)

        listOf(
            "panels/menu.png",
            "frame/menu_face_small.png",
            "frame/menu_face_wide.png",
            "frame/menu_label_shop.png",
            "frame/menu_label_play.png",
            "frame/menu_label_profile.png",
        )
            .forEach { name ->
                val expected = ART.resolve(name)
                assertTrue(expected.exists(), "missing $name; run art/generate.py")
                assertEquals(
                    0,
                    differences(readSprite(expected), readSprite(produced.resolve(name))),
                    name,
                )
            }
    }
}
