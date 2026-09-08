package com.fedesack.raybanmetadat

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Latest-wins mailbox: at most one item pending and one send in flight.
 * A slow consumer overwrites pending instead of queueing a backlog.
 */
class GazeLatestSend<T>(
    private val deliver: (T) -> Boolean,
    private val execute: (() -> Unit) -> Unit,
) {
    private val pending = AtomicReference<T?>(null)
    private val inFlight = AtomicBoolean(false)
    private val dropped = AtomicInteger(0)
    private val sent = AtomicInteger(0)

    val dropCount: Int get() = dropped.get()
    val sentCount: Int get() = sent.get()
    val busy: Boolean get() = inFlight.get()
    val hasPending: Boolean get() = pending.get() != null

    fun offer(item: T) {
        val previous = pending.getAndSet(item)
        if (previous != null) dropped.incrementAndGet()
        kick()
    }

    fun clear() {
        if (pending.getAndSet(null) != null) dropped.incrementAndGet()
    }

    private fun kick() {
        if (!inFlight.compareAndSet(false, true)) return
        try {
            execute { drain() }
        } catch (_: Throwable) {
            inFlight.set(false)
        }
    }

    private fun drain() {
        try {
            while (true) {
                val next = pending.getAndSet(null) ?: break
                if (!deliver(next)) break
                sent.incrementAndGet()
            }
        } catch (_: Throwable) {
            // A failed deliver must not leave inFlight stuck.
        } finally {
            inFlight.set(false)
            if (pending.get() != null) kick()
        }
    }
}
