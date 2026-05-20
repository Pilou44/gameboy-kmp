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
    private val carry: Boolean
        get() = cartridgeSave.carry
    private val rtcOffset: Long
        get() = cartridgeSave.rtcOffset

    private var haltRtc = false

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
        TODO("Not yet implemented")
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

    private fun latch() {
        val currentTime = Clock.System.now()
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
        if (!withSave) return

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
