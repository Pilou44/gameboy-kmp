package com.wechantloup.gameboykmp.apu

import com.wechantloup.gameboykmp.bus.Bus
import kotlinx.coroutines.channels.Channel

class Apu(
    private val bus: Bus,
) {
    val samplesChannel = Channel<FloatArray>(8) // 8 frames

    private var frameSequencer = 0
    private var frameSequencerCycleCount = 0

    private var channelsCycleCount = 0
    private val channels = listOf(
        Channel1(bus),
        Channel2(bus),
        Channel3(bus),
        Channel4(bus),
    )

    private val samples = mutableListOf<Float>()

    init {
        bus.onApuPowerOff = { powerOff() }
    }

    fun step(cycles: Int) {
        frameSequencerStep(cycles)
        channelsStep(cycles)
    }

    private fun powerOff() {
        channels.forEach { it.reset() }
        frameSequencer = 0
        frameSequencerCycleCount = 0
        channelsCycleCount = 0
        samples.clear()
    }

    private fun channelsStep(cycles: Int) {
        channels.forEach { it.step(cycles) }

        channelsCycleCount += cycles

        if (channelsCycleCount < CYCLES_PER_SAMPLE) return

        channelsCycleCount -= CYCLES_PER_SAMPLE

        val activeChannels = channels.filter { it.isEnabled }
        if (activeChannels.isEmpty()) {
            samples.add(0f) // silence
        } else {
            val sum = channels.sumOf { ch ->
                if (ch.isEnabled && ch.dacEnabled) (ch.getSample() / 7.5) - 1.0
                else 0.0
            }
            val adjustedSample = (sum / 4).toFloat()

            samples.add(adjustedSample)
        }

        if (samples.size == SAMPLES_PER_FRAME) {
            samplesChannel.trySend(samples.toFloatArray())
            samples.clear()
        }
    }

    private fun frameSequencerStep(cycles: Int) {
        frameSequencerCycleCount += cycles

        if (frameSequencerCycleCount < 8192) return

        frameSequencerCycleCount -= 8192

        when (frameSequencer) {
            0 -> {
                tickLength()
            }
            1 -> {
                // Nothing to do
            }
            2 -> {
                tickLength()
                tickSweep()
            }
            3 -> {
                // Nothing to do
            }
            4 -> {
                tickLength()
            }
            5 -> {}
            6 -> {
                tickLength()
                tickSweep()
            }
            7 -> {
                tickEnvelope()
            }
        }

        frameSequencer = (frameSequencer + 1) % 8
    }

    private fun tickLength() {
        // CH1, CH2, CH3 and CH4
        channels.forEach { it.tickLength() }
    }

    private fun tickSweep() {
        // CH1 only
        channels.forEach { channel ->
            when (channel) {
                is Channel1 -> channel.tickSweep()
                else -> {} // Nothing to do
            }
        }
    }

    private fun tickEnvelope() {
        // CH1, CH2 and CH4
        channels.forEach { channel ->
            when (channel) {
                is Channel1 -> channel.tickEnvelope()
                is Channel2 -> channel.tickEnvelope()
                is Channel4 -> channel.tickEnvelope()
                is Channel3 -> {} // Nothing to do
            }
        }
    }

    companion object {
        // TODO: use fractional accumulator to avoid sample drift
        // correct value is 4194304.0 / 44100.0 = 95.108...
        private const val CYCLES_PER_SAMPLE = 95

        // TODO: adjust buffer size to account for fractional samples per frame
        // correct value is 44100.0 / 59.7 = 738.7...
        const val SAMPLES_PER_FRAME = 735
    }
}
