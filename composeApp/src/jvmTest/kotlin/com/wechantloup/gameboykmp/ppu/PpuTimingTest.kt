package com.wechantloup.gameboykmp.ppu

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Ground-truth port of mooneye acceptance/ppu/lcdon_timing-GS.
 *
 * The ROM enables the LCD then reads LY / STAT / OAM / VRAM at 8 fixed M-cycle
 * offsets, repeated over 3 passes shifted by +0 / +1 / +2 M-cycles. Below are the
 * exact values the real DMG returns, lifted straight from the test's expectation
 * tables. This pins every PpuTiming constant EXCEPT PpuTiming.ACCESS_OFFSET, which
 * is an emulator-integration phase and is exercised by the live ROM instead.
 *
 * Use this as the fast red/green loop when nudging the [SOFT] constants.
 */
class PpuTimingTest {

    private val baseCounts = intArrayOf(0, 17, 60, 110, 130, 174, 224, 244)

    // pass -> read: expected LY
    private val expLy = arrayOf(
        intArrayOf(0, 0, 0, 0, 1, 1, 1, 2),
        intArrayOf(0, 0, 0, 1, 1, 1, 2, 2),
        intArrayOf(0, 0, 0, 1, 1, 1, 2, 2),
    )
    // expected full STAT byte, LYC = 0
    private val expStatLyc0 = arrayOf(
        intArrayOf(0x84, 0x84, 0x87, 0x84, 0x82, 0x83, 0x80, 0x82),
        intArrayOf(0x84, 0x87, 0x84, 0x80, 0x82, 0x80, 0x80, 0x82),
        intArrayOf(0x84, 0x87, 0x84, 0x82, 0x83, 0x80, 0x82, 0x83),
    )
    // expected full STAT byte, LYC = 1
    private val expStatLyc1 = arrayOf(
        intArrayOf(0x80, 0x80, 0x83, 0x80, 0x86, 0x87, 0x84, 0x82),
        intArrayOf(0x80, 0x83, 0x80, 0x80, 0x86, 0x84, 0x80, 0x82),
        intArrayOf(0x80, 0x83, 0x80, 0x86, 0x87, 0x84, 0x82, 0x83),
    )
    // expected OAM read (0x00 = accessible, 0xFF = blocked)
    private val expOam = arrayOf(
        intArrayOf(0x00, 0x00, 0xFF, 0x00, 0xFF, 0xFF, 0x00, 0xFF),
        intArrayOf(0x00, 0xFF, 0x00, 0xFF, 0xFF, 0x00, 0xFF, 0xFF),
        intArrayOf(0x00, 0xFF, 0x00, 0xFF, 0xFF, 0x00, 0xFF, 0xFF),
    )
    // expected VRAM read (0x00 = accessible, 0xFF = blocked)
    private val expVram = arrayOf(
        intArrayOf(0x00, 0x00, 0xFF, 0x00, 0x00, 0xFF, 0x00, 0x00),
        intArrayOf(0x00, 0xFF, 0x00, 0x00, 0xFF, 0x00, 0x00, 0xFF),
        intArrayOf(0x00, 0xFF, 0x00, 0x00, 0xFF, 0x00, 0x00, 0xFF),
    )

    private fun lcdOnDot(pass: Int, read: Int): Int = 4 * (baseCounts[read] + pass)

    @Test
    fun reproduces_lcdon_timing_GS() {
        for (p in 0..2) {
            for (i in baseCounts.indices) {
                val dot = lcdOnDot(p, i)
                val s0 = PpuTiming.sample(dot, lyc = 0, accessOffset = 0)
                val s1 = PpuTiming.sample(dot, lyc = 1, accessOffset = 0)
                val where = "pass=$p read=$i (lcdOnDot=$dot)"

                assertEquals(expLy[p][i], s0.ly, "LY @ $where")
                assertEquals(expStatLyc0[p][i], s0.stat, "STAT(LYC=0) @ $where")
                assertEquals(expStatLyc1[p][i], s1.stat, "STAT(LYC=1) @ $where")
                assertEquals(expOam[p][i],  if (s0.oamBlocked)  0xFF else 0x00, "OAM @ $where")
                assertEquals(expVram[p][i], if (s0.vramBlocked) 0xFF else 0x00, "VRAM @ $where")
            }
        }
    }
}
