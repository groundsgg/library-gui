package gg.grounds.gui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

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
        val panel = Panel("shop", "shop.png", 176, 166, offsetY = -6)
        assertEquals(TITLE_ASCENT + 6, panel.ascent)
        assertEquals(TITLE_ASCENT, Panel("shop", "shop.png", 176, 166).ascent)
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
}
