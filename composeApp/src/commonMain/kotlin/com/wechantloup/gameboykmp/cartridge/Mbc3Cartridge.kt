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

    private var latchedRtc = RtcSnapshot(
        seconds = 0, minutes = 0, hours = 0, days = 0,
        carry = false, isHalted = false,
    )

    private var romBank = 1
    private var ramBank = 0
    private var ramEnabled = false
    private var lastWriteRom: Pair<Int, Int> = 0 to 0

    // Two-level RTC tick chain:
    //   rtcCycleAccumulator : T-cycles within the current RTC tick    (0..127)
    //   rtcTickAccumulator  : RTC ticks within the current second      (0..32767)
    private var rtcCycleAccumulator = 0
    private var rtcTickAccumulator = 0

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
            0x02 -> 1 // 8 KB
            0x03 -> 4 // 32 KB
            0x04 -> 16 // 128 KB
            0x05 -> 8  // 64 KB
            else -> throw IllegalStateException("Unknown RAM size byte at 0x0149")
        }
    }

    override fun readRom(address: Int): Int {
        return when (address) {
            in 0x0000..0x3FFF -> rom[address].toInt() and 0xFF
            in 0x4000..0x7FFF -> {
                val maskedBank = romBank and (romBankCount - 1)
                rom[maskedBank * 0x4000 + (address - 0x4000)].toInt() and 0xFF
            }
            else -> throw IllegalArgumentException("Bad address")
        }
    }

    /**
     * 0x0000–0x1FFF : RAM enable/disable
     * 0x2000–0x3FFF : ROM bank select (7 bits, min 1)
     * 0x4000–0x5FFF : RAM bank / RTC register select (0x00–0x03 or 0x08–0x0C)
     * 0x6000–0x7FFF : latch clock data (sequence 0x00 → 0x01)
     */
    override fun writeRom(address: Int, value: Int) {
        if (address !in 0x0000..0x7FFF) return
        when (address) {
            in 0x0000..0x1FFF -> ramEnabled = value and 0x0F == 0x0A
            in 0x2000..0x3FFF -> romBank = (value and 0x7F).coerceAtLeast(1)
            in 0x4000..0x5FFF -> ramBank = value and 0x0F
            in 0x6000..0x7FFF -> {
                if (value == 0x01
                    && lastWriteRom.second == 0x00
                    && lastWriteRom.first in 0x6000..0x7FFF
                ) {
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
                if (ramBankCount == 0) 0xFF
                else {
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
        if (cartridgeSave.isHalted) return
        rtcCycleAccumulator += 4
        while (rtcCycleAccumulator >= CYCLES_PER_RTC_TICK) {
            rtcCycleAccumulator -= CYCLES_PER_RTC_TICK
            rtcTickAccumulator++
            if (rtcTickAccumulator >= RTC_TICKS_PER_SECOND) {
                rtcTickAccumulator = 0
                cartridgeSave.tickOnce()
            }
        }
    }

    private fun latch() {
        latchedRtc = RtcSnapshot(
            seconds  = cartridgeSave.seconds,
            minutes  = cartridgeSave.minutes,
            hours    = cartridgeSave.hours,
            days     = ((cartridgeSave.ctrl and 0x01) shl 8) or cartridgeSave.daysLow,
            carry    = (cartridgeSave.ctrl and 0x80) != 0,
            isHalted = cartridgeSave.isHalted,
        )
    }

    private fun readRtc(): Int {
        return when (ramBank) {
            0x08 -> latchedRtc.seconds and 0x3F   // valid bits 0-5 only
            0x09 -> latchedRtc.minutes and 0x3F   // valid bits 0-5 only
            0x0A -> latchedRtc.hours   and 0x1F   // valid bits 0-4 only
            0x0B -> latchedRtc.days    and 0xFF
            0x0C -> {
                val carryBit = if (latchedRtc.carry)    0x80 else 0
                val haltBit  = if (latchedRtc.isHalted) 0x40 else 0
                ((latchedRtc.days shr 8) and 0x01) or carryBit or haltBit
            }
            else -> 0xFF
        }
    }

    private fun writeRtc(value: Int) {
        // Raw value is stored without masking: upper bits are hardware-preserved.
        // rtcTickAccumulator is intentionally NOT reset here: sub-second position
        // is preserved across register writes (e.g. setting seconds=0 at 500ms
        // into a second keeps the next tick 500ms away, not 1000ms).
        when (ramBank) {
            0x08 -> cartridgeSave.seconds = value and 0xFF
            0x09 -> cartridgeSave.minutes = value and 0xFF
            0x0A -> cartridgeSave.hours   = value and 0xFF
            0x0B -> cartridgeSave.daysLow = value and 0xFF
            0x0C -> cartridgeSave.ctrl    = value and 0xFF
            else -> return
        }
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
        private const val CYCLES_PER_RTC_TICK = 128   // 4_194_304 / 32_768 T-cycles per RTC tick
        private const val RTC_TICKS_PER_SECOND = 32_768
    }
}
