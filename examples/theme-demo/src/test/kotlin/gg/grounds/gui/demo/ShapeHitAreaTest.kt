package gg.grounds.gui.demo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the derived hit areas have to be true of, rather than what they happen to be.
 *
 * Asserting the numbers would put the list in a third place and defeat the point of deriving it.
 * These are the properties that make a non-rectangular control work: a triangle reaches its apex
 * and its base and not its top corners, and a rectangle covers its block exactly.
 */
class ShapeHitAreaTest {
    private val square = DemoTheme.SHAPES.first { it.name == "square" }.slots
    private val triangle = DemoTheme.SHAPES.first { it.name == "triangle" }.slots

    @Test
    fun `the square covers its whole block and nothing else`() {
        assertEquals(listOf(3, 4, 5, 12, 13, 14, 21, 22, 23), square)
    }

    @Test
    fun `the triangle reaches its apex and its base but not its top corners`() {
        assertTrue(1 in triangle, "the apex")
        assertTrue(listOf(18, 19, 20).all { it in triangle }, "the base")
        assertTrue(0 !in triangle && 2 !in triangle, "corners the shape never reaches")
        assertTrue(
            triangle.size < 9,
            "a hit area smaller than its block is the whole reason to derive it",
        )
    }

    @Test
    fun `both shapes stay inside the three rows they are drawn in`() {
        (square + triangle).forEach { slot ->
            assertTrue(slot in 0 until 27, "slot $slot is outside the shop's own rows")
        }
    }
}
