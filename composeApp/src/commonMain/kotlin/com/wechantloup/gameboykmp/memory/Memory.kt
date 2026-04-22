package com.wechantloup.gameboykmp.memory

class Memory {
    private val memory = IntArray(0x10000)

    fun read(address: Int): Int {
        return memory[address]
    }

    fun write(address: Int, value: Int) {
        memory[address] = value and 0xFF
    }
}
