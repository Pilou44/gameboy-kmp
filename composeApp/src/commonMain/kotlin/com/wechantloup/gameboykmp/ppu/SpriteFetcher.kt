package com.wechantloup.gameboykmp.ppu

import com.wechantloup.gameboykmp.bus.Bus

/**
 * Sprite fetcher (DMG). When the shifter reaches a sprite's start X, the PPU pauses the BG fetcher
 * and runs one of these: it reads the sprite's tile row from VRAM and merges 8 pixels into the
 * sprite FIFO. Like the BG fetcher, it talks only to the Bus.
 *
 * Sprite-vs-sprite priority is resolved by the MERGE, not by a sort: pixels are written only into
 * FIFO slots that are still transparent. Because sprites trigger in X order (smaller X reaches the
 * shifter first) and ties are scanned in OAM order, the first sprite to cover a pixel keeps it —
 * which is exactly the DMG rule (smaller X wins, then lower OAM index).
 *
 * A merged sprite pixel packs: bits 0-1 colour (0 = transparent), bit 2 palette (0 = OBP0,
 * 1 = OBP1), bit 3 the OBJ-to-BG priority flag (attribute bit 7). BGP/OBP are applied later, at
 * mix time, so a mid-line palette write is honoured natively.
 *
 * TODO: the sprite fetch duration and the BG-fetch abort penalty (a sprite interrupting a BG fetch
 *  mid-step) are stand-ins here; pin them against mooneye intr_2_mode0_timing_sprites + Mealybug
 *  with the simulator. Sprites with X < 8 (clipped on the left edge) are not handled yet.
 */
class SpriteFetcher(private val bus: Bus) {

    private var dotsLeft = 0
    private var line = 0
    private var spriteY = 0
    private var spriteTile = 0
    private var spriteAttributes = 0

    /** Begins a fetch for [sprite] on scanline [line]. */
    fun start(sprite: Sprite, line: Int) {
        this.line = line
        spriteY = sprite.y
        spriteTile = sprite.tile
        spriteAttributes = sprite.attributes
        dotsLeft = SPRITE_FETCH_DOTS
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
        val low = bus.readVram(addr)
        val high = bus.readVram(addr + 1)

        val xFlip = spriteAttributes and ATTR_X_FLIP != 0
        val paletteBit = if (spriteAttributes and ATTR_PALETTE != 0) PIXEL_PALETTE else 0
        val priorityBit = if (spriteAttributes and ATTR_PRIORITY != 0) PIXEL_PRIORITY else 0

        for (i in 0 until 8) {
            val bit = if (xFlip) i else 7 - i                 // pixel i, leftmost = 0
            val color = (((high shr bit) and 1) shl 1) or ((low shr bit) and 1)
            val pixel = color or paletteBit or priorityBit
            // Fill-if-transparent merge (see class doc): existing opaque pixels are kept.
            if (i < fifo.size) {
                if (fifo.peek(i) and PIXEL_COLOR == 0) fifo.replace(i, pixel)
            } else {
                fifo.push(pixel)
            }
        }
    }

    companion object {
        // TODO: stand-in duration; pin against the sprite-timing oracles + simulator.
        private const val SPRITE_FETCH_DOTS = 6

        private const val REG_LCDC = 0xFF40
        private const val LCDC_OBJ_SIZE = 0x04   // LCDC.2: 0 = 8x8, 1 = 8x16

        private const val ATTR_PRIORITY = 0x80   // attribute bit 7: 1 = behind BG colours 1-3
        private const val ATTR_Y_FLIP = 0x40     // attribute bit 6
        private const val ATTR_X_FLIP = 0x20     // attribute bit 5
        private const val ATTR_PALETTE = 0x10    // attribute bit 4: 0 = OBP0, 1 = OBP1 (DMG)

        // Packed sprite-pixel layout (shared with the mixer in Ppu).
        const val PIXEL_COLOR = 0x03
        const val PIXEL_PALETTE = 0x04
        const val PIXEL_PRIORITY = 0x08
    }
}
