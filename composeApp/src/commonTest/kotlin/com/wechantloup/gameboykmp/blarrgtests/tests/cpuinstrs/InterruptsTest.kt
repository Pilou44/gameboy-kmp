package com.wechantloup.gameboykmp.blarrgtests.tests.cpuinstrs

import com.wechantloup.gameboykmp.blarrgtests.helpers.gameBoyTest
import com.wechantloup.gameboykmp.blarrgtests.helpers.registers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InterruptsTest {
    @Test
    fun `EI enables interrupts`() {
        val h = gameBoyTest {
            registers { pc = 0x0100; sp = 0xFFFE }
            cartridge.onReadRom = { address ->
                when (address) {
                    0x0100 -> 0xFB  // EI
                    0x0050 -> 0xC9  // RET (timer interrupt vector)
                    else -> 0x00
                }
            }
        }
        h.bus.write(0xFFFF, 0x04)  // IE = timer interrupt enabled
        h.step()  // EI
        h.bus.setIF(0x04)          // trigger timer interrupt
        h.step()  // CPU handles interrupt → jumps to 0x0050
        assertEquals(0x0050, h.cpu.registers.pc)
        assertEquals(0x00, h.bus.iF and 0x04, "IF bit 2 should be cleared after interrupt handled")
    }

    @Test
    fun `DI disables interrupts`() {
        val h = gameBoyTest {
            registers { pc = 0x0100; sp = 0xFFFE }
            cartridge.onReadRom = { address ->
                when (address) {
                    0x0100 -> 0xF3  // DI
                    else -> 0x00
                }
            }
        }
        h.bus.write(0xFFFF, 0x04)  // IE = timer interrupt enabled
        h.step()  // DI
        h.bus.setIF(0x04)          // trigger timer interrupt
        h.step()  // CPU should NOT handle interrupt
        assertEquals(0x0101, h.cpu.registers.pc, "PC should not have jumped to interrupt vector")
        assertEquals(0x04, h.bus.iF and 0x04, "IF bit 2 should still be set")
    }

    @Test
    fun `Timer raises IF after enough cycles`() {
        val h = gameBoyTest {
            registers { pc = 0x0100; sp = 0xFFFE }
            cartridge.onReadRom = { 0x00 }  // NOP everywhere
        }
        h.bus.write(0xFF07, 0x05)  // TAC = timer on, frequency 1 (period = 16 cycles)
        h.bus.write(0xFF05, 0x00)  // TIMA = 0
        h.bus.setIF(0x00)          // IF = 0

        // TIMA needs 256 increments × 16 cycles = 4096 cycles to overflow
        h.stepCycles(2048)
        assertEquals(0x00, h.bus.iF and 0x04, "IF bit 2 should not be set yet after 2048 cycles")

        h.stepCycles(2048)
        assertEquals(0x04, h.bus.iF and 0x04, "IF bit 2 should be set after 4096 cycles")
    }

    @Test
    fun `HALT exits when timer interrupt fires`() {
        val h = gameBoyTest {
            registers { pc = 0x0100; sp = 0xFFFE }
            cartridge.onReadRom = { address ->
                when (address) {
                    0x0100 -> 0x76  // HALT
                    else -> 0x00
                }
            }
        }
        h.bus.write(0xFFFF, 0x04)  // IE = timer interrupt enabled
        h.bus.write(0xFF07, 0x05)  // TAC = timer on, period 16 cycles
        h.bus.write(0xFF05, 0x00)  // TIMA = 0
        h.bus.setIF(0x00)          // IF = 0

        h.step()  // HALT
        assertTrue(h.cpu.isHalted, "CPU should be halted")

        h.stepCycles(4096)
        assertFalse(h.cpu.isHalted, "CPU should have exited HALT after timer interrupt")
    }
}
