package gg.grounds.gui.theme

/**
 * A walk through [steps], one step every [ticksPerStep] server ticks.
 *
 * Animation here is not a pack feature and not a shader one: a marker is part of an item's tooltip,
 * so changing the item changes what is drawn. Re-sending the slot on a schedule is the whole
 * mechanism, and this is the small piece that was still being written out by hand at every call
 * site — which frame, which colour, at this tick.
 *
 * Two things follow from where markers live, and both are worth knowing before reaching for this:
 *
 * A marker only draws while its slot is hovered, so an animation is only ever seen by the player
 * pointing at it. There is no such thing as ambient motion in a themed container; the panel itself
 * rides in the window title, and a title cannot be changed without reopening the window.
 *
 * And the slot being re-sent is, by definition, the slot under the cursor. The client rebuilds that
 * tooltip from the new stack, which is cheap but not free — keep [ticksPerStep] at two or more and
 * animate a handful of slots rather than a screenful.
 */
data class Sequence<T>(val steps: List<T>, val ticksPerStep: Int = 2) {
    init {
        require(steps.isNotEmpty()) { "a sequence needs at least one step" }
        require(ticksPerStep >= 1) { "ticksPerStep is at least 1, got $ticksPerStep" }
    }

    /** How long one full pass takes. */
    val periodTicks: Int
        get() = steps.size * ticksPerStep

    /** The step at [tick], wrapping round. */
    fun at(tick: Long): T = steps[index(tick, steps.size)]

    /**
     * The step at [tick], walking forward and then back rather than wrapping.
     *
     * A pulse that runs to its brightest and snaps to its dimmest reads as a glitch. Reversing
     * costs nothing and is the difference between a control that breathes and one that flickers. A
     * two-step sequence is the same either way; three steps take four ticks to return.
     */
    fun pingPong(tick: Long): T {
        if (steps.size == 1) return steps[0]
        val span = 2 * steps.size - 2
        val position = index(tick, span)
        return steps[if (position < steps.size) position else span - position]
    }

    private fun index(tick: Long, modulus: Int): Int =
        (Math.floorDiv(tick, ticksPerStep.toLong()).mod(modulus.toLong())).toInt()
}
