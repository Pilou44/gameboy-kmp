package com.wechantloup.gameboykmp.cartridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class Mbc1Cartridge(
    private val rom: ByteArray,
    private val romName: String,
    private val scope: CoroutineScope,
    private val withBattery: Boolean,
) : Cartridge {
    private val _isSaving = MutableStateFlow(false)
    override val isSaving: StateFlow<Boolean> = _isSaving

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

    private var romBank = 1
    private var ramBank = 0
    private var ramEnabled = false
    private var bankingMode = 0  // 0=ROM banking, 1=RAM banking
    private val ram = IntArray(0x8000)  // 32KB max
        .also { ram ->
            if (withBattery) { SaveManager.load(romName)?.copyInto(ram) }
        }

    private var saveJob: Job? = null

    override fun readRom(address: Int): Int {
        return when (address) {
            in 0x0000..0x3FFF -> {
                // In RAM banking mode (mode 1), ramBank bits 0-1 become bits 5-6 of the ROM bank number
                // allowing access to banks 0x00, 0x20, 0x40, 0x60 in the lower ROM area
                val bank = if (bankingMode == 1) (ramBank shl 5) else 0
                // romBankCount is always a power of 2 (2, 4, 8, 16...), so (romBankCount - 1) is a perfect bit mask.
                // e.g. 64 banks → 64 - 1 = 63 = 0b00111111, masking any bank number to the valid range.
                val maskedBank = bank and (romBankCount - 1)
                rom[maskedBank * 0x4000 + address].toInt() and 0xFF
            }
            in 0x4000..0x7FFF -> {
                // In ROM banking mode (mode 0), ramBank bits 0-1 become bits 5-6 of the ROM bank number,
                // allowing access to all 128 banks (7 bits total: 5 from romBank + 2 from ramBank)
                val bank = if (bankingMode == 0) romBank or (ramBank shl 5) else romBank
                // romBankCount is always a power of 2 (2, 4, 8, 16...), so (romBankCount - 1) is a perfect bit mask.
                // e.g. 64 banks → 64 - 1 = 63 = 0b00111111, masking any bank number to the valid range.
                val maskedBank = bank and (romBankCount - 1)
                rom[maskedBank * 0x4000 + (address - 0x4000)].toInt() and 0xFF
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
        onRamWritten()  // persist on every write
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
