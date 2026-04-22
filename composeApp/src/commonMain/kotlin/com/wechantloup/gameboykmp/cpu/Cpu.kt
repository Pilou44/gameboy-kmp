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

            /* Unknown opcode */
            else -> TODO("Opcode 0x${opcode.toString(16)} not implemented")
        }
    }

    private fun load(code: Int) {
        val src = code and 0x07
        val dst = (code and 0x38).shr(3)

        val value = getRegister(src)
        setRegister(dst, value)
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

    private fun fetch(): Int {
        val data = memory.read(registers.pc) and 0xFF
        registers.pc = (registers.pc + 1) and 0xFFFF
        return data
    }
}
