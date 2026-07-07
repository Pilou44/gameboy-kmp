package com.wechantloup.gameboykmp.timer

import com.wechantloup.gameboykmp.bus.Bus

class Timer(private val bus: Bus) {

    // The 16-bit system counter now lives in the Bus (single source of truth, shared with the
    // APU frame sequencer and projected as DIV $FF04). The Timer only reads it: TIMA is clocked
    // by falling edges of the bit selected by TAC (bits 3/5/7/9). Each T-cycle the loop calls
    // bus.tick() (advance) then timer.tick() (edge detect on the just-advanced counter).

    private var timaOverflowPending = false
    private var timaOverflowCycles = 0
    private var timaIrqRaised = false

    init {
        bus.onDivReset = {
            // Before the Bus resets the counter, check if the bit that clocks TIMA is currently 1.
            // If yes, forcing it to 0 is a falling edge → TIMA must increment now.
            // (The Bus performs `sysCounter = 0` right after this callback returns.)
            if (timerActivated && isDivBitSet()) {
                incrementTima(overflowCycles = 4)
            }
        }
        bus.onTacWrite = { oldTac, newTac ->
            val wasEnabled = oldTac and 0x04 != 0
            val isEnabled = newTac and 0x04 != 0
            val oldFrequency = oldTac and 0x03
            val newFrequency = newTac and 0x03
            // Falling edge if: timer was on, old bit was 1, and either timer turned off OR frequency changed
            val frequencyChanged = wasEnabled && isEnabled && oldFrequency != newFrequency
            val timerDisabled = wasEnabled && !isEnabled
            if ((frequencyChanged || timerDisabled) && (bus.sysCounter and divBitMask(oldFrequency)) != 0) {
                incrementTima(overflowCycles = 4)
            }
        }
        bus.canWriteOnTima = {
            // Allow write only if not in the reload M-cycle.
            // timaOverflowCycles <= 4 means the reload fires within this M-cycle;
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
            // During the reload M-cycle, reading TIMA returns TMA (hardware ties them together).
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

    // Advances the timer by one T-cycle. The loop calls bus.tick() first (prevSysCounter →
    // sysCounter, then sysCounter++), so edge detection compares the counter across that
    // single increment.
    fun tick() {
        val oldCount = bus.prevSysCounter
        val newCount = bus.sysCounter

        // Advance a pending TIMA overflow, one T-cycle at a time.
        if (timaOverflowPending) {
            timaOverflowCycles -= 1
            // Raise the timer interrupt one M-cycle (4 T) after the overflow — the same instant
            // TIMA observably becomes TMA via timaReadOverride. The raw reload below lands one
            // M-cycle later but is masked by that override, so TIMA reads are unchanged; only the
            // interrupt timing is corrected.
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

        // Detect a falling edge on the TIMA clock bit: was 1, now 0.
        if (timerActivated) {
            val mask = divBitMask()
            val wasSet = (oldCount and mask) != 0
            val isSet = (newCount and mask) != 0
            if (wasSet && !isSet) {
                incrementTima()
            }
        }
    }

    // Which bit of the system counter clocks TIMA, based on TAC frequency bits.
    private fun divBitMask(frequency: Int = timerFrequency): Int = when (frequency) {
        0 -> 1 shl 9   // 4096 Hz   — period 1024 cycles, toggles at bit 9
        1 -> 1 shl 3   // 262144 Hz — period 16 cycles,   toggles at bit 3
        2 -> 1 shl 5   // 65536 Hz  — period 64 cycles,   toggles at bit 5
        3 -> 1 shl 7   // 16384 Hz  — period 256 cycles,  toggles at bit 7
        else -> throw IllegalStateException("Invalid timer frequency: $frequency")
    }

    private fun isDivBitSet(): Boolean = (bus.sysCounter and divBitMask()) != 0

    private fun incrementTima(overflowCycles: Int = 8) {
        val next = bus.readRaw(TIMA_ADDR) + 1
        if (next > 0xFF) {
            bus.writeRaw(TIMA_ADDR, 0x00)
            timaOverflowPending = true
            // TODO: the div-reset / TAC path arms with overflowCycles = 4. Ticked per-T, the
            //  `<= 4` IRQ threshold now fires within the same T as the arming (sub-M-cycle),
            //  whereas the batched step(4) fired it at the next M-cycle boundary. Free-running
            //  edges stay M-cycle-aligned (taps are multiples of 4), so this is the only
            //  non-neutral vector. Re-verify against div_write / rapid_toggle; ROM wins if they
            //  diverge from a unit test.
            timaOverflowCycles = overflowCycles
        } else {
            bus.writeRaw(TIMA_ADDR, next and 0xFF)
        }
    }

    companion object {
        private const val TIMA_ADDR = 0xFF05
        private const val TMA_ADDR = 0xFF06
        private const val TAC_ADDR = 0xFF07
    }
}
