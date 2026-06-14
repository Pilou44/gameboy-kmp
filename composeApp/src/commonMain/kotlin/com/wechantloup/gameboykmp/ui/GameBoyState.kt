package com.wechantloup.gameboykmp.ui

data class GameBoyState(
    val coloredFrameBuffer: IntArray? = null,
    val dmgPalette: Palette = Palette.DMG,
    val frameCount: Int = 0,
    val isSaving: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GameBoyState) return false
        return frameCount == other.frameCount &&
                dmgPalette == other.dmgPalette &&
                isSaving == other.isSaving &&
                (coloredFrameBuffer?.contentEquals(other.coloredFrameBuffer) ?: (other.coloredFrameBuffer == null))
    }

    override fun hashCode(): Int {
        var result = coloredFrameBuffer?.contentHashCode() ?: 0
        result = 31 * result + dmgPalette.hashCode()
        result = 31 * result + frameCount
        result = 31 * result + isSaving.hashCode()
        return result
    }
}
