package com.wechantloup.gameboykmp.cpu

/**
 * One T-cycle of CPU work. Strict rule: 1 MicroOp = 1 T-cycle, no exception — Idle and Internal
 * consume their T exactly like a bus access. Instances are immutable, stateless and built once
 * (shared, never allocated per execution); execution state in flight (a byte just read, an address
 * being assembled) lives in the CPU's WZ latches, NOT here.
 *
 * Bus accesses are typed DATA (Read/Write describe what, not how) so phase C can place their access
 * at the right T-cycle centrally, without touching any opcode's sequence. Internal effects are the
 * only code-carrying variant, and the lambda is capture-free (acts via its Cpu argument).
 */
sealed interface MicroOp {

    /** A T-cycle with no bus access and no effect: pure padding within an M-cycle. */
    data object Idle : MicroOp

    /** Read [PC], PC++, store into a latch. The shared opcode/operand fetch shape. */
    data class ReadImmediate(val into: Latch) : MicroOp

    /** Read [addr] into a latch. */
    data class ReadMem(val addr: Addr16, val into: Latch) : MicroOp

    /** Write value -> [addr]. */
    data class WriteMem(val addr: Addr16, val value: Src8) : MicroOp

    /**
     * A T-cycle of internal work: no bus access, just an effect on CPU state (ALU, flags, a taken/
     * not-taken decision, a latch combine). [effect] is capture-free — it reads/writes only through
     * its Cpu parameter — so the instance is built once and shared.
     */
    class Internal(val effect: (Cpu) -> Unit) : MicroOp
}

/** Internal 8-bit latches — the micro-coded core's "WZ" — carrying data between T-cycles of one instruction. */
enum class Latch { W, Z }

/** 16-bit address sources for a memory access. */
enum class Addr16 { BC, DE, HL, SP, WZ }   // TODO add immediate-nn / 0xFF00+C as opcodes need them

/** 8-bit value sources for a write. */
enum class Src8 { A, B, C, D, E, H, L, W, Z }
