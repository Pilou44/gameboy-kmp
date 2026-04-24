package com.wechantloup.gameboykmp.cpu

import com.wechantloup.gameboykmp.bus.Bus

class Cpu(
    private val bus: Bus,
) {
    val registers = Registers()
    var isHalted = false
    var ime = false
    var imeScheduled = false

    fun step(): Int {
        // Check for pending interrupts
        val pending = bus.ie and bus.iF and 0x1F

        if (pending != 0) {
            // Wake from HALT regardless of IME
            isHalted = false

            if (ime) {
                ime = false

                // Find highest priority interrupt (lowest bit)
                val bit = pending.countTrailingZeroBits()

                // Clear the bit in IF
                bus.setIF(bus.iF and (1 shl bit).inv())

                // Push current PC and jump to interrupt vector
                push(registers.pc)
                registers.pc = when (bit) {
                    0 -> 0x0040  // V-Blank
                    1 -> 0x0048  // LCD STAT
                    2 -> 0x0050  // Timer
                    3 -> 0x0058  // Serial
                    4 -> 0x0060  // Joypad
                    else -> 0x0040
                }
            }
            return 4 // TODO: return correct interrupt cycles
        }

        if (isHalted) return 4

//        // Log les 20 premières instructions
//        if (registers.pc < 0x0400) {
//            println("PC=0x${registers.pc.toString(16)} opcode=0x${bus.read(registers.pc).toString(16)}")
//        }

        val opcode = fetch()
        execute(opcode) // TODO: return correct cycles per opcode
        if (imeScheduled) {
            ime = true
            imeScheduled = false
        }
        return 4
    }

    fun reset() {
        ime = false
        registers.reset()
    }

    internal fun getRegister(code: Int): Int {
        return when (code) {
            0 -> registers.b
            1 -> registers.c
            2 -> registers.d
            3 -> registers.e
            4 -> registers.h
            5 -> registers.l
            6 -> bus.read(registers.hl)  // (HL)
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
            6 -> bus.write(registers.hl, value)  // (HL)
            7 -> registers.a = value
            else -> throw IllegalArgumentException("Unknown register code: $code")
        }
    }

    private fun execute(opcode: Int) {
        when (opcode) {
            0x00 -> { /* NOP - do nothing */ }

            0x76 -> isHalted = true  // HALT

            /* --- 8-bit loads: immediate --- */
            0x06 -> registers.b = fetch()
            0x0E -> registers.c = fetch()
            0x16 -> registers.d = fetch()
            0x1E -> registers.e = fetch()
            0x26 -> registers.h = fetch()
            0x2E -> registers.l = fetch()
            0x36 -> bus.write(registers.hl, fetch())  // LD (HL), n
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
                bus.write(addr + 1, (registers.sp shr 8) and 0xFF)
            }
            0xF8 -> {                                   // LD HL, SP+n
                val offset = fetch().toByte().toInt()
                val result = (registers.sp + offset) and 0xFFFF
                registers.flagZ = false
                registers.flagN = false
                registers.flagH = (registers.sp xor offset xor result) and 0x10 != 0
                registers.flagC = (registers.sp xor offset xor result) and 0x100 != 0
                registers.hl = result
            }
            0xF9 -> registers.sp = registers.hl        // LD SP, HL

            /* --- LD (HL±), A / LD A, (HL±) --- */
            0x22 -> { bus.write(registers.hl, registers.a); registers.hl = (registers.hl + 1) and 0xFFFF }
            0x32 -> { bus.write(registers.hl, registers.a); registers.hl = (registers.hl - 1) and 0xFFFF }
            0x2A -> { registers.a = bus.read(registers.hl); registers.hl = (registers.hl + 1) and 0xFFFF }
            0x3A -> { registers.a = bus.read(registers.hl); registers.hl = (registers.hl - 1) and 0xFFFF }

            /* --- LD (BC/DE), A / LD A, (BC/DE) --- */
            0x02 -> bus.write(registers.bc, registers.a)
            0x12 -> bus.write(registers.de, registers.a)
            0x0A -> registers.a = bus.read(registers.bc)
            0x1A -> registers.a = bus.read(registers.de)

            /* --- I/O loads --- */
            0xE0 -> bus.write(0xFF00 + fetch(), registers.a)
            0xF0 -> registers.a = bus.read(0xFF00 + fetch())
            0xE2 -> bus.write(0xFF00 + registers.c, registers.a)
            0xF2 -> registers.a = bus.read(0xFF00 + registers.c)
            0xEA -> bus.write(fetch16(), registers.a)
            0xFA -> registers.a = bus.read(fetch16())

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
            0x04 -> inc(0); 0x0C -> inc(1); 0x14 -> inc(2); 0x1C -> inc(3)
            0x24 -> inc(4); 0x2C -> inc(5); 0x34 -> inc(6); 0x3C -> inc(7)

            0x05 -> dec(0); 0x0D -> dec(1); 0x15 -> dec(2); 0x1D -> dec(3)
            0x25 -> dec(4); 0x2D -> dec(5); 0x35 -> dec(6); 0x3D -> dec(7)

            /* --- 16-bit INC/DEC --- */
            0x03 -> registers.bc = (registers.bc + 1) and 0xFFFF
            0x13 -> registers.de = (registers.de + 1) and 0xFFFF
            0x23 -> registers.hl = (registers.hl + 1) and 0xFFFF
            0x33 -> registers.sp = (registers.sp + 1) and 0xFFFF

            0x0B -> registers.bc = (registers.bc - 1) and 0xFFFF
            0x1B -> registers.de = (registers.de - 1) and 0xFFFF
            0x2B -> registers.hl = (registers.hl - 1) and 0xFFFF
            0x3B -> registers.sp = (registers.sp - 1) and 0xFFFF

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
            0x2F -> { registers.a = registers.a.inv() and 0xFF; registers.flagN = true; registers.flagH = true }  // CPL
            0x37 -> { registers.flagN = false; registers.flagH = false; registers.flagC = true }   // SCF
            0x3F -> { registers.flagN = false; registers.flagH = false; registers.flagC = !registers.flagC }  // CCF

            /* --- Jumps --- */
            0xC3 -> jp()
            0xC2 -> jp(!registers.flagZ); 0xCA -> jp(registers.flagZ)
            0xD2 -> jp(!registers.flagC); 0xDA -> jp(registers.flagC)
            0xE9 -> registers.pc = registers.hl  // JP HL

            0x18 -> jr()
            0x20 -> jr(!registers.flagZ); 0x28 -> jr(registers.flagZ)
            0x30 -> jr(!registers.flagC); 0x38 -> jr(registers.flagC)

            /* --- CALL / RET --- */
            0xCD -> call()
            0xC4 -> call(!registers.flagZ); 0xCC -> call(registers.flagZ)
            0xD4 -> call(!registers.flagC); 0xDC -> call(registers.flagC)

            0xC9 -> ret()
            0xC0 -> ret(!registers.flagZ); 0xC8 -> ret(registers.flagZ)
            0xD0 -> ret(!registers.flagC); 0xD8 -> ret(registers.flagC)
            0xD9 -> { registers.pc = pop(); ime = true }  // RETI

            /* --- PUSH / POP --- */
            0xC5 -> push(registers.bc); 0xD5 -> push(registers.de)
            0xE5 -> push(registers.hl); 0xF5 -> push(registers.af)

            0xC1 -> registers.bc = pop(); 0xD1 -> registers.de = pop()
            0xE1 -> registers.hl = pop(); 0xF1 -> registers.af = pop()

            /* --- RST --- */
            0xC7 -> rst(0x00); 0xCF -> rst(0x08)
            0xD7 -> rst(0x10); 0xDF -> rst(0x18)
            0xE7 -> rst(0x20); 0xEF -> rst(0x28)
            0xF7 -> rst(0x30); 0xFF -> rst(0x38)

            /* --- Interrupts --- */
            0xF3 -> ime = false
            0xFB -> imeScheduled = true

            /* --- CB prefix --- */
            0xCB -> executeCb(fetch())

            else -> println("Opcode 0x${opcode.toString(16).uppercase()} not implemented at PC=0x${(registers.pc - 1).toString(16)}")
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
            else -> when {
                opcode in 0x40..0x7F -> {  // BIT b, r
                    val bit = (opcode - 0x40) shr 3
                    registers.flagZ = (getRegister(reg) shr bit) and 1 == 0
                    registers.flagN = false
                    registers.flagH = true
                }
                opcode in 0x80..0xBF -> {  // RES b, r
                    val bit = (opcode - 0x80) shr 3
                    setRegister(reg, getRegister(reg) and (1 shl bit).inv())
                }
                else -> {  // SET b, r (0xC0..0xFF)
                    val bit = (opcode - 0xC0) shr 3
                    setRegister(reg, getRegister(reg) or (1 shl bit))
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
        if (!registers.flagN) {
            if (registers.flagH || (a and 0x0F) > 9) a += 0x06
            if (registers.flagC || a > 0x99) { a += 0x60; registers.flagC = true }
        } else {
            if (registers.flagH) a -= 0x06
            if (registers.flagC) a -= 0x60
        }
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
        if (condition) registers.pc = value
    }

    private fun jr(condition: Boolean = true) {
        val offset = fetch().toByte().toInt()
        if (condition) registers.pc = (registers.pc + offset) and 0xFFFF
    }

    private fun call(condition: Boolean = true) {
        val value = fetch16()
        if (condition) {
            push(registers.pc)
            registers.pc = value
        }
    }

    private fun ret(condition: Boolean = true) {
        if (condition) registers.pc = pop()
    }

    private fun fetch(): Int {
        val data = bus.read(registers.pc) and 0xFF
        registers.pc = (registers.pc + 1) and 0xFFFF
        return data
    }

    private fun fetch16(): Int {
        val low = fetch()
        val high = fetch()
        return (high shl 8) or low
    }

    private fun push(address: Int) {
        registers.sp = (registers.sp - 1) and 0xFFFF
        bus.write(registers.sp, (address shr 8) and 0xFF)
        registers.sp = (registers.sp - 1) and 0xFFFF
        bus.write(registers.sp, address and 0xFF)
    }

    private fun pop(): Int {
        val low = bus.read(registers.sp)
        registers.sp = (registers.sp + 1) and 0xFFFF
        val high = bus.read(registers.sp)
        registers.sp = (registers.sp + 1) and 0xFFFF
        return (high shl 8) or low
    }

    /**
     * Initialize registers with boot values.
     */
    private fun Registers.reset() {
        a = 0x01
        b = 0x00
        c = 0x13
        d = 0x00
        e = 0xD8
        h = 0x01
        l = 0x4D
        f = 0xB0
        pc = 0x0100
        sp = 0xFFFE
    }
}
