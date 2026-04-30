package com.wechantloup.gameboykmp.apu

import com.wechantloup.gameboykmp.bus.Bus

class Channel2(
    private val bus: Bus,
): SoundChannel {

    /**
     * NR21 — FF16 — Duty & Length
     * Bits 7-6: duty cycle (0-3 → 12.5%, 25%, 50%, 75%)
     * Bits 5-0: length load (0-63)
     * Effective length = 64 - length_load.
     *
     * NR22 — FF17 — Envelope
     * Bits 7-4: initial volume (0-15)
     * Bit 3   : direction (0 = decrease, 1 = increase)
     * Bits 2-0: period (0-7)
     * If period = 0, envelope is disabled and volume stays fixed.
     *
     * NR23 — FF18 — Frequency low
     * Bits 7-0: low 8 bits of frequency
     *
     * NR24 — FF19 — Frequency high + control
     * Bit 7   : trigger (1 = restart channel)
     * Bit 6   : length enable (1 = disable channel when length expires)
     * Bits 2-0: high 3 bits of frequency
     * Frequency is 11 bits total (NR23 + bits 2-0 of NR24).
     */

    private var enabled = false
        set(value) {
            field = value
            bus.setChannelEnabled(0x02, value)
        }
    private var frequencyTimer = 0  // frequency timer, decremented each cycle
    private var dutyStep = 0        // 0-7, current position in duty cycle wave
    private var currentVolume = 0   // 0-15, modified by envelope
    private var frequency = 0
    private var lengthCounter = 0   // counts down to 0, then disables channel
    private var envelopeTimer = 0   // envelope timer counter

    override val isEnabled: Boolean
        get() = enabled
    override val dacEnabled: Boolean
        get() = (bus.read(NR22_ADDR) and 0xF8) != 0

    override fun step(cycles: Int) {
        checkInitialization()

        if (!enabled) return

        frequencyTimer -= cycles

        if (frequencyTimer <= 0) {
            dutyStep = (dutyStep + 1) % 8
            frequencyTimer = (2048 - frequency) * 4
        }
    }

    override fun tickLength() {
        val lengthEnable = bus.read(NR24_ADDR) and 0x40 > 0
        if (!lengthEnable) return
        if (lengthCounter == 0) return

        lengthCounter--

        if (lengthEnable) {
            enabled = false
        }
    }

    fun tickEnvelope() {
        if (envelopeTimer == 0) return

        envelopeTimer--

        if (envelopeTimer == 0) {
            val nr22 = bus.read(NR22_ADDR)
            val direction = nr22 and 0x08
            envelopeTimer = nr22 and 0x07

            if (direction > 0 && currentVolume < 15) {
                currentVolume++
            } else if (direction == 0 && currentVolume > 0) {
                currentVolume--
            }
        }
    }

    override fun getSample(): Int {
        val dutyCycle = (bus.read(NR21_ADDR) and 0xC0) shr 6
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
        val nr24 = bus.read(NR24_ADDR)
        if (nr24 and 0x80 != 0) {
            if (dacEnabled) trigger()
            // Remettre le bit 7 à 0 pour ne pas re-déclencher au prochain step
            bus.write(NR24_ADDR, nr24 and 0x7F)
        }
    }

    private fun trigger() {
//        println("trigger channel 2")
        enabled = true

        loadFrequency()

//        val lengthLoad = bus.read(NR21_ADDR) and 0x3F
//        lengthCounter = 64 - lengthLoad
        if (lengthCounter == 0) {
            lengthCounter = 64  // valeur max, pas depuis NR11
        }

        val nr22 = bus.read(NR22_ADDR)
        currentVolume = (nr22 and 0xF0) shr 4
        envelopeTimer = nr22 and 0x07
//        println("currentVolume = $currentVolume")
    }

    private fun loadFrequency() {
        val frequencyHigh = bus.read(NR24_ADDR) and 0x07
        val frequencyLow = bus.read(NR23_ADDR) and 0xFF
        val newFrequency = frequencyHigh shl 8 or frequencyLow
        frequencyTimer = (2048 - newFrequency) * 4
        frequency = newFrequency
    }

    // TODO: add reset() method to clean all internal state on ROM change

    companion object {
        private const val NR21_ADDR = 0xFF16
        private const val NR22_ADDR = 0xFF17
        private const val NR23_ADDR = 0xFF18
        private const val NR24_ADDR = 0xFF19
    }
}
