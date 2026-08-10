package gg.grounds.gui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ThemeTest {
    private val format = PackFormat(88)

    @Test
    fun `glyphs are allocated by sorted id, not declaration order`() {
        val declared =
            theme("grounds", format) {
                panel("shop", "shop.png", 176, 166)
                panel("armory", "armory.png", 176, 166)
            }
        val reordered =
            theme("grounds", format) {
                panel("armory", "armory.png", 176, 166)
                panel("shop", "shop.png", 176, 166)
            }
        assertEquals(declared.glyph("shop"), reordered.glyph("shop"))
        assertEquals(declared.glyph("armory"), reordered.glyph("armory"))
        assertNotEquals(declared.glyph("shop"), declared.glyph("armory"))
        assertEquals(Spaces.firstFreeCodepoint, declared.glyph("armory").codePointAt(0))
    }

    @Test
    fun `offsetY reads as pixels below the title and drives the ascent`() {
        // The default lifts the artwork to the window's top edge, which is ascent 13 — the value
        // every other implementation of this technique also lands on.
        assertEquals(TITLE_BASELINE_Y, Panel("shop", "shop.png", 176, 166).ascent)
        // Level with the title text instead of the window: six pixels lower, six less ascent.
        assertEquals(TITLE_ASCENT, Panel("shop", "shop.png", 176, 166, offsetY = 0).ascent)
    }

    @Test
    fun `advance defaults to the drawn width plus the vanilla gap and can be overridden`() {
        assertEquals(177, Panel("shop", "shop.png", 176, 166).effectiveAdvance)
        assertEquals(353, Panel("shop", "shop.png", 176, 166, scale = 2).effectiveAdvance)
        assertEquals(120, Panel("shop", "shop.png", 176, 166, advance = 120).effectiveAdvance)
    }

    @Test
    fun `an ascent taller than the artwork fails, because the client rejects that pack`() {
        assertFailsWith<IllegalArgumentException> {
            Panel("tall", "tall.png", 16, 8, offsetY = -100)
        }
    }

    @Test
    fun `duplicate ids fail`() {
        assertFailsWith<IllegalArgumentException> {
            theme("grounds", format) {
                panel("shop", "a.png", 16, 16)
                panel("shop", "b.png", 16, 16)
            }
        }
    }

    @Test
    fun `ids and texture paths are validated`() {
        assertFailsWith<IllegalArgumentException> { Panel("Shop", "shop.png", 16, 16) }
        assertFailsWith<IllegalArgumentException> { Panel("shop", "../shop.png", 16, 16) }
        assertFailsWith<IllegalArgumentException> { Panel("shop", "shop.jpg", 16, 16) }
        assertFailsWith<IllegalArgumentException> { theme("Grounds", format) }
    }

    @Test
    fun `component ids resolve only for declared entries`() {
        val subject =
            theme("grounds", format) {
                icon("sword", "sword.png")
                tooltip("gold", "gold_bg.png", "gold_frame.png")
            }
        assertEquals("grounds:sword", subject.itemModel("sword"))
        assertEquals("grounds:gold", subject.tooltipStyle("gold"))
        assertFailsWith<IllegalArgumentException> { subject.itemModel("shield") }
        assertFailsWith<IllegalArgumentException> { subject.tooltipStyle("silver") }
        assertFailsWith<IllegalArgumentException> { subject.panel("shop") }
    }

    @Test
    fun `a pack format outside its own supported range fails`() {
        assertFailsWith<IllegalArgumentException> {
            PackFormat(88, minInclusive = 90, maxInclusive = 92)
        }
    }

    @Test
    fun `a frame never shares a codepoint with the space that cancels it`() {
        // The two blocks are numbered from the same index, so a frame is only distinguishable from
        // a cancelling space by the distance between the bases. Where they meet, both providers
        // claim the codepoint, the font's later entry wins, and the marker draws a space instead of
        // its artwork — invisibly, which is the whole reason this is asserted rather than trusted.
        assertTrue(FRAME_CAPACITY > 0, "the frame block must not run into the space block")
        assertEquals(FRAME_SPACE_BASE, FRAME_GLYPH_BASE + FRAME_CAPACITY)
        assertTrue(
            FRAME_SPACE_BASE + FRAME_CAPACITY - 1 <= Spaces.PUA_END,
            "both blocks have to stay inside the private use area",
        )
    }

    @Test
    fun `more frames than the block can number is refused`() {
        val ids = (0..FRAME_CAPACITY).map { "frame_$it" }
        assertFailsWith<IllegalArgumentException> {
            theme("grounds", format) { ids.forEach { id -> frame(id, "$id.png") } }
        }
    }

    @Test
    fun `two frames under one id are refused`() {
        assertFailsWith<IllegalArgumentException> {
            theme("grounds", format) {
                frame("outline", "a.png")
                frame("outline", "b.png")
            }
        }
    }
}
