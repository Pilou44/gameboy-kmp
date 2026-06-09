package com.wechantloup.gameboykmp.cartridge

import kotlin.time.Clock

data class Mbc3CartridgeSave(
    var seconds: Int = 0,    // raw 8-bit register value
    var minutes: Int = 0,    // raw 8-bit register value
    var hours: Int = 0,      // raw 8-bit register value
    var daysLow: Int = 0,    // raw 8-bit register value
    var ctrl: Int = 0,       // bit 7=carry, bit 6=halt, bit 0=days bit 8
    var savedAtMs: Long = Clock.System.now().toEpochMilliseconds(),
    val ram: IntArray = IntArray(0x10000),
) {

    val isHalted: Boolean
        get() = (ctrl and 0x40) != 0

    // Save format:
    //   0x00000..0x0FFFF : RAM (64KB)
    //   0x10000          : seconds register
    //   0x10001          : minutes register
    //   0x10002          : hours register
    //   0x10003          : daysLow register
    //   0x10004          : ctrl register (carry | halt | days bit 8)
    //   0x10005..0x1000C : savedAtMs (8 bytes, big-endian)
    constructor(value: IntArray) : this(
        ram = value.copyOfRange(0x00, 0x10000),
        seconds  = value[0x10000],
        minutes  = value[0x10001],
        hours    = value[0x10002],
        daysLow  = value[0x10003],
        ctrl     = value[0x10004],
        savedAtMs =
            (value[0x10005] and 0xFF).toLong() shl 56 or
                    (value[0x10006] and 0xFF).toLong() shl 48 or
                    (value[0x10007] and 0xFF).toLong() shl 40 or
                    (value[0x10008] and 0xFF).toLong() shl 32 or
                    (value[0x10009] and 0xFF).toLong() shl 24 or
                    (value[0x1000A] and 0xFF).toLong() shl 16 or
                    (value[0x1000B] and 0xFF).toLong() shl 8  or
                    (value[0x1000C] and 0xFF).toLong(),
    )

    init {
        // Advance by real-world elapsed time since last save (whole seconds only).
        // Sub-second remainder is intentionally dropped — emulation picks up from there.
        if (!isHalted) {
            val now = Clock.System.now().toEpochMilliseconds()
            val elapsedMs = now - savedAtMs
            if (elapsedMs > 0L) advanceBySeconds(elapsedMs / 1000L)
            savedAtMs = now
        }
    }

    /**
     * Advances the RTC by [count] whole seconds, propagating carries through the
     * minutes/hours/days chain. Upper (reserved) bits of each register are preserved.
     */
    fun advanceBySeconds(count: Long) {
        if (count <= 0L) return

        // Extract valid bits only; upper bits are hardware-preserved and not part of the counter.
        var s = (seconds and 0x3F).toLong() + count
        var m = (minutes and 0x3F).toLong()
        var h = (hours   and 0x1F).toLong()
        var d = (((ctrl and 0x01) shl 8) or (daysLow and 0xFF)).toLong()

        m += s / 60L;  s %= 60L
        h += m / 60L;  m %= 60L
        d += h / 24L;  h %= 24L

        if (d >= 512L) {
            ctrl = ctrl or 0x80  // set carry flag
            d %= 512L
        }
        ctrl = (ctrl and 0xFE) or ((d shr 8).toInt() and 0x01)

        // Merge computed valid bits back, preserving the original upper bits.
        seconds  = s.toInt() or (seconds and 0xC0)
        minutes  = m.toInt() or (minutes and 0xC0)
        hours    = h.toInt() or (hours   and 0xE0)
        daysLow  = (d and 0xFF).toInt()
    }

    fun toIntArray(
        saveTimeMs: Long = Clock.System.now().toEpochMilliseconds()
    ): IntArray {
        val out = IntArray(0x1000D)
        ram.copyInto(out)
        out[0x10000] = seconds
        out[0x10001] = minutes
        out[0x10002] = hours
        out[0x10003] = daysLow
        out[0x10004] = ctrl
        // Snapshot the wall-clock time at the moment of persistence so that the
        // next load correctly computes elapsed real-world time.
        out[0x10005] = (saveTimeMs shr 56).toInt() and 0xFF
        out[0x10006] = (saveTimeMs shr 48).toInt() and 0xFF
        out[0x10007] = (saveTimeMs shr 40).toInt() and 0xFF
        out[0x10008] = (saveTimeMs shr 32).toInt() and 0xFF
        out[0x10009] = (saveTimeMs shr 24).toInt() and 0xFF
        out[0x1000A] = (saveTimeMs shr 16).toInt() and 0xFF
        out[0x1000B] = (saveTimeMs shr  8).toInt() and 0xFF
        out[0x1000C] = saveTimeMs.toInt()          and 0xFF
        return out
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as Mbc3CartridgeSave
        if (seconds  != other.seconds)  return false
        if (minutes  != other.minutes)  return false
        if (hours    != other.hours)    return false
        if (daysLow  != other.daysLow)  return false
        if (ctrl     != other.ctrl)     return false
        if (savedAtMs != other.savedAtMs) return false
        if (!ram.contentEquals(other.ram)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = seconds
        result = 31 * result + minutes
        result = 31 * result + hours
        result = 31 * result + daysLow
        result = 31 * result + ctrl
        result = 31 * result + savedAtMs.hashCode()
        result = 31 * result + ram.contentHashCode()
        return result
    }

    /**
     * Advances the RTC by exactly one second, using the hardware carry rules:
     *  - Normal carry fires when the counter reaches 60 (sec/min) or 24 (hours).
     *  - Binary overflow fires at 64 (sec/min, 6-bit) or 32 (hours, 5-bit):
     *    resets to 0 with NO carry to the next register.
     * Upper (reserved) bits of each register are preserved across the tick.
     */
    fun tickOnce() {
        // Seconds (6-bit counter, valid bits 0-5)
        val secNext = (seconds and 0x3F) + 1
        val secCarry: Boolean = when (secNext) {
            60   -> { seconds = seconds and 0xC0; true }           // normal carry
            64   -> { seconds = seconds and 0xC0; false }          // 6-bit binary overflow, no carry
            else -> { seconds = secNext or (seconds and 0xC0); false }
        }
        if (!secCarry) return

        // Minutes (6-bit counter, valid bits 0-5)
        val minNext = (minutes and 0x3F) + 1
        val minCarry: Boolean = when (minNext) {
            60   -> { minutes = minutes and 0xC0; true }
            64   -> { minutes = minutes and 0xC0; false }
            else -> { minutes = minNext or (minutes and 0xC0); false }
        }
        if (!minCarry) return

        // Hours (5-bit counter, valid bits 0-4)
        val hourNext = (hours and 0x1F) + 1
        val hourCarry: Boolean = when (hourNext) {
            24   -> { hours = hours and 0xE0; true }               // normal carry
            32   -> { hours = hours and 0xE0; false }              // 5-bit binary overflow, no carry
            else -> { hours = hourNext or (hours and 0xE0); false }
        }
        if (!hourCarry) return

        // Days (9-bit counter)
        val days = ((ctrl and 0x01) shl 8) or (daysLow and 0xFF)
        val newDays = (days + 1) and 0x1FF  // 9-bit wrap
        if (days == 0x1FF) ctrl = ctrl or 0x80  // set carry flag on 511 → 0 transition
        daysLow = newDays and 0xFF
        ctrl = (ctrl and 0xFE) or ((newDays shr 8) and 0x01)
    }
}
