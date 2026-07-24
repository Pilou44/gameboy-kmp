package com.wechantloup.gameboykmp.ppu

import com.wechantloup.gameboykmp.bus.Bus
import com.wechantloup.gameboykmp.cpu.MachineMode
import kotlinx.coroutines.channels.Channel

/**
 * Dot-driven PPU — mode FSM + DMG background pipeline.
 *
 * The backbone owns the scanline/dot timing and the mode state machine. Mode 3 now renders the
 * background through a real fetcher + FIFO + shifter (DMG); sprites, window and CGB are still to
 * come. Cadence: tick() is called once per T-cycle by the emulation loop. In single speed one
 * tick advances one dot; in double speed the LCD keeps its normal rate, so the PPU advances one
 * dot every two ticks (internal divider, see dotDivider).
 *
 * Single authorities (no mirroring — matches the Bus surface):
 *   - line        : the internal scanline 0..153. The only authority for "which line".
 *   - bus.ppuLy   : the CPU-visible LY (0xFF44). Projection of `line`, with the LY153 quirk.
 *   - bus.ppuMode : the CPU-visible mode (0..3). Projected into STAT bits 0-1 by the Bus.
 *   - coincidence : derived by the Bus at STAT read (ppuLy == LYC), never stored here.
 *
 * The mode 3 -> mode 0 joint:
 *   Mode 3 has an EMERGENT duration — it lasts as long as the shifter takes to output 160 pixels.
 *   The FSM never computes that duration; it leaves mode 3 only when `drawingDone` is set, which
 *   the shifter does at pixel 160. For pure BG this comes out to 172 + (SCX & 7) dots.
 *
 * Mode 3 BG pipeline (per dot): bgFetcher.tick() fetches VRAM in 4 steps and pushes 8 pixels into
 * bgFifo when there is room; shiftPixel() pops one, drops it for fine-scroll (SCX & 7) or applies
 * BGP live and writes the shade into frameBuffer. Registers (SCX/SCY/LCDC/BGP) are read live, so
 * mid-mode-3 writes take effect natively (Mealybug) — no compensation layer.
 *
 * Included: dot/line counting, the 2->3->0 (visible) and 1 (VBlank) mode FSM, LY progression +
 * frame wrap, the LY153 quirk (structure; exact dots TODO), LCD on/off, the VBlank interrupt, a
 * simple event-based STAT interrupt, the HBlank-DMA edge call, DMG BG rendering, the mode-2 OAM
 * scan, DMG sprite rendering (fetch + FIFO + mixer with priorities), the window (own line counter,
 * WY latch, WX trigger restarting the fetcher on the window map), and LCDC.0 (DMG BG/window blank).
 *
 * Deferred (each marked with a TODO at its site): CGB (bank-1 attributes, RGB555 output, LCDC.0 as
 * master priority instead of enable), sprites clipped at X < 8, the exact sprite-fetch and window-
 * restart stall timing, the WX 0..6 / WX 166 quirks, the LCD-on first-frame quirk, the STAT-
 * blocking / rising-edge refinement, double-speed switch-phase anchoring, and the LCD-off screen
 * clear.
 *
 * Naming and structure here are a starting point — open to reshaping.
 */
class Ppu(private val bus: Bus) {

    /**
     * Frame output. One IntArray per completed frame, 160x144 in row-major order.
     * Encoding matches the existing ViewModel contract: a palette index (0..3) on DMG, an
     * RGB555 colour on CGB / CGB_COMPAT. CONFLATED: the UI only ever needs the latest frame.
     */
    val frameChannel = Channel<IntArray>(Channel.CONFLATED)

    private enum class Mode(val id: Int) { HBLANK(0), VBLANK(1), OAM_SCAN(2), DRAWING(3) }

    private var mode = Mode.HBLANK
    private var line = 0            // internal scanline 0..153 (single authority for the line)
    private var lineDot = 0         // dot within the current line, 0..455
    private var oamScanDot = 0      // dots elapsed in mode 2 (2 per OAM entry)
    private var drawingDone = false // the mode 3 -> 0 joint, driven by the shifter (160 px out)
    private var lcdOn = false       // tracks LCDC.7 edges for power on/off
    private var justPoweredOn = false // one-shot: line 0 after LCD enable skips mode 2 (LCD-on quirk)
    private var dotDivider = false  // double-speed dot divider phase
    // The STAT interrupt line: OR of all enabled sources. IF is armed only on its rising edge
    // (STAT blocking). Stored because the edge needs the previous level, not because it mirrors state.
    private var statLine = false

    // Mode 2 output: the sprites on the current line, in OAM order. Pooled (allocated once, reused
    // each line) so building the list costs nothing. Consumed by the mode-3 sprite fetch (later).
    private val sprites = Array(MAX_SPRITES_PER_LINE) { Sprite() }
    private var spriteCount = 0

    private val machineMode = bus.machineMode

    // Mode 3 BG pipeline. The fetcher is chosen once per session; DMG and CGB_COMPAT share the
    // DMG-shaped fetch (no VBK / no bank-1 attributes), CGB adds per-tile attributes.
    private val bgFifo = PixelFifo(FIFO_CAPACITY)
    private val bgFetcher: BgFetcher = when (machineMode) {
        MachineMode.CGB -> BgFetcherCgb(bus)
        else -> BgFetcherDmg(bus)
    }
    private var lcdX = 0            // visible pixels output on the current line, 0..160
    private var discard = 0         // remaining fine-scroll (SCX & 7) pixels to drop, latched per line

    // Mode 3 sprite pipeline (DMG). The sprite FIFO is an 8-wide window aligned to lcdX.
    private val spriteFifo = PixelFifo(SPRITE_FIFO_CAPACITY)
    private val spriteFetcher: SpriteFetcher = when (machineMode) {
        MachineMode.CGB -> SpriteFetcherCgb(bus)
        else -> SpriteFetcherDmg(bus)   // DMG and CGB_COMPAT share the DMG-shaped sprite fetch
    }
    private var fetchingSprite = false                        // a sprite fetch is in progress (BG paused)
    private val spriteFetched = BooleanArray(MAX_SPRITES_PER_LINE)  // which selected sprites are done

    // Window state. windowLine is the window's OWN Y counter: it advances only on lines the window
    // is actually drawn, never from LY. wyConditionMet latches once LY reaches WY within the frame.
    private var windowLine = 0
    private var wyConditionMet = false
    private var windowActiveThisLine = false

    // Persistent framebuffer, reused every frame; a copy is emitted on the channel per frame.
    private val frameBuffer = IntArray(SCREEN_PIXELS)

    /**
     * Advances the PPU by one T-cycle. Called once per T from the emulation loop.
     */
    fun tick() {
        val on = bus.read(REG_LCDC) and LCDC_ENABLE != 0
        if (!on) {
            if (lcdOn) powerOffLcd()
            return
        }
        if (!lcdOn) powerOnLcd()

        // Double-speed dot divider: LCD runs at the normal rate, so in double speed one dot
        // is advanced every two ticks; in single speed, one dot per tick.
        // TODO (double-speed correctness): re-anchor the divider phase on the KEY1/STOP speed
        //  switch event (bus.performSpeedSwitch()) instead of letting it free-run. A switch-notify
        //  hook is needed; until then the phase can be off by one tick across a switch.
        if (bus.isDoubleSpeed) {
            dotDivider = !dotDivider
            if (!dotDivider) return
        }

        advanceOneDot()
    }

    private fun advanceOneDot() {
        when (mode) {
            Mode.OAM_SCAN -> {
                // 2 dots per OAM entry; evaluate the entry on its second dot. Builds the <=10
                // sprite list, in OAM order. Consumed by the mode-3 sprite fetch (not yet wired),
                // so this has no visible effect on its own — it is the producer for that step.
                if (oamScanDot and 1 == 1) evaluateOamEntry(oamScanDot / 2)
                oamScanDot++
                if (oamScanDot == OAM_SCAN_DOTS) enterDrawing()
            }
            Mode.DRAWING -> {
                // One dot of mode 3. A sprite fetch pauses the shifter (the stall that grows mode 3 beyond
                // 172 + SCX&7). Order per dot: finish an in-flight sprite fetch; else advance the BG fetcher,
                // then check for a sprite starting at the pixel about to be output. The check must come AFTER
                // the fetcher tick: the push and the first shift land on the same dot, so testing the FIFO
                // before the tick leaves no dot where lcdX = 0 with a non-empty FIFO — sprites at the left
                // edge were unreachable (intr_2_mode0_timing_sprites #00).
                if (fetchingSprite) {
                    if (spriteFetcher.tick(spriteFifo)) fetchingSprite = false
                } else {
                    bgFetcher.tick(bgFifo)
                    val due = if (objEnabled() && bgFifo.size > 0 && discard == 0) nextSpriteAt(lcdX) else -1
                    if (due >= 0) {
                        spriteFetched[due] = true
                        spriteFetcher.start(sprites[due], line)
                        fetchingSprite = true      // the shifter stalls: no pixel is output this dot
                    } else {
                        shiftPixel()
                    }
                }
                if (drawingDone) enterHBlank()
            }
            Mode.HBLANK -> {
                // Normally passive: HBlank length is emergent, whatever remains of the 456 dots after
                // mode 2 + mode 3 — never computed, just what is left.
                // LCD-on quirk only: on the first line after enable there is no mode 2, so this mode-0
                // window drives the line. Count a normal mode-2-length slot (mirroring OAM_SCAN's dot
                // alignment via oamScanDot), then hand over to mode 3 directly. One-shot: cleared here so
                // lines 1+ take the normal enterOamScan() path.
                if (justPoweredOn) {
                    oamScanDot++
                    if (oamScanDot == OAM_SCAN_DOTS) {
                        justPoweredOn = false
                        enterDrawing()
                    }
                }
            }
            Mode.VBLANK -> {
                // Idle. The whole line is mode 1.
            }
        }

        // LY leads the line boundary by one M-cycle: measured on lcdon_timing (at dot 452 of a 456-dot
        // line, LY already reads the next line while STAT still reports the current line's mode 0).
        // Only the readable value leads — the mode transition stays in endOfLine() at dot 456.
        if (lineDot == DOTS_PER_LINE - LY_LEAD_DOTS) {
            pushLy(if (line + 1 == LINES_PER_FRAME) 0 else line + 1)
        }

        // LY153 quirk: on the final line, LY reads 153 only briefly, then 0 for the rest of the
        // line (still in VBlank). Structure in place; exact timing to pin.
        // TODO: pin LY153_VISIBLE_DOTS against mooneye ppu (ly / lyc-153 timing) + the Python sim.
        if (line == LAST_LINE && lineDot == LY153_VISIBLE_DOTS) pushLy(0)

        lineDot++
        if (lineDot == DOTS_PER_LINE) endOfLine()

        updateStatLine()
    }

    private fun endOfLine() {
        lineDot = 0
        line++
        if (line == LINES_PER_FRAME) {
            line = 0
            frameComplete()
        }
        // LY is no longer pushed here: it was already published LY_LEAD_DOTS earlier.
        when {
            line < VISIBLE_LINES -> enterOamScan()
            line == VISIBLE_LINES -> enterVBlank()
        }
    }

    // ----- mode transitions -----

    private fun setMode(m: Mode) {
        mode = m
        bus.ppuMode = m.id   // one-way push; the Bus projects this into STAT bits 0-1
    }

    private fun enterOamScan() {
        setMode(Mode.OAM_SCAN)
        oamScanDot = 0
        spriteCount = 0
        // WY is a per-frame latch: once LY reaches WY, the window may start on this and later lines.
        if (line == bus.read(REG_WY)) wyConditionMet = true
//        if (statEnabled(STAT_MODE2_IRQ)) requestStatIrq()
    }

    /** Selects OAM entry [index] into the per-line list if it covers this line and there is room. */
    private fun evaluateOamEntry(index: Int) {
        if (spriteCount >= MAX_SPRITES_PER_LINE) return   // hardware keeps the first 10 in OAM order
        val base = index * 4
        val spriteY = bus.readOam(base)                   // raw Y = screen Y + 16
        val height = if (bus.read(REG_LCDC) and LCDC_OBJ_SIZE != 0) 16 else 8
        val row = line + 16                               // compare in the same +16 space as spriteY
        if (row >= spriteY && row < spriteY + height) {
            val s = sprites[spriteCount]
            s.y = spriteY
            s.x = bus.readOam(base + 1)
            s.tile = bus.readOam(base + 2)
            s.attributes = bus.readOam(base + 3)
            s.oamIndex = index
            spriteCount++
        }
    }

    private fun enterDrawing() {
        setMode(Mode.DRAWING)
        drawingDone = false
        lcdX = 0
        discard = bus.read(REG_SCX) and 0x07   // fine-scroll pixels to drop, latched once per line
        bgFifo.clear()
        bgFetcher.reset(line)
        spriteFifo.clear()
        fetchingSprite = false
        spriteFetched.fill(false)
        windowActiveThisLine = false
    }

    /** One dot of the shifter: pop BG (and the lockstep sprite pixel), mix, and write the frame. */
    private fun shiftPixel() {
        if (bgFifo.size == 0) return           // FIFO empty: warm-up or fetcher stall, shifter waits
        // Window trigger: reaching WX-7 hands the rest of the line to the window. Clear the BG FIFO
        // and restart the fetcher on the window map; the emptied FIFO stalls the shifter until it
        // refills — the window's cost, emergent like every other mode-3 stall.
        if (!windowActiveThisLine && discard == 0 && windowEnabled() && wyConditionMet
            && lcdX == bus.read(REG_WX) - 7
        ) {
            bgFifo.clear()
            bgFetcher.startWindow(windowLine)
            windowActiveThisLine = true
            return
        }
        val bgIndex = bgFifo.pop()
        // The sprite FIFO advances in lockstep with the BG FIFO so it stays aligned to lcdX.
        val spritePixel = if (spriteFifo.size > 0) spriteFifo.pop() else 0
        if (discard > 0) {                     // fine-scroll: popped but not displayed
            discard--
            return
        }
        frameBuffer[line * SCREEN_WIDTH + lcdX] = when (machineMode) {
            MachineMode.DMG -> mixDmg(bgIndex, spritePixel)
            MachineMode.CGB -> mixCgb(bgIndex, spritePixel)
            MachineMode.CGB_COMPAT -> mixCgbCompat(bgIndex, spritePixel)
        }
        lcdX++
        if (lcdX == SCREEN_WIDTH) drawingDone = true
    }

    /** BG/sprite priority + palette, all read LIVE so mid-line palette writes are honoured. */
    private fun mixDmg(bgIndex: Int, spritePixel: Int): Int {
        // LCDC.0 = 0 on DMG blanks BG and window (forced to colour 0); sprites are unaffected.
        val bg = if (bgEnabled()) bgIndex else 0
        val spriteColor = spritePixel and SpriteFetcherDmg.PIXEL_COLOR
        if (spriteColor != 0 && objEnabled()) {
            val behind = spritePixel and SpriteFetcherDmg.PIXEL_PRIORITY != 0
            if (!(behind && bg != 0)) {
                val obp = if (spritePixel and SpriteFetcherDmg.PIXEL_PALETTE != 0) bus.read(REG_OBP1)
                else bus.read(REG_OBP0)
                return (obp shr (spriteColor * 2)) and 0x03
            }
        }
        val bgp = bus.read(REG_BGP)                            // BG (or window) wins
        return (bgp shr (bg * 2)) and 0x03
    }

    /**
     * CGB mixer. BG always renders; OBJ is gated by LCDC.1. LCDC.0 is the master BG/OBJ priority
     * (NOT enable): when 0, sprites always win. When 1, BG colours 1-3 win over the sprite if either
     * the BG attribute priority (bit 7) or the OBJ priority (bit 7) is set. Output is RGB555 via CRAM.
     */
    private fun mixCgb(bgPixel: Int, spritePixel: Int): Int {
        val bgc = bgPixel and CgbPixel.COLOR
        val objc = spritePixel and CgbPixel.COLOR
        if (objc != 0 && objEnabled()) {
            // LCDC.0 on CGB = master priority (reuses the LCDC_BG_ENABLE bit, different meaning).
            val masterPriority = bus.read(REG_LCDC) and LCDC_BG_ENABLE != 0
            val bgPriority = bgPixel and CgbPixel.PRIORITY != 0       // BG attr.7: BG-over-OBJ
            val objBehind = spritePixel and CgbPixel.PRIORITY != 0    // OBJ attr.7: OBJ-behind-BG
            val bgWins = masterPriority && bgc != 0 && (bgPriority || objBehind)
            if (!bgWins) {
                val objPal = (spritePixel shr CgbPixel.PALETTE_SHIFT) and 0x07
                return bus.objColorRgb555(objPal, objc)
            }
        }
        val bgPal = (bgPixel shr CgbPixel.PALETTE_SHIFT) and 0x07
        return bus.bgColorRgb555(bgPal, bgc)
    }

    /**
     * CGB_COMPAT mixer: a DMG game on CGB hardware. Priority is pure DMG (DMG fetchers; OPRI = 1, so
     * sprite priority is smaller-X-wins) and LCDC.0 is DMG BG-enable. BGP/OBP still remap the 2-bit
     * index live, so DMG palette animation works. The only difference from mixDmg is the final encode:
     * the DMG shade indexes CGB palette RAM — BG palette 0, OBJ palette 0/1 (from OBP0/OBP1) — to
     * produce RGB555, which the boot ROM's compat palettes colour. Reads DMG-format pixels (DMG
     * fetchers), so it uses the SpriteFetcherDmg layout, not CgbPixel.
     * TODO: LCDC.0 is assumed DMG-enable here; validated by dmg-acid2 run in CGB_COMPAT. Revisit vs
     *  Pan Docs only if a game shows BG where it should be blank.
     */
    private fun mixCgbCompat(bgIndex: Int, spritePixel: Int): Int {
        val bg = if (bgEnabled()) bgIndex else 0                       // LCDC.0 = DMG BG enable
        val spriteColor = spritePixel and SpriteFetcherDmg.PIXEL_COLOR // raw colour: transparency test
        if (spriteColor != 0 && objEnabled()) {
            val behind = spritePixel and SpriteFetcherDmg.PIXEL_PRIORITY != 0
            if (!(behind && bg != 0)) {                               // sprite wins unless behind opaque BG
                val useObp1 = spritePixel and SpriteFetcherDmg.PIXEL_PALETTE != 0
                val obp = if (useObp1) bus.read(REG_OBP1) else bus.read(REG_OBP0)
                val shade = (obp shr (spriteColor * 2)) and 0x03      // OBP remap, then CRAM
                val objPalette = if (useObp1) 1 else 0                // OBP0 -> OBJ palette 0, OBP1 -> 1
                return bus.objColorRgb555(objPalette, shade)
            }
        }
        val bgp = bus.read(REG_BGP)
        val shade = (bgp shr (bg * 2)) and 0x03
        return bus.bgColorRgb555(0, shade)  // compat BG always uses CRAM palette 0
    }

    /** First not-yet-fetched selected sprite that starts at [x], scanned in OAM order, or -1. */
    // TODO (sprite left edge): sprites with OAM X < 8 have a negative screen X and are never matched
    //  here, so they cost no mode-3 time. Hardware still fetches them. The oracle
    //  intr_2_mode0_timing_sprites gives the target: a single sprite costs 8 - (X mod 8) dots
    //  uniformly, including X < 8, and each further sprite at the same X costs 6. Reproducing that
    //  needs the mode-3 loop rework (the shifter must keep running while the BG FIFO is fed), so the
    //  left edge is left unhandled rather than approximated: a clamp to lcdX = 0 collapses X = 0..3
    //  and X = 4..7 onto the same cost, and would also draw an off-screen sprite at columns 0..7.
    private fun nextSpriteAt(x: Int): Int {
        for (i in 0 until spriteCount) {
            if (spriteFetched[i]) continue
            if (sprites[i].x - 8 == x) return i
        }
        return -1
    }

    private fun objEnabled(): Boolean = bus.read(REG_LCDC) and LCDC_OBJ_ENABLE != 0
    private fun windowEnabled(): Boolean = bus.read(REG_LCDC) and LCDC_WINDOW_ENABLE != 0
    private fun bgEnabled(): Boolean = bus.read(REG_LCDC) and LCDC_BG_ENABLE != 0

    private fun enterHBlank() {
        setMode(Mode.HBLANK)
        // The window's Y counter advances only on lines where the window was actually drawn.
        if (windowActiveThisLine) windowLine++
//        if (statEnabled(STAT_MODE0_IRQ)) requestStatIrq()
        // Mode 3 -> 0 edge = exactly one HBlank per visible line. The Bus pumps one HBlank-DMA
        // block here if a transfer is active (no-op otherwise); it stays ignorant of the PPU.
        bus.stepHblankDma()
    }

    private fun enterVBlank() {
        setMode(Mode.VBLANK)
        requestVBlankIrq()                                 // IF bit 0 — always, on line 144 entry
//        if (statEnabled(STAT_MODE1_IRQ)) requestStatIrq()   // STAT mode-1 source
    }

    // ----- LY projection + interrupts -----

    private fun pushLy(value: Int) {
        bus.ppuLy = value
//        checkLycInterrupt(value)
    }

//    private fun checkLycInterrupt(visibleLy: Int) {
//        // Simple event-based coincidence: fire when the freshly visible LY equals LYC and the
//        // LYC STAT source is enabled.
//        // TODO (STAT IRQ phase): replace the per-source event firing here and in enterX() with a
//        //  single rising-edge check on the combined STAT line (STAT blocking). The naive version
//        //  can double-fire when sources overlap; a previous rising-edge attempt caused regressions
//        //  and was reverted — revisit with mooneye stat_irq.
//        if (visibleLy == bus.read(REG_LYC) && statEnabled(STAT_LYC_IRQ)) requestStatIrq()
//    }

    private fun statEnabled(mask: Int): Boolean = bus.read(REG_STAT) and mask != 0

    private fun requestVBlankIrq() { bus.setIF(bus.iF or IF_VBLANK) }
    private fun requestStatIrq() { bus.setIF(bus.iF or IF_STAT) }

    /**
     * Recomputes the combined STAT interrupt line and arms IF on its rising edge only.
     * Hardware has a single line, not four independent events: while it is already high, another
     * source going high fires nothing. This is what stat_irq_blocking measures.
     */
    private fun updateStatLine() {
        val stat = bus.read(REG_STAT)

        val coincidence = bus.ppuLy == bus.read(REG_LYC)
        bus.ppuCoincidence = coincidence          // push the flip-flop; frozen while the LCD is off

        // DMG quirk: the mode-2 source also asserts at the start of line 144, when VBlank begins,
        // even though the reported mode is 1. vblank_stat_intr-GS verifies that this STAT IRQ and the
        // VBlank IRQ are raised at the same time. CGB/AGB do not do this, hence the machine-mode gate.
        // TODO (STAT, line-144 width): this oracle pins only the START of the assertion. The 80-dot
        //  width mirrors a normal mode 2 and is unverified; revisit if a STAT-blocking case disagrees.
        val mode2Line144 = machineMode == MachineMode.DMG &&
                line == VISIBLE_LINES && lineDot < OAM_SCAN_DOTS

        val level =
            (mode == Mode.HBLANK && stat and STAT_MODE0_IRQ != 0) ||
                    (mode == Mode.VBLANK && stat and STAT_MODE1_IRQ != 0) ||
                    ((mode == Mode.OAM_SCAN || mode2Line144) && stat and STAT_MODE2_IRQ != 0) ||
                    (coincidence && stat and STAT_LYC_IRQ != 0)

        if (level && !statLine) requestStatIrq()   // rising edge only
        statLine = level

        // TODO (STAT, perf): two Bus reads per dot. Acceptable while validating; fold into a cached
        //  STAT/LYC snapshot invalidated on write once the behaviour is pinned by the oracles.
    }

    // ----- LCD power on/off -----

    private fun powerOnLcd() {
        lcdOn = true
        dotDivider = false
        line = 0
        lineDot = 0
        drawingDone = false
        // LCD-on quirk (verified against lcdon_timing-GS): the first line after the LCD is enabled
        // has NO mode 2. It reports mode 0 with OAM and VRAM readable, then goes straight to mode 3.
        // The mode-0 window is exactly a normal mode-2 slot long (OAM_SCAN_DOTS); the only difference
        // is that OAM is not scanned (so no sprites are selected for line 0) and access is not locked.
        // Mode 3 then runs the normal emergent pipeline — no compensation constant. No IRQ on enable.
        // TODO (lcd-on, sub-dot): hardware is ~2 T-cycles "late" on line 0. That shift is below this
        //  oracle's M-cycle sampling and is NOT modelled here; it surfaces as the horizontal pixel
        //  offset tracked under cluster C and must be pinned there, never as a magic offset.
        // TODO (lcd-on, STAT): confirm whether enabling into mode 0 should raise a mode-0 STAT IRQ.
        //  Not exercised by lcdon_timing; left silent (setMode, not enterHBlank) until an oracle covers it.
        justPoweredOn = true
        setMode(Mode.HBLANK)   // mode 0 -> OAM/VRAM readable via the Bus mode gating; no scan, no lock
        oamScanDot = 0         // reused below as the line-0 mode-0 window counter
        spriteCount = 0        // no OAM scan on line 0: the sprite list is empty for the first line
        bus.ppuLy = 0
        windowLine = 0
        wyConditionMet = false
        windowActiveThisLine = false
    }

    private fun powerOffLcd() {
        // LCD off: the PPU is frozen, LY reads 0 and the mode reads 0 (HBlank). No interrupts.
        // Pipeline/window state is re-initialised on power-on (see powerOnLcd).
        lcdOn = false
        line = 0
        lineDot = 0
        setMode(Mode.HBLANK)
        bus.ppuLy = 0
        // NOTE: statLine is deliberately NOT reset. The STAT logic is frozen with the LCD, like the
        // coincidence flip-flop: stat_lyc_onoff round 2 requires that a line already high stays high
        // across an off/on cycle (no spurious edge), and round 4 that a low line can still rise.

        // Blanking is a panel property, not a PPU value: the DMG's reflective STN panel goes blank
        // (shade 0) when undriven, the CGB's backlit TFT goes black. The buffer holds DMG shades or
        // RGB555 accordingly. One frame is emitted so the consumer repaints; without it the last drawn
        // frame stays on screen (daid stop_instr).
        val blank = if (machineMode == MachineMode.DMG) 0 else 0x0000
        frameBuffer.fill(blank)
        frameChannel.trySend(frameBuffer.copyOf())
    }

    private fun frameComplete() {
        // New frame: the window's Y counter restarts and the WY latch clears.
        windowLine = 0
        wyConditionMet = false
        // frameBuffer is persistent and reused next frame; the consumer reads it asynchronously,
        // so emit a copy to avoid tearing. DMG stores shade 0..3 per pixel (the channel contract).
        // TODO (perf): revisit per-frame copy once CGB rendering lands; measure against the baseline.
        frameChannel.trySend(frameBuffer.copyOf())
    }

    companion object {
        private const val SCREEN_WIDTH = 160
        private const val SCREEN_HEIGHT = 144
        private const val SCREEN_PIXELS = SCREEN_WIDTH * SCREEN_HEIGHT

        private const val DOTS_PER_LINE = 456
        private const val VISIBLE_LINES = 144    // lines 0..143 draw
        private const val LINES_PER_FRAME = 154  // + 10 VBlank lines (144..153)
        private const val LAST_LINE = 153
        private const val OAM_SCAN_DOTS = 80      // mode 2 length (fixed): 2 dots per OAM entry
        private const val MAX_SPRITES_PER_LINE = 10
        private const val FIFO_CAPACITY = 16      // BG FIFO holds two tiles' worth of pixels
        private const val SPRITE_FIFO_CAPACITY = 8 // one sprite wide; overlaps merge into this window

        // LY153 quirk: how long LY still reads 153 at the start of the last line before reading 0.
        // TODO: pin against mooneye ppu ly/lyc-153 timing + the Python simulator.
        private const val LY153_VISIBLE_DOTS = 4

        private const val LY_LEAD_DOTS = 4   // LY becomes readable one M-cycle before the line ends

        // LCDC / STAT bit masks
        private const val LCDC_ENABLE = 0x80     // LCDC.7: LCD & PPU enable
        private const val LCDC_WINDOW_ENABLE = 0x20 // LCDC.5: window on/off
        private const val LCDC_OBJ_ENABLE = 0x02 // LCDC.1: sprites on/off
        private const val LCDC_OBJ_SIZE = 0x04   // LCDC.2: sprite height (0 = 8x8, 1 = 8x16)
        private const val LCDC_BG_ENABLE = 0x01  // LCDC.0: BG & window on/off (DMG)

        private const val STAT_MODE0_IRQ = 0x08  // STAT bit 3: HBlank source
        private const val STAT_MODE1_IRQ = 0x10  // STAT bit 4: VBlank source
        private const val STAT_MODE2_IRQ = 0x20  // STAT bit 5: OAM source
        private const val STAT_LYC_IRQ = 0x40    // STAT bit 6: LY == LYC source

        private const val IF_VBLANK = 0x01       // IF bit 0
        private const val IF_STAT = 0x02         // IF bit 1

        // I/O registers the PPU reads
        private const val REG_LCDC = 0xFF40
        private const val REG_STAT = 0xFF41
        private const val REG_SCX = 0xFF43
        private const val REG_LYC = 0xFF45
        private const val REG_BGP = 0xFF47
        private const val REG_OBP0 = 0xFF48
        private const val REG_OBP1 = 0xFF49
        private const val REG_WY = 0xFF4A
        private const val REG_WX = 0xFF4B
    }
}
