object CgbPixel {
    const val COLOR = 0x03
    const val PALETTE_SHIFT = 2      // bits 2-4: CGB palette 0-7
    const val PRIORITY = 0x20        // bit 5: attribute bit 7 (BG: BG-over-OBJ; OBJ: OBJ-behind-BG)

    // Sprite-only FIFO merge tag: the OAM index (0-39) of the sprite that placed the pixel. Rides
    // in the pixel Int so it stays aligned through the sliding sprite FIFO for free; the mixer never
    // reads it. Models the per-pixel priority the hardware OBJ FIFO must carry to resolve CGB
    // OAM-priority when sprites arrive out of index order (X-triggered fetch).
    const val OAM_INDEX_SHIFT = 6    // bits 6-11
    const val OAM_INDEX_MASK = 0x3F
}
