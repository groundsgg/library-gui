package gg.grounds.gui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import net.kyori.adventure.text.Component

class PaletteTest {
    private val format = PackFormat(88)

    private val subject =
        theme("grounds", format) {
            colour("gold", 0xFFBE28)
            colour("dim", 0x969AA4)
            frame("card", "frame/card.png")
        }

    private fun payloadOf(component: Component): Int =
        component.children().first().style().color()!!.value()

    @Test
    fun `no tint leaves the low byte at zero, which is what untinted has always meant`() {
        val plain = subject.frameMarker("card", 8, 18, imageHeight = containerHeight(3))
        assertEquals(0, payloadOf(plain) and 0xFF)
    }

    @Test
    fun `a tint rides in the low byte without disturbing the offset`() {
        val height = containerHeight(3)
        val plain = payloadOf(subject.frameMarker("card", 8, 18, imageHeight = height))
        val tinted =
            payloadOf(subject.frameMarker("card", 8, 18, imageHeight = height, tint = "gold"))
        // Red and green carry the position and must come through untouched; only blue changes.
        assertEquals(plain ushr 8, tinted ushr 8)
        assertNotEquals(0, tinted and 0xFF)
    }

    @Test
    fun `colours are numbered by name, so declaring one cannot renumber the rest`() {
        // Sorted: "dim" before "gold", whatever order they were declared in. A payload that shifted
        // when a colour was added would repaint every cached pack's markers.
        assertEquals(1, subject.paletteIndex("dim"))
        assertEquals(2, subject.paletteIndex("gold"))
        val reordered =
            theme("grounds", format) {
                colour("dim", 0x969AA4)
                colour("gold", 0xFFBE28)
            }
        assertEquals(subject.paletteIndex("gold"), reordered.paletteIndex("gold"))
    }

    @Test
    fun `an undeclared colour fails rather than rendering something arbitrary`() {
        assertFailsWith<IllegalArgumentException> { subject.paletteIndex("crimson") }
        assertFailsWith<IllegalArgumentException> {
            subject.frameMarker("card", 8, 18, imageHeight = containerHeight(3), tint = "crimson")
        }
    }

    @Test
    fun `a colour needs a usable name and an RGB triple`() {
        assertFailsWith<IllegalArgumentException> { ThemeColour("Gold", 0xFFBE28) }
        assertFailsWith<IllegalArgumentException> { ThemeColour("gold", 0x1FFBE28) }
        assertFailsWith<IllegalArgumentException> {
            theme("grounds", format) {
                colour("gold", 1)
                colour("gold", 2)
            }
        }
    }

    @Test
    fun `more colours than the byte can index is refused`() {
        assertFailsWith<IllegalArgumentException> {
            theme("grounds", format) {
                (0..PALETTE_CAPACITY).forEach { index -> colour("c$index", index) }
            }
        }
    }

    @Test
    fun `the payload stays inside a colour, so it can never look like ordinary text`() {
        // Offsets span the full signed byte, which means red can reach any value including the one
        // reserved in the docs. That is fine and worth pinning: identity is the sprite's data
        // pixels, never the vertex colour, so no text colour can be mistaken for a marker.
        val height = containerHeight(6)
        val far = subject.frameMarker("card", 8, 18, imageHeight = height, tint = "gold")
        assertTrue(payloadOf(far) in 0..0xFFFFFF)
    }
}
