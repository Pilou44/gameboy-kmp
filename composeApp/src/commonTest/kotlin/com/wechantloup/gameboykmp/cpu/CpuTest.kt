package com.wechantloup.gameboykmp.cpu

import com.wechantloup.gameboykmp.cartridge.RomOnlyCartridge
import com.wechantloup.gameboykmp.memory.Memory
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CpuTest {
    private lateinit var memory: Memory
    private lateinit var cpu: Cpu
    val cartridge = RomOnlyCartridge(ByteArray(0x7FFF))

    @BeforeTest
    fun setUp() {
        memory = Memory(cartridge)
        cpu = Cpu(memory)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
    }

    /* Loaders */

    @Test
    fun loadATest() {
        memory.write(0xC000, 0x3E)
        memory.write(0xC001, 0x42)
        cpu.step()
        assertEquals(0x42, cpu.registers.a)
    }
    @Test
    fun loadBTest() {
        memory.write(0xC000, 0x06)
        memory.write(0xC001, 0x42)
        cpu.step()
        assertEquals(0x42, cpu.registers.b)
    }
    @Test
    fun loadCTest() {
        memory.write(0xC000, 0x0E)
        memory.write(0xC001, 0x42)
        cpu.step()
        assertEquals(0x42, cpu.registers.c)
    }
    @Test
    fun loadDTest() {
        memory.write(0xC000, 0x16)
        memory.write(0xC001, 0x42)
        cpu.step()
        assertEquals(0x42, cpu.registers.d)
    }
    @Test
    fun loadETest() {
        memory.write(0xC000, 0x1E)
        memory.write(0xC001, 0x42)
        cpu.step()
        assertEquals(0x42, cpu.registers.e)
    }
    @Test
    fun loadHTest() {
        memory.write(0xC000, 0x26)
        memory.write(0xC001, 0x42)
        cpu.step()
        assertEquals(0x42, cpu.registers.h)
    }
    @Test
    fun loadLTest() {
        memory.write(0xC000, 0x2E)
        memory.write(0xC001, 0x42)
        cpu.step()
        assertEquals(0x42, cpu.registers.l)
    }

    @Test
    fun loadRegisterTest() {
        for (src in 0..7) {
            if (src == 6) continue  // (HL) - ToDo not implemented
            for (dst in 0..7) {
                if (dst == 6) continue  // (HL) - ToDo not implemented

                cpu.reset()
                cpu.registers.f = 0x00
                cpu.registers.pc = 0xC000
                val code = 0x40 or (dst shl 3) or src
                memory.write(0xC000, code)

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
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.a = 0x01
        memory.write(0xC000, 0x80) // ADD A, B
        cpu.registers.b = 0xFF
        cpu.step()
        assertEquals(0x00, cpu.registers.a)
        assertTrue(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagN)
        assertTrue(cpu.registers.flagC)

        // H = true (0x08 + 0x08)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.a = 0x08
        cpu.registers.b = 0x08
        memory.write(0xC000, 0x80) // ADD A, B
        cpu.step()
        assertTrue(cpu.registers.flagH)

        // H = false, Z = false, C = false (0x04 + 0x03)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.a = 0x04
        cpu.registers.b = 0x03
        memory.write(0xC000, 0x80) // ADD A, B
        cpu.step()
        assertEquals(0x07, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)

        // Same tests with C = true
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = true
        cpu.registers.a = 0x01
        memory.write(0xC000, 0x80) // ADD A, B
        cpu.registers.b = 0xFF
        cpu.step()
        assertEquals(0x00, cpu.registers.a)
        assertTrue(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagN)
        assertTrue(cpu.registers.flagC)

        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = true
        cpu.registers.a = 0x08
        cpu.registers.b = 0x08
        memory.write(0xC000, 0x80) // ADD A, B
        cpu.step()
        assertTrue(cpu.registers.flagH)

        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = true
        cpu.registers.a = 0x04
        cpu.registers.b = 0x03
        memory.write(0xC000, 0x80) // ADD A, B
        cpu.step()
        assertEquals(0x07, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)
    }

    @Test
    fun addWithCarryTest() {
        // Z = true, C = true (0x01 + 0xFF = 0x100 -> 0x00)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.a = 0x01
        cpu.registers.flagC = true
        memory.write(0xC000, 0x88) // ADC A, B
        cpu.registers.b = 0xFF
        cpu.step()
        assertEquals(0x01, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagN)
        assertTrue(cpu.registers.flagC)

        // H = true (0x08 + 0x08)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.a = 0x08
        cpu.registers.b = 0x08
        cpu.registers.flagC = true
        memory.write(0xC000, 0x88) // ADC A, B
        cpu.step()
        assertTrue(cpu.registers.flagH)

        // H = false, Z = false, C = false (0x04 + 0x03)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.a = 0x04
        cpu.registers.b = 0x03
        cpu.registers.flagC = true
        memory.write(0xC000, 0x88) // ADC A, B
        cpu.step()
        assertEquals(0x08, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)
    }

    @Test
    fun subFlagsTest() {
        // C = false (a = 0xFF, b = 0x01)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.a = 0xFF
        memory.write(0xC000, 0x90) // SUB A, B
        cpu.registers.b = 0x01
        cpu.step()
        assertEquals(0xFE, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagN)
        assertFalse(cpu.registers.flagC)

        // C = true (a = 0xFF, b = 0x01)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.a = 0x01
        memory.write(0xC000, 0x90) // SUB A, B
        cpu.registers.b = 0xFF
        cpu.step()
        assertEquals(0x02, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagN)
        assertTrue(cpu.registers.flagC)

        // Z = true (0x08 - 0x08)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.a = 0x08
        cpu.registers.b = 0x08
        memory.write(0xC000, 0x90) // SUB A, B
        cpu.step()
        assertTrue(cpu.registers.flagZ)

        // H = true (a = 0x10, b = 0x01)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.a = 0x10
        cpu.registers.b = 0x01
        memory.write(0xC000, 0x90) // SUB A, B
        cpu.step()
        assertEquals(0x0F, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)

        // H = false (a = 0x0F, b = 0x01)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.a = 0x0F
        cpu.registers.b = 0x01
        memory.write(0xC000, 0x90) // SUB A, B
        cpu.step()
        assertEquals(0x0E, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)

        // Same tests with C = true
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = true
        cpu.registers.a = 0xFF
        memory.write(0xC000, 0x90) // SUB A, B
        cpu.registers.b = 0x01
        cpu.step()
        assertEquals(0xFE, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagN)
        assertFalse(cpu.registers.flagC)

        // C = true (a = 0xFF, b = 0x01)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = true
        cpu.registers.a = 0x01
        memory.write(0xC000, 0x90) // SUB A, B
        cpu.registers.b = 0xFF
        cpu.step()
        assertEquals(0x02, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagN)
        assertTrue(cpu.registers.flagC)

        // Z = true (0x08 - 0x08)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = true
        cpu.registers.a = 0x08
        cpu.registers.b = 0x08
        memory.write(0xC000, 0x90) // SUB A, B
        cpu.step()
        assertTrue(cpu.registers.flagZ)

        // H = true (a = 0x10, b = 0x01)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = true
        cpu.registers.a = 0x10
        cpu.registers.b = 0x01
        memory.write(0xC000, 0x90) // SUB A, B
        cpu.step()
        assertEquals(0x0F, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)

        // H = false (a = 0x0F, b = 0x01)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = true
        cpu.registers.a = 0x0F
        cpu.registers.b = 0x01
        memory.write(0xC000, 0x90) // SUB A, B
        cpu.step()
        assertEquals(0x0E, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)
    }

    @Test
    fun subWithCarryTest() {
        // C = false (a = 0xFF, b = 0x01)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = true
        cpu.registers.a = 0xFF
        memory.write(0xC000, 0x98) // SBC A, B
        cpu.registers.b = 0x01
        cpu.step()
        assertEquals(0xFD, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagN)
        assertFalse(cpu.registers.flagC)

        // C = true (a = 0xFF, b = 0x01)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = true
        cpu.registers.a = 0x01
        memory.write(0xC000, 0x98) // SBC A, B
        cpu.registers.b = 0xFF
        cpu.step()
        assertEquals(0x01, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagN)
        assertTrue(cpu.registers.flagC)

        // Z = false (0x08 - 0x08)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = true
        cpu.registers.a = 0x08
        cpu.registers.b = 0x08
        memory.write(0xC000, 0x98) // SBC A, B
        cpu.step()
        assertFalse(cpu.registers.flagZ)

        // H = true (a = 0x10, b = 0x01)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = true
        cpu.registers.a = 0x10
        cpu.registers.b = 0x01
        memory.write(0xC000, 0x98) // SBC A, B
        cpu.step()
        assertEquals(0x0E, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)

        // H = false (a = 0x0F, b = 0x01)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = true
        cpu.registers.a = 0x0F
        cpu.registers.b = 0x01
        memory.write(0xC000, 0x98) // SBC A, B
        cpu.step()
        assertEquals(0x0D, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)

        // C = false (a = 0xFF, b = 0x01)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.a = 0xFF
        memory.write(0xC000, 0x98) // SBC A, B
        cpu.registers.b = 0x01
        cpu.step()
        assertEquals(0xFE, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagN)
        assertFalse(cpu.registers.flagC)

        // C = true (a = 0xFF, b = 0x01)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.a = 0x01
        memory.write(0xC000, 0x98) // SBC A, B
        cpu.registers.b = 0xFF
        cpu.step()
        assertEquals(0x02, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagN)
        assertTrue(cpu.registers.flagC)

        // Z = true (0x08 - 0x08)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.a = 0x08
        cpu.registers.b = 0x08
        memory.write(0xC000, 0x98) // SBC A, B
        cpu.step()
        assertTrue(cpu.registers.flagZ)

        // H = true (a = 0x10, b = 0x01)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.a = 0x10
        cpu.registers.b = 0x01
        memory.write(0xC000, 0x98) // SBC A, B
        cpu.step()
        assertEquals(0x0F, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)

        // H = false (a = 0x0F, b = 0x01)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.a = 0x0F
        cpu.registers.b = 0x01
        memory.write(0xC000, 0x98) // SBC A, B
        cpu.step()
        assertEquals(0x0E, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)
    }

    @Test
    fun and8Test() {
        cpu.registers.a = 0xFF
        cpu.registers.b = 0x01
        memory.write(0xC000, 0xA0) // AND A, B
        cpu.step()
        assertEquals(0x01, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagN)
        assertTrue(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)
    }
    @Test
    fun and8NullTest() {
        cpu.registers.a = 0xFE
        cpu.registers.b = 0x01
        memory.write(0xC000, 0xA0) // AND A, B
        cpu.step()
        assertEquals(0x00, cpu.registers.a)
        assertTrue(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagN)
        assertTrue(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)
    }
    @Test
    fun or8Test() {
        cpu.registers.a = 0x01
        cpu.registers.b = 0x10
        memory.write(0xC000, 0xB0) // OR A, B
        cpu.step()
        assertEquals(0x11, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagN)
        assertFalse(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)
    }
    @Test
    fun or8NullTest() {
        cpu.registers.a = 0x00
        cpu.registers.b = 0x00
        memory.write(0xC000, 0xB0) // OR A, B
        cpu.step()
        assertEquals(0x00, cpu.registers.a)
        assertTrue(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagN)
        assertFalse(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)
    }
    @Test
    fun xor8Test() {
        cpu.registers.a = 0xFF
        cpu.registers.b = 0x03
        memory.write(0xC000, 0xA8) // XOR A, B
        cpu.step()
        assertEquals(0xFC, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagN)
        assertFalse(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)
    }
    @Test
    fun xor8NullTest() {
        cpu.registers.a = 0x01
        cpu.registers.b = 0x01
        memory.write(0xC000, 0xA8) // XOR A, B
        cpu.step()
        assertEquals(0x00, cpu.registers.a)
        assertTrue(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagN)
        assertFalse(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)
    }

    @Test
    fun cpFlagsTest() {
        // C = false (a = 0xFF, b = 0x01)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.a = 0xFF
        memory.write(0xC000, 0xB8) // CP A, B
        cpu.registers.b = 0x01
        cpu.step()
        assertEquals(0xFF, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagN)
        assertFalse(cpu.registers.flagC)

        // C = true (a = 0xFF, b = 0x01)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.a = 0x01
        memory.write(0xC000, 0xB8) // CP A, B
        cpu.registers.b = 0xFF
        cpu.step()
        assertEquals(0x01, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagN)
        assertTrue(cpu.registers.flagC)

        // Z = true (0x08 - 0x08)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.a = 0x08
        cpu.registers.b = 0x08
        memory.write(0xC000, 0xB8) // CP A, B
        cpu.step()
        assertEquals(0x08, cpu.registers.a)
        assertTrue(cpu.registers.flagZ)

        // H = true (a = 0x10, b = 0x01)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.a = 0x10
        cpu.registers.b = 0x01
        memory.write(0xC000, 0xB8) // CP A, B
        cpu.step()
        assertEquals(0x10, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)

        // H = false (a = 0x0F, b = 0x01)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.a = 0x0F
        cpu.registers.b = 0x01
        memory.write(0xC000, 0xB8) // CP A, B
        cpu.step()
        assertEquals(0x0F, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)
    }

    @Test
    fun incFlagsTest() {
        // inc from 0x01, c = false
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.a = 0x01
        memory.write(0xC000, 0x3C) // INC A
        cpu.step()
        assertEquals(0x02, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagN)
        assertFalse(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)

        // inc from 0x0F, c = false
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.a = 0x0F
        memory.write(0xC000, 0x3C) // INC A
        cpu.step()
        assertEquals(0x10, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagN)
        assertTrue(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)

        // inc from 0xFF, c = false
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.a = 0xFF
        memory.write(0xC000, 0x3C) // INC A
        cpu.step()
        assertEquals(0x00, cpu.registers.a)
        assertTrue(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagN)
        assertTrue(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)

        // inc from 0x01, c = true
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = true
        cpu.registers.a = 0x01
        memory.write(0xC000, 0x3C) // INC A
        cpu.step()
        assertEquals(0x02, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagN)
        assertFalse(cpu.registers.flagH)
        assertTrue(cpu.registers.flagC)

        // inc from 0x0F, c = true
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = true
        cpu.registers.a = 0x0F
        memory.write(0xC000, 0x3C) // INC A
        cpu.step()
        assertEquals(0x10, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagN)
        assertTrue(cpu.registers.flagH)
        assertTrue(cpu.registers.flagC)

        // inc from 0xFF, c = true
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = true
        cpu.registers.a = 0xFF
        memory.write(0xC000, 0x3C) // INC A
        cpu.step()
        assertEquals(0x00, cpu.registers.a)
        assertTrue(cpu.registers.flagZ)
        assertFalse(cpu.registers.flagN)
        assertTrue(cpu.registers.flagH)
        assertTrue(cpu.registers.flagC)
    }

    @Test
    fun decFlagsTest() {
        // dec from 0x02, c = false
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.a = 0x02
        memory.write(0xC000, 0x3D) // DEC A
        cpu.step()
        assertEquals(0x01, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagN)
        assertFalse(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)

        // dec from 0x10, c = false
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.a = 0x10
        memory.write(0xC000, 0x3D) // DEC A
        cpu.step()
        assertEquals(0x0F, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagN)
        assertTrue(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)

        // dec from 0x00, c = false
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.a = 0x00
        memory.write(0xC000, 0x3D) // DEC A
        cpu.step()
        assertEquals(0xFF, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagN)
        assertTrue(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)

        // dec from 0x01, c = false
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.a = 0x01
        memory.write(0xC000, 0x3D) // DEC A
        cpu.step()
        assertEquals(0x00, cpu.registers.a)
        assertTrue(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagN)
        assertFalse(cpu.registers.flagH)
        assertFalse(cpu.registers.flagC)

        // dec from 0x02, c = false
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = true
        cpu.registers.a = 0x02
        memory.write(0xC000, 0x3D) // DEC A
        cpu.step()
        assertEquals(0x01, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagN)
        assertFalse(cpu.registers.flagH)
        assertTrue(cpu.registers.flagC)

        // dec from 0x10, c = false
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = true
        cpu.registers.a = 0x10
        memory.write(0xC000, 0x3D) // DEC A
        cpu.step()
        assertEquals(0x0F, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagN)
        assertTrue(cpu.registers.flagH)
        assertTrue(cpu.registers.flagC)

        // dec from 0x00, c = false
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = true
        cpu.registers.a = 0x00
        memory.write(0xC000, 0x3D) // DEC A
        cpu.step()
        assertEquals(0xFF, cpu.registers.a)
        assertFalse(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagN)
        assertTrue(cpu.registers.flagH)
        assertTrue(cpu.registers.flagC)

        // dec from 0x01, c = false
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = true
        cpu.registers.a = 0x01
        memory.write(0xC000, 0x3D) // DEC A
        cpu.step()
        assertEquals(0x00, cpu.registers.a)
        assertTrue(cpu.registers.flagZ)
        assertTrue(cpu.registers.flagN)
        assertFalse(cpu.registers.flagH)
        assertTrue(cpu.registers.flagC)
    }

    @Test
    fun jpTest() {
        // jump to 0x1303
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        memory.write(0xC000, 0xC3) // JP
        memory.write(0xC001, 0x03)
        memory.write(0xC002, 0x13)
        cpu.step()
        assertEquals(0x1303, cpu.registers.pc)

        // jump to 0x1303 if z true
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagZ = true
        memory.write(0xC000, 0xCA) // JP
        memory.write(0xC001, 0x03)
        memory.write(0xC002, 0x13)
        cpu.step()
        assertEquals(0x1303, cpu.registers.pc)

        // jump to 0x1303 if c true
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = true
        memory.write(0xC000, 0xDA) // JP
        memory.write(0xC001, 0x03)
        memory.write(0xC002, 0x13)
        cpu.step()
        assertEquals(0x1303, cpu.registers.pc)

        // jump to 0x1303 if !z true
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagZ = false
        memory.write(0xC000, 0xC2) // JP
        memory.write(0xC001, 0x03)
        memory.write(0xC002, 0x13)
        cpu.step()
        assertEquals(0x1303, cpu.registers.pc)

        // jump to 0x1303 if !c true
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = false
        memory.write(0xC000, 0xD2) // JP
        memory.write(0xC001, 0x03)
        memory.write(0xC002, 0x13)
        cpu.step()
        assertEquals(0x1303, cpu.registers.pc)

        // jump to 0x1303 if z false
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagZ = false
        memory.write(0xC000, 0xCA) // JP
        memory.write(0xC001, 0x03)
        memory.write(0xC002, 0x13)
        cpu.step()
        assertEquals(0xC003, cpu.registers.pc)

        // jump to 0x1303 if c false
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = false
        memory.write(0xC000, 0xDA) // JP
        memory.write(0xC001, 0x03)
        memory.write(0xC002, 0x13)
        cpu.step()
        assertEquals(0xC003, cpu.registers.pc)

        // jump to 0x1303 if !z false
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagZ = true
        memory.write(0xC000, 0xC2) // JP
        memory.write(0xC001, 0x03)
        memory.write(0xC002, 0x13)
        cpu.step()
        assertEquals(0xC003, cpu.registers.pc)

        // jump to 0x1303 if !c false
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = true
        memory.write(0xC000, 0xD2) // JP
        memory.write(0xC001, 0x03)
        memory.write(0xC002, 0x13)
        cpu.step()
        assertEquals(0xC003, cpu.registers.pc)
    }

    @Test
    fun jrTest() {
        // jump to pc +10
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        memory.write(0xC000, 0x18) // JR
        memory.write(0xC001, 10)
        cpu.step()
        assertEquals(0xC00C, cpu.registers.pc)

        // jump to pc -10
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC00A
        memory.write(0xC00A, 0x18) // JR
        memory.write(0xC00B, -10)
        cpu.step()
        assertEquals(0xC002, cpu.registers.pc)

        // jump from 0x0100 with offset -128
        // 0x102 + (-128) = 0x82
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC080
        memory.write(0xC080, 0x18)
        memory.write(0xC081, 0x80) // -128 signed
        cpu.step()
        assertEquals(0xC002, cpu.registers.pc)
    }

    @Test
    fun jrCTest() {
        /* Jump */

        // jump to pc +10 if c true
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = true
        memory.write(0xC000, 0x38) // JR
        memory.write(0xC001, 10)
        cpu.step()
        assertEquals(0xC00C, cpu.registers.pc)

        // jump to pc -10 if c true
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC00A
        cpu.registers.flagC = true
        memory.write(0xC00A, 0x38) // JR
        memory.write(0xC00B, -10)
        cpu.step()
        assertEquals(0xC002, cpu.registers.pc)

        // jump from 0x0100 with offset -128 if c true
        // 0x102 + (-128) = 0x82
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC080
        cpu.registers.flagC = true
        memory.write(0xC080, 0x38)
        memory.write(0xC081, 0x80) // -128 signed
        cpu.step()
        assertEquals(0xC002, cpu.registers.pc)

        // jump to pc +10 if !c true
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = false
        memory.write(0xC000, 0x30) // JR
        memory.write(0xC001, 10)
        cpu.step()
        assertEquals(0xC00C, cpu.registers.pc)

        // jump to pc -10 if !c true
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC00A
        cpu.registers.flagC = false
        memory.write(0xC00A, 0x30) // JR
        memory.write(0xC00B, -10)
        cpu.step()
        assertEquals(0xC002, cpu.registers.pc)

        // jump from 0x0100 with offset -128 if !c true
        // 0x102 + (-128) = 0x82
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC080
        cpu.registers.flagC = false
        memory.write(0xC080, 0x30)
        memory.write(0xC081, 0x80) // -128 signed
        cpu.step()
        assertEquals(0xC002, cpu.registers.pc)

        /* No jump */

        // jump to pc +10 if c false
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = false
        memory.write(0xC000, 0x38) // JR
        memory.write(0xC001, 10)
        cpu.step()
        assertEquals(0xC002, cpu.registers.pc)

        // jump to pc -10 if c false
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = false
        memory.write(0xC000, 0x38) // JR
        memory.write(0xC001, -10)
        cpu.step()
        assertEquals(0xC002, cpu.registers.pc)

        // jump from 0x0100 with offset -128 if c false
        // 0x102 + (-128) = 0x82
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = false
        memory.write(0xC000, 0x38)
        memory.write(0xC001, 0x80) // -128 signed
        cpu.step()
        assertEquals(0xC002, cpu.registers.pc)

        // jump to pc +10 if !c false
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = true
        memory.write(0xC000, 0x30) // JR
        memory.write(0xC001, 10)
        cpu.step()
        assertEquals(0xC002, cpu.registers.pc)

        // jump to pc -10 if !c false
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = true
        memory.write(0xC000, 0x30) // JR
        memory.write(0xC001, -10)
        cpu.step()
        assertEquals(0xC002, cpu.registers.pc)

        // jump from 0x0100 with offset -128 if !c false
        // 0x102 + (-128) = 0x82
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = true
        memory.write(0xC000, 0x30)
        memory.write(0xC001, 0x80) // -128 signed
        cpu.step()
        assertEquals(0xC002, cpu.registers.pc)
    }

    @Test
    fun jrZTest() {
        /* Jump */

        // jump to pc +10 if z true
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagZ = true
        memory.write(0xC000, 0x28) // JR
        memory.write(0xC001, 10)
        cpu.step()
        assertEquals(0xC00C, cpu.registers.pc)

        // jump to pc -10 if z true
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC00A
        cpu.registers.flagZ = true
        memory.write(0xC00A, 0x28) // JR
        memory.write(0xC00B, -10)
        cpu.step()
        assertEquals(0xC002, cpu.registers.pc)

        // jump from 0x0100 with offset -128 if z true
        // 0x102 + (-128) = 0x82
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC080
        cpu.registers.flagZ = true
        memory.write(0xC080, 0x28)
        memory.write(0xC081, 0x80) // -128 signed
        cpu.step()
        assertEquals(0xC002, cpu.registers.pc)

        // jump to pc +10 if !z true
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagZ = false
        memory.write(0xC000, 0x20) // JR
        memory.write(0xC001, 10)
        cpu.step()
        assertEquals(0xC00C, cpu.registers.pc)

        // jump to pc -10 if !z true
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC00A
        cpu.registers.flagZ = false
        memory.write(0xC00A, 0x20) // JR
        memory.write(0xC00B, -10)
        cpu.step()
        assertEquals(0xC002, cpu.registers.pc)

        // jump from 0x0100 with offset -128 if !z true
        // 0x102 + (-128) = 0x82
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC080
        cpu.registers.flagZ = false
        memory.write(0xC080, 0x20)
        memory.write(0xC081, 0x80) // -128 signed
        cpu.step()
        assertEquals(0xC002, cpu.registers.pc)

        /* No jump */

        // jump to pc +10 if z false
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagZ = false
        memory.write(0xC000, 0x28) // JR
        memory.write(0xC001, 10)
        cpu.step()
        assertEquals(0xC002, cpu.registers.pc)

        // jump to pc -10 if z false
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagZ = false
        memory.write(0xC000, 0x28) // JR
        memory.write(0xC001, -10)
        cpu.step()
        assertEquals(0xC002, cpu.registers.pc)

        // jump from 0x0100 with offset -128 if z false
        // 0x102 + (-128) = 0x82
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagZ = false
        memory.write(0xC000, 0x28)
        memory.write(0xC001, 0x80) // -128 signed
        cpu.step()
        assertEquals(0xC002, cpu.registers.pc)

        // jump to pc +10 if !z false
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagZ = true
        memory.write(0xC000, 0x20) // JR
        memory.write(0xC001, 10)
        cpu.step()
        assertEquals(0xC002, cpu.registers.pc)

        // jump to pc -10 if !z false
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagZ = true
        memory.write(0xC000, 0x20) // JR
        memory.write(0xC001, -10)
        cpu.step()
        assertEquals(0xC002, cpu.registers.pc)

        // jump from 0x0100 with offset -128 if !z false
        // 0x102 + (-128) = 0x82
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagZ = true
        memory.write(0xC000, 0x20)
        memory.write(0xC001, 0x80) // -128 signed
        cpu.step()
        assertEquals(0xC002, cpu.registers.pc)
    }

    @Test
    fun callRetTest() {
        // call to 0xC303
        memory.write(0xC000, 0xCD) // CALL
        memory.write(0xC001, 0x03)
        memory.write(0xC002, 0xC3)
        cpu.step()
        assertEquals(0xC303, cpu.registers.pc)
        assertEquals(0x03, memory.read(cpu.registers.sp))
        assertEquals(0xC0, memory.read(cpu.registers.sp + 1))

        // ret back to 0xC003
        memory.write(0xC303, 0xC9) // RET
        cpu.step()
        assertEquals(0xC003, cpu.registers.pc)
    }

    @Test
    fun callOkRetOkTest() {
        // call to 0xC303 if c true
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = true
        memory.write(0xC000, 0xDC) // CALL
        memory.write(0xC001, 0x03)
        memory.write(0xC002, 0xC3)
        cpu.step()
        assertEquals(0xC303, cpu.registers.pc)
        assertEquals(0x03, memory.read(cpu.registers.sp))
        assertEquals(0xC0, memory.read(cpu.registers.sp + 1))

        // ret back to 0xC003 if c true
        memory.write(0xC303, 0xD8) // RET
        cpu.step()
        assertEquals(0xC003, cpu.registers.pc)

        // call to 0xC303 if z true
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagZ = true
        memory.write(0xC000, 0xCC) // CALL
        memory.write(0xC001, 0x03)
        memory.write(0xC002, 0xC3)
        cpu.step()
        assertEquals(0xC303, cpu.registers.pc)
        assertEquals(0x03, memory.read(cpu.registers.sp))
        assertEquals(0xC0, memory.read(cpu.registers.sp + 1))

        // ret back to 0xC003 if z true
        memory.write(0xC303, 0xC8) // RET
        cpu.step()
        assertEquals(0xC003, cpu.registers.pc)

        // call to 0xC303 if !c true
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = false
        memory.write(0xC000, 0xD4) // CALL
        memory.write(0xC001, 0x03)
        memory.write(0xC002, 0xC3)
        cpu.step()
        assertEquals(0xC303, cpu.registers.pc)
        assertEquals(0x03, memory.read(cpu.registers.sp))
        assertEquals(0xC0, memory.read(cpu.registers.sp + 1))

        // ret back to 0xC003 if !c true
        memory.write(0xC303, 0xD0) // RET
        cpu.step()
        assertEquals(0xC003, cpu.registers.pc)

        // call to 0xC303 if !z true
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagZ = false
        memory.write(0xC000, 0xC4) // CALL
        memory.write(0xC001, 0x03)
        memory.write(0xC002, 0xC3)
        cpu.step()
        assertEquals(0xC303, cpu.registers.pc)
        assertEquals(0x03, memory.read(cpu.registers.sp))
        assertEquals(0xC0, memory.read(cpu.registers.sp + 1))

        // ret back to 0xC003 if !z true
        memory.write(0xC303, 0xC0) // RET
        cpu.step()
        assertEquals(0xC003, cpu.registers.pc)
    }

    @Test
    fun callOkRetNokTest() {
        // call to 0xC303 if c true
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = true
        memory.write(0xC000, 0xDC) // CALL
        memory.write(0xC001, 0x03)
        memory.write(0xC002, 0xC3)
        cpu.step()
        assertEquals(0xC303, cpu.registers.pc)
        assertEquals(0x03, memory.read(cpu.registers.sp))
        assertEquals(0xC0, memory.read(cpu.registers.sp + 1))

        // ret back to 0xC003 if c true
        cpu.registers.flagC = false
        memory.write(0xC303, 0xD8) // RET
        cpu.step()
        assertEquals(0xC304, cpu.registers.pc)

        // call to 0xC303 if z true
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagZ = true
        memory.write(0xC000, 0xCC) // CALL
        memory.write(0xC001, 0x03)
        memory.write(0xC002, 0xC3)
        cpu.step()
        assertEquals(0xC303, cpu.registers.pc)
        assertEquals(0x03, memory.read(cpu.registers.sp))
        assertEquals(0xC0, memory.read(cpu.registers.sp + 1))

        // ret back to 0xC003 if z true
        cpu.registers.flagZ = false
        memory.write(0xC303, 0xC8) // RET
        cpu.step()
        assertEquals(0xC304, cpu.registers.pc)

        // call to 0xC303 if !c true
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = false
        memory.write(0xC000, 0xD4) // CALL
        memory.write(0xC001, 0x03)
        memory.write(0xC002, 0xC3)
        cpu.step()
        assertEquals(0xC303, cpu.registers.pc)
        assertEquals(0x03, memory.read(cpu.registers.sp))
        assertEquals(0xC0, memory.read(cpu.registers.sp + 1))

        // ret back to 0xC003 if !c true
        cpu.registers.flagC = true
        memory.write(0xC303, 0xD0) // RET
        cpu.step()
        assertEquals(0xC304, cpu.registers.pc)

        // call to 0xC303 if !z true
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagZ = false
        memory.write(0xC000, 0xC4) // CALL
        memory.write(0xC001, 0x03)
        memory.write(0xC002, 0xC3)
        cpu.step()
        assertEquals(0xC303, cpu.registers.pc)
        assertEquals(0x03, memory.read(cpu.registers.sp))
        assertEquals(0xC0, memory.read(cpu.registers.sp + 1))

        // ret back to 0xC003 if !z true
        cpu.registers.flagZ = true
        memory.write(0xC303, 0xC0) // RET
        cpu.step()
        assertEquals(0xC304, cpu.registers.pc)
    }

    @Test
    fun callNokTest() {
        // call to 0xC303 if c true
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = false
        memory.write(0xC000, 0xDC) // CALL
        memory.write(0xC001, 0x03)
        memory.write(0xC002, 0xC3)
        cpu.step()
        assertEquals(0xC003, cpu.registers.pc)

        // call to 0xC303 if z true
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagZ = false
        memory.write(0xC000, 0xCC) // CALL
        memory.write(0xC001, 0x03)
        memory.write(0xC002, 0xC3)
        cpu.step()
        assertEquals(0xC003, cpu.registers.pc)

        // call to 0xC303 if !c true
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagC = true
        memory.write(0xC000, 0xD4) // CALL
        memory.write(0xC001, 0x03)
        memory.write(0xC002, 0xC3)
        cpu.step()
        assertEquals(0xC003, cpu.registers.pc)

        // call to 0xC303 if !z true
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.flagZ = true
        memory.write(0xC000, 0xC4) // CALL
        memory.write(0xC001, 0x03)
        memory.write(0xC002, 0xC3)
        cpu.step()
        assertEquals(0xC003, cpu.registers.pc)
    }

    @Test
    fun pushPopBCTest() {
        cpu.registers.bc = 0x1303
        memory.write(0xC000, 0xC5) // PUSH BC
        cpu.step()
        cpu.registers.bc = 0x2702
        assertEquals(0x2702, cpu.registers.bc)
        memory.write(0xC001, 0xC1) // POP BC
        cpu.step()
        assertEquals(0x1303, cpu.registers.bc)
    }

    @Test
    fun pushPopDETest() {
        cpu.registers.de = 0x1303
        memory.write(0xC000, 0xD5) // PUSH DE
        cpu.step()
        cpu.registers.de = 0x2702
        assertEquals(0x2702, cpu.registers.de)
        memory.write(0xC001, 0xD1) // POP DE
        cpu.step()
        assertEquals(0x1303, cpu.registers.de)
    }

    @Test
    fun pushPopHLTest() {
        cpu.registers.hl = 0x1303
        memory.write(0xC000, 0xE5) // PUSH HL
        cpu.step()
        cpu.registers.hl = 0x2702
        assertEquals(0x2702, cpu.registers.hl)
        memory.write(0xC001, 0xE1) // POP HL
        cpu.step()
        assertEquals(0x1303, cpu.registers.hl)
    }

    @Test
    fun pushPopAFTest() {
        cpu.registers.af = 0x1303
        memory.write(0xC000, 0xF5) // PUSH AF
        cpu.step()
        cpu.registers.af = 0x2702
        assertEquals(0x2700, cpu.registers.af)
        memory.write(0xC001, 0xF1) // POP AF
        cpu.step()
        assertEquals(0x1300, cpu.registers.af)
    }

    @Test
    fun interruptionsTest() {
        assertFalse(cpu.ime)
        memory.write(0xC000, 0xFB) // EI
        cpu.step()
        assertTrue(cpu.ime)
        memory.write(0xC001, 0xF3) // DI
        cpu.step()
        assertFalse(cpu.ime)
        memory.write(0xC002, 0xD9) // RETI

        // Simulate push of 0x1303
        memory.write((cpu.registers.sp - 1) and 0xFFFF, 0x13) // high byte
        memory.write((cpu.registers.sp - 2) and 0xFFFF, 0x03) // low byte
        cpu.registers.sp = (cpu.registers.sp - 2) and 0xFFFF

        cpu.step()
        assertTrue(cpu.ime)
        assertEquals(0x1303, cpu.registers.pc)
    }

    @Test
    fun interruptHandlingTest() {
        // IME true, V-Blank pending -> jump to 0x0040
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.ime = true
        memory.write(0xFFFF, 0x01)  // IE: V-Blank enabled
        memory.setIF(0x01)  // IF: V-Blank pending
        cpu.step()
        assertEquals(0x0040, cpu.registers.pc)
        assertTrue(cpu.registers.sp < 0xFFFE)  // PC a été pushé
        assertEquals(0x00, memory.iF and 0x01)  // bit 0 effacé dans IF
        assertFalse(cpu.ime)  // IME désactivé

        // IME false, interrupt pending -> ignored
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.ime = false
        memory.write(0xFFFF, 0x01)  // IE: V-Blank enabled
        memory.setIF(0x01)  // IF: V-Blank pending
        memory.write(0xC000, 0x00)  // NOP
        cpu.step()
        assertEquals(0xC000, cpu.registers.pc)  // pas de saut, mais NOP non exécuté non plus

        // HALT woken by interrupt (IME false)
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.ime = false
        cpu.isHalted = true
        memory.write(0xFFFF, 0x01)  // IE: V-Blank enabled
        memory.setIF(0x01)  // IF: V-Blank pending
        cpu.step()
        assertFalse(cpu.isHalted)
        assertEquals(0xC000, cpu.registers.pc)  // pas de saut car IME false

        // Priority: Timer (bit 2) et V-Blank (bit 0) pending -> V-Blank traité en premier
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.ime = true
        memory.write(0xFFFF, 0x05)  // IE: V-Blank et Timer enabled
        memory.setIF(0x05)  // IF: V-Blank et Timer pending
        cpu.step()
        assertEquals(0x0040, cpu.registers.pc)  // V-Blank prioritaire
        assertEquals(0x04, memory.iF and 0x05)  // seul bit 0 effacé
    }

    @Test
    fun fetch16Test() {
        memory.write(0xC000, 0x21) // LD HL, nn
        memory.write(0xC001, 0x03) // low byte
        memory.write(0xC002, 0x13) // high byte
        cpu.step()
        assertEquals(0x1303, cpu.registers.hl)
    }

    @Test
    fun ldHlIncDecTest() {
        // LD (HL+), A
        cpu.registers.a = 0x42
        cpu.registers.hl = 0xC100
        memory.write(0xC000, 0x22)
        cpu.step()
        assertEquals(0x42, memory.read(0xC100))
        assertEquals(0xC101, cpu.registers.hl)

        // LD (HL-), A
        cpu.reset()
        cpu.registers.f = 0x00
        cpu.registers.pc = 0xC000
        cpu.registers.a = 0x42
        cpu.registers.hl = 0xC100
        memory.write(0xC000, 0x32)
        cpu.step()
        assertEquals(0x42, memory.read(0xC100))
        assertEquals(0xC0FF, cpu.registers.hl)
    }
}
