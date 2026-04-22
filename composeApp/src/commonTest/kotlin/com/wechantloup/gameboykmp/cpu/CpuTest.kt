package com.wechantloup.gameboykmp.cpu

import com.wechantloup.gameboykmp.memory.Memory
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CpuTest {
    private lateinit var memory: Memory
    private lateinit var cpu: Cpu

    @BeforeTest
    fun setUp() {
        memory = Memory()
        cpu = Cpu(memory)
    }

    /* Loaders */

    @Test
    fun loadATest() {
        memory.write(0x00, 0x3E)
        memory.write(0x01, 0x42)
        cpu.step()
        assertEquals(0x42, cpu.registers.a)
    }
    @Test
    fun loadBTest() {
        memory.write(0x00, 0x06)
        memory.write(0x01, 0x42)
        cpu.step()
        assertEquals(0x42, cpu.registers.b)
    }
    @Test
    fun loadCTest() {
        memory.write(0x00, 0x0E)
        memory.write(0x01, 0x42)
        cpu.step()
        assertEquals(0x42, cpu.registers.c)
    }
    @Test
    fun loadDTest() {
        memory.write(0x00, 0x16)
        memory.write(0x01, 0x42)
        cpu.step()
        assertEquals(0x42, cpu.registers.d)
    }
    @Test
    fun loadETest() {
        memory.write(0x00, 0x1E)
        memory.write(0x01, 0x42)
        cpu.step()
        assertEquals(0x42, cpu.registers.e)
    }
    @Test
    fun loadHTest() {
        memory.write(0x00, 0x26)
        memory.write(0x01, 0x42)
        cpu.step()
        assertEquals(0x42, cpu.registers.h)
    }
    @Test
    fun loadLTest() {
        memory.write(0x00, 0x2E)
        memory.write(0x01, 0x42)
        cpu.step()
        assertEquals(0x42, cpu.registers.l)
    }
}
