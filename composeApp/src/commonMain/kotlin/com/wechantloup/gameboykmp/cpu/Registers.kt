package com.wechantloup.gameboykmp.cpu

/**
 * Registers of the LR35902 CPU (Game Boy CPU).
 *
 * The CPU has 8-bit registers that can be paired into 16-bit registers.
 * Initial values represent the state after the boot ROM has run,
 * as documented in the Pan Docs (https://gbdev.io/pandocs/).
 */
class Registers {

    // ── 8-bit registers ───────────────────────────────────────────────────────

    /** Accumulator - main register, most arithmetic operations use it */
    var a: Int = 0x00

    /** General purpose - often paired with C as BC (counter or address) */
    var b: Int = 0x00

    /** General purpose - often paired with B as BC (counter or address) */
    var c: Int = 0x00

    /** General purpose - often paired with E as DE (source pointer) */
    var d: Int = 0x00

    /** General purpose - often paired with D as DE (source pointer) */
    var e: Int = 0x00

    /** General purpose - often paired with L as HL (default memory pointer) */
    var h: Int = 0x00

    /** General purpose - often paired with H as HL (default memory pointer) */
    var l: Int = 0x00

    /**
     * Flags register - upper 4 bits encode the result of the last operation.
     * Bit 7 (Z): Zero flag - set if result is 0
     * Bit 6 (N): Subtract flag - set if last operation was a subtraction
     * Bit 5 (H): Half-carry flag - set if carry from bit 3 to bit 4
     * Bit 4 (C): Carry flag - set if result overflowed 8 bits
     * Lower 4 bits are always 0.
     */
    var f: Int = 0x00

    // ── 16-bit special registers ──────────────────────────────────────────────

    /** Program Counter - address of the next instruction to execute */
    var pc: Int = 0x0000

    /** Stack Pointer - address of the top of the stack */
    var sp: Int = 0x0000

    // ── 16-bit register pairs ─────────────────────────────────────────────────

    /**
     * AF pair - A as high byte, F as low byte.
     * Used mainly for stack operations (PUSH/POP AF).
     * Note: lower 4 bits of F are always 0.
     */
    var af: Int
        get() = (a shl 8) or (f and 0xF0)
        set(value) {
            a = (value shr 8) and 0xFF
            f = value and 0xF0
        }

    /** BC pair - general purpose 16-bit register, often used as a counter */
    var bc: Int
        get() = (b shl 8) or c
        set(value) {
            b = (value shr 8) and 0xFF
            c = value and 0xFF
        }

    /** DE pair - general purpose 16-bit register, often used as a source pointer */
    var de: Int
        get() = (d shl 8) or e
        set(value) {
            d = (value shr 8) and 0xFF
            e = value and 0xFF
        }

    /**
     * HL pair - the most used 16-bit register.
     * Frequently used as a memory pointer for read/write operations.
     */
    var hl: Int
        get() = (h shl 8) or l
        set(value) {
            h = (value shr 8) and 0xFF
            l = value and 0xFF
        }

    // ── Flags ─────────────────────────────────────────────────────────────────

    /**
     * Zero flag (bit 7 of F).
     * Set if the result of the last operation was 0.
     */
    var flagZ: Boolean
        get() = (f and 0x80) != 0
        set(value) = setFlag(7, value)

    /**
     * Subtract flag (bit 6 of F).
     * Set if the last operation was a subtraction.
     */
    var flagN: Boolean
        get() = (f and 0x40) != 0
        set(value) = setFlag(6, value)

    /**
     * Half-carry flag (bit 5 of F).
     * Set if there was a carry from bit 3 to bit 4 in the last operation.
     */
    var flagH: Boolean
        get() = (f and 0x20) != 0
        set(value) = setFlag(5, value)

    /**
     * Carry flag (bit 4 of F).
     * Set if the last operation overflowed 8 bits (or 16 bits for 16-bit ops).
     */
    var flagC: Boolean
        get() = (f and 0x10) != 0
        set(value) = setFlag(4, value)

    /**
     * Initialize registers with boot values.
     */
    fun reset() {
        a = 0x01
        b = 0x00
        c = 0x13
        d = 0x00
        e = 0xD8
        h = 0x01
        l = 0x4D
        f = 0xB0
        pc = 0x0100
        sp = 0xFFFE

    }

    /**
     * Sets or clears a specific bit in the F register.
     * Always masks the lower 4 bits to 0 (they must always be 0).
     */
    private fun setFlag(bit: Int, value: Boolean) {
        f = if (value) f or (1 shl bit) else f and (1 shl bit).inv()
        f = f and 0xF0  // lower nibble is always 0
    }
}
