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

    private val ramBankCount: Int = run {
        when (rom[0x0149].toInt() and 0xFF) {
            0x00 -> 0 // No RAM
            0x01 -> 0 // Unused
            0x02 -> 1 // 8kB
            0x03 -> 4 // 32kB
            else -> throw IllegalStateException("Unknown RAM size byte at 0x0149")
        }
    }

    private val isMulticart: Boolean = run {
        val isMulticart = when (romBankCount) {
            64 -> {
                val bank2 = rom.copyOfRange(16 * 0x4000 + 0x104, 16 * 0x4000 + 0x134)
                val bank3 = rom.copyOfRange(32 * 0x4000 + 0x104, 32 * 0x4000 + 0x134)
                bank2.contentEquals(nintendoLogo) || bank3.contentEquals(nintendoLogo)
            }
            128 -> {
                val bank2 = rom.copyOfRange(16 * 0x4000 + 0x104, 16 * 0x4000 + 0x134)
                val bank3 = rom.copyOfRange(32 * 0x4000 + 0x104, 32 * 0x4000 + 0x134)
                val bank4 = rom.copyOfRange(48 * 0x4000 + 0x104, 48 * 0x4000 + 0x134)
                val bank5 = rom.copyOfRange(64 * 0x4000 + 0x104, 64 * 0x4000 + 0x134)
                bank2.contentEquals(nintendoLogo) || bank3.contentEquals(nintendoLogo) ||
                    bank4.contentEquals(nintendoLogo) || bank5.contentEquals(nintendoLogo)
            }
            else -> false
        }
        if (isMulticart) {
            Logger.debug("MBC1", "Multicart Cartridge")
        } else {
            Logger.debug("MBC1", "Standard Cartridge")
        }
        isMulticart
    }

    private var romBank = 1
    private var ramBank = 0
    private var ramEnabled = false
    private var bankingMode = 0  // 0=ROM banking, 1=RAM banking
    private val ram = IntArray(0x8000)  // 32KB max
        .also { ram ->
            try {
                SaveManager.load(romName)?.copyInto(ram)
            } catch (e: Exception) {
                Logger.error("Mbc1Cartridge", "Can't load save", e)
            }
        }

    private var saveJob: Job? = null

    override fun readRom(address: Int): Int {
        return when (address) {
            in 0x0000..0x3FFF -> {
                // In RAM banking mode (mode 1), ramBank bits 0-1 become bits 5-6 of the ROM bank number
                // allowing access to banks 0x00, 0x20, 0x40, 0x60 in the lower ROM area
                val bank = if (bankingMode == 1) {
                    if (isMulticart) {
                        ramBank shl 4
                    } else {
                        ramBank shl 5
                    }
                } else {
                    0
                }
                // romBankCount is always a power of 2 (2, 4, 8, 16...), so (romBankCount - 1) is a perfect bit mask.
                // e.g. 64 banks → 64 - 1 = 63 = 0b00111111, masking any bank number to the valid range.
                val maskedBank = bank and (romBankCount - 1)
                rom[maskedBank * 0x4000 + address].toInt() and 0xFF
            }
            in 0x4000..0x7FFF -> {
                // BANK2 bits always contribute as bits 5-6 of the ROM bank number, regardless of banking mode.
                // Combined with BANK1 (bits 0-4), this gives a 7-bit bank number (up to 128 banks).
                // The mask ensures the bank number wraps within the actual ROM size.
                val bank = if (isMulticart) {
                    (romBank and 0x0F) or (ramBank shl 4)
                } else {
                    romBank or (ramBank shl 5)
                }
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
                val raw = value and 0x1F
                romBank = if (raw == 0) {
                    1
                } else {
                    if (isMulticart) value and 0x0F else raw
                }
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
        if (!ramEnabled || ramBankCount == 0) return 0xFF
        val effectiveRamBank = if (bankingMode == 1) ramBank else 0
        val maskedBank = effectiveRamBank and (ramBankCount - 1)
        return ram[maskedBank * 0x2000 + address]
    }

    override fun writeRam(address: Int, value: Int) {
        if (!ramEnabled || ramBankCount == 0) return
        val effectiveRamBank = if (bankingMode == 1) ramBank else 0
        val maskedBank = effectiveRamBank and (ramBankCount - 1)
        ram[maskedBank * 0x2000 + address] = value
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

        private val nintendoLogo = intArrayOf(
            0xCE, 0xED, 0x66, 0x66, 0xCC, 0x0D, 0x00, 0x0B, 0x03, 0x73, 0x00, 0x83, 0x00, 0x0C, 0x00, 0x0D,
            0x00, 0x08, 0x11, 0x1F, 0x88, 0x89, 0x00, 0x0E, 0xDC, 0xCC, 0x6E, 0xE6, 0xDD, 0xDD, 0xD9, 0x99,
            0xBB, 0xBB, 0x67, 0x63, 0x6E, 0x0E, 0xEC, 0xCC, 0xDD, 0xDC, 0x99, 0x9F, 0xBB, 0xB9, 0x33, 0x3E,
        ).map { it.toByte() }.toByteArray()
    }
}
