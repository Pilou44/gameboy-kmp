package com.wechantloup.gameboykmp.blarrgtests.tests.cpuinstrs

import com.wechantloup.gameboykmp.blarrgtests.helpers.gameBoyTest
import com.wechantloup.gameboykmp.blarrgtests.helpers.registers
import com.wechantloup.gameboykmp.blarrgtests.helpers.rom
import kotlin.test.Test
import kotlin.test.assertEquals

class SpecialTest {

    /**
     * Test 2: JR negative
     * Loads 0 into A, jumps forward past an INC, then jumps back (JR negative offset)
     * to a second INC. Final value should be 2.
     */
    @Test
    fun `JR negative`() {
        val h = gameBoyTest {
            registers { pc = 0x0100 }
            rom(0x0100,
                0x3E, 0x00,        // LD A, 0          @ 0x0100
                0xC3, 0x06, 0x01,  // JP 0x0106        @ 0x0102
                0x3C,              // INC A (skipped)  @ 0x0105
                0x3C,              // INC A (label -)  @ 0x0106  ← jr lands here
                0x18, 0x00,        // JR + 0 (NOP)     @ 0x0107  ← jr_neg: jr -
            )
        }
        h.step()  // LD A, 0
        assertEquals(0x00, h.cpu.registers.a)
        h.step()  // JP 0x0106 → skips INC A @ 0x0105
        assertEquals(0x0106, h.cpu.registers.pc)
        h.step()  // INC A @ 0x0106 → A=1
        assertEquals(0x01, h.cpu.registers.a)
    }

    /**
     * Test 3: JR positive
     * Jumps forward over one INC A, lands on second INC A. Final value should be 2.
     */
    @Test
    fun `JR positive`() {
        val h = gameBoyTest {
            registers { pc = 0x0100 }
            // ld a, 0    → 0x3E 0x00
            // jr +       → 0x18 0x01   (skip next byte, land on second INC)
            // inc a      → 0x3C        (skipped)
            // inc a (+)  → 0x3C        (jump target)
            // inc a      → 0x3C
            rom(0x0100,
                0x3E, 0x00,  // LD A, 0
                0x18, 0x01,  // JR + (skip 1 byte)
                0x3C,        // INC A (skipped)
                0x3C,        // INC A (jump target)
                0x3C,        // INC A
            )
        }
        h.step()  // LD A, 0
        assertEquals(0x00, h.cpu.registers.a)
        h.step()  // JR +
        h.step()  // INC A (target)
        h.step()  // INC A
        assertEquals(0x02, h.cpu.registers.a)
    }

    /**
     * Test 4: LD PC, HL (JP HL)
     * Loads address of target into HL, jumps via JP (HL), skipping one INC A.
     * Final value should be 2.
     */
    @Test
    fun `LD PC HL`() {
        val h = gameBoyTest {
            registers { pc = 0x0100 }
            rom(0x0100,
                0x21, 0x07, 0x01,  // LD HL, 0x0107    @ 0x0100
                0x3E, 0x00,        // LD A, 0           @ 0x0103
                0xE9,              // JP HL             @ 0x0105
                0x3C,              // INC A (skipped)   @ 0x0106
                0x3C,              // INC A (target)    @ 0x0107
                0x3C,              // INC A             @ 0x0108
            )
        }
        h.step()  // LD HL
        h.step()  // LD A, 0
        h.step()  // JP HL
        h.step()  // INC A @ 0x0107
        h.step()  // INC A @ 0x0108
        assertEquals(0x02, h.cpu.registers.a)
    }

    /**
     * Test 5: POP AF
     * Verifies that the lower nibble of F is always masked to 0 after POP AF.
     * Iterates over all combinations of B (0x12..0xFF) and C (0x00..0xFF step 0x10).
     */
    @Test
    fun `POP AF masks lower nibble of F`() {
        for (b in 0x12..0xFF) {
            for (c in 0x00..0xFF step 0x10) {
                val h = gameBoyTest {
                    registers {
                        bc = (b shl 8) or c
                        sp = 0xFFFE
                        pc = 0x0100
                    }
                    rom(0x0100,
                        0xC5,  // PUSH BC
                        0xF1,  // POP AF
                        0xC5,  // PUSH AF
                        0xD1,  // POP DE
                    )
                }
                h.step()  // PUSH BC
                h.step()  // POP AF
                h.step()  // PUSH AF
                h.step()  // POP DE
                assertEquals(c and 0xF0, h.cpu.registers.e,
                    "F lower nibble not masked for B=0x${b.toString(16)} C=0x${c.toString(16)}")
            }
        }
    }

    /**
     * Test 6: DAA
     * Representative cases covering the combinations most likely to reveal bugs.
     */
    @Test
    fun `DAA after ADD no carry no half-carry`() {
        val h = gameBoyTest {
            registers { a = 0x09; f = 0x00; pc = 0x0100 }
            rom(0x0100, 0xC6, 0x01, 0x27)  // ADD A, 0x01 → DAA
        }
        h.step()  // ADD A, 0x01 → A = 0x0A
        h.step()  // DAA → A = 0x10
        assertEquals(0x10, h.cpu.registers.a)
        assertEquals(false, h.cpu.registers.flagC)
        assertEquals(false, h.cpu.registers.flagZ)
    }

    @Test
    fun `DAA after ADD with half-carry`() {
        val h = gameBoyTest {
            registers { a = 0x08; f = 0x00; pc = 0x0100 }
            rom(0x0100, 0xC6, 0x08, 0x27)  // ADD A, 0x08 → DAA
        }
        h.step()  // ADD A, 0x08 → A = 0x10, H set
        h.step()  // DAA → A = 0x16
        assertEquals(0x16, h.cpu.registers.a)
        assertEquals(false, h.cpu.registers.flagC)
    }

    @Test
    fun `DAA after ADD with carry`() {
        val h = gameBoyTest {
            registers { a = 0x99; f = 0x00; pc = 0x0100 }
            rom(0x0100, 0xC6, 0x01, 0x27)  // ADD A, 0x01 → DAA
        }
        h.step()  // ADD A, 0x01 → A = 0x9A
        h.step()  // DAA → A = 0x00, C set
        assertEquals(0x00, h.cpu.registers.a)
        assertEquals(true, h.cpu.registers.flagC)
        assertEquals(true, h.cpu.registers.flagZ)
    }

    @Test
    fun `DAA after SUB no carry`() {
        val h = gameBoyTest {
            registers { a = 0x10; f = 0x00; pc = 0x0100 }
            rom(0x0100, 0xD6, 0x01, 0x27)  // SUB 0x01 → DAA
        }
        h.step()  // SUB 0x01 → A = 0x0F, N set, H set
        h.step()  // DAA → A = 0x09
        assertEquals(0x09, h.cpu.registers.a)
        assertEquals(false, h.cpu.registers.flagC)
    }

    @Test
    fun `DAA after SUB with carry`() {
        val h = gameBoyTest {
            registers { a = 0x00; f = 0x00; pc = 0x0100 }
            rom(0x0100, 0xD6, 0x01, 0x27)  // SUB 0x01 → DAA
        }
        h.step()  // SUB 0x01 → A = 0xFF, N set, C set, H set
        h.step()  // DAA → A = 0x99, C set
        assertEquals(0x99, h.cpu.registers.a)
        assertEquals(true, h.cpu.registers.flagC)
    }
}
