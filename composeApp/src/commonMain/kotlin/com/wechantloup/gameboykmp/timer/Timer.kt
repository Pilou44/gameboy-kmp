package com.wechantloup.gameboykmp.timer

import com.wechantloup.gameboykmp.bus.Bus

class Timer(private val bus: Bus) {

    private var cycleCount = 0

    private var timaOverflowPending = false
    private var timaOverflowCycles = 0

    init {
        bus.onDivReset = {
            // Before resetting, check if the bit that clocks TIMA is currently 1.
            // If yes, forcing it to 0 is a falling edge → TIMA must increment now.
            if (timerActivated && isDivBitSet()) {
                incrementTima()
            }
            cycleCount = 0
        }
        bus.canWriteOnTima = {
            !timaOverflowPending
        }
        bus.onTacWrite = { oldTac, newTac ->
            val wasEnabled = oldTac and 0x04 != 0
            val isEnabled = newTac and 0x04 != 0
            val oldFrequency = oldTac and 0x03
            val newFrequency = newTac and 0x03
            // Falling edge if: timer was on, old bit was 1, and either timer turned off OR frequency changed
            val frequencyChanged = wasEnabled && isEnabled && oldFrequency != newFrequency
            val timerDisabled = wasEnabled && !isEnabled
            if ((frequencyChanged || timerDisabled) && (cycleCount and divBitMask(oldFrequency)) != 0) {
                incrementTima()
            }
        }
    }

    private var tima: Int
        get() = bus.read(TIMA_ADDR)
        set(value) = bus.write(TIMA_ADDR, value)

    private val tma: Int
        get() = bus.read(TMA_ADDR)

    private val timerActivated: Boolean
        get() = bus.read(TAC_ADDR) and 0x04 > 0

    private val timerFrequency: Int
        get() = bus.read(TAC_ADDR) and 0x03

    fun step(cycles: Int) {
        val oldCount = cycleCount
        cycleCount += cycles

        // Handle pending TIMA overflow (reload TMA + fire interrupt, delayed by 4 cycles)
        if (timaOverflowPending) {
            timaOverflowCycles -= cycles
            if (timaOverflowCycles <= 0) {
                timaOverflowPending = false
                tima = tma
                bus.setIF(bus.iF or 0x04)
            }
        }

        // Increment DIV register (visible byte = bits 8-15 of cycleCount)
        // DIV increments every 256 T-cycles, i.e. when bit 8 toggles
        val oldDiv = oldCount shr 8
        val newDiv = cycleCount shr 8
        if (newDiv != oldDiv) {
            bus.incDiv()
        }

        // Detect falling edges on the TIMA clock bit
        // A falling edge = the bit was 1 before, and is 0 now
        if (timerActivated) {
            val mask = divBitMask()
            val wasSet = (oldCount and mask) != 0
            val isSet = (cycleCount and mask) != 0
            if (wasSet && !isSet) {
                incrementTima()
            }
        }
    }

    // Returns which bit of cycleCount clocks TIMA, based on TAC frequency bits
    private fun divBitMask(frequency: Int = timerFrequency): Int = when (frequency) {
        0 -> 1 shl 9   // 4096 Hz   — period 1024 cycles, toggles at bit 9
        1 -> 1 shl 3   // 262144 Hz — period 16 cycles,   toggles at bit 3
        2 -> 1 shl 5   // 65536 Hz  — period 64 cycles,   toggles at bit 5
        3 -> 1 shl 7   // 16384 Hz  — period 256 cycles,  toggles at bit 7
        else -> throw IllegalStateException("Invalid timer frequency: $frequency")
    }

    private fun isDivBitSet(): Boolean = (cycleCount and divBitMask()) != 0

    private fun incrementTima() {
        val next = tima + 1
        if (next > 0xFF) {
            tima = 0x00
            timaOverflowPending = true
            timaOverflowCycles = 4
        } else {
            tima = next and 0xFF
        }
    }

    companion object {
        private const val TIMA_ADDR = 0xFF05
        private const val TMA_ADDR = 0xFF06
        private const val TAC_ADDR = 0xFF07
    }
}
