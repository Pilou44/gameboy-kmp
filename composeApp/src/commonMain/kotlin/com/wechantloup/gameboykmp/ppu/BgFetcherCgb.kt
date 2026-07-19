package com.wechantloup.gameboykmp.ppu

import com.wechantloup.gameboykmp.bus.Bus

/**
 * CGB background fetcher: adds the bank-1 attribute per tile. On CGB the tile *number* is always in
 * VRAM bank 0 and the *attribute* at the same tilemap offset in bank 1 — read via explicit-bank
 * accessors because the CPU-visible VBK is not a reliable indicator during rendering. The attribute
 * selects the tile-data bank (bit 3), the CGB palette (bits 0-2), the flips (bits 5/6) and the
 * BG-over-OBJ priority (bit 7). The attribute is read within the same TILE_NUMBER step as the tile
 * number (both come from one VRAM fetch on hardware), so it costs no extra dot: the validated
 * 172 + (SCX & 7) timing is unchanged.
 *
 * The window uses the same seam: its attribute is at the window map offset in bank 1, so Y/X-flip
 * and palette apply to window tiles too, with no special-casing here.
 */
class BgFetcherCgb(bus: Bus) : BgFetcher(bus) {

    private var palette = 0         // attribute bits 0-2: CGB BG palette 0-7
    private var dataBank = 0        // attribute bit 3: VRAM bank of the tile data
    private var xFlip = false       // attribute bit 5
    private var bgPriority = false  // attribute bit 7: BG-over-OBJ priority

    override fun fetchTile(mapAddr: Int, rawFineY: Int) {
        tileNumber = bus.readVram(0, mapAddr)          // tile number is always in bank 0
        val attr = bus.readVram(1, mapAddr)            // attribute is the same offset in bank 1
        palette = attr and ATTR_PALETTE
        dataBank = (attr shr 3) and 1
        xFlip = attr and ATTR_X_FLIP != 0
        bgPriority = attr and ATTR_PRIORITY != 0
        val yFlip = attr and ATTR_Y_FLIP != 0
        fineY = if (yFlip) 7 - rawFineY else rawFineY
    }

    override fun readTileData(offset: Int): Int = bus.readVram(dataBank, offset)

    override fun pushRow(fifo: PixelFifo) {
        // Leftmost pixel first; X-flip reverses the bit order. Each pixel carries colour + CGB
        // palette + BG priority (see CgbPixel); the CRAM lookup and priority resolution happen in
        // the mixer.
        val paletteField = palette shl CgbPixel.PALETTE_SHIFT
        val priorityField = if (bgPriority) CgbPixel.PRIORITY else 0
        for (i in 0 until 8) {
            val bit = if (xFlip) i else 7 - i
            val lo = (dataLow shr bit) and 1
            val hi = (dataHigh shr bit) and 1
            val color = (hi shl 1) or lo
            fifo.push(color or paletteField or priorityField)
        }
    }

    companion object {
        private const val ATTR_PALETTE = 0x07    // bits 0-2
        private const val ATTR_X_FLIP = 0x20     // bit 5
        private const val ATTR_Y_FLIP = 0x40     // bit 6
        private const val ATTR_PRIORITY = 0x80   // bit 7: BG-over-OBJ
    }
}
