package com.wechantloup.gameboykmp.cpu

import kotlin.test.Test
import kotlin.test.assertEquals

class RegistersTest {

    @Test
    fun testBcRegister() {
        val registers = Registers()
        registers.bc = 0xAA55

        assertEquals(0xAA, registers.b)
        assertEquals(0x55, registers.c)
    }

    @Test
    fun testBAndCRegister() {
        val registers = Registers()
        registers.b = 0xAA
        registers.c = 0x55

        assertEquals(0xAA55, registers.bc)
    }

    @Test
    fun testDeRegister() {
        val registers = Registers()
        registers.de = 0xAA55

        assertEquals(0xAA, registers.d)
        assertEquals(0x55, registers.e)
    }

    @Test
    fun testDAndERegister() {
        val registers = Registers()
        registers.d = 0xAA
        registers.e = 0x55

        assertEquals(0xAA55, registers.de)
    }

    @Test
    fun testHlRegister() {
        val registers = Registers()
        registers.hl = 0xAA55

        assertEquals(0xAA, registers.h)
        assertEquals(0x55, registers.l)
    }

    @Test
    fun testHAndLRegister() {
        val registers = Registers()
        registers.h = 0xAA
        registers.l = 0x55

        assertEquals(0xAA55, registers.hl)
    }

    @Test
    fun testAfRegister() {
        val registers = Registers()
        registers.af = 0xAA55

        assertEquals(0xAA, registers.a)
        assertEquals(0x50, registers.f)
    }

    @Test
    fun testAAndFRegister() {
        val registers = Registers()
        registers.a = 0xAA
        registers.f = 0x55

        assertEquals(0xAA50, registers.af)
    }

    @Test
    fun testZFlag() {
        val registers = Registers()
        registers.flagZ = true

        assertEquals(0x80, registers.f)
    }

    @Test
    fun testNFlag() {
        val registers = Registers()
        registers.flagN = true

        assertEquals(0x40, registers.f)
    }

    @Test
    fun testHFlag() {
        val registers = Registers()
        registers.flagH = true

        assertEquals(0x20, registers.f)
    }

    @Test
    fun testCFlag() {
        val registers = Registers()
        registers.flagC = true

        assertEquals(0x10, registers.f)
    }

    @Test
    fun testFalseZFlag() {
        val registers = Registers()
        registers.f = 0xF0
        registers.flagZ = false

        assertEquals(0x70, registers.f)
    }

    @Test
    fun testFalseNFlag() {
        val registers = Registers()
        registers.f = 0xF0
        registers.flagN = false

        assertEquals(0xB0, registers.f)
    }

    @Test
    fun testFalseHFlag() {
        val registers = Registers()
        registers.f = 0xF0
        registers.flagH = false

        assertEquals(0xD0, registers.f)
    }

    @Test
    fun testFalseCFlag() {
        val registers = Registers()
        registers.f = 0xF0
        registers.flagC = false

        assertEquals(0xE0, registers.f)
    }
}
