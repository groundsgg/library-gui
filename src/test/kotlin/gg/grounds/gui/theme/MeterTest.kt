package gg.grounds.gui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import net.kyori.adventure.text.Component

class MeterTest {
    private val height = containerHeight(6)

    private val subject =
        theme("grounds", PackFormat(88)) {
            colour("gold", 0xFFBE28)
            frame("bar", "frame/bar.png", meter = MeterAxis.HORIZONTAL)
            frame("column", "frame/column.png", meter = MeterAxis.VERTICAL)
            frame("plate", "frame/plate.png")
        }

    private fun payload(component: Component): Int =
        component.children().first().style().color()!!.value()

    @Test
    fun `the fill rides in the byte a tint would have used`() {
        assertEquals(
            255,
            payload(subject.meterMarker("bar", 8, 18, 1.0, imageHeight = height)) and 0xFF,
        )
        assertEquals(
            128,
            payload(subject.meterMarker("bar", 8, 18, 0.5, imageHeight = height)) and 0xFF,
        )
        assertEquals(
            0,
            payload(subject.meterMarker("bar", 8, 18, 0.0, imageHeight = height)) and 0xFF,
        )
    }

    @Test
    fun `the position comes through a fill untouched`() {
        val plain = payload(subject.frameMarker("plate", 8, 18, imageHeight = height))
        val filled = payload(subject.meterMarker("bar", 8, 18, 0.5, imageHeight = height))
        assertEquals(plain ushr 8, filled ushr 8, "red and green carry the offset either way")
    }

    @Test
    fun `a fill outside the range is clamped rather than thrown`() {
        // Percentages arrive from arithmetic upstream, and 1.02 should show a full bar rather than
        // take down the screen it was opened on.
        assertEquals(
            255,
            payload(subject.meterMarker("bar", 8, 18, 1.4, imageHeight = height)) and 0xFF,
        )
        assertEquals(
            0,
            payload(subject.meterMarker("bar", 8, 18, -3.0, imageHeight = height)) and 0xFF,
        )
    }

    @Test
    fun `a frame that is not a meter refuses to be filled`() {
        val boom =
            assertFailsWith<IllegalArgumentException> {
                subject.meterMarker("plate", 8, 18, 0.5, imageHeight = height)
            }
        assertTrue("not a meter" in boom.message.orEmpty(), boom.message.orEmpty())
        assertFailsWith<IllegalArgumentException> {
            subject.meterMarker("absent", 8, 18, 0.5, imageHeight = height)
        }
    }

    @Test
    fun `an axis is part of the sprite, not of the marker`() {
        // Which way a bar fills does not change between draws, and the payload has no room left —
        // so it rides in the sprite's own size pixel, where it costs neither width nor a data
        // pixel.
        assertEquals(1, MeterAxis.HORIZONTAL.code)
        assertEquals(2, MeterAxis.VERTICAL.code)
        assertEquals(MeterAxis.VERTICAL, subject.frames.first { it.id == "column" }.meter)
        assertEquals(null, subject.frames.first { it.id == "plate" }.meter)
    }
}
