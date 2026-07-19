package com.wechantloup.gameboykmp.ppu

import com.wechantloup.gameboykmp.bus.Bus

/**
 * DMG background fetcher: no per-tile attributes, no VRAM banking, no flips. The three seams are
 * exactly the pre-CGB behaviour, so this path is byte-for-byte the fetcher validated against
 * dmg-acid2 — the refactor to an abstract base changes nothing on DMG.
 *
 * Also used for CGB_COMPAT: a DMG game never writes VBK nor the bank-1 attribute map, so its BG
 * fetch is DMG-shaped (the compat colouring happens later, in the mixer).
 */
class BgFetcherDmg(bus: Bus) : BgFetcher(bus) {

    override fun fetchTile(mapAddr: Int, rawFineY: Int) {
        tileNumber = bus.readVram(mapAddr)   // current bank (pinned to 0 on DMG)
        fineY = rawFineY                      // no Y-flip on DMG
    }

    override fun readTileData(offset: Int): Int = bus.readVram(offset)

    override fun pushRow(fifo: PixelFifo) {
        // Leftmost pixel first (bit 7). DMG pixel = 2-bit colour index; the palette (BGP) is applied
        // at output time by the mixer, not stored here.
        for (bit in 7 downTo 0) {
            val lo = (dataLow shr bit) and 1
            val hi = (dataHigh shr bit) and 1
            fifo.push((hi shl 1) or lo)
        }
    }
}
