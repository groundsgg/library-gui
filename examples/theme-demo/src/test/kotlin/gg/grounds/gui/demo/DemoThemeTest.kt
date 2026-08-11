package gg.grounds.gui.demo

import java.nio.file.Path
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlin.io.path.isRegularFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Pins the split the demo's whole tuning loop rests on: two of the three offsets live only in the
 * title string the server sends, and one is baked into the pack. Getting that backwards would
 * either make players re-download on every nudge, or show them stale artwork after a real change.
 */
class DemoThemeTest {
    private val art = Path.of("art")

    @AfterTest
    fun restoreDefaults() {
        DemoTheme.reset()
    }

    /** Rebuilds into a throwaway directory and returns the pack's hash. */
    private fun hash(): String = DemoTheme.rebuild(art, createTempDirectory("demo-pack")).sha1

    @Test
    fun `theme declares the exact shader pack format`() {
        val format = DemoTheme.current().packFormat

        assertEquals(88, format.format)
        assertEquals(88, format.minInclusive)
        assertEquals(88, format.maxInclusive)
    }

    @Test
    fun `rebuild reports hashes and size for the served zip`() {
        val artifact = DemoTheme.rebuild(art, createTempDirectory("demo-pack-artifact"))

        assertTrue(artifact.path.isRegularFile())
        assertEquals(40, artifact.sha1.length)
        assertEquals(64, artifact.sha256.length)
        assertEquals(Files.size(artifact.path), artifact.size)
        assertEquals(artifact.sha1, sha1(Files.readAllBytes(artifact.path)))
    }

    @Test
    fun `rebuilding the same theme produces identical zip bytes and hashes`() {
        val first = DemoTheme.rebuild(art, createTempDirectory("demo-pack-first"))
        val second = DemoTheme.rebuild(art, createTempDirectory("demo-pack-second"))

        assertContentEquals(Files.readAllBytes(first.path), Files.readAllBytes(second.path))
        assertEquals(first.sha1, second.sha1)
        assertEquals(first.sha256, second.sha256)
    }

    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    @Test
    fun `horizontal tuning never reaches the pack`() {
        DemoTheme.reset()
        val before = hash()
        DemoTheme.offsetX = -40
        assertEquals(before, hash(), "offsetX only moves the cursor in the title string")
    }

    @Test
    fun `the artwork advances its full width, so no override is needed`() {
        DemoTheme.reset()
        // The calibration panel carries a 1px edge, so its rightmost column is opaque and the
        // client's trimming leaves the width alone. writePack asserts this exactly; if the artwork
        // ever grew a transparent right edge, rebuilding would fail with the real number.
        DemoTheme.rebuild(art, createTempDirectory("demo-pack-advance"))
    }

    @Test
    fun `vertical tuning rewrites the pack, so clients refetch`() {
        DemoTheme.reset()
        val before = hash()
        DemoTheme.offsetY = DemoTheme.DEFAULT_OFFSET_Y + 3
        assertNotEquals(before, hash(), "offsetY becomes the glyph's ascent, which lives in the font file")
    }

    @Test
    fun `rebuilding repeatedly succeeds, despite the generator refusing a populated directory`() {
        val out = createTempDirectory("demo-pack-reused")
        repeat(3) { DemoTheme.rebuild(art, out) }
    }

    @Test
    fun `the snippet reports what was actually tuned`() {
        DemoTheme.reset()
        DemoTheme.offsetX = -12
        DemoTheme.offsetY = -4
        val snippet = DemoTheme.snippet()
        assertTrue("offsetX = -12" in snippet, snippet)
        assertTrue("offsetY = -4" in snippet, snippet)
    }
}
