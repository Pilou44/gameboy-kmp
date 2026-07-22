package com.wechantloup.gameboykmp.cpu

/**
 * Sharp SM83 CPU core, advanced one T-cycle at a time.
 *
 * Timing contract: [tick] advances the CPU by a single T-cycle. Each instruction is expressed as a
 * sequence of [MicroOp] values (one micro-op = one T-cycle) held in an internal pipeline. When the
 * pipeline drains, [onPipelineEmpty] decides what fills it next — interrupt dispatch, HALT/STOP
 * freeze, or a normal opcode fetch — and that decision order encodes the hardware priority.
 *
 * The CPU does NOT drive the rest of the system. The caller (the emulation loop) is responsible for
 * cadencing the PPU, timer, APU and DMA, conventionally once every 4 T-cycles (one M-cycle), or
 * every 2 in double-speed mode. This inversion is deliberate: it keeps a single clock authority in
 * the loop rather than having the CPU push M-cycle ticks outward.
 *
 * Why per-T and not per-M-cycle: the SM83 makes at most one bus access per M-cycle, but the exact T
 * at which that access lands is observable (mid-scanline PPU effects, timer edges). Bus accesses are
 * therefore carried as typed micro-ops ([MicroOp.ReadImmediate]/[MicroOp.ReadMem]/[MicroOp.WriteMem])
 * rather than direct bus calls buried inside effects, so their position within the M-cycle can be
 * tuned centrally without touching each instruction.
 *
 * @param bus system bus the CPU reads from and writes to.
 */
class Cpu(
    private val bus: CpuBus,
) {

    // ──── CPU register file & interrupt state ───────────────────────────────────────
    val registers = Registers() // Visible for tests
    var ime = false // Visible for tests
    private var imeScheduled = false
    private var haltBug = false
    private var isStopped = false

    // ──── WZ latches: 8-bit data in flight between T-cycles of the current instruction ────
    private var latchW = 0
    private var latchZ = 0

    // ──── Micro-op pipeline & sequencing ────────────────────────────────────────────
    private val pipeline = RingBuffer<MicroOp>(32)
    internal val isAtInstructionBoundary: Boolean get() = pipeline.isEmpty
    // Latches the interrupt vector between the re-sample M-cycle and the jump M-cycle of ISR dispatch.
    private var isrVector: Int = 0

    // ──── Pre-built, shared micro-op singletons (pushed by reference, zero alloc on the hot path) ────
    private val retReadLow  = MicroOp.ReadMem(Addr16.SP, Latch.Z)
    private val retReadHigh = MicroOp.ReadMem(Addr16.SP, Latch.W)
    private val opIncZ = MicroOp.Internal { it.microIncZ() }
    private val opDecZ = MicroOp.Internal { it.microDecZ() }
    private val opIncSp     = MicroOp.Internal { it.microIncSp() }
    private val opWZtoPc    = MicroOp.Internal { it.microWZtoPc() }
    private val opDecSp     = MicroOp.Internal { it.microDecSp() }
    private val opWritePch  = MicroOp.WriteMem(Addr16.SP, Src8.PCH)
    private val opWritePcl  = MicroOp.WriteMem(Addr16.SP, Src8.PCL)
    private val opCbReadHL  = MicroOp.ReadMem(Addr16.HL, Latch.Z)
    private val opCbWriteHL = MicroOp.WriteMem(Addr16.HL, Src8.Z)
    private val opCbApplyZ = MicroOp.Internal { it.cbApplyZ() }

    // Pre-allocated, capture-free singletons (like opIncZ/opDecSp). Declared as private val on Cpu.
    private val opIsrResolveVector = MicroOp.Internal { cpu ->
        // Re-sample IE & IF right after the high-byte push: that push may have overwritten IE
        // (when SP-1 == 0xFFFF), which changes the outcome. A later overwrite by the low-byte push
        // comes too late to matter. If no enabled+requested bit remains, dispatch is cancelled:
        // vector forced to 0x0000 and NO IF bit is cleared.
        val latePending = cpu.bus.ie and cpu.bus.iF and 0x1F
        val bit = if (latePending != 0) latePending.countTrailingZeroBits() else -1
        cpu.isrVector = if (bit >= 0) {
            cpu.bus.setIF(cpu.bus.iF and (1 shl bit).inv())  // clear only the serviced bit
            0x0040 + (bit * 8)  // 0x40,0x48,0x50,0x58,0x60
        } else {
            0x0000
        }
    }

    private val opIsrJump = MicroOp.Internal { cpu -> cpu.registers.pc = cpu.isrVector }

    private val opPollStopWake = MicroOp.Internal { cpu ->
        // A selected line is low (button pressed) when any of bits 0..3 is 0.
        if ((cpu.bus.read(0xFF00) and 0x0F) != 0x0F) cpu.isStopped = false
    }

    // ──── Lifecycle ─────────────────────────────────────────────────────────────────
    fun reset() {
        ime = false
        registers.reset()
        bus.cpuHalted = false
    }

    /**
     * Initialize registers with boot values.
     */
    private fun Registers.reset() {
        if (bus.machineMode != MachineMode.DMG && bus.bootRom != null) {
            a = 0x00
            b = 0x00
            c = 0x00
            d = 0x00
            e = 0x00
            f = 0x00
            h = 0x00
            l = 0x00

            pc = 0x0000
            sp = 0x0000

            return
        }

        // Post-boot CPU state, per Pan Docs "Console state after boot ROM hand-off".
        // Games detect the hardware via A (0x11 = CGB); the rest matches real hardware
        // so the full register file is correct (cf. Mooneye boot_regs-* tests).
        when (bus.machineMode) {
            MachineMode.DMG -> {
                // F = Z+H+C. H and C are clear if the header checksum is 0; 0xB0 is the
                // usual case (any cartridge with a valid non-zero checksum).
                a = 0x01
                b = 0x00
                c = 0x13
                d = 0x00
                e = 0xD8
                f = 0xB0
                h = 0x01
                l = 0x4D
            }
            MachineMode.CGB -> {
                a = 0x11
                b = 0x00
                c = 0x00
                d = 0xFF
                e = 0x56
                f = 0x80 // Z only
                h = 0x00
                l = 0x0D
            }
            MachineMode.CGB_COMPAT -> {
                // CGB hardware running a DMG game. Fixed registers below; B and HL depend
                // on the title checksum, which is the same hash that drives auto-palette.
                a = 0x11
                c = 0x00
                d = 0x00
                e = 0x08
                f = 0x80
                // TODO: tie B and HL to the title checksum at the auto-palette step:
                //   B = sum of the 16 title bytes for Nintendo-licensed games, else 0x00;
                //   HL = 0x991A if B is 0x43/0x58, else 0x007C.
                // Using the common (non-Nintendo) case for now.
                b = 0x00
                h = 0x00; l = 0x7C
            }
        }
        pc = 0x0100
        sp = 0xFFFE
    }

    // ──── Scheduler: T-cycle driver and pipeline refill ─────────────────────────────
    fun tick() {
        if (pipeline.isEmpty) {
            onPipelineEmpty()
        }

        perform(pipeline.pop())
    }

    private fun onPipelineEmpty() {
        // Decision point when the pipeline drains: pick what fills it next. Guard order encodes hardware
        // priority — (GDMA) > STOP > interrupt dispatch > HALT freeze > normal fetch. The order of these
        // returns IS the semantics; do not reorder.

        // GDMA stall: highest priority (drained first, before any fetch). A general-purpose
        // DMA started by the previous instruction freezes the CPU for the whole transfer; the Bus already
        // did the (atomic) copy and only published the duration. Burn it one M-cycle per pass — NOT all at
        // once: a transfer can reach ~1024 M-cycles, far past the 32-slot pipeline, so we drain it
        // incrementally like the HALT/STOP freeze, decrementing the published count.
        if (bus.pendingGdmaStallMCycles > 0) {
            bus.pendingGdmaStallMCycles--
            repeat(4) {
                pipeline.push(MicroOp.Idle)
            }
            return
        }

        // STOP freeze (DMG): frozen until a selected joypad line goes low. Tested BEFORE the interrupt
        // block — unlike HALT, a pending interrupt does NOT wake STOP. Like HALT's freeze, it burns one
        // M-cycle per pass without fetching.
        // TODO: the joypad poll is modeled as a bus read of FF00 (the hardware wake is an async input-line
        //  signal, not a software read), so its T within the M-cycle is arbitrary — placed at T0 by
        //  convention. Not exercised by the parity harness (no Case enters isStopped) nor by the test ROMs
        //  (no button input), so this migration is neutral-by-construction, not empirically validated.
        //  Revisit the placement if a STOP test ever lands.
        if (isStopped) {
            pipeline.push(opPollStopWake)   // Internal: reads FF00, clears isStopped if a line is low
            pipeline.push(MicroOp.Idle)
            pipeline.push(MicroOp.Idle)
            pipeline.push(MicroOp.Idle)
            return
        }

        // Check for pending interrupts
        val pending = bus.ie and bus.iF and 0x1F

        // A pending interrupt wakes HALT regardless of IME. Must run BEFORE the freeze below,
        // otherwise the freeze re-arms and the interrupt never gets its chance (immortal HALT).
        if (pending != 0 && bus.cpuHalted) {
            bus.cpuHalted = false
            // Wake consumes no M-cycle of its own: the fetch below is the first M-cycle after HALT,
            // exactly as the legacy `return`-without-tick did. IME=true falls through to ISR dispatch.
        }

        if (pending != 0 && ime) {
            ime = false

            // M1 + M2: two internal M-cycles (no bus access).
            repeat(8) { pipeline.push(MicroOp.Idle) }

            // M3: SP-- then push PCH. Manual stack writes, intentionally NOT push() (which adds an extra
            // internal M-cycle suited for the PUSH instruction, not for interrupt dispatch).
            pipeline.push(opDecSp)
            pipeline.push(MicroOp.Idle)
            pipeline.push(MicroOp.WriteMem(Addr16.SP, Src8.PCH))
            pipeline.push(MicroOp.Idle)

            // M4: re-sample the vector FIRST (after the high-byte push, before the low-byte push),
            // then SP-- and push PCL.
            pipeline.push(opIsrResolveVector)
            pipeline.push(opDecSp)
            pipeline.push(MicroOp.WriteMem(Addr16.SP, Src8.PCL))
            pipeline.push(MicroOp.Idle)

            // M5: jump to the resolved vector (0x0000 if dispatch was cancelled).
            pipeline.push(opIsrJump)
            pipeline.push(MicroOp.Idle)
            pipeline.push(MicroOp.Idle)
            pipeline.push(MicroOp.Idle)

            return
        }

        if (bus.cpuHalted) {
            repeat(4) {
                pipeline.push(MicroOp.Idle)
            }

            return
        }

        pipeline.push(MicroOp.FetchOpCode)
    }

    private fun perform(op: MicroOp) {
        when (op) {
            MicroOp.Idle -> Unit

            is MicroOp.FetchOpCode -> fetchOpCode()

            is MicroOp.ReadImmediate -> {
                val v = bus.read(registers.pc)
                registers.pc = (registers.pc + 1) and 0xFFFF
                setLatch(op.into, v)
            }
            is MicroOp.ReadMem -> setLatch(op.into, bus.read(addr16(op.addr)))
            is MicroOp.ReadMemToReg -> {
                val value = bus.read(addr16(op.addr))
                when (op.into) {
                    Reg8.A -> registers.a = value
                    Reg8.B -> registers.b = value
                    Reg8.C -> registers.c = value
                    Reg8.D -> registers.d = value
                    Reg8.E -> registers.e = value
                    Reg8.F -> registers.f = value and 0xF0
                    Reg8.H -> registers.h = value
                    Reg8.L -> registers.l = value
                }
            }
            is MicroOp.WriteMem -> bus.write(addr16(op.addr), src8(op.value))
            is MicroOp.ZtoReg -> when (op.dst) {
                Reg8.A -> registers.a = latchZ
                Reg8.B -> registers.b = latchZ
                Reg8.C -> registers.c = latchZ
                Reg8.D -> registers.d = latchZ
                Reg8.E -> registers.e = latchZ
                Reg8.F -> registers.f = latchZ and 0xF0
                Reg8.H -> registers.h = latchZ
                Reg8.L -> registers.l = latchZ
            }
            is MicroOp.RegToZ -> latchZ = when (op.src) {
                Reg8.A -> registers.a
                Reg8.B -> registers.b
                Reg8.C -> registers.c
                Reg8.D -> registers.d
                Reg8.E -> registers.e
                Reg8.F -> registers.f
                Reg8.H -> registers.h
                Reg8.L -> registers.l
            }
            is MicroOp.AddHl -> addHl16(op.src)
            is MicroOp.Rst -> rst(op.vector)
            is MicroOp.AluZ -> aluZ(op.aluOp)

            is MicroOp.Internal -> op.effect(this)
        }
    }

    // ──── Fetch & decode ────────────────────────────────────────────────────────────
    private fun fetchOpCode() {
        latchZ = bus.read(registers.pc) and 0xFF
        if (haltBug) {
            haltBug = false  // only skip increment once
        } else {
            registers.pc = (registers.pc + 1) and 0xFFFF
        }

        if (imeScheduled) {
            ime = true
            imeScheduled = false
        }

        // Phase A: micro-op table first (so (HL) variants never fall through to phase B)
        val seq = MicroCode.TABLE[latchZ]
        if (seq != null) {
            // the 3 Idle completing the fetch M-cycle
            pipeline.push(MicroOp.Idle)
            pipeline.push(MicroOp.Idle)
            pipeline.push(MicroOp.Idle)
            seq.forEach { pipeline.push(it) }
            return
        }

        // Phase B
        if (handleImmediateOpCode()) {
            return
        }

        TODO("Opcode 0x${latchZ.toString(16).uppercase()} not implemented at PC=0x${(registers.pc - 1).toString(16)}")
    }

    private fun handleImmediateOpCode(): Boolean {
        val opCode = latchZ
        return when (opCode) {
            0x76 -> {
                val pending = bus.ie and bus.iF and 0x1F
                if (pending != 0 && !ime) {
                    haltBug = true  // halt bug regardless of IME state
                } else {
                    bus.cpuHalted = true
                }
                pushFetchPadding()
                true
            }

            0x10 -> {
                // STOP is a 2-byte instruction (0x10 0x00) -> skip the padding byte.
                registers.pc = (registers.pc + 1) and 0xFFFF

                if (bus.performSpeedSwitch()) {
                    // CGB speed switch: a KEY1 switch was armed, so STOP toggles the speed and
                    // execution continues with the next instruction (the LCD is left untouched).
                    // STOP still resets DIV.
                    // TODO: hardware halts the CPU ~2050 M-cycles during the switch; not modeled
                    //  (immediate toggle). Fine for boot; revisit if a timing test needs it.
                    bus.write(0xFF04, 0x00)
                } else {
                    // Real STOP mode: CPU frozen until a selected joypad line goes low.
                    isStopped = true
                    bus.write(0xFF40, bus.read(0xFF40) and 0x7F)  // blank the screen (clear LCDC bit 7)
                    bus.write(0xFF04, 0x00)
                }
                pushFetchPadding()
                true
            }

            0x04 -> {
                pipeline.push(MicroOp.RegToZ(Reg8.B))
                pipeline.push(opIncZ)
                pipeline.push(MicroOp.ZtoReg(Reg8.B))
                true
            }
            0x0C -> {
                pipeline.push(MicroOp.RegToZ(Reg8.C))
                pipeline.push(opIncZ)
                pipeline.push(MicroOp.ZtoReg(Reg8.C))
                true
            }
            0x14 -> {
                pipeline.push(MicroOp.RegToZ(Reg8.D))
                pipeline.push(opIncZ)
                pipeline.push(MicroOp.ZtoReg(Reg8.D))
                true
            }
            0x1C -> {
                pipeline.push(MicroOp.RegToZ(Reg8.E))
                pipeline.push(opIncZ)
                pipeline.push(MicroOp.ZtoReg(Reg8.E))
                true
            }
            0x24 -> {
                pipeline.push(MicroOp.RegToZ(Reg8.H))
                pipeline.push(opIncZ)
                pipeline.push(MicroOp.ZtoReg(Reg8.H))
                true
            }
            0x2C -> {
                pipeline.push(MicroOp.RegToZ(Reg8.L))
                pipeline.push(opIncZ)
                pipeline.push(MicroOp.ZtoReg(Reg8.L))
                true
            }
            0x3C -> {
                pipeline.push(MicroOp.RegToZ(Reg8.A))
                pipeline.push(opIncZ)
                pipeline.push(MicroOp.ZtoReg(Reg8.A))
                true
            }

            0x05 -> {
                pipeline.push(MicroOp.RegToZ(Reg8.B))
                pipeline.push(opDecZ)
                pipeline.push(MicroOp.ZtoReg(Reg8.B))
                true
            }
            0x0D -> {
                pipeline.push(MicroOp.RegToZ(Reg8.C))
                pipeline.push(opDecZ)
                pipeline.push(MicroOp.ZtoReg(Reg8.C))
                true
            }
            0x15 -> {
                pipeline.push(MicroOp.RegToZ(Reg8.D))
                pipeline.push(opDecZ)
                pipeline.push(MicroOp.ZtoReg(Reg8.D))
                true
            }
            0x1D -> {
                pipeline.push(MicroOp.RegToZ(Reg8.E))
                pipeline.push(opDecZ)
                pipeline.push(MicroOp.ZtoReg(Reg8.E))
                true
            }
            0x25 -> {
                pipeline.push(MicroOp.RegToZ(Reg8.H))
                pipeline.push(opDecZ)
                pipeline.push(MicroOp.ZtoReg(Reg8.H))
                true
            }
            0x2D -> {
                pipeline.push(MicroOp.RegToZ(Reg8.L))
                pipeline.push(opDecZ)
                pipeline.push(MicroOp.ZtoReg(Reg8.L))
                true
            }
            0x3D -> {
                pipeline.push(MicroOp.RegToZ(Reg8.A))
                pipeline.push(opDecZ)
                pipeline.push(MicroOp.ZtoReg(Reg8.A))
                true
            }

            /* --- Misc --- */
            0x27 -> { daa() ; pushFetchPadding() ; true }
            0x2F -> {
                // CPL
                registers.a = registers.a.inv() and 0xFF
                registers.flagN = true
                registers.flagH = true
                pushFetchPadding()
                true
            }
            0x37 -> {
                // SCF
                registers.flagN = false
                registers.flagH = false
                registers.flagC = true
                pushFetchPadding()
                true
            }
            0x3F -> {
                // CCF
                registers.flagN = false
                registers.flagH = false
                registers.flagC = !registers.flagC
                pushFetchPadding()
                true
            }

            /* --- Jumps --- */
            0xE9 -> {
                registers.pc = registers.hl
                pushFetchPadding()
                true
            } // JP HL

            /* --- Rotate accumulator --- */
            0x07 -> {  // RLCA
                val bit7 = (registers.a shr 7) and 1
                registers.a = ((registers.a shl 1) or bit7) and 0xFF
                registers.flagZ = false
                registers.flagN = false
                registers.flagH = false
                registers.flagC = bit7 != 0
                pushFetchPadding()
                true
            }
            0x0F -> {  // RRCA
                val bit0 = registers.a and 1
                registers.a = (registers.a ushr 1) or (bit0 shl 7)
                registers.flagZ = false
                registers.flagN = false
                registers.flagH = false
                registers.flagC = bit0 != 0
                pushFetchPadding()
                true
            }
            0x17 -> {  // RLA
                val oldC = if (registers.flagC) 1 else 0
                val bit7 = (registers.a shr 7) and 1
                registers.a = ((registers.a shl 1) or oldC) and 0xFF
                registers.flagZ = false
                registers.flagN = false
                registers.flagH = false
                registers.flagC = bit7 != 0
                pushFetchPadding()
                true
            }
            0x1F -> {  // RRA
                val oldC = if (registers.flagC) 1 else 0
                val bit0 = registers.a and 1
                registers.a = (registers.a ushr 1) or (oldC shl 7)
                registers.flagZ = false; registers.flagN = false; registers.flagH = false
                registers.flagC = bit0 != 0
                pushFetchPadding()
                true
            }

            /* --- Interrupts --- */
            0xF3 -> { ime = false ; pushFetchPadding() ; true }
            0xFB -> { imeScheduled = true ; pushFetchPadding() ; true }

            /* --- 8-bit arithmetic: register --- */
            in 0x80..0x87 -> { add(opCode) ; pushFetchPadding() ; true }
            in 0x88..0x8F -> { add(opCode, withCarry = true) ; pushFetchPadding() ; true }
            in 0x90..0x97 -> { sub(opCode) ; pushFetchPadding() ; true }
            in 0x98..0x9F -> { sub(opCode, withCarry = true) ; pushFetchPadding() ; true }
            in 0xA0..0xA7 -> { and8(opCode) ; pushFetchPadding() ; true }
            in 0xA8..0xAF -> { xor8(opCode) ; pushFetchPadding() ; true }
            in 0xB0..0xB7 -> { or8(opCode) ; pushFetchPadding() ; true }
            in 0xB8..0xBF -> { sub(opCode, storeResult = false) ; pushFetchPadding() ; true } // CP

            /* --- 8-bit loads: register to register (0x40–0x7F, 0x76=HALT handled above) --- */
            in 0x40..0x7F -> { load(opCode) ; pushFetchPadding() ; true }
            else -> false
        }
    }

    private fun pushFetchPadding() {
        pipeline.push(MicroOp.Idle)
        pipeline.push(MicroOp.Idle)
        pipeline.push(MicroOp.Idle)
    }

    // ──── Micro-op effects (Internal handlers, run capture-free via MicroOp.Internal) ────
    /** INC effect on the Z latch (+ flags), reused by INC (HL) and later INC r. C is untouched. */
    internal fun microIncZ() {
        val old = latchZ
        val v = (old + 1) and 0xFF
        latchZ = v
        registers.flagZ = v == 0
        registers.flagN = false
        registers.flagH = (old and 0x0F) == 0x0F
    }

    internal fun microDecZ() {
        val old = latchZ
        val v = (old - 1) and 0xFF
        latchZ = v
        registers.flagZ = v == 0
        registers.flagN = true
        registers.flagH = (old and 0x0F) == 0x00
    }

    /** Post-increment / post-decrement HL. Own T-cycle; shared by LD (HL±),A and LD A,(HL±). */
    internal fun microIncHl() { registers.hl = (registers.hl + 1) and 0xFFFF }
    internal fun microDecHl() { registers.hl = (registers.hl - 1) and 0xFFFF }
    internal fun microIncBc() { registers.bc = (registers.bc + 1) and 0xFFFF }
    internal fun microDecBc() { registers.bc = (registers.bc - 1) and 0xFFFF }
    internal fun microIncDe() { registers.de = (registers.de + 1) and 0xFFFF }
    internal fun microDecDe() { registers.de = (registers.de - 1) and 0xFFFF }
    internal fun microIncSp() { registers.sp = (registers.sp + 1) and 0xFFFF }
    internal fun microDecSp() { registers.sp = (registers.sp - 1) and 0xFFFF }

    /** Increment the 16-bit address held in the WZ latches (W=high, Z=low). Pointer arithmetic, no flags.
     *  Used by LD (nn),SP to step from addr to addr+1. */
    internal fun microIncWZ() {
        val addr = ((latchW shl 8) or latchZ) + 1 and 0xFFFF
        latchW = (addr shr 8) and 0xFF
        latchZ = addr and 0xFF
    }

    /** LD SP,HL: copy HL into SP. The extra internal M-cycle (vs LD r,r') is the 16-bit register move. */
    internal fun microSpFromHl() { registers.sp = registers.hl }

    /** Assemble the popped pair from the WZ latches (W=high, Z=low) into BC. */
    internal fun microWZtoBc() { registers.bc = (latchW shl 8) or latchZ }
    internal fun microWZtoDe() { registers.de = (latchW shl 8) or latchZ }
    internal fun microWZtoHl() { registers.hl = (latchW shl 8) or latchZ }
    internal fun microWZtoPc() { registers.pc = (latchW shl 8) or latchZ }
    /** Assemble WZ (W=high, Z=low) into SP. Used by LD SP,nn — SP is never a POP target. */
    internal fun microWZtoSp() { registers.sp = (latchW shl 8) or latchZ }

    /**
     * Assemble the popped pair into AF. Unlike the other pairs, F holds only its top 4 bits in hardware,
     * so the low nibble of the popped low byte (which lands in F) is masked off — POP AF can never set
     * flag bits 0..3.
     */
    internal fun microPopAf() { registers.af = (latchW shl 8) or (latchZ and 0xF0) }

    internal fun microSetIme() { ime = true }

    /** High-page address from C into WZ: W=0xFF, Z=C. For LDH (C),A / LDH A,(C). 0xFF is a literal,
     *  not a capture, so the Internal stays capture-free. */
    internal fun microHighPageC() { latchW = 0xFF; latchZ = registers.c }

    /** Set the high-page base in W (0xFF). Z is filled separately (immediate or C). For LDH (n),A / A,(n). */
    internal fun microHighPageW() { latchW = 0xFF }

    internal fun testCondition(c: Condition): Boolean = when (c) {
        Condition.NZ -> !registers.flagZ
        Condition.Z  ->  registers.flagZ
        Condition.NC -> !registers.flagC
        Condition.C  ->  registers.flagC
    }

    /**
     * JR cc resolution, run as an Internal AFTER the signed offset has been read into Z. If the branch is
     * taken, applies PC += (signed)Z and pushes ONE extra internal micro-op (the M-cycle hardware spends
     * updating PC). Not taken: nothing pushed, so the instruction ends one M-cycle shorter — exactly the
     * legacy jr() shape. This is the first opcode where the pipeline is fed conditionally at run time.
     */
    internal fun jrResolve(c: Condition) {
        if (!testCondition(c)) return                         // not taken: no extra M-cycle
        jrResolve()
    }

    internal fun jrResolve() {
        val offset = latchZ.toByte().toInt()                  // Z holds the raw offset byte; sign-extend
        registers.pc = (registers.pc + offset) and 0xFFFF
        pipeline.push(MicroOp.Idle)                    // taken: the extra internal M-cycle (4 T)
        pipeline.push(MicroOp.Idle)
        pipeline.push(MicroOp.Idle)
        pipeline.push(MicroOp.Idle)
    }

    internal fun jpResolve(c: Condition) {
        if (!testCondition(c)) return                         // not taken: no extra M-cycle

        pipeline.push(opWZtoPc)
        pipeline.push(MicroOp.Idle)
        pipeline.push(MicroOp.Idle)
        pipeline.push(MicroOp.Idle)
    }

    internal fun retResolve(c: Condition) {
        if (!testCondition(c)) return                         // not taken: no extra M-cycle
        // RET
        // M1: read low byte into Z, SP++.
        pipeline.push(retReadLow)
        pipeline.push(opIncSp)
        pipeline.push(MicroOp.Idle)
        pipeline.push(MicroOp.Idle)
        // M2: read high byte into W, SP++.
        pipeline.push(retReadHigh)
        pipeline.push(opIncSp)
        pipeline.push(MicroOp.Idle)
        pipeline.push(MicroOp.Idle)
        // M3: internal jump M-cycle — assemble (W<<8)|Z into PC.
        pipeline.push(opWZtoPc)
        pipeline.push(MicroOp.Idle)
        pipeline.push(MicroOp.Idle)
        pipeline.push(MicroOp.Idle)
    }

    internal fun callResolve(c: Condition) {
        if (!testCondition(c)) return                         // not taken: no extra M-cycle

        // M3
        pipeline.push(MicroOp.Idle)
        pipeline.push(MicroOp.Idle)
        pipeline.push(MicroOp.Idle)
        pipeline.push(MicroOp.Idle)
        // M4: SP-- then write PC high byte to [SP].
        pipeline.push(opDecSp)
        pipeline.push(MicroOp.Idle)
        pipeline.push(MicroOp.Idle)
        pipeline.push(opWritePch)
        // M5: SP-- then write PC low byte to [SP], then jump (PC <- WZ = nn).
        pipeline.push(opDecSp)
        pipeline.push(MicroOp.Idle)
        pipeline.push(opWritePcl)
        pipeline.push(opWZtoPc)
    }

    /**
     * Shared core of ADD SP,e (0xE8) and LD HL,SP+e (0xF8). Z holds the raw offset byte (sign-extended
     * here). Flags use the unsigned add of SP's low byte vs the offset byte — Z and N are always 0 — via
     * the (sp xor offset xor result) trick.
     * Caller decides the destination; the M-cycle count differs (4 for SP, 3 for HL) and is set by the
     * number of Idle micro-ops in each sequence, not here.
     */
    private fun spOffsetResult(): Int {
        val offset = latchZ.toByte().toInt()
        val result = (registers.sp + offset) and 0xFFFF
        registers.flagZ = false
        registers.flagN = false
        registers.flagH = (registers.sp xor offset xor result) and 0x10 != 0
        registers.flagC = (registers.sp xor offset xor result) and 0x100 != 0
        return result
    }

    internal fun addSpE()  { registers.sp = spOffsetResult() }   // 0xE8
    internal fun ldHlSpE() { registers.hl = spOffsetResult() }   // 0xF8

    internal fun cbDecode() {
        val op = latchW
        val reg = op and 0x07           // bits 2-0: target register (6 = (HL))
        val yyy = (op shr 3) and 0x07   // bits 5-3: operation index, or bit number for BIT/RES/SET
        val group = (op shr 6) and 0x03 // bits 7-6: 0=rot/shift, 1=BIT, 2=RES, 3=SET

        if (reg != 6) {
            val v = readReg(reg)          // get the register value
            val result = cbApply(group, yyy, v)   // transform + set flags
            if (group != GROUP_BIT) writeReg(reg, result)   // BIT does not write back
            return                        // nothing pushed: instruction ends here
        }

        // reg == 6: operand is (HL) — bus access required, so push the M-cycles into the pipeline.
        if (group == GROUP_BIT) {
            // BIT n,(HL): read then test. No write-back → 1 M-cycle.
            pipeline.push(opCbReadHL)          // ReadMem(HL → Z)
            pipeline.push(MicroOp.Idle)
            pipeline.push(MicroOp.Idle)
            pipeline.push(opCbApplyZ)        // Internal: test bit of Z, set flags
        } else {
            // rotations / RES / SET on (HL): read-modify-write -> 2 M-cycles.
            // opCbApplyZ (non-bus Z transform) is glided to the read M-cycle tail so the
            // write leads its own M-cycle (start-of-M-cycle counter view; see policy TODO).
            pipeline.push(opCbReadHL)          // M read: ReadMem(HL -> Z)
            pipeline.push(MicroOp.Idle)
            pipeline.push(MicroOp.Idle)
            pipeline.push(opCbApplyZ)          // transform Z (was at M-write head)
            pipeline.push(opCbWriteHL)         // M write leads: WriteMem(HL, Z)
            pipeline.push(MicroOp.Idle)
            pipeline.push(MicroOp.Idle)
            pipeline.push(MicroOp.Idle)
        }
    }

    // Re-decode the CB opcode from W (still holds it) and apply the effect to the Z latch.
    // Used by the (HL) path, where the operand was read from memory into Z.
    private fun cbApplyZ() {
        val op = latchW
        val yyy = (op shr 3) and 0x07
        val group = (op shr 6) and 0x03
        latchZ = cbApply(group, yyy, latchZ)
    }

    // ──── ALU ───────────────────────────────────────────────────────────────────────
    // TODO: two ALU families coexist — register-operand (add/sub/and8/or8/xor8, operand = readReg(code))
    //  and Z-latch (addZ/subZ/andZ/orZ/xorZ, operand = latchZ). They are near-duplicates. The register
    //  family only survives on the phase-B immediate path in handleImmediateOpCode; once every opcode is
    //  routed through micro-ops (Z-latch), the register family becomes removable. Kept for now.

    private fun add(code: Int, withCarry: Boolean = false) {
        val a = registers.a
        val b = readReg(code and 0x07)
        val carry = if (withCarry && registers.flagC) 1 else 0
        val result = a + b + carry
        registers.a = result and 0xFF
        registers.flagZ = (result and 0xFF) == 0
        registers.flagN = false
        registers.flagH = (a and 0x0F) + (b and 0x0F) + carry > 0x0F
        registers.flagC = result > 0xFF
    }

    private fun sub(code: Int, withCarry: Boolean = false, storeResult: Boolean = true) {
        val a = registers.a
        val b = readReg(code and 0x07)
        val carry = if (withCarry && registers.flagC) 1 else 0
        val result = a - b - carry
        if (storeResult) registers.a = result and 0xFF
        registers.flagZ = (result and 0xFF) == 0
        registers.flagN = true
        registers.flagH = (a and 0x0F) < (b and 0x0F) + carry
        registers.flagC = a < b + carry
    }

    private fun and8(code: Int) {
        val result = registers.a and readReg(code and 0x07)
        registers.a = result and 0xFF
        registers.flagZ = result == 0
        registers.flagN = false
        registers.flagH = true
        registers.flagC = false
    }

    private fun or8(code: Int) {
        val result = registers.a or readReg(code and 0x07)
        registers.a = result and 0xFF
        registers.flagZ = result == 0
        registers.flagN = false
        registers.flagH = false
        registers.flagC = false
    }

    private fun xor8(code: Int) {
        val result = registers.a xor readReg(code and 0x07)
        registers.a = result and 0xFF
        registers.flagZ = result == 0
        registers.flagN = false
        registers.flagH = false
        registers.flagC = false
    }

    private fun daa() {
        var a = registers.a
        var correction = 0

        if (registers.flagH || (!registers.flagN && (a and 0x0F) > 9)) {
            correction = 0x06
        }
        if (registers.flagC || (!registers.flagN && a > 0x99)) {
            correction = correction or 0x60
            registers.flagC = true
        }

        a = if (registers.flagN) a - correction else a + correction

        registers.a = a and 0xFF
        registers.flagZ = registers.a == 0
        registers.flagH = false
    }

    private fun addZ(withCarry: Boolean) {
        val a = registers.a
        val b = latchZ
        val carry = if (withCarry && registers.flagC) 1 else 0
        val result = a + b + carry
        registers.a = result and 0xFF
        registers.flagZ = registers.a == 0
        registers.flagN = false
        registers.flagH = (a and 0x0F) + (b and 0x0F) + carry > 0x0F
        registers.flagC = result > 0xFF
    }

    private fun subZ(withCarry: Boolean, storeResult: Boolean) {
        val a = registers.a
        val b = latchZ
        val carry = if (withCarry && registers.flagC) 1 else 0
        val result = a - b - carry
        if (storeResult) registers.a = result and 0xFF
        registers.flagZ = (result and 0xFF) == 0
        registers.flagN = true
        registers.flagH = (a and 0x0F) < (b and 0x0F) + carry
        registers.flagC = a < b + carry
    }

    private fun andZ() {
        val result = registers.a and latchZ
        registers.a = result and 0xFF
        registers.flagZ = result == 0
        registers.flagN = false
        registers.flagH = true
        registers.flagC = false
    }

    private fun orZ() {
        val result = registers.a or latchZ
        registers.a = result and 0xFF
        registers.flagZ = result == 0
        registers.flagN = false
        registers.flagH = false
        registers.flagC = false
    }

    private fun xorZ() {
        val result = registers.a xor latchZ
        registers.a = result and 0xFF
        registers.flagZ = result == 0
        registers.flagN = false
        registers.flagH = false
        registers.flagC = false
    }

    private fun aluZ(aluOp: AluOp) {
        when (aluOp) {
            AluOp.ADD -> addZ(withCarry = false)
            AluOp.ADC -> addZ(withCarry = true)
            AluOp.SUB -> subZ(withCarry = false, storeResult = true)
            AluOp.SBC -> subZ(withCarry = true, storeResult = true)
            AluOp.AND -> andZ()
            AluOp.XOR -> xorZ()
            AluOp.OR -> orZ()
            AluOp.CP -> subZ(withCarry = false, storeResult = false)
        }
    }

    private fun addHl16(src: Reg16) {
        val value = when (src) {
            Reg16.BC -> registers.bc
            Reg16.DE -> registers.de
            Reg16.HL -> registers.hl
            Reg16.SP -> registers.sp
        }

        val hl = registers.hl
        val result = hl + value
        registers.hl = result and 0xFFFF
        registers.flagN = false
        registers.flagH = (hl and 0x0FFF) + (value and 0x0FFF) > 0x0FFF
        registers.flagC = result > 0xFFFF
    }

    private fun cbApply(group: Int, yyy: Int, value: Int): Int {
        return when (group) {
            0 -> when (yyy) {
                /* RLC RRC RL RR SLA SRA SWAP SRL */
                0 -> {
                    // RLC r
                    val bit7 = (value shr 7) and 1
                    val ret = ((value shl 1) or bit7) and 0xFF
                    registers.flagZ = ret == 0
                    registers.flagN = false
                    registers.flagH = false
                    registers.flagC = bit7 != 0
                    ret
                }
                1 -> {
                    // RRC r
                    val bit0 = value and 1
                    val ret = (value ushr 1) or (bit0 shl 7)
                    registers.flagZ = ret == 0
                    registers.flagN = false
                    registers.flagH = false
                    registers.flagC = bit0 != 0
                    ret
                }
                2 -> {
                    // RL r
                    val bit7 = (value shr 7) and 1
                    val oldC = if (registers.flagC) 1 else 0
                    val ret = ((value shl 1) or oldC) and 0xFF
                    registers.flagZ = ret == 0
                    registers.flagN = false
                    registers.flagH = false
                    registers.flagC = bit7 != 0
                    ret
                }
                3 -> {
                    // RR r
                    val bit0 = value and 1
                    val oldC = if (registers.flagC) 1 else 0
                    val ret = (value ushr 1) or (oldC shl 7)
                    registers.flagZ = ret == 0
                    registers.flagN = false
                    registers.flagH = false
                    registers.flagC = bit0 != 0
                    ret
                }
                4 -> {
                    // SLA r
                    val bit7 = (value shr 7) and 1
                    val ret = (value shl 1) and 0xFF
                    registers.flagZ = ret == 0
                    registers.flagN = false
                    registers.flagH = false
                    registers.flagC = bit7 != 0
                    ret
                }
                5 -> {
                    // SRA r (arithmetic shift, sign extends)
                    val bit0 = value and 1
                    val bit7 = value and 0x80
                    val ret = (value ushr 1) or bit7
                    registers.flagZ = ret == 0
                    registers.flagN = false
                    registers.flagH = false
                    registers.flagC = bit0 != 0
                    ret
                }
                6 -> {
                    // SWAP r
                    val ret = ((value and 0x0F) shl 4) or ((value and 0xF0) shr 4)
                    registers.flagZ = ret == 0
                    registers.flagN = false
                    registers.flagH = false
                    registers.flagC = false
                    ret
                }
                7 -> {
                    // SRL r (logical shift)
                    val bit0 = value and 1
                    val ret = value ushr 1
                    registers.flagZ = ret == 0
                    registers.flagN = false
                    registers.flagH = false
                    registers.flagC = bit0 != 0
                    ret
                }
                else -> value
            }
            1 -> {
                /* BIT yyy: test bit, set flags, return value unchanged */
                registers.flagZ = ((value shr yyy) and 1) == 0
                registers.flagN = false
                registers.flagH = true
                value
            }
            2 -> {
                /* RES yyy */
                value and (1 shl yyy).inv()
            }
            3 -> {
                /* SET yyy */
                value or (1 shl yyy)
            }
            else -> value
        }
    }

    // ──── Bus-access plumbing: latches, address and source resolution ───────────────
    private fun setLatch(l: Latch, v: Int) {
        when (l) {
            Latch.W -> latchW = v and 0xFF
            Latch.Z -> latchZ = v and 0xFF
        }
    }

    private fun addr16(a: Addr16): Int = when (a) {
        Addr16.BC -> registers.bc
        Addr16.DE -> registers.de
        Addr16.HL -> registers.hl
        Addr16.SP -> registers.sp
        Addr16.WZ -> (latchW shl 8) or latchZ
    }

    private fun src8(s: Src8): Int = when (s) {
        Src8.A -> registers.a
        Src8.B -> registers.b
        Src8.C -> registers.c
        Src8.D -> registers.d
        Src8.E -> registers.e
        Src8.F -> registers.f and 0xF0
        Src8.H -> registers.h
        Src8.L -> registers.l
        Src8.W -> latchW
        Src8.Z -> latchZ
        Src8.PCH -> (registers.pc shr 8) and 0xFF
        Src8.PCL -> registers.pc and 0xFF
        Src8.SPH -> (registers.sp shr 8) and 0xFF
        Src8.SPL -> registers.sp and 0xFF
    }

    // ──── Register file access ──────────────────────────────────────────────────────
    fun readReg(reg: Int): Int = when (reg) {
        0 -> registers.b
        1 -> registers.c
        2 -> registers.d
        3 -> registers.e
        4 -> registers.h
        5 -> registers.l
        7 -> registers.a
        else -> throw IllegalArgumentException("Unknown register code: $reg")
    }

    fun writeReg(reg: Int, value: Int) {
        when (reg) {
            0 -> registers.b = value
            1 -> registers.c = value
            2 -> registers.d = value
            3 -> registers.e = value
            4 -> registers.h = value
            5 -> registers.l = value
            7 -> registers.a = value
            else -> throw IllegalArgumentException("Unknown register code: $reg")
        }
    }

    private fun load(code: Int) {
        val src = code and 0x07
        val dst = (code and 0x38) shr 3
        writeReg(dst, readReg(src))
    }

    private fun rst(vector: Int) {
        registers.pc = vector
    }

    // ──── Companion ─────────────────────────────────────────────────────────────────
    companion object {
        private const val GROUP_BIT = 1
    }
}
