package com.wechantloup.gameboykmp.blarrgtests.helpers

import com.wechantloup.gameboykmp.apu.Apu
import com.wechantloup.gameboykmp.bus.Bus
import com.wechantloup.gameboykmp.cpu.Cpu
import com.wechantloup.gameboykmp.cpu.Registers
import com.wechantloup.gameboykmp.ppu.Ppu
import com.wechantloup.gameboykmp.timer.Timer

/**
 * Full Game Boy test harness, mirroring the real emulation loop.
 * All components are wired together exactly as in production.
 */
class GameBoyTestHarness {
    val cartridge = FakeCartridge()
    val bus = Bus(cartridge)
    val cpu = Cpu(bus).also { it.reset() }
    val timer = Timer(bus)
    val ppu = Ppu(bus)
    val apu = Apu(bus)

    /**
     * Run [n] full emulation steps.
     * Each step ticks all components in the same order as the production loop.
     */
    fun step(n: Int = 1) {
        repeat(n) {
//            println(cpu.registers.pc)
            val cycles = cpu.step()
            ppu.step(cycles)
            apu.step(cycles)
            timer.step(cycles)
        }
    }

//    fun stepCycles(targetCycles: Int) {
//        var elapsed = 0
//        while (elapsed < targetCycles) {
////            println("PC = 0x${cpu.registers.pc.toString(16)}")
//            val cycles = cpu.step()
//            ppu.step(cycles)
//            apu.step(cycles)
//            timer.step(cycles)
//            elapsed += cycles
//        }
//    }

    private var cycleDebt = 0

    fun stepCycles(targetCycles: Int) {
        var elapsed = -cycleDebt
        cycleDebt = 0
        while (elapsed < targetCycles) {
            val cycles = cpu.step()
            ppu.step(cycles)
            apu.step(cycles)
            timer.step(cycles)
            elapsed += cycles
        }
        cycleDebt = elapsed - targetCycles
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
    return GameBoyTestHarness().apply(block)
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
