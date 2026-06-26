package com.wechantloup.gameboykmp.cpu

/**
 * Opcode -> micro-op sequence, built ONCE. null = not yet migrated (legacy execute() handles it).
 * The opcode's own fetch (and the boot-time decode) stays in step()/fetch() for now, so a sequence
 * only covers the M-cycles AFTER the opcode fetch. NOP therefore has an empty sequence: its single
 * M-cycle IS that fetch.
 */
object MicroCode {
    private val EMPTY = emptyArray<MicroOp>()

    val TABLE: Array<Array<MicroOp>?> = arrayOfNulls<Array<MicroOp>>(256).apply {
        this[0x00] = EMPTY                                   // NOP

        this[0x36] = arrayOf(                                // LD (HL), n
            MicroOp.ReadImmediate(Latch.Z),                  // M2: read n -> Z
            MicroOp.WriteMem(Addr16.HL, Src8.Z),             // M3: write [HL] <- Z
        )
        // TODO migrate remaining opcodes incrementally; harness stays green after each.
    }
}
