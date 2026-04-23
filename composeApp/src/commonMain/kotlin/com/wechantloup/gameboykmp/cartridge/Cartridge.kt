package com.wechantloup.gameboykmp.cartridge

sealed interface Cartridge {
    fun readRom(address: Int): Int
    fun writeRom(address: Int, value: Int)
    fun readRam(address: Int): Int
    fun writeRam(address: Int, value: Int)
}
