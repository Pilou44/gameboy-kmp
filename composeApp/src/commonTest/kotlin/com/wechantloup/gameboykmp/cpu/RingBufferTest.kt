package com.wechantloup.gameboykmp.cpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RingBufferTest {

    @Test fun `starts empty`() {
        val rb = RingBuffer<Int>(4)
        assertTrue(rb.isEmpty); assertFalse(rb.isFull); assertEquals(0, rb.size)
    }

    @Test fun `push then pop preserves FIFO order`() {
        val rb = RingBuffer<Int>(4)
        rb.push(1); rb.push(2); rb.push(3)
        assertEquals(1, rb.pop()); assertEquals(2, rb.pop()); assertEquals(3, rb.pop())
        assertTrue(rb.isEmpty)
    }

    @Test fun `fills to capacity then reports full`() {
        val rb = RingBuffer<Int>(4)
        repeat(4) { rb.push(it) }
        assertTrue(rb.isFull); assertEquals(4, rb.size)
    }

    @Test fun `wraps around the ring with interleaved push and pop`() {
        val rb = RingBuffer<Int>(4)
        rb.push(10); rb.push(11); rb.push(12)
        assertEquals(10, rb.pop()); assertEquals(11, rb.pop())
        rb.push(13); rb.push(14); rb.push(15)   // tail wraps past the last physical slot
        assertEquals(12, rb.pop()); assertEquals(13, rb.pop())
        assertEquals(14, rb.pop()); assertEquals(15, rb.pop())
        assertTrue(rb.isEmpty)
    }

    @Test fun `overflow throws`() {
        val rb = RingBuffer<Int>(4)
        repeat(4) { rb.push(it) }
        assertFailsWith<IllegalStateException> { rb.push(99) }
    }

    @Test fun `underflow throws`() {
        assertFailsWith<IllegalStateException> { RingBuffer<Int>(4).pop() }
    }

    @Test fun `clear resets to empty and allows reuse`() {
        val rb = RingBuffer<Int>(4)
        rb.push(1); rb.push(2); rb.clear()
        assertTrue(rb.isEmpty)
        rb.push(7); assertEquals(7, rb.pop())
    }

    @Test fun `non power of two capacity is rejected`() {
        assertFailsWith<IllegalArgumentException> { RingBuffer<Int>(6) }
    }
}
