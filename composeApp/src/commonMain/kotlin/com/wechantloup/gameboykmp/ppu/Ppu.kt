package com.wechantloup.gameboykmp.ppu

import com.wechantloup.gameboykmp.MachineMode
import com.wechantloup.gameboykmp.bus.Bus
import kotlinx.coroutines.channels.Channel

class Ppu(
    private val bus: Bus,
) {
    val frameChannel = Channel<IntArray>(Channel.CONFLATED)

    val frameBuffer = IntArray(160 * 144)
    val bgColorIndexBuffer = IntArray(160 * 144)

    private var ly = 0
    private var modeClock = 0
    private var mode = 2
    private var statMode = 2
    private var pendingStatMode: Int? = null
    private var windowLine = 0
    private var lcdWasOn = true
    private var isFirstScanline = false
    private var mode0Duration = 204
    private var mode3Duration = 172
    private var statLine = false
    private var lcdOnDot = 0
    private var firstFrameAfterLcdOn = false

    private val bgpDots = IntArray(200)
    private val bgpVals = IntArray(200)
    private var bgpCount = 0

    private var ly153Wrapped = false

    init {
        bus.onStatWrite = { refreshStatInterrupt() }
        bus.onLycWrite = {
            updateLycFlag()
            refreshStatInterrupt()
        }
        bus.ppuSampler = sampler@{ addr ->

            if (!firstFrameAfterLcdOn) return@sampler null

            val s = PpuTiming.sample(lcdOnDot, bus.readRaw(0xFF45))

            when (addr) {
                0xFF41 -> (bus.readRaw(0xFF41) and 0x78) or s.stat
                0xFF44 -> s.ly
                in 0xFE00..0xFE9F -> when {
                    s.oamBlocked    -> 0xFF
                    bus.isDmaActive -> 0xFF                    // OAM inaccessible pendant DMA
                    else            -> bus.readOam(addr - 0xFE00)   // modèle dot seul, bypass ppuMode
                }
                in 0x8000..0x9FFF -> if (s.vramBlocked) 0xFF else bus.readVram(addr - 0x8000)
                else -> null
            }
        }
        bus.ppuWriteIntercept = wi@{ addr, v ->
            if (!firstFrameAfterLcdOn) return@wi false          // hors 1re frame → write() normal
            when (addr) {
                in 0xFE00..0xFE9F -> {
                    if (!PpuTiming.oamWriteBlocked(lcdOnDot) && !bus.isDmaActive) bus.writeOam(addr - 0xFE00, v)
                    true                                         // géré (drop OU écrit) → court-circuite ppuMode
                }
                in 0x8000..0x9FFF -> {
                    if (!PpuTiming.vramWriteBlocked(lcdOnDot)) bus.writeVram(addr - 0x8000, v)
                    true
                }
                else -> false
            }
        }
        bus.onBgpWrite = { v ->
            if (mode == 3 && bgpCount < bgpDots.size) {
                bgpDots[bgpCount] = modeClock   // dot du mode 3 au moment de l'écriture
                bgpVals[bgpCount] = v
                bgpCount++
            }
        }
    }

    fun step(cycles: Int) {
        pendingStatMode?.let { m ->
            statMode = m
            bus.ppuMode = m
            val stat = bus.readRaw(0xFF41)
            bus.writeRaw(0xFF41, (stat and 0xFC) or (m and 0x03))
            refreshStatInterrupt()
            pendingStatMode = null
        }

        val lcdc = bus.read(0xFF40)
        if (firstFrameAfterLcdOn) lcdOnDot += cycles

        if (lcdc and 0x80 == 0) {
            // LCD off
            if (lcdWasOn) {
                lcdWasOn = false
                ly153Wrapped = false
                firstFrameAfterLcdOn = false
                ly = 0
                modeClock = 0
                mode = 2
                windowLine = 0
                bus.ppuMode = 0 // OAM and VRAM accessible when LCD is off

                // fill scanline with white
                frameBuffer.fill(0)
                frameChannel.trySend(frameBuffer.copyOf())

                bus.write(0xFF44, ly)

                // set STAT mode bits to 0 (H-Blank) when LCD turns off
                // Clear the STAT mode bits (mode reads 0 while off) but KEEP the
                // LYC coincidence flag (bit 2): turning the LCD off freezes the
                // comparison, it does not reset the flag.
                val stat = bus.read(0xFF41)
                bus.writeRaw(0xFF41, stat and 0xFC)
                // statLine is intentionally NOT reset here: the interrupt line is
                // frozen at its current value, so re-enabling the LCD only fires
                // an interrupt if the comparison result actually changes.
            }
            return
        } else if (!lcdWasOn) {
            lcdOnDot = 0
            firstFrameAfterLcdOn = true

            lcdWasOn = true
            ly = 0
            modeClock = 0
            mode = 0           // Line 0 starts in mode 0, skipping mode 2
            isFirstScanline = true
            mode0Duration = 80 // Short initial mode 0: 80 T-cycles before mode 3
            bus.ppuMode = 0
            updateLycFlag()    // comparison clock resumes: re-evaluate LY(0) == LYC first
            updateStat(0)
            checkLyc()
        }

        modeClock += cycles

        when (mode) {
            // Mode 2 - OAM Search
            // PPU scans OAM to find sprites visible on current scanline.
            // OAM is not accessible to CPU during this mode.
            // Duration: 80 cycles
            2 -> if (modeClock >= 80) {
                modeClock -= 80
                mode = 3
                bgpCount = 1
                bgpDots[0] = 0
                bgpVals[0] = bus.readRaw(0xFF47)
                val scx = bus.read(0xFF43)
                val penalty = scxPenalty(scx) + spriteMode3Penalty(lcdc, scx)
                mode3Duration = 172 + penalty
                mode0Duration = 204 - penalty
                updateStat(3)
            }

            // Mode 3 - Drawing
            // PPU reads VRAM and renders pixels for the current scanline.
            // Neither VRAM nor OAM are accessible to CPU during this mode.
            // Duration: 172 cycles
            3 -> if (modeClock >= mode3Duration) {
                modeClock -= mode3Duration

                renderScanline(lcdc)
                mode = 0
                updateStat(0)
            }

            // Mode 0 - H-Blank
            // Rest period between scanlines.
            // CPU can freely access VRAM and OAM.
            // Duration: 204 cycles
            0 -> if (modeClock >= mode0Duration) {
                modeClock -= mode0Duration
                if (isFirstScanline) {
                    isFirstScanline = false
                    mode0Duration = 200
                    mode3Duration = 172      // ← ligne 0 : pas de pénalité SCX, chemin lcdon inchangé
                    mode = 3
                    updateStat(3)
                } else {
                    // Normal HBlank exit
                    mode0Duration = 204 // Reset to standard (adjusted for SCX in fix 4)
                    ly++
                    bus.write(0xFF44, ly)
                    checkLyc()
                    if (ly == 144) {
                        firstFrameAfterLcdOn = false
                        mode = 1
                        windowLine = 0
                        updateStat(1)
                        bus.setIF(bus.iF or 0x01)
                        frameChannel.trySend(frameBuffer.copyOf())
                    } else {
                        mode = 2
                        updateStat(2)
                    }
                }
            }

            // Mode 1 - V-Blank
            // PPU has finished drawing all 144 visible lines.
            // Lines 144-153 are off-screen - CPU can safely update VRAM.
            // V-Blank interrupt is triggered at the start of this mode.
            // Duration: 456 cycles × 10 lines (lines 144-153)
            1 -> {
                // LY=153 quirk: LY only reads 153 for ~1 M-cycle, then reads 0 for the rest
                // of the line. The LYC=0 coincidence (and the STAT interrupt) therefore fires
                // ~452 dots earlier than at the real start of line 0.
                if (ly == 153 && !ly153Wrapped && modeClock >= 4) {
                    ly153Wrapped = true
                    ly = 0
                    bus.write(0xFF44, 0)
                    checkLyc()                 // LYC=0 interrupt fires HERE, ~1 line earlier
                }

                if (modeClock >= 456) {
                    modeClock -= 456
                    if (ly153Wrapped) {
                        // End of "line 153" -> actual start of the frame, LY already 0
                        ly153Wrapped = false
                        ly = 0
                        modeClock = 0
                        mode = 2
                        updateStat(2)
                        // No checkLyc here: LY==0 has already been signaled
                    } else {
                        ly++
                        bus.write(0xFF44, ly)
                        checkLyc()
                    }
                }
            }
        }
    }

    private fun scxPenalty(scx: Int): Int = when (scx and 0x07) {
        0 -> 0
        in 1..4 -> 4
        else -> 8            // 5..7
    }

    private fun updateStat(newMode: Int) {
        pendingStatMode = newMode      // bits STAT + IRQ : appliqués au step suivant (+4 dots)
    }

    private fun checkLyc() {
        updateLycFlag()
        refreshStatInterrupt()
    }

    /**
     * Keeps the STAT LYC == LY coincidence flag (bit 2) in sync with the live
     * comparison. Called when LY changes and when the CPU writes LYC.
     *
     * The comparison clock only runs while the LCD is on; while off the flag is
     * frozen at its last value, so writes to LYC have no effect on it.
     */
    private fun updateLycFlag() {
        if (bus.read(0xFF40) and 0x80 == 0) return  // comparison clock not running
        val coincidence = ly == bus.read(0xFF45)
        val stat = bus.readRaw(0xFF41)
        bus.writeRaw(0xFF41, if (coincidence) stat or 0x04 else stat and 0x04.inv())
    }

    /**
     * Models the single internal STAT interrupt line.
     *
     * The line is the OR of the four STAT sources, each gated by its enable bit in
     * STAT (bits 3-6): LYC == LY (bit 6), mode 2 / OAM (bit 5), mode 1 / V-Blank
     * (bit 4) and mode 0 / H-Blank (bit 3). A STAT interrupt (IF bit 1) is requested
     * only on the RISING edge of this combined line.
     *
     * While the LCD is off the line is frozen: it keeps its last value and cannot
     * fire. Re-enabling the LCD therefore only produces an interrupt if the resumed
     * comparison flips the line from low to high.
     *
     * The current mode comes from the `mode` field rather than the STAT register so
     * it stays correct right after a CPU write to STAT.
     */
    private fun refreshStatInterrupt() {
        if (bus.read(0xFF40) and 0x80 == 0) return  // line frozen while the LCD is off

        val stat = bus.readRaw(0xFF41)
        val condition =
            (stat and 0x40 != 0 && stat and 0x04 != 0) ||              // LYC == LY
                    (stat and 0x20 != 0 && (statMode == 2 || ly == 144)) ||        // mode 2 (OAM); also pulses at line 144
                    (stat and 0x10 != 0 && statMode == 1) ||                       // mode 1 (V-Blank)
                    (stat and 0x08 != 0 && statMode == 0)                          // mode 0 (H-Blank)

        if (condition && !statLine) {
            bus.setIF(bus.iF or 0x02)
        }
        statLine = condition
    }

    private fun renderScanline(lcdc: Int) {
        // Reset BG color index buffer for this scanline before rendering
        for (x in 0 until 160) bgColorIndexBuffer[ly * 160 + x] = 0

        // CGB uses a separate rendering path. LCDC.0 is no longer "BG enable" on CGB
        // (it is the BG/OBJ master priority), so the background is always drawn.
        if (bus.machineMode == MachineMode.DMG) {
            if (lcdc and 0x01 != 0) renderBackground(lcdc)
            if (lcdc and 0x20 != 0) renderWindow(lcdc)
            if (lcdc and 0x02 != 0) renderSprites(lcdc)
        } else {
            renderBackgroundCgb(lcdc)
            renderWindowCgb(lcdc)
            // TODO: renderWindowCgb (gated on LCDC.5) and renderSpritesCgb (gated on LCDC.2)
            //  are the next steps. Until they exist, window and sprites are not drawn in CGB
            //  mode — expect BG-only color output.
            // TODO: CGB_COMPAT (DMG game on CGB hardware) needs its own path: DMG-style
            //  rendering whose BGP/OBP indices map into the auto-palette CGB palettes. This
            //  branch currently renders it like full CGB (BG palette 0, BGP ignored), which is
            //  only a placeholder until the auto-palette step. Test DMG-only games in DMG mode
            //  for now.
        }
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

    private fun renderWindowCgb(lcdc: Int) {
        val wx = bus.read(0xFF4B)
        val wy = bus.read(0xFF4A)

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
            val tileIndex = bus.readVram(0, tileMapAddr)

            val attr = bus.readVram(1, tileMapAddr)

            // BG map attributes (CGB):
            // bits 0-2 — BG palette (0-7)
            // bit 3    — tile VRAM bank
            // bit 5    — X flip
            // bit 6    — Y flip
            // bit 7    — BG-to-OBJ priority
            val palette = attr and 0x07
            val tileBank = (attr shr 3) and 0x01
            val xFlip = attr and 0x20 != 0
            val yFlip = attr and 0x40 != 0
            val bgPriority = attr and 0x80 != 0

            val rowInTile = if (yFlip) 7 - tilePixelY else tilePixelY

            val tileDataAddr = if (unsignedTileData) {
                tileIndex * 16 + rowInTile * 2                                  // 0x8000-based, unsigned
            } else {
                0x1000 + tileIndex.toByte().toInt() * 16 + rowInTile * 2        // 0x9000-based, signed
            }

            val loByte = bus.readVram(tileBank, tileDataAddr)
            val hiByte = bus.readVram(tileBank, tileDataAddr + 1)

            val bitIndex = if (xFlip) tilePixelX else 7 - tilePixelX
            val loBit = (loByte shr bitIndex) and 0x01
            val hiBit = (hiByte shr bitIndex) and 0x01
            val colorIndex = (hiBit shl 1) or loBit

            frameBuffer[ly * 160 + screenX] = bus.bgColorRgb555(palette, colorIndex)
            // Pack the color index (bits 0-1) with the per-tile BG priority (bit 2). Both
            // are consumed by the CGB sprite/BG priority resolution in a later step.
            bgColorIndexBuffer[ly * 160 + screenX] = colorIndex or (if (bgPriority) 0x04 else 0)
        }

        windowLine++
    }

    private fun renderBackground(lcdc: Int) {
        val scy = bus.read(0xFF42)
        val scx = bus.read(0xFF43)
        val warmup = 6 + (scx and 7)          // dot du mode 3 où sort le pixel x=0

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

            val bgp  = bgpAt(warmup + screenX)
            val gray = (bgp shr (colorIndex * 2)) and 0x03
            frameBuffer[ly * 160 + screenX] = gray
            bgColorIndexBuffer[ly * 160 + screenX] = colorIndex
        }
    }

    private fun renderBackgroundCgb(lcdc: Int) {
        val scy = bus.read(0xFF42)
        val scx = bus.read(0xFF43)

        // Bit 3: BG tile map — 0=0x9800, 1=0x9C00
        val tileMapBase = if (lcdc and 0x08 != 0) 0x1C00 else 0x1800

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
            // Tile index lives in VRAM bank 0; its attribute byte is at the same offset
            // in bank 1.
            val tileIndex = bus.readVram(0, tileMapAddr)
            val attr = bus.readVram(1, tileMapAddr)

            // BG map attributes (CGB):
            // bits 0-2 — BG palette (0-7)
            // bit 3    — tile VRAM bank
            // bit 5    — X flip
            // bit 6    — Y flip
            // bit 7    — BG-to-OBJ priority
            val palette = attr and 0x07
            val tileBank = (attr shr 3) and 0x01
            val xFlip = attr and 0x20 != 0
            val yFlip = attr and 0x40 != 0
            val bgPriority = attr and 0x80 != 0

            val rowInTile = if (yFlip) 7 - tilePixelY else tilePixelY

            val tileDataAddr = if (unsignedTileData) {
                tileIndex * 16 + rowInTile * 2                                  // 0x8000-based, unsigned
            } else {
                0x1000 + tileIndex.toByte().toInt() * 16 + rowInTile * 2        // 0x9000-based, signed
            }

            val loByte = bus.readVram(tileBank, tileDataAddr)
            val hiByte = bus.readVram(tileBank, tileDataAddr + 1)

            val bitIndex = if (xFlip) tilePixelX else 7 - tilePixelX
            val loBit = (loByte shr bitIndex) and 0x01
            val hiBit = (hiByte shr bitIndex) and 0x01
            val colorIndex = (hiBit shl 1) or loBit

            frameBuffer[ly * 160 + screenX] = bus.bgColorRgb555(palette, colorIndex)
            // Pack the color index (bits 0-1) with the per-tile BG priority (bit 2). Both
            // are consumed by the CGB sprite/BG priority resolution in a later step.
            bgColorIndexBuffer[ly * 160 + screenX] = colorIndex or (if (bgPriority) 0x04 else 0)
        }
    }

    private fun bgpAt(dot: Int): Int {
        var v = bgpVals[0]; var i = 1
        while (i < bgpCount && bgpDots[i] <= dot) { v = bgpVals[i]; i++ }
        return v
    }

    private fun spriteMode3Penalty(lcdc: Int, scx: Int): Int {
        if (lcdc and 0x02 == 0) return 0                 // sprites désactivés
        val height = if (lcdc and 0x04 != 0) 16 else 8
        val xs = ArrayList<Int>(10)
        var i = 0
        while (i < 40 && xs.size < 10) {                 // scan mode 2 : 10 max, ordre OAM, par Y
            val y = bus.readOam(i * 4)
            if (ly + 16 >= y && ly + 16 < y + height) xs.add(bus.readOam(i * 4 + 1))
            i++
        }
        xs.removeAll { it >= 168 }                        // hors écran à droite : pas de pénalité
        xs.sort()
        var p = 0; var lastTile = -1
        for (x in xs) {
            p += 6
            val tile = (x + scx) / 8
            if (tile != lastTile) { p += maxOf(0, 5 - ((x + scx) and 7)); lastTile = tile }
        }
        return (p / 4) * 4                                // ← tronqué au M-cycle (floor)
    }
}
