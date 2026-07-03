package com.wechantloup.gameboykmp.helpers

import com.wechantloup.gameboykmp.apu.Apu
import com.wechantloup.gameboykmp.bus.Bus
import com.wechantloup.gameboykmp.cpu.Cpu
import com.wechantloup.gameboykmp.cpu.MachineMode
import com.wechantloup.gameboykmp.cpu.Registers
import com.wechantloup.gameboykmp.ppu.Ppu
import com.wechantloup.gameboykmp.timer.Timer

/**
 * Full Game Boy test harness, mirroring the real emulation loop.
 * All components are wired together exactly as in production.
 */
class GameBoyTestHarness(
    machineMode: MachineMode,
) {
    val cartridge = FakeCartridge()
    val bus = Bus(cartridge, machineMode, bootRom = null)
    val cpu = Cpu(bus, ::step1).also { it.reset() }
    val timer = Timer(bus)
    val ppu = Ppu(bus)
    val apu = Apu(bus)

    var totalCycles = 0
    private var cycleDebt = 0

    private var tCounter = 0

    private fun tickT() {
        cpu.tick()
        if (++tCounter % 4 == 0) {
            val ppuCycles = if (bus.isDoubleSpeed) 2 else 4
            ppu.step(ppuCycles)
            timer.step(4)          // always 4 — double speed doesn't slow the timer (§1)
            apu.step(ppuCycles)
            totalCycles += 4       // T CPU per M-cycle — the test clock, in T not dots
        }
    }

    /**
     * Run [n] full emulation steps.
     * Each step ticks all components in the same order as the production loop.
     */
    fun step(n: Int = 1) = repeat(n) {
        do { tickT() } while (!cpu.isAtInstructionBoundary)
    }

    // Advance until PC reaches [target] (or a safety cap trips), leaving totalCycles measurable across it.
    fun runUntilPc(target: Int, maxT: Int = 200_000) {
        var t = 0
        while (cpu.registers.pc != target) {
            tickT(); if (++t > maxT) error("runUntilPc: PC never reached ${target.toString(16)}")
        }
    }

    fun step1() {
        ppu.step(4)
        timer.step(4)
        apu.step(4)
        totalCycles += 4
    }

    fun stepCycles(targetCycles: Int) {
        val target = totalCycles + targetCycles - cycleDebt
        cycleDebt = 0
        while (totalCycles < target) {
            cpu.step()
        }
        cycleDebt = totalCycles - target
    }

    fun parkCpu() {
        // Infinite loop at current PC: JR -2 (0x18, 0xFE)
        val pc = cpu.registers.pc
        rom(pc, 0x18, 0xFE)
    }
}

/**
 * DSL entry point. Creates a harness, applies [block] to configure initial state,
 * and returns it ready to run.
 *
 * Usage:
 *   val h = gameBoyTest {
 *       registers { a = 0x01; pc = 0x0100 }
 *       rom(0x0100, 0x3C)  // INC A
 *   }
 *   h.step()
 *   assertEquals(0x02, h.cpu.registers.a)
 */
fun gameBoyTest(block: GameBoyTestHarness.() -> Unit): GameBoyTestHarness {
    return GameBoyTestHarness(machineMode = MachineMode.DMG).apply(block)
}

/** Configure CPU registers in the DSL. */
fun GameBoyTestHarness.registers(block: Registers.() -> Unit) {
    cpu.registers.apply(block)
}

/**
 * Inject bytes into ROM at [address].
 * Set PC to [address] in the registers block to execute them.
 */
fun GameBoyTestHarness.rom(address: Int, vararg bytes: Int) {
    cartridge.loadRom(address, *bytes)
}

/**
 * Write bytes into Work RAM (0xC000–0xDFFF).
 * Useful to set up data that the program will read.
 */
fun GameBoyTestHarness.wram(address: Int, vararg bytes: Int) {
    bytes.forEachIndexed { i, b -> bus.write(address + i, b) }
}
