package gg.grounds.gui

import kotlin.test.Test
import kotlin.test.assertEquals

class SignalTest {
    @Test
    fun `effect runs once immediately`() {
        val tracker = SignalTracker()
        var runs = 0
        Effect(tracker) { runs++ }.run()
        assertEquals(1, runs)
    }

    @Test
    fun `effect re-runs when a read signal changes`() {
        val tracker = SignalTracker()
        val name = Signal("a", tracker)
        var runs = 0
        var seen = ""
        Effect(tracker) {
                runs++
                seen = name.get()
            }
            .run()
        name.set("b")
        assertEquals(2, runs)
        assertEquals("b", seen)
    }

    @Test
    fun `signal not read by the effect does not trigger it`() {
        val tracker = SignalTracker()
        val read = Signal(1, tracker)
        val unread = Signal(1, tracker)
        var runs = 0
        Effect(tracker) {
                runs++
                read.get()
            }
            .run()
        unread.set(2)
        assertEquals(1, runs)
    }

    @Test
    fun `two effects reading the same signal both re-run`() {
        val tracker = SignalTracker()
        val signal = Signal(0, tracker)
        var a = 0
        var b = 0
        Effect(tracker) {
                a++
                signal.get()
            }
            .run()
        Effect(tracker) {
                b++
                signal.get()
            }
            .run()
        signal.set(1)
        assertEquals(2, a)
        assertEquals(2, b)
    }

    @Test
    fun `effect subscribes only once despite many re-runs`() {
        val tracker = SignalTracker()
        val signal = Signal(0, tracker)
        var runs = 0
        Effect(tracker) {
                runs++
                signal.get()
            }
            .run()
        signal.set(1)
        signal.set(2)
        signal.set(3)
        assertEquals(4, runs)
    }

    @Test
    fun `writing an equal value is a no-op`() {
        val tracker = SignalTracker()
        val signal = Signal(5, tracker)
        var runs = 0
        Effect(tracker) {
                runs++
                signal.get()
            }
            .run()
        signal.set(5)
        assertEquals(1, runs)
    }

    @Test
    fun `effect writing a signal it reads does not recurse`() {
        val tracker = SignalTracker()
        val signal = Signal(0, tracker)
        var runs = 0
        Effect(tracker) {
                runs++
                signal.set(signal.get() + 1)
            }
            .run()
        // The write inside the running effect must not re-trigger it.
        assertEquals(1, runs)
        assertEquals(1, signal.get())
    }

    @Test
    fun `update inside an effect does not subscribe or recurse`() {
        val tracker = SignalTracker()
        val counter = Signal(0, tracker)
        var runs = 0
        Effect(tracker) {
                runs++
                counter.update { it + 1 }
            }
            .run()
        assertEquals(1, runs)
        assertEquals(1, counter.get())
        // The untracked update-read must not have subscribed the effect.
        counter.set(10)
        assertEquals(1, runs)
    }

    @Test
    fun `works as delegated property`() {
        val tracker = SignalTracker()
        val holder =
            object {
                var count: Int by Signal(0, tracker)
            }
        var seen = -1
        Effect(tracker) { seen = holder.count }.run()
        holder.count = 7
        assertEquals(7, seen)
    }

    @Test
    fun `update transforms the current value`() {
        val tracker = SignalTracker()
        val signal = Signal(10, tracker)
        signal.update { it + 5 }
        assertEquals(15, signal.get())
    }

    @Test
    fun `reading outside an effect does not subscribe`() {
        val tracker = SignalTracker()
        val signal = Signal(0, tracker)
        signal.get()
        var runs = 0
        Effect(tracker) { runs++ }.run()
        signal.set(1)
        assertEquals(1, runs)
    }
}
