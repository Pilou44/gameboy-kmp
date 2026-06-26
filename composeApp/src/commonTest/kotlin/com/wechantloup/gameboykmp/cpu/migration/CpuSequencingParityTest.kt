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
            Case("JR taken")       { b, r -> r.pc = 0xC000; b.load(0xC000, 0x18, 0x05) },
            Case("JR NZ not taken"){ b, r -> r.pc = 0xC000; r.flagZ = true; b.load(0xC000, 0x20, 0x05) },
            Case("ADD HL,DE")      { b, r -> r.pc = 0xC000; r.hl = 0x0FFF; r.de = 0x0001; b.load(0xC000, 0x19) },
        )
    }
}
