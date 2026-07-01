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

        this[0x08] = arrayOf(
            // M1
            MicroOp.ReadImmediate(Latch.Z),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            // M2
            MicroOp.ReadImmediate(Latch.W),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            // M3
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.WZ, Src8.SPL),
            MicroOp.Idle,
            // M4: WZ++ (addr -> addr+1), then write SP high byte to [addr+1].
            MicroOp.Internal { it.microIncWZ() },
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.WZ, Src8.SPH),
            MicroOp.Idle,
        )

        this[0x01] = arrayOf(
            // M1
            MicroOp.ReadImmediate(Latch.Z),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            // M2
            MicroOp.ReadImmediate(Latch.W),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Internal { it.microWZtoBc() },
        )

        this[0x11] = arrayOf(
            // M1
            MicroOp.ReadImmediate(Latch.Z),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            // M2
            MicroOp.ReadImmediate(Latch.W),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Internal { it.microWZtoDe() },
        )

        this[0x21] = arrayOf(
            // M1
            MicroOp.ReadImmediate(Latch.Z),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            // M2
            MicroOp.ReadImmediate(Latch.W),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Internal { it.microWZtoHl() },
        )

        this[0x31] = arrayOf(
            // M1
            MicroOp.ReadImmediate(Latch.Z),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            // M2
            MicroOp.ReadImmediate(Latch.W),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Internal { it.microWZtoSp() },
        )

        this[0x02] = arrayOf(
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.BC, Src8.A),
            MicroOp.Idle,
        )

        this[0x12] = arrayOf(
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.DE, Src8.A),
            MicroOp.Idle,
        )

        this[0x0A] = arrayOf(
            MicroOp.ReadMem(Addr16.BC, Latch.Z),
            MicroOp.ZtoReg(Reg8.A),
            MicroOp.Idle,
            MicroOp.Idle,
        )

        this[0x1A] = arrayOf(
            MicroOp.ReadMem(Addr16.DE, Latch.Z),
            MicroOp.ZtoReg(Reg8.A),
            MicroOp.Idle,
            MicroOp.Idle,
        )

        this[0x06] = arrayOf(
            MicroOp.ReadImmediate(Latch.Z),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.ZtoReg(Reg8.B),
        )

        this[0x0E] = arrayOf(
            MicroOp.ReadImmediate(Latch.Z),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.ZtoReg(Reg8.C),
        )

        this[0x16] = arrayOf(
            MicroOp.ReadImmediate(Latch.Z),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.ZtoReg(Reg8.D),
        )

        this[0x1E] = arrayOf(
            MicroOp.ReadImmediate(Latch.Z),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.ZtoReg(Reg8.E),
        )

        this[0x26] = arrayOf(
            MicroOp.ReadImmediate(Latch.Z),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.ZtoReg(Reg8.H),
        )

        this[0x2E] = arrayOf(
            MicroOp.ReadImmediate(Latch.Z),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.ZtoReg(Reg8.L),
        )

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

        this[0x3E] = arrayOf(
            MicroOp.ReadImmediate(Latch.Z),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.ZtoReg(Reg8.A),
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

        this[0xC1] = arrayOf(
            // POP BC
            // M1 (after fetch): read low byte into Z, then SP++. Increment follows the read (data dependency).
            MicroOp.ReadMem(Addr16.SP, Latch.Z),
            MicroOp.Internal { it.microIncSp() },
            MicroOp.Idle,
            MicroOp.Idle,
            // M2: read high byte into W, SP++, then assemble (W<<8)|Z into BC (internal, no bus access).
            MicroOp.ReadMem(Addr16.SP, Latch.W),
            MicroOp.Internal { it.microIncSp() },
            MicroOp.Internal { it.microWZtoBc() },
            MicroOp.Idle,
        )

        this[0xD1] = arrayOf(
            // POP DE
            // M1 (after fetch): read low byte into Z, then SP++. Increment follows the read (data dependency).
            MicroOp.ReadMem(Addr16.SP, Latch.Z),
            MicroOp.Internal { it.microIncSp() },
            MicroOp.Idle,
            MicroOp.Idle,
            // M2: read high byte into W, SP++, then assemble (W<<8)|Z into DE (internal, no bus access).
            MicroOp.ReadMem(Addr16.SP, Latch.W),
            MicroOp.Internal { it.microIncSp() },
            MicroOp.Internal { it.microWZtoDe() },
            MicroOp.Idle,
        )

        this[0xE1] = arrayOf(
            // POP HL
            // M1 (after fetch): read low byte into Z, then SP++. Increment follows the read (data dependency).
            MicroOp.ReadMem(Addr16.SP, Latch.Z),
            MicroOp.Internal { it.microIncSp() },
            MicroOp.Idle,
            MicroOp.Idle,
            // M2: read high byte into W, SP++, then assemble (W<<8)|Z into HL (internal, no bus access).
            MicroOp.ReadMem(Addr16.SP, Latch.W),
            MicroOp.Internal { it.microIncSp() },
            MicroOp.Internal { it.microWZtoHl() },
            MicroOp.Idle,
        )

        this[0xF1] = arrayOf(
            // POP HL
            // M1 (after fetch): read low byte into Z, then SP++. Increment follows the read (data dependency).
            MicroOp.ReadMem(Addr16.SP, Latch.Z),
            MicroOp.Internal { it.microIncSp() },
            MicroOp.Idle,
            MicroOp.Idle,
            // M2: read high byte into W, SP++, then assemble (W<<8)|Z into HL (internal, no bus access).
            MicroOp.ReadMem(Addr16.SP, Latch.W),
            MicroOp.Internal { it.microIncSp() },
            MicroOp.Internal { it.microPopAf() },
            MicroOp.Idle,
        )

        this[0xC9] = arrayOf(
            // RET
            // M1 (after fetch): read low byte into Z, SP++.
            MicroOp.ReadMem(Addr16.SP, Latch.Z),
            MicroOp.Internal { it.microIncSp() },
            MicroOp.Idle,
            MicroOp.Idle,
            // M2: read high byte into W, SP++.
            MicroOp.ReadMem(Addr16.SP, Latch.W),
            MicroOp.Internal { it.microIncSp() },
            MicroOp.Idle,
            MicroOp.Idle,
            // M3: internal jump M-cycle — assemble (W<<8)|Z into PC.
            MicroOp.Internal { it.microWZtoPc() },
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
        )

        this[0xC0] = retCc(Condition.NZ)
        this[0xC8] = retCc(Condition.Z)
        this[0xD0] = retCc(Condition.NC)
        this[0xD8] = retCc(Condition.C)

        this[0xD9] = arrayOf(
            // RETI
            // M1 (after fetch): read low byte into Z, SP++.
            MicroOp.ReadMem(Addr16.SP, Latch.Z),
            MicroOp.Internal { it.microIncSp() },
            MicroOp.Idle,
            MicroOp.Idle,
            // M2: read high byte into W, SP++.
            MicroOp.ReadMem(Addr16.SP, Latch.W),
            MicroOp.Internal { it.microIncSp() },
            MicroOp.Idle,
            MicroOp.Idle,
            // M3: internal jump M-cycle — assemble PC, then enable interrupts.
            MicroOp.Internal { it.microWZtoPc() },
            MicroOp.Internal { it.microSetIme() },
            MicroOp.Idle,
            MicroOp.Idle,
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
            MicroOp.ZtoReg(Reg8.A),
            MicroOp.Idle,
        )
        this[0x3A] = arrayOf(
            // LD A, (HL-)
            MicroOp.ReadMem(Addr16.HL, Latch.Z),
            MicroOp.Internal { it.microDecHl() },
            MicroOp.ZtoReg(Reg8.A),
            MicroOp.Idle,
        )

        this[0x18] = arrayOf(
            MicroOp.ReadImmediate(Latch.Z),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Internal { it.jrResolve() },
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

        this[0xC3] = arrayOf(
            // M1
            MicroOp.ReadImmediate(Latch.Z),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            // M2
            MicroOp.ReadImmediate(Latch.W),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            // M3
            MicroOp.Internal { it.microWZtoPc() },
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
        )

        this[0xC2] = jpCc(Condition.NZ)
        this[0xCA] = jpCc(Condition.Z)
        this[0xD2] = jpCc(Condition.NC)
        this[0xDA] = jpCc(Condition.C)

        this[0xCD] = arrayOf(
            // M1
            MicroOp.ReadImmediate(Latch.Z),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            // M2
            MicroOp.ReadImmediate(Latch.W),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            // M3
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            // M4: SP-- then write PC high byte to [SP].
            MicroOp.Internal { it.microDecSp() },
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.SP, Src8.PCH),
            // M5: SP-- then write PC low byte to [SP], then jump (PC <- WZ = nn).
            MicroOp.Internal { it.microDecSp() },
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.SP, Src8.PCL),
            MicroOp.Internal { it.microWZtoPc() },
        )

        this[0xC4] = callCc(Condition.NZ)
        this[0xCC] = callCc(Condition.Z)
        this[0xD4] = callCc(Condition.NC)
        this[0xDC] = callCc(Condition.C)

        this[0x03] = arrayOf(
            MicroOp.Internal { it.microIncBc() },
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
        )
        this[0x13] = arrayOf(
            MicroOp.Internal { it.microIncDe() },
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
        )
        this[0x23] = arrayOf(
            MicroOp.Internal { it.microIncHl() },
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
        )
        this[0x33] = arrayOf(
            MicroOp.Internal { it.microIncSp() },
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
        )
        this[0x0B] = arrayOf(
            MicroOp.Internal { it.microDecBc() },
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
        )
        this[0x1B] = arrayOf(
            MicroOp.Internal { it.microDecDe() },
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
        )
        this[0x2B] = arrayOf(
            MicroOp.Internal { it.microDecHl() },
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
        )
        this[0x3B] = arrayOf(
            MicroOp.Internal { it.microDecSp() },
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
        )

        this[0x09] = arrayOf(
            MicroOp.AddHl(Reg16.BC),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle
        )
        this[0x19] = arrayOf(
            MicroOp.AddHl(Reg16.DE),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle
        )
        this[0x29] = arrayOf(
            MicroOp.AddHl(Reg16.HL),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle
        )
        this[0x39] = arrayOf(
            MicroOp.AddHl(Reg16.SP),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle
        )

        this[0xC7] = arrayOf(
            // RST 00h
            // M1 (after fetch): internal SP-prep M-cycle, no bus access, no decrement here —
            // matches push()'s leading onMachineCycleTick() before any SP--.
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            // M2: SP-- then write high byte (PCH) to [SP]. Decrement precedes the access (data dependency).
            MicroOp.Internal { it.microDecSp() },
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.SP, Src8.PCH),
            MicroOp.Idle,
            // M3: SP-- then write low byte (PCL) to [SP], then jump to the vector (PC <- vector).
            MicroOp.Internal { it.microDecSp() },
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.SP, Src8.PCL),
            MicroOp.Rst(0x00),
        )
        this[0xCF] = arrayOf(
            // RST 08h
            // M1 (after fetch): internal SP-prep M-cycle, no bus access, no decrement here —
            // matches push()'s leading onMachineCycleTick() before any SP--.
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            // M2: SP-- then write high byte (PCH) to [SP]. Decrement precedes the access (data dependency).
            MicroOp.Internal { it.microDecSp() },
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.SP, Src8.PCH),
            MicroOp.Idle,
            // M3: SP-- then write low byte (PCL) to [SP], then jump to the vector (PC <- vector).
            MicroOp.Internal { it.microDecSp() },
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.SP, Src8.PCL),
            MicroOp.Rst(0x08),
        )
        this[0xD7] = arrayOf(
            // RST 10h
            // M1 (after fetch): internal SP-prep M-cycle, no bus access, no decrement here —
            // matches push()'s leading onMachineCycleTick() before any SP--.
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            // M2: SP-- then write high byte (PCH) to [SP]. Decrement precedes the access (data dependency).
            MicroOp.Internal { it.microDecSp() },
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.SP, Src8.PCH),
            MicroOp.Idle,
            // M3: SP-- then write low byte (PCL) to [SP], then jump to the vector (PC <- vector).
            MicroOp.Internal { it.microDecSp() },
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.SP, Src8.PCL),
            MicroOp.Rst(0x10),
        )
        this[0xDF] = arrayOf(
            // RST 18h
            // M1 (after fetch): internal SP-prep M-cycle, no bus access, no decrement here —
            // matches push()'s leading onMachineCycleTick() before any SP--.
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            // M2: SP-- then write high byte (PCH) to [SP]. Decrement precedes the access (data dependency).
            MicroOp.Internal { it.microDecSp() },
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.SP, Src8.PCH),
            MicroOp.Idle,
            // M3: SP-- then write low byte (PCL) to [SP], then jump to the vector (PC <- vector).
            MicroOp.Internal { it.microDecSp() },
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.SP, Src8.PCL),
            MicroOp.Rst(0x18),
        )
        this[0xE7] = arrayOf(
            // RST 20h
            // M1 (after fetch): internal SP-prep M-cycle, no bus access, no decrement here —
            // matches push()'s leading onMachineCycleTick() before any SP--.
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            // M2: SP-- then write high byte (PCH) to [SP]. Decrement precedes the access (data dependency).
            MicroOp.Internal { it.microDecSp() },
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.SP, Src8.PCH),
            MicroOp.Idle,
            // M3: SP-- then write low byte (PCL) to [SP], then jump to the vector (PC <- vector).
            MicroOp.Internal { it.microDecSp() },
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.SP, Src8.PCL),
            MicroOp.Rst(0x20),
        )
        this[0xEF] = arrayOf(
            // RST 28h
            // M1 (after fetch): internal SP-prep M-cycle, no bus access, no decrement here —
            // matches push()'s leading onMachineCycleTick() before any SP--.
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            // M2: SP-- then write high byte (PCH) to [SP]. Decrement precedes the access (data dependency).
            MicroOp.Internal { it.microDecSp() },
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.SP, Src8.PCH),
            MicroOp.Idle,
            // M3: SP-- then write low byte (PCL) to [SP], then jump to the vector (PC <- vector).
            MicroOp.Internal { it.microDecSp() },
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.SP, Src8.PCL),
            MicroOp.Rst(0x28),
        )
        this[0xF7] = arrayOf(
            // RST 30h
            // M1 (after fetch): internal SP-prep M-cycle, no bus access, no decrement here —
            // matches push()'s leading onMachineCycleTick() before any SP--.
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            // M2: SP-- then write high byte (PCH) to [SP]. Decrement precedes the access (data dependency).
            MicroOp.Internal { it.microDecSp() },
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.SP, Src8.PCH),
            MicroOp.Idle,
            // M3: SP-- then write low byte (PCL) to [SP], then jump to the vector (PC <- vector).
            MicroOp.Internal { it.microDecSp() },
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.SP, Src8.PCL),
            MicroOp.Rst(0x30),
        )
        this[0xFF] = arrayOf(
            // RST 38h
            // M1 (after fetch): internal SP-prep M-cycle, no bus access, no decrement here —
            // matches push()'s leading onMachineCycleTick() before any SP--.
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            // M2: SP-- then write high byte (PCH) to [SP]. Decrement precedes the access (data dependency).
            MicroOp.Internal { it.microDecSp() },
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.SP, Src8.PCH),
            MicroOp.Idle,
            // M3: SP-- then write low byte (PCL) to [SP], then jump to the vector (PC <- vector).
            MicroOp.Internal { it.microDecSp() },
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.SP, Src8.PCL),
            MicroOp.Rst(0x38),
        )

        this[0xE0] = arrayOf(
            // LDH (n),A — M2: n -> Z ; M3: W=0xFF then write A to [0xFF00|n]
            MicroOp.ReadImmediate(Latch.Z),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Internal { it.microHighPageW() },
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.WZ, Src8.A),
        )
        this[0xF0] = arrayOf(
            // LDH A,(n) — M2: n -> Z ; M3: W=0xFF, read [0xFF00|n] -> Z, then Z -> A
            MicroOp.ReadImmediate(Latch.Z),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Internal { it.microHighPageW() },
            MicroOp.ReadMem(Addr16.WZ, Latch.Z),
            MicroOp.Idle,
            MicroOp.ZtoReg(Reg8.A),
        )

        this[0xE2] = arrayOf(
            // LDH (C),A — addr = 0xFF00 | C, write A
            MicroOp.Internal { it.microHighPageC() },
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.WZ, Src8.A),
        )
        this[0xF2] = arrayOf(
            // LDH A,(C) — addr = 0xFF00 | C, read into A
            MicroOp.Internal { it.microHighPageC() },
            MicroOp.ReadMem(Addr16.WZ, Latch.Z),
            MicroOp.Idle,
            MicroOp.ZtoReg(Reg8.A),
        )

        this[0xEA] = arrayOf(
            // M1
            MicroOp.ReadImmediate(Latch.Z),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            // M2
            MicroOp.ReadImmediate(Latch.W),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            // M3
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.WriteMem(Addr16.WZ, Src8.A),
            MicroOp.Idle,
        )
        this[0xFA] = arrayOf(
            // M1
            MicroOp.ReadImmediate(Latch.Z),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            // M2
            MicroOp.ReadImmediate(Latch.W),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.Idle,
            // M3
            MicroOp.ReadMem(Addr16.WZ, Latch.Z),
            MicroOp.Idle,
            MicroOp.Idle,
            MicroOp.ZtoReg(Reg8.A),
        )

        // TODO migrate distinct shapes next: push (pre-dec SP), LDI (post-inc HL), JR cc (conditional
        //  push to pipeline), ISR — to prove the MicroOp set has no dead-end before bulk-filling.
    }

    /** JR cc, e: read signed offset into Z (M2), then resolve — jrResolve pushes the taken M-cycle. */
    private fun jrCc(c: Condition): Array<MicroOp> = arrayOf(
        MicroOp.ReadImmediate(Latch.Z),
        MicroOp.Idle,
        MicroOp.Idle,
        MicroOp.Internal { it.jrResolve(c) },
    )

    private fun jpCc(c: Condition): Array<MicroOp> = arrayOf(
        // M1
        MicroOp.ReadImmediate(Latch.Z),
        MicroOp.Idle,
        MicroOp.Idle,
        MicroOp.Idle,
        // M2
        MicroOp.ReadImmediate(Latch.W),
        MicroOp.Idle,
        MicroOp.Idle,
        MicroOp.Internal { it.jpResolve(c) },
    )

    private fun retCc(c: Condition): Array<MicroOp> = arrayOf(
        MicroOp.Idle,
        MicroOp.Idle,
        MicroOp.Idle,
        MicroOp.Internal { it.retResolve(c) },
    )

    private fun callCc(c: Condition): Array<MicroOp> = arrayOf(
        // M1
        MicroOp.ReadImmediate(Latch.Z),
        MicroOp.Idle,
        MicroOp.Idle,
        MicroOp.Idle,
        // M2
        MicroOp.ReadImmediate(Latch.W),
        MicroOp.Idle,
        MicroOp.Idle,
        MicroOp.Internal { it.callResolve(c) },
    )
}
