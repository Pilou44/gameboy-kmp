package com.wechantloup.gameboykmp.cpu

/**
 * CPU micro-operations: the explicit, per-M-cycle steps an instruction is made of. Each MicroOp is an
 * immutable, shared value — built once into TABLE, never allocated per execution. Bus accesses are
 * typed data (ReadImmediate / ReadMem / WriteMem) so phase C can place their access at a configurable
 * T-cycle centrally, without touching any opcode. Internal effects (ALU, flags, decisions) are
 * capture-free functions (Cpu) -> Unit — also built once, also zero-alloc at runtime.
 *
 * Execution state (the byte just read, an address being built) does NOT live in a MicroOp; it lives in
 * the CPU's WZ latches, so MicroOps stay stateless and shareable.
 */

/** Internal 8-bit latches (the micro-coded core's "WZ"): carry data between M-cycles of one instruction. */
enum class Latch { W, Z }

/** 8-bit value sources for a bus write: a real register or a latch. */
enum class Src8 { A, B, C, D, E, H, L, W, Z }

/** 16-bit address sources for a memory access. */
enum class Addr16 { BC, DE, HL, SP, WZ }   // TODO extend (HRAM via C, immediate nn) as opcodes need them

sealed interface MicroOp {

    /** Read [PC], then PC++ — the immediate-fetch shape — storing the byte into a latch. */
    data class ReadImmediate(val into: Latch) : MicroOp

    /** Read [addr] into a latch. TODO pointer effects (HL+/HL-) when LDI/LDD are migrated. */
    data class ReadMem(val addr: Addr16, val into: Latch) : MicroOp

    /** Write [addr] <- value. TODO pre-decrement (push) / post-effects when stack ops are migrated. */
    data class WriteMem(val addr: Addr16, val value: Src8) : MicroOp

    /**
     * A pure internal M-cycle: no bus access, just an effect on CPU state. [effect] is capture-free
     * (acts via its Cpu parameter), built once, shared — so it costs no allocation per execution.
     */
    class Internal(val effect: (Cpu) -> Unit) : MicroOp   // TODO used once ADD HL,rr / CALL etc. migrate
}
