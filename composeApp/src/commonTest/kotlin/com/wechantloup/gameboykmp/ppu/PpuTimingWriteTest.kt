package com.wechantloup.gameboykmp.ppu

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Ground-truth port of mooneye acceptance/ppu/lcdon_write_timing-GS.
 *
 * The ROM enables the LCD, waits XXX nops, writes $81 to OAM/VRAM, disables the
 * PPU, then reads the byte back: $81 if the write was accepted (region writable at
 * that dot), $00 if it was dropped (region blocked). Below are the exact DMG
 * results. This pins the write-access edges EXCEPT PpuTiming.WRITE_ACCESS_OFFSET,
 * which is an emulator-integration phase exercised by the live ROM.
 *
 * As with the read test, run with accessOffset = 0: the model frame is calibrated
 * so dot = 4 * nopCount.
 */
class PpuTimingWriteTest {

    private val nopCounts = intArrayOf(
        0, 17, 18, 60, 61, 110, 111, 112, 130, 131, 132, 174, 175, 224, 225, 226, 244, 245, 246
    )

    // $81 = write accepted (accessible), $00 = write dropped (blocked)
    private val expectOam = intArrayOf(
        0x81, 0x81, 0x00, 0x00, 0x81, 0x81, 0x81, 0x00, 0x00, 0x81, 0x00, 0x00, 0x81, 0x81, 0x81, 0x00, 0x00, 0x81, 0x00
    )
    private val expectVram = intArrayOf(
        0x81, 0x81, 0x00, 0x00, 0x81, 0x81, 0x81, 0x81, 0x81, 0x81, 0x00, 0x00, 0x81, 0x81, 0x81, 0x81, 0x81, 0x81, 0x00
    )

    @Test
    fun reproduces_lcdon_write_timing_GS() {
        for (i in nopCounts.indices) {
            val dot = 4 * nopCounts[i]
            val oam  = if (PpuTiming.oamWriteBlocked(dot, accessOffset = 0)) 0x00 else 0x81
            val vram = if (PpuTiming.vramWriteBlocked(dot, accessOffset = 0)) 0x00 else 0x81
            assertEquals(expectOam[i],  oam,  "OAM write @ nop=${nopCounts[i]} (dot=$dot)")
            assertEquals(expectVram[i], vram, "VRAM write @ nop=${nopCounts[i]} (dot=$dot)")
        }
    }
}
