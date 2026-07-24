package com.wechantloup.gameboykmp.ppu

import com.wechantloup.gameboykmp.bus.Bus

/**
 * Sprite fetcher backbone (hardware-agnostic). When the shifter reaches a sprite's start X, the PPU
 * pauses the BG fetcher and runs one of these: it reads the sprite's tile row from VRAM and merges
 * 8 pixels into the sprite FIFO. It talks only to the Bus.
 *
 * The tile-row address (8x8 / 8x16 selection, Y-flip, fine-Y) is computed here — identical on both
 * hardwares. Two seams differ:
 *   - readTileByte(addr) : the VRAM bank (bank 0 on DMG; attribute bit 3 on CGB).
 *   - mergeRow(fifo,...) : packing + the sprite-vs-sprite priority rule (fill-if-transparent on DMG;
 *                          overwrite-if-lower-OAM-index on CGB).
 *
 * TODO: the sprite fetch duration and the BG-fetch abort penalty are stand-ins; pin against mooneye
 *  intr_2_mode0_timing_sprites + Mealybug with the simulator. Sprites with X < 8 (clipped on the
 *  left edge) are not handled yet.
 */
abstract class SpriteFetcher(protected val bus: Bus) {

    private var dotsLeft = 0
    protected var line = 0
    private var spriteY = 0
    private var spriteTile = 0
    protected var spriteAttributes = 0
    protected var spriteOamIndex = 0   // used by the CGB merge; latched for both, read on CGB only

    /** Begins a fetch for [sprite] on scanline [line]. */
    fun start(sprite: Sprite, line: Int) {
        this.line = line
        spriteY = sprite.y
        spriteTile = sprite.tile
        spriteAttributes = sprite.attributes
        spriteOamIndex = sprite.oamIndex
        // The fetch occupies SPRITE_FETCH_DOTS dots in total, and the dot that triggers it is already
        // the first of them: the shifter is stalled on that dot (no pixel output), so only the remaining
        // dots are counted here. Measured against intr_2_mode0_timing_sprites: 6 dots per sprite.
        dotsLeft = SPRITE_FETCH_DOTS - 1
    }

    /** Advances the fetch one dot. Returns true on the dot it completes (pixels merged into [fifo]). */
    fun tick(fifo: PixelFifo): Boolean {
        dotsLeft--
        if (dotsLeft > 0) return false
        fetchAndMerge(fifo)
        return true
    }

    private fun fetchAndMerge(fifo: PixelFifo) {
        val tall = bus.read(REG_LCDC) and LCDC_OBJ_SIZE != 0
        val height = if (tall) 16 else 8
        var row = (line + 16) - spriteY                       // 0..height-1
        if (spriteAttributes and ATTR_Y_FLIP != 0) row = height - 1 - row
        val tileIndex = if (tall) (spriteTile and 0xFE) or (row shr 3) else spriteTile
        val fineY = row and 7
        val addr = tileIndex * 16 + fineY * 2                 // sprites always use 0x8000 addressing
        val low = readTileByte(addr)                          // seam: VRAM bank
        val high = readTileByte(addr + 1)
        mergeRow(fifo, low, high)                             // seam: pack + priority rule
    }

    /** Reads a sprite tile-data byte at [addr] (bank 0 on DMG; attribute-selected bank on CGB). */
    protected abstract fun readTileByte(addr: Int): Int

    /** Packs the 8 pixels from [low]/[high] and merges them into [fifo] per the hardware's rule. */
    protected abstract fun mergeRow(fifo: PixelFifo, low: Int, high: Int)

    companion object {
        // TODO: stand-in duration; pin against the sprite-timing oracles + simulator.
        private const val SPRITE_FETCH_DOTS = 6

        private const val REG_LCDC = 0xFF40
        private const val LCDC_OBJ_SIZE = 0x04   // LCDC.2: 0 = 8x8, 1 = 8x16

        protected const val ATTR_PRIORITY = 0x80 // attribute bit 7: OBJ-behind-BG
        protected const val ATTR_Y_FLIP = 0x40   // attribute bit 6
        protected const val ATTR_X_FLIP = 0x20   // attribute bit 5
    }
}
