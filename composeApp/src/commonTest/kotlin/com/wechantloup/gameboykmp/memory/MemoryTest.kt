package com.wechantloup.gameboykmp.memory

import kotlin.test.Test
import kotlin.test.assertEquals

class MemoryTest {
    @Test
    fun testWriteAndRead() {
        val memory = Memory()
        val expected = 0x5A
        val address = 0x2702
        memory.write(address, expected)
        assertEquals(expected, memory.read(address))
    }
    @Test
    fun testWriteBigAndRead() {
        val memory = Memory()
        val value = 0x555
        val expected = 0x055
        val address = 0x2702
        memory.write(address, value)
        assertEquals(expected, memory.read(address))
    }
    @Test
    fun testWriteAndReadStart() {
        val memory = Memory()
        val expected = 0x5A
        val address = 0x00
        memory.write(address, expected)
        assertEquals(expected, memory.read(address))
    }
    @Test
    fun testWriteAndReadEnd() {
        val memory = Memory()
        val expected = 0x5A
        val address = 0xFFFF
        memory.write(address, expected)
        assertEquals(expected, memory.read(address))
    }
    @Test
    fun testReadUninitializedMemory() {
        val memory = Memory()
        assertEquals(0, memory.read(0x2702))
    }
}
