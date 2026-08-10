package gg.grounds.gui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TextMarkersTest {
    private val format = PackFormat(88)

    /** 'A' and 'B' draw; ' ' has an advance but no frame, the way a blank glyph works. */
    private val subject =
        theme("grounds", format) {
            glyphs("ascii", "glyph_", mapOf(32 to 4, 65 to 6, 66 to 5))
            frame("glyph_65", "frame/glyph_65.png")
            frame("glyph_66", "frame/glyph_66.png")
        }

    /** One child per drawn character: `text` appends a marker, and a marker is one component. */
    private fun drawn(component: net.kyori.adventure.text.Component): Int =
        component.children().size

    @Test
    fun `width is the sum of the advances, blanks included`() {
        assertEquals(15, subject.textWidth("ascii", "A B"))
        assertEquals(0, subject.textWidth("ascii", ""))
    }

    @Test
    fun `a blank advances the pen without drawing`() {
        // Two drawn characters, so two markers — the space contributes width and nothing else. If a
        // missing sprite were mistaken for a blank this count would still look right, which is why
        // the position check below matters more than the count.
        assertEquals(2, drawn(subject.text("ascii", 0, 0, "A B", imageHeight = containerHeight(3))))
        assertEquals(0, drawn(subject.text("ascii", 0, 0, "   ", imageHeight = containerHeight(3))))
    }

    @Test
    fun `the pen advances per character rather than by a fixed pitch`() {
        // 'A' is 6 wide and 'B' is 5, so the third character starts at 11, not at 12. Proportional
        // spacing is the whole reason the advances travel with the sprites.
        val one = subject.text("ascii", 0, 0, "AAB", imageHeight = containerHeight(3))
        val other = subject.text("ascii", 0, 0, "ABA", imageHeight = containerHeight(3))
        assertTrue(one != other, "different strings must place their glyphs differently")
        assertEquals(17, subject.textWidth("ascii", "AAB"))
    }

    @Test
    fun `an undeclared set and an unmapped character both fail loudly`() {
        val height = containerHeight(3)
        assertFailsWith<IllegalArgumentException> {
            subject.text("cyrillic", 0, 0, "A", imageHeight = height)
        }
        // Rendering short and silently is the failure this replaces.
        val boom =
            assertFailsWith<IllegalArgumentException> {
                subject.text("ascii", 0, 0, "Aä", imageHeight = height)
            }
        assertTrue("U+E4" in boom.message.orEmpty(), boom.message.orEmpty())
    }

    @Test
    fun `a glyph set needs an id and at least one advance`() {
        assertFailsWith<IllegalArgumentException> { GlyphSet("ascii", "glyph_", emptyMap()) }
        assertFailsWith<IllegalArgumentException> { GlyphSet("ASCII", "glyph_", mapOf(65 to 6)) }
        assertFailsWith<IllegalArgumentException> {
            theme("grounds", format) {
                glyphs("ascii", "glyph_", mapOf(65 to 6))
                glyphs("ascii", "other_", mapOf(65 to 6))
            }
        }
    }

    @Test
    fun `screen placement is the offset itself, with the same reach as everything else`() {
        val subject =
            theme("grounds", format) {
                frame("plate", "frame/plate.png")
                glyphs("ascii", "glyph_", mapOf(65 to 6))
                frame("glyph_65", "frame/glyph_65.png")
            }
        // A dialog has no window to measure against, so these coordinates are already the offset
        // from the screen's centre — the one point the shader computes for itself.
        val payload =
            subject.screenMarker("plate", -112, -70).children().first().style().color()!!.value()
        assertEquals(-112 + 128, (payload shr 16) and 0xFF)
        assertEquals(-70 + 128, (payload shr 8) and 0xFF)
        // And the byte still binds: a dialog is wider than it, which is the real constraint here.
        assertFailsWith<IllegalArgumentException> { subject.screenMarker("plate", 200, 0) }
        assertFailsWith<IllegalArgumentException> {
            subject.screenText("ascii", 0, 0, "A".repeat(40))
        }
    }
}
