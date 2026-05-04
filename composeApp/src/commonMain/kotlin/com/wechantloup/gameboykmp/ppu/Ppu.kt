package com.wechantloup.gameboykmp.ppu

import com.wechantloup.gameboykmp.bus.Bus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class Ppu(
    private val bus: Bus,
) {
    // Initialize with DMG white (lightest green) so the screen is visible immediately
    private val _frameFlow = MutableStateFlow(IntArray(160 * 144))
    val frameFlow: StateFlow<IntArray> = _frameFlow

    val frameBuffer = IntArray(160 * 144)
    val bgColorIndexBuffer = IntArray(160 * 144)

    private var ly = 0
    private var modeClock = 0
    private var mode = 2
    private var windowLine = 0

    fun step(cycles: Int) {
        val lcdc = bus.read(0xFF40)

        modeClock += cycles

        when (mode) {
            // Mode 2 - OAM Search
            // PPU scans OAM to find sprites visible on current scanline.
            // OAM is not accessible to CPU during this mode.
            // Duration: 80 cycles
            2 -> if (modeClock >= 80) {
                modeClock -= 80
                mode = 3
                updateStat(3)
            }

            // Mode 3 - Drawing
            // PPU reads VRAM and renders pixels for the current scanline.
            // Neither VRAM nor OAM are accessible to CPU during this mode.
            // Duration: 172 cycles
            3 -> if (modeClock >= 172) {
                modeClock -= 172
                renderScanline(lcdc)
                mode = 0
                updateStat(0)
            }

            // Mode 0 - H-Blank
            // Rest period between scanlines.
            // CPU can freely access VRAM and OAM.
            // Duration: 204 cycles
            0 -> if (modeClock >= 204) {
                modeClock -= 204
                ly++
                bus.write(0xFF44, ly)
                checkLyc()
                if (ly == 144) {
                    mode = 1
                    windowLine = 0
                    updateStat(1)
                    bus.setIF(bus.iF or 0x01)  // V-Blank interrupt
                    _frameFlow.value = frameBuffer.copyOf()
                } else {
                    mode = 2
                    updateStat(2)
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
                bus.write(0xFF44, ly)
                checkLyc()
                if (ly > 153) {
                    ly = 0
                    bus.write(0xFF44, 0)
                    checkLyc()
                    modeClock = 0
                    mode = 2
                    updateStat(2)
                }
            }
        }
    }

    private fun updateStat(newMode: Int) {
        val stat = bus.read(0xFF41)
        bus.write(0xFF41, (stat and 0xFC) or (newMode and 0x03))

        // Trigger STAT IRQ if the corresponding enable bit is set
        val irqBit = when (newMode) {
            0 -> 0x08  // H-Blank
            1 -> 0x10  // V-Blank
            2 -> 0x20  // OAM
            else -> 0
        }
        if (irqBit != 0 && stat and irqBit != 0) {
            bus.setIF(bus.iF or 0x02)
        }
    }

    private fun checkLyc() {
        val lyc = bus.read(0xFF45)
        val stat = bus.read(0xFF41)
        if (ly == lyc) {
            bus.write(0xFF41, stat or 0x04)  // Set coincidence flag
            if (stat and 0x40 != 0) {        // LYC=LY interrupt enabled?
                bus.setIF(bus.iF or 0x02)
            }
        } else {
            bus.write(0xFF41, stat and 0x04.inv())
        }
    }

    private fun renderScanline(lcdc: Int) {
        // Reset BG color index buffer for this scanline before rendering
        for (x in 0 until 160) bgColorIndexBuffer[ly * 160 + x] = 0

        if (lcdc and 0x80 == 0) {
            // LCD off: fill scanline with white
            for (x in 0 until 160) frameBuffer[ly * 160 + x] = 0
            return
        }
        if (lcdc and 0x01 != 0) renderBackground(lcdc)
        if (lcdc and 0x20 != 0) renderWindow(lcdc)
        if (lcdc and 0x02 != 0) renderSprites(lcdc)
    }

    private fun renderSprites(lcdc: Int) {
        // squareSprite
        // true for 8x16
        // false for 8x8
        val squareSprite = lcdc and 0x04 == 0
        val spriteHeight = if (squareSprite) 8 else 16

        var spriteCounter = 0
        var spriteIndexesToDisplay = mutableListOf<Int>()
        for (spriteIndex in 0..39) {
            val positionY = bus.readOam(spriteIndex * 4)

            val isSpriteOnLine = ly >= positionY - 16 && ly < positionY - 16 + spriteHeight // sprite is displayed

            // Max 10 sprites per line
            if (isSpriteOnLine && spriteCounter < 10) {
                spriteCounter++
                spriteIndexesToDisplay.add(spriteIndex)
            }
        }

        spriteIndexesToDisplay = spriteIndexesToDisplay
            .reversed()
            .sortedByDescending { bus.readOam(it * 4 + 1) }
            .toMutableList()
        for (spriteIndex in spriteIndexesToDisplay) {
            val positionY = bus.readOam(spriteIndex * 4)

            // Sprite attributes (byte 3 of OAM):
            // bit 7 — BG priority: 0=sprite in front of background, 1=sprite behind background
            // bit 6 — Y flip: 0=normal, 1=sprite flipped vertically
            // bit 5 — X flip: 0=normal, 1=sprite flipped horizontally
            // bit 4 — Palette: 0=OBP0 (0xFF48), 1=OBP1 (0xFF49)
            // bits 3-0 — unused on DMG
            val spriteAttributes = bus.readOam(spriteIndex * 4 + 3)
            val flipY = spriteAttributes and 0x40 > 0
            val flipX = spriteAttributes and 0x20 > 0
            val bgPriority = spriteAttributes and 0x80 > 0
            val paletteAddress = if (spriteAttributes and 0x10 > 0) 0xFF49 else 0xFF48

            val tileRow = if (!flipY) {
                ly - (positionY - 16)
            } else {
                spriteHeight - 1 - (ly - (positionY - 16))
            }

            var tileIndex = bus.readOam(spriteIndex * 4 + 2)
            if (!squareSprite) tileIndex = if (tileRow < 8) {
                tileIndex and 0xFE
            } else {
                tileIndex or 0x01
            }

            val adjustedTileRow = if (tileRow >= 8) {
                tileRow - 8
            } else {
                tileRow
            }

            val tileDataAddr = tileIndex * 16 + adjustedTileRow * 2

            val loByte = bus.readVram(tileDataAddr)
            val hiByte = bus.readVram(tileDataAddr + 1)

            val positionX = bus.readOam(spriteIndex * 4 + 1)

            for (pixelIndexX in 0 until 8) {
                val pixelX = if (flipX) 7 - pixelIndexX else pixelIndexX
                val screenX = positionX - 8 + pixelIndexX
                if (screenX < 0) continue
                if (screenX >= 160) continue

                val loBit = (loByte shr (7 - pixelX)) and 0x01
                val hiBit = (hiByte shr (7 - pixelX)) and 0x01
                val colorIndex = (hiBit shl 1) or loBit

                if (colorIndex == 0) continue // Do not display transparent color

                val bgp = bus.read(paletteAddress)
                val gray = (bgp shr (colorIndex * 2)) and 0x03

                if (!bgPriority || bgColorIndexBuffer[ly * 160 + screenX] == 0) {
                    frameBuffer[ly * 160 + screenX] = gray
                }
            }
        }
    }

    private fun renderWindow(lcdc: Int) {
        val wx = bus.read(0xFF4B)
        val wy = bus.read(0xFF4A)
        val bgp = bus.read(0xFF47)

        if (ly < wy) return
        if (wx - 7 >= 160) return

        val tileRow = windowLine / 8
        val tilePixelY = windowLine % 8

        // Bit 6: Window tile map — 0=0x9800, 1=0x9C00
        val tileMapBase = if (lcdc and 0x40 != 0) 0x1C00 else 0x1800

        // Bit 4: Tile data area — 1=0x8000 (unsigned), 0=0x8800 (signed, base at 0x9000)
        val unsignedTileData = lcdc and 0x10 != 0

        val startScreenX = maxOf(0, wx - 7)
        for (screenX in startScreenX until 160) {
            val windowX = screenX - (wx - 7)
            val tileCol = windowX / 8
            val tilePixelX = windowX % 8

            val tileMapAddr = tileMapBase + tileRow * 32 + tileCol
            val tileIndex = bus.readVram(tileMapAddr)

            // Compute tile data address in VRAM
            val tileDataAddr = if (unsignedTileData) {
                tileIndex * 16 + tilePixelY * 2          // 0x8000-based, unsigned
            } else {
                0x1000 + tileIndex.toByte().toInt() * 16 + tilePixelY * 2  // 0x9000-based, signed
            }

            val loByte = bus.readVram(tileDataAddr)
            val hiByte = bus.readVram(tileDataAddr + 1)

            val loBit = (loByte shr (7 - tilePixelX)) and 0x01
            val hiBit = (hiByte shr (7 - tilePixelX)) and 0x01
            val colorIndex = (hiBit shl 1) or loBit

            val gray = (bgp shr (colorIndex * 2)) and 0x03
            frameBuffer[ly * 160 + screenX] = gray
            bgColorIndexBuffer[ly * 160 + screenX] = colorIndex
        }

        windowLine++
    }

    private fun renderBackground(lcdc: Int) {
        val scy = bus.read(0xFF42)
        val scx = bus.read(0xFF43)
        val bgp = bus.read(0xFF47)

        // Bit 3: BG tile map — 0=0x9800, 1=0x9C00
        val tileMapBase = if (lcdc and 0x08 != 0) 0x1C00 else 0x1800  // VRAM offsets

        // Bit 4: Tile data area — 1=0x8000 (unsigned), 0=0x8800 (signed, base at 0x9000)
        val unsignedTileData = lcdc and 0x10 != 0

        val scrolledY = (ly + scy) and 0xFF
        val tileRow = scrolledY / 8
        val tilePixelY = scrolledY % 8

        for (screenX in 0 until 160) {
            val scrolledX = (screenX + scx) and 0xFF
            val tileCol = scrolledX / 8
            val tilePixelX = scrolledX % 8

            val tileMapAddr = tileMapBase + tileRow * 32 + tileCol
            val tileIndex = bus.readVram(tileMapAddr)

            // Compute tile data address in VRAM
            val tileDataAddr = if (unsignedTileData) {
                tileIndex * 16 + tilePixelY * 2          // 0x8000-based, unsigned
            } else {
                0x1000 + tileIndex.toByte().toInt() * 16 + tilePixelY * 2  // 0x9000-based, signed
            }

            val loByte = bus.readVram(tileDataAddr)
            val hiByte = bus.readVram(tileDataAddr + 1)

            val loBit = (loByte shr (7 - tilePixelX)) and 0x01
            val hiBit = (hiByte shr (7 - tilePixelX)) and 0x01
            val colorIndex = (hiBit shl 1) or loBit

            val gray = (bgp shr (colorIndex * 2)) and 0x03
            frameBuffer[ly * 160 + screenX] = gray
            bgColorIndexBuffer[ly * 160 + screenX] = colorIndex
        }
    }
}
