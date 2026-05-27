package com.wechantloup.gameboykmp.cartridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock

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

    override fun readRom(address: Int): Int {
        return when (address) {
            in 0x0000..0x3FFF -> {
                rom[address].toInt() and 0xFF
            }
            in 0x4000..0x7FFF -> {
                rom[romBank * 0x4000 + (address - 0x4000)].toInt() and 0xFF
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
            in 0x00..0x03 -> ram[ramBank * 0x2000 + address]
            in 0x08..0x0C -> readRtc()
            else -> 0xFF
        }
    }

    override fun writeRam(address: Int, value: Int) {
        if (!ramEnabled) return
        when (ramBank) {
            in 0x00..0x03 -> ram[ramBank * 0x2000 + address] = value
            in 0x08..0x0C -> writeRtc(value)
            else -> {}
        }
        onRamWritten()
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
        val effectiveNowMs = if (isRtcHalted) cartridgeSave.haltTimeMs else Clock.System.now().toEpochMilliseconds()
        flushElapsedToRaw(effectiveNowMs)
        when (ramBank) {
            0x08 -> {
                cartridgeSave.rawSeconds = value and 0x3F
            }
            0x09 -> {
                cartridgeSave.rawMinutes = value and 0x3F
            }
            0x0A -> {
                cartridgeSave.rawHours = value and 0x1F
            }
            0x0B -> {
                val newDaysLow = value and 0xFF
                cartridgeSave.rawDays = (cartridgeSave.rawDays and 0x100) or newDaysLow
            }
            0x0C -> {
                val newDaysHigh = (value and 0x01) shl 8
                cartridgeSave.rawDays = (cartridgeSave.rawDays and 0xFF) or newDaysHigh

                cartridgeSave.carry = (value and 0x80) != 0
                val halt = (value and 0x40) > 0
                handleHalt(halt, effectiveNowMs)
            }
            else -> {}
        }
    }

    private fun handleHalt(halt: Boolean, nowMs: Long) {
        if (halt == isRtcHalted) return

        if (halt) {
            cartridgeSave.haltTimeMs = nowMs
        } else {
            val durationMs = Clock.System.now().toEpochMilliseconds() - cartridgeSave.haltTimeMs
            cartridgeSave.lastTickMs += durationMs
        }
        cartridgeSave.isRtcHalted = halt
    }

    private fun latch() {
        val gameRtcMs = if (isRtcHalted) cartridgeSave.haltTimeMs else Clock.System.now().toEpochMilliseconds()
        latchedRtc = advanceRtc(gameRtcMs)
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

    private fun flushElapsedToRaw(effectiveNowMs: Long) {
        val rtcSnapshot = advanceRtc(effectiveNowMs)
        cartridgeSave.rawSeconds = rtcSnapshot.seconds
        cartridgeSave.rawMinutes = rtcSnapshot.minutes
        cartridgeSave.rawHours = rtcSnapshot.hours
        cartridgeSave.rawDays = rtcSnapshot.days
        cartridgeSave.carry = rtcSnapshot.carry

        val elapsedSeconds = (effectiveNowMs - cartridgeSave.lastTickMs) / 1000
        cartridgeSave.lastTickMs += elapsedSeconds * 1000
    }

    private fun advanceRtc(effectiveNowMs: Long): RtcSnapshot {
        val elapsedSeconds = (effectiveNowMs - cartridgeSave.lastTickMs) / 1000

        if (elapsedSeconds == 0L) {
            return RtcSnapshot(
                seconds = cartridgeSave.rawSeconds,
                minutes = cartridgeSave.rawMinutes,
                hours = cartridgeSave.rawHours,
                days = cartridgeSave.rawDays,
                carry = cartridgeSave.carry,
                isHalted = cartridgeSave.isRtcHalted,
            )
        }

        val rawS = cartridgeSave.rawSeconds.toLong()
        val minuteCarry: Long
        val seconds: Long
        if (rawS < 60L) {
            val total = rawS + elapsedSeconds
            minuteCarry = total / 60L
            seconds = total % 60L
        } else {
            minuteCarry = 0L
            seconds = (rawS + elapsedSeconds) % 64L
        }

        val rawM = cartridgeSave.rawMinutes.toLong()
        val hourCarry: Long
        val minutes: Long
        if (rawM < 60L) {
            val total = rawM + minuteCarry
            hourCarry = total / 60L
            minutes = total % 60L
        } else {
            hourCarry = 0L
            minutes = (rawM + minuteCarry) % 64L
        }

        val rawH = cartridgeSave.rawHours.toLong()
        val dayCarry: Long
        val hours: Long
        if (rawH < 24L) {
            val total = rawH + hourCarry
            dayCarry = total / 24L
            hours = total % 24L
        } else {
            dayCarry = 0L
            hours = (rawH + hourCarry) % 32L
        }

        var days = cartridgeSave.rawDays.toLong() + dayCarry
        val newCarry = cartridgeSave.carry || days > 511
        days %= 512

        return RtcSnapshot(
            seconds = seconds.toInt(),
            minutes = minutes.toInt(),
            hours = hours.toInt(),
            days = days.toInt(),
            carry = newCarry,
            isHalted = cartridgeSave.isRtcHalted,
        )
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
    }
}
