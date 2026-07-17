package com.wechantloup.gameboykmp.ppu

/**
 * One sprite selected by the mode-2 OAM scan. These are pooled (pre-allocated once, reused every
 * line) so building the per-line list costs no allocation. Fields hold raw OAM bytes:
 *   - y    : raw OAM Y = screen Y + 16
 *   - x    : raw OAM X = screen X + 8
 *   - tile : tile number (bit 0 ignored in 8x16 mode)
 *   - attributes : flags — bit 7 OBJ-to-BG priority, bit 6 Y-flip, bit 5 X-flip,
 *                  bit 4 DMG palette (OBP0/OBP1); bits 0-3 are CGB-only (palette, bank).
 *   - oamIndex : position in OAM (0..39), used to break priority ties.
 *
 * TODO (his call): if the packed-Int representation is preferred over this holder, swap it here;
 *  nothing outside the scan/fetch touches these fields.
 */
class Sprite {
    var y = 0
    var x = 0
    var tile = 0
    var attributes = 0
    var oamIndex = 0
}
