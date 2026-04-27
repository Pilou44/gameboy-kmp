package com.wechantloup.gameboykmp.bus

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
class Bus(
    private val cartridge: Cartridge,
) {
    val ie: Int get() = read(0xFFFF)
    val iF: Int get() = read(0xFF0F)

    private val internalRam = IntArray(0x10000).also { initPostBootRegisters(it) }
    private val vram = IntArray(0x2000)  // 8KB
    private val oam = IntArray(0xA0) // 160 octets = 40 sprites × 4 octets

    fun read(address: Int): Int = when (address) {
        in 0x0000..0x7FFF -> cartridge.readRom(address)
        in 0x8000..0x9FFF -> readVram(address - 0x8000)
        in 0xA000..0xBFFF -> cartridge.readRam(address - 0xA000)
        in 0xFE00..0xFE9F -> readOam(address - 0XFE00)
        else -> internalRam[address]
    }

    fun write(address: Int, value: Int) {
        val v = value and 0xFF
        when (address) {
            in 0x0000..0x7FFF -> cartridge.writeRom(address, v)
            in 0x8000..0x9FFF -> writeVram(address - 0x8000, v)
            in 0xA000..0xBFFF -> cartridge.writeRam(address - 0xA000, v)
            in 0xFE00..0xFE9F -> writeOam(address - 0XFE00, v)
            else -> internalRam[address] = v
        }
    }

    fun readVram(address: Int): Int = vram[address]        // address 0x0000..0x1FFF
    fun writeVram(address: Int, value: Int) { vram[address] = value }

    fun readOam(address: Int): Int = oam[address]
    fun writeOam(address: Int, value: Int) { oam[address] = value }

    fun setIF(value: Int) = write(0xFF0F, value)

    companion object {
        /**
         * I/O register state left by the DMG boot ROM.
         * We skip the boot ROM and start at 0x0100, so we must reproduce this state.
         * Without it, LCDC=0 (LCD off) and games that poll LY==144 loop forever.
         */
        private fun initPostBootRegisters(ram: IntArray) {
            ram[0xFF05] = 0x00  // TIMA
            ram[0xFF06] = 0x00  // TMA
            ram[0xFF07] = 0x00  // TAC
            ram[0xFF10] = 0x80  // NR10
            ram[0xFF11] = 0xBF  // NR11
            ram[0xFF12] = 0xF3  // NR12
            ram[0xFF14] = 0xBF  // NR14
            ram[0xFF16] = 0x3F  // NR21
            ram[0xFF17] = 0x00  // NR22
            ram[0xFF19] = 0xBF  // NR24
            ram[0xFF1A] = 0x7F  // NR30
            ram[0xFF1B] = 0xFF  // NR31
            ram[0xFF1C] = 0x9F  // NR32
            ram[0xFF1E] = 0xBF  // NR33
            ram[0xFF20] = 0xFF  // NR41
            ram[0xFF21] = 0x00  // NR42
            ram[0xFF22] = 0x00  // NR43
            ram[0xFF23] = 0xBF  // NR44
            ram[0xFF24] = 0x77  // NR50
            ram[0xFF25] = 0xF3  // NR51
            ram[0xFF26] = 0xF1  // NR52
            ram[0xFF40] = 0x91  // LCDC — LCD on, BG on, tile data 0x8800, tile map 0x9800
            ram[0xFF41] = 0x85  // STAT — mode 1 (V-Blank)
            ram[0xFF42] = 0x00  // SCY
            ram[0xFF43] = 0x00  // SCX
            ram[0xFF44] = 0x00  // LY
            ram[0xFF45] = 0x00  // LYC
            ram[0xFF47] = 0xFC  // BGP
            ram[0xFF48] = 0xFF  // OBP0
            ram[0xFF49] = 0xFF  // OBP1
            ram[0xFF4A] = 0x00  // WY
            ram[0xFF4B] = 0x00  // WX
            ram[0xFFFF] = 0x00  // IE
        }
    }
}
