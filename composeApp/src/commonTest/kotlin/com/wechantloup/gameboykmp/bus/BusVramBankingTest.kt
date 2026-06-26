package com.wechantloup.gameboykmp.bus

import com.wechantloup.gameboykmp.cartridge.RomRamCartridge
import com.wechantloup.gameboykmp.cpu.MachineMode
import dev.mokkery.mock
import kotlin.test.Test
import kotlin.test.assertEquals

class BusVramBankingTest {

    private val cartridge = RomRamCartridge(
        rom = ByteArray(0x7FFF),
        romName = "name",
        scope = mock(),
        withBattery = false,
    )

    private fun makeBus(mode: MachineMode): Bus =
        Bus(cartridge = cartridge, machineMode = mode, bootRom = null)

    @Test
    fun `VBK switches the active VRAM bank for CPU access`() {
        val bus = makeBus(MachineMode.CGB)

        bus.write(0xFF4F, 0x00)
        bus.write(0x8000, 0xAA)   // bank 0

        bus.write(0xFF4F, 0x01)
        bus.write(0x8000, 0x55)   // bank 1, same address

        bus.write(0xFF4F, 0x00)
        assertEquals(0xAA, bus.read(0x8000)) // bank 0 kept its byte
        bus.write(0xFF4F, 0x01)
        assertEquals(0x55, bus.read(0x8000)) // bank 1 kept its own
    }

    @Test
    fun `explicit-bank read sees the byte written through the active bank`() {
        val bus = makeBus(MachineMode.CGB)

        bus.write(0xFF4F, 0x01)
        bus.write(0x9000, 0x42)   // VRAM offset 0x1000, bank 1

        bus.write(0xFF4F, 0x00)   // flip active bank back to 0
        assertEquals(0x42, bus.readVram(1, 0x1000)) // explicit read ignores VBK
        assertEquals(0x00, bus.readVram(0, 0x1000)) // bank 0 untouched
    }

    @Test
    fun `VBK read-back masks unused bits to 1`() {
        val bus = makeBus(MachineMode.CGB)

        bus.write(0xFF4F, 0x00)
        assertEquals(0xFE, bus.read(0xFF4F)) // bank 0, bits 1-7 read as 1

        bus.write(0xFF4F, 0xFF)              // only bit 0 is meaningful
        assertEquals(0xFF, bus.read(0xFF4F)) // bank 1
    }

    @Test
    fun `on DMG VBK is inert and reads as 0xFF`() {
        val bus = makeBus(MachineMode.DMG)

        bus.write(0x8000, 0xAA)   // bank 0 (only reachable bank on DMG)
        bus.write(0xFF4F, 0x01)   // ignored: gate never fires on DMG
        bus.write(0x8000, 0x55)   // still bank 0 → overwrites 0xAA

        assertEquals(0x55, bus.read(0x8000)) // proves VBK did not switch banks
        assertEquals(0xFF, bus.read(0xFF4F)) // DMG path: CGB register reads 0xFF
    }

    @Test
    fun `CGB_COMPAT exposes the CGB register block like CGB`() {
        val bus = makeBus(MachineMode.CGB_COMPAT)

        bus.write(0xFF4F, 0x00)
        // 0xFE (not the DMG 0xFF) proves the gate fired on CGB silicon.
        assertEquals(0xFE, bus.read(0xFF4F))
    }
}
