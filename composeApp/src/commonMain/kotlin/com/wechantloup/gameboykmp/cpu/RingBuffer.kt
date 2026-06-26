package com.wechantloup.gameboykmp.cpu

/**
 * Fixed-capacity ring buffer for the CPU micro-op pipeline. Allocated once and reused for the whole
 * CPU lifetime: push/pop only move cursors, never allocate, so the per-T hot path stays GC-free —
 * critical on Kotlin/Native and the Pi Zero.
 *
 * Capacity is a power of two so wrap is a bitmask (& mask), not a modulo — cheaper on in-order ARM.
 * It must exceed the longest sequence ever queued at once: worst cases are CALL (6 M-cycles = 24 T)
 * and interrupt dispatch (5 M-cycles = 20 T), so 32 leaves margin.
 *
 * Over/underflow are bugs (wrong sizing, or popping when empty), not normal states: they fail loud
 * and local via check(), like the rest of the migration scaffolding.
 *
 * Stored as references (Array<Any?>): for the production type MicroOp — a reference type — this is
 * boxing-free. Generic solely so the buffer can be unit-tested in isolation, before MicroOp exists.
 */
class RingBuffer<T : Any>(private val capacity: Int = 32) {

    init {
        require(capacity > 0 && (capacity and (capacity - 1)) == 0) {
            "capacity must be a power of two, was $capacity"
        }
    }

    private val mask = capacity - 1
    private val slots = arrayOfNulls<Any?>(capacity)
    private var head = 0   // read cursor
    private var tail = 0   // write cursor
    private var count = 0

    val size: Int get() = count
    val isEmpty: Boolean get() = count == 0
    val isFull: Boolean get() = count == capacity

    fun push(item: T) {
        check(count < capacity) { "RingBuffer overflow (capacity=$capacity)" }
        slots[tail] = item
        tail = (tail + 1) and mask
        count++
    }

    @Suppress("UNCHECKED_CAST")
    fun pop(): T {
        check(count > 0) { "RingBuffer underflow" }
        val item = slots[head] as T
        head = (head + 1) and mask
        count--
        return item
    }

    /**
     * Resets to empty in O(1). Slots are NOT nulled: the stored references are long-lived MicroOp
     * singletons owned by the CPU, so leaving stale references costs nothing and keeps clear() free
     * of a wipe loop. Used to abandon a partially decoded instruction (e.g. on reset).
     */
    fun clear() {
        head = 0
        tail = 0
        count = 0
    }
}
