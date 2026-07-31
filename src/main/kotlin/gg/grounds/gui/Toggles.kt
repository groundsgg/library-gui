package gg.grounds.gui

import net.minestom.server.item.ItemStack

/**
 * A button cycling through [values] (distinct, non-empty): left click forward, right click
 * backward. Returns the current value as a [Signal], so effects can react to it. [onChange] fires
 * on player-driven changes only; writing the signal re-renders without firing it. Call from the GUI
 * body, not inside an effect.
 */
fun <T> Gui.cycleButton(
    slot: Int,
    values: List<T>,
    initial: T = values.first(),
    render: (T) -> ItemStack,
    onChange: ((T) -> Unit)? = null,
): Signal<T> {
    require(values.isNotEmpty()) { "values must not be empty" }
    require(values.distinct().size == values.size) { "values must be distinct" }
    require(initial in values) { "initial value not in values" }
    val current = signal(initial)
    effect {
        val value = current.get()
        fun shift(offset: Int) {
            val index = values.indexOf(value)
            val next = values[(index + offset + values.size) % values.size]
            current.set(next)
            onChange?.invoke(next)
        }
        button(slot, render(value)) {
            onLeftClick { shift(1) }
            onRightClick { shift(-1) }
        }
    }
    return current
}

/** A two-state [cycleButton]. Returns the state as a [Signal]. */
fun Gui.toggleButton(
    slot: Int,
    off: ItemStack,
    on: ItemStack,
    initial: Boolean = false,
    onChange: ((Boolean) -> Unit)? = null,
): Signal<Boolean> =
    cycleButton(slot, listOf(false, true), initial, { if (it) on else off }, onChange)
