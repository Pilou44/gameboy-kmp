package com.wechantloup.gameboykmp.ppu

class Ppu {
    // VRAM 8KB
    private val vram = IntArray(0x2000)

    // OAM 160 bytes (40 sprites × 4 bytes)
    private val oam = IntArray(0xA0)

    // Framebuffer 160×144 pixels (ARGB)
    val frameBuffer = IntArray(160 * 144)

    // Registres LCD
    var lcdc = 0x91  // LCD Control
    var stat = 0x85  // LCD Status
    var scy = 0x00   // Scroll Y
    var scx = 0x00   // Scroll X
    var ly = 0x00    // Current scanline
    var lyc = 0x00   // LY Compare
    var bgp = 0xFC   // BG Palette
    var obp0 = 0xFF  // OBJ Palette 0
    var obp1 = 0xFF  // OBJ Palette 1
    var wy = 0x00    // Window Y
    var wx = 0x00    // Window X

    private var modeClock = 0
    private var mode = 2

    fun step(cycles: Int) {
        modeClock += cycles

        when (mode) {
            // Mode 2 - OAM Search
            // PPU scans OAM to find sprites visible on current scanline.
            // OAM is not accessible to CPU during this mode.
            // Duration: 80 cycles
            2 -> if (modeClock >= 80) {
                modeClock -= 80
                mode = 3
            }

            // Mode 3 - Drawing
            // PPU reads VRAM and renders pixels for the current scanline.
            // Neither VRAM nor OAM are accessible to CPU during this mode.
            // Duration: 172 cycles
            3 -> if (modeClock >= 172) {
                modeClock -= 172
                renderBackground()
                mode = 0
            }

            // Mode 0 - H-Blank
            // Rest period between scanlines.
            // CPU can freely access VRAM and OAM.
            // Duration: 204 cycles
            0 -> if (modeClock >= 204) {
                modeClock -= 204
                ly++
                if (ly == 144) {
                    // All visible scanlines drawn, enter V-Blank
                    mode = 1
                    // TODO: trigger V-Blank interrupt
                } else {
                    // Start next scanline
                    mode = 2
                }
            }

            // Mode 1 - V-Blank
            // PPU has finished drawing all 144 visible lines.
            // Lines 144-153 are off-screen - CPU can safely update VRAM.
            // V-Blank interrupt is triggered at the start of this mode.
            // Duration: 456 cycles × 10 lines (lines 144-153)
            1 -> if (modeClock >= 456) {
                modeClock -= 456
                ly++
                if (ly > 153) {
                    // End of V-Blank, start new frame
                    ly = 0
                    mode = 2
                }
            }
        }
    }

    private fun renderBackground() {
        // Which line in the tile grid? (accounting for vertical scroll)
        val scrolledY = (ly + scy) and 0xFF
        val tileRow = scrolledY / 8        // which row of tiles
        val tilePixelY = scrolledY % 8     // which line within the tile

        for (screenX in 0 until 160) {
            // Which column of tiles? (accounting for horizontal scroll)
            val scrolledX = (screenX + scx) and 0xFF
            val tileCol = scrolledX / 8
            val tilePixelX = scrolledX % 8

            // Read tile index from tile map (0x9800 in VRAM, offset by 0x8000)
            val tileMapAddr = 0x9800 - 0x8000 + tileRow * 32 + tileCol
            val tileIndex = vram[tileMapAddr]

            // Read the 2 bytes encoding the pixel row in the tile
            val tileAddr = tileIndex * 16 + tilePixelY * 2
            val loByte = vram[tileAddr]
            val hiByte = vram[tileAddr + 1]

            // Extract color index from the 2 bytes (bit 7 = leftmost pixel)
            val loBit = (loByte shr (7 - tilePixelX)) and 0x01
            val hiBit = (hiByte shr (7 - tilePixelX)) and 0x01
            val colorIndex = (hiBit shl 1) or loBit

            // Apply background palette (BGP register)
            // Each color index uses 2 bits in BGP
            val gray = (bgp shr (colorIndex * 2)) and 0x03

            // Write pixel to framebuffer
            frameBuffer[ly * 160 + screenX] = grayToColor(gray)
        }
    }

    private fun grayToColor(gray: Int): Int = when (gray) {
        0 -> 0xFF9BBC0F.toInt()  // white  → light green
        1 -> 0xFF8BAC0F.toInt()  // light gray → medium green
        2 -> 0xFF306230.toInt()  // dark gray → dark green
        3 -> 0xFF0F380F.toInt()  // black → very dark green
        else -> 0xFF000000.toInt()
    }
}
