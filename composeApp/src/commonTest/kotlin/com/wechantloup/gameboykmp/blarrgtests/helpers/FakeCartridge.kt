package com.wechantloup.gameboykmp.blarrgtests.helpers

import com.wechantloup.gameboykmp.cartridge.Cartridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Minimal cartridge for unit tests.
 * Backed by a flat ByteArray — no MBC, no banking.
 * ROM: 0x0000–0x7FFF, RAM: 0xA000–0xBFFF
 */
class FakeCartridge : Cartridge {

    override val isSaving: StateFlow<Boolean> = MutableStateFlow(false)

    private val rom = ByteArray(0x8000)
    private val ram = ByteArray(0x2000)

    /** Optional override for ROM reads. If null, uses the internal rom array. */
    var onReadRom: ((address: Int) -> Int)? = null

    fun loadRom(address: Int, vararg bytes: Int) {
        bytes.forEachIndexed { i, b -> rom[address + i] = b.toByte() }
    }

    override fun readRom(address: Int): Int {
        return onReadRom?.invoke(address) ?: (rom[address].toInt() and 0xFF)
    }

    override fun writeRom(address: Int, value: Int) { /* read-only */ }
    override fun readRam(address: Int): Int = ram[address - 0xA000].toInt() and 0xFF
    override fun writeRam(address: Int, value: Int) { ram[address - 0xA000] = value.toByte() }
}
