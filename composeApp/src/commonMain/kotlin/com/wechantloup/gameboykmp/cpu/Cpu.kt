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

        val opcode = fetch()

        if (imeScheduled) {
            ime = true
            imeScheduled = false
        }

        val seq = MicroCode.TABLE[opcode]
        if (seq != null) runMicroSequence(seq) else execute(opcode)   // migrated path vs legacy
    }

    fun reset() {
        ime = false
        registers.reset()
        bus.cpuHalted = false
    }

    internal fun getRegister(code: Int): Int {
        return when (code) {
            0 -> registers.b
            1 -> registers.c
            2 -> registers.d
            3 -> registers.e
            4 -> registers.h
            5 -> registers.l
            6 -> {
                val value = bus.read(registers.hl) // (HL)
                onMachineCycleTick()
                value
            }
            7 -> registers.a
            else -> throw IllegalArgumentException("Unknown register code: $code")
        }
    }

    internal fun setRegister(code: Int, value: Int) {
        when (code) {
            0 -> registers.b = value
            1 -> registers.c = value
            2 -> registers.d = value
            3 -> registers.e = value
            4 -> registers.h = value
            5 -> registers.l = value
            6 -> {
                bus.write(registers.hl, value) // (HL)
                onMachineCycleTick()
            }
            7 -> registers.a = value
            else -> throw IllegalArgumentException("Unknown register code: $code")
        }
    }

    private fun execute(opcode: Int) {
        when (opcode) {
            0x00 -> Unit /* NOP - do nothing */

            0x76 -> {
                val pending = bus.ie and bus.iF and 0x1F
                if (pending != 0 && !ime) {
                    haltBug = true  // halt bug regardless of IME state
                } else {
                    bus.cpuHalted = true
                }
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
            }

            /* --- 8-bit loads: immediate --- */
            0x06 -> registers.b = fetch()
            0x0E -> registers.c = fetch()
            0x16 -> registers.d = fetch()
            0x1E -> registers.e = fetch()
            0x26 -> registers.h = fetch()
            0x2E -> registers.l = fetch()
            0x36 -> {  // LD (H
                val value = fetch()
                bus.write(registers.hl, value)
                onMachineCycleTick()
            }
            0x3E -> registers.a = fetch()

            /* --- 8-bit loads: register to register (0x40–0x7F, 0x76=HALT handled above) --- */
            in 0x40..0x7F -> load(opcode)

            /* --- 16-bit loads: immediate --- */
            0x01 -> registers.bc = fetch16()
            0x11 -> registers.de = fetch16()
            0x21 -> registers.hl = fetch16()
            0x31 -> registers.sp = fetch16()

            /* --- 16-bit loads: special --- */
            0x08 -> {                                   // LD (nn), SP
                val addr = fetch16()
                bus.write(addr, registers.sp and 0xFF)
                onMachineCycleTick()
                bus.write(addr + 1, (registers.sp shr 8) and 0xFF)
                onMachineCycleTick()
            }
            0xF8 -> {                                   // LD HL, SP+n
                val offset = fetch().toByte().toInt()
                val result = (registers.sp + offset) and 0xFFFF
                registers.flagZ = false
                registers.flagN = false
                registers.flagH = (registers.sp xor offset xor result) and 0x10 != 0
                registers.flagC = (registers.sp xor offset xor result) and 0x100 != 0
                registers.hl = result
                onMachineCycleTick()
            }
            0xF9 -> {
                registers.sp = registers.hl        // LD SP, HL
                onMachineCycleTick()
            }

            /* --- LD (HL±), A / LD A, (HL±) --- */
            0x22 -> {
                bus.write(registers.hl, registers.a)
                onMachineCycleTick()
                registers.hl = (registers.hl + 1) and 0xFFFF
            }
            0x32 -> {
                bus.write(registers.hl, registers.a)
                onMachineCycleTick()
                registers.hl = (registers.hl - 1) and 0xFFFF
            }
            0x2A -> {
                registers.a = bus.read(registers.hl)
                onMachineCycleTick()
                registers.hl = (registers.hl + 1) and 0xFFFF
            }
            0x3A -> {
                registers.a = bus.read(registers.hl)
                onMachineCycleTick()
                registers.hl = (registers.hl - 1) and 0xFFFF
            }

            /* --- LD (BC/DE), A / LD A, (BC/DE) --- */
            0x02 -> {
                bus.write(registers.bc, registers.a)
                onMachineCycleTick()
            }
            0x12 -> {
                bus.write(registers.de, registers.a)
                onMachineCycleTick()
            }
            0x0A -> {
                registers.a = bus.read(registers.bc)
                onMachineCycleTick()
            }
            0x1A -> {
                registers.a = bus.read(registers.de)
                onMachineCycleTick()
            }

            /* --- I/O loads --- */
            0xE0 -> {
                val value = fetch()
                bus.write(0xFF00 + value, registers.a)
                onMachineCycleTick()
            }
            0xF0 -> {
                val value = fetch()
                registers.a = bus.read(0xFF00 + value)
                onMachineCycleTick()
            }
            0xE2 -> {
                bus.write(0xFF00 + registers.c, registers.a)
                onMachineCycleTick()
            }
            0xF2 -> {
                registers.a = bus.read(0xFF00 + registers.c)
                onMachineCycleTick()
            }
            0xEA -> {
                val value = fetch16()
                bus.write(value, registers.a)
                onMachineCycleTick()
            }
            0xFA -> {
                val address = fetch16()
                registers.a = bus.read(address)
                onMachineCycleTick()
            }

            /* --- 8-bit arithmetic: register --- */
            in 0x80..0x87 -> add(opcode)
            in 0x88..0x8F -> add(opcode, withCarry = true)
            in 0x90..0x97 -> sub(opcode)
            in 0x98..0x9F -> sub(opcode, withCarry = true)
            in 0xA0..0xA7 -> and8(opcode)
            in 0xA8..0xAF -> xor8(opcode)
            in 0xB0..0xB7 -> or8(opcode)
            in 0xB8..0xBF -> sub(opcode, storeResult = false)  // CP

            /* --- 8-bit arithmetic: immediate --- */
            0xC6 -> addImmediate(fetch())
            0xCE -> addImmediate(fetch(), withCarry = true)
            0xD6 -> subImmediate(fetch())
            0xDE -> subImmediate(fetch(), withCarry = true)
            0xE6 -> andImmediate(fetch())
            0xEE -> xorImmediate(fetch())
            0xF6 -> orImmediate(fetch())
            0xFE -> subImmediate(fetch(), storeResult = false)  // CP n

            /* --- 8-bit INC/DEC --- */
            0x04 -> inc(0)
            0x0C -> inc(1)
            0x14 -> inc(2)
            0x1C -> inc(3)
            0x24 -> inc(4)
            0x2C -> inc(5)
            0x34 -> inc(6)
            0x3C -> inc(7)

            0x05 -> dec(0)
            0x0D -> dec(1)
            0x15 -> dec(2)
            0x1D -> dec(3)
            0x25 -> dec(4)
            0x2D -> dec(5)
            0x35 -> dec(6)
            0x3D -> dec(7)

            /* --- 16-bit INC/DEC --- */
            0x03 -> {
                registers.bc = (registers.bc + 1) and 0xFFFF
                onMachineCycleTick()
            }
            0x13 -> {
                registers.de = (registers.de + 1) and 0xFFFF
                onMachineCycleTick()
            }
            0x23 -> {
                registers.hl = (registers.hl + 1) and 0xFFFF
                onMachineCycleTick()
            }
            0x33 -> {
                registers.sp = (registers.sp + 1) and 0xFFFF
                onMachineCycleTick()
            }

            0x0B -> {
                registers.bc = (registers.bc - 1) and 0xFFFF
                onMachineCycleTick()
            }
            0x1B -> {
                registers.de = (registers.de - 1) and 0xFFFF
                onMachineCycleTick()
            }
            0x2B -> {
                registers.hl = (registers.hl - 1) and 0xFFFF
                onMachineCycleTick()
            }
            0x3B -> {
                registers.sp = (registers.sp - 1) and 0xFFFF
                onMachineCycleTick()
            }

            /* --- ADD HL, rr --- */
            0x09 -> addHL(registers.bc)
            0x19 -> addHL(registers.de)
            0x29 -> addHL(registers.hl)
            0x39 -> addHL(registers.sp)

            /* --- ADD SP, n --- */
            0xE8 -> {
                val offset = fetch().toByte().toInt()
                val result = (registers.sp + offset) and 0xFFFF
                registers.flagZ = false
                registers.flagN = false
                registers.flagH = (registers.sp xor offset xor result) and 0x10 != 0
                registers.flagC = (registers.sp xor offset xor result) and 0x100 != 0
                registers.sp = result
                onMachineCycleTick()  // internal M-cycle 1
                onMachineCycleTick()  // internal M-cycle 2
            }

            /* --- Rotate accumulator --- */
            0x07 -> {  // RLCA
                val bit7 = (registers.a shr 7) and 1
                registers.a = ((registers.a shl 1) or bit7) and 0xFF
                registers.flagZ = false; registers.flagN = false; registers.flagH = false
                registers.flagC = bit7 != 0
            }
            0x0F -> {  // RRCA
                val bit0 = registers.a and 1
                registers.a = (registers.a ushr 1) or (bit0 shl 7)
                registers.flagZ = false; registers.flagN = false; registers.flagH = false
                registers.flagC = bit0 != 0
            }
            0x17 -> {  // RLA
                val oldC = if (registers.flagC) 1 else 0
                val bit7 = (registers.a shr 7) and 1
                registers.a = ((registers.a shl 1) or oldC) and 0xFF
                registers.flagZ = false; registers.flagN = false; registers.flagH = false
                registers.flagC = bit7 != 0
            }
            0x1F -> {  // RRA
                val oldC = if (registers.flagC) 1 else 0
                val bit0 = registers.a and 1
                registers.a = (registers.a ushr 1) or (oldC shl 7)
                registers.flagZ = false; registers.flagN = false; registers.flagH = false
                registers.flagC = bit0 != 0
            }

            /* --- Misc --- */
            0x27 -> daa()
            0x2F -> {
                // CPL
                registers.a = registers.a.inv() and 0xFF
                registers.flagN = true
                registers.flagH = true
            }
            0x37 -> {
                // SCF
                registers.flagN = false
                registers.flagH = false
                registers.flagC = true
            }
            0x3F -> {
                // CCF
                registers.flagN = false
                registers.flagH = false
                registers.flagC = !registers.flagC
            }

            /* --- Jumps --- */
            0xC3 -> jp()
            0xC2 -> jp(!registers.flagZ)
            0xCA -> jp(registers.flagZ)
            0xD2 -> jp(!registers.flagC)
            0xDA -> jp(registers.flagC)
            0xE9 -> registers.pc = registers.hl  // JP HL

            0x18 -> jr()
            0x20 -> jr(!registers.flagZ)
            0x28 -> jr(registers.flagZ)
            0x30 -> jr(!registers.flagC)
            0x38 -> jr(registers.flagC)

            /* --- CALL / RET --- */
            0xCD -> call()
            0xC4 -> call(!registers.flagZ)
            0xCC -> call(registers.flagZ)
            0xD4 -> call(!registers.flagC)
            0xDC -> call(registers.flagC)

            0xC9 -> {
                registers.pc = pop()
                onMachineCycleTick()
            }
            0xC0 -> ret(!registers.flagZ)
            0xC8 -> ret(registers.flagZ)
            0xD0 -> ret(!registers.flagC)
            0xD8 -> ret(registers.flagC)
            0xD9 -> {
                // RETI
                registers.pc = pop()
                onMachineCycleTick()
                ime = true
            }

            /* --- PUSH / POP --- */
            0xC5 -> push(registers.bc)
            0xD5 -> push(registers.de)
            0xE5 -> push(registers.hl)
            0xF5 -> push(registers.af)

            0xC1 -> registers.bc = pop()
            0xD1 -> registers.de = pop()
            0xE1 -> registers.hl = pop()
            0xF1 -> registers.af = pop()

            /* --- RST --- */
            0xC7 -> rst(0x00)
            0xCF -> rst(0x08)
            0xD7 -> rst(0x10)
            0xDF -> rst(0x18)
            0xE7 -> rst(0x20)
            0xEF -> rst(0x28)
            0xF7 -> rst(0x30)
            0xFF -> rst(0x38)

            /* --- Interrupts --- */
            0xF3 -> ime = false
            0xFB -> imeScheduled = true

            /* --- CB prefix --- */
            0xCB -> {
                val code = fetch()
                executeCb(code)
            }

            else -> TODO("Opcode 0x${opcode.toString(16).uppercase()} not implemented at PC=0x${(registers.pc - 1).toString(16)}")
        }
    }

    private fun executeCb(opcode: Int) {
        val reg = opcode and 0x07
        when (opcode and 0xF8) {
            0x00 -> {  // RLC r
                var v = getRegister(reg)
                val bit7 = (v shr 7) and 1
                v = ((v shl 1) or bit7) and 0xFF
                setRegister(reg, v)
                registers.flagZ = v == 0; registers.flagN = false; registers.flagH = false; registers.flagC = bit7 != 0
            }
            0x08 -> {  // RRC r
                var v = getRegister(reg)
                val bit0 = v and 1
                v = (v ushr 1) or (bit0 shl 7)
                setRegister(reg, v)
                registers.flagZ = v == 0; registers.flagN = false; registers.flagH = false; registers.flagC = bit0 != 0
            }
            0x10 -> {  // RL r
                var v = getRegister(reg)
                val bit7 = (v shr 7) and 1
                val oldC = if (registers.flagC) 1 else 0
                v = ((v shl 1) or oldC) and 0xFF
                setRegister(reg, v)
                registers.flagZ = v == 0; registers.flagN = false; registers.flagH = false; registers.flagC = bit7 != 0
            }
            0x18 -> {  // RR r
                var v = getRegister(reg)
                val bit0 = v and 1
                val oldC = if (registers.flagC) 1 else 0
                v = (v ushr 1) or (oldC shl 7)
                setRegister(reg, v)
                registers.flagZ = v == 0; registers.flagN = false; registers.flagH = false; registers.flagC = bit0 != 0
            }
            0x20 -> {  // SLA r
                var v = getRegister(reg)
                val bit7 = (v shr 7) and 1
                v = (v shl 1) and 0xFF
                setRegister(reg, v)
                registers.flagZ = v == 0; registers.flagN = false; registers.flagH = false; registers.flagC = bit7 != 0
            }
            0x28 -> {  // SRA r (arithmetic shift, sign extends)
                var v = getRegister(reg)
                val bit0 = v and 1
                val bit7 = v and 0x80
                v = (v ushr 1) or bit7
                setRegister(reg, v)
                registers.flagZ = v == 0; registers.flagN = false; registers.flagH = false; registers.flagC = bit0 != 0
            }
            0x30 -> {  // SWAP r
                var v = getRegister(reg)
                v = ((v and 0x0F) shl 4) or ((v and 0xF0) shr 4)
                setRegister(reg, v)
                registers.flagZ = v == 0; registers.flagN = false; registers.flagH = false; registers.flagC = false
            }
            0x38 -> {  // SRL r (logical shift)
                var v = getRegister(reg)
                val bit0 = v and 1
                v = v ushr 1
                setRegister(reg, v)
                registers.flagZ = v == 0; registers.flagN = false; registers.flagH = false; registers.flagC = bit0 != 0
            }
            else -> when (opcode) {
                in 0x40..0x7F -> {  // BIT b, r
                    val bit = (opcode - 0x40) shr 3
                    registers.flagZ = (getRegister(reg) shr bit) and 1 == 0
                    registers.flagN = false
                    registers.flagH = true
                }
                in 0x80..0xBF -> {  // RES b, r
                    val bit = (opcode - 0x80) shr 3
                    val value = getRegister(reg)
                    setRegister(reg, value and (1 shl bit).inv())
                }
                else -> {  // SET b, r (0xC0..0xFF)
                    val bit = (opcode - 0xC0) shr 3
                    val value = getRegister(reg)
                    setRegister(reg, value or (1 shl bit))
                }
            }
        }
    }

    private fun add(code: Int, withCarry: Boolean = false) {
        val a = registers.a
        val b = getRegister(code and 0x07)
        val carry = if (withCarry && registers.flagC) 1 else 0
        val result = a + b + carry
        registers.a = result and 0xFF
        registers.flagZ = (result and 0xFF) == 0
        registers.flagN = false
        registers.flagH = (a and 0x0F) + (b and 0x0F) + carry > 0x0F
        registers.flagC = result > 0xFF
    }

    private fun addImmediate(n: Int, withCarry: Boolean = false) {
        val a = registers.a
        val carry = if (withCarry && registers.flagC) 1 else 0
        val result = a + n + carry
        registers.a = result and 0xFF
        registers.flagZ = (result and 0xFF) == 0
        registers.flagN = false
        registers.flagH = (a and 0x0F) + (n and 0x0F) + carry > 0x0F
        registers.flagC = result > 0xFF
    }

    private fun sub(code: Int, withCarry: Boolean = false, storeResult: Boolean = true) {
        val a = registers.a
        val b = getRegister(code and 0x07)
        val carry = if (withCarry && registers.flagC) 1 else 0
        val result = a - b - carry
        if (storeResult) registers.a = result and 0xFF
        registers.flagZ = (result and 0xFF) == 0
        registers.flagN = true
        registers.flagH = (a and 0x0F) < (b and 0x0F) + carry
        registers.flagC = a < b + carry
    }

    private fun subImmediate(n: Int, withCarry: Boolean = false, storeResult: Boolean = true) {
        val a = registers.a
        val carry = if (withCarry && registers.flagC) 1 else 0
        val result = a - n - carry
        if (storeResult) registers.a = result and 0xFF
        registers.flagZ = (result and 0xFF) == 0
        registers.flagN = true
        registers.flagH = (a and 0x0F) < (n and 0x0F) + carry
        registers.flagC = a < n + carry
    }

    private fun and8(code: Int) {
        val result = registers.a and getRegister(code and 0x07)
        registers.a = result and 0xFF
        registers.flagZ = result == 0; registers.flagN = false; registers.flagH = true; registers.flagC = false
    }

    private fun andImmediate(n: Int) {
        val result = registers.a and n
        registers.a = result and 0xFF
        registers.flagZ = result == 0; registers.flagN = false; registers.flagH = true; registers.flagC = false
    }

    private fun or8(code: Int) {
        val result = registers.a or getRegister(code and 0x07)
        registers.a = result and 0xFF
        registers.flagZ = result == 0; registers.flagN = false; registers.flagH = false; registers.flagC = false
    }

    private fun orImmediate(n: Int) {
        val result = registers.a or n
        registers.a = result and 0xFF
        registers.flagZ = result == 0; registers.flagN = false; registers.flagH = false; registers.flagC = false
    }

    private fun xor8(code: Int) {
        val result = registers.a xor getRegister(code and 0x07)
        registers.a = result and 0xFF
        registers.flagZ = result == 0; registers.flagN = false; registers.flagH = false; registers.flagC = false
    }

    private fun xorImmediate(n: Int) {
        val result = registers.a xor n
        registers.a = result and 0xFF
        registers.flagZ = result == 0; registers.flagN = false; registers.flagH = false; registers.flagC = false
    }

    private fun addHL(value: Int) {
        val hl = registers.hl
        val result = hl + value
        registers.hl = result and 0xFFFF
        registers.flagN = false
        registers.flagH = (hl and 0x0FFF) + (value and 0x0FFF) > 0x0FFF
        registers.flagC = result > 0xFFFF
        onMachineCycleTick() // internal M-cycle (16-bit ALU)
    }

    private fun inc(registerCode: Int) {
        val old = getRegister(registerCode)
        val value = (old + 1) and 0xFF
        setRegister(registerCode, value)
        registers.flagZ = value == 0
        registers.flagN = false
        registers.flagH = (old and 0x0F) == 0x0F
    }

    private fun dec(registerCode: Int) {
        val old = getRegister(registerCode)
        val value = (old - 1) and 0xFF
        setRegister(registerCode, value)
        registers.flagZ = value == 0
        registers.flagN = true
        registers.flagH = (old and 0x0F) == 0x00
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
        setRegister(dst, getRegister(src))
    }

    private fun rst(vector: Int) {
        push(registers.pc)
        registers.pc = vector
    }

    private fun jp(condition: Boolean = true) {
        val value = fetch16()

        if (!condition) return

        registers.pc = value
        onMachineCycleTick()  // internal M-cycle (PC update)
    }

    private fun jr(condition: Boolean = true) {
        val offset = fetch().toByte().toInt()

        if (!condition) return

        registers.pc = (registers.pc + offset) and 0xFFFF
        onMachineCycleTick()  // internal M-cycle (PC update)
    }

    private fun call(condition: Boolean = true) {
        val value = fetch16()

        if (!condition) return

        push(registers.pc)
        registers.pc = value
    }

    private fun ret(condition: Boolean = true) {
        onMachineCycleTick()  // condition check M-cycle (always)
        if (!condition) return

        registers.pc = pop()
        onMachineCycleTick()  // jump M-cycle
    }

    private fun fetch(): Int {
        val data = bus.read(registers.pc) and 0xFF
        if (haltBug) {
            haltBug = false  // only skip increment once
        } else {
            registers.pc = (registers.pc + 1) and 0xFFFF
        }
        onMachineCycleTick()
        return data
    }

    private fun fetch16(): Int {
        val low = fetch()
        val high = fetch()
        return (high shl 8) or low
    }

    private fun push(address: Int) {
        onMachineCycleTick()  // internal M-cycle (stack pointer prep)
        registers.sp = (registers.sp - 1) and 0xFFFF
        bus.write(registers.sp, (address shr 8) and 0xFF)
        onMachineCycleTick()  // write high byte
        registers.sp = (registers.sp - 1) and 0xFFFF
        bus.write(registers.sp, address and 0xFF)
        onMachineCycleTick()  // write low byte
    }

    private fun pop(): Int {
        val low = bus.read(registers.sp)
        registers.sp = (registers.sp + 1) and 0xFFFF
        onMachineCycleTick()  // read low byte
        val high = bus.read(registers.sp)
        registers.sp = (registers.sp + 1) and 0xFFFF
        onMachineCycleTick()  // read high byte
        return (high shl 8) or low
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

    /**
     * Phase A: push the instruction's micro-ops, then drain them fully here, firing onMachineCycleTick
     * every 4 T. Access happens whenever its micro-op runs within the M-cycle — invisible to the harness,
     * which only sees (mCycle, op, addr, value). The full-drain is phase-A staging: phase B will instead
     * pop ONE micro-op per external tick(), the pipeline persisting across ticks.
     */
    private fun runMicroSequence(seq: Array<MicroOp>) {
        for (op in seq) pipeline.push(op)
        microTCounter = 0
        while (!pipeline.isEmpty) {
            perform(pipeline.pop())
            if (++microTCounter == 4) {
                microTCounter = 0
                onMachineCycleTick()   // one M-cycle elapsed; producers still batched (step 1)
            }
        }
    }

    private fun perform(op: MicroOp) {
        when (op) {
            MicroOp.Idle -> Unit
            is MicroOp.ReadImmediate -> {
                val v = bus.read(registers.pc)
                registers.pc = (registers.pc + 1) and 0xFFFF
                setLatch(op.into, v)
            }
            is MicroOp.ReadMem  -> setLatch(op.into, bus.read(addr16(op.addr)))
            is MicroOp.WriteMem -> bus.write(addr16(op.addr), src8(op.value))
            is MicroOp.Internal -> op.effect(this)
        }
    }

    private fun setLatch(l: Latch, v: Int) { when (l) { Latch.W -> latchW = v and 0xFF; Latch.Z -> latchZ = v and 0xFF } }

    private fun addr16(a: Addr16): Int = when (a) {
        Addr16.BC -> registers.bc
        Addr16.DE -> registers.de
        Addr16.HL -> registers.hl
        Addr16.SP -> registers.sp
        Addr16.WZ -> (latchW shl 8) or latchZ
    }

    private fun src8(s: Src8): Int = when (s) {
        Src8.A -> registers.a; Src8.B -> registers.b; Src8.C -> registers.c; Src8.D -> registers.d
        Src8.E -> registers.e; Src8.H -> registers.h; Src8.L -> registers.l
        Src8.W -> latchW; Src8.Z -> latchZ
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

    /** Pre-decrement SP. Its own T-cycle (one MicroOp = one T); shared by PUSH/CALL/RST. */
    internal fun microDecSp() { registers.sp = (registers.sp - 1) and 0xFFFF }

    /** Post-increment / post-decrement HL. Own T-cycle; shared by LD (HL±),A and LD A,(HL±). */
    internal fun microIncHl() { registers.hl = (registers.hl + 1) and 0xFFFF }
    internal fun microDecHl() { registers.hl = (registers.hl - 1) and 0xFFFF }

    /** Copy the Z latch into A. Internal T-cycle; shared by LD A,(HL±) and later LD A,(HL)/LD r,(HL). */
    internal fun microZtoA() { registers.a = latchZ }
}
