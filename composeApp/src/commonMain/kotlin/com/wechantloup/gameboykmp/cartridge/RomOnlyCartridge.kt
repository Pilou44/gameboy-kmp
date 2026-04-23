package com.wechantloup.gameboykmp.cartridge

class RomOnlyCartridge(private val rom: ByteArray) : Cartridge {

    override fun readRom(address: Int): Int {
        return rom[address].toInt() and 0xFF
    }

    override fun writeRom(address: Int, value: Int) {
        // Read-only, ignore writes
    }

    override fun readRam(address: Int): Int {
        // No external RAM
        return 0xFF
    }

    override fun writeRam(address: Int, value: Int) {
        // No external RAM, ignore writes
    }
}
