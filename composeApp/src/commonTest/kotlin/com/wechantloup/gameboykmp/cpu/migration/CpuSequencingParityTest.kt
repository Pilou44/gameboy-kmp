package com.wechantloup.gameboykmp.cpu.migration

import com.wechantloup.gameboykmp.cpu.Cpu
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Golden-master harness for the CPU T-cycle refactor. Oracle = ReferenceCpu, a frozen copy of the
 * pre-refactor Cpu. For each case, reference and refactored Cpu run against identical TracingTestBus
 * instances; traces, M-cycle counts and final registers must match exactly.
 *
 * Proves NEUTRALITY (new CPU behaves like old), not hardware correctness — that stays the job of the
 * blargg/mooneye ROMs. Throwaway: delete this harness and ReferenceCpu once the refactor is green.
 *
 * traceReference and traceRefactored are intentionally duplicated rather than factored behind a shared
 * interface: it keeps the production Cpu untouched (no test-only supertype), and the redundancy dies
 * with the harness.
 */
class CpuSequencingParityTest {

    private class Case(val name: String, val setup: (TracingTestBus, com.wechantloup.gameboykmp.cpu.Registers) -> Unit)

    private fun traceReference(c: Case): InstructionTrace {
        val bus = TracingTestBus()
        val cpu = ReferenceCpu(bus, bus::onMCycleTick).also { it.reset() }
        c.setup(bus, cpu.registers)
        bus.beginTrace()            // discard fixture writes, trace only the instruction
        cpu.step()
        return InstructionTrace(bus.trace.toList(), bus.mCycle, cpu.registers.snapshot())
    }

    private fun traceRefactored(c: Case): InstructionTrace {
        val bus = TracingTestBus()
        val cpu = Cpu(bus, bus::onMCycleTick).also { it.reset() }
        c.setup(bus, cpu.registers)
        bus.beginTrace()
        cpu.step()
        return InstructionTrace(bus.trace.toList(), bus.mCycle, cpu.registers.snapshot())
    }

    @Test
    fun `refactored CPU matches reference, opcode by opcode`() {
        for (c in CASES) {
            assertEquals(traceReference(c), traceRefactored(c), "Trace mismatch on: ${c.name}")
        }
    }

    private companion object {
        // TODO expand to full opcode coverage. Seed set covers the distinct sequencing shapes: plain
        //  fetch, (HL) read+write, immediate+write, push/pop, call/ret, conditional jr taken/not-taken,
        //  16-bit ALU internal M-cycle. Undefined opcodes excluded on purpose.
        val CASES = listOf(
            Case("NOP")            { b, r -> r.pc = 0xC000; b.load(0xC000, 0x00) },
            Case("LD (HL),n")      { b, r -> r.pc = 0xC000; r.hl = 0xC100; b.load(0xC000, 0x36, 0x42) },
            Case("INC (HL)")       { b, r -> r.pc = 0xC000; r.hl = 0xC100; b.poke(0xC100, 0x0F); b.load(0xC000, 0x34) },
            Case("PUSH BC")        { b, r -> r.pc = 0xC000; r.sp = 0xD000; r.bc = 0x1234; b.load(0xC000, 0xC5) },
            Case("POP BC")         { b, r -> r.pc = 0xC000; r.sp = 0xCFFE; b.poke(0xCFFE, 0x34); b.poke(0xCFFF, 0x12); b.load(0xC000, 0xC1) },
            Case("CALL nn")        { b, r -> r.pc = 0xC000; r.sp = 0xD000; b.load(0xC000, 0xCD, 0x00, 0xC2) },
            Case("RET")            { b, r -> r.pc = 0xC000; r.sp = 0xCFFE; b.poke(0xCFFE, 0x00); b.poke(0xCFFF, 0xC2); b.load(0xC000, 0xC9) },
            Case("RETI")           { b, r -> r.pc = 0xC000; r.sp = 0xCFFE; b.poke(0xCFFE, 0x00); b.poke(0xCFFF, 0xC2); b.load(0xC000, 0xD9) },
            Case("JR taken")       { b, r -> r.pc = 0xC000; b.load(0xC000, 0x18, 0x05) },
            Case("JR NZ not taken"){ b, r -> r.pc = 0xC000; r.flagZ = true; b.load(0xC000, 0x20, 0x05) },
            Case("ADD HL,DE")      { b, r -> r.pc = 0xC000; r.hl = 0x0FFF; r.de = 0x0001; b.load(0xC000, 0x19) },

            // ADD SP,e (0xE8) and LD HL,SP+e (0xF8): flags H/C are on the UNSIGNED add of SP's low byte vs the
            // offset byte, Z=N=0 always. Inputs chosen so H and C are actually toggled (not trivially 0), with
            // both a positive and a negative offset, since sign-extension of e is part of what's under test.
            Case("ADD SP,e +carry") { b, r -> r.pc = 0xC000; r.sp = 0x00FF; b.load(0xC000, 0xE8, 0x01) }, // +1: H and C set
            Case("ADD SP,e neg")    { b, r -> r.pc = 0xC000; r.sp = 0xC010; b.load(0xC000, 0xE8, 0xF0) }, // -16: low-byte borrow path
            Case("LD HL,SP+e +carry"){ b, r -> r.pc = 0xC000; r.sp = 0x00FF; b.load(0xC000, 0xF8, 0x01) }, // +1: H and C set, dest HL
            Case("LD HL,SP+e neg")  { b, r -> r.pc = 0xC000; r.sp = 0xC010; b.load(0xC000, 0xF8, 0xF0) }, // -16

            // Push: DE/HL are the same shape as BC (form regressions, cheap). AF carries the F-mask path.
            Case("PUSH DE")        { b, r -> r.pc = 0xC000; r.sp = 0xD000; r.de = 0x5678; b.load(0xC000, 0xD5) },
            Case("PUSH HL")        { b, r -> r.pc = 0xC000; r.sp = 0xD000; r.hl = 0x9ABC; b.load(0xC000, 0xE5) },
            // PUSH AF exercises Src8.F's `and 0xF0`. With a flag-derived F the low nibble is already 0, so this
            // guards the form/regression but cannot differentially trigger the mask itself.
            // TODO: confirm in Registers whether `f` can hold low-nibble bits; if so, add a case that sets them.
            Case("PUSH AF")        { b, r -> r.pc = 0xC000; r.sp = 0xD000; r.a = 0x42; r.flagZ = true; r.flagN = true; r.flagH = true; r.flagC = true; b.load(0xC000, 0xF5) },

            // LD (HL±),A and LD A,(HL±): bus access + pointer post-modify (reads also go through microZtoA).
            Case("LD (HL+),A")     { b, r -> r.pc = 0xC000; r.hl = 0xC100; r.a = 0x99; b.load(0xC000, 0x22) },
            Case("LD (HL-),A")     { b, r -> r.pc = 0xC000; r.hl = 0xC100; r.a = 0x99; b.load(0xC000, 0x32) },
            Case("LD A,(HL+)")     { b, r -> r.pc = 0xC000; r.hl = 0xC100; b.poke(0xC100, 0x77); b.load(0xC000, 0x2A) },
            Case("LD A,(HL-)")     { b, r -> r.pc = 0xC000; r.hl = 0xC100; b.poke(0xC100, 0x77); b.load(0xC000, 0x3A) },

            // JR cc taken: hits the testCondition==true branch of jrResolve(c) — no current case does (0x18 is
            // unconditional, 0x20 is not-taken). 0x30/0x38 taken are form duplicates, included for free.
            Case("JR Z taken")     { b, r -> r.pc = 0xC000; r.flagZ = true; b.load(0xC000, 0x28, 0x05) },
            Case("JR NC taken")    { b, r -> r.pc = 0xC000; r.flagC = false; b.load(0xC000, 0x30, 0x05) },
            Case("JR C taken")     { b, r -> r.pc = 0xC000; r.flagC = true; b.load(0xC000, 0x38, 0x05) },

            // RET cc: the conditional counterpart of RET. The condition-check M-cycle is always spent (even when
            // not taken), so the two branches differ in duration — not-taken is 2 M-cycles, taken is 5. Both
            // branches must be exercised: taken drives retResolve's dynamic push, not-taken drives the early return.
            Case("RET Z taken")     { b, r -> r.pc = 0xC000; r.sp = 0xCFFE; r.flagZ = true;  b.poke(0xCFFE, 0x00); b.poke(0xCFFF, 0xC2); b.load(0xC000, 0xC8) },
            Case("RET Z not taken") { b, r -> r.pc = 0xC000; r.sp = 0xCFFE; r.flagZ = false; b.load(0xC000, 0xC8) },

            // JP nn: read nn into WZ (two immediate reads), then an internal jump M-cycle loads PC from WZ.
            Case("JP nn") { b, r -> r.pc = 0xC000; b.load(0xC000, 0xC3, 0x00, 0xC2) },   // jump to 0xC200

            // JP cc: like JR cc but with a 16-bit immediate target. Taken drives jpResolve's push, not-taken
            // drives the early return (the two nn reads happen either way; only the jump M-cycle is conditional).
            Case("JP Z taken")     { b, r -> r.pc = 0xC000; r.flagZ = true;  b.load(0xC000, 0xCA, 0x00, 0xC2) },
            Case("JP Z not taken") { b, r -> r.pc = 0xC000; r.flagZ = false; b.load(0xC000, 0xCA, 0x00, 0xC2) },

            // CALL cc: conditional CALL. Taken pushes the return address and jumps (6 M-cycles); not-taken just
            // reads nn and stops (3 M-cycles). Both branches of callResolve must be exercised.
            Case("CALL Z taken")     { b, r -> r.pc = 0xC000; r.sp = 0xD000; r.flagZ = true;  b.load(0xC000, 0xCC, 0x00, 0xC2) },
            Case("CALL Z not taken") { b, r -> r.pc = 0xC000; r.sp = 0xD000; r.flagZ = false; b.load(0xC000, 0xCC, 0x00, 0xC2) },

            // LD rr,nn: read nn into WZ (two immediate reads), assemble into the pair — no extra M-cycle.
            Case("LD BC,nn") { b, r -> r.pc = 0xC000; b.load(0xC000, 0x01, 0x34, 0x12) },   // BC <- 0x1234

            // LD r,n: read immediate into Z, then ZtoReg moves it into the destination register.
            Case("LD B,n") { b, r -> r.pc = 0xC000; b.load(0xC000, 0x06, 0x42) },   // B <- 0x42

            // LD (rr),A and LD A,(rr): single bus access in one M-cycle (write A out, or read into A via ZtoReg).
            Case("LD (BC),A") { b, r -> r.pc = 0xC000; r.bc = 0xC100; r.a = 0x99; b.load(0xC000, 0x02) },
            Case("LD (DE),A") { b, r -> r.pc = 0xC000; r.de = 0xC100; r.a = 0x99; b.load(0xC000, 0x12) },
            Case("LD A,(BC)") { b, r -> r.pc = 0xC000; r.bc = 0xC100; b.poke(0xC100, 0x77); b.load(0xC000, 0x0A) },
            Case("LD A,(DE)") { b, r -> r.pc = 0xC000; r.de = 0xC100; b.poke(0xC100, 0x77); b.load(0xC000, 0x1A) },

            // INC/DEC rr (16-bit): one internal M-cycle, no flags touched. Wrap cases catch a missing & 0xFFFF.
            Case("INC BC") { b, r -> r.pc = 0xC000; r.bc = 0x12FF; b.load(0xC000, 0x03) },   // -> 0x1300
            Case("DEC BC") { b, r -> r.pc = 0xC000; r.bc = 0x1300; b.load(0xC000, 0x0B) },   // -> 0x12FF

            // ADD HL,rr: 16-bit add into HL. flagC is the bit-15 carry, flagH the bit-11 carry, Z untouched.
            // The seeded ADD HL,DE only exercises H. These add the C path and the HL+HL (0x29) doubling shape.
            Case("ADD HL,BC carry") { b, r -> r.pc = 0xC000; r.hl = 0xFFFF; r.bc = 0x0001; b.load(0xC000, 0x09) }, // -> 0x0000, H+C set
            Case("ADD HL,HL")       { b, r -> r.pc = 0xC000; r.hl = 0x8800; b.load(0xC000, 0x29) },                 // HL+HL, C set (bit15)

            // RST: push PC (return address) then jump to the fixed vector. Same shape as CALL minus the nn reads.
            Case("RST 00h") { b, r -> r.pc = 0xC000; r.sp = 0xD000; b.load(0xC000, 0xC7) },   // push 0xC001, PC <- 0x0000

            // LD (nn),A and LD A,(nn): read a 16-bit address into WZ, then a single bus access through Addr16.WZ.
            Case("LD (nn),A") { b, r -> r.pc = 0xC000; r.a = 0x99; b.load(0xC000, 0xEA, 0x00, 0xC1) },               // write A to 0xC100
            Case("LD A,(nn)") { b, r -> r.pc = 0xC000; b.poke(0xC100, 0x77); b.load(0xC000, 0xFA, 0x00, 0xC1) },     // read 0xC100 into A

            // LDH (C),A / LDH A,(C): high-page access addressed by 0xFF00 | C (no immediate fetch).
            Case("LDH (C),A") { b, r -> r.pc = 0xC000; r.c = 0x80; r.a = 0x99; b.load(0xC000, 0xE2) },   // write A to 0xFF80
            Case("LDH A,(C)") { b, r -> r.pc = 0xC000; r.c = 0x80; b.poke(0xFF80, 0x77); b.load(0xC000, 0xF2) },

            // LDH (n),A / LDH A,(n): high-page access addressed by 0xFF00 | n, n being an immediate byte.
            Case("LDH (n),A") { b, r -> r.pc = 0xC000; r.a = 0x99; b.load(0xC000, 0xE0, 0x80) },                 // write A to 0xFF80
            Case("LDH A,(n)") { b, r -> r.pc = 0xC000; b.poke(0xFF80, 0x77); b.load(0xC000, 0xF0, 0x80) },       // read 0xFF80 into A

            // LD (nn),SP: write SP low to [nn], SP high to [nn+1]. Two consecutive addresses — a case that only
            // checks one address would miss a missing WZ increment (both bytes landing on nn).
            Case("LD (nn),SP") { b, r -> r.pc = 0xC000; r.sp = 0x1234; b.load(0xC000, 0x08, 0x00, 0xC1) },  // 0xC100 <- 0x34, 0xC101 <- 0x12

            // LD SP,HL: 16-bit register move with one internal M-cycle (unlike the 1-M-cycle LD r,r').
            Case("LD SP,HL") { b, r -> r.pc = 0xC000; r.hl = 0xD000; b.load(0xC000, 0xF9) },

            // DEC (HL): read-modify-write. old=0x10 gives a low-nibble borrow (H set) and N set — the two flag
            // bits that differ from INC. A "clean" value like 0x05 would leave H clear and hide a bad H formula.
            Case("DEC (HL)") { b, r -> r.pc = 0xC000; r.hl = 0xC100; b.poke(0xC100, 0x10); b.load(0xC000, 0x35) },  // 0x10 -> 0x0F, H+N set

            // ALU A,n: read immediate into Z, apply ALU to A in the same M-cycle. Inputs chosen to toggle H and C
            // (and the incoming carry for ADC/SBC), so a bad half-carry / carry-in formula is actually exercised.
            Case("ADD A,n H+C")  { b, r -> r.pc = 0xC000; r.a = 0xF8; b.load(0xC000, 0xC6, 0x0A) },                  // 0xF8+0x0A: H and C set
            Case("ADC A,n carry"){ b, r -> r.pc = 0xC000; r.a = 0x0F; r.flagC = true; b.load(0xC000, 0xCE, 0x00) },  // 0x0F+0+carry: H via carry-in
            Case("SUB n borrow") { b, r -> r.pc = 0xC000; r.a = 0x10; b.load(0xC000, 0xD6, 0x01) },                  // 0x10-0x01: H borrow
            Case("SBC n carry")  { b, r -> r.pc = 0xC000; r.a = 0x10; r.flagC = true; b.load(0xC000, 0xDE, 0x00) },  // 0x10-0-carry: H borrow via carry-in
            Case("AND n")        { b, r -> r.pc = 0xC000; r.a = 0xF0; b.load(0xC000, 0xE6, 0x0F) },                  // 0x00, Z set, H=true
            Case("XOR n")        { b, r -> r.pc = 0xC000; r.a = 0xFF; b.load(0xC000, 0xEE, 0x0F) },                  // 0xF0
            Case("OR n")         { b, r -> r.pc = 0xC000; r.a = 0xF0; b.load(0xC000, 0xF6, 0x0F) },                  // 0xFF
            Case("CP n equal")   { b, r -> r.pc = 0xC000; r.a = 0x42; b.load(0xC000, 0xFE, 0x42) },                  // A==n: Z set, A unchanged

            // ALU A,(HL): same as ALU A,n but the operand is read from [HL]. One representative per pitfall — the
            // AluZ dispatch itself is already covered by the immediate cases; here we just confirm the (HL) operand.
            Case("ADD A,(HL) H+C")  { b, r -> r.pc = 0xC000; r.hl = 0xC100; r.a = 0xF8; b.poke(0xC100, 0x0A); b.load(0xC000, 0x86) }, // H+C
            Case("ADC A,(HL) carry"){ b, r -> r.pc = 0xC000; r.hl = 0xC100; r.a = 0x0F; r.flagC = true; b.poke(0xC100, 0x00); b.load(0xC000, 0x8E) }, // carry-in
            Case("CP (HL) equal")   { b, r -> r.pc = 0xC000; r.hl = 0xC100; r.a = 0x42; b.poke(0xC100, 0x42); b.load(0xC000, 0xBE) }, // Z set, A unchanged

            // LD r,(HL) / LD (HL),r: single bus access, register selected by the opcode. 0x76 is HALT, not (HL),(HL).
            Case("LD B,(HL)") { b, r -> r.pc = 0xC000; r.hl = 0xC100; b.poke(0xC100, 0x77); b.load(0xC000, 0x46) },
            Case("LD (HL),B") { b, r -> r.pc = 0xC000; r.hl = 0xC100; r.b = 0x99; b.load(0xC000, 0x70) },

            // CB register path (reg != 6): effect applied inline in cbDecode, 2 M-cycles (prefix + CB fetch).
            // bit7=1 so RLC sets C, and the value is non-zero so Z is clear — exercises the flag output.
            Case("CB RLC B")      { b, r -> r.pc = 0xC000; r.b = 0x85; b.load(0xC000, 0xCB, 0x00) },

            // CB register BIT: sets Z/N/H, leaves C untouched, does NOT write the register back. Testing bit 7
            // of 0x7F (=0) sets Z; a spurious write-back would show as a changed register vs ReferenceCpu.
            Case("CB BIT 7,A")    { b, r -> r.pc = 0xC000; r.a = 0x7F; r.flagC = true; b.load(0xC000, 0xCB, 0x7F) },

            // CB (HL) read-modify-write: 4 M-cycles (prefix, CB fetch, read HL, write HL). RLC of 0x80 -> 0x01,
            // C set. Trace must show read then write at HL.
            Case("CB RLC (HL)")   { b, r -> r.pc = 0xC000; r.hl = 0xC100; b.poke(0xC100, 0x80); b.load(0xC000, 0xCB, 0x06) },

            // CB BIT n,(HL): the short (HL) branch — 3 M-cycles, read only, NO write-back. This is the case that
            // breaks if the group==GROUP_BIT bifurcation is wrong (a stray write would add an M-cycle + access).
            Case("CB BIT 0,(HL)") { b, r -> r.pc = 0xC000; r.hl = 0xC100; b.poke(0xC100, 0xFE); b.load(0xC000, 0xCB, 0x46) },

            // INC/DEC r: RegToZ -> incZ/decZ -> ZtoReg, entirely inside the fetch M-cycle (1 M-cycle, no bus
            // access). The Z/N/H formulas and "C untouched" are already proven by INC/DEC (HL); these cases add
            // the per-register mapping (a swapped RegToZ/ZtoReg pair only surfaces on its own register). Values
            // also probe the flag edges: the wrap cases start with flagC=false so a bogus C-out would diverge
            // from the reference; low-nibble 0xF (INC) / 0x0 (DEC) toggle H.
            Case("INC B") { b, r -> r.pc = 0xC000; r.b = 0xFF; r.flagC = false; b.load(0xC000, 0x04) }, // ->0x00: Z+H set, C stays clear
            Case("INC C") { b, r -> r.pc = 0xC000; r.c = 0x0F; r.flagC = true;  b.load(0xC000, 0x0C) }, // ->0x10: H set, C stays set
            Case("INC D") { b, r -> r.pc = 0xC000; r.d = 0x41; b.load(0xC000, 0x14) },                  // baseline (mapping)
            Case("INC E") { b, r -> r.pc = 0xC000; r.e = 0x41; b.load(0xC000, 0x1C) },                  // baseline (mapping)
            Case("INC H") { b, r -> r.pc = 0xC000; r.h = 0x41; b.load(0xC000, 0x24) },                  // baseline (mapping)
            Case("INC L") { b, r -> r.pc = 0xC000; r.l = 0x41; b.load(0xC000, 0x2C) },                  // baseline (mapping)
            Case("INC A") { b, r -> r.pc = 0xC000; r.a = 0xFF; r.flagC = false; b.load(0xC000, 0x3C) }, // ->0x00: Z+H set, C stays clear

            Case("DEC B") { b, r -> r.pc = 0xC000; r.b = 0x00; r.flagC = false; b.load(0xC000, 0x05) }, // ->0xFF: H+N set, C stays clear (borrow-out guard)
            Case("DEC C") { b, r -> r.pc = 0xC000; r.c = 0x01; b.load(0xC000, 0x0D) },                  // ->0x00: Z+N set, H clear
            Case("DEC D") { b, r -> r.pc = 0xC000; r.d = 0x10; b.load(0xC000, 0x15) },                  // ->0x0F: H+N set (low-nibble borrow)
            Case("DEC E") { b, r -> r.pc = 0xC000; r.e = 0x42; b.load(0xC000, 0x1D) },                  // baseline (mapping)
            Case("DEC H") { b, r -> r.pc = 0xC000; r.h = 0x42; b.load(0xC000, 0x25) },                  // baseline (mapping)
            Case("DEC L") { b, r -> r.pc = 0xC000; r.l = 0x42; b.load(0xC000, 0x2D) },                  // baseline (mapping)
            Case("DEC A") { b, r -> r.pc = 0xC000; r.a = 0x00; r.flagC = false; b.load(0xC000, 0x3D) }, // ->0xFF: H+N set, C stays clear
        )
    }
}
