package com.wechantloup.gameboykmp.apu

import com.wechantloup.gameboykmp.bus.Bus

class Channel3(
    private val bus: Bus,
): SoundChannel {

    /**
     * NR30 — FF1A — DAC enable
     * Bit 7: DAC power (0 = off, 1 = on)
     *
     * NR31 — FF1B — Length load
     * Bits 7-0: length load (0-255)
     * Effective length = 256 - length_load.
     *
     * NR32 — FF1C — Volume
     * Bits 6-5: volume (0 = mute, 1 = 100%, 2 = 50%, 3 = 25%)
     *
     * NR33 — FF1D — Frequency low
     * Bits 7-0: low 8 bits of frequency
     *
     * NR34 — FF1E — Frequency high + control
     * Bit 7  : trigger (1 = restart channel)
     * Bit 6  : length enable (1 = disable channel when length expires)
     * Bits 2-0: high 3 bits of frequency
     * Frequency is 11 bits total (NR33 + bits 2-0 of NR34).
     *
     * Wave RAM — FF30–FF3F
     * 16 bytes, each containing 2 nibbles (4 bits each) = 32 samples total.
     * High nibble of each byte is played first.
     */

    private var enabled = false
    private var frequencyTimer = 0  // frequency timer, decremented each cycle
    private var frequency = 0
    private var currentVolume = 0   // 0-3, modified by envelope
    private var lengthCounter = 0   // counts down to 0, then disables channel
    private var wavePosition = 0        // 0-31, current position in duty cycle wave

    private val dacEnabled: Boolean
        get() = (bus.read(NR30_ADDR) and 0x80 != 0)
    override val isEnabled: Boolean
        get() = enabled && dacEnabled

    override fun step(cycles: Int) {
        checkInitialization()

        if (!enabled) return

        frequencyTimer -= cycles

        if (frequencyTimer <= 0) {
            wavePosition = (wavePosition + 1) % 32
            frequencyTimer = (2048 - frequency) * 2
        }
    }

    override fun tickLength() {
        if (lengthCounter == 0) return

        lengthCounter--

        var lengthEnable = false
        if (lengthCounter == 0) {
            lengthEnable = bus.read(NR34_ADDR) and 0x40 > 0
        }
        if (lengthEnable) {
            enabled = false
        }
    }

    override fun getSample(): Int {
        if (currentVolume == 0) return 0

        val byteIndex = wavePosition / 2        // 0-15
        val address = WAVE_ADDR + byteIndex
        val byte = bus.read(address)
        val nibble = if (wavePosition % 2 == 0) {
            (byte and 0xF0) shr 4  // high nibble
        } else {
            byte and 0x0F          // low nibble
        }

        return when (currentVolume) {
            1 -> nibble
            2 -> nibble shr 1
            3 -> nibble shr 2
            else -> throw IllegalStateException("Bad volume value")
        }
    }

    private fun checkInitialization() {
        val nr34 = bus.read(NR34_ADDR)
        if (nr34 and 0x80 != 0) {
            trigger()
            // Remettre le bit 7 à 0 pour ne pas re-déclencher au prochain step
            bus.write(NR34_ADDR, nr34 and 0x7F)
        }
    }

    private fun trigger() {
        enabled = true

        loadFrequency()

        val lengthLoad = bus.read(NR31_ADDR) and 0xFF
        lengthCounter = 256 - lengthLoad

        val nr12 = bus.read(NR32_ADDR)
        currentVolume = (nr12 and 0x60) shr 5
    }

    private fun loadFrequency() {
        val frequencyHigh = bus.read(NR34_ADDR) and 0x07
        val frequencyLow = bus.read(NR33_ADDR) and 0xFF
        val newFrequency = frequencyHigh shl 8 or frequencyLow
        frequencyTimer = (2048 - newFrequency) * 2
        frequency = newFrequency
    }

    // TODO: add reset() method to clean all internal state on ROM change

    companion object {
        private const val NR30_ADDR = 0xFF1A
        private const val NR31_ADDR = 0xFF1B
        private const val NR32_ADDR = 0xFF1C
        private const val NR33_ADDR = 0xFF1D
        private const val NR34_ADDR = 0xFF1E
        private const val WAVE_ADDR = 0xFF30
    }
}
