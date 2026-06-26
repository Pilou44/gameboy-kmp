package com.wechantloup.gameboykmp.cpu.migration

import com.wechantloup.gameboykmp.cpu.MachineMode
import com.wechantloup.gameboykmp.cpu.CpuBus

/**
 * Flat 64 KB bus for CPU-sequencing tests. Every read/write is logged with the current M-cycle index;
 * the CPU's machine-cycle callback (onMCycleTick) advances that index, so a tick with no access marks
 * an internal M-cycle. Interrupt-line samples (ie/iF) are NOT logged — they are control samples, not
 * addressed instruction accesses — and stay 0 so no interrupt fires mid-trace. No PPU/APU/DMA: this
 * bus only captures the order, addresses, values and M-cycle count of instruction bus traffic.
 */
class TracingTestBus(
    override val machineMode: MachineMode = MachineMode.DMG,
    override val bootRom: ByteArray? = null,
) : CpuBus {

    private val mem = IntArray(0x10000)
    val trace = mutableListOf<BusAccess>()
    var mCycle = 0
        private set

    /** Pass as the CPU's onMachineCycleTick. One call = one elapsed M-cycle. */
    fun onMCycleTick() { mCycle++ }

    override fun read(address: Int): Int {
        val a = address and 0xFFFF
        val v = mem[a]
        trace += BusAccess(mCycle, 0, BusOp.READ, a, v)   // TODO phase C: real T instead of 0
        return v
    }

    override fun write(address: Int, value: Int) {
        val a = address and 0xFFFF
        val v = value and 0xFF
        mem[a] = v
        trace += BusAccess(mCycle, 0, BusOp.WRITE, a, v)  // TODO phase C: real T instead of 0
    }

    override val ie: Int get() = mem[0xFFFF]
    override val iF: Int get() = mem[0xFF0F]
    override fun setIF(value: Int) { mem[0xFF0F] = value and 0xFF }
    override var cpuHalted: Boolean = false
    override var pendingGdmaStallMCycles: Int = 0
    override fun performSpeedSwitch(): Boolean = false

    // Fixture helpers — write directly, never traced.
    fun load(address: Int, vararg bytes: Int) =
        bytes.forEachIndexed { i, b -> mem[(address + i) and 0xFFFF] = b and 0xFF }
    fun poke(address: Int, value: Int) { mem[address and 0xFFFF] = value and 0xFF }

    /** Start tracing from a clean slate, after fixture setup. */
    fun beginTrace() { trace.clear(); mCycle = 0 }
}
