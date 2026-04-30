package com.wechantloup.gameboykmp.apu

sealed interface SoundChannel {
    val isEnabled: Boolean
    val dacEnabled: Boolean
    fun step(cycles: Int)
    fun tickLength()
    fun getSample(): Int // 0-15
}
