package gg.grounds.gui

import kotlin.math.min
import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import net.minestom.server.inventory.Inventory

/** Pure pagination math, kept free of Minestom for testability. Copies [items] defensively. */
internal class Paginator<T>(items: List<T>, private val pageSize: Int) {
    private val items: List<T> = items.toList()

    init {
        require(pageSize > 0) { "pageSize must be positive" }
    }

    val pageCount: Int = ((this.items.size + pageSize - 1) / pageSize).coerceAtLeast(1)

    fun clamp(page: Int): Int = page.coerceIn(0, pageCount - 1)

    fun page(page: Int): List<T> {
        val from = clamp(page) * pageSize
        val to = min(from + pageSize, items.size)
        return if (from >= to) emptyList() else items.subList(from, to)
    }
}

/**
 * A [Gui] that renders a window of items into [contentSlots] and re-renders on page change — same
 * inventory, so page flips don't flicker. Navigation is up to the caller: place buttons that call
 * [nextPage]/[previousPage].
 */
class PagedGui<T>(
    player: Player,
    inventory: Inventory,
    items: List<T>,
    private val contentSlots: List<Int>,
    private val titleFor: ((page: Int, pageCount: Int) -> Component)? = null,
    private val render: (T) -> GuiButton,
) : Gui(player, inventory) {
    private var paginator = Paginator(items, contentSlots.size)
    private val pageSignal = signal(0)
    private val contentVersion = signal(0)

    val pageCount: Int
        get() = paginator.pageCount

    /**
     * Current page, zero-based. Writes clamp into range and re-render; a write that doesn't change
     * the (clamped) page is a no-op — so navigation clicks at the boundaries cost nothing.
     */
    var page: Int
        get() = pageSignal.get()
        set(value) = pageSignal.set(paginator.clamp(value))

    init {
        require(contentSlots.all { it in 0 until size }) { "content slots outside inventory" }
        effect {
            contentVersion.get()
            val current = paginator.clamp(pageSignal.get())
            val slice = paginator.page(current)
            contentSlots.forEachIndexed { index, slot ->
                setButton(slot, slice.getOrNull(index)?.let(render))
            }
            titleFor?.let { title(it(current, paginator.pageCount)) }
        }
    }

    /** Replaces the item list and re-renders; [page] clamps into the new range. */
    fun setItems(items: List<T>) {
        paginator = Paginator(items, contentSlots.size)
        pageSignal.set(paginator.clamp(pageSignal.get()))
        contentVersion.update { it + 1 }
    }

    fun nextPage() {
        page += 1
    }

    fun previousPage() {
        page -= 1
    }
}
