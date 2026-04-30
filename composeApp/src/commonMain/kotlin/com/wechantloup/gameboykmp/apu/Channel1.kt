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
    override val dacEnabled: Boolean
        get() = (bus.read(NR12_ADDR) and 0xF8) != 0

    override fun step(cycles: Int) {
        checkInitialization()

        if (!enabled) return

        frequencyTimer -= cycles

        if (frequencyTimer <= 0) {
            dutyStep = (dutyStep + 1) % 8
            frequencyTimer = (2048 - shadowFrequency) * 4
        }
    }

    override fun tickLength() {
        if (lengthCounter == 0) return

        lengthCounter--

        var lengthEnable = false
        if (lengthCounter == 0) {
            lengthEnable = bus.read(NR14_ADDR) and 0x40 > 0
        }
        if (lengthEnable) {
            enabled = false
        }
    }

    fun tickEnvelope() {
        if (envelopeTimer == 0) return

        envelopeTimer--

        if (envelopeTimer == 0) {
            val nr12 = bus.read(NR12_ADDR)
            val direction = nr12 and 0x08
            envelopeTimer = nr12 and 0x07

            if (direction > 0 && currentVolume < 15) {
                currentVolume++
            } else if (direction == 0 && currentVolume > 0) {
                currentVolume--
            }
        }
    }

    fun tickSweep() {
        if (sweepTimer == 0) return

        sweepTimer--

        if (sweepTimer == 0) {
            val nr10 = bus.read(NR10_ADDR)
            sweepTimer = (nr10 and 0x70) shr 4

            if (sweepTimer == 0) return

            val negate = nr10 and 0x08
            val shift = nr10 and 0x07

            val newFreq = if (negate > 0) {
                shadowFrequency - (shadowFrequency shr shift)
            } else {
                shadowFrequency + (shadowFrequency shr shift)
            }

            if (newFreq > 2047) {
                enabled = false
            } else if (shift > 0) {
                shadowFrequency = newFreq
            }
        }
    }

    override fun getSample(): Int {
        val dutyCycle = (bus.read(NR11_ADDR) and 0xC0) shr 6
        val dutyPattern = when (dutyCycle) {
            0 -> 0b00000001  // 12.5%
            1 -> 0b10000001  // 25%
            2 -> 0b10000111  // 50%
            3 -> 0b11111110  // 75%
            else -> throw IllegalStateException()
        }
        val on = (dutyPattern shr dutyStep) and 0x01 > 0
        return if (on) currentVolume else 0
    }

    private fun checkInitialization() {
        val nr14 = bus.read(NR14_ADDR)
        if (nr14 and 0x80 != 0) {
            if (dacEnabled) trigger()
            // Remettre le bit 7 à 0 pour ne pas re-déclencher au prochain step
            bus.write(NR14_ADDR, nr14 and 0x7F)
        }
    }

    private fun trigger() {
        println("trigger channel 1")
        enabled = true

        loadFrequency()

        val lengthLoad = bus.read(NR11_ADDR) and 0x3F
        lengthCounter = 64 - lengthLoad

        val nr12 = bus.read(NR12_ADDR)
        currentVolume = (nr12 and 0xF0) shr 4
        envelopeTimer = nr12 and 0x07

        sweepTimer = (bus.read(NR10_ADDR) and 0x70) shr 4
        println("currentVolume = $currentVolume")
    }

    private fun loadFrequency() {
        val frequencyHigh = bus.read(NR14_ADDR) and 0x07
        val frequencyLow = bus.read(NR13_ADDR) and 0xFF
        val frequency = frequencyHigh shl 8 or frequencyLow
        frequencyTimer = (2048 - frequency) * 4
        shadowFrequency = frequency
    }

    // TODO: add reset() method to clean all internal state on ROM change

    companion object {
        private const val NR10_ADDR = 0xFF10
        private const val NR11_ADDR = 0xFF11
        private const val NR12_ADDR = 0xFF12
        private const val NR13_ADDR = 0xFF13
        private const val NR14_ADDR = 0xFF14
    }
}
