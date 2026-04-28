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
            in 0x0000..0x3FFF -> rom[address].toInt() and 0xFF
            // TODO: For >32 banks, combine romBank with ramBank: romBank or (ramBank shl 5)
            in 0x4000..0x7FFF -> rom[romBank * 0x4000 + (address - 0x4000)].toInt() and 0xFF
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
            in 0x4000..0x5FFF -> { // select RAM bank or ROM high bits
                ramBank = (value and 0x03)
                // TODO: Handle high ROM bits for cartridges with more than 32 banks (bankingMode == 0)
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
