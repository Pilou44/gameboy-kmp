package com.wechantloup.gameboykmp.ppu

import com.wechantloup.gameboykmp.bus.Bus
import com.wechantloup.gameboykmp.cpu.MachineMode
import kotlinx.coroutines.channels.Channel

class Ppu(
    private val bus: Bus,
) {
    val frameChannel = Channel<IntArray>(Channel.CONFLATED)

    val frameBuffer = IntArray(160 * 144)
    val bgColorIndexBuffer = IntArray(160 * 144)

    // --- T-cycle driving (block 1a scaffolding — the M-cycle bookkeeping here is temporary and
    //  goes away in block 1b, when pendingStatMode's boundary deferral is removed) ---
    private var mCyclePhase = 0             // mirrors the loop's tCounter % 4; boundary at 0
    private var startedFirstMCycle = false  // suppresses dots before the first boundary, so the PPU's
                                            //  first activity lands on the same T the batched step()
                                            //  fired on (tCounter % 4 == 0) — otherwise all mode timing
                                            //  shifts by 3 dots. VALIDATE: first suspect if timing reds.
    private var dotPhase = 0                // double-speed dot divider parity
    private var wasDoubleSpeed = bus.isDoubleSpeed
    private var cachedLcdc = 0              // LCDC is stable within an M-cycle; read once per boundary
    private var skipDotsThisMCycle = false  // set at the boundary when the LCD is off

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
        set(value) {
            field = value
            bus.ppuDotOverrideActive = value
        }

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
        // TODO step 2: this sampler, the write-intercept below, and the PpuTiming object are all
        //  removed once the live per-dot machine drives the lcd-on observable directly. This lambda
        //  is also the source of the per-access Int boxing (a function type over a primitive) — the
        //  main perf cost the T-state refactor eliminates.
        bus.ppuSampler = sampler@{ addr ->

            if (!firstFrameAfterLcdOn) return@sampler null

            val s = PpuTiming.sample(lcdOnDot, bus.readRaw(0xFF45))

            when (addr) {
                0xFF41 -> (bus.readRaw(0xFF41) and 0x78) or s.stat
                0xFF44 -> s.ly
                in 0xFE00..0xFE9F -> when {
                    s.oamBlocked    -> 0xFF
                    bus.isDmaActive -> 0xFF                    // OAM inaccessible during DMA
                    else            -> bus.readOam(addr - 0xFE00)   // dot-only model, bypasses ppuMode
                }
                in 0x8000..0x9FFF -> if (s.vramBlocked) 0xFF else bus.readVram(addr - 0x8000)
                else -> null
            }
        }
        bus.ppuWriteIntercept = wi@{ addr, v ->
            if (!firstFrameAfterLcdOn) return@wi false          // outside the first frame → normal write()
            when (addr) {
                in 0xFE00..0xFE9F -> {
                    if (!PpuTiming.oamWriteBlocked(lcdOnDot) && !bus.isDmaActive) bus.writeOam(addr - 0xFE00, v)
                    true                                         // handled (dropped OR written) → short-circuits ppuMode
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
                bgpDots[bgpCount] = modeClock   // mode-3 dot at the time of the write
                bgpVals[bgpCount] = v
                bgpCount++
            }
        }
    }

    fun tick() {
        // Speed-switch edge: define the divider phase at the only event that changes the stride.
        val ds = bus.isDoubleSpeed
        if (ds != wasDoubleSpeed) {
            wasDoubleSpeed = ds
            dotPhase = 0
            // TODO hardware: mid-active-frame speed switch is undefined (games switch LCD-off);
            //  anchoring here only keeps the divider deterministic.
        }

        // M-cycle boundary work: reproduces the once-per-step() preamble of the batched model.
        // The boundary is every 4 T regardless of speed (the CPU M-cycle is always 4 T).
        // VALIDATE: this phase must match the CPU's access phase (T0). The batched step() fired at
        //  tCounter % 4 == 0 and the suite was green there, so mCyclePhase must be aligned to it.
        mCyclePhase = (mCyclePhase + 1) and 0x03
        if (mCyclePhase == 0) {
            startedFirstMCycle = true

            // (1) Apply the mode change queued during the PREVIOUS M-cycle's dots. The batched model
            //     applied it at the top of the next step() — one M-cycle later. Keeping that exact
            //     lag is what makes 1(a) CPU-observably identical. (Removed in 1b.)
            pendingStatMode?.let { m ->
                statMode = m
                bus.ppuMode = m
                val stat = bus.readRaw(0xFF41)
                bus.writeRaw(0xFF41, (stat and 0xFC) or (m and 0x03))
                refreshStatInterrupt()
                pendingStatMode = null
            }

            // (2) LCDC is stable within an M-cycle — read once, cache for this M-cycle's dots.
            cachedLcdc = bus.read(0xFF40)

            // (3) lcdOnDot keeps its step-level phase: advance per M-cycle, not per dot (advancing
            //     per dot would shift the lcd-on rising edge by +4). Consumed only by PpuTiming.
            if (firstFrameAfterLcdOn) lcdOnDot += if (ds) 2 else 4

            // (4) LCD on/off transitions, once per M-cycle as before.
            skipDotsThisMCycle = false
            if (cachedLcdc and 0x80 == 0) {
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

                    frameBuffer.fill(0)
                    frameChannel.trySend(frameBuffer.copyOf())

                    bus.ppuLy = ly

                    val stat = bus.read(0xFF41)
                    bus.writeRaw(0xFF41, stat and 0xFC)
                    // statLine intentionally NOT reset: the interrupt line is frozen at its
                    // current value; re-enabling the LCD only fires if the comparison flips.
                }
                skipDotsThisMCycle = true
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
        }

        // Dot advance, gated by (a) the first-boundary suppression and (b) the double-speed divider.
        // dotPhase only advances in double speed (short-circuit), so it never drifts in single speed.
        if (!startedFirstMCycle || skipDotsThisMCycle) return
        val isDotTick = !ds || (dotPhase++ and 1) == 0
        if (isDotTick) advanceOneDot(cachedLcdc)
    }

    private fun advanceOneDot(lcdc: Int) {
        // NOTE: lcdOnDot is intentionally NOT incremented here — see step(). Only modeClock
        // and the mode state machine advance per dot in step 1.

        modeClock++

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
                // TODO FIFO / level B (conditional — only if these formula-based durations leave
                //  timing reds): mode-3 duration should become emergent from the fetcher/FIFO instead
                //  of being computed here. Not a planned step; depends on level-A results.
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
                bus.stepHblankDma()
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
                    mode3Duration = 172      // line 0: no SCX penalty, lcdon path unchanged
                    mode = 3
                    updateStat(3)
                } else {
                    // Normal HBlank exit
                    mode0Duration = 204 // Reset to the standard mode-0 duration (SCX adjustment is reapplied at the mode 2→3 transition)
                    ly++
                    bus.ppuLy = ly
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
                    bus.ppuLy = 0
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
                        bus.ppuLy = ly
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
        // Queue the STAT mode change; applied at the top of the next step() — i.e. on the next
        // M-cycle boundary, NOT after a fixed dot delay. The transition is detected at the exact
        // dot in advanceOneDot(), but its observable effect stays boundary-aligned so CPU-visible
        // timing is unchanged.
        // TODO T-state precision (after step 2): hardware changes the internal mode + access
        //  blocking immediately and lags only the 0xFF41 mode bits (~4 dots); the IRQ follows the
        //  real transition with per-source quirks (mode 2 checked at one M-cycle, can't block; LYC
        //  delayed ~1 cycle after mode 2). Validated one source at a time.
        pendingStatMode = newMode
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

        when (bus.machineMode) {
            MachineMode.DMG -> {
                if (lcdc and 0x01 != 0) {
                    renderBackground(lcdc)
                    if (lcdc and 0x20 != 0) renderWindow(lcdc)
                } else {
                    // DMG: LCDC.0 = 0 disables BG *and* window; the line shows BGP colour 0.
                    fillBgColorZeroDmg()
                }
                if (lcdc and 0x02 != 0) renderSprites(lcdc)
            }
            MachineMode.CGB_COMPAT -> {
                // DMG game on CGB hardware: DMG rendering rules (LCDC.0 = BG enable, bank-0
                // tiles, no per-tile attributes, BGP/OBP shade mapping), but the resolved DMG
                // shade indexes the CGB palettes instead of the four greys.
                if (lcdc and 0x01 != 0) {
                    renderBackgroundCompat(lcdc)
                    if (lcdc and 0x20 != 0) renderWindowCompat(lcdc)
                } else {
                    // DMG semantics: LCDC.0 = 0 disables BG *and* window; the line shows BGP colour 0.
                    fillBgColorZeroCompat()
                }
                if (lcdc and 0x02 != 0) renderSpritesCompat(lcdc)
            }
            MachineMode.CGB -> {
                renderBackgroundCgb(lcdc)
                if (lcdc and 0x20 != 0) renderWindowCgb(lcdc)
                if (lcdc and 0x02 != 0) renderSpritesCgb(lcdc)
            }
        }
    }

    private fun fillBgColorZeroDmg() {
        // LCDC.0 = 0: BG forced to colour index 0, mapped through BGP. frameBuffer holds raw DMG
        // shades here, so write the shade BGP maps colour 0 to (not an RGB555 value). Skipping the
        // render would leave shade 0, which only looks right when BGP[0] == 0. bgColorIndexBuffer
        // stays 0, so sprites correctly treat the BG as colour 0.
        val shade0 = (bus.read(0xFF47)) and 0x03
        val base = ly * 160
        for (x in 0 until 160) frameBuffer[base + x] = shade0
    }

    private fun fillBgColorZeroCompat() {
        // LCDC.0 = 0 under DMG rules: the BG layer is forced to colour index 0, mapped through BGP.
        // In compat that DMG shade indexes CGB BG palette 0, so we write its CGB colour. Skipping
        // the render (as the LCDC.0 != 0 path does) would leave the frame buffer at RGB555 0x0000
        // (black) — the dmg-acid2 hair band. bgColorIndexBuffer stays 0 (already reset for the line),
        // so sprites correctly treat the BG as colour 0 and show on top.
        val shade0 = bus.read(0xFF47) and 0x03
        val color = bus.bgColorRgb555(0, shade0)
        val base = ly * 160
        for (x in 0 until 160) frameBuffer[base + x] = color
    }

    private fun renderSprites(lcdc: Int) {
        // squareSprite: false for 8x16, true for 8x8
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
            val positionX = bus.readOam(spriteIndex * 4 + 1)

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

    private fun renderSpritesCompat(lcdc: Int) {
        // squareSprite: false for 8x16, true for 8x8
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
            val positionX = bus.readOam(spriteIndex * 4 + 1)

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

            for (pixelIndexX in 0 until 8) {
                val pixelX = if (flipX) 7 - pixelIndexX else pixelIndexX
                val screenX = positionX - 8 + pixelIndexX
                if (screenX < 0) continue
                if (screenX >= 160) continue

                val loBit = (loByte shr (7 - pixelX)) and 0x01
                val hiBit = (hiByte shr (7 - pixelX)) and 0x01
                val colorIndex = (hiBit shl 1) or loBit

                if (colorIndex == 0) continue // Do not display transparent color

                val obp = bus.read(paletteAddress)              // OBP0 (FF48) or OBP1 (FF49)
                val dmgIndex = (obp shr (colorIndex * 2)) and 0x03
                val objPalette = if (paletteAddress == 0xFF49) 1 else 0
                if (!bgPriority || bgColorIndexBuffer[ly * 160 + screenX] == 0) {
                    frameBuffer[ly * 160 + screenX] = bus.objColorRgb555(objPalette, dmgIndex)
                }
            }
        }
    }

    private fun renderSpritesCgb(lcdc: Int) {
        // squareSprite: false for 8x16, true for 8x8
        val squareSprite = lcdc and 0x04 == 0
        val spriteHeight = if (squareSprite) 8 else 16

        var spriteCounter = 0
        val spriteIndexesToDisplay = mutableListOf<Int>()
        for (spriteIndex in 0..39) {
            val positionY = bus.readOam(spriteIndex * 4)

            val isSpriteOnLine = ly >= positionY - 16 && ly < positionY - 16 + spriteHeight // sprite is displayed

            // Max 10 sprites per line
            if (isSpriteOnLine && spriteCounter < 10) {
                spriteCounter++
                spriteIndexesToDisplay.add(spriteIndex)
            }
        }

        val masterPriority = lcdc and 0x01 != 0   // LCDC.0 on CGB = BG/OBJ master priority

        // CGB priority is by OAM index only (no X sort): lowest index wins. The list is in ascending
        // OAM order, so iterating it reversed paints the lowest index last = on top.
        // OPRI (FF6C) is latched by the boot ROM (0 here in CGB mode, 1 in DMG-compat) and has no
        // effect when written post-boot, so the priority mode is fixed per machine mode and correctly
        // hardcoded per render path — there is nothing to switch at runtime.
        for (spriteIndex in spriteIndexesToDisplay.reversed()) {
            val positionY = bus.readOam(spriteIndex * 4)
            val positionX = bus.readOam(spriteIndex * 4 + 1)

            // Sprite attributes (OAM byte 3) on CGB:
            // bit 7    — OBJ-to-BG priority (1 = behind BG colors 1-3)
            // bit 6    — Y flip
            // bit 5    — X flip
            // bit 3    — tile VRAM bank
            // bits 0-2 — OBJ palette (0-7) via OCPD  (bit 4 ignored on CGB)
            val attr = bus.readOam(spriteIndex * 4 + 3)
            val flipY = attr and 0x40 > 0
            val flipX = attr and 0x20 > 0
            val spriteBgPriority = attr and 0x80 > 0
            val spriteBank = (attr shr 3) and 0x01
            val palette = attr and 0x07

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

            // Tile pixels come from VRAM, in the bank selected by attribute bit 3.
            val loByte = bus.readVram(spriteBank, tileDataAddr)
            val hiByte = bus.readVram(spriteBank, tileDataAddr + 1)

            for (pixelIndexX in 0 until 8) {
                val pixelX = if (flipX) 7 - pixelIndexX else pixelIndexX
                val screenX = positionX - 8 + pixelIndexX
                if (screenX < 0) continue
                if (screenX >= 160) continue

                val loBit = (loByte shr (7 - pixelX)) and 0x01
                val hiBit = (hiByte shr (7 - pixelX)) and 0x01
                val colorIndex = (hiBit shl 1) or loBit

                if (colorIndex == 0) continue // Do not display transparent color

                // BG/OBJ priority resolution. The BG layer's color index and per-tile
                // priority bit were packed into bgColorIndexBuffer by the BG/window render.
                val packed = bgColorIndexBuffer[ly * 160 + screenX]
                val bgColorIndex = packed and 0x03
                val bgHasPriority = packed and 0x04 != 0

                val spriteWins = !masterPriority ||           // master off → OBJ always on top
                        bgColorIndex == 0 ||                       // BG is color 0 → OBJ shows through
                        (!bgHasPriority && !spriteBgPriority)       // neither layer claims priority

                if (spriteWins) {
                    frameBuffer[ly * 160 + screenX] = bus.objColorRgb555(palette, colorIndex)
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

    private fun renderWindowCompat(lcdc: Int) {
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

            val dmgIndex = (bgp shr (colorIndex * 2)) and 0x03
            // CGB_COMPAT: the DMG shade index selects a colour from CGB BG palette 0
            // (preloaded grey now, overwritten by the boot ROM later — no render change).
            frameBuffer[ly * 160 + screenX] = bus.bgColorRgb555(0, dmgIndex)
            bgColorIndexBuffer[ly * 160 + screenX] = colorIndex   // raw index for DMG-style OBJ priority
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
        val warmup = 6 + (scx and 7)          // mode-3 dot at which pixel x=0 is emitted

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

    private fun renderBackgroundCompat(lcdc: Int) {
        val scy = bus.read(0xFF42)
        val scx = bus.read(0xFF43)
        val warmup = 3 + (scx and 7)          // mode-3 dot at which pixel x=0 is emitted

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

            val bgp = bgpAt(warmup + screenX)
            val dmgIndex = (bgp shr (colorIndex * 2)) and 0x03
            // CGB_COMPAT: the DMG shade index selects a colour from CGB BG palette 0
            // (preloaded grey now, overwritten by the boot ROM later — no render change).
            frameBuffer[ly * 160 + screenX] = bus.bgColorRgb555(0, dmgIndex)
            bgColorIndexBuffer[ly * 160 + screenX] = colorIndex   // raw index for DMG-style OBJ priority
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
        if (lcdc and 0x02 == 0) return 0                 // sprites disabled
        val height = if (lcdc and 0x04 != 0) 16 else 8
        val xs = ArrayList<Int>(10)
        var i = 0
        while (i < 40 && xs.size < 10) {                 // mode 2 scan: max 10, OAM order, by Y
            val y = bus.readOam(i * 4)
            if (ly + 16 >= y && ly + 16 < y + height) xs.add(bus.readOam(i * 4 + 1))
            i++
        }
        xs.removeAll { it >= 168 }                        // off-screen to the right: no penalty
        xs.sort()
        var p = 0; var lastTile = -1
        for (x in xs) {
            p += 6
            val tile = (x + scx) / 8
            if (tile != lastTile) { p += maxOf(0, 5 - ((x + scx) and 7)); lastTile = tile }
        }
        return (p / 4) * 4                                // truncated to the M-cycle (floor)
    }
}
