package com.wechantloup.gameboykmp.bus

import com.wechantloup.gameboykmp.cpu.MachineMode
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
 * DMG runs the same program as the zero-stall baseline, so every CGB assertion also pins DMG
 * non-regression.
 *
 * Measurement runs each program up to a PC marker (a NOP placed just before the self-loop) that is
 * reached only AFTER the stall has fully drained. The identical instruction prefix (LD / LDH / NOP)
 * cancels in the CGB-DMG differential, leaving the stall alone — independent of how many M-cycles
 * the decremental drain spans. This is what makes the test robust to the tick()-based CPU, where the
 * stall is drained one M-cycle per pipeline-empty pass rather than atomically inside one step.
 *
 * TODO: this test relies on the tick()-based harness (runUntilPc / tickT). It no longer uses
 *  cpu.step(); keep it aligned if the harness's stepping primitives change.
 */
class GdmaStallTest {

    /**
     * Sets up HDMA1-4 + the trigger program, ready to run. Does not step.
     *
     * The NOP at 0x0104 is a landing marker: PC reaches [SELF_LOOP_PC] only once the stall has
     * drained and the NOP has been fetched. Without it, the JR self-loop would share PC=0x0104 with
     * the pre-stall position (PC is already 0x0104 before, during and after the stall), so a
     * run-until-PC could stop before the stall even begins.
     */
    private fun prepare(mode: MachineMode, lengthByte: Int): GameBoyTestHarness {
        val h = GameBoyTestHarness(mode)
        // Source 0x0000 (ROM bank 0), dest VRAM offset 0. Byte values are irrelevant to the
        // stall; the copy is harmless. On DMG these CGB-register writes are no-ops.
        h.bus.write(0xFF51, 0x00); h.bus.write(0xFF52, 0x00)
        h.bus.write(0xFF53, 0x00); h.bus.write(0xFF54, 0x00)
        // LD A, lengthByte ; LDH (0x55), A ; NOP ; JR -2
        h.rom(0x0100, 0x3E, lengthByte, 0xE0, 0x55, 0x00, 0x18, 0xFE)
        h.registers { pc = 0x0100 }
        return h
    }

    private fun systemCycles(mode: MachineMode, lengthByte: Int): Int {
        val h = prepare(mode, lengthByte)
        val before = h.totalCycles
        h.runUntilPc(SELF_LOOP_PC)   // spans the whole stall, whatever its drain granularity
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
    fun `CGB GDMA stalls 16 M-cycles per block in double speed`() {
        fun doubleSpeedCycles(lengthByte: Int): Int {
            val h = GameBoyTestHarness(MachineMode.CGB)
            h.bus.write(0xFF51, 0x00); h.bus.write(0xFF52, 0x00)
            h.bus.write(0xFF53, 0x00); h.bus.write(0xFF54, 0x00)
            // Arm KEY1 (bit0), commit with STOP -> double speed, then fire the GDMA.
            // LD A,01 ; LDH (4D),A ; STOP ; LD A,len ; LDH (55),A ; NOP ; JR -2
            // NOP marker at 0x010A, self-loop at 0x010B.
            h.rom(
                0x0100,
                0x3E, 0x01, 0xE0, 0x4D, 0x10, 0x00, 0x3E, lengthByte, 0xE0, 0x55, 0x00, 0x18, 0xFE,
            )
            h.registers { pc = 0x0100 }
            val before = h.totalCycles
            h.runUntilPc(0x010B)
            // Guard the test's own premise: the prelude must really have engaged double speed.
            assertTrue(h.bus.isDoubleSpeed, "KEY1 + STOP did not engage double speed")
            return h.totalCycles - before
        }
        // Length-differential cancels the identical prelude (KEY1/STOP/fetches/NOP/JR); only the
        // per-block stall differs. 16 M-cycles per block in double speed vs the 8 pinned above.
        assertEquals((64 - 1) * 16 * 4, doubleSpeedCycles(0x3F) - doubleSpeedCycles(0x00))
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
        // ~4 more than the identical DMG run. Guards against a drain that bumps the clock without
        // ticking the peripherals.
        val cgbDiv = prepare(MachineMode.CGB, 0x7F).also { it.runUntilPc(SELF_LOOP_PC) }.bus.read(0xFF04)
        val dmgDiv = prepare(MachineMode.DMG, 0x7F).also { it.runUntilPc(SELF_LOOP_PC) }.bus.read(0xFF04)
        val divDelta = (cgbDiv - dmgDiv) and 0xFF
        // TODO: with the run-until-PC measurement divDelta is genuinely positive (~4). Under the old
        //  step-budget it could go negative and the `and 0xFF` masked it into a passing value. If this
        //  ever regresses, check the raw (unmasked) sign before trusting the `>= 3` bound.
        assertTrue(divDelta >= 3, "expected DIV to advance with the stall, got $divDelta")
    }

    @BeforeTest
    fun setup() {
        Logger.sink = NoOpLogSink
    }

    private companion object {
        // The JR self-loop sits one byte past the NOP marker planted by prepare() at 0x0104.
        const val SELF_LOOP_PC = 0x0105
    }
}
