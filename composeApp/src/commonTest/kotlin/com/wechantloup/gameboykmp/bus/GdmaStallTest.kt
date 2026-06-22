package com.wechantloup.gameboykmp.bus

import com.wechantloup.gameboykmp.MachineMode
import com.wechantloup.gameboykmp.helpers.GameBoyTestHarness
import com.wechantloup.gameboykmp.helpers.registers
import com.wechantloup.gameboykmp.helpers.rom
import com.wechantloup.gameboykmp.logger.Logger
import com.wechantloup.gameboykmp.logger.NoOpLogSink
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * GDMA CPU stall contract.
 *
 * Behavioural only: we assert on the system clock (totalCycles), never on internal stall state.
 * DMG runs the exact same program and acts as the zero-stall baseline, so every CGB assertion
 * also pins DMG non-regression. The stall, if any, is drained inside the LDH instruction, so it
 * is captured within a fixed step budget regardless of its exact drain site; running the SAME
 * number of steps everywhere makes instruction + self-loop cost cancel in any differential.
 */
class GdmaStallTest {

    /** Sets up HDMA1-4 + the trigger program, ready to run. Does not step. */
    private fun prepare(mode: MachineMode, lengthByte: Int): GameBoyTestHarness {
        val h = GameBoyTestHarness(mode)
        // Source 0x0000 (ROM bank 0), dest VRAM offset 0. Byte values are irrelevant to the
        // stall; the copy is harmless. On DMG these CGB-register writes are no-ops.
        h.bus.write(0xFF51, 0x00); h.bus.write(0xFF52, 0x00)
        h.bus.write(0xFF53, 0x00); h.bus.write(0xFF54, 0x00)
        // LD A, lengthByte ; LDH (0x55), A ; JR -2 (self-loop absorbs the remaining steps)
        h.rom(0x0100, 0x3E, lengthByte, 0xE0, 0x55, 0x18, 0xFE)
        h.registers { pc = 0x0100 }
        return h
    }

    private fun systemCycles(mode: MachineMode, lengthByte: Int, steps: Int = 8): Int {
        val h = prepare(mode, lengthByte)
        val before = h.totalCycles
        h.step(steps)
        return h.totalCycles - before
    }

    private fun blocksFor(lengthByte: Int) = (lengthByte and 0x7F) + 1
    // 8 M-cycles per 0x10-byte block, normal speed; harness ticks 4 T-cycles per M-cycle.
    private fun expectedStallT(blocks: Int) = blocks * 8 * 4

    @Test
    fun `DMG ignores FF55 so length never changes timing`() {
        assertEquals(
            systemCycles(MachineMode.DMG, 0x00),
            systemCycles(MachineMode.DMG, 0x7F),
        )
    }

    @Test
    fun `CGB GDMA stalls 8 M-cycles per block in single speed`() {
        // Covers 1 block (min) up to 128 blocks (max) — guards the (length + 1) off-by-one.
        for (lengthByte in listOf(0x00, 0x0F, 0x3F, 0x7F)) {
            val stall = systemCycles(MachineMode.CGB, lengthByte) -
                    systemCycles(MachineMode.DMG, lengthByte)
            assertEquals(expectedStallT(blocksFor(lengthByte)), stall, "lengthByte=$lengthByte")
        }
    }

    @Test
    fun `CGB GDMA stall is linear in block count`() {
        val oneBlock = systemCycles(MachineMode.CGB, 0x00)
        val sixtyFour = systemCycles(MachineMode.CGB, 0x3F)
        assertEquals(expectedStallT(64 - 1), sixtyFour - oneBlock)
    }

    @Test
    fun `CGB_COMPAT stalls too because gating is machineMode != DMG`() {
        val stall = systemCycles(MachineMode.CGB_COMPAT, 0x00) -
                systemCycles(MachineMode.DMG, 0x00)
        assertEquals(expectedStallT(1), stall)
    }

    @Test
    fun `HBlank DMA does not pay the GDMA full-transfer stall`() {
        // bit7 = 1 -> HBlank DMA (4 blocks here). Its per-block stall is a separate concern;
        // it must NOT incur the whole-transfer GDMA stall.
        val hblank = 0x83
        val stall = systemCycles(MachineMode.CGB, hblank) - systemCycles(MachineMode.DMG, hblank)
        assertEquals(0, stall)
    }

    @Test
    fun `the stall advances the timer, not just the clock`() {
        // 0x7F = 128 blocks = 1024 stall M-cycles -> DIV (one tick per 256 M-cycles) advances
        // ~4 more than the identical DMG run. Guards against a drain that bumps the counter
        // without ticking the peripherals.
        val cgbDiv = prepare(MachineMode.CGB, 0x7F).also { it.step(8) }.bus.read(0xFF04)
        val dmgDiv = prepare(MachineMode.DMG, 0x7F).also { it.step(8) }.bus.read(0xFF04)
        val divDelta = (cgbDiv - dmgDiv) and 0xFF
        assertTrue(divDelta >= 3, "expected DIV to advance with the stall, got $divDelta")
    }

    @BeforeTest
    fun setup() {
        Logger.sink = NoOpLogSink
    }

}
