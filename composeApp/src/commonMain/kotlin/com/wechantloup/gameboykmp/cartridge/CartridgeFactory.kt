package com.wechantloup.gameboykmp.cartridge

object CartridgeFactory {
    fun create(rom: ByteArray): Cartridge {
        val typeCode = rom[0x0147].toInt() and 0xFF
        val type = CartridgeType.fromCode(typeCode)
            ?: throw IllegalArgumentException("Unknown cartridge type: 0x${typeCode.toString(16)}")
        return when (type) {
            CartridgeType.ROM_ONLY -> RomOnlyCartridge(rom)
            else -> TODO("Cartridge type $type not yet implemented")
        }
    }
}
