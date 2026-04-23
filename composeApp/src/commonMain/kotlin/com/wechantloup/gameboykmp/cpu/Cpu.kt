package com.wechantloup.gameboykmp.cpu

import com.wechantloup.gameboykmp.bus.Bus

class Cpu(
    private val bus: Bus,
) {
    val registers = Registers()
    var isHalted = false
    var ime = false

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

        val opcode = fetch()
        execute(opcode) // TODO: return correct cycles per opcode
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
            // 6 -> (HL) - ToDo
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
            // 6 -> (HL) - ToDo
            7 -> registers.a = value
            else -> throw IllegalArgumentException("Unknown register code: $code")
        }
    }

    private fun execute(opcode: Int) {
        when (opcode) {
            0x00 -> { /* NOP - do nothing */ }

            0x76 -> halt() /* HALT - stop CPU until interrupt */

            /* Load block */

            0x3E -> registers.a = fetch() /* LD A, n   (load n in A) */
            0x06 -> registers.b = fetch() /* LD B, n   (load n in B) */
            0x0E -> registers.c = fetch() /* LD C, n   (load n in C) */
            0x16 -> registers.d = fetch() /* LD D, n   (load n in D) */
            0x1E -> registers.e = fetch() /* LD E, n   (load n in E) */
            0x26 -> registers.h = fetch() /* LD H, n   (load n in H) */
            0x2E -> registers.l = fetch() /* LD L, n   (load n in L) */

            in 0x40..0x7F -> load(opcode)

            0x01 -> registers.bc = fetch16() /* LD BC, nn */
            0x11 -> registers.de = fetch16() /* LD DE, nn */
            0x21 -> registers.hl = fetch16() /* LD HL, nn */
            0x31 -> registers.sp = fetch16() /* LD SP, nn */

            0x22 -> {
                bus.write(registers.hl, registers.a)
                registers.hl = (registers.hl + 1) and 0xFFFF
            } /* LD (HL+), A  - écrire A à (HL) puis incrémenter HL */
            0x32 -> {
                bus.write(registers.hl, registers.a)
                registers.hl = (registers.hl - 1) and 0xFFFF
            } /* LD (HL-), A  - écrire A à (HL) puis décrémenter HL */
            0x2A -> {
                registers.a = bus.read(registers.hl)
                registers.hl = (registers.hl + 1) and 0xFFFF
            } /* LD A, (HL+)  - lire (HL) dans A puis incrémenter HL */
            0x3A -> {
                registers.a = bus.read(registers.hl)
                registers.hl = (registers.hl - 1) and 0xFFFF
            } /* LD A, (HL-)  - lire (HL) dans A puis décrémenter HL */

            0xE0 -> {
                val offset = fetch()
                bus.write(0xFF00 + offset, registers.a)
            } /* LD (0xFF00+n), A */
            0xF0 -> {
                val offset = fetch()
                registers.a = bus.read(0xFF00 + offset)
            }/* LD A, (0xFF00+n)  - lire depuis 0xFF00+n dans A */

            /* Arithmetic block */

            in 0x80..0x87 -> add(opcode) /* ADD A, r */
            in 0x88..0x8F -> add(opcode, withCarry = true) /* ADC A, r */

            in 0x90..0x97 -> sub(opcode) /* SUB A, r */
            in 0x98..0x9F -> sub(opcode, withCarry = true) /* SBC A, r */

            in 0xA0..0xA7 -> and8(opcode)
            in 0xB0..0xB7 -> or8(opcode)
            in 0xA8..0xAF -> xor8(opcode)

            in 0xB8..0xBF -> sub(opcode, storeResult = false) /* CP */

            0x04 -> inc(0) /* INC B */
            0x0C -> inc(1) /* INC C */
            0x14 -> inc(2) /* INC D */
            0x1C -> inc(3) /* INC E */
            0x24 -> inc(4) /* INC H */
            0x2C -> inc(5) /* INC L */
            0x3C -> inc(7) /* INC A */

            0x05 -> dec(0) /* DEC B */
            0x0D -> dec(1) /* DEC C */
            0x15 -> dec(2) /* DEC D */
            0x1D -> dec(3) /* DEC E */
            0x25 -> dec(4) /* DEC H */
            0x2D -> dec(5) /* DEC L */
            0x3D -> dec(7) /* DEC A */

            /* Jumps */

            0xC3 -> jp() /* JP nn (always jump) */
            0xC2 -> jp(!registers.flagZ) /* JP NZ    (jump if Z = false) */
            0xCA -> jp(registers.flagZ)  /*  JP Z     (jump if Z = true) */
            0xD2 -> jp(!registers.flagC) /* JP NC    (jump if C = false) */
            0xDA -> jp(registers.flagC)  /*  JP C     (jump if C = true) */

            0x18 -> jr() /* JR nn (always jump) */
            0x20 -> jr(!registers.flagZ) /* JR NZ    (jump if Z = false) */
            0x28 -> jr(registers.flagZ)  /* JR Z     (jump if Z = true) */
            0x30 -> jr(!registers.flagC) /* JR NC    (jump if C = false) */
            0x38 -> jr(registers.flagC)  /* JR C     (jump if C = true) */

            0xCD -> call() /* CALL nn (always) */
            0xC4 -> call(!registers.flagZ) /* CALL NZ */
            0xCC -> call(registers.flagZ)  /* CALL Z  */
            0xD4 -> call(!registers.flagC) /* CALL NC */
            0xDC -> call(registers.flagC)  /* CALL C  */

            0xC9 -> ret() /* RET (always) */
            0xC0 -> ret(!registers.flagZ) /* RET NZ */
            0xC8 -> ret(registers.flagZ)  /* RET Z  */
            0xD0 -> ret(!registers.flagC) /* RET NC */
            0xD8 -> ret(registers.flagC)  /* RET C  */

            0xC5 -> push(registers.bc) /* PUSH BC */
            0xD5 -> push(registers.de) /* PUSH DE */
            0xE5 -> push(registers.hl) /* PUSH HL */
            0xF5 -> push(registers.af) /* PUSH AF */

            0xC1 -> registers.bc = pop() /* POP BC */
            0xD1 -> registers.de = pop() /* POP DE */
            0xE1 -> registers.hl = pop() /* POP HL */
            0xF1 -> registers.af = pop() /* POP AF */

            /* Interruptions */

            0xF3 -> ime = false /* DI - Disable Interrupts */
            0xFB -> ime = true  /* EI - Enable Interrupts */
            0xD9 -> { registers.pc = pop(); ime = true } /* RETI - Return from Interrupt */

            /* Unknown opcode */
            else -> TODO("Opcode 0x${opcode.toString(16)} not implemented")
        }
    }

    private fun halt() {
        isHalted = true
    }

    private fun add(code: Int, withCarry: Boolean = false) {
        val src = code and 0x07
        val a = registers.a
        val b = getRegister(src)
        val carry = if (withCarry && registers.flagC) 1 else 0
        val addition = a + b + carry
        registers.a = addition and 0xFF
        registers.flagZ = (addition and 0xFF) == 0
        registers.flagN = false
        registers.flagH = (a and 0x0F) + (b and 0x0F) + carry > 0x0F
        registers.flagC = addition > 0xff
    }

    private fun sub(
        code: Int,
        withCarry: Boolean = false,
        storeResult: Boolean = true
    ) {
        val src = code and 0x07
        val a = registers.a
        val b = getRegister(src)
        val carry = if (withCarry && registers.flagC) 1 else 0
        val subtraction = a - b - carry

        if (storeResult) registers.a = subtraction and 0xFF

        registers.flagZ = (subtraction and 0xFF) == 0
        registers.flagN = true
        registers.flagH = (a and 0x0F) < (b and 0x0F) + carry
        registers.flagC = a < b + carry
    }

    private fun and8(code: Int) {
        val src = code and 0x07
        val a = registers.a
        val b = getRegister(src)

        val result = a and b
        registers.a = result and 0xFF
        registers.flagZ = (result and 0xFF) == 0
        registers.flagN = false
        registers.flagH = true
        registers.flagC = false
    }

    private fun or8(code: Int) {
        val src = code and 0x07
        val a = registers.a
        val b = getRegister(src)

        val result = a or b
        registers.a = result and 0xFF
        registers.flagZ = (result and 0xFF) == 0
        registers.flagN = false
        registers.flagH = false
        registers.flagC = false
    }

    private fun xor8(code: Int) {
        val src = code and 0x07
        val a = registers.a
        val b = getRegister(src)

        val result = a xor b
        registers.a = result and 0xFF
        registers.flagZ = (result and 0xFF) == 0
        registers.flagN = false
        registers.flagH = false
        registers.flagC = false
    }

    private fun inc(registerCode: Int) {
        var value = getRegister(registerCode)
        value++
        setRegister(registerCode, value and 0xFF)
        registers.flagZ = (value and 0xFF) == 0
        registers.flagN = false
        registers.flagH = (value - 1) and 0x0F == 0x0F
    }

    private fun dec(registerCode: Int) {
        var value = getRegister(registerCode)
        value--
        setRegister(registerCode, value and 0xFF)
        registers.flagZ = (value and 0xFF) == 0
        registers.flagN = true
        registers.flagH = (value + 1) and 0x0F == 0x00
    }

    private fun load(code: Int) {
        val src = code and 0x07
        val dst = (code and 0x38).shr(3)

        val value = getRegister(src)
        setRegister(dst, value)
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
        var address = 0
        address = address or bus.read(registers.sp)
        registers.sp = (registers.sp + 1) and 0xFFFF
        address = address or (bus.read(registers.sp) shl 8)
        registers.sp = (registers.sp + 1) and 0xFFFF
        return address
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
