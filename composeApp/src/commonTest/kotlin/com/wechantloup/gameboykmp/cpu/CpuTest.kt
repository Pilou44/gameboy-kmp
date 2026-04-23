package com.wechantloup.gameboykmp.cpu

import com.wechantloup.gameboykmp.memory.Memory
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CpuTest {
    private lateinit var memory: Memory
    private lateinit var cpu: Cpu

    @BeforeTest
    fun setUp() {
        memory = Memory()
        cpu = Cpu(memory)
        cpu.registers.reset()
        cpu.registers.f = 0x00
    }

    /* Loaders */

    @Test
    fun loadATest() {
        memory.write(0x0100, 0x3E)
        memory.write(0x0101, 0x42)
        cpu.step()
        assertEquals(0x42, cpu.registers.a)
    }
    @Test
    fun loadBTest() {
        memory.write(0x0100, 0x06)
        memory.write(0x0101, 0x42)
        cpu.step()
        assertEquals(0x42, cpu.registers.b)
    }
    @Test
    fun loadCTest() {
        memory.write(0x0100, 0x0E)
        memory.write(0x0101, 0x42)
        cpu.step()
        assertEquals(0x42, cpu.registers.c)
    }
    @Test
    fun loadDTest() {
        memory.write(0x0100, 0x16)
        memory.write(0x0101, 0x42)
        cpu.step()
        assertEquals(0x42, cpu.registers.d)
    }
    @Test
    fun loadETest() {
        memory.write(0x0100, 0x1E)
        memory.write(0x0101, 0x42)
        cpu.step()
        assertEquals(0x42, cpu.registers.e)
    }
    @Test
    fun loadHTest() {
        memory.write(0x0100, 0x26)
        memory.write(0x0101, 0x42)
        cpu.step()
        assertEquals(0x42, cpu.registers.h)
    }
    @Test
    fun loadLTest() {
        memory.write(0x0100, 0x2E)
        memory.write(0x0101, 0x42)
        cpu.step()
        assertEquals(0x42, cpu.registers.l)
    }

    @Test
    fun loadRegisterTest() {
        for (src in 0..7) {
            if (src == 6) continue  // (HL) - ToDo not implemented
            for (dst in 0..7) {
                if (dst == 6) continue  // (HL) - ToDo not implemented

                cpu.registers.reset()
                cpu.registers.f = 0x00
                val code = 0x40 or (dst shl 3) or src
                memory.write(0x100, code)

                for (i in 0..7) {
                    if (i == 6) continue
                    cpu.setRegister(i, i + 1)  // B=1, C=2, D=3...
                }

                cpu.step()
                assertEquals(cpu.getRegister(src), cpu.getRegister(dst))
            }
        }
    }

    /* Arithmetic */

    @Test
    fun addFlagsTest() {
        // Z = true, C = true (0x01 + 0xFF = 0x100 -> 0x00)
        cpu.registers.reset()
        cpu.registers.f = 0x00
        cpu.registers.a = 0x01
        memory.write(0x100, 0x80) // ADD A, B
        cpu.registers.b = 0xFF
        cpu.step()
        assertEquals(0x00, cpu.registers.a)
        assertTrue(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagN)
        assertTrue(cpu.registers.flagC)

        // H = true (0x08 + 0x08)
        cpu.registers.reset()
        cpu.registers.f = 0x00
        cpu.registers.a = 0x08
        cpu.registers.b = 0x08
        memory.write(0x100, 0x80) // ADD A, B
        cpu.step()
        assertTrue(cpu.registers.flagH)

        // H = false, Z = false, C = false (0x04 + 0x03)
        cpu.registers.reset()
        cpu.registers.f = 0x00
        cpu.registers.a = 0x04
        cpu.registers.b = 0x03
        memory.write(0x100, 0x80) // ADD A, B
        cpu.step()
        assertEquals(0x07, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)

        // Same tests with C = true
        cpu.registers.reset()
        cpu.registers.f = 0x00
        cpu.registers.flagC = true
        cpu.registers.a = 0x01
        memory.write(0x100, 0x80) // ADD A, B
        cpu.registers.b = 0xFF
        cpu.step()
        assertEquals(0x00, cpu.registers.a)
        assertTrue(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagN)
        assertTrue(cpu.registers.flagC)

        cpu.registers.reset()
        cpu.registers.f = 0x00
        cpu.registers.flagC = true
        cpu.registers.a = 0x08
        cpu.registers.b = 0x08
        memory.write(0x100, 0x80) // ADD A, B
        cpu.step()
        assertTrue(cpu.registers.flagH)

        cpu.registers.reset()
        cpu.registers.f = 0x00
        cpu.registers.flagC = true
        cpu.registers.a = 0x04
        cpu.registers.b = 0x03
        memory.write(0x100, 0x80) // ADD A, B
        cpu.step()
        assertEquals(0x07, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)
    }

    @Test
    fun addWithCarryTest() {
        // Z = true, C = true (0x01 + 0xFF = 0x100 -> 0x00)
        cpu.registers.reset()
        cpu.registers.f = 0x00
        cpu.registers.a = 0x01
        cpu.registers.flagC = true
        memory.write(0x100, 0x88) // ADC A, B
        cpu.registers.b = 0xFF
        cpu.step()
        assertEquals(0x01, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagN)
        assertTrue(cpu.registers.flagC)

        // H = true (0x08 + 0x08)
        cpu.registers.reset()
        cpu.registers.f = 0x00
        cpu.registers.a = 0x08
        cpu.registers.b = 0x08
        cpu.registers.flagC = true
        memory.write(0x100, 0x88) // ADC A, B
        cpu.step()
        assertTrue(cpu.registers.flagH)

        // H = false, Z = false, C = false (0x04 + 0x03)
        cpu.registers.reset()
        cpu.registers.f = 0x00
        cpu.registers.a = 0x04
        cpu.registers.b = 0x03
        cpu.registers.flagC = true
        memory.write(0x100, 0x88) // ADC A, B
        cpu.step()
        assertEquals(0x08, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)
    }

    @Test
    fun subFlagsTest() {
        // C = false (a = 0xFF, b = 0x01)
        cpu.registers.reset()
        cpu.registers.f = 0x00
        cpu.registers.a = 0xFF
        memory.write(0x100, 0x90) // SUB A, B
        cpu.registers.b = 0x01
        cpu.step()
        assertEquals(0xFE, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagN)
        assertFalse(cpu.registers.flagC)

        // C = true (a = 0xFF, b = 0x01)
        cpu.registers.reset()
        cpu.registers.f = 0x00
        cpu.registers.a = 0x01
        memory.write(0x100, 0x90) // SUB A, B
        cpu.registers.b = 0xFF
        cpu.step()
        assertEquals(0x02, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagN)
        assertTrue(cpu.registers.flagC)

        // Z = true (0x08 - 0x08)
        cpu.registers.reset()
        cpu.registers.f = 0x00
        cpu.registers.a = 0x08
        cpu.registers.b = 0x08
        memory.write(0x100, 0x90) // SUB A, B
        cpu.step()
        assertTrue(cpu.registers.flagZ)

        // H = true (a = 0x10, b = 0x01)
        cpu.registers.reset()
        cpu.registers.f = 0x00
        cpu.registers.a = 0x10
        cpu.registers.b = 0x01
        memory.write(0x100, 0x90) // SUB A, B
        cpu.step()
        assertEquals(0x0F, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)

        // H = false (a = 0x0F, b = 0x01)
        cpu.registers.reset()
        cpu.registers.f = 0x00
        cpu.registers.a = 0x0F
        cpu.registers.b = 0x01
        memory.write(0x100, 0x90) // SUB A, B
        cpu.step()
        assertEquals(0x0E, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)

        // Same tests with C = true
        cpu.registers.reset()
        cpu.registers.f = 0x00
        cpu.registers.flagC = true
        cpu.registers.a = 0xFF
        memory.write(0x100, 0x90) // SUB A, B
        cpu.registers.b = 0x01
        cpu.step()
        assertEquals(0xFE, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagN)
        assertFalse(cpu.registers.flagC)

        // C = true (a = 0xFF, b = 0x01)
        cpu.registers.reset()
        cpu.registers.f = 0x00
        cpu.registers.flagC = true
        cpu.registers.a = 0x01
        memory.write(0x100, 0x90) // SUB A, B
        cpu.registers.b = 0xFF
        cpu.step()
        assertEquals(0x02, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagN)
        assertTrue(cpu.registers.flagC)

        // Z = true (0x08 - 0x08)
        cpu.registers.reset()
        cpu.registers.f = 0x00
        cpu.registers.flagC = true
        cpu.registers.a = 0x08
        cpu.registers.b = 0x08
        memory.write(0x100, 0x90) // SUB A, B
        cpu.step()
        assertTrue(cpu.registers.flagZ)

        // H = true (a = 0x10, b = 0x01)
        cpu.registers.reset()
        cpu.registers.f = 0x00
        cpu.registers.flagC = true
        cpu.registers.a = 0x10
        cpu.registers.b = 0x01
        memory.write(0x100, 0x90) // SUB A, B
        cpu.step()
        assertEquals(0x0F, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)

        // H = false (a = 0x0F, b = 0x01)
        cpu.registers.reset()
        cpu.registers.f = 0x00
        cpu.registers.flagC = true
        cpu.registers.a = 0x0F
        cpu.registers.b = 0x01
        memory.write(0x100, 0x90) // SUB A, B
        cpu.step()
        assertEquals(0x0E, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)
    }

    @Test
    fun subWithCarryTest() {
        // C = false (a = 0xFF, b = 0x01)
        cpu.registers.reset()
        cpu.registers.f = 0x00
        cpu.registers.flagC = true
        cpu.registers.a = 0xFF
        memory.write(0x100, 0x98) // SBC A, B
        cpu.registers.b = 0x01
        cpu.step()
        assertEquals(0xFD, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagN)
        assertFalse(cpu.registers.flagC)

        // C = true (a = 0xFF, b = 0x01)
        cpu.registers.reset()
        cpu.registers.f = 0x00
        cpu.registers.flagC = true
        cpu.registers.a = 0x01
        memory.write(0x100, 0x98) // SBC A, B
        cpu.registers.b = 0xFF
        cpu.step()
        assertEquals(0x01, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagN)
        assertTrue(cpu.registers.flagC)

        // Z = false (0x08 - 0x08)
        cpu.registers.reset()
        cpu.registers.f = 0x00
        cpu.registers.flagC = true
        cpu.registers.a = 0x08
        cpu.registers.b = 0x08
        memory.write(0x100, 0x98) // SBC A, B
        cpu.step()
        assertFalse(cpu.registers.flagZ)

        // H = true (a = 0x10, b = 0x01)
        cpu.registers.reset()
        cpu.registers.f = 0x00
        cpu.registers.flagC = true
        cpu.registers.a = 0x10
        cpu.registers.b = 0x01
        memory.write(0x100, 0x98) // SBC A, B
        cpu.step()
        assertEquals(0x0E, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)

        // H = false (a = 0x0F, b = 0x01)
        cpu.registers.reset()
        cpu.registers.f = 0x00
        cpu.registers.flagC = true
        cpu.registers.a = 0x0F
        cpu.registers.b = 0x01
        memory.write(0x100, 0x98) // SBC A, B
        cpu.step()
        assertEquals(0x0D, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)

        // C = false (a = 0xFF, b = 0x01)
        cpu.registers.reset()
        cpu.registers.f = 0x00
        cpu.registers.a = 0xFF
        memory.write(0x100, 0x98) // SBC A, B
        cpu.registers.b = 0x01
        cpu.step()
        assertEquals(0xFE, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagN)
        assertFalse(cpu.registers.flagC)

        // C = true (a = 0xFF, b = 0x01)
        cpu.registers.reset()
        cpu.registers.f = 0x00
        cpu.registers.a = 0x01
        memory.write(0x100, 0x98) // SBC A, B
        cpu.registers.b = 0xFF
        cpu.step()
        assertEquals(0x02, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagN)
        assertTrue(cpu.registers.flagC)

        // Z = true (0x08 - 0x08)
        cpu.registers.reset()
        cpu.registers.f = 0x00
        cpu.registers.a = 0x08
        cpu.registers.b = 0x08
        memory.write(0x100, 0x98) // SBC A, B
        cpu.step()
        assertTrue(cpu.registers.flagZ)

        // H = true (a = 0x10, b = 0x01)
        cpu.registers.reset()
        cpu.registers.f = 0x00
        cpu.registers.a = 0x10
        cpu.registers.b = 0x01
        memory.write(0x100, 0x98) // SBC A, B
        cpu.step()
        assertEquals(0x0F, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)

        // H = false (a = 0x0F, b = 0x01)
        cpu.registers.reset()
        cpu.registers.f = 0x00
        cpu.registers.a = 0x0F
        cpu.registers.b = 0x01
        memory.write(0x100, 0x98) // SBC A, B
        cpu.step()
        assertEquals(0x0E, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)
    }
}
