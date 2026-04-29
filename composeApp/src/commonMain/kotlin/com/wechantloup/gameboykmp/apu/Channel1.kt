package com.wechantloup.gameboykmp.apu

import com.wechantloup.gameboykmp.bus.Bus

class Channel1(
    private val bus: Bus,
): SoundChannel {

    /**
     * NR10 — FF10 — Sweep
     * Single byte, 3 fields:
     * Bits 6-4: sweep period (0-7)
     * Bit 3   : negate (0 = frequency increases, 1 = frequency decreases)
     * Bits 2-0: sweep shift (0-7)
     * New frequency formula: new_freq = current_freq ± (current_freq >> shift)
     * If shift = 0, frequency does not change but sweep still runs.
     * If period = 0, sweep is disabled.
     *
     * NR11 — FF11 — Duty & Length
     * Bits 7-6: duty cycle (0-3 → 12.5%, 25%, 50%, 75%)
     * Bits 5-0: length load (0-63)
     * Effective length = 64 - length_load.
     *
     * NR12 — FF12 — Envelope
     * Bits 7-4: initial volume (0-15)
     * Bit 3   : direction (0 = decrease, 1 = increase)
     * Bits 2-0: period (0-7)
     * If period = 0, envelope is disabled and volume stays fixed.
     *
     * NR13 — FF13 — Frequency low
     * Bits 7-0: low 8 bits of frequency
     *
     * NR14 — FF14 — Frequency high + control
     * Bit 7   : trigger (1 = restart channel)
     * Bit 6   : length enable (1 = disable channel when length expires)
     * Bits 2-0: high 3 bits of frequency
     * Frequency is 11 bits total (NR13 + bits 2-0 of NR14).
     */

    private var enabled = false
    private var frequencyTimer = 0  // frequency timer, decremented each cycle
    private var dutyStep = 0        // 0-7, current position in duty cycle wave
    private var currentVolume = 0   // 0-15, modified by envelope
    private var sweepTimer = 0      // sweep timer counter
    private var shadowFrequency = 0 // frequency copy, modified by sweep
    private var lengthCounter = 0   // counts down to 0, then disables channel
    private var envelopeTimer = 0   // envelope timer counter

    override val isEnabled: Boolean
        get() = enabled

    override fun step(cycles: Int) {
        TODO("Not yet implemented")
    }

    override fun tickLength() {
        TODO("Not yet implemented")
    }

    override fun getSample(): Int {
        val dutyCycle = (bus.read(NR11_ADDR) and 0xC0) shr 6
        return 0 // ToDO
    }

    companion object {
        private const val NR10_ADDR = 0xFF10
        private const val NR11_ADDR = 0xFF11
        private const val NR12_ADDR = 0xFF12
        private const val NR13_ADDR = 0xFF13
        private const val NR14_ADDR = 0xFF14
    }
}
