package com.wechantloup.gameboykmp.bus

import com.wechantloup.gameboykmp.MachineMode
import com.wechantloup.gameboykmp.cartridge.RomRamCartridge
import dev.mokkery.mock
import kotlin.test.Test
import kotlin.test.assertEquals

class BusPaletteRamTest {

    private val cartridge = RomRamCartridge(
        rom = ByteArray(0x7FFF),
        romName = "name",
        scope = mock(),
        withBattery = false,
    )

    private fun makeBus(mode: MachineMode): Bus =
        Bus(cartridge = cartridge, machineMode = mode)

    @Test
    fun `BCPD writes auto-increment the index when the flag is set`() {
        val bus = makeBus(MachineMode.CGB)
        bus.write(0xFF68, 0x80)                            // index 0, auto-increment ON
        for (i in 0 until 4) bus.write(0xFF69, 0xA0 + i)   // fill indices 0..3

        // Reads never advance the index, so re-point BCPS before each read-back.
        for (i in 0 until 4) {
            bus.write(0xFF68, i)
            assertEquals(0xA0 + i, bus.read(0xFF69))
        }
    }

    @Test
    fun `BCPD reads do not auto-increment the index`() {
        val bus = makeBus(MachineMode.CGB)
        bus.write(0xFF68, 0x80)   // index 0, auto-increment ON
        bus.write(0xFF69, 0x11)   // index 0 → 0x11, index advances to 1
        bus.write(0xFF69, 0x22)   // index 1 → 0x22, index advances to 2

        bus.write(0xFF68, 0x80)   // back to index 0
        assertEquals(0x11, bus.read(0xFF69))
        assertEquals(0x11, bus.read(0xFF69))  // still index 0 — reads never advance
    }

    @Test
    fun `BCPD writes stay put when auto-increment is off`() {
        val bus = makeBus(MachineMode.CGB)
        bus.write(0xFF68, 0x05)   // index 5, auto-increment OFF
        bus.write(0xFF69, 0xDE)
        bus.write(0xFF69, 0xAD)   // overwrites index 5

        bus.write(0xFF68, 0x05)
        assertEquals(0xAD, bus.read(0xFF69))  // only the last write survived
    }

    @Test
    fun `BCPD index wraps from 0x3F to 0x00`() {
        val bus = makeBus(MachineMode.CGB)
        bus.write(0xFF68, 0x80 or 0x3F)  // index 63, auto-increment ON
        bus.write(0xFF69, 0x77)          // writes index 63, index wraps to 0
        bus.write(0xFF69, 0x88)          // writes index 0

        bus.write(0xFF68, 0x3F)
        assertEquals(0x77, bus.read(0xFF69))
        bus.write(0xFF68, 0x00)
        assertEquals(0x88, bus.read(0xFF69))
    }

    @Test
    fun `BCPS read-back sets bit 6 and reflects index and auto-increment`() {
        val bus = makeBus(MachineMode.CGB)
        bus.write(0xFF68, 0x80 or 0x2A)       // index 0x2A, auto-inc ON
        assertEquals(0xEA, bus.read(0xFF68))  // 0x80 | 0x40 | 0x2A

        bus.write(0xFF68, 0x2A)               // auto-inc OFF
        assertEquals(0x6A, bus.read(0xFF68))  // 0x40 | 0x2A (bit 6 still 1)
    }

    @Test
    fun `BG and OBJ palette RAM are independent`() {
        val bus = makeBus(MachineMode.CGB)
        bus.write(0xFF68, 0x00); bus.write(0xFF69, 0x11)  // BG index 0
        bus.write(0xFF6A, 0x00); bus.write(0xFF6B, 0x22)  // OBJ index 0

        bus.write(0xFF68, 0x00); assertEquals(0x11, bus.read(0xFF69))
        bus.write(0xFF6A, 0x00); assertEquals(0x22, bus.read(0xFF6B))
    }

    @Test
    fun `OCPD shares the same auto-increment behavior as BCPD`() {
        val bus = makeBus(MachineMode.CGB)
        bus.write(0xFF6A, 0x80)                            // index 0, auto-inc ON
        for (i in 0 until 3) bus.write(0xFF6B, 0xF0 + i)

        for (i in 0 until 3) {
            bus.write(0xFF6A, i)
            assertEquals(0xF0 + i, bus.read(0xFF6B))
        }
    }

    @Test
    fun `on DMG palette registers are inert and read 0xFF`() {
        val bus = makeBus(MachineMode.DMG)
        bus.write(0xFF68, 0x80)
        bus.write(0xFF69, 0x11)
        assertEquals(0xFF, bus.read(0xFF68))
        assertEquals(0xFF, bus.read(0xFF69))
    }
}
