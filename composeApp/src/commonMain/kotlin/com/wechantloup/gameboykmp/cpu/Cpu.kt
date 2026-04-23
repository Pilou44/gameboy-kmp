package com.wechantloup.gameboykmp.cpu

import com.wechantloup.gameboykmp.memory.Memory

class Cpu(
    private val memory: Memory,
) {
    val registers = Registers()

    fun step() {
        val opcode = fetch()
        execute(opcode)
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

            /* Load block */
            0x3E -> registers.a = fetch() /* LD A, n   (load n in A) */
            0x06 -> registers.b = fetch() /* LD B, n   (load n in B) */
            0x0E -> registers.c = fetch() /* LD C, n   (load n in C) */
            0x16 -> registers.d = fetch() /* LD D, n   (load n in D) */
            0x1E -> registers.e = fetch() /* LD E, n   (load n in E) */
            0x26 -> registers.h = fetch() /* LD H, n   (load n in H) */
            0x2E -> registers.l = fetch() /* LD L, n   (load n in L) */

            in 0x40..0x7F -> load(opcode)

            /* Arithmetic block */
            in 0x80..0x87 -> add(opcode) /* ADD A, r */
            in 0x88..0x8F -> add(opcode, withCarry = true) /* ADC A, r */

            in 0x90..0x97 -> sub(opcode) /* SUB A, r */
            in 0x98..0x9F -> sub(opcode, withCarry = true) /* SBC A, r */

            /* Logic block */
            in 0xA0..0xA7 -> and8(opcode)
            in 0xB0..0xB7 -> or8(opcode)
            in 0xA8..0xAF -> xor8(opcode)

            /* Unknown opcode */
            else -> TODO("Opcode 0x${opcode.toString(16)} not implemented")
        }
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

    private fun sub(code: Int, withCarry: Boolean = false) {
        val src = code and 0x07
        val a = registers.a
        val b = getRegister(src)
        val carry = if (withCarry && registers.flagC) 1 else 0
        val subtraction = a - b - carry
        registers.a = subtraction and 0xFF
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

    private fun load(code: Int) {
        val src = code and 0x07
        val dst = (code and 0x38).shr(3)

        val value = getRegister(src)
        setRegister(dst, value)
    }

    private fun fetch(): Int {
        val data = memory.read(registers.pc) and 0xFF
        registers.pc = (registers.pc + 1) and 0xFFFF
        return data
    }
}
