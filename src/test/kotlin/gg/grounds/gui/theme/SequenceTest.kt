package gg.grounds.gui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SequenceTest {
    private val three = Sequence(listOf("a", "b", "c"), ticksPerStep = 2)

    @Test
    fun `a step lasts its ticks, then the walk wraps`() {
        assertEquals(listOf("a", "a", "b", "b", "c", "c", "a"), (0L..6L).map(three::at))
        assertEquals(6, three.periodTicks)
    }

    @Test
    fun `ping-pong turns round instead of snapping`() {
        // Forward to the end, back to the start, and only then round again. A pulse that ran to
        // its brightest and jumped to its dimmest would read as a glitch rather than as motion.
        assertEquals(
            listOf("a", "a", "b", "b", "c", "c", "b", "b", "a", "a", "b"),
            (0L..10L).map(three::pingPong),
        )
    }

    @Test
    fun `a single step is the same either way, and two steps have nothing to reverse`() {
        val one = Sequence(listOf("only"))
        assertEquals("only", one.at(7))
        assertEquals("only", one.pingPong(7))
        val two = Sequence(listOf("a", "b"), ticksPerStep = 1)
        assertEquals((0L..5L).map(two::at), (0L..5L).map(two::pingPong))
    }

    @Test
    fun `a tick before zero walks backwards rather than throwing`() {
        // Server ticks do not go negative, but a caller subtracting an offset can, and an index
        // built with % rather than floorMod would step out of the list there.
        // One tick before the start is the last step of the previous pass: "c" over a six-tick
        // wrap, "b" over the eight-tick ping-pong, which is the step the walk was on its way from.
        assertEquals("c", three.at(-1))
        assertEquals("b", three.pingPong(-1))
    }

    @Test
    fun `an empty sequence and a zero rate are refused`() {
        assertFailsWith<IllegalArgumentException> { Sequence(emptyList<String>()) }
        assertFailsWith<IllegalArgumentException> { Sequence(listOf("a"), ticksPerStep = 0) }
    }
}
