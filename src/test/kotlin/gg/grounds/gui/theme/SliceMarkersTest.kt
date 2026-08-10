package gg.grounds.gui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import net.kyori.adventure.text.Component

class SliceMarkersTest {
    private val height = containerHeight(3)

    private val subject =
        theme("grounds", PackFormat(88)) {
            slice("face", "face_l", "face_m", "face_r", capWidth = 3, middleWidth = 170)
            frame("face_l", "frame/face_l.png")
            frame("face_m", "frame/face_m.png", meter = MeterAxis.HORIZONTAL)
            frame("face_r", "frame/face_r.png")
        }

    /**
     * The payload of each marker, which sits a level down.
     *
     * A marker is a glyph plus the space cancelling its advance, wrapped together — so the child of
     * `sliceMarkers` is that wrapper and the colour is on the glyph inside it.
     */
    private fun payloads(component: Component): List<Int> =
        component.children().map { it.children().first().style().color()!!.value() }

    @Test
    fun `any width is three markers, and the caps sit at its ends`() {
        val parts =
            payloads(subject.sliceMarkers("face", x = 7, y = 17, width = 126, imageHeight = height))
        assertEquals(3, parts.size, "two caps and one clipped middle, whatever the width")
        // A payload carries the offset from the window's centre, not the window coordinate, so put
        // the half-width back to compare against the x that was asked for.
        fun windowX(payload: Int) = ((payload shr 16) and 0xFF) - 128 + CONTAINER_WIDTH / 2
        val left = windowX(parts[0])
        val middle = windowX(parts[1])
        val right = windowX(parts[2])
        assertEquals(7, left)
        assertEquals(10, middle, "the middle starts one cap in")
        assertEquals(7 + 126 - 3, right, "the right cap ends flush with the requested width")
    }

    @Test
    fun `the middle is clipped to exactly the gap between the caps`() {
        // A pixel short leaves a seam and a pixel long overdraws the cap; either reads as a
        // rendering fault rather than as the rounding it would be.
        listOf(6, 7, 18, 54, 126, 176).forEach { width ->
            val parts = payloads(subject.sliceMarkers("face", 0, 0, width, imageHeight = height))
            val span = width - 6
            if (span > 0) {
                assertEquals(span, drawnWidth(parts[1] and 0xFF, 170), "width $width")
            }
        }
    }

    @Test
    fun `every span a middle can cover is reachable exactly`() {
        // The guarantee behind the search: a middle is at most 255 wide, so the drawn width steps
        // by zero or one and therefore hits every integer in its range.
        listOf(1, 17, 170, 194, 255).forEach { full ->
            (0..full).forEach { target ->
                assertEquals(target, drawnWidth(meterStep(target, full), full))
            }
        }
    }

    @Test
    fun `a width the artwork cannot make is refused rather than clamped`() {
        val narrow =
            assertFailsWith<IllegalArgumentException> {
                subject.sliceMarkers("face", 0, 0, 5, imageHeight = height)
            }
        assertTrue("caps alone" in narrow.message.orEmpty(), narrow.message.orEmpty())
        val wide =
            assertFailsWith<IllegalArgumentException> {
                subject.sliceMarkers("face", 0, 0, 200, imageHeight = height)
            }
        assertTrue("at most" in wide.message.orEmpty(), wide.message.orEmpty())
        assertFailsWith<IllegalArgumentException> {
            subject.sliceMarkers("absent", 0, 0, 54, imageHeight = height)
        }
    }

    @Test
    fun `a slice needs a cap and a middle a byte can describe`() {
        assertFailsWith<IllegalArgumentException> { Slice("f", "l", "m", "r", 0, 170) }
        assertFailsWith<IllegalArgumentException> { Slice("f", "l", "m", "r", 3, 256) }
        assertEquals(176, Slice("f", "l", "m", "r", 3, 170).maxWidth)
    }
}
