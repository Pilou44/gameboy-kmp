package com.wechantloup.gameboykmp.cartridge

data class Mbc3CartridgeSave(
    val rtcOffset: Long = 0,
    val carry: Boolean = false,
    val ram: IntArray = IntArray(0x8000), // 32KB max - 4 banks × 8KB
) {
    constructor(value: IntArray) : this(
        ram = value.copyOfRange(0x00, 0x8000),
        carry = value[0x8008] > 0,
        rtcOffset =
            (value[0x8000] and 0xFF).toLong() shl 56 or
            (value[0x8001] and 0xFF).toLong() shl 48 or
            (value[0x8002] and 0xFF).toLong() shl 40 or
            (value[0x8003] and 0xFF).toLong() shl 32 or
            (value[0x8004] and 0xFF).toLong() shl 24 or
            (value[0x8005] and 0xFF).toLong() shl 16 or
            (value[0x8006] and 0xFF).toLong() shl 8 or
            (value[0x8007] and 0xFF).toLong()
        ,
    )

    fun toIntArray(): IntArray {
        val finalArray = IntArray(0x8009)
        ram.copyInto(finalArray)
        finalArray[0x8000] = (rtcOffset shr 56).toInt() and 0xFF
        finalArray[0x8000 + 1] = (rtcOffset shr 48).toInt() and 0xFF
        finalArray[0x8000 + 2] = (rtcOffset shr 40).toInt() and 0xFF
        finalArray[0x8000 + 3] = (rtcOffset shr 32).toInt() and 0xFF
        finalArray[0x8000 + 4] = (rtcOffset shr 24).toInt() and 0xFF
        finalArray[0x8000 + 5] = (rtcOffset shr 16).toInt() and 0xFF
        finalArray[0x8000 + 6] = (rtcOffset shr 8).toInt() and 0xFF
        finalArray[0x8000 + 7] = rtcOffset.toInt() and 0xFF
        finalArray[0x8000 + 8] = if (carry) 1 else 0

        return finalArray
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Mbc3CartridgeSave

        if (rtcOffset != other.rtcOffset) return false
        if (carry != other.carry) return false
        if (!ram.contentEquals(other.ram)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = rtcOffset.hashCode()
        result = 31 * result + carry.hashCode()
        result = 31 * result + ram.contentHashCode()
        return result
    }
}
