package com.wechantloup.gameboykmp.apu

import com.wechantloup.gameboykmp.bus.Bus

class Channel4(
    private val bus: Bus,
): SoundChannel {

    /**
     * NR41 — FF20 — Length load
     * Bits 5-0: length load (0-63)
     * Effective length = 64 - length_load.
     *
     * NR42 — FF21 — Envelope
     * Bits 7-4: initial volume (0-15)
     * Bit 3   : direction (0 = decrease, 1 = increase)
     * Bits 2-0: period (0-7)
     *
     * NR43 — FF22 — Polynomial counter
     * Bits 7-4: clock shift (0-15)
     * Bit 3   : width mode (0 = 15 bits, 1 = 7 bits)
     * Bits 2-0: divisor code (0-7)
     *
     * NR44 — FF23 — Control
     * Bit 7: trigger (1 = restart channel)
     * Bit 6: length enable (1 = disable channel when length expires)
     */

    private var enabled = false
        set(value) {
            field = value
            bus.setChannelEnabled(0x08, value)
        }

    private var frequencyTimer = 0  // frequency timer, decremented each cycle
    private var currentVolume = 0   // 0-15, modified by envelope
    private var lengthCounter = 0   // counts down to 0, then disables channel
    private var lfsr = 0            // Linear Feedback Shift Register
    private var envelopeTimer = 0   // envelope timer counter

    override val isEnabled: Boolean
        get() = enabled
    override val dacEnabled: Boolean
        get() = (bus.read(NR42_ADDR) and 0xF8) != 0

    override fun step(cycles: Int) {
        if (!enabled) return

        frequencyTimer -= cycles

        if (frequencyTimer > 0) return

        loadFrequency()

        val bit0 = lfsr and 0x01
        val bit1 = (lfsr and 0x02) shr 1
        val xorBit = bit0 xor bit1
        lfsr = lfsr shr 1
        lfsr = lfsr or (xorBit shl 14)

        val mode7Bits = (bus.read(NR43_ADDR) and 0x08) > 0
        if (mode7Bits) {
            lfsr = lfsr or (xorBit shl 6)
        }
    }

    override fun tickLength() {
        val lengthEnable = bus.read(NR44_ADDR) and 0x40 > 0
        if (!lengthEnable) return
        if (lengthCounter == 0) return

        lengthCounter--

        if (lengthCounter == 0) {
            enabled = false
        }
    }

    override fun getSample(): Int {
        val bit0 = lfsr and 0x01
        return if (bit0 == 0) {
            currentVolume
        } else {
            0
        }
    }

    fun tickEnvelope() {
        if (envelopeTimer == 0) return

        envelopeTimer--

        if (envelopeTimer == 0) {
            val nr42 = bus.read(NR42_ADDR)
            val direction = nr42 and 0x08
            envelopeTimer = nr42 and 0x07

            if (direction > 0 && currentVolume < 15) {
                currentVolume++
            } else if (direction == 0 && currentVolume > 0) {
                currentVolume--
            }
        }
    }

    override fun reset() {
        enabled = false
        frequencyTimer = 0
        currentVolume = 0
        lengthCounter = 0
        lfsr = 0
        envelopeTimer = 0
    }

    override fun loadLengthCounter(value: Int) {
        val lengthLoad = value and 0x3F
        lengthCounter = 64 - lengthLoad
    }

    override fun trigger() {
        if (!dacEnabled) return

        enabled = true
        loadFrequency()

        if (lengthCounter == 0) lengthCounter = 64

        val nr42 = bus.read(NR42_ADDR)
        currentVolume = (nr42 and 0xF0) shr 4
        envelopeTimer = nr42 and 0x07

        lfsr = 0x7FFF
    }

    private fun loadFrequency() {
        val nr43 = bus.read(NR43_ADDR)

        val divisorCode = nr43 and 0x07
        val clockShift = (nr43 and 0xF0) shr 4
        val divisor = when (divisorCode) {
            0 -> 8
            1 -> 16
            2 -> 32
            3 -> 48
            4 -> 64
            5 -> 80
            6 -> 96
            7 -> 112
            else -> 8
        }
        frequencyTimer = divisor shl clockShift
    }

    companion object {
        private const val NR41_ADDR = 0xFF20
        private const val NR42_ADDR = 0xFF21
        private const val NR43_ADDR = 0xFF22
        private const val NR44_ADDR = 0xFF23
    }
}
