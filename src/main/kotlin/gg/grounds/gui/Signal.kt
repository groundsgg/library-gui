package gg.grounds.gui

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Receives change notifications from [Signal]s it depends on. */
internal fun interface SignalListener {
    fun onChange()
}

/**
 * Tracks which listener is currently executing, so that reading a [Signal] inside an effect block
 * registers the effect as a dependency.
 */
internal class SignalTracker {
    var currentlyTracking: SignalListener? = null
}

/**
 * A reactive variable owned by a [Gui]; create one via [Gui.signal]. Reading it inside a
 * [Gui.effect] block subscribes that block; writing a *different* value re-runs every subscribed
 * block (writing an equal value is a no-op).
 *
 * Usable as a delegated property: `var page by signal(0)`.
 */
class Signal<V> internal constructor(initial: V, private val tracker: SignalTracker) :
    ReadWriteProperty<Any?, V> {
    private var value: V = initial
    private val listeners = LinkedHashSet<SignalListener>()

    override fun getValue(thisRef: Any?, property: KProperty<*>): V = get()

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: V) = set(value)

    fun get(): V {
        tracker.currentlyTracking?.let { listeners += it }
        return value
    }

    fun set(newValue: V) {
        if (newValue == value) return
        value = newValue
        // Copy before iterating: a re-running listener may (re-)subscribe itself.
        listeners.toList().forEach(SignalListener::onChange)
    }

    /**
     * Applies [transform] to the current value and stores the result. The read is untracked, so
     * calling this inside an effect does not subscribe the effect to this signal.
     */
    fun update(transform: (V) -> V) = set(transform(value))
}

/**
 * Re-runs [block] whenever a [Signal] read during its previous run changes. Re-entrant triggers
 * (the block writing a signal it also reads) do not re-run the block.
 */
internal class Effect(private val tracker: SignalTracker, private val block: () -> Unit) :
    SignalListener {
    private var running = false

    fun run() {
        if (running) return
        running = true
        val previous = tracker.currentlyTracking
        tracker.currentlyTracking = this
        try {
            block()
        } finally {
            tracker.currentlyTracking = previous
            running = false
        }
    }

    override fun onChange() = run()
}
