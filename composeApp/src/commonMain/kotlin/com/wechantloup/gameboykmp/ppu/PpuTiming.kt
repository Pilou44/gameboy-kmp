package com.wechantloup.gameboykmp.ppu

/**
 * Dot-accurate (T-cycle) PPU timing model for what the CPU OBSERVES when it reads
 * LY / STAT / OAM / VRAM. Pure and side-effect free.
 *
 * It does NOT drive rendering or interrupts: those stay on the existing M-cycle
 * step path in [Ppu]. This model only answers "what does a CPU read see at a
 * precise dot", which is what sub-M-cycle tests probe.
 *
 * Validated against mooneye acceptance/ppu/lcdon_timing-GS: all 24 sample points
 * reproduced (see PpuTimingTest).
 *
 * Coordinate `lcdOnDot` = dots elapsed since the LCD was last enabled (LCDC bit 7
 * 0 -> 1). The very first scanline after enabling is special (no mode 2, PPU is
 * "late"); every later line is normal.
 *
 * Edges marked [SOFT] are under-determined by the test to a few dots; the values
 * below are a valid centre of the solution family.
 */
object PpuTiming {

    const val LINE_DOTS = 456

    // --- Lines >= 1 (normal), SCX = 0: mode2 [0,80) | mode3 [80,252) | mode0 [252,456)
    const val MODE2_END = 80
    const val MODE3_END = 252

    // --- Line 0 right after LCD enable -------------------------------------- [SOFT]
    const val L0_LEN          = 441   // LY increments to 1 at this dot
    const val L0_MODE3_START  = 65    // line-0 mode 3 window (drives STAT mode bits)
    const val L0_MODE3_END    = 238
    const val L0_ACCESS_START = 70    // line-0 OAM/VRAM block window (independent of STAT)
    const val L0_ACCESS_END   = 242

    // --- Flag offsets (validated) ------------------------------------------------
    const val STAT_LAG           = 4  // STAT mode bits = true mode of STAT_LAG dots ago
    const val COINC_DROP         = 4  // coincidence flag forced 0 for this many dots after LY++
    const val ACCESS_BLOCK_TRAIL = 4  // lines>=1: OAM/VRAM stay blocked past true mode-3 end

    // --- Integration phase, converge against the live test ------------------- [SOFT]
    // Dot within the M-cycle at which a CPU read latches. The unit test runs with 0;
    // the real emulator may need a different value depending on where lcdOnDot is
    // sampled in the machine loop. lcdon_timing-GS prints Cycle/Expected/Actual on
    // failure, so a uniform shift here is a 1-2 iteration fix.
    const val ACCESS_OFFSET = -4

    data class Sample(
        val ly: Int,
        val stat: Int,          // full STAT byte as the CPU reads it (bit7=1, coincidence, mode)
        val oamBlocked: Boolean, // true -> OAM reads return 0xFF
        val vramBlocked: Boolean // true -> VRAM reads return 0xFF
    )

    private fun lineIndex(d: Int): Int =
        if (d < 0) -1 else if (d < L0_LEN) 0 else 1 + (d - L0_LEN) / LINE_DOTS

    private fun lineStart(line: Int): Int =
        if (line <= 0) 0 else L0_LEN + (line - 1) * LINE_DOTS

    /** "True" internal mode at dot d (STAT shows this STAT_LAG dots later). */
    private fun trueMode(d: Int): Int {
        val line = lineIndex(d)
        if (line < 0) return 0
        if (line == 0) return when {
            d < L0_MODE3_START -> 0
            d < L0_MODE3_END   -> 3
            else               -> 0
        }
        val u = d - lineStart(line)
        return when {
            u < MODE2_END -> 2
            u < MODE3_END -> 3
            else          -> 0
        }
    }

    private fun oamBlocked(d: Int): Boolean {
        val line = lineIndex(d)
        if (line < 0) return false
        if (line == 0) return d in L0_ACCESS_START until L0_ACCESS_END
        val u = d - lineStart(line)
        return u in 0 until (MODE3_END + ACCESS_BLOCK_TRAIL) // mode 2 + mode 3 (+ trail)
    }

    private fun vramBlocked(d: Int): Boolean {
        val line = lineIndex(d)
        if (line < 0) return false
        if (line == 0) return d in L0_ACCESS_START until L0_ACCESS_END
        val u = d - lineStart(line)
        return u in MODE2_END until (MODE3_END + ACCESS_BLOCK_TRAIL) // mode 3 only (+ trail)
    }

    private fun coincidence(d: Int, lyc: Int): Boolean {
        val line = lineIndex(d)
        val ly = if (line < 0) 0 else line
        // dropout: the flag reads 0 for COINC_DROP dots after every LY increment
        if (d >= L0_LEN) {
            val boundary = L0_LEN + ((d - L0_LEN) / LINE_DOTS) * LINE_DOTS
            if (d < boundary + COINC_DROP) return false
        }
        return ly == lyc
    }

    /**
     * What the CPU sees when it reads a PPU register at [lcdOnDot], the access landing
     * [accessOffset] dots into the current M-cycle. [lyc] is the raw LYC register value.
     */
    fun sample(lcdOnDot: Int, lyc: Int, accessOffset: Int = ACCESS_OFFSET): Sample {
        val d = lcdOnDot + accessOffset
        val line = lineIndex(d)
        val ly = if (line < 0) 0 else line
        val mode = trueMode(d - STAT_LAG)
        val coin = if (coincidence(d, lyc)) 0x04 else 0x00
        val stat = 0x80 or coin or (mode and 0x03)
        return Sample(ly, stat, oamBlocked(d), vramBlocked(d))
    }
}
