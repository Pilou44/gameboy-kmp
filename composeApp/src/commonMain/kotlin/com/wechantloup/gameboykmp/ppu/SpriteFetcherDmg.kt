package com.wechantloup.gameboykmp.ppu

import com.wechantloup.gameboykmp.bus.Bus

/**
 * DMG sprite fetcher: OBP0/OBP1 palette (attribute bit 4), no VRAM banking. Priority emerges from
 * fill-if-transparent + the X-triggered fetch order (smaller X wins, ties by OAM order). This is
 * the pre-CGB behaviour, byte-for-byte — dmg-acid2 stays green by construction.
 *
 * Also used for CGB_COMPAT (a DMG game keeps DMG-shaped sprites; compat colouring is in the mixer).
 *
 * A merged pixel packs: bits 0-1 colour (0 = transparent), bit 2 palette (0 = OBP0, 1 = OBP1),
 * bit 3 the OBJ-to-BG priority flag. BGP/OBP are applied later, at mix time.
 */
class SpriteFetcherDmg(bus: Bus) : SpriteFetcher(bus) {

    override fun readTileByte(addr: Int): Int = bus.readVram(addr)

    override fun mergeRow(fifo: PixelFifo, low: Int, high: Int) {
        val xFlip = spriteAttributes and ATTR_X_FLIP != 0
        val paletteBit = if (spriteAttributes and ATTR_PALETTE != 0) PIXEL_PALETTE else 0
        val priorityBit = if (spriteAttributes and ATTR_PRIORITY != 0) PIXEL_PRIORITY else 0
        for (i in 0 until 8) {
            val bit = if (xFlip) i else 7 - i                 // pixel i, leftmost = 0
            val color = (((high shr bit) and 1) shl 1) or ((low shr bit) and 1)
            val pixel = color or paletteBit or priorityBit
            if (i < fifo.size) {
                if (fifo.peek(i) and PIXEL_COLOR == 0) fifo.replace(i, pixel)
            } else {
                fifo.push(pixel)
            }
        }
    }

    companion object {
        private const val ATTR_PALETTE = 0x10    // attribute bit 4: 0 = OBP0, 1 = OBP1 (DMG)

        // Packed DMG sprite-pixel layout (shared with mixDmg in Ppu).
        const val PIXEL_COLOR = 0x03
        const val PIXEL_PALETTE = 0x04
        const val PIXEL_PRIORITY = 0x08
    }
}
