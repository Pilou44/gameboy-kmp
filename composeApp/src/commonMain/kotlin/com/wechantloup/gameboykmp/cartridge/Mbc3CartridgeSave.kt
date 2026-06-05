package com.wechantloup.gameboykmp.cartridge

import kotlin.time.Clock

data class Mbc3CartridgeSave(
    var rawSeconds: Int = 0, // 6 bits
    var rawMinutes: Int = 0, // 6 bits
    var rawHours: Int = 0, // 5 bits
    var rawDays: Int = 0, // 9 bits
    var lastTickMs: Long = Clock.System.now().toEpochMilliseconds(),
    var haltTimeMs: Long = 0,
    var isRtcHalted: Boolean = false,
    var carry: Boolean = false,
    val ram: IntArray = IntArray(0x10000), // 64KB max - 8 banks × 8KB
) {
    constructor(value: IntArray) : this(
        ram = value.copyOfRange(0x00, 0x10000),
        lastTickMs =
            (value[0x10000] and 0xFF).toLong() shl 56 or
            (value[0x10001] and 0xFF).toLong() shl 48 or
            (value[0x10002] and 0xFF).toLong() shl 40 or
            (value[0x10003] and 0xFF).toLong() shl 32 or
            (value[0x10004] and 0xFF).toLong() shl 24 or
            (value[0x10005] and 0xFF).toLong() shl 16 or
            (value[0x10006] and 0xFF).toLong() shl 8 or
            (value[0x10007] and 0xFF).toLong(),
        carry = value[0x10008] > 0,
        haltTimeMs =
            (value[0x10009] and 0xFF).toLong() shl 56 or
            (value[0x1000A] and 0xFF).toLong() shl 48 or
            (value[0x1000B] and 0xFF).toLong() shl 40 or
            (value[0x1000C] and 0xFF).toLong() shl 32 or
            (value[0x1000D] and 0xFF).toLong() shl 24 or
            (value[0x1000E] and 0xFF).toLong() shl 16 or
            (value[0x1000F] and 0xFF).toLong() shl 8 or
            (value[0x10010] and 0xFF).toLong(),
        isRtcHalted = value[0x10011] > 0,
        rawSeconds = value[0x10012] and 0x3F,
        rawMinutes = value[0x10013] and 0x3F,
        rawHours = value[0x10014] and 0x1F,
        rawDays = (value[0x10015] and 0x01) shl 8 or
            (value[0x10016] and 0xFF),
    )

    fun toIntArray(): IntArray {
        val finalArray = IntArray(0x10017)
        ram.copyInto(finalArray)
        finalArray[0x10000] = (lastTickMs shr 56).toInt() and 0xFF
        finalArray[0x10000 + 1] = (lastTickMs shr 48).toInt() and 0xFF
        finalArray[0x10000 + 2] = (lastTickMs shr 40).toInt() and 0xFF
        finalArray[0x10000 + 3] = (lastTickMs shr 32).toInt() and 0xFF
        finalArray[0x10000 + 4] = (lastTickMs shr 24).toInt() and 0xFF
        finalArray[0x10000 + 5] = (lastTickMs shr 16).toInt() and 0xFF
        finalArray[0x10000 + 6] = (lastTickMs shr 8).toInt() and 0xFF
        finalArray[0x10000 + 7] = lastTickMs.toInt() and 0xFF
        finalArray[0x10000 + 8] = if (carry) 1 else 0
        finalArray[0x10000 + 9] = (haltTimeMs shr 56).toInt() and 0xFF
        finalArray[0x10000 + 10] = (haltTimeMs shr 48).toInt() and 0xFF
        finalArray[0x10000 + 11] = (haltTimeMs shr 40).toInt() and 0xFF
        finalArray[0x10000 + 12] = (haltTimeMs shr 32).toInt() and 0xFF
        finalArray[0x10000 + 13] = (haltTimeMs shr 24).toInt() and 0xFF
        finalArray[0x10000 + 14] = (haltTimeMs shr 16).toInt() and 0xFF
        finalArray[0x10000 + 15] = (haltTimeMs shr 8).toInt() and 0xFF
        finalArray[0x10000 + 16] = haltTimeMs.toInt() and 0xFF
        finalArray[0x10000 + 17] = if (isRtcHalted) 1 else 0
        finalArray[0x10000 + 18] = rawSeconds and 0x3F
        finalArray[0x10000 + 19] = rawMinutes and 0x3F
        finalArray[0x10000 + 20] = rawHours and 0x1F
        finalArray[0x10000 + 21] = (rawDays shr 8) and 0x01
        finalArray[0x10000 + 22] = rawDays and 0xFF

        return finalArray
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Mbc3CartridgeSave

        if (lastTickMs != other.lastTickMs) return false
        if (carry != other.carry) return false
        if (haltTimeMs != other.haltTimeMs) return false
        if (isRtcHalted != other.isRtcHalted) return false
        if (!ram.contentEquals(other.ram)) return false
        if (rawSeconds != other.rawSeconds) return false
        if (rawMinutes != other.rawMinutes) return false
        if (rawHours != other.rawHours) return false
        if (rawDays != other.rawDays) return false

        return true
    }

    override fun hashCode(): Int {
        var result = lastTickMs.hashCode()
        result = 31 * result + carry.hashCode()
        result = 31 * result + haltTimeMs.hashCode()
        result = 31 * result + isRtcHalted.hashCode()
        result = 31 * result + ram.contentHashCode()
        result = 31 * result + rawSeconds.hashCode()
        result = 31 * result + rawMinutes.hashCode()
        result = 31 * result + rawHours.hashCode()
        result = 31 * result + rawDays.hashCode()
        return result
    }
}
