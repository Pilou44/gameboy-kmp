package com.wechantloup.gameboykmp.cartridge

// TODO: Handle battery-backed RAM persistence (MBC1_RAM_BATTERY)
class Mbc1Cartridge(private val rom: ByteArray) : Cartridge {
    private var romBank = 1
    private var ramBank = 0
    private var ramEnabled = false
    private var bankingMode = 0  // 0=ROM banking, 1=RAM banking
    private val ram = IntArray(0x8000)  // 32KB max

    override fun readRom(address: Int): Int {
        return when (address) {
            in 0x0000..0x3FFF -> {
                // In RAM banking mode (mode 1), ramBank bits 0-1 become bits 5-6 of the ROM bank number
                // allowing access to banks 0x00, 0x20, 0x40, 0x60 in the lower ROM area
                val bank = if (bankingMode == 1) (ramBank shl 5) else 0
                rom[bank * 0x4000 + address].toInt() and 0xFF
            }
            in 0x4000..0x7FFF -> {
                // In ROM banking mode (mode 0), ramBank bits 0-1 become bits 5-6 of the ROM bank number,
                // allowing access to all 128 banks (7 bits total: 5 from romBank + 2 from ramBank)
                val bank = if (bankingMode == 0) romBank or (ramBank shl 5) else romBank
                rom[bank * 0x4000 + (address - 0x4000)].toInt() and 0xFF
            }
            else -> throw IllegalArgumentException("Bad address")
        }
    }

    override fun writeRom(address: Int, value: Int) {
        when (address) {
            in 0x0000..0x1FFF -> { // enable/disable RAM
                ramEnabled = value and 0x0F == 0x0A
            }
            in 0x2000..0x3FFF -> { // select ROM bank (bits 0-4)
                romBank = (value and 0x1F).coerceAtLeast(1)
            }
            in 0x4000..0x5FFF -> {
                // Store 2 bits used as:
                // - RAM bank number for 0xA000..0xBFFF in mode 1
                // - bits 5-6 of ROM bank number for 0x4000..0x7FFF in mode 0
                // - bits 5-6 of ROM bank number for 0x0000..0x3FFF in mode 1
                ramBank = (value and 0x03)
            }
            in 0x6000..0x7FFF -> { // banking mode (0 or 1)
                bankingMode = value and 0x01
            }
        }
    }

    override fun readRam(address: Int): Int {
        if (!ramEnabled) return 0xFF
        return ram[ramBank * 0x2000 + address]
    }

    override fun writeRam(address: Int, value: Int) {
        if (!ramEnabled) return
        ram[ramBank * 0x2000 + address] = value
    }
}
