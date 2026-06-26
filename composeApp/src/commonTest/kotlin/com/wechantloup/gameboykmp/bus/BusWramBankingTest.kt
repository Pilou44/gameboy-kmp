package com.wechantloup.gameboykmp.bus

import com.wechantloup.gameboykmp.cartridge.RomRamCartridge
import com.wechantloup.gameboykmp.cpu.MachineMode
import dev.mokkery.mock
import kotlin.test.Test
import kotlin.test.assertEquals

class BusWramBankingTest {

    private val cartridge = RomRamCartridge(
        rom = ByteArray(0x7FFF),
        romName = "name",
        scope = mock(),
        withBattery = false,
    )

    private fun makeBus(mode: MachineMode): Bus =
        Bus(cartridge = cartridge, machineMode = mode, bootRom = null)

    @Test
    fun `SVBK switches the banked WRAM region`() {
        val bus = makeBus(MachineMode.CGB)

        bus.write(0xFF70, 0x01)
        bus.write(0xD000, 0xAA)   // bank 1

        bus.write(0xFF70, 0x02)
        bus.write(0xD000, 0x55)   // bank 2, same address

        bus.write(0xFF70, 0x01)
        assertEquals(0xAA, bus.read(0xD000))
        bus.write(0xFF70, 0x02)
        assertEquals(0x55, bus.read(0xD000))
    }

    @Test
    fun `the fixed WRAM region is unaffected by SVBK`() {
        val bus = makeBus(MachineMode.CGB)

        bus.write(0xFF70, 0x01)
        bus.write(0xC000, 0x11)   // fixed region, always bank 0

        bus.write(0xFF70, 0x05)   // switch the banked region
        assertEquals(0x11, bus.read(0xC000))
    }

    @Test
    fun `writing 0 to SVBK selects bank 1`() {
        val bus = makeBus(MachineMode.CGB)

        bus.write(0xFF70, 0x01)
        bus.write(0xD000, 0x42)   // bank 1

        bus.write(0xFF70, 0x00)   // quirk: 0 maps to bank 1
        assertEquals(0x42, bus.read(0xD000))
    }

    @Test
    fun `echo RAM reflects the active WRAM bank`() {
        val bus = makeBus(MachineMode.CGB)

        bus.write(0xFF70, 0x03)
        bus.write(0xD000, 0x99)            // banked region, bank 3
        assertEquals(0x99, bus.read(0xF000)) // echo of 0xD000 (0xD000 + 0x2000)
    }

    @Test
    fun `SVBK read-back masks upper bits to 1`() {
        val bus = makeBus(MachineMode.CGB)

        bus.write(0xFF70, 0x03)
        assertEquals(0xFB, bus.read(0xFF70)) // 0x03 or 0xF8

        // Normalized-at-write model: writing 0 stores bank 1, so it reads back as 1.
        // TODO: if a SVBK read-back test ever expects the raw written value (0xF8),
        //  switch to a raw-store model and remap 0->1 only inside readWram/writeWram.
        bus.write(0xFF70, 0x00)
        assertEquals(0xF9, bus.read(0xFF70)) // 0x01 or 0xF8
    }

    @Test
    fun `on DMG SVBK is inert and reads as 0xFF`() {
        val bus = makeBus(MachineMode.DMG)

        bus.write(0xD000, 0xAA)   // single banked region on DMG
        bus.write(0xFF70, 0x03)   // ignored: gate never fires on DMG
        bus.write(0xD000, 0x55)   // still bank 1 → overwrites 0xAA

        assertEquals(0x55, bus.read(0xD000)) // proves SVBK did not switch banks
        assertEquals(0xFF, bus.read(0xFF70)) // DMG path
    }
}
