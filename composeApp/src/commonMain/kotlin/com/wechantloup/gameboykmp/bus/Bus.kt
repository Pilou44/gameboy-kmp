package com.wechantloup.gameboykmp.bus

import com.wechantloup.gameboykmp.cartridge.Cartridge
import com.wechantloup.gameboykmp.cpu.CpuBus
import com.wechantloup.gameboykmp.cpu.MachineMode
import com.wechantloup.gameboykmp.joypad.JoypadButton
import kotlin.concurrent.Volatile

/**
 * Represents the Game Boy memory bus - the full 64KB addressable space.
 *
 * Memory map:
 * 0x0000 - 0x3FFF  ROM Bank 0 (cartridge, fixed)
 * 0x4000 - 0x7FFF  ROM Bank N (cartridge, switchable via MBC)
 * 0x8000 - 0x9FFF  VRAM (Video RAM)
 * 0xA000 - 0xBFFF  External RAM (cartridge)
 * 0xC000 - 0xCFFF  Work RAM Bank 0
 * 0xD000 - 0xDFFF  Work RAM Bank 1
 * 0xE000 - 0xFDFF  Echo RAM (mirror of 0xC000-0xDDFF, avoid using)
 * 0xFE00 - 0xFE9F  OAM (Sprite Attribute Table)
 * 0xFEA0 - 0xFEFF  Unusable
 * 0xFF00 - 0xFF7F  I/O Registers
 * 0xFF80 - 0xFFFE  High RAM (HRAM)
 * 0xFFFF           Interrupt Enable Register
 */
class Bus(
    private val cartridge: Cartridge,
    override val machineMode: MachineMode,
    override val bootRom: ByteArray?,
): CpuBus {

    private var bootRomEnabled: Boolean = bootRom != null

    // --- System counter (DIV) ---
    // The free-running 16-bit counter. DIV ($FF04) is its high byte (read = sysCounter ushr 8).
    // Advanced one T-cycle at a time by tick(); the Timer clocks TIMA off falling edges of
    // bits 3/5/7/9, and (later) the APU frame sequencer off bit 12/13. Post-boot the DMG hands
    // off at 0xABCC (see boot_div); with a boot ROM present it starts at 0 and counts up live.
    var sysCounter: Int = if (bootRom == null) POST_BOOT_DIV_COUNTER else 0
        private set
    // sysCounter *before* the current T's increment. Edge-detection consumers (Timer, APU)
    // compare (prevSysCounter, sysCounter) across the single increment done by tick().
    var prevSysCounter: Int = sysCounter
        private set

    // Bit 0 : V-Blank  - PPU entered V-Blank period (LY == 144)
    // Bit 1 : LCD STAT - PPU mode change or LY==LYC coincidence (depends on STAT bits 3-6)
    // Bit 2 : Timer    - TIMA overflowed and was reloaded from TMA
    // Bit 3 : Serial   - Serial transfer complete
    // Bit 4 : Joypad   - Joypad button pressed (high-to-low transition)
    // Bits 5-7 : unused, always read as 1
    override val ie: Int get() = read(0xFFFF) // Enabled interrupts
    override val iF: Int get() = read(0xFF0F) // Requested interrupts

    private val internalRam = IntArray(0x10000).also {
        if (bootRom == null) initPostBootRegisters(it)
    }

    // VRAM: 2 banks of 8KB. Bank 1 is CGB-only (BG attribute map + extra tile data).
    // On DMG vramBank stays 0, so bank 1 is allocated but never touched.
    private val vram = Array(2) { IntArray(0x2000) }
    private var vramBank = 0  // VBK (0xFF4F bit 0); pinned to 0 on DMG (write is CGB-gated)

    // WRAM: 8 banks of 4KB. 0xC000-0xCFFF is always bank 0; 0xD000-0xDFFF is banked
    // via SVBK. Banks 2-7 are CGB-only; on DMG wramBank stays 1, reproducing the old
    // flat 0xC000-0xDFFF region that used to live inside internalRam.
    private val wram = Array(8) { IntArray(0x1000) }
    private var wramBank = 1  // SVBK; effective bank for 0xD000-0xDFFF, always 1..7

    private var hdma1 = 0  // HDMA1 source high
    private var hdma2 = 0  // HDMA2 source low
    private var hdma3 = 0  // HDMA3 dest high
    private var hdma4 = 0  // HDMA4 dest low

    // Active HBlank DMA state (used in the next step)
    private var hdmaActive = false
    private var hdmaSource = 0     // running source address
    private var hdmaDest = 0       // running dest offset within VRAM (0x0000-0x1FF0)
    private var hdmaRemaining = 0  // remaining 0x10-byte chunks

    // M-cycles the CPU must be stalled by the general-purpose DMA that just started. Published as
    // a primitive (like cpuHalted / ppuMode) because the Bus cannot call the CPU: the CPU drains
    // this via onMachineCycleTick at the next instruction boundary, then resets it to 0.
    override var pendingGdmaStallMCycles = 0

    // Near the other CGB state (e.g. after the hdma fields):
    // Double-speed (CGB). CPU, timer/DIV and serial run twice as fast in double speed;
    // the PPU and APU keep their normal rate. Armed via KEY1 bit 0, committed by STOP.
    var isDoubleSpeed = false
        private set
    private var speedSwitchArmed = false
    override var cpuHalted: Boolean = false


    // CGB palette RAM: 8 palettes × 4 colors × 2 bytes each, stored raw as RGB555 LE.
    // Color conversion to ARGB is deferred to the render step.
    private val bgPaletteRam = IntArray(0x40)
    private val objPaletteRam = IntArray(0x40)
    private var bgPaletteIndex = 0       // BCPS bits 0-5
    private var bgPaletteAutoInc = false // BCPS bit 7
    private var objPaletteIndex = 0      // OCPS bits 0-5
    private var objPaletteAutoInc = false

    // OPRI (FF6C) bit 0: OBJ priority mode. Read-back latch ONLY — the priority mode is latched at
    // boot and hardcoded per render path (see Ppu), so this value never drives rendering, matching
    // hardware where post-boot writes have no effect on priority. With a boot ROM the boot writes it
    // ($01 in DMG-compat, untouched/$00 in CGB); without one we reproduce that residue here.
    private var opri = if (bootRom == null && machineMode == MachineMode.CGB_COMPAT) 1 else 0

    private val oam = IntArray(0xA0) // 160 bytes = 40 sprites × 4 bytes
    @Volatile
    private var joypadState = 0xFF  // all buttons released

    // --- DMA state ---
    private var dmaCounter = 0       // active transfer: 162→0 (2 setup ticks + 160 copies); 0 = inactive
    private var dmaSourceBase = 0    // source address of the active transfer (sourceHighByte shl 8)
    private var dmaRestartDelay = 0  // M-cycles before a restart requested mid-transfer takes over; 0 = none
    private var dmaRestartSource = 0 // source address for the pending restart

    // OAM is locked once the transfer reaches its copy phase (dmaCounter 160→1),
    // AND for the whole duration of a pending restart — otherwise OAM would briefly
    // become accessible if the previous transfer ends before the restart fires.
    // The 2 setup ticks (162/161) of a fresh transfer leave OAM accessible for
    // ~1 M-cycle, exactly as real hardware does.
    val isDmaActive: Boolean get() = dmaCounter in 1..160 || dmaRestartDelay > 0

    var ppuMode: Int = 0
    // Bus: plain primitive field, flipped by the PPU when the first-frame window opens/closes.
    var ppuDotOverrideActive: Boolean = false


    /**
     * Callback invoked when the APU is powered off (NR52 bit 7: 1 -> 0).
     * The APU should register here to reset its channels' internal state.
     */
    var onApuPowerOff: (() -> Unit)? = null

    var onChannel1Trigger: (() -> Unit)? = null
    var onChannel2Trigger: (() -> Unit)? = null
    var onChannel3Trigger: (() -> Unit)? = null
    var onChannel4Trigger: (() -> Unit)? = null
    var onChannel1LengthWrite: ((Int) -> Unit)? = null
    var onChannel2LengthWrite: ((Int) -> Unit)? = null
    var onChannel3LengthWrite: ((Int) -> Unit)? = null
    var onChannel4LengthWrite: ((Int) -> Unit)? = null
    var onChannel1DacWrite: ((Int) -> Unit)? = null
    var onChannel2DacWrite: ((Int) -> Unit)? = null
    var onChannel3DacWrite: ((Int) -> Unit)? = null
    var onChannel4DacWrite: ((Int) -> Unit)? = null
    var onChannel1ControlWrite: ((Int) -> Unit)? = null
    var onChannel2ControlWrite: ((Int) -> Unit)? = null
    var onChannel3ControlWrite: ((Int) -> Unit)? = null
    var onChannel4ControlWrite: ((Int) -> Unit)? = null
    var onChannel1Nr10Write: ((Int) -> Unit)? = null
    var onWaveRamRead: ((Int) -> Int)? = null
    var onWaveRamWrite: ((Int, Int) -> Unit)? = null
    var onApuDivReset: (() -> Unit)? = null
    var onApuPowerOn: (() -> Unit)? = null

    var onDivReset: (() -> Unit)? = null
    var onTacWrite: ((Int, Int) -> Unit)? = null
    var canWriteOnTima: () -> Boolean = { true }
    var onTimaWrite: () -> Unit = { }
    var timaReadOverride: (() -> Int?)? = null

    var onStatWrite: (() -> Unit)? = null
    var onLycWrite: (() -> Unit)? = null
    var ppuSampler: ((Int) -> Int?)? = null
    var ppuWriteIntercept: ((Int, Int) -> Boolean)? = null
    var onBgpWrite: ((Int) -> Unit)? = null

    val apuPoweredOn: Boolean get() = internalRam[0xFF26] and 0x80 != 0

    init {
        if (bootRom == null && machineMode == MachineMode.CGB_COMPAT) {
            initCompatPalettes()
        }
    }

    // Advances the system counter by one T-cycle. Called once per T from the emulation loop,
    // after cpu.tick() and before the edge-detection consumers (timer.tick(), later apu.tick()).
    fun tick() {
        prevSysCounter = sysCounter
        sysCounter = (sysCounter + 1) and 0xFFFF
    }

    /**
     * Advances the OAM DMA by one M-cycle.
     * Must be called once per M-cycle from the emulation loop.
     *
     * Timing:
     *   dmaCounter = 162/161 : setup ticks — OAM still accessible, no byte copied
     *   dmaCounter = 160 : copy byte 0 (OAM now locked)
     *   ...
     *   dmaCounter =   1 : copy byte 159 → DMA done on next tick
     */
    fun stepDma() {
        // A restart requested while a transfer was still copying takes over only
        // after a 2 M-cycle delay; until then the previous transfer keeps running
        // (and keeps OAM locked). The delay itself serves as the new startup.
        if (dmaRestartDelay > 0) {
            dmaRestartDelay--
            if (dmaRestartDelay == 0) {
                dmaSourceBase = dmaRestartSource
                dmaCounter = 160 // copy phase begins right away (no extra setup ticks)
            }
        }
        if (dmaCounter <= 0) return
        if (dmaCounter < 161) {
            val byteIndex = 160 - dmaCounter
            oam[byteIndex] = readDmaSource(dmaSourceBase + byteIndex)
        }
        dmaCounter--
    }

    /**
     * Reads one byte for an OAM DMA transfer. The DMA controller has its own
     * memory bus, so it ignores the CPU-facing OAM/VRAM access locks and the I/O
     * register mapping. Any source in $E000-$FFFF is read as a WRAM echo
     * (address - $2000): this is why the region past $DFFF (including
     * $FE00-$FFFF) mirrors WRAM instead of OAM / the I/O area.
     */
    private fun readDmaSource(address: Int): Int = when (address) {
        in 0x0000..0x7FFF -> cartridge.readRom(address)
        in 0x8000..0x9FFF -> readVram(address - 0x8000)
        in 0xA000..0xBFFF -> cartridge.readRam(address - 0xA000)
        in 0xC000..0xDFFF -> readWram(address)
        else -> readWram(address - 0x2000) // $E000-$FFFF: WRAM echo
    }

    override fun read(address: Int): Int {
        // Cheap gate: one boolean test on the common path, no call, no boxing.
        if (ppuDotOverrideActive) {
            ppuSampler?.invoke(address)?.let { return it }   // dot-accurate override, null = fall through
            // TODO replace with:
//            val sampled = samplePpuRead(address) // concrete method, Int sentinel, NOT a (Int)->Int?
//            if (sampled >= 0) return sampled
        }

        // CGB-only registers are resolved before the DMG when() below, so that path
        // stays byte-for-byte identical on DMG. null = not a CGB register, fall through.
        if (hasCgbRegisters) {
            readCgbRegister(address)?.let { return it }
        }

        return when (address) {
            in 0x8000..0x9FFF ->
                if (ppuMode == 3) 0xFF
                else readVram(address - 0x8000)
            in 0xE000..0xFDFF -> read(address - 0x2000) // Echo RAM: 0xE000–0xFDFF == 0xC000–0xDDFF
            in 0xFE00..0xFE9F ->
                if (isDmaActive || ppuMode == 2 || ppuMode == 3) 0xFF
                else readOam(address - 0xFE00)
            0xFF00 -> {
                val p1 = internalRam[0xFF00]
                // Bits 0-3 are active-low: 0=pressed, 1=released
                // Bit 5 selects direction keys, bit 4 selects action buttons
                val result = when {
                    p1 and 0x20 == 0 -> (p1 and 0xF0) or (joypadState shr 4 and 0x0F)  // directions
                    p1 and 0x10 == 0 -> (p1 and 0xF0) or (joypadState and 0x0F)         // buttons
                    else -> p1 or 0x0F
                }
                result or 0xC0 // bits 7-6 always read as 1
            }
            0xFF02 -> internalRam[0xFF02] or 0x7E  // SC: unused bits always read as 1 on DMG
            0xFF03, in 0xFF08..0xFF0E -> 0xFF  // Unused I/O registers, always read 0xFF on DMG
            0xFF04 -> sysCounter ushr 8  // DIV = high byte of the system counter (live projection)
            0xFF05 -> timaReadOverride?.invoke() ?: internalRam[0xFF05]
            0xFF07 -> internalRam[0xFF07] or 0xF8  // TAC: bits 7-3 always read as 1 on DMG
            0xFF41 -> internalRam[0xFF41] or 0x80  // STAT: bit 7 always reads as 1 on DMG
            in 0xFF4C..0xFF7F -> 0xFF  // GBC registers and unused I/O, always read 0xFF on DMG

            // There's a hole in boot rom at addresses 0x0100..0x01FF to read cartridge
            in 0x0100..0x01FF -> cartridge.readRom(address)
            in 0x0000..0x08FF -> if (bootRomEnabled) {
                requireNotNull(bootRom)[address].toInt() and 0xFF
            } else {
                cartridge.readRom(address)
            }
            in 0x0900..0x7FFF -> cartridge.readRom(address)

            in 0x8000..0x9FFF -> readVram(address - 0x8000)
            in 0xA000..0xBFFF -> cartridge.readRam(address - 0xA000)
            in 0xC000..0xDFFF -> readWram(address)
            in 0xFE00..0xFE9F -> if (isDmaActive) 0xFF else readOam(address - 0xFE00)
            in 0xFF10..0xFF3F -> readApuRegister(address)
            0xFF0F -> internalRam[0xFF0F] or 0xE0  // IF: upper 3 bits always read as 1
            else -> internalRam[address]
        }
    }

    override fun write(address: Int, value: Int) {
        val v = value and 0xFF
        if (ppuDotOverrideActive) {
            if (ppuWriteIntercept?.invoke(address, v) == true) return
        }

        // CGB-only register writes are intercepted before the DMG path (same rationale
        // as read). v is the already-masked value.
        if (hasCgbRegisters && writeCgbRegister(address, v)) return

        when (address) {
            0xFF50 -> {
                internalRam[0xFF50] = v
                if (v and 0x01 > 0) {
                    bootRomEnabled = false
                }
            }
            // TODO: VRAM lock is gated on ppuMode at M-cycle granularity, so writes landing
            //  within a few dots of the mode 3<->0 boundary can be misclassified (dropped when
            //  hardware would accept them, or vice-versa). Visible on blargg cpu_instrs 06 in CGB
            //  (the 'd' of "Passed" is dropped; SameBoy shows it). A precise fix needs dot/T-state
            //  PPU stepping (or a per-access PPU catch-up). Cosmetic only: serial pass/fail is
            //  unaffected. Revisit with the T-state rendering refactor.
            in 0x8000..0x9FFF -> if (ppuMode != 3) writeVram(address - 0x8000, v)
            in 0xE000..0xFDFF -> write(address - 0x2000, v) // Echo RAM: 0xE000–0xFDFF == 0xC000–0xDDFF
            in 0xFE00..0xFE9F -> if (!isDmaActive && ppuMode != 2 && ppuMode != 3) writeOam(address - 0xFE00, v)
            0xFF04 -> {
                onDivReset?.invoke()   // timer phantom-edge check reads the current sysCounter
                sysCounter = 0         // reset AFTER the edge check — was Timer's cycleCount = 0
                onApuDivReset?.invoke()
            }
            0xFF05 -> {
                if (canWriteOnTima()) {
                    internalRam[address] = v
                    onTimaWrite()
                }
                // Write silently ignored during the reload M-cycle
            }
            0xFF07 -> {
                val oldTac = internalRam[0xFF07]
                internalRam[0xFF07] = v
                onTacWrite?.invoke(oldTac, v)
            }
            0xFF46 -> triggerDmaTransfer(v)
            0xFF47 -> {
                internalRam[0xFF47] = v
                onBgpWrite?.invoke(v)
            }
            0xFF26 -> writeNR52(v)

            // Length registers: writable even when APU is off (DMG quirk)
            0xFF11 -> {
                internalRam[address] = if (apuPoweredOn) {
                    v
                } else {
                    (internalRam[address] and 0xC0) or (v and 0x3F)
                }
                onChannel1LengthWrite?.invoke(v)
            }
            0xFF16 -> {
                internalRam[address] = if (apuPoweredOn) {
                    v
                } else {
                    (internalRam[address] and 0xC0) or (v and 0x3F)
                }
                onChannel2LengthWrite?.invoke(v)
            }
            0xFF1B -> {
                internalRam[address] = if (apuPoweredOn) {
                    v
                } else {
                    (internalRam[address] and 0xC0) or (v and 0x3F)
                }
                onChannel3LengthWrite?.invoke(v)
            }
            0xFF20 -> {
                internalRam[address] = if (apuPoweredOn) {
                    v
                } else {
                    (internalRam[address] and 0xC0) or (v and 0x3F)
                }
                onChannel4LengthWrite?.invoke(v)
            }

            // All other APU registers: ignored when APU is off
            in 0xFF10..0xFF25 -> if (apuPoweredOn) {
                internalRam[address] = v
                when (address) {
                    // TODO: bit 7 (trigger) is write-only on real hardware and should not be stored
                    0xFF10 -> {
                        onChannel1Nr10Write?.invoke(v)
                    }
                    0xFF14 -> {
                        onChannel1ControlWrite?.invoke(v)
                        if (v and 0x80 != 0) onChannel1Trigger?.invoke()
                    }
                    0xFF19 -> {
                        onChannel2ControlWrite?.invoke(v)
                        if (v and 0x80 != 0) onChannel2Trigger?.invoke()
                    }
                    0xFF1E -> {
                        onChannel3ControlWrite?.invoke(v)
                        if (v and 0x80 != 0) onChannel3Trigger?.invoke()
                    }
                    0xFF23 -> {
                        onChannel4ControlWrite?.invoke(v)
                        if (v and 0x80 != 0) onChannel4Trigger?.invoke()
                    }
                    0xFF12 -> onChannel1DacWrite?.invoke(v)
                    0xFF17 -> onChannel2DacWrite?.invoke(v)
                    0xFF1A -> onChannel3DacWrite?.invoke(v)
                    0xFF21 -> onChannel4DacWrite?.invoke(v)
                }
            }
            in 0xFF30..0xFF3F -> onWaveRamWrite?.invoke(address, v)
            0xFF41 -> {
                // STAT bits 0-2 (mode + LYC coincidence flag) are owned by the PPU and are
                // read-only for the CPU; only the interrupt-enable bits 3-6 are writable.
                val current = internalRam[0xFF41]
                internalRam[0xFF41] = (current and 0x07) or (v and 0x78)
                onStatWrite?.invoke()
            }
            0xFF45 -> {
                // Writing LYC must re-evaluate the LY == LYC coincidence right away.
                internalRam[0xFF45] = v
                onLycWrite?.invoke()
            }
            in 0x0000..0x7FFF -> cartridge.writeRom(address, v)
            in 0x8000..0x9FFF -> writeVram(address - 0x8000, v)
            in 0xA000..0xBFFF -> cartridge.writeRam(address - 0xA000, v)
            in 0xC000..0xDFFF -> writeWram(address, v)
            in 0xFE00..0xFE9F -> if (!isDmaActive) writeOam(address - 0xFE00, v)
            else -> internalRam[address] = v
        }
    }

    /**
     * Direct register read bypassing APU CPU-view masks.
     * For internal APU use only — returns the raw stored value.
     */
    fun readRaw(address: Int): Int = internalRam[address]

    /**
     * Direct register write bypassing APU CPU-view callbacks.
     * For internal APU use only — writes directly to internal RAM.
     */
    fun writeRaw(address: Int, value: Int) {
        internalRam[address] = value
    }

    /**
     * Called by channels to update their status bit in NR52 (bits 3-0).
     * Bypasses the normal write path to avoid triggering power-off logic.
     */
    fun setChannelEnabled(channelBit: Int, enabled: Boolean) {
        val current = internalRam[0xFF26]
        internalRam[0xFF26] = if (enabled) current or channelBit else current and channelBit.inv()
    }

    fun setButtonPressed(button: JoypadButton) {
        val mask = buttonMask(button)
        joypadState = joypadState and mask.inv()  // set bit to 0 (active-low)
        // Trigger joypad interrupt
        setIF(iF or 0x10)
    }

    fun setButtonReleased(button: JoypadButton) {
        val mask = buttonMask(button)
        joypadState = joypadState or mask  // set bit to 1 (released)
    }

    /**
     * Commits a pending KEY1 speed switch. Called by the STOP instruction: if bit 0 of KEY1
     * was armed, the speed toggles and the armed flag clears. Returns true if a switch
     * actually happened, so STOP knows it was a speed switch rather than a normal stop.
     */
    override fun performSpeedSwitch(): Boolean {
        if (!speedSwitchArmed) return false
        isDoubleSpeed = !isDoubleSpeed
        speedSwitchArmed = false
        return true
    }

    /**
     * Transfers a single 16-byte block of an active HBlank DMA.
     *
     * Called by the PPU on every mode 3 -> mode 0 edge (one HBlank per visible
     * scanline). The PPU stays ignorant of HDMA: it only reports the edge; the Bus
     * decides whether a transfer is pending and whether it is allowed to run.
     *
     * Relies on this HDMA state (map the names to your existing infra):
     *  - hdmaActive          : true while an HBlank DMA is in progress
     *  - hdmaSource          : running source address, 16-aligned, set at FF55 start
     *  - hdmaDest            : running VRAM offset (0x0000-0x1FF0), set at FF55 start
     *  - hdmaBlocksRemaining : 16-byte blocks left; FF55 read-back = (this - 1)
     *  - cpuHalted           : published by the CPU
     */
    fun stepHblankDma() {
        if (!hdmaActive) return
        if (cpuHalted) return   // MagenTests quirk: HBlank DMA is suspended while the CPU is halted

        // One block = 16 bytes. The destination is written into the *current* VBK bank:
        // reading VBK live (single-arg writeVram) also handles a game changing VBK
        // mid-transfer, which is the hardware behaviour.
        // The source is never VRAM/OAM, so no PPU access gating applies.
        for (i in 0 until 16) {
            val byte = readDmaSource(hdmaSource + i)    // banked source read (MBC honoured)
            writeVram(hdmaDest + i, byte)       // single-arg -> current VBK bank
        }

        hdmaSource = (hdmaSource + 16) and 0xFFFF
        hdmaDest = (hdmaDest + 16) and 0x1FFF     // stay within the 8 KiB VRAM bank

        hdmaRemaining--
        if (hdmaRemaining == 0) {
            hdmaActive = false
            // FF55 must now read 0xFF. Make sure your read-back derives that from
            // hdmaActive == false rather than from a stale length byte.
        }

        // TODO (T-state tier): the CPU is not stalled for the per-block transfer cycles
        // (~8 M-cycles, doubled in double-speed). Deferred to the T-state refactor;
        // functionally the block still lands in the correct HBlank.
    }

    private fun buttonMask(button: JoypadButton): Int = when (button) {
        JoypadButton.RIGHT  -> 0x01
        JoypadButton.LEFT   -> 0x02
        JoypadButton.UP     -> 0x04
        JoypadButton.DOWN   -> 0x08
        JoypadButton.A      -> 0x10
        JoypadButton.B      -> 0x20
        JoypadButton.SELECT -> 0x40
        JoypadButton.START  -> 0x80
    }

    /**
     * NR52 (0xFF26) write handler.
     *
     * Only bit 7 (APU power) is writable by the CPU.
     * Bits 6-4 are always 1 (handled by readApuRegister mask).
     * Bits 3-0 reflect channel enable status and are read-only from the CPU's perspective;
     * they are updated directly via setChannelEnabled().
     *
     * Power off (bit 7: 1 -> 0):
     *   - Notifies APU to reset all channel internal state
     *   - Clears NR10-NR51 registers to 0
     *   - Clears NR52 entirely (including channel status bits 3-0)
     *
     * Power on (bit 7: 0 -> 1):
     *   - Sets bit 7 only; channel status bits remain 0
     *
     * Already on:
     *   - Only bit 7 is updated; channel status bits 3-0 are preserved
     */
    private fun writeNR52(value: Int) {
        val wasOn = internalRam[0xFF26] and 0x80 != 0
        val isOn = value and 0x80 != 0

        when {
            wasOn && !isOn -> {
                // Power off: notify APU, then clear all audio registers and channel status
                onApuPowerOff?.invoke()
                for (addr in 0xFF10..0xFF25) {
                    internalRam[addr] = 0
                }
                internalRam[0xFF26] = 0  // bit 7 = 0, status bits = 0
            }
            !wasOn && isOn -> {
                // Power on: set bit 7 only, channel status bits stay 0
                internalRam[0xFF26] = 0x80
                onApuPowerOn?.invoke()
            }
            else -> {
                // No power state change: preserve channel status bits 3-0
                val currentStatus = internalRam[0xFF26] and 0x0F
                internalRam[0xFF26] = (value and 0x80) or currentStatus
            }
        }
    }

    private fun readApuRegister(address: Int): Int {
        val raw = internalRam[address]
        return when (address) {
            0xFF10 -> raw or 0x80  // NR10 : bit 7 always 1
            0xFF11 -> raw or 0x3F  // NR11 : bits 5-0 write-only → read as 1
            0xFF12 -> raw          // NR12 : fully readable
            0xFF13 -> 0xFF         // NR13 : write-only
            // TODO: bit 7 reads as 1 due to or 0xBF mask, but it should reflect nothing — trigger bit is write-only
            0xFF14 -> raw or 0xBF  // NR14 : bits 5-0 and 7 read as 1, except bit 6
            0xFF15 -> 0xFF         // NR20 : unused, always 0xFF
            0xFF16 -> raw or 0x3F  // NR21 : bits 5-0 write-only
            0xFF17 -> raw          // NR22 : fully readable
            0xFF18 -> 0xFF         // NR23 : write-only
            // TODO: bit 7 reads as 1 due to or 0xBF mask, but it should reflect nothing — trigger bit is write-only
            0xFF19 -> raw or 0xBF  // NR24 : same mask as NR14
            0xFF1A -> raw or 0x7F  // NR30 : bits 6-0 always 1
            0xFF1B -> 0xFF         // NR31 : write-only
            0xFF1C -> raw or 0x9F  // NR32 : bits 4-0 and 7 always 1
            0xFF1D -> 0xFF         // NR33 : write-only
            // TODO: bit 7 reads as 1 due to or 0xBF mask, but it should reflect nothing — trigger bit is write-only
            0xFF1E -> raw or 0xBF  // NR34 : same mask as NR14
            0xFF1F -> 0xFF         // NR40 : unused, always 0xFF
//            0xFF20 -> raw or 0xFF  // NR41 : fully masked → always 0xFF
            0xFF20 -> 0xFF  // NR41 : write-only, reads as 0xFF
            0xFF21 -> raw          // NR42 : fully readable
            0xFF22 -> raw          // NR43 : fully readable
            // TODO: bit 7 reads as 1 due to or 0xBF mask, but it should reflect nothing — trigger bit is write-only
            0xFF23 -> raw or 0xBF  // NR44 : same mask as NR14
            0xFF24 -> raw          // NR50 : fully readable
            0xFF25 -> raw          // NR51 : fully readable
            0xFF26 -> raw or 0x70  // NR52 : bits 6-4 always 1
            in 0xFF27..0xFF2F -> 0xFF  // unused registers → read as 0xFF
            in 0xFF30..0xFF3F -> onWaveRamRead?.invoke(address) ?: raw  // Wave RAM : fully readable
            else -> raw
        }
    }

    private fun triggerDmaTransfer(sourceHighByte: Int) {
        internalRam[0xFF46] = sourceHighByte // store for readback at $FF46
        if (isDmaActive) {
            // Restart while a transfer is mid-copy: hardware does not stop the
            // running one immediately — the new one takes over after a delay,
            // during which OAM stays locked by the still-running transfer.
            // The delay is 3 so the restarted transfer unlocks OAM at the very
            // same offset a fresh DMA would (write +162 M-cycles): 3 + 159 = 162.
            dmaRestartSource = sourceHighByte shl 8
            dmaRestartDelay = 3
        } else {
            dmaSourceBase = sourceHighByte shl 8
            dmaCounter = 162 // 2 setup ticks (OAM still accessible) + 160 transfers
        }
    }

    /**
     * CGB-only register reads. Returns null when the address is not a CGB register,
     * letting read() fall through to the unchanged DMG path.
     *
     * TODO: in CGB_COMPAT the real boot ROM locks the palette registers after setup.
     *   Harmless for a DMG game (never accesses them), but the lock read-back semantics
     *   must be verified vs Pan Docs at the auto-palette step.
     */
    private fun readCgbRegister(address: Int): Int? = when (address) {
        // KEY1: bit 7 = current speed, bit 0 = switch armed, unused bits 1-6 read as 1.
        0xFF4D -> (if (isDoubleSpeed) 0x80 else 0) or (if (speedSwitchArmed) 0x01 else 0) or 0x7E
        0xFF4F -> vramBank or 0xFE  // VBK: only bit 0 is meaningful, bits 1-7 read as 1
        0xFF70 -> wramBank or 0xF8  // SVBK: bits 0-2 = bank, bits 3-7 read as 1
        // BCPS/OCPS: index in bits 0-5, auto-increment in bit 7, bit 6 reads as 1
        0xFF68 -> bgPaletteIndex or (if (bgPaletteAutoInc) 0x80 else 0) or 0x40
        0xFF6A -> objPaletteIndex or (if (objPaletteAutoInc) 0x80 else 0) or 0x40
        // BCPD/OCPD: the byte at the current index.
        // TODO: during PPU mode 3 these reads must return 0xFF (CGB palette access lock).
        0xFF69 -> bgPaletteRam[bgPaletteIndex]
        0xFF6B -> objPaletteRam[objPaletteIndex]
        // OPRI: bit 0 = OBJ priority mode (read-back only), bits 1-7 read as 1.
        0xFF6C -> opri or 0xFE
        0xFF55 -> (if (hdmaActive) 0x00 else 0x80) or ((hdmaRemaining - 1) and 0x7F)
        else -> null
    }

    /**
     * CGB-only register writes. Returns true when handled (write() returns early),
     * false to fall through to the DMG path.
     */
    private fun writeCgbRegister(address: Int, value: Int): Boolean = when (address) {
        // Only bit 0 (prepare switch) is writable; bit 7 in the written value is ignored
        0xFF4D -> { speedSwitchArmed = value and 0x01 != 0; true }
        0xFF4F -> { vramBank = value and 0x01; true }  // VBK: bit 0 selects the VRAM bank
        0xFF70 -> { wramBank = (value and 0x07).let { if (it == 0) 1 else it }; true }  // 0 selects bank 1
        0xFF68 -> { bgPaletteIndex = value and 0x3F; bgPaletteAutoInc = value and 0x80 != 0; true }  // BCPS
        0xFF6A -> { objPaletteIndex = value and 0x3F; objPaletteAutoInc = value and 0x80 != 0; true } // OCPS
        // BCPD/OCPD: write at the current index, then advance iff auto-increment is set.
        // The increment fires on write only, never on read.
        // TODO: during PPU mode 3 these writes must be ignored (CGB palette access lock).
        0xFF69 -> {
            bgPaletteRam[bgPaletteIndex] = value
            if (bgPaletteAutoInc) bgPaletteIndex = (bgPaletteIndex + 1) and 0x3F
            true
        }
        0xFF6B -> {
            objPaletteRam[objPaletteIndex] = value
            if (objPaletteAutoInc) objPaletteIndex = (objPaletteIndex + 1) and 0x3F
            true
        }
        0xFF6C -> {
            // OPRI: only bit 0 is writable
            opri = value and 0x01
            true
        }
        0xFF51 -> {
            hdma1 = value
//            Logger.debug("Bus", "W HDMA1=${value.toString(16)}")
            true
        }
        0xFF52 -> {
            hdma2 = value
//            Logger.debug("Bus", "W HDMA2=${value.toString(16)}")
            true
        }
        0xFF53 -> {
            hdma3 = value
//            Logger.debug("Bus", "W HDMA3=${value.toString(16)}")
            true
        }
        0xFF54 -> {
            hdma4 = value
//            Logger.debug("Bus", "W HDMA4=${value.toString(16)}")
            true
        }
        0xFF55 -> {
            startHdma(value)
            true
        }
        else -> false
    }

    // CPU-facing / active-bank access. VBK selects the bank; pinned to 0 on DMG,
    // so every existing caller reads bank 0 exactly as before.
    fun readVram(address: Int): Int = vram[vramBank][address]        // address 0x0000..0x1FFF
    fun writeVram(address: Int, value: Int) { vram[vramBank][address] = value }

    // WRAM routing. 0xC000-0xCFFF is the fixed bank 0; 0xD000-0xDFFF is the banked
    // region. wramBank is normalized to 1..7 on write, so no remap is needed here.
    private fun readWram(address: Int): Int =
        if (address < 0xD000) wram[0][address - 0xC000]
        else wram[wramBank][address - 0xD000]

    private fun writeWram(address: Int, value: Int) {
        if (address < 0xD000) wram[0][address - 0xC000] = value
        else wram[wramBank][address - 0xD000] = value
    }

    // Explicit-bank read — PPU entry point for CGB rendering (tile data bank 0/1,
    // attribute map always bank 1), independent of VBK. Unused until the CGB BG/sprite
    // rendering step; exercised by unit tests now.
    fun readVram(bank: Int, address: Int): Int = vram[bank][address]

    fun readOam(address: Int): Int = oam[address]
    fun writeOam(address: Int, value: Int) { oam[address] = value }

    override fun setIF(value: Int) = write(0xFF0F, value)

    // Reads a CGB BG color from palette RAM as a raw 15-bit RGB555 value (little-endian,
    // as stored). The RGB555 → ARGB expansion happens at display time, not here.
    // palette: 0-7, colorIndex: 0-3.
    fun bgColorRgb555(palette: Int, colorIndex: Int): Int {
        val offset = palette * 8 + colorIndex * 2   // 8 bytes per palette, 2 bytes per color
        return bgPaletteRam[offset] or (bgPaletteRam[offset + 1] shl 8)
    }

    // OBJ counterpart of bgColorRgb555. palette: 0-7, colorIndex: 1-3 (0 is transparent).
    fun objColorRgb555(palette: Int, colorIndex: Int): Int {
        val offset = palette * 8 + colorIndex * 2
        return objPaletteRam[offset] or (objPaletteRam[offset + 1] shl 8)
    }

    private fun startHdma(value: Int) {
        // Writing bit 7 = 0 while an HBlank DMA is in progress aborts it (HDMA5 then
        // reads back with bit 7 set). It does not start a GDMA.
        if (value and 0x80 == 0 && hdmaActive) {
            hdmaActive = false
            hdmaRemaining = (value and 0x7F) + 1   // FF55 low 7 bits latch the written length on stop
            return
        }

        val source = ((hdma1 shl 8) or hdma2) and 0xFFF0
        val dest = ((hdma3 shl 8) or hdma4) and 0x1FF0   // offset within VRAM

        if (value and 0x80 == 0) {
            // General Purpose DMA: copy the whole block at once into the current VBK bank.
            val blocks = (value and 0x7F) + 1
            val length = blocks * 0x10
            for (i in 0 until length) {
                writeVram((dest + i) and 0x1FFF, readDmaSource(source + i))
            }
            // GDMA freezes the CPU for the entire transfer: 8 M-cycles per 0x10-byte block in normal
            // speed, 16 in double speed (same ~8us/block wall time either way). The bytes are already
            // copied above; this figure only drives the timer/PPU advance.
            // TODO: not consumed yet — the CPU must drain pendingGdmaStallMCycles via onMachineCycleTick
            //  at the next instruction boundary, then reset it to 0.
            pendingGdmaStallMCycles = blocks * (if (isDoubleSpeed) 16 else 8)
        } else {
            // HBlank DMA: 0x10 bytes per HBlank (mode 0), LY 0-143. Chunks are pumped by
            // the PPU hook (next step).
            hdmaSource = source
            hdmaDest = dest
            hdmaRemaining = (value and 0x7F) + 1   // number of 0x10-byte chunks
            hdmaActive = true

            // First-chunk-immediate quirk. ppuMode == 0 is true in two cases that BOTH must
            // transfer one block right now:
            //   - LCD on, visible-line HBlank (the normal quirk), or
            //   - LCD off, where ppuMode is pinned to 0 and there will be no HBlank to pump the
            //     rest — hardware does exactly one block, then the transfer stalls (verified by
            //     gdma_addr_mask / hdma_lcd_off).
            // The LCD-off case is then frozen by the PPU's early return until the LCD comes back.
            if (ppuMode == 0) {
                stepHblankDma()
            }
        }
    }

    private fun initCompatPalettes() {
        // CGB_COMPAT skip-boot fallback. The real CGB boot ROM preloads the compatibility
        // palettes; we skip it, so seed a neutral grey ramp so the screen isn't black.
        // white → black, RGB555 (5 bits/channel).
        val COMPAT_GREY = intArrayOf(0x7FFF, 0x56B5, 0x294A, 0x0000)

        // BG palette 0 (the compat attribute map is all zeros → every BG tile uses palette 0).
        writeCgbRegister(0xFF68, 0x80)                  // BCPS: index 0, auto-increment on
        for (color in COMPAT_GREY) {
            writeCgbRegister(0xFF69, color and 0xFF)    // low byte
            writeCgbRegister(0xFF69, (color shr 8) and 0xFF) // high byte
        }

        // OBJ palettes 0 and 1 (compat sprites pick OBP0/OBP1 → CGB OBJ palette 0/1).
        writeCgbRegister(0xFF6A, 0x80)                  // OCPS: index 0, auto-increment on
        repeat(2) {
            for (color in COMPAT_GREY) {
                writeCgbRegister(0xFF6B, color and 0xFF)
                writeCgbRegister(0xFF6B, (color shr 8) and 0xFF)
            }
        }
    }

    // The CGB register block is physically present on CGB silicon, i.e. in both
    // CGB (color game) and CGB_COMPAT (a CGB running a DMG game). Only true DMG
    // hardware lacks it.
    private val hasCgbRegisters: Boolean get() = machineMode != MachineMode.DMG

    companion object {
        private const val POST_BOOT_DIV_COUNTER = 0xABCC

        /**
         * I/O register state left by the DMG boot ROM.
         * We skip the boot ROM and start at 0x0100, so we must reproduce this state.
         * Without it, LCDC=0 (LCD off) and games that poll LY==144 loop forever.
         */
        private fun initPostBootRegisters(ram: IntArray) {
            ram[0xFF05] = 0x00  // TIMA
            ram[0xFF06] = 0x00  // TMA
            ram[0xFF07] = 0x00  // TAC
            ram[0xFF0F] = 0x01  // IF — VBlank pending after boot ROM
            ram[0xFF10] = 0x80  // NR10
            ram[0xFF11] = 0xBF  // NR11
            ram[0xFF12] = 0xF3  // NR12
            ram[0xFF14] = 0xBF  // NR14
            ram[0xFF16] = 0x3F  // NR21
            ram[0xFF17] = 0x00  // NR22
            ram[0xFF19] = 0xBF  // NR24
            ram[0xFF1A] = 0x7F  // NR30
            ram[0xFF1B] = 0xFF  // NR31
            ram[0xFF1C] = 0x9F  // NR32
            ram[0xFF1E] = 0xBF  // NR33
            ram[0xFF20] = 0xFF  // NR41
            ram[0xFF21] = 0x00  // NR42
            ram[0xFF22] = 0x00  // NR43
            ram[0xFF23] = 0xBF  // NR44
            ram[0xFF24] = 0x77  // NR50
            ram[0xFF25] = 0xF3  // NR51
            ram[0xFF26] = 0xF1  // NR52
            ram[0xFF40] = 0x91  // LCDC — LCD on, BG on, tile data 0x8800, tile map 0x9800
            ram[0xFF41] = 0x85  // STAT — mode 1 (V-Blank)
            ram[0xFF42] = 0x00  // SCY
            ram[0xFF43] = 0x00  // SCX
            ram[0xFF44] = 0x00  // LY
            ram[0xFF45] = 0x00  // LYC
            ram[0xFF47] = 0xFC  // BGP
            ram[0xFF48] = 0xFF  // OBP0
            ram[0xFF49] = 0xFF  // OBP1
            ram[0xFF4A] = 0x00  // WY
            ram[0xFF4B] = 0x00  // WX
            ram[0xFF50] = 0x00  // Boot done
            ram[0xFFFF] = 0x00  // IE
        }
    }
}
