package com.wechantloup.gameboykmp.cartridge

import com.wechantloup.gameboykmp.cartridge.Mbc3Cartridge.Companion.RTC_CYCLES_PER_SECOND
import kotlin.time.Clock

data class Mbc3CartridgeSave(
    var totalCycles: Long = 0L,
    var savedAtMs: Long = Clock.System.now().toEpochMilliseconds(),
    var isRtcHalted: Boolean = false,
    var carry: Boolean = false,
    val ram: IntArray = IntArray(0x10000),
) {
    constructor(value: IntArray) : this(
        ram = value.copyOfRange(0x00, 0x10000),
        totalCycles =
            (value[0x10000] and 0xFF).toLong() shl 56 or
            (value[0x10001] and 0xFF).toLong() shl 48 or
            (value[0x10002] and 0xFF).toLong() shl 40 or
            (value[0x10003] and 0xFF).toLong() shl 32 or
            (value[0x10004] and 0xFF).toLong() shl 24 or
            (value[0x10005] and 0xFF).toLong() shl 16 or
            (value[0x10006] and 0xFF).toLong() shl 8 or
            (value[0x10007] and 0xFF).toLong(), // TODO compute with elapsed time
        carry = value[0x10008] > 0,
        savedAtMs =
            (value[0x10009] and 0xFF).toLong() shl 56 or
            (value[0x1000A] and 0xFF).toLong() shl 48 or
            (value[0x1000B] and 0xFF).toLong() shl 40 or
            (value[0x1000C] and 0xFF).toLong() shl 32 or
            (value[0x1000D] and 0xFF).toLong() shl 24 or
            (value[0x1000E] and 0xFF).toLong() shl 16 or
            (value[0x1000F] and 0xFF).toLong() shl 8 or
            (value[0x10010] and 0xFF).toLong(),
        isRtcHalted = value[0x10011] > 0,
    )

    init {
        val now = Clock.System.now().toEpochMilliseconds()
        if (!isRtcHalted) {
            val elapsedMs = now - savedAtMs
            val elapsedTicks = elapsedMs * RTC_CYCLES_PER_SECOND / 1000L
            if (elapsedTicks > 0) {
                totalCycles += elapsedTicks
            }
        }
        savedAtMs = now
    }

    fun toIntArray(): IntArray {
        val finalArray = IntArray(0x10017)
        ram.copyInto(finalArray)
        finalArray[0x10000] = (totalCycles shr 56).toInt() and 0xFF
        finalArray[0x10000 + 1] = (totalCycles shr 48).toInt() and 0xFF
        finalArray[0x10000 + 2] = (totalCycles shr 40).toInt() and 0xFF
        finalArray[0x10000 + 3] = (totalCycles shr 32).toInt() and 0xFF
        finalArray[0x10000 + 4] = (totalCycles shr 24).toInt() and 0xFF
        finalArray[0x10000 + 5] = (totalCycles shr 16).toInt() and 0xFF
        finalArray[0x10000 + 6] = (totalCycles shr 8).toInt() and 0xFF
        finalArray[0x10000 + 7] = totalCycles.toInt() and 0xFF
        finalArray[0x10000 + 8] = if (carry) 1 else 0
        val now = Clock.System.now().toEpochMilliseconds()
        finalArray[0x10000 + 9] = (now shr 56).toInt() and 0xFF
        finalArray[0x10000 + 10] = (now shr 48).toInt() and 0xFF
        finalArray[0x10000 + 11] = (now shr 40).toInt() and 0xFF
        finalArray[0x10000 + 12] = (now shr 32).toInt() and 0xFF
        finalArray[0x10000 + 13] = (now shr 24).toInt() and 0xFF
        finalArray[0x10000 + 14] = (now shr 16).toInt() and 0xFF
        finalArray[0x10000 + 15] = (now shr 8).toInt() and 0xFF
        finalArray[0x10000 + 16] = now.toInt() and 0xFF
        finalArray[0x10000 + 17] = if (isRtcHalted) 1 else 0

        return finalArray
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Mbc3CartridgeSave

        if (totalCycles != other.totalCycles) return false
        if (carry != other.carry) return false
        if (savedAtMs != other.savedAtMs) return false
        if (isRtcHalted != other.isRtcHalted) return false
        if (!ram.contentEquals(other.ram)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = totalCycles.hashCode()
        result = 31 * result + carry.hashCode()
        result = 31 * result + savedAtMs.hashCode()
        result = 31 * result + isRtcHalted.hashCode()
        result = 31 * result + ram.contentHashCode()
        return result
    }
}
