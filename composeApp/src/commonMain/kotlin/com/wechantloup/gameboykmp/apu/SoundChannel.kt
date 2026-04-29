package com.wechantloup.gameboykmp.apu

interface SoundChannel {
    val isEnabled: Boolean
    fun step(cycles: Int)
    fun tickLength()
    fun getSample(): Int // 0-15
}
