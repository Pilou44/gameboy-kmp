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
        )
    }
}
