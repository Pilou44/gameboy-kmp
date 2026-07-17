package com.wechantloup.gameboykmp.ppu

import com.wechantloup.gameboykmp.bus.Bus
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
 * scan, and DMG sprite rendering (fetch + sprite FIFO + BG/sprite mixer with priorities).
 *
 * Deferred (each marked with a TODO at its site): the window, CGB (bank-1 attributes, RGB555
 * output, LCDC.0 master priority), sprites clipped at X < 8, the exact sprite-fetch stall timing,
 * the LCD-on first-frame quirk, the STAT-blocking / rising-edge refinement, double-speed
 * switch-phase anchoring, and the LCD-off screen clear.
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
    private var dotDivider = false  // double-speed dot divider phase

    // Mode 2 output: the sprites on the current line, in OAM order. Pooled (allocated once, reused
    // each line) so building the list costs nothing. Consumed by the mode-3 sprite fetch (later).
    private val sprites = Array(MAX_SPRITES_PER_LINE) { Sprite() }
    private var spriteCount = 0

    // Mode 3 BG pipeline (DMG). The collaborators talk only to the Bus; the shifter lives here.
    private val bgFifo = PixelFifo(FIFO_CAPACITY)
    private val bgFetcher = BgFetcher(bus)
    private var lcdX = 0            // visible pixels output on the current line, 0..160
    private var discard = 0         // remaining fine-scroll (SCX & 7) pixels to drop, latched per line

    // Mode 3 sprite pipeline (DMG). The sprite FIFO is an 8-wide window aligned to lcdX.
    private val spriteFifo = PixelFifo(SPRITE_FIFO_CAPACITY)
    private val spriteFetcher = SpriteFetcher(bus)
    private var fetchingSprite = false                        // a sprite fetch is in progress (BG paused)
    private val spriteFetched = BooleanArray(MAX_SPRITES_PER_LINE)  // which selected sprites are done

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
                // One dot of mode 3. A sprite fetch pauses the BG fetcher and the shifter (the
                // stall that grows mode 3 beyond 172 + SCX&7). Order per dot: finish an in-flight
                // sprite fetch; else start one if a sprite begins here; else advance BG + shift.
                if (fetchingSprite) {
                    if (spriteFetcher.tick(spriteFifo)) fetchingSprite = false
                } else {
                    val due = if (objEnabled() && bgFifo.size > 0 && discard == 0) nextSpriteAt(lcdX) else -1
                    if (due >= 0) {
                        spriteFetched[due] = true
                        spriteFetcher.start(sprites[due], line)
                        fetchingSprite = true
                    } else {
                        bgFetcher.tick(bgFifo)
                        shiftPixel()
                    }
                }
                if (drawingDone) enterHBlank()
            }
            Mode.HBLANK -> {
                // Idle until the line ends. HBlank length is emergent: whatever remains of the
                // 456 dots after mode 2 + mode 3 — never computed, just what's left.
            }
            Mode.VBLANK -> {
                // Idle. The whole line is mode 1.
            }
        }

        // LY153 quirk: on the final line, LY reads 153 only briefly, then 0 for the rest of the
        // line (still in VBlank). Structure in place; exact timing to pin.
        // TODO: pin LY153_VISIBLE_DOTS against mooneye ppu (ly / lyc-153 timing) + the Python sim.
        if (line == LAST_LINE && lineDot == LY153_VISIBLE_DOTS) pushLy(0)

        lineDot++
        if (lineDot == DOTS_PER_LINE) endOfLine()
    }

    private fun endOfLine() {
        lineDot = 0
        line++
        if (line == LINES_PER_FRAME) {
            line = 0
            frameComplete()
        }
        pushLy(line)
        when {
            line < VISIBLE_LINES -> enterOamScan()   // lines 0..143 draw: start mode 2
            line == VISIBLE_LINES -> enterVBlank()    // line 144: enter VBlank
            // lines 145..153: already in VBlank (mode stays 1), nothing to change
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
        if (statEnabled(STAT_MODE2_IRQ)) requestStatIrq()
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
        // There is no mode-3 STAT interrupt source.
        // TODO: window trigger + fetcher restart (the window is still unimplemented).
    }

    /** One dot of the shifter: pop BG (and the lockstep sprite pixel), mix, and write the frame. */
    private fun shiftPixel() {
        if (bgFifo.size == 0) return           // FIFO empty: warm-up or fetcher stall, shifter waits
        val bgIndex = bgFifo.pop()
        // The sprite FIFO advances in lockstep with the BG FIFO so it stays aligned to lcdX.
        val spritePixel = if (spriteFifo.size > 0) spriteFifo.pop() else 0
        if (discard > 0) {                     // fine-scroll: popped but not displayed
            discard--
            return
        }
        frameBuffer[line * SCREEN_WIDTH + lcdX] = mix(bgIndex, spritePixel)
        lcdX++
        if (lcdX == SCREEN_WIDTH) drawingDone = true
    }

    /** BG/sprite priority + palette, all read LIVE so mid-line palette writes are honoured. */
    private fun mix(bgIndex: Int, spritePixel: Int): Int {
        val spriteColor = spritePixel and SpriteFetcher.PIXEL_COLOR
        if (spriteColor != 0 && objEnabled()) {                // opaque sprite pixel, OBJ on
            val behind = spritePixel and SpriteFetcher.PIXEL_PRIORITY != 0
            if (!(behind && bgIndex != 0)) {                   // sprite wins unless behind an opaque BG
                val obp = if (spritePixel and SpriteFetcher.PIXEL_PALETTE != 0) bus.read(REG_OBP1)
                else bus.read(REG_OBP0)
                return (obp shr (spriteColor * 2)) and 0x03
            }
        }
        val bgp = bus.read(REG_BGP)                            // BG wins
        return (bgp shr (bgIndex * 2)) and 0x03
    }

    /** First not-yet-fetched selected sprite that starts at [x], scanned in OAM order, or -1. */
    private fun nextSpriteAt(x: Int): Int {
        for (i in 0 until spriteCount) {
            if (!spriteFetched[i] && sprites[i].x - 8 == x) return i
        }
        return -1
    }

    private fun objEnabled(): Boolean = bus.read(REG_LCDC) and LCDC_OBJ_ENABLE != 0

    private fun enterHBlank() {
        setMode(Mode.HBLANK)
        if (statEnabled(STAT_MODE0_IRQ)) requestStatIrq()
        // Mode 3 -> 0 edge = exactly one HBlank per visible line. The Bus pumps one HBlank-DMA
        // block here if a transfer is active (no-op otherwise); it stays ignorant of the PPU.
        bus.stepHblankDma()
    }

    private fun enterVBlank() {
        setMode(Mode.VBLANK)
        requestVBlankIrq()                                 // IF bit 0 — always, on line 144 entry
        if (statEnabled(STAT_MODE1_IRQ)) requestStatIrq()   // STAT mode-1 source
    }

    // ----- LY projection + interrupts -----

    private fun pushLy(value: Int) {
        bus.ppuLy = value
        checkLycInterrupt(value)
    }

    private fun checkLycInterrupt(visibleLy: Int) {
        // Simple event-based coincidence: fire when the freshly visible LY equals LYC and the
        // LYC STAT source is enabled.
        // TODO (STAT IRQ phase): replace the per-source event firing here and in enterX() with a
        //  single rising-edge check on the combined STAT line (STAT blocking). The naive version
        //  can double-fire when sources overlap; a previous rising-edge attempt caused regressions
        //  and was reverted — revisit with mooneye stat_irq.
        if (visibleLy == bus.read(REG_LYC) && statEnabled(STAT_LYC_IRQ)) requestStatIrq()
    }

    private fun statEnabled(mask: Int): Boolean = bus.read(REG_STAT) and mask != 0

    private fun requestVBlankIrq() { bus.setIF(bus.iF or IF_VBLANK) }
    private fun requestStatIrq() { bus.setIF(bus.iF or IF_STAT) }

    // ----- LCD power on/off -----

    private fun powerOnLcd() {
        lcdOn = true
        dotDivider = false
        line = 0
        lineDot = 0
        drawingDone = false
        // Start a fresh frame at the top: line 0, mode 2. No interrupt is fired on enable.
        // (The Bus seeds ppuMode = 1 post-boot only to cover reads before this first tick.)
        // TODO (lcd-on quirk, deferred): the first frame after enabling the LCD is special —
        //  mode 3 on the first line is shorter and mode timing is shifted. Pin later with
        //  lcdon_timing + the Python simulator; the FSM starts a normal frame for now.
        setMode(Mode.OAM_SCAN)
        oamScanDot = 0
        bus.ppuLy = 0
    }

    private fun powerOffLcd() {
        // LCD off: the PPU is frozen, LY reads 0 and the mode reads 0 (HBlank). No interrupts.
        lcdOn = false
        line = 0
        lineDot = 0
        setMode(Mode.HBLANK)
        bus.ppuLy = 0
        // TODO: also reset fetcher / FIFO / window state here once they exist.
    }

    private fun frameComplete() {
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

        // LCDC / STAT bit masks
        private const val LCDC_ENABLE = 0x80     // LCDC.7: LCD & PPU enable
        private const val LCDC_OBJ_ENABLE = 0x02 // LCDC.1: sprites on/off
        private const val LCDC_OBJ_SIZE = 0x04   // LCDC.2: sprite height (0 = 8x8, 1 = 8x16)

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
    }
}
