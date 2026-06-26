package com.wechantloup.gameboykmp.cpu.migration

import com.wechantloup.gameboykmp.cpu.Registers

enum class BusOp { READ, WRITE }

/**
 * One CPU bus access. mCycle = 0-based M-cycle index within the instruction. tWithinMCycle = T-cycle
 * of the access inside that M-cycle (0..3): constant 0 in the M-cycle-accurate phases (read-then-tick),
 * meaningful only once the access is placed at a real T (phase C), where this same format becomes the
 * lcdon/SameBoy oracle.
 */
data class BusAccess(
    val mCycle: Int,
    val tWithinMCycle: Int,
    val op: BusOp,
    val address: Int,
    val value: Int,
)

/** Full observable footprint of one executed instruction. */
data class InstructionTrace(
    val accesses: List<BusAccess>,
    val mCycleCount: Int,            // total M-cycles, internal (no-access) ones included
    val registers: RegisterSnapshot,
)

data class RegisterSnapshot(
    val a: Int, val f: Int, val b: Int, val c: Int,
    val d: Int, val e: Int, val h: Int, val l: Int,
    val pc: Int, val sp: Int,
)

fun Registers.snapshot() = RegisterSnapshot(a, f, b, c, d, e, h, l, pc, sp)
