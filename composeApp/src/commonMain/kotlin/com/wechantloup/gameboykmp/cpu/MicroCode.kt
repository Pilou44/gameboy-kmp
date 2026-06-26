package com.wechantloup.gameboykmp.cpu

/**
 * Opcode -> micro-op sequence (the M-cycles AFTER the opcode fetch; the fetch stays in Cpu). null =
 * not migrated yet -> legacy execute(). Built once, immutable; MicroOps are shared, stateless values
 * and Internal effects are capture-free (they act via their Cpu argument), so this top-level object
 * holds NO execution state and pins no Cpu instance — safe as a global, unlike a stateful singleton.
 * Each sequence length is a multiple of 4 (1 M-cycle = 4 T).
 */
object MicroCode {

    val TABLE: Array<Array<MicroOp>?> = arrayOfNulls<Array<MicroOp>>(256).apply {
        this[0x00] = emptyArray()                                  // NOP

        this[0x36] = arrayOf(
            // LD (HL), n
            MicroOp.ReadImmediate(Latch.Z),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.HL, Src8.Z),
        )

        this[0x34] = arrayOf(
            // INC (HL)
            MicroOp.ReadMem(Addr16.HL, Latch.Z),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Internal { it.microIncZ() },
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.HL, Src8.Z),
        )
        // TODO migrate distinct shapes next: push (pre-dec SP), LDI (post-inc HL), JR cc (conditional
        //  push to pipeline), ISR — to prove the MicroOp set has no dead-end before bulk-filling.
    }
}
