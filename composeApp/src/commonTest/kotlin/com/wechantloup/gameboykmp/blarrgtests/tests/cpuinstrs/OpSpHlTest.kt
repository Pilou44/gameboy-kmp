package com.wechantloup.gameboykmp.blarrgtests.tests.cpuinstrs

import com.wechantloup.gameboykmp.blarrgtests.helpers.gameBoyTest
import com.wechantloup.gameboykmp.blarrgtests.helpers.registers
import com.wechantloup.gameboykmp.blarrgtests.helpers.rom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpSpHlTest {
    @Test
    fun `INC SP`() {
        val h = gameBoyTest {
            registers { sp = 0x1000; pc = 0x0100 }
            rom(0x0100, 0x33)  // INC SP
        }
        h.step()
        assertEquals(0x1001, h.cpu.registers.sp)
    }

    @Test
    fun `INC SP wraps around`() {
        val h = gameBoyTest {
            registers { sp = 0xFFFF; pc = 0x0100 }
            rom(0x0100, 0x33)  // INC SP
        }
        h.step()
        assertEquals(0x0000, h.cpu.registers.sp)
    }

    @Test
    fun `DEC SP`() {
        val h = gameBoyTest {
            registers { sp = 0x1000; pc = 0x0100 }
            rom(0x0100, 0x3B)  // DEC SP
        }
        h.step()
        assertEquals(0x0FFF, h.cpu.registers.sp)
    }

    @Test
    fun `DEC SP wraps around`() {
        val h = gameBoyTest {
            registers { sp = 0x0000; pc = 0x0100 }
            rom(0x0100, 0x3B)  // DEC SP
        }
        h.step()
        assertEquals(0xFFFF, h.cpu.registers.sp)
    }

    @Test
    fun `ADD HL SP no carry`() {
        val h = gameBoyTest {
            registers { hl = 0x1000; sp = 0x0FFF; pc = 0x0100 }
            rom(0x0100, 0x39)  // ADD HL, SP
        }
        h.step()
        assertEquals(0x1FFF, h.cpu.registers.hl)
        assertFalse(h.cpu.registers.flagN)
        assertFalse(h.cpu.registers.flagC)
        assertFalse(h.cpu.registers.flagH)
    }

    @Test
    fun `ADD HL SP with half carry`() {
        val h = gameBoyTest {
            registers { hl = 0x0FFF; sp = 0x0001; pc = 0x0100 }
            rom(0x0100, 0x39)  // ADD HL, SP
        }
        h.step()
        assertEquals(0x1000, h.cpu.registers.hl)
        assertFalse(h.cpu.registers.flagN)
        assertFalse(h.cpu.registers.flagC)
        assertTrue(h.cpu.registers.flagH)
    }

    @Test
    fun `ADD HL SP with carry`() {
        val h = gameBoyTest {
            registers { hl = 0x8000; sp = 0x8000; pc = 0x0100 }
            rom(0x0100, 0x39)  // ADD HL, SP
        }
        h.step()
        assertEquals(0x0000, h.cpu.registers.hl)
        assertFalse(h.cpu.registers.flagN)
        assertTrue(h.cpu.registers.flagC)
    }

    @Test
    fun `LD SP HL`() {
        val h = gameBoyTest {
            registers { hl = 0x1234; sp = 0x0000; pc = 0x0100 }
            rom(0x0100, 0xF9)  // LD SP, HL
        }
        h.step()
        assertEquals(0x1234, h.cpu.registers.sp)
    }

    @Test
    fun `ADD SP positive offset`() {
        val h = gameBoyTest {
            registers { sp = 0x1000; pc = 0x0100 }
            rom(0x0100, 0xE8, 0x01)  // ADD SP, 1
        }
        h.step()
        assertEquals(0x1001, h.cpu.registers.sp)
        assertFalse(h.cpu.registers.flagZ)
        assertFalse(h.cpu.registers.flagN)
    }

    @Test
    fun `ADD SP negative offset`() {
        val h = gameBoyTest {
            registers { sp = 0x1000; pc = 0x0100 }
            rom(0x0100, 0xE8, 0xFF)  // ADD SP, -1
        }
        h.step()
        assertEquals(0x0FFF, h.cpu.registers.sp)
        assertFalse(h.cpu.registers.flagZ)
        assertFalse(h.cpu.registers.flagN)
    }

    @Test
    fun `ADD SP carry flags`() {
        val h = gameBoyTest {
            registers { sp = 0x00FF; pc = 0x0100 }
            rom(0x0100, 0xE8, 0x01)  // ADD SP, 1
        }
        h.step()
        assertEquals(0x0100, h.cpu.registers.sp)
        assertTrue(h.cpu.registers.flagH)
        assertTrue(h.cpu.registers.flagC)
    }

    @Test
    fun `LD HL SP plus positive offset`() {
        val h = gameBoyTest {
            registers { sp = 0x1000; hl = 0x0000; pc = 0x0100 }
            rom(0x0100, 0xF8, 0x01)  // LD HL, SP+1
        }
        h.step()
        assertEquals(0x1001, h.cpu.registers.hl)
        assertEquals(0x1000, h.cpu.registers.sp)  // SP unchanged
        assertFalse(h.cpu.registers.flagZ)
        assertFalse(h.cpu.registers.flagN)
    }

    @Test
    fun `LD HL SP plus negative offset`() {
        val h = gameBoyTest {
            registers { sp = 0x1000; hl = 0x0000; pc = 0x0100 }
            rom(0x0100, 0xF8, 0xFF)  // LD HL, SP-1
        }
        h.step()
        assertEquals(0x0FFF, h.cpu.registers.hl)
        assertEquals(0x1000, h.cpu.registers.sp)  // SP unchanged
    }

    @Test
    fun `LD HL SP plus carry flags`() {
        val h = gameBoyTest {
            registers { sp = 0x00FF; hl = 0x0000; pc = 0x0100 }
            rom(0x0100, 0xF8, 0x01)  // LD HL, SP+1
        }
        h.step()
        assertEquals(0x0100, h.cpu.registers.hl)
        assertTrue(h.cpu.registers.flagH)
        assertTrue(h.cpu.registers.flagC)
    }
}
