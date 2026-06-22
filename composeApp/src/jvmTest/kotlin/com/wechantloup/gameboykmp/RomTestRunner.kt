package com.wechantloup.gameboykmp

import com.wechantloup.gameboykmp.joypad.JoypadButton
import com.wechantloup.gameboykmp.joypad.JoypadEvent
import com.wechantloup.gameboykmp.logger.Logger
import com.wechantloup.gameboykmp.serializer.deserialize
import com.wechantloup.gameboykmp.serializer.serialize
import com.wechantloup.gameboykmp.ui.GameBoyViewModel
import com.wechantloup.gameboykmp.ui.Palette
import com.wechantloup.gameboykmp.utils.AllTestRun
import com.wechantloup.gameboykmp.utils.TestRun
import com.wechantloup.gameboykmp.utils.TestStatus
import javax.imageio.ImageIO
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.TestMethodOrder
import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.test.assertNotNull
import kotlin.test.fail

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class RomTestRunner {
    @Test
    @Order(1)
    fun `acid-which`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "acid/which.gb", 200)
    }

    @Test
    @Order(2)
    fun `acid-dmg-acid2`(testInfo: TestInfo) {
        runTest(
            testName = testInfo.displayName,
            romPath = "acid/dmg-acid2.gb",
            duration = 200,
            machineMode = MachineMode.DMG,
        )
    }

    @Test
    @Order(2)
    fun `acid-dmg-acid2_cgb`(testInfo: TestInfo) {
        runTest(
            testName = testInfo.displayName,
            romPath = "acid/dmg-acid2.gb",
            duration = 200,
            captureNameSuffix = "_cgb",
            machineMode = MachineMode.CGB_COMPAT,
        )
    }

    @Test
    @Order(2)
    fun `acid-cgb-acid2`(testInfo: TestInfo) {
        runTest(
            testName = testInfo.displayName,
            romPath = "acid/cgb-acid2.gbc",
            duration = 500,
        )
    }

    // ToDo T-cycle precision is mandatory for this one
//    @Test
//    @Order(2)
//    fun `acid-cgb-acid-hell`(testInfo: TestInfo) {
//        runTest(
//            testName = testInfo.displayName,
//            romPath = "acid/cgb-acid-hell.gbc",
//            duration = 500,
//        )
//    }

    @Test
    @Order(3)
    fun `blarrg-cpu_instrs-01`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/cpu_instrs/01-special.gb", 3_000)
    }

    @Test
    @Order(4)
    fun `blarrg-cpu_instrs-02`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/cpu_instrs/02-interrupts.gb", 1_000)
    }

    @Test
    @Order(5)
    fun `blarrg-cpu_instrs-03`(testInfo: TestInfo) {
        runTest(
            testName = testInfo.displayName,
            romPath = "blarrg/cpu_instrs/03-op_sp,hl.gb",
            duration = 3_000,
            machineMode = MachineMode.DMG,
        )
    }

    @Test
    @Order(6)
    fun `blarrg-cpu_instrs-04`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/cpu_instrs/04-op_r,imm.gb", 3_000)
    }

    @Test
    @Order(7)
    fun `blarrg-cpu_instrs-05`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/cpu_instrs/05-op_rp.gb", 4_000)
    }

    @Test
    @Order(8)
    fun `blarrg-cpu_instrs-06`(testInfo: TestInfo) {
        runTest(
            testName = testInfo.displayName,
            romPath = "blarrg/cpu_instrs/06-ld_r,r.gb",
            duration = 1_000,
            machineMode = MachineMode.DMG,
        )
    }

    @Test
    @Order(9)
    fun `blarrg-cpu_instrs-07`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/cpu_instrs/07-jr,jp,call,ret,rst.gb", 1_000)
    }

    @Test
    @Order(10)
    fun `blarrg-cpu_instrs-08`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/cpu_instrs/08-misc_instrs.gb", 1_000)
    }

    @Test
    @Order(11)
    fun `blarrg-cpu_instrs-09`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/cpu_instrs/09-op_r,r.gb", 10_000)
    }

    @Test
    @Order(12)
    fun `blarrg-cpu_instrs-10`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/cpu_instrs/10-bit_ops.gb", 14_000)
    }

    @Test
    @Order(13)
    fun `blarrg-cpu_instrs-11`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/cpu_instrs/11-op_a,(hl).gb", 18_000)
    }

    @Test
    @Order(14)
    fun `blarrg-halt_bug`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/halt_bug.gb", 2_000)
    }

    @Test
    @Order(15)
    fun `blarrg-instr_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/instr_timing.gb", 1_000)
    }

    @Test
    @Order(16)
    fun `blarrg-interrupt_time`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/interrupt_time.gb", 1_000)
    }

    @Test
    @Order(17)
    fun `blarrg-mem_timing-01-read_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/mem_timing/01-read_timing.gb", 500)
    }

    @Test
    @Order(18)
    fun `blarrg-mem_timing-02-write_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/mem_timing/02-write_timing.gb", 500)
    }

    @Test
    @Order(19)
    fun `blarrg-mem_timing-03-modify_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/mem_timing/03-modify_timing.gb", 1_000)
    }

    @Test
    @Order(20)
    fun `blarrg-mem_timing-2-01-read_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/mem_timing-2/01-read_timing.gb", 500)
    }

    @Test
    @Order(21)
    fun `blarrg-mem_timing-2-02-write_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/mem_timing-2/02-write_timing.gb", 500)
    }

    @Test
    @Order(22)
    fun `blarrg-mem_timing-2-03-modify_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/mem_timing-2/03-modify_timing.gb", 1_000)
    }

    @Test
    @Order(23)
    fun `blarrg-oam_bug-1-lcd_sync`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/oam_bug/1-lcd_sync.gb", 1_000)
    }

    @Test
    @Order(24)
    fun `blarrg-oam_bug-2-causes`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/oam_bug/2-causes.gb", 1_000)
    }

    @Test
    @Order(25)
    fun `blarrg-oam_bug-3-non_causes`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/oam_bug/3-non_causes.gb", 2_000)
    }

    @Test
    @Order(26)
    fun `blarrg-oam_bug-4-scanline_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/oam_bug/4-scanline_timing.gb", 1_000)
    }

    @Test
    @Order(27)
    fun `blarrg-oam_bug-5-timing_bug`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/oam_bug/5-timing_bug.gb", 1_000)
    }

    @Test
    @Order(28)
    fun `blarrg-oam_bug-6-timing_no_bug`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/oam_bug/6-timing_no_bug.gb", 2_000)
    }

    @Test
    @Order(29)
    fun `blarrg-oam_bug-7-timing_effect`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/oam_bug/7-timing_effect.gb", 8_000)
    }

    @Test
    @Order(30)
    fun `blarrg-oam_bug-8-instr_effect`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/oam_bug/8-instr_effect.gb", 1_000)
    }

    @Test
    @Order(31)
    fun `blarrg-dmg_sound-01-registers`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/dmg_sound/01-registers.gb", 1_000)
    }

    @Test
    @Order(32)
    fun `blarrg-dmg_sound-02-len_ctr`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/dmg_sound/02-len_ctr.gb", 10_000)
    }

    @Test
    @Order(33)
    fun `blarrg-dmg_sound-03-trigger`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/dmg_sound/03-trigger.gb", 17_000)
    }

    @Test
    @Order(34)
    fun `blarrg-dmg_sound-04-sweep`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/dmg_sound/04-sweep.gb", 2_000)
    }

    @Test
    @Order(35)
    fun `blarrg-dmg_sound-05-sweep_details`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/dmg_sound/05-sweep_details.gb", 2_000)
    }

    @Test
    @Order(36)
    fun `blarrg-dmg_sound-06-overflow_on_trigger`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/dmg_sound/06-overflow_on_trigger.gb", 1_000)
    }

    @Test
    @Order(37)
    fun `blarrg-dmg_sound-07-len_sweep_period_sync`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/dmg_sound/07-len_sweep_period_sync.gb", 1_000)
    }

    @Test
    @Order(38)
    fun `blarrg-dmg_sound-08-len_ctr_during_power`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/dmg_sound/08-len_ctr_during_power.gb", 2_000)
    }

    @Test
    @Order(39)
    fun `blarrg-dmg_sound-09-wave_read_while_on`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/dmg_sound/09-wave_read_while_on.gb", 1_000)
    }

    @Test
    @Order(40)
    fun `blarrg-dmg_sound-10-wave_trigger_while_on`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/dmg_sound/10-wave_trigger_while_on.gb", 4_000)
    }

    @Test
    @Order(41)
    fun `blarrg-dmg_sound-11-regs_after_power`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/dmg_sound/11-regs_after_power.gb", 1_000)
    }

    @Test
    @Order(42)
    fun `blarrg-dmg_sound-12-wave_write_while_on`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "blarrg/dmg_sound/12-wave_write_while_on.gb", 4_000)
    }

    @Test
    @Order(43)
    fun `daid-ppu_scanline_bgp_dmg`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "daid/ppu_scanline_bgp.gb", 500, captureNameSuffix = "_0.dmg")
    }

    @Test
    @Order(43)
    fun `daid-ppu_scanline_bgp_cgb`(testInfo: TestInfo) {
        runTest(
            testName = testInfo.displayName,
            romPath = "daid/ppu_scanline_bgp.gb",
            duration = 3500,
            captureNameSuffix = ".gbc",
            machineMode = MachineMode.CGB_COMPAT,
            skipBoot = false,
        )
    }

    @Test
    @Order(44)
    fun `daid-stop_instr_dmg`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "daid/stop_instr.gb", 500, captureNameSuffix = ".dmg")
    }

    @Test
    @Order(44)
    fun `daid-stop_instr_cgb`(testInfo: TestInfo) {
        runTest(
            testName = testInfo.displayName,
            romPath = "daid/stop_instr.gb",
            duration = 500,
            captureNameSuffix = ".cgb",
            machineMode = MachineMode.CGB_COMPAT,
        )
    }

    @Test
    @Order(45)
    fun `ax6-rtc3test-basic_tests`(testInfo: TestInfo) {
        runTest(
            testName = testInfo.displayName,
            romPath = "ax6/rtc3test.gb",
            duration = 13_000,
            commands = listOf(
                JoypadEvent.Pressed(JoypadButton.A),
                JoypadEvent.Released(JoypadButton.A),
            ),
            captureNameSuffix = "_basic_tests",
            machineMode = MachineMode.DMG,
        )
    }

    @Test
    @Order(46)
    fun `ax6-rtc3test-range_tests`(testInfo: TestInfo) {
        runTest(
            testName = testInfo.displayName,
            romPath = "ax6/rtc3test.gb",
            duration = 9_000,
            commands = listOf(
                JoypadEvent.Pressed(JoypadButton.DOWN),
                JoypadEvent.Released(JoypadButton.DOWN),
                JoypadEvent.Pressed(JoypadButton.A),
                JoypadEvent.Released(JoypadButton.A),
            ),
            captureNameSuffix = "_range_tests",
            machineMode = MachineMode.DMG,
        )
    }

    @Test
    @Order(47)
    fun `ax6-rtc3test-sub-second_writes`(testInfo: TestInfo) {
        runTest(
            testName = testInfo.displayName,
            romPath = "ax6/rtc3test.gb",
            duration = 26_000,
            commands = listOf(
                JoypadEvent.Pressed(JoypadButton.DOWN),
                JoypadEvent.Released(JoypadButton.DOWN),
                JoypadEvent.Pressed(JoypadButton.DOWN),
                JoypadEvent.Released(JoypadButton.DOWN),
                JoypadEvent.Pressed(JoypadButton.A),
                JoypadEvent.Released(JoypadButton.A),
            ),
            captureNameSuffix = "_sub-second_writes",
            machineMode = MachineMode.DMG,
        )
    }

    @Test
    @Order(48)
    fun `mooneye-acceptance-add_sp_e_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/add_sp_e_timing.gb", 1_000)
    }

    @Test
    @Order(49)
    fun `mooneye-acceptance-bits-mem_oam`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/bits/mem_oam.gb", 1_000)
    }

    @Test
    @Order(50)
    fun `mooneye-acceptance-bits-reg_f`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/bits/reg_f.gb", 1_000)
    }

    @Test
    @Order(51)
    fun `mooneye-acceptance-bits-unused_hwio-GS`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/bits/unused_hwio-GS.gb", 1_000)
    }

    @Test
    @Order(52)
    fun `mooneye-acceptance-boot_div-dmgABCmgb`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/boot_div-dmgABCmgb.gb", 1_000)
    }

    @Test
    @Order(53)
    fun `mooneye-acceptance-boot_hwio-dmgABCmgb`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/boot_hwio-dmgABCmgb.gb", 1_000)
    }

    @Test
    @Order(54)
    fun `mooneye-acceptance-boot_regs-dmgABC`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/boot_regs-dmgABC.gb", 1_000)
    }

    @Test
    @Order(55)
    fun `mooneye-acceptance-call_cc_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/call_cc_timing.gb", 1_000)
    }

    @Test
    @Order(56)
    fun `mooneye-acceptance-call_cc_timing2`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/call_cc_timing2.gb", 1_000)
    }

    @Test
    @Order(57)
    fun `mooneye-acceptance-call_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/call_timing.gb", 1_000)
    }

    @Test
    @Order(58)
    fun `mooneye-acceptance-call_timing2`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/call_timing2.gb", 1_000)
    }

    @Test
    @Order(59)
    fun `mooneye-acceptance-div_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/div_timing.gb", 1_000)
    }

    @Test
    @Order(60)
    fun `mooneye-acceptance-di_timing-GS`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/di_timing-GS.gb", 1_000)
    }

    @Test
    @Order(61)
    fun `mooneye-acceptance-ei_sequence`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/ei_sequence.gb", 1_000)
    }

    @Test
    @Order(62)
    fun `mooneye-acceptance-ei_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/ei_timing.gb", 1_000)
    }

    @Test
    @Order(63)
    fun `mooneye-acceptance-halt_ime0_ei`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/halt_ime0_ei.gb", 1_000)
    }

    @Test
    @Order(64)
    fun `mooneye-acceptance-halt_ime0_nointr_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/halt_ime0_nointr_timing.gb", 1_000)
    }

    @Test
    @Order(65)
    fun `mooneye-acceptance-halt_ime1_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/halt_ime1_timing.gb", 1_000)
    }

    @Test
    @Order(66)
    fun `mooneye-acceptance-halt_ime1_timing2-GS`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/halt_ime1_timing2-GS.gb", 1_000)
    }

    @Test
    @Order(67)
    fun `mooneye-acceptance-if_ie_registers`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/if_ie_registers.gb", 1_000)
    }

    @Test
    @Order(68)
    fun `mooneye-acceptance-instr-daa`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/instr/daa.gb", 1_000)
    }

    @Test
    @Order(69)
    fun `mooneye-acceptance-interrupts-ie_push`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/interrupts/ie_push.gb", 1_000)
    }

    @Test
    @Order(70)
    fun `mooneye-acceptance-intr_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/intr_timing.gb", 1_000)
    }

    @Test
    @Order(71)
    fun `mooneye-acceptance-jp_cc_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/jp_cc_timing.gb", 1_000)
    }

    @Test
    @Order(72)
    fun `mooneye-acceptance-jp_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/jp_timing.gb", 1_000)
    }

    @Test
    @Order(73)
    fun `mooneye-acceptance-ld_hl_sp_e_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/ld_hl_sp_e_timing.gb", 1_000)
    }

    @Test
    @Order(74)
    fun `mooneye-acceptance-oam_dma-basic`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/oam_dma/basic.gb", 1_000)
    }

    @Test
    @Order(75)
    fun `mooneye-acceptance-oam_dma-reg_read`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/oam_dma/reg_read.gb", 1_000)
    }

    @Test
    @Order(76)
    fun `mooneye-acceptance-oam_dma-sources-GS`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/oam_dma/sources-GS.gb", 1_000)
    }

    @Test
    @Order(77)
    fun `mooneye-acceptance-oam_dma_restart`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/oam_dma_restart.gb", 1_000)
    }

    @Test
    @Order(78)
    fun `mooneye-acceptance-oam_dma_start`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/oam_dma_start.gb", 1_000)
    }

    @Test
    @Order(79)
    fun `mooneye-acceptance-oam_dma_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/oam_dma_timing.gb", 1_000)
    }

    @Test
    @Order(80)
    fun `mooneye-acceptance-pop_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/pop_timing.gb", 1_000)
    }

    @Test
    @Order(81)
    fun `mooneye-acceptance-ppu-hblank_ly_scx_timing-GS`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/ppu/hblank_ly_scx_timing-GS.gb", 1_000)
    }

    @Test
    @Order(82)
    fun `mooneye-acceptance-ppu-intr_1_2_timing-GS`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/ppu/intr_1_2_timing-GS.gb", 1_000)
    }

    @Test
    @Order(83)
    fun `mooneye-acceptance-ppu-intr_2_0_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/ppu/intr_2_0_timing.gb", 1_000)
    }

    @Test
    @Order(84)
    fun `mooneye-acceptance-ppu-intr_2_mode0_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/ppu/intr_2_mode0_timing.gb", 1_000)
    }

    @Test
    @Order(85)
    fun `mooneye-acceptance-ppu-intr_2_mode0_timing_sprites`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/ppu/intr_2_mode0_timing_sprites.gb", 4_000)
    }

    @Test
    @Order(86)
    fun `mooneye-acceptance-ppu-intr_2_mode3_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/ppu/intr_2_mode3_timing.gb", 1_000)
    }

    @Test
    @Order(87)
    fun `mooneye-acceptance-ppu-intr_2_oam_ok_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/ppu/intr_2_oam_ok_timing.gb", 1_000)
    }

    @Test
    @Order(88)
    fun `mooneye-acceptance-ppu-lcdon_timing-GS`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/ppu/lcdon_timing-GS.gb", 1_000)
    }

    @Test
    @Order(89)
    fun `mooneye-acceptance-ppu-lcdon_write_timing-GS`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/ppu/lcdon_write_timing-GS.gb", 1_000)
    }

    @Test
    @Order(90)
    fun `mooneye-acceptance-ppu-stat_irq_blocking`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/ppu/stat_irq_blocking.gb", 1_000)
    }

    @Test
    @Order(91)
    fun `mooneye-acceptance-ppu-stat_lyc_onoff`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/ppu/stat_lyc_onoff.gb", 1_000)
    }

    @Test
    @Order(92)
    fun `mooneye-acceptance-ppu-vblank_stat_intr-GS`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/ppu/vblank_stat_intr-GS.gb", 1_000)
    }

    @Test
    @Order(93)
    fun `mooneye-acceptance-push_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/push_timing.gb", 1_000)
    }

    @Test
    @Order(94)
    fun `mooneye-acceptance-rapid_di_ei`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/rapid_di_ei.gb", 1_000)
    }

    @Test
    @Order(95)
    fun `mooneye-acceptance-reti_intr_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/reti_intr_timing.gb", 1_000)
    }

    @Test
    @Order(96)
    fun `mooneye-acceptance-reti_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/reti_timing.gb", 1_000)
    }

    @Test
    @Order(97)
    fun `mooneye-acceptance-ret_cc_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/ret_cc_timing.gb", 1_000)
    }

    @Test
    @Order(98)
    fun `mooneye-acceptance-ret_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/ret_timing.gb", 1_000)
    }

    @Test
    @Order(99)
    fun `mooneye-acceptance-rst_timing`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/rst_timing.gb", 1_000)
    }

    @Test
    @Order(100)
    fun `mooneye-acceptance-serial-boot_sclk_align-dmgABCmgb`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/serial/boot_sclk_align-dmgABCmgb.gb", 1_000)
    }

    @Test
    @Order(101)
    fun `mooneye-acceptance-timer-div_write`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/timer/div_write.gb", 1_000)
    }

    @Test
    @Order(102)
    fun `mooneye-acceptance-timer-rapid_toggle`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/timer/rapid_toggle.gb", 1_000)
    }

    @Test
    @Order(103)
    fun `mooneye-acceptance-timer-tim00`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/timer/tim00.gb", 1_000)
    }

    @Test
    @Order(104)
    fun `mooneye-acceptance-timer-tim00_div_trigger`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/timer/tim00_div_trigger.gb", 1_000)
    }

    @Test
    @Order(105)
    fun `mooneye-acceptance-timer-tim01`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/timer/tim01.gb", 1_000)
    }

    @Test
    @Order(106)
    fun `mooneye-acceptance-timer-tim01_div_trigger`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/timer/tim01_div_trigger.gb", 1_000)
    }

    @Test
    @Order(107)
    fun `mooneye-acceptance-timer-tim10`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/timer/tim10.gb", 1_000)
    }

    @Test
    @Order(108)
    fun `mooneye-acceptance-timer-tim10_div_trigger`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/timer/tim10_div_trigger.gb", 1_000)
    }

    @Test
    @Order(109)
    fun `mooneye-acceptance-timer-tim11`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/timer/tim11.gb", 1_000)
    }

    @Test
    @Order(110)
    fun `mooneye-acceptance-timer-tim11_div_trigger`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/timer/tim11_div_trigger.gb", 1_000)
    }

    @Test
    @Order(111)
    fun `mooneye-acceptance-timer-tima_reload`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/timer/tima_reload.gb", 1_000)
    }

    @Test
    @Order(112)
    fun `mooneye-acceptance-timer-tima_write_reloading`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/timer/tima_write_reloading.gb", 1_000)
    }

    @Test
    @Order(113)
    fun `mooneye-acceptance-timer-tma_write_reloading`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/acceptance/timer/tma_write_reloading.gb", 1_000)
    }

    @Test
    @Order(114)
    fun `mooneye-emulator-only-mbc1-bits_bank1`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/emulator-only/mbc1/bits_bank1.gb", 3_000)
    }

    @Test
    @Order(115)
    fun `mooneye-emulator-only-mbc1-bits_bank2`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/emulator-only/mbc1/bits_bank2.gb", 4_000)
    }

    @Test
    @Order(116)
    fun `mooneye-emulator-only-mbc1-bits_mode`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/emulator-only/mbc1/bits_mode.gb", 3_000)
    }

    @Test
    @Order(117)
    fun `mooneye-emulator-only-mbc1-bits_ramg`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/emulator-only/mbc1/bits_ramg.gb", 6_500)
    }

    @Test
    @Order(118)
    fun `mooneye-emulator-only-mbc1-multicart_rom_8Mb`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/emulator-only/mbc1/multicart_rom_8Mb.gb", 1_000)
    }

    @Test
    @Order(119)
    fun `mooneye-emulator-only-mbc1-ram_256kb`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/emulator-only/mbc1/ram_256kb.gb", 2_000)
    }

    @Test
    @Order(120)
    fun `mooneye-emulator-only-mbc1-ram_64kb`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/emulator-only/mbc1/ram_64kb.gb", 2_000)
    }

    @Test
    @Order(121)
    fun `mooneye-emulator-only-mbc1-rom_16Mb`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/emulator-only/mbc1/rom_16Mb.gb", 1_000)
    }

    @Test
    @Order(122)
    fun `mooneye-emulator-only-mbc1-rom_1Mb`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/emulator-only/mbc1/rom_1Mb.gb", 1_000)
    }

    @Test
    @Order(123)
    fun `mooneye-emulator-only-mbc1-rom_2Mb`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/emulator-only/mbc1/rom_2Mb.gb", 1_000)
    }

    @Test
    @Order(124)
    fun `mooneye-emulator-only-mbc1-rom_4Mb`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/emulator-only/mbc1/rom_4Mb.gb", 1_000)
    }

    @Test
    @Order(125)
    fun `mooneye-emulator-only-mbc1-rom_512kb`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/emulator-only/mbc1/rom_512kb.gb", 1_000)
    }

    @Test
    @Order(126)
    fun `mooneye-emulator-only-mbc1-rom_8Mb`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/emulator-only/mbc1/rom_8Mb.gb", 1_000)
    }

    @Test
    @Order(127)
    fun `mooneye-emulator-only-mbc2-bits_ramg`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/emulator-only/mbc2/bits_ramg.gb", 6_000)
    }

    @Test
    @Order(128)
    fun `mooneye-emulator-only-mbc2-bits_romb`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/emulator-only/mbc2/bits_romb.gb", 4_000)
    }

    @Test
    @Order(129)
    fun `mooneye-emulator-only-mbc2-bits_unused`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/emulator-only/mbc2/bits_unused.gb", 3_000)
    }

    @Test
    @Order(130)
    fun `mooneye-emulator-only-mbc2-ram`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/emulator-only/mbc2/ram.gb", 1_000)
    }

    @Test
    @Order(131)
    fun `mooneye-emulator-only-mbc2-rom_1Mb`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/emulator-only/mbc2/rom_1Mb.gb", 1_000)
    }

    @Test
    @Order(132)
    fun `mooneye-emulator-only-mbc2-rom_2Mb`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/emulator-only/mbc2/rom_2Mb.gb", 1_000)
    }

    @Test
    @Order(133)
    fun `mooneye-emulator-only-mbc2-rom_512kb`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/emulator-only/mbc2/rom_512kb.gb", 1_000)
    }

    @Test
    @Order(134)
    fun `mooneye-emulator-only-mbc5-rom_16Mb`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/emulator-only/mbc5/rom_16Mb.gb", 1_000)
    }

    @Test
    @Order(135)
    fun `mooneye-emulator-only-mbc5-rom_1Mb`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/emulator-only/mbc5/rom_1Mb.gb", 1_000)
    }

    @Test
    @Order(136)
    fun `mooneye-emulator-only-mbc5-rom_2Mb`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/emulator-only/mbc5/rom_2Mb.gb", 1_000)
    }

    @Test
    @Order(137)
    fun `mooneye-emulator-only-mbc5-rom_32Mb`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/emulator-only/mbc5/rom_32Mb.gb", 1_000)
    }

    @Test
    @Order(138)
    fun `mooneye-emulator-only-mbc5-rom_4Mb`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/emulator-only/mbc5/rom_4Mb.gb", 1_000)
    }

    @Test
    @Order(139)
    fun `mooneye-emulator-only-mbc5-rom_512kb`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/emulator-only/mbc5/rom_512kb.gb", 1_000)
    }

    @Test
    @Order(140)
    fun `mooneye-emulator-only-mbc5-rom_64Mb`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/emulator-only/mbc5/rom_64Mb.gb", 1_000)
    }

    @Test
    @Order(141)
    fun `mooneye-emulator-only-mbc5-rom_8Mb`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/emulator-only/mbc5/rom_8Mb.gb", 1_000)
    }

    @Test
    @Order(142)
    fun `mooneye-manual-only-sprite_priority`(testInfo: TestInfo) {
        runTest(testInfo.displayName, "mooneye/manual-only/sprite_priority.gb", 1_000)
    }

    @Test
    @Order(143)
    fun `mooneye-misc-boot_div-cgbABCDE`(testInfo: TestInfo) {
        // ToDo Test doesn't pass but has exactly same results as Same Boy. Seems good since we use Same Boy Boot ROM
        runTest(
            testName = testInfo.displayName,
            romPath = "mooneye/misc/boot_div-cgbABCDE.gb",
            duration = 4_000,
            machineMode = MachineMode.CGB_COMPAT,
            skipBoot = false,
        )
    }

    @Test
    @Order(144)
    fun `mooneye-misc-boot_regs-cgb`(testInfo: TestInfo) {
        runTest(
            testName = testInfo.displayName,
            romPath = "mooneye/misc/boot_regs-cgb.gb",
            duration = 1_000,
            machineMode = MachineMode.CGB_COMPAT,
        )
    }

    @Test
    @Order(144)
    fun `samesuite-dma-gdma_addr_mask`(testInfo: TestInfo) {
        runTest(
            testName = testInfo.displayName,
            romPath = "samesuite/dma/gdma_addr_mask.gb",
            duration = 500,
            machineMode = MachineMode.CGB_COMPAT,
        )
    }

    @Test
    @Order(144)
    fun `samesuite-dma-hdma_lcd_off`(testInfo: TestInfo) {
        runTest(
            testName = testInfo.displayName,
            romPath = "samesuite/dma/hdma_lcd_off.gb",
            duration = 500,
            machineMode = MachineMode.CGB_COMPAT,
        )
    }

    @Test
    @Order(145)
    fun `magenTests-hblank_vram_dma`(testInfo: TestInfo) {
        runTest(
            testName = testInfo.displayName,
            romPath = "magenTests/hblank_vram_dma.gbc",
            duration = 500,
        )
    }

    companion object {
        private const val TEST_RESULTS_PATH = "build/test-results"
        private const val TEST_RESULTS_HTML_FILE = "test-results.html"
        private const val TEST_RUNS_FILE = "runs.json"
        private const val TEST_RUNS_HTML_FILE = "runs.html"

        /** Color-aware comparison for CGB tests whose pass/fail differs by HUE, not luminance
         *  (e.g. hblank_vram_dma: green = pass, red = fail). A per-channel tolerance absorbs the
         *  RGB555->RGB888 expansion mismatch between our output formula and the reference PNG's,
         *  while staying far below the distance between the discrete colors a test ROM uses.
         *
         *  TODO: CHANNEL_TOLERANCE is a heuristic, not a measured formula gap. If a future test
         *  pairs two *close* colors with larger expansion artifacts, calibrate it (intra-color
         *  noise vs inter-color distance) — otherwise it could false-pass or false-fail. */
        private const val CHANNEL_TOLERANCE = 8  // per-channel delta, 0..255

        private val resultFile = File("$TEST_RESULTS_PATH/$TEST_RESULTS_HTML_FILE")
        private val runsFile = File("$TEST_RESULTS_PATH/$TEST_RUNS_FILE")
        private val runsHtmlFile = File("$TEST_RESULTS_PATH/$TEST_RUNS_HTML_FILE")
        private var testCount: Int = 0
        private var successTestCount: Int = 0


        private lateinit var runs: AllTestRun
        private lateinit var currentRun: TestRun

        private var viewModel: GameBoyViewModel? = null


        @BeforeAll
        @JvmStatic
        fun setup() {
            Logger.debug("TEST", "Start of tests")

            viewModel = GameBoyViewModel()
                .apply {
                    setDmgPalette(Palette.DOC_BOY_TEST)
                }

            testCount = 0
            successTestCount = 0

            runs = initRuns()
            val timestamp: String = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            currentRun = TestRun(timestamp,linkedMapOf())

            resultFile.writeText("""
                <html><head><style>
                    table { border-collapse: collapse }
                    .emulator { position: sticky; top: 0px; }
                    .test { position: sticky; left: 0px; }
                    .tooltiptext { visibility: hidden; width: 200px; background-color: black; color: #fff; text-align: center; padding: 5px 0; border-radius: 6px; position: absolute; z-index: 1; left: 102%; }
                    tr:hover .tooltiptext { visibility: visible; }
                    td, th { border: #333 solid 1px; text-align: center; line-height: 1.5}
                    .PASS { background-color: #6e2 }
                    .FAIL { background-color: #e44 }
                    .UNKNOWN { background-color: #fd6 }
                    td {font-size:80%}
                    th{background:#eee}
                    th:first-child{text-align:right; padding-right:4px}
                    .screenshot { width: 160; height: 144; }
                    body{font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Helvetica,Arial,sans-serif}
                    </style></head><body><table>
                        <tr><th style="text-align:left"><!--Updated On<br>Sun, 22 Mar 2026 14:54:53 +0000--></th>
                          <th class='emulator'>Reference</th>
                          <th class='emulator'>Result</th>
                        </tr>
            """.trimIndent())
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            Logger.debug("TEST", "End of tests")

            viewModel?.stop()
            viewModel = null

            runs.testRuns.add(currentRun)
            saveRuns()
            convertRunsToHtml()

            resultFile.appendText("""
                </table>
                Successfull tests: $successTestCount/$testCount
                </html>
            """.trimIndent())
        }

        private fun runTest(
            testName: String,
            romPath: String,
            duration: Long,
            commands: List<JoypadEvent> = emptyList(),
            captureNameSuffix: String = "",
            machineMode: MachineMode? = null,
            skipBoot: Boolean = true,
        ) {
            testCount++

            val testName = testName.substringBeforeLast("(")

            val romName = romPath.substring(
                romPath.lastIndexOf('/') + 1,
                romPath.lastIndexOf('.'),
            )
            val pngPath = romPath.substring(
                0,
                romPath.lastIndexOf('.'),
            ) + "$captureNameSuffix.png"
            val captureName = "${romPath.replace('/', '_')}$captureNameSuffix.png"
            Logger.debug("TEST", pngPath)

//            val viewModel = GameBoyViewModel()

            val imageBuffer = try {
                val rom = ClassLoader
                    .getSystemResourceAsStream("roms/$romPath")
                val romBytes = requireNotNull(rom?.readBytes())

                val viewModel = requireNotNull(viewModel)

                if (machineMode != null) {
                    viewModel.loadRom(romBytes, romName, machineMode, skipBoot)
                } else {
                    viewModel.loadRom(romBytes, romName, skipBoot = skipBoot)
                }

                if (commands.isNotEmpty()) {
                    Thread.sleep(500)
                    val buttonChannel = viewModel.buttonChannel
                    commands.forEach { event ->
                        buttonChannel.trySend(event)
                        Thread.sleep(20)
                    }
                }

                Thread.sleep(duration)

                val frameBuffer = viewModel.stateFlow.value.coloredFrameBuffer
                assertNotNull(frameBuffer)

                frameBuffer.toPng("$TEST_RESULTS_PATH/$captureName")

                frameBuffer
            } catch (e: Throwable) {
                currentRun.tests[testName] = TestStatus.FAIL

                resultFile.appendText("""
                <tr><th class='test'>$romPath</th>
                  <td class='INFO'>No reference</td>
                  <td class='FAIL'>FAIL<br>${e.message}</td>
                </tr>
                """.trimIndent())

//                viewModel.stop()

                fail("Unknown error", e)
            }

            try {
                val reference = requireNotNull(ClassLoader.getSystemResourceAsStream("references/$pngPath"))
                val output = File("$TEST_RESULTS_PATH/$captureName").inputStream()

                val passed = if (machineMode == MachineMode.DMG || (machineMode == MachineMode.CGB_COMPAT && skipBoot)) {
                    matchesReference(output, reference)
                } else {
                    matchesReferenceColor(output, reference)
                }

                val status = if (passed) "PASS" else "FAIL"

                resultFile.appendText("""
                <tr><th class='test'>$romPath</th>
                  <td class='INFO'>INFO<br><img class='screenshot' src='../../src/jvmTest/resources/references/$pngPath'></td>
                  <td class='$status'>$status<br><img class='screenshot' src='$captureName'></td>
                </tr>
                """.trimIndent())

                if (passed) {
                    currentRun.tests[testName] = TestStatus.PASS
                    successTestCount++
                } else {
                    currentRun.tests[testName] = TestStatus.FAIL
                    fail("Screenshot does not match reference")
                }
            } catch (e: Exception) {
                currentRun.tests[testName] = TestStatus.UNKNOWN

                resultFile.appendText("""
                <tr><th class='test'>$romPath</th>
                  <td class='INFO'>No reference</td>
                  <td class='UNKNOWN'>UNKNOWN<br><img class='screenshot' src='$captureName'></td>
                </tr>
                """.trimIndent())

//                viewModel.stop()

                fail("No reference found", e)
            }

//            viewModel.stop()
        }

        private fun IntArray.toPng(outputPath: String) {
            val image = BufferedImage(160, 144, BufferedImage.TYPE_INT_ARGB)
            image.setRGB(0, 0, 160, 144, this, 0, 160)
            ImageIO.write(image, "PNG", File(outputPath))
        }

        private fun initRuns(): AllTestRun {
            if (!runsFile.exists()) {
                return AllTestRun(testRuns = mutableListOf())
            }

            val text = runsFile.readText()
            return text.deserialize()
        }

        private fun saveRuns() {
            val text = runs.serialize()
            runsFile.writeText(text)
        }

        private fun convertRunsToHtml() {
            runsHtmlFile.writeText("""
                <html><head><style>
                    table { border-collapse: collapse }
                    .emulator { position: sticky; top: 0px; }
                    .test { position: sticky; left: 0px; }
                    .tooltiptext { visibility: hidden; width: 200px; background-color: black; color: #fff; text-align: center; padding: 5px 0; border-radius: 6px; position: absolute; z-index: 1; left: 102%; }
                    tr:hover .tooltiptext { visibility: visible; }
                    td, th { border: #333 solid 1px; text-align: center; line-height: 1.5}
                    .PASS { background-color: #6e2 }
                    .FAIL { background-color: #e44 }
                    .UNKNOWN { background-color: #fd6 }
                    td {font-size:80%}
                    th{background:#eee}
                    th:first-child{text-align:right; padding-right:4px}
                    .screenshot { width: 160; height: 144; }
                    body{font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Helvetica,Arial,sans-serif}
                    </style></head><body><table>
            """.trimIndent())

            val headers = runs.testRuns.map { it.timeStamp }
            runsHtmlFile.appendText("<tr><th style=\"text-align:left\">Test</th><th class='emulator'>${headers.joinToString("</th><th class='emulator'>")}</th></tr>\n")

            val testNames = runs.testRuns.flatMap { it.tests.keys }.distinct()

            testNames
                .forEach { name ->
                    runsHtmlFile.appendText("<tr><th class='test'>$name</th>")
                    runs.testRuns.forEach { run ->
                        val result = run.tests[name]
                        val text = when (result) {
                            TestStatus.PASS -> "<td class='PASS'>PASS</td>"
                            TestStatus.FAIL -> "<td class='FAIL'>FAIL</td>"
                            TestStatus.UNKNOWN -> "<td class='UNKNOWN'>UNKNOWN</td>"
                            null -> "<td></td>"
                        }
                        runsHtmlFile.appendText(text)
                    }
                    runsHtmlFile.appendText("</tr>\n")
                }

            runsHtmlFile.appendText("</table></html>\n")
        }


        /** Référence PNG -> indices de nuance GB 0..3.
         *  Convention : 0 = blanc (clair) … 3 = noir, comme ton frameBuffer. */
        fun pngToShades(stream: InputStream): IntArray {
            val img = stream.use { ImageIO.read(it) }      // .use ferme le flux ; ImageIO ne le fait pas
                ?: error("PNG illisible (aucun décodeur ImageIO ou flux vide)")
            require(img.width == 160 && img.height == 144) {
                "attendu 160x144, reçu ${img.width}x${img.height}"
            }
            val palette = intArrayOf(255, 170, 85, 0)      // luminance par nuance ; index = nuance GB
            val out = IntArray(160 * 144)
            for (y in 0 until 144) for (x in 0 until 160) {
                val p = img.getRGB(x, y)
                val r = (p ushr 16) and 0xFF
                val g = (p ushr 8)  and 0xFF
                val b =  p          and 0xFF
                val lum = (r * 299 + g * 587 + b * 114) / 1000
                var best = 0; var bestD = Int.MAX_VALUE
                for (s in palette.indices) {
                    val d = abs(lum - palette[s])
                    if (d < bestD) { bestD = d; best = s }
                }
                out[y * 160 + x] = best
            }
            return out
        }

        /** PNG -> packed 0xAARRGGBB pixels. Same loading contract as pngToShades:
         *  closes the stream, fails loudly on a missing decoder, enforces 160x144. */
        private fun pngToArgb(stream: InputStream): IntArray {
            val img = stream.use { ImageIO.read(it) }
                ?: error("PNG illisible (aucun décodeur ImageIO ou flux vide)")
            require(img.width == 160 && img.height == 144) {
                "attendu 160x144, reçu ${img.width}x${img.height}"
            }
            // Bulk read: fine for a 160x144 buffer, avoids per-pixel getRGB overhead.
            return img.getRGB(0, 0, 160, 144, IntArray(160 * 144), 0, 160)
        }

        fun matchesReference(output: InputStream, reference: InputStream): Boolean {
            val outputShades = pngToShades(output)
            val referenceShades = pngToShades(reference)
            return outputShades.contentEquals(referenceShades)
        }

        fun matchesReferenceColor(
            output: InputStream,
            reference: InputStream,
            tolerance: Int = CHANNEL_TOLERANCE,
        ): Boolean {
            val out = pngToArgb(output)
            val ref = pngToArgb(reference)
            for (i in out.indices) {
                val o = out[i]; val r = ref[i]
                // Alpha ignored: some encoders emit it, it carries no test signal.
                if (abs(((o ushr 16) and 0xFF) - ((r ushr 16) and 0xFF)) > tolerance) return false
                if (abs(((o ushr 8)  and 0xFF) - ((r ushr 8)  and 0xFF)) > tolerance) return false
                if (abs(( o          and 0xFF) - ( r          and 0xFF)) > tolerance) return false
            }
            return true
        }
    }
}
