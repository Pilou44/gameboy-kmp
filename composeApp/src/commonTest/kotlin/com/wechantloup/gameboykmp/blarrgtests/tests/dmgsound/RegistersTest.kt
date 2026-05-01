package com.wechantloup.gameboykmp.blarrgtests.tests.dmgsound

import com.wechantloup.gameboykmp.blarrgtests.helpers.gameBoyTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RegistersTest {

    /**
     * Test 2: NR10-NR51 and wave RAM write/read
     * Registers always have some bits forced to 1 when read back (the masks).
     * Writing value D and reading back should give D OR mask.
     */
    @Test
    fun `APU registers read back with forced bits`() {
        val masks = intArrayOf(
            0x80, 0x3F, 0x00, 0xFF, 0xBF,  // NR10-NR14
            0xFF, 0x3F, 0x00, 0xFF, 0xBF,  // NR20-NR24
            0x7F, 0xFF, 0x9F, 0xFF, 0xBF,  // NR30-NR34
            0xFF, 0xFF, 0x00, 0x00, 0xBF,  // NR40-NR44
            0x00, 0x00,                     // NR50-NR51
        )
        val h = gameBoyTest {}
        h.bus.write(0xFF26, 0x80)

        for (d in 0x00..0xFF) {
            for (i in masks.indices) {
                val address = 0xFF10 + i
                if (address != 0xFF25) h.bus.write(0xFF25, 0x00)
                if (address != 0xFF1A) h.bus.write(0xFF1A, 0x00)
                h.bus.write(address, d)
                val expected = masks[i] or d
                assertEquals(expected, h.bus.read(address),
                    "Register 0x${address.toString(16)} with value 0x${d.toString(16)}")
            }
            for (i in 0..15) {
                val address = 0xFF30 + i
                h.bus.write(address, d)
                assertEquals(d, h.bus.read(address),
                    "Wave RAM 0x${address.toString(16)} with value 0x${d.toString(16)}")
            }
        }
    }

    /**
     * Test 3: NR52 write/read
     * Bits 6-4 are always 1. Bit 7 is writable. Bits 3-0 are read-only channel status.
     * Off (bit7=0): read back as 0x70
     * On  (bit7=1): read back as 0xF0 (channel bits may be 0)
     */
    @Test
    fun `NR52 read back masks`() {
        val h = gameBoyTest {}
        h.bus.write(0xFF26, 0x00)
        assertEquals(0x70, h.bus.read(0xFF26), "NR52 off should read 0x70")
        h.bus.write(0xFF26, 0xFF)
        assertEquals(0xF0, h.bus.read(0xFF26) and 0xF0, "NR52 on should read 0xF0 (ignoring channel bits)")
    }

    /**
     * Test 4: Powering APU off/on should not affect wave RAM
     */
    @Test
    fun `Wave RAM unaffected by APU power`() {
        val h = gameBoyTest {}
        // Fill wave RAM with 0x37
        for (i in 0..15) h.bus.write(0xFF30 + i, 0x37)
        // Power off
        h.bus.write(0xFF26, 0x00)
        // Verify wave RAM unchanged
        for (i in 0..15) {
            assertEquals(0x37, h.bus.read(0xFF30 + i), "Wave RAM byte $i should be unchanged after power off")
        }
        // Power on
        h.bus.write(0xFF26, 0x80)
        // Verify wave RAM still unchanged
        for (i in 0..15) {
            assertEquals(0x37, h.bus.read(0xFF30 + i), "Wave RAM byte $i should be unchanged after power on")
        }
    }

    /**
     * Test 5: Powering APU off should clear all registers
     */
    @Test
    fun `APU power off clears all registers`() {
        val masks = intArrayOf(
            0x80, 0x3F, 0x00, 0xFF, 0xBF,
            0xFF, 0x3F, 0x00, 0xFF, 0xBF,
            0x7F, 0xFF, 0x9F, 0xFF, 0xBF,
            0xFF, 0xFF, 0x00, 0x00, 0xBF,
            0x00, 0x00,
        )
        val h = gameBoyTest {}
        // Fill all APU registers with 0xFF
        h.bus.write(0xFF26, 0x80)
        for (i in 0xFF10..0xFF25) h.bus.write(i, 0xFF)
        // Power off then on
        h.bus.write(0xFF26, 0x00)
        h.bus.write(0xFF26, 0x80)
        // All registers should read back as their mask only (written value cleared to 0)
        for (i in masks.indices) {
            val address = 0xFF10 + i
            assertEquals(masks[i], h.bus.read(address),
                "Register 0x${address.toString(16)} should be cleared after power cycle")
        }
    }

    /**
     * Test 6: When APU is off, writes to registers are ignored
     */
    @Test
    fun `APU off ignores register writes`() {
        val masks = intArrayOf(
            0x80, 0x3F, 0x00, 0xFF, 0xBF,
            0xFF, 0x3F, 0x00, 0xFF, 0xBF,
            0x7F, 0xFF, 0x9F, 0xFF, 0xBF,
            0xFF, 0xFF, 0x00, 0x00, 0xBF,
            0x00, 0x00,
        )
        val h = gameBoyTest {}
        // Power off
        h.bus.write(0xFF26, 0x00)
        // Try to write 0xFF to all registers
        for (i in 0xFF10..0xFF25) h.bus.write(i, 0xFF)
        // Power on
        h.bus.write(0xFF26, 0x80)
        // Registers should still read as mask only (writes were ignored)
        for (i in masks.indices) {
            val address = 0xFF10 + i
            assertEquals(masks[i], h.bus.read(address),
                "Register 0x${address.toString(16)} should be unchanged when written while APU off")
        }
    }

    /**
     * Test 7: When APU is off, reads should still work normally
     */
    @Test
    fun `APU off allows normal register reads`() {
        val masks = intArrayOf(
            0x80, 0x3F, 0x00, 0xFF, 0xBF,
            0xFF, 0x3F, 0x00, 0xFF, 0xBF,
            0x7F, 0xFF, 0x9F, 0xFF, 0xBF,
            0xFF, 0xFF, 0x00, 0x00, 0xBF,
            0x00, 0x00,
        )
        val h = gameBoyTest {}
        // Power off
        h.bus.write(0xFF26, 0x00)
        // Reads should return mask values (registers cleared, forced bits still present)
        for (i in masks.indices) {
            val address = 0xFF10 + i
            assertEquals(masks[i], h.bus.read(address),
                "Register 0x${address.toString(16)} should be readable while APU off")
        }
        // Power back on
        h.bus.write(0xFF26, 0x80)
    }
}
