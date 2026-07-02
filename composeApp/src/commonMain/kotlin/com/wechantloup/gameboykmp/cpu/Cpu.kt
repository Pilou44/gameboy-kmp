package com.wechantloup.gameboykmp.cpu

/**
 * Sharp SM83 CPU core.
 *
 * Timing contract: the CPU drives the rest of the system through [onMachineCycleTick], fired
 * once per machine cycle (M-cycle = 4 T-cycles). This granularity is deliberate, not a
 * simplification. The SM83 performs at most one bus access per M-cycle (opcode fetch, operand
 * read, or data read/write); between M-cycles it only does internal work (ALU, decode) that is
 * invisible to other components. Every externally observable CPU event therefore lands on an
 * M-cycle boundary, so ticking more often would gain no information — a finer callback would
 * fire 3 ticks out of 4 with nothing to report.
 *
 * Sub-M-cycle (per-dot) timing is the PPU's concern: each tick advances the PPU by a fixed
 * number of dots (4, or 2 in double-speed mode), and the PPU subdivides that span internally.
 * Dot-accurate behavior belongs there, never in this class.
 *
 * @param bus system bus the CPU reads from and writes to.
 * @param onMachineCycleTick callback fired once per M-cycle; see the timing contract above.
 */
class Cpu(
    private val bus: CpuBus,
    private val onMachineCycleTick: () -> Unit,
) {
    // ── Micro-op pipeline (T-cycle CPU core) ─────────────────────────────────────
    // WZ latches: 8-bit data in flight between T-cycles of the current instruction.
    private var latchW = 0
    private var latchZ = 0

    private val pipeline = RingBuffer<MicroOp>(32)
    private var microTCounter = 0   // T within the current M-cycle of the running sequence (0..3)

    val registers = Registers() // Visible for tests
    private var isStopped = false
    var ime = false // Visible for tests

    private var imeScheduled = false

    private var haltBug = false

    // Pre-built, shared micro-ops for the dynamic RET-taken tail (pushed by reference, zero alloc per RET).
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

    fun step() {
        // A general-purpose DMA started by the previous instruction freezes the CPU for the entire
        // transfer. Drain it here, at the instruction boundary, before anything else: the timer/PPU
        // advance during the stall, and any interrupt that comes due is then seen by the pending
        // check below — serviced right after the transfer, as on hardware (the CPU cannot dispatch
        // while frozen).
        drainGdmaStall()

        // STOP mode (DMG): frozen until a selected joypad input line goes low.
        // Checked before the interrupt logic: unlike HALT, a pending interrupt does
        // NOT wake STOP (here IE=IF=0x01 / VBlank pending when STOP runs).
        if (isStopped) {
            // A selected line is low (button pressed) when any of bits 0..3 is 0.
            if ((bus.read(0xFF00) and 0x0F) != 0x0F) {
                isStopped = false
            }
            // Keep ticking: the PPU needs at least one tick after LCDC bit 7 was cleared
            // to run its "LCD off" path and push the blank frame. Once off, its step()
            // early-returns, so the screen stays blank while stopped.
            onMachineCycleTick()
            return
        }

        // Check for pending interrupts
        val pending = bus.ie and bus.iF and 0x1F

        if (pending != 0) {
            // Wake from HALT regardless of IME
            if (bus.cpuHalted) {
                bus.cpuHalted = false
                if (!ime) {
                    // IME=false: just wake, no extra M-cycle consumed.
                    // Next step() will fetch the instruction after HALT normally.
                    return
                }
                // IME=true: fall through to service interrupt immediately
            }

            if (ime) {
                ime = false

                onMachineCycleTick()  // internal M-cycle 1
                onMachineCycleTick()  // internal M-cycle 2

                // Write PC to stack manually — intentionally NOT using push(),
                // which adds an extra internal M-cycle suited for the PUSH instruction
                // but not for interrupt dispatch.
                registers.sp = (registers.sp - 1) and 0xFFFF
                bus.write(registers.sp, (registers.pc shr 8) and 0xFF)
                onMachineCycleTick()  // M-cycle 3: write PCH (may overwrite IE when SP-1 == $FFFF)

                // The interrupt vector is decided HERE, right after the high-byte push:
                // IE & IF are re-sampled, so a push that just overwrote IE ($FFFF) changes
                // the outcome. If no enabled+requested bit remains, the dispatch is
                // cancelled and PC is forced to $0000 (IF is left untouched). A later
                // overwrite of IE by the low-byte push comes too late to matter.
                val latePending = bus.ie and bus.iF and 0x1F
                val bit = if (latePending != 0) latePending.countTrailingZeroBits() else -1

                registers.sp = (registers.sp - 1) and 0xFFFF
                bus.write(registers.sp, registers.pc and 0xFF)
                onMachineCycleTick()  // M-cycle 4: write PCL (a write to IE here is too late)

                registers.pc = if (bit >= 0) {
                    bus.setIF(bus.iF and (1 shl bit).inv())  // clear only the serviced bit
                    when (bit) {
                        0 -> 0x0040  // V-Blank
                        1 -> 0x0048  // LCD STAT
                        2 -> 0x0050  // Timer
                        3 -> 0x0058  // Serial
                        4 -> 0x0060  // Joypad
                        else -> 0x0040
                    }
                } else {
                    0x0000  // dispatch cancelled by the IE overwrite — no IF bit cleared
                }
                onMachineCycleTick()  // M-cycle 5: jump to vector
                return
            }
        }

        if (bus.cpuHalted) {
            onMachineCycleTick()
            return
        }

        pipeline.push(MicroOp.FetchOpCode)

        while (!pipeline.isEmpty) {
            perform(pipeline.pop())
            if (++microTCounter == 4) {
                microTCounter = 0
                onMachineCycleTick()   // one M-cycle elapsed; producers still batched (step 1)
            }
        }
    }

    fun reset() {
        ime = false
        registers.reset()
        bus.cpuHalted = false
    }

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

    private fun load(code: Int) {
        val src = code and 0x07
        val dst = (code and 0x38) shr 3
        writeReg(dst, readReg(src))
    }

    private fun rst(vector: Int) {
        registers.pc = vector
    }

    /**
     * Consumes the CPU stall published by a general-purpose DMA. GDMA freezes the CPU for the
     * whole transfer; the Bus only publishes the M-cycle count (it cannot tick), so the CPU drains
     * it through the same machine-cycle tick as everything else, advancing timer/PPU/APU by exactly
     * the transfer duration. Speed is already baked into the published count (8 M-cycles per block
     * in normal speed, 16 in double speed).
     */
    private fun drainGdmaStall() {
        val stallMCycles = bus.pendingGdmaStallMCycles
        if (stallMCycles == 0) return
        bus.pendingGdmaStallMCycles = 0
        repeat(stallMCycles) { onMachineCycleTick() }
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

    private fun perform(op: MicroOp) {
        when (op) {
            MicroOp.Idle -> Unit

            is MicroOp.FetchOpCode -> fetchOpCode()

            is MicroOp.ReadImmediate -> {
                val v = bus.read(registers.pc)
                registers.pc = (registers.pc + 1) and 0xFFFF
                setLatch(op.into, v)
            }
            is MicroOp.ReadMem  -> setLatch(op.into, bus.read(addr16(op.addr)))
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

    private fun pushFetchPadding() {
        pipeline.push(MicroOp.Idle)
        pipeline.push(MicroOp.Idle)
        pipeline.push(MicroOp.Idle)
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
     * the (sp xor offset xor result) trick, kept identical to the legacy execute() so the harness matches.
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
            // rotations / RES / SET on (HL): read-modify-write → 2 M-cycles.
            pipeline.push(opCbReadHL)          // M read: ReadMem(HL → Z)
            pipeline.push(MicroOp.Idle)
            pipeline.push(MicroOp.Idle)
            pipeline.push(MicroOp.Idle)
            pipeline.push(opCbApplyZ)        // M write: transform Z...
            pipeline.push(MicroOp.Idle)
            pipeline.push(MicroOp.Idle)
            pipeline.push(opCbWriteHL)         // ...then WriteMem(HL, Z)
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

    companion object {
        private const val GROUP_BIT = 1
    }
}
