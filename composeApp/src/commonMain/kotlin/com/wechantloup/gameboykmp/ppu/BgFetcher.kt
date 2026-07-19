package com.wechantloup.gameboykmp.ppu

import com.wechantloup.gameboykmp.bus.Bus

/**
 * Background fetcher backbone (hardware-agnostic). Produces 8 BG pixels per cycle into a PixelFifo,
 * driven one dot at a time by the PPU during mode 3. It talks only to the Bus.
 *
 * This base owns the dot-accurate machine validated against dmg-acid2 and hblank_ly_scx_timing:
 * four steps of 2 dots each (tile number -> data low -> data high -> push), the discarded "dummy"
 * first fetch folded into the data-high step (no extra dot), and the push gating (only when the
 * FIFO has room for 8 more) that couples the fetch rate to the shifter drain and yields mode 3 =
 * 172 + (SCX & 7) dots for pure BG. None of that timing logic lives in the subclasses.
 *
 * Exactly three seams differ between hardware and are left abstract; each fires at a step boundary
 * (a few times per tile), never per dot, so the timing model is identical for both:
 *   - fetchTile(mapAddr, rawFineY) : latch the tile number (and, on CGB, the bank-1 attribute).
 *   - readTileData(offset)         : read a tile-data byte (current bank on DMG, attr bank on CGB).
 *   - pushRow(fifo)                : pack and push the 8 pixels (colour only on DMG; colour +
 *                                    palette + priority, with X-flip, on CGB).
 *
 * The map address and the raw fine-Y row are computed here (identical on both hardwares) and passed
 * into fetchTile; the subclass only decides how to read/latch. Registers are read LIVE at each
 * step, so mid-line writes to SCX/SCY/LCDC take effect natively (Mealybug), no compensation layer.
 */
abstract class BgFetcher(protected val bus: Bus) {

    private enum class Step { TILE_NUMBER, DATA_LOW, DATA_HIGH, PUSH }

    private var step = Step.TILE_NUMBER
    private var firstDot = false    // each fetch step spans 2 dots
    private var dummyDone = false   // the first fetch of the line is discarded
    private var fetcherX = 0        // tile column within the line: 0, 1, 2, ...
    private var line = 0            // scanline being drawn, latched at reset
    private var window = false      // fetching the window map instead of the BG map
    private var windowLine = 0      // the window's own Y counter, latched at startWindow

    // The only mutable state crossing the inheritance boundary: written by the seams, read by the
    // base (tileDataAddress / pushRow). Kept as small as possible.
    protected var tileNumber = 0
    protected var fineY = 0         // row within the tile; Y-flip already applied on CGB
    protected var dataLow = 0
    protected var dataHigh = 0

    /** Resets for the start of a line's mode 3 (background mode). */
    fun reset(line: Int) {
        step = Step.TILE_NUMBER
        firstDot = false
        dummyDone = false
        fetcherX = 0
        window = false
        this.line = line
    }

    /**
     * Switches to the window map mid-line: same fetcher pointed at LCDC.6, Y from the window's own
     * counter, X from 0 (no SCX/SCY). The caller clears the BG FIFO first; the refill is the window
     * trigger's cost. No dummy fetch on the restart.
     * TODO: the exact restart penalty is a stand-in; pin against Mealybug window timing.
     */
    fun startWindow(windowLine: Int) {
        step = Step.TILE_NUMBER
        firstDot = false
        dummyDone = true
        fetcherX = 0
        window = true
        this.windowLine = windowLine
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
                // Map address + raw fine-Y are hardware-agnostic; compute here, latch in the seam.
                val lcdc = bus.read(REG_LCDC)
                val mapAddr: Int
                val rawFineY: Int
                if (window) {
                    rawFineY = windowLine and 7
                    val tileRow = windowLine / 8
                    val tileCol = fetcherX and 0x1F
                    val mapBase = if (lcdc and LCDC_WINDOW_MAP != 0) 0x1C00 else 0x1800
                    mapAddr = mapBase + tileRow * 32 + tileCol
                } else {
                    val y = (line + bus.read(REG_SCY)) and 0xFF
                    rawFineY = y and 7
                    val tileRow = y / 8                                // 0..31
                    val tileCol = ((bus.read(REG_SCX) / 8) + fetcherX) and 0x1F
                    val mapBase = if (lcdc and LCDC_BG_MAP != 0) 0x1C00 else 0x1800
                    mapAddr = mapBase + tileRow * 32 + tileCol
                }
                fetchTile(mapAddr, rawFineY)   // seam: latch tileNumber + fineY (+ CGB attribute)
                step = Step.DATA_LOW
            }
            Step.DATA_LOW -> {
                dataLow = readTileData(tileDataAddress())
                step = Step.DATA_HIGH
            }
            Step.DATA_HIGH -> {
                dataHigh = readTileData(tileDataAddress() + 1)
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

    /**
     * 0x8000 unsigned / 0x8800 signed addressing (LCDC.4). Identical on both hardwares; only the
     * VRAM bank the bytes are read from differs, and that is the subclass's readTileData concern.
     */
    protected fun tileDataAddress(): Int {
        val lcdc = bus.read(REG_LCDC)
        return if (lcdc and LCDC_BG_DATA != 0) {
            tileNumber * 16 + fineY * 2                                // 0x8000: unsigned, base 0x0000
        } else {
            0x1000 + tileNumber.toByte().toInt() * 16 + fineY * 2      // 0x8800: signed, base 0x9000
        }
    }

    // ----- hardware-specific seams -----

    /** Latch [tileNumber] (and, on CGB, the bank-1 attribute), and set [fineY] from [rawFineY]
     *  (applying Y-flip on CGB). [mapAddr] is the tilemap offset within a VRAM bank. */
    protected abstract fun fetchTile(mapAddr: Int, rawFineY: Int)

    /** Read one tile-data byte at [offset] within VRAM (current bank on DMG, attribute-selected
     *  bank on CGB). */
    protected abstract fun readTileData(offset: Int): Int

    /** Pack and push the 8 pixels of the fetched row into [fifo], leftmost first. */
    protected abstract fun pushRow(fifo: PixelFifo)

    companion object {
        private const val HALF_FIFO = 8

        private const val REG_LCDC = 0xFF40
        private const val REG_SCY = 0xFF42
        private const val REG_SCX = 0xFF43

        private const val LCDC_BG_MAP = 0x08     // LCDC.3: BG tile map (0 = 0x9800, 1 = 0x9C00)
        private const val LCDC_WINDOW_MAP = 0x40 // LCDC.6: window tile map (0 = 0x9800, 1 = 0x9C00)
        private const val LCDC_BG_DATA = 0x10    // LCDC.4: BG tile data (1 = 0x8000, 0 = 0x8800)
    }
}
