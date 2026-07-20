package com.wechantloup.gameboykmp.ppu

import com.wechantloup.gameboykmp.bus.Bus

/**
 * CGB sprite fetcher: palette 0-7 (attribute bits 0-2, via CRAM OBJ), VRAM bank from attribute bit
 * 3, priority from bit 7. Sprite-vs-sprite priority is OAM-index order (OPRI = 0, latched at boot):
 * lowest OAM index wins, X irrelevant. Since sprites are still fetched in X-trigger order, a
 * later-triggered sprite with a lower index must OVERWRITE an already-placed opaque pixel — the
 * fill-if-transparent DMG rule is not enough (verified with the priority micro-sim). Each placed
 * pixel therefore carries the placer's OAM index (CgbPixel merge tag) so the merge can compare.
 *
 * A merged pixel packs (CgbPixel): colour, palette, priority, and the OAM-index merge tag. The
 * mixer reads colour/palette/priority and ignores the tag.
 */
class SpriteFetcherCgb(bus: Bus) : SpriteFetcher(bus) {

    override fun readTileByte(addr: Int): Int =
        bus.readVram((spriteAttributes shr 3) and 1, addr)   // attribute bit 3 selects the VRAM bank

    override fun mergeRow(fifo: PixelFifo, low: Int, high: Int) {
        val xFlip = spriteAttributes and ATTR_X_FLIP != 0
        val paletteField = (spriteAttributes and ATTR_PALETTE) shl CgbPixel.PALETTE_SHIFT
        val priorityField = if (spriteAttributes and ATTR_PRIORITY != 0) CgbPixel.PRIORITY else 0
        val indexField = spriteOamIndex shl CgbPixel.OAM_INDEX_SHIFT
        for (i in 0 until 8) {
            val bit = if (xFlip) i else 7 - i
            val color = (((high shr bit) and 1) shl 1) or ((low shr bit) and 1)
            val pixel = color or paletteField or priorityField or indexField
            if (i < fifo.size) {
                val existing = fifo.peek(i)
                if (existing and CgbPixel.COLOR == 0) {
                    fifo.replace(i, pixel)                    // transparent slot: fill
                } else if (color != 0) {                      // opaque vs opaque: OAM index decides
                    val existingIndex = (existing shr CgbPixel.OAM_INDEX_SHIFT) and CgbPixel.OAM_INDEX_MASK
                    if (spriteOamIndex < existingIndex) fifo.replace(i, pixel)
                }
            } else {
                fifo.push(pixel)
            }
        }
    }

    companion object {
        private const val ATTR_PALETTE = 0x07    // attribute bits 0-2: CGB OBJ palette 0-7
    }
}
