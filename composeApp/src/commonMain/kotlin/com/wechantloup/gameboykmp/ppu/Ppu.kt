package com.wechantloup.gameboykmp.ppu

import com.wechantloup.gameboykmp.bus.Bus
import kotlinx.coroutines.channels.Channel

/**
 * Dot-driven PPU — mode FSM skeleton (timing only).
 *
 * This is the backbone of the dot-accurate rewrite: it owns the scanline/dot timing and the
 * mode state machine, and nothing else yet. There is deliberately NO fetcher, NO pixel FIFO,
 * NO framebuffer — those arrive in the renderer phase and plug into the joint documented below.
 *
 * Cadence: tick() is called once per T-cycle by the emulation loop. In single speed one tick
 * advances one dot; in double speed the LCD keeps its normal rate, so the PPU advances one dot
 * every two ticks (internal divider, see dotDivider).
 *
 * Single authorities (no mirroring — matches the Bus surface):
 *   - line        : the internal scanline 0..153. The only authority for "which line".
 *   - bus.ppuLy   : the CPU-visible LY (0xFF44). Projection of `line`, with the LY153 quirk.
 *   - bus.ppuMode : the CPU-visible mode (0..3). Projected into STAT bits 0-1 by the Bus.
 *   - coincidence : derived by the Bus at STAT read (ppuLy == LYC), never stored here.
 *
 * The mode 3 -> mode 0 joint:
 *   Mode 3 has an EMERGENT duration — it lasts as long as the pipeline takes to shift out 160
 *   pixels. The FSM must never compute that duration. It leaves mode 3 only when `drawingDone`
 *   is set. Today a non-calibrated stub sets it; the future fetcher/FIFO will set it instead,
 *   WITHOUT any change to this FSM. That is the whole point of the joint.
 *
 * Included in this skeleton: dot/line counting, the 2->3->0 (visible) and 1 (VBlank) mode FSM,
 * LY progression + frame wrap, the LY153 quirk (structure; exact dots TODO), LCD on/off,
 * the VBlank interrupt, a simple event-based STAT interrupt, and the HBlank-DMA edge call.
 *
 * Deferred (each marked with a TODO at its site): the real OAM scan + sprite list, the fetcher/
 * FIFO renderer + framebuffer, the LCD-on first-frame quirk, the STAT-blocking / rising-edge
 * refinement, and double-speed switch-phase anchoring.
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
    private var oamScanDot = 0      // dots elapsed in mode 2 (stub counter)
    private var drawingDot = 0      // dots elapsed in mode 3 (stub counter)
    private var drawingDone = false // the mode 3 -> 0 joint; the fetcher/FIFO will drive this
    private var lcdOn = false       // tracks LCDC.7 edges for power on/off
    private var dotDivider = false  // double-speed dot divider phase

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
                // TODO: the real mode 2 scans OAM and builds the <=10 sprite list for this line.
                //  Stub: just consume the fixed 80 dots, no sprite selection yet.
                oamScanDot++
                if (oamScanDot == OAM_SCAN_DOTS) enterDrawing()
            }
            Mode.DRAWING -> {
                // TODO: the fetcher/FIFO renders one dot here and sets drawingDone once 160 px
                //  have been shifted out. The FSM leaves mode 3 ONLY on that signal — never on a
                //  duration. Until the fetcher exists, a non-calibrated stub stands in for it.
                drawingDot++
                if (drawingDot >= STUB_DRAWING_DOTS) drawingDone = true
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
        if (statEnabled(STAT_MODE2_IRQ)) requestStatIrq()
    }

    private fun enterDrawing() {
        setMode(Mode.DRAWING)
        drawingDot = 0
        drawingDone = false
        // There is no mode-3 STAT interrupt source.
        // TODO: initialise fetcher / FIFO / window / fine-scroll state here once they exist.
    }

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
        // TODO: the FIFO renderer writes real pixels into a persistent framebuffer and emits it
        //  here (palette index on DMG, RGB555 on CGB/CGB_COMPAT — the frameChannel contract).
        //  Until then, emit a blank placeholder so the output pipeline and frame cadence can be
        //  validated end-to-end. The renderer will reuse one buffer instead of allocating per frame.
        frameChannel.trySend(IntArray(SCREEN_PIXELS))
    }

    companion object {
        private const val SCREEN_WIDTH = 160
        private const val SCREEN_HEIGHT = 144
        private const val SCREEN_PIXELS = SCREEN_WIDTH * SCREEN_HEIGHT

        private const val DOTS_PER_LINE = 456
        private const val VISIBLE_LINES = 144    // lines 0..143 draw
        private const val LINES_PER_FRAME = 154  // + 10 VBlank lines (144..153)
        private const val LAST_LINE = 153
        private const val OAM_SCAN_DOTS = 80     // mode 2 length (fixed)

        // Non-calibrated placeholder for the emergent mode-3 duration. It exists only so the FSM
        // produces coherent per-line timing before the fetcher/FIFO exists. It is deliberately
        // NOT tuned to pass any test — the real duration emerges from the FIFO. 172 is the
        // hardware minimum, used purely as a plausible stand-in.
        // TODO: delete when the fetcher drives drawingDone.
        private const val STUB_DRAWING_DOTS = 172

        // LY153 quirk: how long LY still reads 153 at the start of the last line before reading 0.
        // TODO: pin against mooneye ppu ly/lyc-153 timing + the Python simulator.
        private const val LY153_VISIBLE_DOTS = 4

        // LCDC / STAT bit masks
        private const val LCDC_ENABLE = 0x80     // LCDC.7: LCD & PPU enable

        private const val STAT_MODE0_IRQ = 0x08  // STAT bit 3: HBlank source
        private const val STAT_MODE1_IRQ = 0x10  // STAT bit 4: VBlank source
        private const val STAT_MODE2_IRQ = 0x20  // STAT bit 5: OAM source
        private const val STAT_LYC_IRQ = 0x40    // STAT bit 6: LY == LYC source

        private const val IF_VBLANK = 0x01       // IF bit 0
        private const val IF_STAT = 0x02         // IF bit 1

        // I/O registers the PPU reads
        private const val REG_LCDC = 0xFF40
        private const val REG_STAT = 0xFF41
        private const val REG_LYC = 0xFF45
    }
}
