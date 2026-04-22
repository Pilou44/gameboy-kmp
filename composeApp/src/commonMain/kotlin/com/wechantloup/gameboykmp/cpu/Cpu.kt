package com.wechantloup.gameboykmp.cpu

import com.wechantloup.gameboykmp.memory.Memory

class Cpu(
    private val memory: Memory,
) {
    val registers = Registers()

    fun step() {
        val opcode = memory.read(registers.pc)
        registers.pc = (registers.pc + 1) and 0xFFFF
        execute(opcode)
    }

    private fun execute(opcode: Int) {

    }

    private fun fetch(): Int {
        val data = memory.read(registers.pc) and 0xFF
        registers.pc = (registers.pc + 1) and 0xFFFF
        return data
    }
}
