package gg.grounds.gui.demo

import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
    private fun hash(): String = DemoTheme.rebuild(art, createTempDirectory("demo-pack")).second

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
