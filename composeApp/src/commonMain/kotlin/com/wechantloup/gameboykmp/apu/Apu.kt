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
    private val channels = listOf<SoundChannel>(Channel1(bus))

    private val samples = mutableListOf<Float>()

    fun step(cycles: Int) {
        frameSequencerStep(cycles)
        channelsStep(cycles)
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
            val sum = activeChannels.sumOf { it.getSample() }
            val max = activeChannels.size * 15
            val adjustedSample = (sum - max / 2f) / (max / 2f)
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
//        TODO()
    }

    private fun tickSweep() {
        // CH1 only
        // CH1 - NR10 - 0xFF10
        // Un seul octet, 3 champs :
        // Bit 6-4 : sweep period  (0-7)
        // Bit 3   : negate        (0 = fréquence monte, 1 = fréquence descend)
        // Bit 2-0 : sweep shift   (0-7)
//        TODO()
    }

    private fun tickEnvelope() {
        // CH1, CH2 and CH4
//        TODO()
    }

    companion object {
        // TODO: use fractional accumulator to avoid sample drift
        // correct value is 4194304.0 / 44100.0 = 95.108...
        private val CYCLES_PER_SAMPLE = 95

        // TODO: adjust buffer size to account for fractional samples per frame
        // correct value is 44100.0 / 59.7 = 738.7...
        private const val  SAMPLES_PER_FRAME = 735
    }
}
