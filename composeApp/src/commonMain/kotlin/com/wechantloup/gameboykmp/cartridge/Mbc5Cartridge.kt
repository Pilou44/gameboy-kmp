package com.wechantloup.gameboykmp.cartridge

import com.wechantloup.gameboykmp.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class Mbc5Cartridge(
    private val rom: ByteArray,
    private val romName: String,
    private val scope: CoroutineScope,
    private val withBattery: Boolean,
): Cartridge {
    private var saveJob: Job? = null
    private val _isSaving = MutableStateFlow(false)
    override val isSaving: StateFlow<Boolean> = _isSaving

    private var ramEnabled = false
    private var romBank = 1
    private var ramBank = 0

    private val romBankCount: Int = run {
        val fromHeader = when (rom[0x0148].toInt() and 0xFF) {
            0x00 -> 2
            0x01 -> 4
            0x02 -> 8
            0x03 -> 16
            0x04 -> 32
            0x05 -> 64
            0x06 -> 128
            0x07 -> 256
            0x08 -> 512
            else -> throw IllegalStateException("Unknown ROM size byte at 0x0148")
        }
        val fromSize = rom.size / 0x4000
        require(fromHeader == fromSize) {
            "ROM size mismatch: header says $fromHeader banks but file has $fromSize banks"
        }
        fromHeader
    }

    private val ramBankCount: Int = run {
        when (rom[0x0149].toInt() and 0xFF) {
            0x00 -> 0 // No RAM
            0x01 -> 0 // Unused
            0x02 -> 1 // 8kB
            0x03 -> 4 // 32kB
            0x04 -> 16 // 128kB
            0x05 -> 8 // 64kB
            else -> throw IllegalStateException("Unknown RAM size byte at 0x0149")
        }
    }

    private val ram = IntArray(0x20000) // 128KB max - 16 banks × 8KB
        .also { ram ->
            if (withBattery) {
                try {
                    SaveManager.load(romName)?.copyInto(ram)
                } catch (e: Exception) {
                    Logger.error("Mbc5Cartridge", "Can't load save", e)
                }
            }
        }

    override fun readRom(address: Int): Int {
        return when (address) {
            in 0x0000..0x3FFF -> {
                rom[address].toInt() and 0xFF
            }
            in 0x4000..0x7FFF -> {
                val maskedBank = romBank and (romBankCount - 1)
                rom[maskedBank * 0x4000 + (address - 0x4000)].toInt() and 0xFF
            }
            else -> throw IllegalArgumentException("Bad address")
        }
    }

    override fun writeRom(address: Int, value: Int) {
        if (address !in 0x0000..0x5FFF) return

        when (address) {
            in 0x0000..0x1FFF -> {
                ramEnabled = value and 0x0F == 0x0A
            }
            in 0x2000..0x2FFF -> {
                // The 8 least significant bits of the ROM bank number
                val romBankHigh = romBank and 0x100
                val romBankLow = value and 0xFF
                romBank = romBankHigh or romBankLow
            }
            in 0x3000..0x3FFF -> {
                // The 9th bit of the ROM bank number
                val romBankHigh = (value and 0x01) shl 8
                val romBankLow = romBank and 0xFF
                romBank = romBankHigh or romBankLow
            }
            in 0x4000..0x5FFF -> {
                ramBank = value and 0x0F
            }
        }
    }

    override fun readRam(address: Int): Int {
        if (!ramEnabled || ramBankCount == 0) return 0xFF
        val maskedBank = ramBank and (ramBankCount - 1)
        return ram[maskedBank * 0x2000 + address]
    }

    override fun writeRam(address: Int, value: Int) {
        if (!ramEnabled || ramBankCount == 0) return
        val maskedBank = ramBank and (ramBankCount - 1)
        ram[maskedBank * 0x2000 + address] = value
        onRamWritten()
    }

    private fun onRamWritten() {
        if (!withBattery) return

        _isSaving.value = true
        saveJob?.cancel()
        saveJob = scope.launch(Dispatchers.IO) {
            delay(DEBOUNCE_DURATION_MS) // debounce: wait for writes to settle before persisting
            SaveManager.save(romName, ram)
            _isSaving.value = false
        }
    }

    companion object {
        private const val DEBOUNCE_DURATION_MS = 500L
    }
}
