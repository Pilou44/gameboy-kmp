package com.wechantloup.gameboykmp.cpu

/**
 * The bus surface the CPU actually depends on. Extracting it gives the CPU-sequencing harness
 * a seam: the real Bus implements it unchanged, while tests supply a flat, tracing bus with no
 * PPU/APU/DMA side effects. Neutral refactor — Bus already exposes every member below.
 */
interface CpuBus {
    val machineMode: MachineMode
    val bootRom: ByteArray?

    val ie: Int
    val iF: Int
    var cpuHalted: Boolean
    var pendingGdmaStallMCycles: Int

    fun read(address: Int): Int
    fun write(address: Int, value: Int)
    fun setIF(value: Int)
    fun performSpeedSwitch(): Boolean
}
