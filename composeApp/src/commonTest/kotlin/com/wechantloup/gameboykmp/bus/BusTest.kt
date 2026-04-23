package com.wechantloup.gameboykmp.bus

import com.wechantloup.gameboykmp.cartridge.RomOnlyCartridge
import kotlin.test.Test
import kotlin.test.assertEquals

class BusTest {
    val cartridge = RomOnlyCartridge(ByteArray(0x7FFF))

    @Test
    fun testWriteAndRead() {
        val bus = Bus(cartridge)
        val expected = 0x5A
        val address = 0xC702
        bus.write(address, expected)
        assertEquals(expected, bus.read(address))
    }
    @Test
    fun testWriteBigAndRead() {
        val bus = Bus(cartridge)
        val value = 0x555
        val expected = 0x055
        val address = 0xC702
        bus.write(address, value)
        assertEquals(expected, bus.read(address))
    }
    @Test
    fun testWriteAndReadStart() {
        val bus = Bus(cartridge)
        val expected = 0x5A
        val address = 0xC000
        bus.write(address, expected)
        assertEquals(expected, bus.read(address))
    }
    @Test
    fun testWriteAndReadEnd() {
        val bus = Bus(cartridge)
        val expected = 0x5A
        val address = 0xFFFF
        bus.write(address, expected)
        assertEquals(expected, bus.read(address))
    }
    @Test
    fun testReadUninitializedMemory() {
        val bus = Bus(cartridge)
        assertEquals(0, bus.read(0x2702))
    }
}
