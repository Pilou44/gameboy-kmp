package com.wechantloup.gameboykmp.memory

import com.wechantloup.gameboykmp.cartridge.RomOnlyCartridge
import kotlin.test.Test
import kotlin.test.assertEquals

class MemoryTest {
    val cartridge = RomOnlyCartridge(ByteArray(0x7FFF))

    @Test
    fun testWriteAndRead() {
        val memory = Memory(cartridge)
        val expected = 0x5A
        val address = 0xC702
        memory.write(address, expected)
        assertEquals(expected, memory.read(address))
    }
    @Test
    fun testWriteBigAndRead() {
        val memory = Memory(cartridge)
        val value = 0x555
        val expected = 0x055
        val address = 0xC702
        memory.write(address, value)
        assertEquals(expected, memory.read(address))
    }
    @Test
    fun testWriteAndReadStart() {
        val memory = Memory(cartridge)
        val expected = 0x5A
        val address = 0xC000
        memory.write(address, expected)
        assertEquals(expected, memory.read(address))
    }
    @Test
    fun testWriteAndReadEnd() {
        val memory = Memory(cartridge)
        val expected = 0x5A
        val address = 0xFFFF
        memory.write(address, expected)
        assertEquals(expected, memory.read(address))
    }
    @Test
    fun testReadUninitializedMemory() {
        val memory = Memory(cartridge)
        assertEquals(0, memory.read(0x2702))
    }
}
