package com.wechantloup.gameboykmp.cartridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class Mbc3Cartridge(
    private val rom: ByteArray,
    private val romName: String,
    private val scope: CoroutineScope,
    private val withSave: Boolean,
    private val withRtc: Boolean,
) : Cartridge {
    private var saveJob: Job? = null
    private val _isSaving = MutableStateFlow(false)
    override val isSaving: StateFlow<Boolean> = _isSaving

    private val cartridgeSave: Mbc3CartridgeSave = SaveManager.load(romName)
        ?.let { Mbc3CartridgeSave(it) }
        ?: Mbc3CartridgeSave()
    private val ram: IntArray
        get() = cartridgeSave.ram

    private val isRtcHalted: Boolean
        get() = cartridgeSave.isRtcHalted

    private var latchedRtc = RtcSnapshot(
        seconds = 0,
        minutes = 0,
        hours = 0,
        days = 0,
        carry = false,
        isHalted = false
    )

    private var romBank = 1
    private var ramBank = 0
    private var ramEnabled = false
    private var lastWriteRom: Pair<Int, Int> = 0 to 0
    private var rtcCycleAccumulator = 0

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

    /**
     * 0x0000–0x1FFF : RAM enable/disable
     * 0x2000–0x3FFF : ROM bank select (7 bits)
     * 0x4000–0x5FFF : RAM bank / RTC register select (0x00–0x03 ou 0x08–0x0C)
     * 0x6000–0x7FFF : latch (séquence 0x00 → 0x01)
     */
    override fun writeRom(address: Int, value: Int) {
        if (address !in 0x0000..0x7FFF) return

        when (address) {
            in 0x0000..0x1FFF -> {
                ramEnabled = value and 0x0F == 0x0A
            }
            in 0x2000..0x3FFF -> {
                romBank = (value and 0x7F).coerceAtLeast(1)
            }
            in 0x4000..0x5FFF -> {
                ramBank = value and 0x0F
            }
            in 0x6000..0x7FFF -> {
                if (value == 0x01 && lastWriteRom.second == 0x00 && lastWriteRom.first in 0x6000..0x7FFF) {
                    latch()
                }
            }
        }

        lastWriteRom = address to value
    }

    override fun readRam(address: Int): Int {
        if (!ramEnabled) return 0xFF
        return when (ramBank) {
            in 0x00..0x07 -> {
                if (ramBankCount == 0) {
                    0xFF
                } else {
                    val maskedBank = ramBank and (ramBankCount - 1)
                    ram[maskedBank * 0x2000 + address]
                }
            }
            in 0x08..0x0C -> readRtc()
            else -> 0xFF
        }
    }

    override fun writeRam(address: Int, value: Int) {
        if (!ramEnabled) return
        when (ramBank) {
            in 0x00..0x07 -> {
                if (ramBankCount == 0) return
                val maskedBank = ramBank and (ramBankCount - 1)
                ram[maskedBank * 0x2000 + address] = value
            }
            in 0x08..0x0C -> writeRtc(value)
            else -> {}
        }
        onRamWritten()
    }

    override fun stepRtc() {
        if (isRtcHalted) return
        rtcCycleAccumulator += 4  // +4 T-cycles par M-cycle
        // 1 tick RTC = 4 194 304 / 32 768 = 128 T-cycles
        while (rtcCycleAccumulator >= CYCLES_PER_RTC_TICK) {
            rtcCycleAccumulator -= CYCLES_PER_RTC_TICK
            cartridgeSave.totalCycles++  // 1 tick = 1/32768 s
        }
    }

    private fun readRtc(): Int {
        return when (ramBank) {
            0x08 -> latchedRtc.seconds and 0xFF
            0x09 -> latchedRtc.minutes and 0xFF
            0x0A -> latchedRtc.hours and 0xFF
            0x0B -> latchedRtc.days and 0xFF
            0x0C -> {
                val carryInt = if (latchedRtc.carry) 1 shl 7 else 0
                val haltInt = if (latchedRtc.isHalted) 1 shl 6 else 0
                ((latchedRtc.days shr 8) and 0x01) or carryInt or haltInt
            }
            else -> 0xFF
        }
    }

    private fun writeRtc(value: Int) {
        val current = totalCyclesToSnapshot()
        val updated = when (ramBank) {
            0x08 -> current.copy(seconds = value and 0x3F)
            0x09 -> current.copy(minutes = value and 0x3F)
            0x0A -> current.copy(hours = value and 0x1F)
            0x0B -> current.copy(days = (current.days and 0x100) or (value and 0xFF))
            0x0C -> {
                val newDaysHigh = (value and 0x01) shl 8
                cartridgeSave.carry = (value and 0x80) != 0
                val halt = (value and 0x40) != 0
                handleHalt(halt)
                current.copy(days = (current.days and 0xFF) or newDaysHigh)
            }
            else -> return
        }
        cartridgeSave.totalCycles = snapshotToTotalCycles(updated)
    }

    private fun handleHalt(halt: Boolean) {
        if (halt == isRtcHalted) return

        cartridgeSave.isRtcHalted = halt
    }

    private fun latch() {
        latchedRtc = totalCyclesToSnapshot()
    }

    private fun totalCyclesToSnapshot(): RtcSnapshot {
        val totalSeconds = cartridgeSave.totalCycles / RTC_CYCLES_PER_SECOND
        val seconds = (totalSeconds % 60).toInt()
        val minutes = (totalSeconds / 60 % 60).toInt()
        val hours = (totalSeconds / 3600 % 24).toInt()
        val days = (totalSeconds / 86400).toInt()
        val wrappedDays = days % 512
        if (days >= 512) cartridgeSave.carry = true
        cartridgeSave.totalCycles %= 86400L * RTC_CYCLES_PER_SECOND * 512L
        return RtcSnapshot(seconds, minutes, hours, wrappedDays, cartridgeSave.carry, isRtcHalted)
    }

    private fun snapshotToTotalCycles(snapshot: RtcSnapshot): Long {
        val totalSeconds = snapshot.seconds.toLong() +
                snapshot.minutes.toLong() * 60 +
                snapshot.hours.toLong() * 3600 +
                snapshot.days.toLong() * 86400
        return totalSeconds * RTC_CYCLES_PER_SECOND
    }


    private fun onRamWritten() {
        if (!withSave && !withRtc) return

        _isSaving.value = true
        saveJob?.cancel()
        saveJob = scope.launch(Dispatchers.IO) {
            delay(DEBOUNCE_DURATION_MS) // debounce: wait for writes to settle before persisting
            SaveManager.save(romName, cartridgeSave.toIntArray())
            _isSaving.value = false
        }
    }

    data class RtcSnapshot(
        val seconds: Int,
        val minutes: Int,
        val hours: Int,
        val days: Int,
        val carry: Boolean,
        val isHalted: Boolean,
    )

    companion object {
        private const val DEBOUNCE_DURATION_MS = 500L
        private const val CYCLES_PER_RTC_TICK = 128  // 4_194_304 / 32_768
        internal const val RTC_CYCLES_PER_SECOND = 32_768L
    }
}
