package com.wechantloup.gameboykmp.ui

data class GameBoyState(
    val frameBuffer: IntArray? = null,
    val frameCount: Int = 0,
    val isSaving: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GameBoyState) return false
        return frameCount == other.frameCount
                && isSaving == other.isSaving
    }
    override fun hashCode(): Int = frameBuffer.contentHashCode()
}
