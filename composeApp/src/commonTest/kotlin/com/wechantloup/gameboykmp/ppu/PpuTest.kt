package com.wechantloup.gameboykmp.ppu

import com.wechantloup.gameboykmp.cpu.MachineMode
import com.wechantloup.gameboykmp.helpers.GameBoyTestHarness
import kotlin.test.Test
import kotlin.test.assertEquals

class PpuTest {
    @Test
    fun `OPRI FF6C read-back masks to bit 0 with high bits set on CGB`() {
        val h = GameBoyTestHarness(MachineMode.CGB)
        h.bus.write(0xFF6C, 0x01); assertEquals(0xFF, h.bus.read(0xFF6C))  // bit0=1, bits1-7 = 1
        h.bus.write(0xFF6C, 0x00); assertEquals(0xFE, h.bus.read(0xFF6C))  // bit0=0, bits1-7 = 1
        h.bus.write(0xFF6C, 0xFE); assertEquals(0xFE, h.bus.read(0xFF6C))  // only bit0 writable
    }

    @Test
    fun `OPRI FF6C reads 0xFF on DMG`() {
        val h = GameBoyTestHarness(MachineMode.DMG)
        h.bus.write(0xFF6C, 0x01)
        assertEquals(0xFF, h.bus.read(0xFF6C))  // not a CGB register on DMG (FF4C..FF7F -> 0xFF)
    }

    @Test
    fun `OPRI residue is DMG priority in CGB_COMPAT without boot ROM`() {
        val h = GameBoyTestHarness(MachineMode.CGB_COMPAT)
        assertEquals(0xFF, h.bus.read(0xFF6C))  // boot writes OPRI=1 for a DMG cart; reproduced
    }

    @Test
    fun `OPRI residue is CGB priority in CGB without boot ROM`() {
        val h = GameBoyTestHarness(MachineMode.CGB)
        assertEquals(0xFE, h.bus.read(0xFF6C))  // CGB boot leaves OPRI=0
    }
}
