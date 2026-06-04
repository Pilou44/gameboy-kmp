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

class Mbc2Cartridge(
    private val rom: ByteArray,
    private val romName: String,
    private val scope: CoroutineScope,
    private val withBattery: Boolean,
) : Cartridge {
    private val _isSaving = MutableStateFlow(false)
    override val isSaving: StateFlow<Boolean> = _isSaving
    private var saveJob: Job? = null

    private var ramEnabled = false
    private var romBank = 1

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

    private val ram = IntArray(0x200) // 512 entries
        .also { ram ->
            if (withBattery) {
                try {
                    SaveManager.load(romName)?.copyInto(ram)
                } catch (e: Exception) {
                    Logger.error("Mbc2Cartridge", "Can't load save", e)
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
        if (address !in 0x0000..0x3FFF) return

        val bit8 = address and 0x100

        if (bit8 == 0) {
            ramEnabled = value and 0x0F == 0x0A
        } else {
            romBank = (value and 0x0F).coerceAtLeast(1)
        }
    }

    override fun readRam(address: Int): Int {
        if (!ramEnabled) return 0xFF
        return (ram[address and 0x1FF] and 0x0F) or 0xF0
    }

    override fun writeRam(address: Int, value: Int) {
        if (!ramEnabled) return
        ram[address and 0x1FF] = value and 0x0F
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
