package com.wechantloup.gameboykmp.cartridge

import com.wechantloup.gameboykmp.logger.Logger
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
        Logger.debug("CartridgeFactory", "Cartridge type = ${type.name}")
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

            CartridgeType.MBC3_TIMER_BATTERY,
            CartridgeType.MBC3_TIMER_RAM_BATTERY,
            CartridgeType.MBC3,
            CartridgeType.MBC3_RAM,
            CartridgeType.MBC3_RAM_BATTERY,
            -> Mbc3Cartridge(
                rom = rom,
                romName = romName,
                scope = scope,
                withSave = type in setOf(
                    CartridgeType.MBC3_RAM_BATTERY,
                    CartridgeType.MBC3_TIMER_RAM_BATTERY,
                ),
                withRtc = type in setOf(
                    CartridgeType.MBC3_TIMER_BATTERY,
                    CartridgeType.MBC3_TIMER_RAM_BATTERY,
                ),
            )

            CartridgeType.MBC5,
            CartridgeType.MBC5_RAM,
            CartridgeType.MBC5_RAM_BATTERY,
            CartridgeType.MBC5_RUMBLE,
            CartridgeType.MBC5_RUMBLE_RAM,
            CartridgeType.MBC5_RUMBLE_RAM_BATTERY,
            -> Mbc5Cartridge(
                rom = rom,
                romName = romName,
                scope = scope,
                withBattery = type in setOf(
                    CartridgeType.MBC5_RAM_BATTERY,
                    CartridgeType.MBC5_RUMBLE_RAM_BATTERY,
                ),
            )
        }
    }
}
