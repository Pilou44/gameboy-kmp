package com.wechantloup.gameboykmp.cartridge

import kotlinx.coroutines.CoroutineScope

object CartridgeFactory {
    fun create(
        rom: ByteArray,
        romName: String,
        scope: CoroutineScope,
    ): Cartridge {
        val typeCode = rom[0x0147].toInt() and 0xFF
        val type = CartridgeType.fromCode(typeCode)
            ?: throw IllegalArgumentException("Unknown cartridge type: 0x${typeCode.toString(16)}")
        return when (type) {
            CartridgeType.ROM_ONLY,
            CartridgeType.ROM_RAM,
            CartridgeType.ROM_RAM_BATTERY,
            -> RomRamCartridge(
                rom = rom,
                romName = romName,
                scope = scope,
                withBattery = type == CartridgeType.ROM_RAM_BATTERY,
            )

            CartridgeType.MBC1,
            CartridgeType.MBC1_RAM,
            CartridgeType.MBC1_RAM_BATTERY,
            -> Mbc1Cartridge(
                rom = rom,
                romName = romName,
                scope = scope,
                withBattery = type == CartridgeType.MBC1_RAM_BATTERY,
            )

            CartridgeType.MBC2,
            CartridgeType.MBC2_BATTERY,
            -> Mbc2Cartridge(
                rom = rom,
                romName = romName,
                scope = scope,
                withBattery = type == CartridgeType.MBC2_BATTERY,
            )
            else -> TODO("Cartridge type $type not yet implemented")
        }
    }
}
