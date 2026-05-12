package com.wechantloup.gameboykmp.apu

import com.wechantloup.gameboykmp.bus.Bus

class Channel3(
    private val bus: Bus,
    private val getFrameSequencerStep: () -> Int,
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
        set(value) {
            field = value
            bus.setChannelEnabled(0x04, value)
        }
    private var frequencyTimer = 0  // frequency timer, decremented each cycle
    private var frequency = 0
    private var currentVolume = 0   // 0-3, modified by envelope
    private var lengthCounter = 0   // counts down to 0, then disables channel
    private var wavePosition = 0    // 0-31, current position in wave table
    private var lengthEnabled = false  // tracks current lengthEnable state

    override val dacEnabled: Boolean
        get() = (bus.read(NR30_ADDR) and 0x80 != 0)
    override val isEnabled: Boolean
        get() = enabled

    override fun step(cycles: Int) {
        if (!enabled) return

        frequencyTimer -= cycles

        while (frequencyTimer <= 0) {
            wavePosition = (wavePosition + 1) % 32

            val frequencyHigh = bus.readRaw(NR34_ADDR) and 0x07
            val frequencyLow = bus.readRaw(NR33_ADDR) and 0xFF
            val newFrequency = frequencyHigh shl 8 or frequencyLow
            frequency = newFrequency

            frequencyTimer += (2048 - frequency) * 2
        }
    }

    override fun tickLength() {
        if (!lengthEnabled) return
        if (lengthCounter == 0) return

        lengthCounter--

        if (lengthCounter == 0) {
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

    override fun reset() {
        enabled = false
        frequencyTimer = 0
        frequency = 0
        currentVolume = 0
        wavePosition = 0
        lengthEnabled = false
    }

    override fun loadLengthCounter(value: Int) {
        val lengthLoad = value and 0xFF  // NR31 is 8-bit (0-255)
        lengthCounter = 256 - lengthLoad
    }

    override fun trigger() {
        val cycleApproximation = (2048 - frequency) * 2 - frequencyTimer
        if (enabled && cycleApproximation in 0..2) {
            // TODO: DMG corruption on retrigger while active — destination logic is implemented
            //  per Pan Docs (case 1: byte index 0-3 → write to wave[0]; case 2: byte index 4-15
            //  → copy aligned 4-byte group to wave[0..3]), but the timing condition is missing.
            //  On real DMG, corruption only occurs "while it reads a sample byte" (~2 T-cycle
            //  window after wavePosition advances). Currently always applied when enabled.
            //  Approximation: check (2048 - frequency) * 2 - frequencyTimer <= 2 before corrupting.
            val index = wavePosition / 2
            if (index < 4) {
                val value = bus.readRaw(WAVE_ADDR + index)
                bus.writeRaw(WAVE_ADDR, value)
            } else {
                val alignedIndex = index and 0x0C
                for (i in 0..3) {
                    val value = bus.readRaw(WAVE_ADDR + alignedIndex + i)
                    bus.writeRaw(WAVE_ADDR + i, value)
                }
            }
        }

        if (lengthCounter == 0) {
            lengthCounter = 256
            // Extra clock if length already enabled and frame sequencer at odd step
            if (lengthEnabled && getFrameSequencerStep() % 2 == 1) {
                lengthCounter--
                if (lengthCounter == 0) enabled = false
            }
        }

        if (!dacEnabled) return

        // TODO: DMG quirk — after trigger, the first sample played is the previous value
        //  still in the high nibble of the sample buffer, not the first nibble of wave RAM.
        //  The wave channel doesn't load the first byte on trigger; the first nibble from
        //  the wave table is not played until the waveform loops.
        //  See: https://gbdev.io/pandocs/Audio_details.html
        wavePosition = 0

        enabled = true
        loadFrequency()

        val nr32 = bus.read(NR32_ADDR)
        currentVolume = (nr32 and 0x60) shr 5
    }

    override fun onDacWrite(value: Int) {
        // DAC disabled when bit 7 is 0
        if (value and 0x80 == 0) enabled = false
    }

    override fun onControlWrite(value: Int) {
        val newLengthEnable = value and 0x40 != 0

        if (!lengthEnabled && newLengthEnable) {
            if (getFrameSequencerStep() % 2 == 1) {
                // Direct extra clock, bypassing the lengthEnabled guard
                if (lengthCounter > 0) {
                    lengthCounter--
                    if (lengthCounter == 0) enabled = false
                }
            }
        }

        lengthEnabled = newLengthEnable
    }

    fun getWaveRamByte(address: Int): Int {
        return if (enabled) {
            // TODO: DMG quirk — wave RAM is only accessible within ~2 T-cycles after CH3
            //  reads a sample. Outside that window, reads should return 0xFF.
            //  Current implementation always redirects to the current wave position byte,
            //  which is a simplification that may cause test 10/12 failures.
            val byteIndex = wavePosition / 2
            val newAddress = WAVE_ADDR + byteIndex
            bus.readRaw(newAddress)
        } else {
            bus.readRaw(address)
        }
    }

    fun setWaveRamByte(address: Int, value: Int) {
        if (enabled) {
            // TODO: DMG quirk — wave RAM writes are only effective within ~2 T-cycles after
            //  CH3 reads a sample. Outside that window, writes should be ignored.
            //  Current implementation always redirects, which is a simplification.
            val byteIndex = wavePosition / 2
            val newAddress = WAVE_ADDR + byteIndex
            bus.writeRaw(newAddress, value)
        } else {
            bus.writeRaw(address, value)
        }
    }

    private fun loadFrequency() {
        val frequencyHigh = bus.readRaw(NR34_ADDR) and 0x07
        val frequencyLow = bus.readRaw(NR33_ADDR) and 0xFF
        val newFrequency = frequencyHigh shl 8 or frequencyLow
        frequencyTimer = (2048 - newFrequency) * 2
        frequency = newFrequency
    }

    companion object {
        private const val NR30_ADDR = 0xFF1A
        private const val NR31_ADDR = 0xFF1B
        private const val NR32_ADDR = 0xFF1C
        private const val NR33_ADDR = 0xFF1D
        private const val NR34_ADDR = 0xFF1E
        private const val WAVE_ADDR = 0xFF30
    }
}
