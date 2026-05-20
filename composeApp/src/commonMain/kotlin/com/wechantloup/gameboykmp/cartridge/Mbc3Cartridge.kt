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
import kotlin.time.Instant

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
    private val carry: Boolean
        get() = cartridgeSave.carry
    private val rtcOffset: Long
        get() = cartridgeSave.rtcOffset

    // TODO haltRtc and haltRtcTime should be saved with RAM
    private var haltRtc = false
    private var haltRtcTime: Instant = Instant.DISTANT_PAST

    private var latchedSeconds = 0
    private var latchedMinutes = 0
    private var latchedHours = 0
    private var latchedDays = 0
    private var latchedHaltRtc = false
    private var latchedCarry = false

    private var ramBank = 0
    private var ramEnabled = false

    override fun readRom(address: Int): Int {
        TODO("Not yet implemented")
    }

    override fun writeRom(address: Int, value: Int) {
        TODO("Not yet implemented")
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
            0x08 -> latchedSeconds and 0xFF
            0x09 -> latchedMinutes and 0xFF
            0x0A -> latchedHours and 0xFF
            0x0B -> latchedDays and 0xFF
            0x0C -> {
                val carryInt = if (latchedCarry) 1 shl 7 else 0
                val haltInt = if (latchedHaltRtc) 1 shl 6 else 0
                ((latchedDays shr 8) and 0x01) or carryInt or haltInt
            }
            else -> 0xFF
        }
    }

    private fun writeRtc(value: Int) {
        val currentTime = if (haltRtc) haltRtcTime else Clock.System.now()
        val currentSeconds = currentTime.epochSeconds
        val gameRtcSeconds = currentSeconds - rtcOffset
        when (ramBank) {
            0x08 -> {
                val newSeconds = value and 0xFF
                val newGameRtcSeconds = (gameRtcSeconds / 60) * 60 + newSeconds
                cartridgeSave.rtcOffset = currentSeconds - newGameRtcSeconds
            }
            0x09 -> {
                val newMinutes = value and 0xFF
                val seconds = gameRtcSeconds % 60
                val newGameRtcSeconds = ((gameRtcSeconds / 60 / 60) * 60 + newMinutes) * 60 + seconds
                cartridgeSave.rtcOffset = currentSeconds - newGameRtcSeconds
            }
            0x0A -> {
                val newHours = value and 0xFF
                val secondsMinutes = gameRtcSeconds % 3600
                val newGameRtcSeconds = ((gameRtcSeconds / 24 / 60 / 60) * 24 + newHours) * 3600 + secondsMinutes
                cartridgeSave.rtcOffset = currentSeconds - newGameRtcSeconds
            }
            0x0B -> {
                val newDaysLow = value and 0xFF
                val secondsMinutesHours = gameRtcSeconds % (3600 * 24)
                val days = gameRtcSeconds / 24 / 60 / 60
                val newDays = (days.toInt() and 0x100) or newDaysLow
                val newGameRtcSeconds = newDays * 24 * 3600 + secondsMinutesHours
                cartridgeSave.rtcOffset = currentSeconds - newGameRtcSeconds
            }
            0x0C -> {
                val newDaysHigh = (value and 0x01) shl 8
                val secondsMinutesHours = gameRtcSeconds % (3600 * 24)
                val days = gameRtcSeconds / 24 / 60 / 60
                val newDays = (days.toInt() and 0xFF) or newDaysHigh
                val newGameRtcSeconds = newDays * 24 * 3600 + secondsMinutesHours
                cartridgeSave.rtcOffset = currentSeconds - newGameRtcSeconds

                cartridgeSave.carry = (value and 0x80) != 0

                val halt = (value and 0x40) > 0
                handleHalt(halt)
            }
            else -> {}
        }
    }

    private fun handleHalt(halt: Boolean) {
        if (halt == haltRtc) return

        if (halt) {
            haltRtcTime = Clock.System.now()
        } else {
            val now = Clock.System.now()
            val duration = (now - haltRtcTime).inWholeSeconds
            cartridgeSave.rtcOffset += duration
        }
        haltRtc = halt
    }

    private fun latch() {
        val currentTime = if (haltRtc) haltRtcTime else Clock.System.now()
        val gameRtcSeconds = currentTime.epochSeconds - rtcOffset
        latchedSeconds = (gameRtcSeconds % 60).toInt() and 0xFF
        latchedMinutes = ((gameRtcSeconds / 60) % 60).toInt() and 0xFF
        latchedHours = ((gameRtcSeconds / 60 / 60) % 24).toInt() and 0xFF
        val days = (gameRtcSeconds / 60 / 60 / 24).toInt()
        if (days > 511) {
            cartridgeSave.carry = true
        }
        latchedDays = days and 0x1FF
        latchedCarry = carry
        latchedHaltRtc = haltRtc
        onRamWritten()
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

    companion object {
        private const val DEBOUNCE_DURATION_MS = 500L
    }
}
