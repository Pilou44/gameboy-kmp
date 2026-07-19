package com.wechantloup.gameboykmp.ppu

/**
 * Packed pixel layout for CGB (shared by the BG fetcher, the sprite fetcher and the mixer — a
 * single source of truth for the bit layout). One pixel fits in an Int:
 *   - bits 0-1 : colour index 0-3 (0 = transparent for sprites; a real colour for BG)
 *   - bits 2-4 : CGB palette 0-7
 *   - bit 5    : priority flag = attribute bit 7. Its meaning is layer-dependent and resolved in
 *                the mixer: for BG it is "BG-over-OBJ"; for a sprite it is "OBJ-behind-BG".
 *
 * The BG/sprite distinction is known from which FIFO a pixel came from, so it needs no bit here.
 */
object CgbPixel {
    const val COLOR = 0x03
    const val PALETTE_SHIFT = 2
    const val PRIORITY = 0x20   // bit 5
}
