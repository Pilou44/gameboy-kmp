package com.wechantloup.gameboykmp.memory

import com.wechantloup.gameboykmp.cartridge.Cartridge

/**
 * Represents the Game Boy memory bus - the full 64KB addressable space.
 *
 * Memory map:
 * 0x0000 - 0x3FFF  ROM Bank 0 (cartridge, fixed)
 * 0x4000 - 0x7FFF  ROM Bank N (cartridge, switchable via MBC)
 * 0x8000 - 0x9FFF  VRAM (Video RAM)
 * 0xA000 - 0xBFFF  External RAM (cartridge)
 * 0xC000 - 0xCFFF  Work RAM Bank 0
 * 0xD000 - 0xDFFF  Work RAM Bank 1
 * 0xE000 - 0xFDFF  Echo RAM (mirror of 0xC000-0xDDFF, avoid using)
 * 0xFE00 - 0xFE9F  OAM (Sprite Attribute Table)
 * 0xFEA0 - 0xFEFF  Unusable
 * 0xFF00 - 0xFF7F  I/O Registers
 * 0xFF80 - 0xFFFE  High RAM (HRAM)
 * 0xFFFF           Interrupt Enable Register
 */
class Memory(
    private val cartridge: Cartridge,
) {
    val ie: Int get() = read(0xFFFF)
    val iF: Int get() = read(0xFF0F)

    private val internalRam = IntArray(0x10000)

    fun read(address: Int): Int = when (address) {
        in 0x0000..0x7FFF -> cartridge.readRom(address)
        in 0xA000..0xBFFF -> cartridge.readRam(address - 0xA000)
        else -> internalRam[address]
    }

    fun write(address: Int, value: Int) {
        val v = value and 0xFF
        when (address) {
            in 0x0000..0x7FFF -> cartridge.writeRom(address, v)
            in 0xA000..0xBFFF -> cartridge.writeRam(address - 0xA000, v)
            else -> internalRam[address] = v
        }
    }

    fun setIF(value: Int) = write(0xFF0F, value)
}
