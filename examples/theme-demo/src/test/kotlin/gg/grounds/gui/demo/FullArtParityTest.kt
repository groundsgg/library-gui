package gg.grounds.gui.demo

import gg.grounds.gui.art.readSprite
import gg.grounds.gui.demo.art.paintAll
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The whole painted tree against the artwork checked into the repository.
 *
 * It earned its keep during the port, holding the Kotlin painter against the Python generator file
 * by file — which is how six orphaned PNGs nothing had written for weeks were found. What it says
 * now is the invariant that replaced it: the artwork in the repository is exactly what the painter
 * produces, so a PNG cannot be edited by hand and a producer cannot drift from what ships.
 *
 * Pixels, not bytes. Deflate is not byte-canonical across implementations, and a dozen files
 * compress a few bytes differently without a single pixel moving.
 *
 * Needs the raw dumps, which this repository deliberately does not carry, so it stands down where
 * they are absent rather than passing on nothing.
 */
class FullArtParityTest {
    private val dumps = ART.resolve("vanilla")

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    private fun tree(root: Path): Map<String, Path> =
        root
            .walk()
            .filter { it.isRegularFile() }
            .associateBy { it.relativeTo(root).toString().replace('\\', '/') }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @Test
    fun `painting everything reproduces the generated tree exactly`() {
        if (!dumps.resolve("ascii.rgba").exists()) return

        val produced = Files.createTempDirectory("art-all")
        paintAll(dumps, produced)

        val expected = tree(ART).filterKeys { it.endsWith(".png") || it.endsWith(".properties") }
        val actual = tree(produced)

        val missing = expected.keys - actual.keys
        assertTrue(missing.isEmpty(), "the painter does not produce: ${missing.sorted()}")
        val extra = actual.keys - expected.keys
        assertTrue(extra.isEmpty(), "the painter produces files the generator never did: ${extra.sorted()}")

        val wrong =
            expected.keys.sorted().filter { name ->
                if (name.endsWith(".properties")) {
                    expected.getValue(name).readText() != actual.getValue(name).readText()
                } else {
                    val a = readSprite(expected.getValue(name))
                    val b = readSprite(actual.getValue(name))
                    if (a.width != b.width || a.height != b.height) {
                        true
                    } else {
                        (0 until a.height).any { row ->
                            (0 until a.width).any { column ->
                                a.getRGB(column, row) != b.getRGB(column, row)
                            }
                        }
                    }
                }
            }
        assertEquals(emptyList(), wrong, "these differ from what the generator wrote")
    }
}
