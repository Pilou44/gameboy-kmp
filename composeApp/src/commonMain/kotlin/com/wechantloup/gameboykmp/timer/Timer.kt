package com.wechantloup.gameboykmp.timer

import com.wechantloup.gameboykmp.bus.Bus

class Timer(private val bus: Bus) {

    // Internal 16-bit DIV counter left by the DMG boot ROM at hand-off ($0100).
    // The visible DIV register ($FF04) is the high byte; the low byte sets the
    // sub-DIV phase, which boot_div verifies. The canonical DMG value is 0xABCC.
    private var cycleCount = if (bus.bootRom == null) {
        POST_BOOT_DIV_COUNTER
    } else {
        0
    }

    private var timaOverflowPending = false
    private var timaOverflowCycles = 0
    private var timaIrqRaised = false

    init {
        bus.onDivReset = {
            // Before resetting, check if the bit that clocks TIMA is currently 1.
            // If yes, forcing it to 0 is a falling edge → TIMA must increment now.
            if (timerActivated && isDivBitSet()) {
                incrementTima(overflowCycles = 4)
            }
            cycleCount = 0
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
                incrementTima(overflowCycles = 4)
            }
        }
        bus.canWriteOnTima = {
            // Allow write only if not in the reload M-cycle.
            // timaOverflowCycles == 4 means the reload fires at the end of this very M-cycle;
            // the CPU write must be ignored in that case (hardware behaviour).
            !(timaOverflowPending && timaOverflowCycles <= 4)
        }
        bus.onTimaWrite = {
            // Only called when canWriteOnTima() returned true, i.e. we are in the cancel window.
            if (timaOverflowPending) {
                timaOverflowPending = false
                timaIrqRaised = false
            }
        }
        bus.timaReadOverride = {
            // Pendant le reload M-cycle : lire TIMA retourne TMA (hardware connecte les deux)
            if (timaOverflowPending && timaOverflowCycles <= 4) tma else null
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

        // Handle pending TIMA overflow
        if (timaOverflowPending) {
            timaOverflowCycles -= cycles
            // Raise the timer interrupt one M-cycle after the overflow (the 4 T-cycle
            // hardware delay) — the same instant TIMA observably becomes TMA via
            // timaReadOverride. The raw reload below lands one M-cycle later but is
            // masked by that override, so TIMA reads are unchanged; only the interrupt
            // timing is corrected (it used to fire with the raw reload, one M-cycle late).
            if (timaOverflowCycles <= 4 && !timaIrqRaised) {
                timaIrqRaised = true
                bus.setIF(bus.iF or 0x04)
            }
            if (timaOverflowCycles <= 0) {
                timaOverflowPending = false
                timaIrqRaised = false
                tima = tma
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

    private fun incrementTima(overflowCycles: Int = 8) {
        val next = bus.readRaw(TIMA_ADDR) + 1
        if (next > 0xFF) {
            bus.writeRaw(TIMA_ADDR, 0x00)
            timaOverflowPending = true
            timaOverflowCycles = overflowCycles
        } else {
            bus.writeRaw(TIMA_ADDR, next and 0xFF)
        }
    }

    companion object {
        private const val POST_BOOT_DIV_COUNTER = 0xABCC
        private const val TIMA_ADDR = 0xFF05
        private const val TMA_ADDR = 0xFF06
        private const val TAC_ADDR = 0xFF07
    }
}
