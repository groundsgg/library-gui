package gg.grounds.gui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PaginatorTest {
    @Test
    fun `empty list still has one page`() {
        val paginator = Paginator(emptyList<Int>(), 9)
        assertEquals(1, paginator.pageCount)
        assertEquals(emptyList(), paginator.page(0))
    }

    @Test
    fun `exact division`() {
        val paginator = Paginator((1..18).toList(), 9)
        assertEquals(2, paginator.pageCount)
        assertEquals((1..9).toList(), paginator.page(0))
        assertEquals((10..18).toList(), paginator.page(1))
    }

    @Test
    fun `remainder adds a page`() {
        val paginator = Paginator((1..19).toList(), 9)
        assertEquals(3, paginator.pageCount)
        assertEquals(listOf(19), paginator.page(2))
    }

    @Test
    fun `out of range pages clamp`() {
        val paginator = Paginator((1..10).toList(), 9)
        assertEquals(0, paginator.clamp(-3))
        assertEquals(1, paginator.clamp(99))
        assertEquals(paginator.page(1), paginator.page(99))
        assertEquals(paginator.page(0), paginator.page(-1))
    }

    @Test
    fun `single short page`() {
        val paginator = Paginator(listOf("a", "b"), 45)
        assertEquals(1, paginator.pageCount)
        assertEquals(listOf("a", "b"), paginator.page(0))
    }

    @Test
    fun `page size must be positive`() {
        assertFailsWith<IllegalArgumentException> { Paginator(listOf(1), 0) }
    }

    @Test
    fun `mutating the source list after construction has no effect`() {
        val source = mutableListOf(1, 2, 3)
        val paginator = Paginator(source, 2)
        source.clear()
        assertEquals(2, paginator.pageCount)
        assertEquals(listOf(1, 2), paginator.page(0))
        assertEquals(listOf(3), paginator.page(1))
    }
}
