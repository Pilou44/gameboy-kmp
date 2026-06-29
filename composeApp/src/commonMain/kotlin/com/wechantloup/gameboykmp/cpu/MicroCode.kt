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

        this[0xC5] = arrayOf(
            // PUSH BC
            // M1 (after fetch): internal SP-prep M-cycle, no bus access, no decrement here —
            // matches push()'s leading onMachineCycleTick() before any SP--.
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            // M2: SP-- then write high byte (B) to [SP]. Decrement precedes the access (data dependency).
            MicroOp.Internal { it.microDecSp() },
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.SP, Src8.B),
            // M3: SP-- then write low byte (C) to [SP].
            MicroOp.Internal { it.microDecSp() },
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.SP, Src8.C),
        )

        this[0xD5] = arrayOf(
            // PUSH DE
            // M1 (after fetch): internal SP-prep M-cycle, no bus access, no decrement here —
            // matches push()'s leading onMachineCycleTick() before any SP--.
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            // M2: SP-- then write high byte (D) to [SP]. Decrement precedes the access (data dependency).
            MicroOp.Internal { it.microDecSp() },
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.SP, Src8.D),
            // M3: SP-- then write low byte (E) to [SP].
            MicroOp.Internal { it.microDecSp() },
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.SP, Src8.E),
        )

        this[0xE5] = arrayOf(
            // PUSH HL
            // M1 (after fetch): internal SP-prep M-cycle, no bus access, no decrement here —
            // matches push()'s leading onMachineCycleTick() before any SP--.
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            // M2: SP-- then write high byte (H) to [SP]. Decrement precedes the access (data dependency).
            MicroOp.Internal { it.microDecSp() },
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.SP, Src8.H),
            // M3: SP-- then write low byte (L) to [SP].
            MicroOp.Internal { it.microDecSp() },
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.SP, Src8.L),
        )

        this[0xF5] = arrayOf(
            // PUSH AF
            // M1 (after fetch): internal SP-prep M-cycle, no bus access, no decrement here —
            // matches push()'s leading onMachineCycleTick() before any SP--.
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            // M2: SP-- then write high byte (A) to [SP]. Decrement precedes the access (data dependency).
            MicroOp.Internal { it.microDecSp() },
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.SP, Src8.A),
            // M3: SP-- then write low byte (F) to [SP].
            MicroOp.Internal { it.microDecSp() },
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.SP, Src8.F),
        )

        this[0x22] = arrayOf(
            // LD (HL+), A
            MicroOp.WriteMem(Addr16.HL, Src8.A),
            MicroOp.Internal { it.microIncHl() },
            MicroOp.Idle,
            MicroOp.Idle,
        )
        this[0x32] = arrayOf(
            // LD (HL-), A
            MicroOp.WriteMem(Addr16.HL, Src8.A),
            MicroOp.Internal { it.microDecHl() },
            MicroOp.Idle,
            MicroOp.Idle,
        )
        this[0x2A] = arrayOf(
            // LD A, (HL+)
            MicroOp.ReadMem(Addr16.HL, Latch.Z),
            MicroOp.Internal { it.microIncHl() },
            MicroOp.Internal { it.microZtoA() },
            MicroOp.Idle,
        )
        this[0x3A] = arrayOf(
            // LD A, (HL-)
            MicroOp.ReadMem(Addr16.HL, Latch.Z),
            MicroOp.Internal { it.microDecHl() },
            MicroOp.Internal { it.microZtoA() },
            MicroOp.Idle,
        )

        this[0x20] = jrCc(Condition.NZ)   // JR NZ, e
        this[0x28] = jrCc(Condition.Z)    // JR Z, e
        this[0x30] = jrCc(Condition.NC)   // JR NC, e
        this[0x38] = jrCc(Condition.C)    // JR C, e

        this[0xE8] = arrayOf(
            // ADD SP, e — 4 M-cycles
            // M2: Z <- e
            MicroOp.ReadImmediate(Latch.Z),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            // M3: compute + write SP
            MicroOp.Internal { it.addSpE() },
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            // M4: internal settle
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
        )
        this[0xF8] = arrayOf(
            // LD HL, SP+e — 3 M-cycles
            // M2: Z <- e
            MicroOp.ReadImmediate(Latch.Z),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            // M3: compute + write HL
            MicroOp.Internal { it.ldHlSpE() },
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
        )

        // TODO migrate distinct shapes next: push (pre-dec SP), LDI (post-inc HL), JR cc (conditional
        //  push to pipeline), ISR — to prove the MicroOp set has no dead-end before bulk-filling.
    }

    /** JR cc, e: read signed offset into Z (M2), then resolve — jrResolve pushes the taken M-cycle. */
    private fun jrCc(c: Condition): Array<MicroOp> = arrayOf(
        MicroOp.ReadImmediate(Latch.Z), MicroOp.Idle, MicroOp.Idle,
        MicroOp.Internal { it.jrResolve(c) },
    )
}
