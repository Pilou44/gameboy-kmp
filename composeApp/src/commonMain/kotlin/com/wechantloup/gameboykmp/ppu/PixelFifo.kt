package com.wechantloup.gameboykmp.ppu

/**
 * Fixed-capacity ring of pixels, backed by a primitive IntArray (no boxing on the hot path).
 * Each entry is one packed pixel; the meaning of the bits is decided by the producer/mixer —
 * on DMG a BG pixel is just a 2-bit colour index. This is the shared shape for the BG FIFO and,
 * later, the sprite FIFO.
 *
 * Capacity must be a power of two so the wrap is a mask, not a modulo.
 */
class PixelFifo(private val capacity: Int = 16) {

    init {
        require(capacity > 0 && capacity and (capacity - 1) == 0) { "capacity must be a power of two" }
    }

    private val buffer = IntArray(capacity)
    private val mask = capacity - 1
    private var head = 0
    private var count = 0

    val size: Int get() = count

    fun clear() {
        head = 0
        count = 0
    }

    /** Appends one pixel. The caller guarantees room (size < capacity). */
    fun push(pixel: Int) {
        buffer[(head + count) and mask] = pixel
        count++
    }

    /** Removes and returns the front pixel. The caller guarantees size > 0. */
    fun pop(): Int {
        val pixel = buffer[head]
        head = (head + 1) and mask
        count--
        return pixel
    }
}
