package com.wechantloup.gameboykmp.apu

interface SoundChannel {
    val isEnabled: Boolean
    val dacEnabled: Boolean
    fun trigger()
    fun step(cycles: Int)
    fun tickLength()
    fun getSample(): Int

    /**
     * Resets all internal channel state.
     * Called when the APU is powered off (NR52 bit 7 = 0).
     */
    fun reset()
}
