package com.wechantloup.gameboykmp.cartridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class Mbc3CartridgeSaveTest {

    /** ctrl = 0x40 : halt bit set → init block is skipped entirely. */
    private fun haltedSave(seconds: Int = 0, minutes: Int = 0, hours: Int = 0,
                           daysLow: Int = 0, ctrl: Int = 0x40) =
        Mbc3CartridgeSave(seconds = seconds, minutes = minutes, hours = hours,
            daysLow = daysLow, ctrl = ctrl)

    @Test
    fun `registers survive toIntArray and constructor round-trip`() {
        val original = haltedSave(seconds = 30, minutes = 47, hours = 9, daysLow = 200)

        val loaded = Mbc3CartridgeSave(original.toIntArray())

        assertEquals(30,   loaded.seconds, "seconds")
        assertEquals(47,   loaded.minutes, "minutes")
        assertEquals(9,    loaded.hours,   "hours")
        assertEquals(200,  loaded.daysLow, "daysLow")
        assertEquals(0x40, loaded.ctrl,    "ctrl")
    }

    @Test
    fun `savedAtMs survives toIntArray and constructor round-trip`() {
        val knownMs = 1_700_000_000_000L
        val save = haltedSave()

        val loaded = Mbc3CartridgeSave(save.toIntArray(knownMs))
        // init skipped (halted) → savedAtMs preserved exactly as loaded
        assertEquals(knownMs, loaded.savedAtMs)
    }

    @Test
    fun `init advances registers by elapsed time since savedAtMs`() {
        val twelveMinutesAgoMs = Clock.System.now().toEpochMilliseconds() - 12 * 60_000L
        val save = Mbc3CartridgeSave(seconds = 0, minutes = 47, hours = 9,
            daysLow = 0, ctrl = 0)  // not halted
        save.savedAtMs = twelveMinutesAgoMs

        val loaded = Mbc3CartridgeSave(save.toIntArray())

        assertEquals(9,  loaded.hours,   "hours unchanged")
        assertEquals(59, loaded.minutes, "minutes: 47 + 12 = 59")
        assertTrue(loaded.seconds < 2,   "seconds ≈ 0 (test runs fast)")
    }

    @Test
    fun `advanceBySeconds 12 minutes from 9h47`() {
        val save = Mbc3CartridgeSave(seconds = 0, minutes = 47, hours = 9, daysLow = 0, ctrl = 0)
        save.advanceBySeconds(720)
        assertEquals(9,  save.hours)
        assertEquals(59, save.minutes)
        assertEquals(0,  save.seconds)
    }

    @Test
    fun `tickOnce high minutes does not carry to hours`() {
        // minutes = 60 (out-of-range): incrementing should give 61, NOT roll over + carry
        val save = Mbc3CartridgeSave(seconds = 59, minutes = 60, hours = 5, daysLow = 0, ctrl = 0)
        save.tickOnce()
        assertEquals(0,  save.seconds, "seconds wrapped to 0")
        assertEquals(61, save.minutes, "minutes incremented to 61, no carry")
        assertEquals(5,  save.hours,   "hours unchanged")
    }

    @Test
    fun `tickOnce seconds binary overflow does not carry to minutes`() {
        // seconds = 63: +1 = 64 → 6-bit overflow → 0, no carry
        val save = Mbc3CartridgeSave(seconds = 63, minutes = 10, hours = 3, daysLow = 0, ctrl = 0)
        save.tickOnce()
        assertEquals(0,  save.seconds, "seconds overflowed to 0")
        assertEquals(10, save.minutes, "minutes unchanged (no carry)")
        assertEquals(3,  save.hours,   "hours unchanged")
    }
}
