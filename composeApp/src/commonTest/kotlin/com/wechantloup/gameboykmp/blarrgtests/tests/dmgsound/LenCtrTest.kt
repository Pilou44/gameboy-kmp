package com.wechantloup.gameboykmp.blarrgtests.tests.dmgsound

import com.wechantloup.gameboykmp.blarrgtests.helpers.GameBoyTestHarness
import com.wechantloup.gameboykmp.blarrgtests.helpers.gameBoyTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LenCtrTest {

    /**
     * Context for a single channel test, mirroring test_chan_common variables.
     * channel: 0=square1, 1=square2, 2=wave, 3=noise
     */
    inner class ChannelTestContext(val channel: Int) {
        val chanBase = channel * 5 + 0x10  // offset from 0xFF00
        val chanMask = 1 shl channel        // bit in NR52
        val chanMaxLen = if (channel == 2) 256 else 64

        fun wchn(n: Int, data: Int, h: GameBoyTestHarness) {
            h.bus.write(0xFF00 + chanBase + n, data and 0xFF)
        }

        fun nr52(h: GameBoyTestHarness) = h.bus.read(0xFF26)

        fun shouldBeOn(h: GameBoyTestHarness) {
            assertNotEquals(0, nr52(h) and chanMask,
                "Channel $channel should be on")
        }

        fun shouldBeOff(h: GameBoyTestHarness) {
            assertEquals(0, nr52(h) and chanMask,
                "Channel $channel should be off")
        }

        fun shouldBeAlmostOff(h: GameBoyTestHarness) {
            shouldBeOn(h)
            h.parkCpu()
            h.stepCycles(16384)  // delay_apu 1
            shouldBeOff(h)
        }

        /**
         * Enables the channel's DAC (test_chan_begin)
         * Wave channel: NR30 bit 7
         * Others: NR12/NR22/NR42 bit 3 (non-zero volume)
         */
        fun enableDac(h: GameBoyTestHarness) {
            if (channel == 2) {
                wchn(0, 0x80, h)  // NR30: DAC on
            } else {
                wchn(2, 0x08, h)  // NRx2: silent but DAC on
            }
        }

        /**
         * Mirrors begin():
         * - sync_apu (wait for length counter clock)
         * - delay 2048
         * - wchn 4, 0x40 (disable length)
         * - wchn 1, -4   (length = 4)
         * - wchn 4, 0xC0 (trigger + enable length)
         */
        fun begin(h: GameBoyTestHarness) {
            h.parkCpu()
            syncApu(h)
            h.stepCycles(2048)
            wchn(4, 0x40, h)   // disable length
            wchn(1, -4, h)     // length = 4
            wchn(4, 0xC0, h)   // trigger + enable length
        }

        /**
         * Mirrors sync_apu: waits until the length counter clock fires.
         * Uses channel 1 (square 2): sets length=1, triggers, waits for NR52 bit 1 to clear.
         * Then aligns to next length clock boundary.
         */
        private fun syncApu(h: GameBoyTestHarness) {
            h.bus.write(0xFF19, 0x00)  // NR24: disable length
            h.bus.write(0xFF16, 0x3E)  // NR21: length = 2
            h.bus.write(0xFF17, 0x08)  // NR22: silent, DAC on
            h.bus.write(0xFF19, 0xC0)  // NR24: trigger + enable length
            // Wait for channel 2 (square 2) to turn off = length counter clocked
            var maxSteps = 100_000
            while (h.bus.read(0xFF26) and 0x02 != 0 && maxSteps-- > 0) {
//                println("PC = 0x${h.cpu.registers.pc.toString(16)}")
                h.stepCycles(4)
            }
        }
    }

    private fun forAllChannels(block: ChannelTestContext.(GameBoyTestHarness) -> Unit) {
        for (channel in 0..3) {
//            println("channel $channel - creating harness")
            val h = gameBoyTest {}
//            println("channel $channel - APU on")
            h.bus.write(0xFF26, 0x80)
//            println("channel $channel - NR51")
            h.bus.write(0xFF25, 0xFF)
//            println("channel $channel - NR50")
            h.bus.write(0xFF24, 0x77)
//            println("channel $channel - creating context")
            val ctx = ChannelTestContext(channel)
//            println("channel $channel - enableDac")
            ctx.enableDac(h)
//            println("channel $channel - calling block")
            ctx.block(h)
//            println("channel $channel - done")
        }
    }

    /**
     * Test 2: Length becoming 0 should clear channel status
     */
    @Test
    fun `Length becoming 0 should clear status`() = forAllChannels { h ->
        begin(h)
        stepCycles(h, 3)   // delay_apu 3
        shouldBeAlmostOff(h)
    }

    /**
     * Test 3: Length can be reloaded at any time
     */
    @Test
    fun `Length can be reloaded at any time`() = forAllChannels { h ->
//        println("begin")
        begin(h)
//        println("wchn")
        wchn(1, -10, h)
//        println("stepCycles")
        stepCycles(h, 9)
//        println("shouldBeAlmostOff")
        shouldBeAlmostOff(h)
    }

    private fun stepCycles(h: GameBoyTestHarness, apuClocks: Int) {
        h.stepCycles(apuClocks * 16384)
    }
}
