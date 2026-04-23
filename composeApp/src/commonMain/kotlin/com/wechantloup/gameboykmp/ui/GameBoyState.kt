package com.wechantloup.gameboykmp.ui

data class GameBoyState(
    val frameBuffer: IntArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GameBoyState) return false
        return frameBuffer.contentEquals(other.frameBuffer)
    }
    override fun hashCode(): Int = frameBuffer.contentHashCode()
}
