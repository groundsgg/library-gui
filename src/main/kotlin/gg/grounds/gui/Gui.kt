package gg.grounds.gui

import gg.grounds.i18n.MessageKey
import gg.grounds.i18n.Translations
import java.util.concurrent.CompletableFuture
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventFilter
import net.minestom.server.event.EventNode
import net.minestom.server.event.inventory.InventoryCloseEvent
import net.minestom.server.event.inventory.InventoryOpenEvent
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.trait.EntityEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.item.ItemStack
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule

/**
 * A per-player inventory GUI.
 *
 * One instance serves exactly one player: Minestom shares a single item array and window id across
 * all viewers of an [Inventory], so per-player state (and per-player translation) requires one
 * inventory per player.
 *
 * Every click is cancelled; [GuiButton] handlers decide what happens instead. Minestom fully
 * resyncs slots and cursor after a cancelled click, so no desync is possible and no item can ever
 * leave the GUI — including via hotbar/offhand swaps and drags, which never reach button handlers.
 */
open class Gui(val player: Player, val inventory: Inventory) {

    val size: Int = inventory.size

    internal val tracker = SignalTracker()

    private val buttons = arrayOfNulls<GuiButton>(size)
    // Cooldown clocks keyed by slot, not by button: effects re-create buttons on
    // every run, and a per-instance clock would reset on each re-render.
    private val slotClocks = arrayOfNulls<Long>(size)
    private val effects = mutableListOf<Effect>()
    private val taskSpecs = mutableListOf<Pair<TaskSchedule, () -> Unit>>()
    private val tasks = mutableListOf<Task>()
    private val closeHandlers = mutableListOf<() -> Unit>()
    private var node: EventNode<EntityEvent>? = null

    var isOpen: Boolean = false
        private set

    /**
     * While true, a client-initiated close immediately reopens the GUI (via
     * `InventoryCloseEvent.setNewInventory`, which reopens synchronously inside the same close
     * call). [close] always works.
     */
    var preventClose: Boolean = false

    /** Translator for [text]; set once at build time. */
    var translations: Translations? = null

    /** Renders [key] in this GUI's player's language, via [translations]. */
    fun text(key: MessageKey, vararg args: Pair<String, Any>): Component {
        val translations = checkNotNull(translations) { "set gui.translations before using text()" }
        return translations.render(key, player, *args)
    }

    /** Creates a [Signal] owned by this GUI, for use with [effect]. */
    fun <V> signal(initial: V): Signal<V> = Signal(initial, tracker)

    /**
     * A signal fed by [future]: starts at [initial] and switches to the result on the tick thread
     * once the future completes — combine with [effect] to swap a loading placeholder for real data
     * (`initial = null` infers a nullable signal). A failed future logs and leaves the signal at
     * [initial]. A completion after the GUI is gone only updates state; nothing is sent.
     */
    fun <T> signal(future: CompletableFuture<out T>, initial: T): Signal<T> {
        val signal = signal(initial)
        future.whenComplete { value, error ->
            if (error != null) {
                LOG.log(System.Logger.Level.WARNING, "async gui signal failed", error)
            } else {
                scheduleNextTick { signal.set(value) }
            }
        }
        return signal
    }

    /**
     * Runs [block] immediately and re-runs it whenever a [Signal] read inside it changes. Buttons
     * set inside the block re-render on every run.
     */
    fun effect(block: () -> Unit) {
        val effect = Effect(tracker, block)
        effects += effect
        effect.run()
    }

    /** Places [item] as a clickable button. Replaces whatever occupied the slot. */
    fun button(slot: Int, item: ItemStack, block: GuiButton.() -> Unit = {}): GuiButton {
        val button = GuiButton(item).apply(block)
        setButton(slot, button)
        return button
    }

    /** Places a non-clickable decoration item. */
    fun item(slot: Int, item: ItemStack) {
        setButton(slot, GuiButton(item))
    }

    /** Places (or with null clears) a button and renders its item into the slot. */
    fun setButton(slot: Int, button: GuiButton?) {
        require(slot in 0 until size) { "slot $slot outside 0..${size - 1}" }
        buttons[slot] = button
        // Minestom skips the packet when the new stack equals the current one,
        // so redundant re-renders are cheap. The flip side: an animation that
        // alternates visually-identical stacks must vary a component.
        inventory.setItemStack(slot, button?.item ?: ItemStack.AIR)
    }

    /**
     * Renders a slot's current button item; call after replacing a button's [GuiButton.item].
     * Minestom skips the packet if the item is value-equal to what the slot already shows.
     */
    fun refreshSlot(slot: Int) {
        require(slot in 0 until size) { "slot $slot outside 0..${size - 1}" }
        inventory.setItemStack(slot, buttons[slot]?.item ?: ItemStack.AIR)
    }

    /**
     * Replaces the window title in place (same window id, no reopen flicker). Costs a full window +
     * contents resend — fine per page flip, not per tick.
     */
    fun title(title: Component) = inventory.setTitle(title)

    /**
     * Runs [block] every [period] while the GUI is open. Tasks start on [open] and are cancelled on
     * every close path — client close, server close, GUI-to-GUI switch and disconnect.
     *
     * Call this from the GUI body, not from inside an [effect] block: effects re-run and every call
     * registers an additional task.
     */
    fun every(period: TaskSchedule, block: () -> Unit) {
        taskSpecs += period to block
        if (isOpen) tasks += schedule(period, block)
    }

    /** Runs when the GUI goes away, on every close path. */
    fun onClose(handler: () -> Unit) {
        closeHandlers += handler
    }

    /**
     * Cycles [frames] through [slot] every [period] while the GUI is open. Animates the button that
     * occupies the slot *at call time* (an empty slot gets a decoration), keeping its click
     * handlers; if something re-renders the slot later — an effect, a page flip — the animation
     * stops rather than stomping the new button. Frames must differ from their neighbours —
     * Minestom skips the packet for value-equal items. Call from the GUI body, not inside [effect].
     */
    fun animate(slot: Int, period: TaskSchedule, frames: List<ItemStack>) {
        require(slot in 0 until size) { "slot $slot outside 0..${size - 1}" }
        require(frames.isNotEmpty()) { "frames must not be empty" }
        val owned = buttons[slot] ?: GuiButton(frames.first()).also { setButton(slot, it) }
        var index = 0
        every(period) {
            if (buttons[slot] !== owned) return@every
            index = (index + 1) % frames.size
            owned.item = frames[index]
            refreshSlot(slot)
        }
    }

    /** Shows the GUI. A GUI the player already has open cleans itself up. */
    open fun open() {
        if (isOpen) return
        isOpen = true
        val node = EventNode.type("library-gui/${player.uuid}", EventFilter.ENTITY)
        configureNode(node)
        this.node = node
        player.eventNode().addChild(node)
        taskSpecs.forEach { (period, block) -> tasks += schedule(period, block) }
        if (!player.openInventory(inventory)) {
            // Someone cancelled the InventoryOpenEvent.
            cleanup()
            return
        }
        // A listener may have *redirected* the open via InventoryOpenEvent.setInventory —
        // then openInventory() returned true but the player is showing something else.
        // Re-check once the dust has settled.
        scheduleNextTick { if (isOpen && player.openInventory !== inventory) cleanup() }
    }

    /**
     * Closes the GUI server-side. Also the only way out while [preventClose] is set: the resulting
     * InventoryCloseEvent has fromClient = false, which bypasses the guard below. Safe to call from
     * click handlers.
     */
    fun close() {
        if (!isOpen) return
        player.closeInventory()
    }

    /** Adds the listeners this GUI needs while open. Subclasses may add their own. */
    protected open fun configureNode(node: EventNode<EntityEvent>) {
        node.addListener(InventoryPreClickEvent::class.java, ::onPreClick)
        node.addListener(InventoryCloseEvent::class.java) { event ->
            if (event.inventory !== inventory) return@addListener
            if (preventClose && event.isFromClient) {
                event.newInventory = inventory
            } else {
                cleanup()
            }
        }
        node.addListener(InventoryOpenEvent::class.java) { event ->
            // Player.openInventory(other) does NOT fire InventoryCloseEvent for
            // the previous inventory — this is the only close signal on the
            // GUI-to-GUI (or GUI-to-container) switch path. But the open may
            // still be cancelled or redirected by listeners running after this
            // one, so NEVER tear down inside the dispatch: a GUI stripped of its
            // click-cancelling listener while still on screen would let players
            // pull the fabricated items out. Re-check next tick instead.
            if (event.inventory !== inventory) {
                scheduleNextTick { if (isOpen && player.openInventory !== inventory) cleanup() }
            }
        }
        node.addListener(PlayerDisconnectEvent::class.java) { cleanup() }
    }

    private fun onPreClick(event: InventoryPreClickEvent) {
        if (player.openInventory !== inventory) return
        // Cancel-by-default; see class doc.
        event.isCancelled = true
        // Clicks in the player's own inventory arrive with the PlayerInventory
        // as event inventory (they never reach an inventory-scoped node, which
        // is why this GUI listens on the player node). They stay cancelled.
        if (event.inventory !== inventory) return
        val click = event.click
        val action = click.action()
        if (action == ClickAction.OTHER) return
        // Single clicks carry their slot; drags would report -999 but are OTHER.
        val slot = click.slot()
        if (slot !in 0 until size) return
        val button = buttons[slot] ?: return
        val cooldown = button.cooldown
        if (cooldown != null && button.hasHandler(action)) {
            val now = System.nanoTime()
            val last = slotClocks[slot]
            if (last != null && now - last < cooldown.toNanos()) return
            slotClocks[slot] = now
        }
        button.dispatch(ClickContext(player, click, slot, action))
    }

    /** Idempotent teardown: cancels tasks, detaches listeners, runs [onClose] handlers. */
    protected fun cleanup() {
        if (!isOpen) return
        isOpen = false
        tasks.forEach(Task::cancel)
        tasks.clear()
        node?.let { player.eventNode().removeChild(it) }
        node = null
        if (closeHandlers.isEmpty()) return
        // Player.closeInventory nulls its open-inventory field AFTER the close event
        // returns, so a GUI opened directly from a close handler would be silently
        // undone (client shows it, server thinks nothing is open, window dead).
        // Deferring one tick makes `onClose { parent.open() }` safe on every path.
        scheduleNextTick { closeHandlers.forEach { it() } }
    }

    private fun schedule(period: TaskSchedule, block: () -> Unit): Task =
        MinecraftServer.getSchedulerManager().scheduleTask(Runnable { block() }, period, period)

    private fun scheduleNextTick(block: () -> Unit) {
        MinecraftServer.getSchedulerManager().scheduleNextTick(Runnable { block() })
    }

    private companion object {
        val LOG: System.Logger = System.getLogger("gg.grounds.gui")
    }
}
