package gg.grounds.gui

import net.minestom.server.entity.Player
import net.minestom.server.inventory.click.Click
import net.minestom.server.item.ItemStack

/** The click categories a [GuiButton] can react to. */
enum class ClickAction {
    LEFT,
    RIGHT,
    SHIFT,
    DOUBLE,
    MIDDLE,
    /** Everything else: drags, hotbar/offhand swaps, drops. Never dispatched to buttons. */
    OTHER,
}

/**
 * Maps a Minestom click to its [ClickAction] category.
 *
 * Hotbar/offhand swaps and drags deliberately map to [ClickAction.OTHER]: they can move items out
 * of GUI slots without any cursor involvement, so a GUI must treat them as blocked, never as a
 * button activation.
 */
internal fun Click.action(): ClickAction =
    when (this) {
        is Click.Left -> ClickAction.LEFT
        is Click.Right -> ClickAction.RIGHT
        is Click.LeftShift,
        is Click.RightShift -> ClickAction.SHIFT
        is Click.Double -> ClickAction.DOUBLE
        is Click.Middle -> ClickAction.MIDDLE
        else -> ClickAction.OTHER
    }

/** Passed to button click handlers. */
class ClickContext(val player: Player, val click: Click, val slot: Int, val action: ClickAction)

/**
 * A clickable item in a [Gui] slot. All clicks on the surrounding GUI are cancelled; handlers only
 * decide what happens *in addition*.
 */
class GuiButton(var item: ItemStack) {
    private val handlers = mutableMapOf<ClickAction, ClickContext.() -> Unit>()
    private var anyHandler: (ClickContext.() -> Unit)? = null

    /** Runs for every actionable click that has no more specific handler. */
    fun onClick(handler: ClickContext.() -> Unit) {
        anyHandler = handler
    }

    fun onLeftClick(handler: ClickContext.() -> Unit) {
        handlers[ClickAction.LEFT] = handler
    }

    fun onRightClick(handler: ClickContext.() -> Unit) {
        handlers[ClickAction.RIGHT] = handler
    }

    fun onShiftClick(handler: ClickContext.() -> Unit) {
        handlers[ClickAction.SHIFT] = handler
    }

    internal fun dispatch(context: ClickContext) {
        if (context.action == ClickAction.OTHER) return
        val handler = handlers[context.action] ?: anyHandler ?: return
        handler(context)
    }
}
