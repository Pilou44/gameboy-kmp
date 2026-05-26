package com.wechantloup.gameboykmp.cartridge

import kotlin.time.Clock

data class Mbc3CartridgeSave(
    var rtcOffsetMs: Long = Clock.System.now().toEpochMilliseconds(),
    var haltRtcTimeMs: Long = 0,
    var isRtcHalted: Boolean = false,
    var carry: Boolean = false,
    val ram: IntArray = IntArray(0x8000), // 32KB max - 4 banks × 8KB
) {
    constructor(value: IntArray) : this(
        ram = value.copyOfRange(0x00, 0x8000),
        rtcOffsetMs =
            (value[0x8000] and 0xFF).toLong() shl 56 or
            (value[0x8001] and 0xFF).toLong() shl 48 or
            (value[0x8002] and 0xFF).toLong() shl 40 or
            (value[0x8003] and 0xFF).toLong() shl 32 or
            (value[0x8004] and 0xFF).toLong() shl 24 or
            (value[0x8005] and 0xFF).toLong() shl 16 or
            (value[0x8006] and 0xFF).toLong() shl 8 or
            (value[0x8007] and 0xFF).toLong(),
        carry = value[0x8008] > 0,
        haltRtcTimeMs =
            (value[0x8009] and 0xFF).toLong() shl 56 or
            (value[0x800A] and 0xFF).toLong() shl 48 or
            (value[0x800B] and 0xFF).toLong() shl 40 or
            (value[0x800C] and 0xFF).toLong() shl 32 or
            (value[0x800D] and 0xFF).toLong() shl 24 or
            (value[0x800E] and 0xFF).toLong() shl 16 or
            (value[0x800F] and 0xFF).toLong() shl 8 or
            (value[0x8010] and 0xFF).toLong(),
        isRtcHalted = value[0x8011] > 0,
    )

    fun toIntArray(): IntArray {
        val finalArray = IntArray(0x8012)
        ram.copyInto(finalArray)
        finalArray[0x8000] = (rtcOffsetMs shr 56).toInt() and 0xFF
        finalArray[0x8000 + 1] = (rtcOffsetMs shr 48).toInt() and 0xFF
        finalArray[0x8000 + 2] = (rtcOffsetMs shr 40).toInt() and 0xFF
        finalArray[0x8000 + 3] = (rtcOffsetMs shr 32).toInt() and 0xFF
        finalArray[0x8000 + 4] = (rtcOffsetMs shr 24).toInt() and 0xFF
        finalArray[0x8000 + 5] = (rtcOffsetMs shr 16).toInt() and 0xFF
        finalArray[0x8000 + 6] = (rtcOffsetMs shr 8).toInt() and 0xFF
        finalArray[0x8000 + 7] = rtcOffsetMs.toInt() and 0xFF
        finalArray[0x8000 + 8] = if (carry) 1 else 0
        finalArray[0x8000 + 9] = (haltRtcTimeMs shr 56).toInt() and 0xFF
        finalArray[0x8000 + 10] = (haltRtcTimeMs shr 48).toInt() and 0xFF
        finalArray[0x8000 + 11] = (haltRtcTimeMs shr 40).toInt() and 0xFF
        finalArray[0x8000 + 12] = (haltRtcTimeMs shr 32).toInt() and 0xFF
        finalArray[0x8000 + 13] = (haltRtcTimeMs shr 24).toInt() and 0xFF
        finalArray[0x8000 + 14] = (haltRtcTimeMs shr 16).toInt() and 0xFF
        finalArray[0x8000 + 15] = (haltRtcTimeMs shr 8).toInt() and 0xFF
        finalArray[0x8000 + 16] = haltRtcTimeMs.toInt() and 0xFF
        finalArray[0x8000 + 17] = if (isRtcHalted) 1 else 0

        return finalArray
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Mbc3CartridgeSave

        if (rtcOffsetMs != other.rtcOffsetMs) return false
        if (carry != other.carry) return false
        if (haltRtcTimeMs != other.haltRtcTimeMs) return false
        if (isRtcHalted != other.isRtcHalted) return false
        if (!ram.contentEquals(other.ram)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = rtcOffsetMs.hashCode()
        result = 31 * result + carry.hashCode()
        result = 31 * result + haltRtcTimeMs.hashCode()
        result = 31 * result + isRtcHalted.hashCode()
        result = 31 * result + ram.contentHashCode()
        return result
    }
}
