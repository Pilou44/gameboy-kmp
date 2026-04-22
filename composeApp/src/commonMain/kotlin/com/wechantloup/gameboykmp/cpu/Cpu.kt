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

            /* Unknown opcode */
            else -> TODO("Opcode 0x${opcode.toString(16)} not implemented")
        }
    }

    private fun fetch(): Int {
        val data = memory.read(registers.pc) and 0xFF
        registers.pc = (registers.pc + 1) and 0xFFFF
        return data
    }
}
