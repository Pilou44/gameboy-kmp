package com.wechantloup.gameboykmp.ppu

import com.wechantloup.gameboykmp.bus.Bus

/**
 * Background fetcher (DMG). Produces 8 BG pixels per cycle into a PixelFifo, driven one dot at a
 * time by the PPU during mode 3. It talks only to the Bus: VRAM via readVram, control registers
 * via read. Nothing here references the PPU internals.
 *
 * Four steps, 2 dots each: tile number -> data low -> data high -> push. The push only succeeds
 * when the FIFO has room for 8 more pixels; otherwise the fetcher stalls, which is what couples
 * the fetch rate (8 at a time) to the shifter's drain rate (1 per dot). The first fetch of the
 * line is a discarded "dummy": it reads but never pushes, and folds into the data-high step so it
 * costs no extra dot. With the push gating, this yields a mode-3 length of 172 + (SCX & 7) dots
 * for pure BG — validated against the hblank_ly_scx_timing oracle with the dot simulator.
 *
 * Registers are read LIVE at each step, so a mid-line write to SCX/SCY/LCDC changes the next
 * fetch natively (Mealybug) — no compensation layer.
 *
 * TODO: the exact sub-dot sampling points (which dot within a step latches SCX/SCY/LCDC) are
 *  pinned by hblank_ly_scx_timing + Mealybug + the simulator; the per-step reads below are the
 *  first cut. Window (fetch restart) and sprite fetch arrive in later steps.
 */
class BgFetcher(private val bus: Bus) {

    private enum class Step { TILE_NUMBER, DATA_LOW, DATA_HIGH, PUSH }

    private var step = Step.TILE_NUMBER
    private var firstDot = false   // each fetch step spans 2 dots
    private var dummyDone = false   // the first fetch of the line is discarded
    private var fetcherX = 0        // tile column within the line: 0, 1, 2, ...
    private var line = 0            // scanline being drawn, latched at reset

    private var tileNumber = 0
    private var dataLow = 0
    private var dataHigh = 0
    private var fineY = 0           // row within the tile, latched at the tile-number step

    /** Resets for the start of a line's mode 3. */
    fun reset(line: Int) {
        step = Step.TILE_NUMBER
        firstDot = false
        dummyDone = false
        fetcherX = 0
        this.line = line
    }

    /** Advances the fetcher by one dot, pushing into [fifo] when a cycle completes with room. */
    fun tick(fifo: PixelFifo) {
        when (step) {
            Step.TILE_NUMBER, Step.DATA_LOW, Step.DATA_HIGH -> {
                firstDot = !firstDot
                if (firstDot) return          // first dot: step in progress
                completeStep()                 // second dot: latch and advance
            }
            Step.PUSH -> {
                // Attempted every dot; pushes only when 8 more pixels fit (FIFO <= 8 of 16).
                if (fifo.size <= HALF_FIFO) {
                    pushRow(fifo)
                    fetcherX++
                    step = Step.TILE_NUMBER
                }
                // else: stall, retry next dot
            }
        }
    }

    private fun completeStep() {
        when (step) {
            Step.TILE_NUMBER -> {
                val lcdc = bus.read(REG_LCDC)
                val y = (line + bus.read(REG_SCY)) and 0xFF
                fineY = y and 7
                val tileRow = y / 8                                    // 0..31
                val tileCol = ((bus.read(REG_SCX) / 8) + fetcherX) and 0x1F
                val mapBase = if (lcdc and LCDC_BG_MAP != 0) 0x1C00 else 0x1800
                tileNumber = bus.readVram(mapBase + tileRow * 32 + tileCol)
                step = Step.DATA_LOW
            }
            Step.DATA_LOW -> {
                dataLow = bus.readVram(tileDataAddress())
                step = Step.DATA_HIGH
            }
            Step.DATA_HIGH -> {
                dataHigh = bus.readVram(tileDataAddress() + 1)
                // The dummy fetch reads but never pushes, and costs no extra dot: it folds into
                // this step's completion. Validated: this is what yields the 172-dot minimum.
                if (!dummyDone) {
                    dummyDone = true
                    step = Step.TILE_NUMBER
                } else {
                    step = Step.PUSH
                }
            }
            Step.PUSH -> Unit   // handled in tick()
        }
    }

    private fun tileDataAddress(): Int {
        val lcdc = bus.read(REG_LCDC)
        return if (lcdc and LCDC_BG_DATA != 0) {
            tileNumber * 16 + fineY * 2                                // 0x8000: unsigned, base 0x0000
        } else {
            0x1000 + tileNumber.toByte().toInt() * 16 + fineY * 2      // 0x8800: signed, base 0x9000
        }
    }

    private fun pushRow(fifo: PixelFifo) {
        // Leftmost pixel first (bit 7). DMG pixel = 2-bit colour index; the palette (BGP) is
        // applied at output time by the shifter, not stored here.
        for (bit in 7 downTo 0) {
            val lo = (dataLow shr bit) and 1
            val hi = (dataHigh shr bit) and 1
            fifo.push((hi shl 1) or lo)
        }
    }

    companion object {
        private const val HALF_FIFO = 8

        private const val REG_LCDC = 0xFF40
        private const val REG_SCY = 0xFF42
        private const val REG_SCX = 0xFF43

        private const val LCDC_BG_MAP = 0x08    // LCDC.3: BG tile map (0 = 0x9800, 1 = 0x9C00)
        private const val LCDC_BG_DATA = 0x10   // LCDC.4: BG tile data (1 = 0x8000, 0 = 0x8800)
    }
}
