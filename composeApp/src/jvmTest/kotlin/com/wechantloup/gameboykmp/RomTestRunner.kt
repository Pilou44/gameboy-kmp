package com.wechantloup.gameboykmp

import com.wechantloup.gameboykmp.joypad.JoypadButton
import com.wechantloup.gameboykmp.joypad.JoypadEvent
import com.wechantloup.gameboykmp.logger.Logger
import com.wechantloup.gameboykmp.ui.GameBoyViewModel
import com.wechantloup.gameboykmp.ui.Palette
import javax.imageio.ImageIO
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.fail

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class RomTestRunner {
    @Test
    @Order(1)
    fun `acid-which`() {
        runTest("acid/which.gb", 200)
    }

    @Test
    @Order(2)
    fun `acid-dmg-acid2`() {
        runTest("acid/dmg-acid2.gb", 200)
    }

    @Test
    @Order(3)
    fun `blarrg-cpu_instrs-01`() {
        runTest("blarrg/cpu_instrs/01-special.gb", 3_000)
    }

    @Test
    @Order(4)
    fun `blarrg-cpu_instrs-02`() {
        runTest("blarrg/cpu_instrs/02-interrupts.gb", 1_000)
    }

    @Test
    @Order(5)
    fun `blarrg-cpu_instrs-03`() {
        runTest("blarrg/cpu_instrs/03-op_sp,hl.gb", 3_000)
    }

    @Test
    @Order(6)
    fun `blarrg-cpu_instrs-04`() {
        runTest("blarrg/cpu_instrs/04-op_r,imm.gb", 3_000)
    }

    @Test
    @Order(7)
    fun `blarrg-cpu_instrs-05`() {
        runTest("blarrg/cpu_instrs/05-op_rp.gb", 4_000)
    }

    @Test
    @Order(8)
    fun `blarrg-cpu_instrs-06`() {
        runTest("blarrg/cpu_instrs/06-ld_r,r.gb", 1_000)
    }

    @Test
    @Order(9)
    fun `blarrg-cpu_instrs-07`() {
        runTest("blarrg/cpu_instrs/07-jr,jp,call,ret,rst.gb", 1_000)
    }

    @Test
    @Order(10)
    fun `blarrg-cpu_instrs-08`() {
        runTest("blarrg/cpu_instrs/08-misc_instrs.gb", 1_000)
    }

    @Test
    @Order(11)
    fun `blarrg-cpu_instrs-09`() {
        runTest("blarrg/cpu_instrs/09-op_r,r.gb", 10_000)
    }

    @Test
    @Order(12)
    fun `blarrg-cpu_instrs-10`() {
        runTest("blarrg/cpu_instrs/10-bit_ops.gb", 14_000)
    }

    @Test
    @Order(13)
    fun `blarrg-cpu_instrs-11`() {
        runTest("blarrg/cpu_instrs/11-op_a,(hl).gb", 18_000)
    }

    @Test
    @Order(14)
    fun `blarrg-halt_bug`() {
        runTest("blarrg/halt_bug.gb", 2_000)
    }

    @Test
    @Order(15)
    fun `blarrg-instr_timing`() {
        runTest("blarrg/instr_timing.gb", 1_000)
    }

    @Test
    @Order(16)
    fun `blarrg-interrupt_time`() {
        runTest("blarrg/interrupt_time.gb", 1_000)
    }

    @Test
    @Order(17)
    fun `blarrg-mem_timing-01-read_timing`() {
        runTest("blarrg/mem_timing/01-read_timing.gb", 500)
    }

    @Test
    @Order(18)
    fun `blarrg-mem_timing-02-write_timing`() {
        runTest("blarrg/mem_timing/02-write_timing.gb", 500)
    }

    @Test
    @Order(19)
    fun `blarrg-mem_timing-03-modify_timing`() {
        runTest("blarrg/mem_timing/03-modify_timing.gb", 1_000)
    }

    @Test
    @Order(20)
    fun `blarrg-mem_timing-2-01-read_timing`() {
        runTest("blarrg/mem_timing-2/01-read_timing.gb", 500)
    }

    @Test
    @Order(21)
    fun `blarrg-mem_timing-2-02-write_timing`() {
        runTest("blarrg/mem_timing-2/02-write_timing.gb", 500)
    }

    @Test
    @Order(22)
    fun `blarrg-mem_timing-2-03-modify_timing`() {
        runTest("blarrg/mem_timing-2/03-modify_timing.gb", 1_000)
    }

    @Test
    @Order(23)
    fun `blarrg-oam_bug-1-lcd_sync`() {
        runTest("blarrg/oam_bug/1-lcd_sync.gb", 1_000)
    }

    @Test
    @Order(24)
    fun `blarrg-oam_bug-2-causes`() {
        runTest("blarrg/oam_bug/2-causes.gb", 1_000)
    }

    @Test
    @Order(25)
    fun `blarrg-oam_bug-3-non_causes`() {
        runTest("blarrg/oam_bug/3-non_causes.gb", 2_000)
    }

    @Test
    @Order(26)
    fun `blarrg-oam_bug-4-scanline_timing`() {
        runTest("blarrg/oam_bug/4-scanline_timing.gb", 1_000)
    }

    @Test
    @Order(27)
    fun `blarrg-oam_bug-5-timing_bug`() {
        runTest("blarrg/oam_bug/5-timing_bug.gb", 1_000)
    }

    @Test
    @Order(28)
    fun `blarrg-oam_bug-6-timing_no_bug`() {
        runTest("blarrg/oam_bug/6-timing_no_bug.gb", 2_000)
    }

    @Test
    @Order(29)
    fun `blarrg-oam_bug-7-timing_effect`() {
        runTest("blarrg/oam_bug/7-timing_effect.gb", 7_000)
    }

    @Test
    @Order(30)
    fun `blarrg-oam_bug-8-instr_effect`() {
        runTest("blarrg/oam_bug/8-instr_effect.gb", 1_000)
    }

    @Test
    @Order(31)
    fun `blarrg-dmg_sound-01-registers`() {
        runTest("blarrg/dmg_sound/01-registers.gb", 1_000)
    }

    @Test
    @Order(32)
    fun `blarrg-dmg_sound-02-len_ctr`() {
        runTest("blarrg/dmg_sound/02-len_ctr.gb", 10_000)
    }

    @Test
    @Order(33)
    fun `blarrg-dmg_sound-03-trigger`() {
        runTest("blarrg/dmg_sound/03-trigger.gb", 17_000)
    }

    @Test
    @Order(34)
    fun `blarrg-dmg_sound-04-sweep`() {
        runTest("blarrg/dmg_sound/04-sweep.gb", 2_000)
    }

    @Test
    @Order(35)
    fun `blarrg-dmg_sound-05-sweep_details`() {
        runTest("blarrg/dmg_sound/05-sweep_details.gb", 2_000)
    }

    @Test
    @Order(36)
    fun `blarrg-dmg_sound-06-overflow_on_trigger`() {
        runTest("blarrg/dmg_sound/06-overflow_on_trigger.gb", 1_000)
    }

    @Test
    @Order(37)
    fun `blarrg-dmg_sound-07-len_sweep_period_sync`() {
        runTest("blarrg/dmg_sound/07-len_sweep_period_sync.gb", 1_000)
    }

    @Test
    @Order(38)
    fun `blarrg-dmg_sound-08-len_ctr_during_power`() {
        runTest("blarrg/dmg_sound/08-len_ctr_during_power.gb", 2_000)
    }

    @Test
    @Order(39)
    fun `blarrg-dmg_sound-09-wave_read_while_on`() {
        runTest("blarrg/dmg_sound/09-wave_read_while_on.gb", 1_000)
    }

    @Test
    @Order(40)
    fun `blarrg-dmg_sound-10-wave_trigger_while_on`() {
        runTest("blarrg/dmg_sound/10-wave_trigger_while_on.gb", 4_000)
    }

    @Test
    @Order(41)
    fun `blarrg-dmg_sound-11-regs_after_power`() {
        runTest("blarrg/dmg_sound/11-regs_after_power.gb", 1_000)
    }

    @Test
    @Order(42)
    fun `blarrg-dmg_sound-12-wave_write_while_on`() {
        runTest("blarrg/dmg_sound/12-wave_write_while_on.gb", 4_000)
    }

    @Test
    @Order(43)
    fun `ax6-rtc3test-basic_tests`() {
        runTest(
            romPath = "ax6/rtc3test.gb",
            duration = 13_000,
            commands = listOf(
                JoypadEvent.Pressed(JoypadButton.A),
                JoypadEvent.Released(JoypadButton.A),
            ),
            captureNameSuffix = "_basic_tests",
        )
    }

    @Test
    @Order(44)
    fun `ax6-rtc3test-range_tests`() {
        runTest(
            romPath = "ax6/rtc3test.gb",
            duration = 9_000,
            commands = listOf(
                JoypadEvent.Pressed(JoypadButton.DOWN),
                JoypadEvent.Released(JoypadButton.DOWN),
                JoypadEvent.Pressed(JoypadButton.A),
                JoypadEvent.Released(JoypadButton.A),
            ),
            captureNameSuffix = "_range_tests"
        )
    }

    @Test
    @Order(45)
    fun `ax6-rtc3test-sub-second_writes`() {
        runTest(
            romPath = "ax6/rtc3test.gb",
            duration = 18_000,
            commands = listOf(
                JoypadEvent.Pressed(JoypadButton.DOWN),
                JoypadEvent.Released(JoypadButton.DOWN),
                JoypadEvent.Pressed(JoypadButton.DOWN),
                JoypadEvent.Released(JoypadButton.DOWN),
                JoypadEvent.Pressed(JoypadButton.A),
                JoypadEvent.Released(JoypadButton.A),
            ),
            captureNameSuffix = "_sub-second_writes"
        )
    }

    @Test
    @Order(46)
    fun `mooneye-acceptance-add_sp_e_timing`() {
        runTest("mooneye/acceptance/add_sp_e_timing.gb", 1_000)
    }

    @Test
    @Order(47)
    fun `mooneye-acceptance-bits-mem_oam`() {
        runTest("mooneye/acceptance/bits/mem_oam.gb", 1_000)
    }

    @Test
    @Order(48)
    fun `mooneye-acceptance-bits-reg_f`() {
        runTest("mooneye/acceptance/bits/reg_f.gb", 1_000)
    }

    @Test
    @Order(49)
    fun `mooneye-acceptance-bits-unused_hwio-GS`() {
        runTest("mooneye/acceptance/bits/unused_hwio-GS.gb", 1_000)
    }

    @Test
    @Order(50)
    fun `mooneye-acceptance-boot_div-dmgABCmgb`() {
        runTest("mooneye/acceptance/boot_div-dmgABCmgb.gb", 1_000)
    }

    @Test
    @Order(51)
    fun `mooneye-acceptance-boot_hwio-dmgABCmgb`() {
        runTest("mooneye/acceptance/boot_hwio-dmgABCmgb.gb", 1_000)
    }

    @Test
    @Order(52)
    fun `mooneye-acceptance-boot_regs-dmgABC`() {
        runTest("mooneye/acceptance/boot_regs-dmgABC.gb", 1_000)
    }

    @Test
    @Order(53)
    fun `mooneye-acceptance-call_cc_timing`() {
        runTest("mooneye/acceptance/call_cc_timing.gb", 1_000)
    }

    @Test
    @Order(54)
    fun `mooneye-acceptance-call_cc_timing2`() {
        runTest("mooneye/acceptance/call_cc_timing2.gb", 1_000)
    }

    @Test
    @Order(55)
    fun `mooneye-acceptance-call_timing`() {
        runTest("mooneye/acceptance/call_timing.gb", 1_000)
    }

    @Test
    @Order(56)
    fun `mooneye-acceptance-call_timing2`() {
        runTest("mooneye/acceptance/call_timing2.gb", 1_000)
    }

    @Test
    @Order(57)
    fun `mooneye-acceptance-div_timing`() {
        runTest("mooneye/acceptance/div_timing.gb", 1_000)
    }

    @Test
    @Order(58)
    fun `mooneye-acceptance-di_timing-GS`() {
        runTest("mooneye/acceptance/di_timing-GS.gb", 1_000)
    }

    @Test
    @Order(59)
    fun `mooneye-acceptance-ei_sequence`() {
        runTest("mooneye/acceptance/ei_sequence.gb", 1_000)
    }

    @Test
    @Order(60)
    fun `mooneye-acceptance-ei_timing`() {
        runTest("mooneye/acceptance/ei_timing.gb", 1_000)
    }

    @Test
    @Order(61)
    fun `mooneye-acceptance-halt_ime0_ei`() {
        runTest("mooneye/acceptance/halt_ime0_ei.gb", 1_000)
    }

    @Test
    @Order(62)
    fun `mooneye-acceptance-halt_ime0_nointr_timing`() {
        runTest("mooneye/acceptance/halt_ime0_nointr_timing.gb", 1_000)
    }

    @Test
    @Order(63)
    fun `mooneye-acceptance-halt_ime1_timing`() {
        runTest("mooneye/acceptance/halt_ime1_timing.gb", 1_000)
    }

    @Test
    @Order(64)
    fun `mooneye-acceptance-halt_ime1_timing2-GS`() {
        runTest("mooneye/acceptance/halt_ime1_timing2-GS.gb", 1_000)
    }

    @Test
    @Order(65)
    fun `mooneye-acceptance-if_ie_registers`() {
        runTest("mooneye/acceptance/if_ie_registers.gb", 1_000)
    }

    @Test
    @Order(66)
    fun `mooneye-acceptance-instr-daa`() {
        runTest("mooneye/acceptance/instr/daa.gb", 1_000)
    }

    @Test
    @Order(67)
    fun `mooneye-acceptance-interrupts-ie_push`() {
        runTest("mooneye/acceptance/interrupts/ie_push.gb", 1_000)
    }

    @Test
    @Order(68)
    fun `mooneye-acceptance-intr_timing`() {
        runTest("mooneye/acceptance/intr_timing.gb", 1_000)
    }

    @Test
    @Order(69)
    fun `mooneye-acceptance-jp_cc_timing`() {
        runTest("mooneye/acceptance/jp_cc_timing.gb", 1_000)
    }

    @Test
    @Order(70)
    fun `mooneye-acceptance-jp_timing`() {
        runTest("mooneye/acceptance/jp_timing.gb", 1_000)
    }

    @Test
    @Order(71)
    fun `mooneye-acceptance-ld_hl_sp_e_timing`() {
        runTest("mooneye/acceptance/ld_hl_sp_e_timing.gb", 1_000)
    }

    @Test
    @Order(72)
    fun `mooneye-acceptance-oam_dma-basic`() {
        runTest("mooneye/acceptance/oam_dma/basic.gb", 1_000)
    }

    @Test
    @Order(73)
    fun `mooneye-acceptance-oam_dma-reg_read`() {
        runTest("mooneye/acceptance/oam_dma/reg_read.gb", 1_000)
    }

    @Test
    @Order(74)
    fun `mooneye-acceptance-oam_dma-sources-GS`() {
        runTest("mooneye/acceptance/oam_dma/sources-GS.gb", 1_000)
    }

    @Test
    @Order(75)
    fun `mooneye-acceptance-oam_dma_restart`() {
        runTest("mooneye/acceptance/oam_dma_restart.gb", 1_000)
    }

    @Test
    @Order(76)
    fun `mooneye-acceptance-oam_dma_start`() {
        runTest("mooneye/acceptance/oam_dma_start.gb", 1_000)
    }

    @Test
    @Order(77)
    fun `mooneye-acceptance-oam_dma_timing`() {
        runTest("mooneye/acceptance/oam_dma_timing.gb", 1_000)
    }

    @Test
    @Order(78)
    fun `mooneye-acceptance-pop_timing`() {
        runTest("mooneye/acceptance/pop_timing.gb", 1_000)
    }

    @Test
    @Order(79)
    fun `mooneye-acceptance-ppu-hblank_ly_scx_timing-GS`() {
        runTest("mooneye/acceptance/ppu/hblank_ly_scx_timing-GS.gb", 1_000)
    }

    @Test
    @Order(80)
    fun `mooneye-acceptance-ppu-intr_1_2_timing-GS`() {
        runTest("mooneye/acceptance/ppu/intr_1_2_timing-GS.gb", 1_000)
    }

    @Test
    @Order(81)
    fun `mooneye-acceptance-ppu-intr_2_0_timing`() {
        runTest("mooneye/acceptance/ppu/intr_2_0_timing.gb", 1_000)
    }

    @Test
    @Order(82)
    fun `mooneye-acceptance-ppu-intr_2_mode0_timing`() {
        runTest("mooneye/acceptance/ppu/intr_2_mode0_timing.gb", 1_000)
    }

    @Test
    @Order(83)
    fun `mooneye-acceptance-ppu-intr_2_mode0_timing_sprites`() {
        runTest("mooneye/acceptance/ppu/intr_2_mode0_timing_sprites.gb", 1_000)
    }

    @Test
    @Order(84)
    fun `mooneye-acceptance-ppu-intr_2_mode3_timing`() {
        runTest("mooneye/acceptance/ppu/intr_2_mode3_timing.gb", 1_000)
    }

    @Test
    @Order(85)
    fun `mooneye-acceptance-ppu-intr_2_oam_ok_timing`() {
        runTest("mooneye/acceptance/ppu/intr_2_oam_ok_timing.gb", 1_000)
    }

    @Test
    @Order(86)
    fun `mooneye-acceptance-ppu-lcdon_timing-GS`() {
        runTest("mooneye/acceptance/ppu/lcdon_timing-GS.gb", 1_000)
    }

    @Test
    @Order(87)
    fun `mooneye-acceptance-ppu-lcdon_write_timing-GS`() {
        runTest("mooneye/acceptance/ppu/lcdon_write_timing-GS.gb", 1_000)
    }

    @Test
    @Order(88)
    fun `mooneye-acceptance-ppu-stat_irq_blocking`() {
        runTest("mooneye/acceptance/ppu/stat_irq_blocking.gb", 1_000)
    }

    @Test
    @Order(89)
    fun `mooneye-acceptance-ppu-stat_lyc_onoff`() {
        runTest("mooneye/acceptance/ppu/stat_lyc_onoff.gb", 1_000)
    }

    @Test
    @Order(90)
    fun `mooneye-acceptance-ppu-vblank_stat_intr-GS`() {
        runTest("mooneye/acceptance/ppu/vblank_stat_intr-GS.gb", 1_000)
    }

    @Test
    @Order(91)
    fun `mooneye-acceptance-push_timing`() {
        runTest("mooneye/acceptance/push_timing.gb", 1_000)
    }

    @Test
    @Order(92)
    fun `mooneye-acceptance-rapid_di_ei`() {
        runTest("mooneye/acceptance/rapid_di_ei.gb", 1_000)
    }

    @Test
    @Order(93)
    fun `mooneye-acceptance-reti_intr_timing`() {
        runTest("mooneye/acceptance/reti_intr_timing.gb", 1_000)
    }

    @Test
    @Order(94)
    fun `mooneye-acceptance-reti_timing`() {
        runTest("mooneye/acceptance/reti_timing.gb", 1_000)
    }

    @Test
    @Order(95)
    fun `mooneye-acceptance-ret_cc_timing`() {
        runTest("mooneye/acceptance/ret_cc_timing.gb", 1_000)
    }

    @Test
    @Order(96)
    fun `mooneye-acceptance-ret_timing`() {
        runTest("mooneye/acceptance/ret_timing.gb", 1_000)
    }

    @Test
    @Order(97)
    fun `mooneye-acceptance-rst_timing`() {
        runTest("mooneye/acceptance/rst_timing.gb", 1_000)
    }

    @Test
    @Order(98)
    fun `mooneye-acceptance-serial-boot_sclk_align-dmgABCmgb`() {
        runTest("mooneye/acceptance/serial/boot_sclk_align-dmgABCmgb.gb", 1_000)
    }

    @Test
    @Order(99)
    fun `mooneye-acceptance-timer-div_write`() {
        runTest("mooneye/acceptance/timer/div_write.gb", 1_000)
    }

    @Test
    @Order(100)
    fun `mooneye-acceptance-timer-rapid_toggle`() {
        runTest("mooneye/acceptance/timer/rapid_toggle.gb", 1_000)
    }

    @Test
    @Order(101)
    fun `mooneye-acceptance-timer-tim00`() {
        runTest("mooneye/acceptance/timer/tim00.gb", 1_000)
    }

    @Test
    @Order(102)
    fun `mooneye-acceptance-timer-tim00_div_trigger`() {
        runTest("mooneye/acceptance/timer/tim00_div_trigger.gb", 1_000)
    }

    @Test
    @Order(103)
    fun `mooneye-acceptance-timer-tim01`() {
        runTest("mooneye/acceptance/timer/tim01.gb", 1_000)
    }

    @Test
    @Order(104)
    fun `mooneye-acceptance-timer-tim01_div_trigger`() {
        runTest("mooneye/acceptance/timer/tim01_div_trigger.gb", 1_000)
    }

    @Test
    @Order(105)
    fun `mooneye-acceptance-timer-tim10`() {
        runTest("mooneye/acceptance/timer/tim10.gb", 1_000)
    }

    @Test
    @Order(106)
    fun `mooneye-acceptance-timer-tim10_div_trigger`() {
        runTest("mooneye/acceptance/timer/tim10_div_trigger.gb", 1_000)
    }

    @Test
    @Order(107)
    fun `mooneye-acceptance-timer-tim11`() {
        runTest("mooneye/acceptance/timer/tim11.gb", 1_000)
    }

    @Test
    @Order(108)
    fun `mooneye-acceptance-timer-tim11_div_trigger`() {
        runTest("mooneye/acceptance/timer/tim11_div_trigger.gb", 1_000)
    }

    @Test
    @Order(109)
    fun `mooneye-acceptance-timer-tima_reload`() {
        runTest("mooneye/acceptance/timer/tima_reload.gb", 1_000)
    }

    @Test
    @Order(110)
    fun `mooneye-acceptance-timer-tima_write_reloading`() {
        runTest("mooneye/acceptance/timer/tima_write_reloading.gb", 1_000)
    }

    @Test
    @Order(111)
    fun `mooneye-acceptance-timer-tma_write_reloading`() {
        runTest("mooneye/acceptance/timer/tma_write_reloading.gb", 1_000)
    }

    @Test
    @Order(112)
    fun `mooneye-emulator-only-mbc1-bits_bank1`() {
        runTest("mooneye/emulator-only/mbc1/bits_bank1.gb", 2_000)
    }

    @Test
    @Order(113)
    fun `mooneye-emulator-only-mbc1-bits_bank2`() {
        runTest("mooneye/emulator-only/mbc1/bits_bank2.gb", 2_000)
    }

    @Test
    @Order(114)
    fun `mooneye-emulator-only-mbc1-bits_mode`() {
        runTest("mooneye/emulator-only/mbc1/bits_mode.gb", 2_000)
    }

    @Test
    @Order(115)
    fun `mooneye-emulator-only-mbc1-bits_ramg`() {
        runTest("mooneye/emulator-only/mbc1/bits_ramg.gb", 2_000)
    }

    @Test
    @Order(116)
    fun `mooneye-emulator-only-mbc1-multicart_rom_8Mb`() {
        runTest("mooneye/emulator-only/mbc1/multicart_rom_8Mb.gb", 1_000)
    }

    @Test
    @Order(117)
    fun `mooneye-emulator-only-mbc1-ram_256kb`() {
        runTest("mooneye/emulator-only/mbc1/ram_256kb.gb", 1_000)
    }

    @Test
    @Order(118)
    fun `mooneye-emulator-only-mbc1-ram_64kb`() {
        runTest("mooneye/emulator-only/mbc1/ram_64kb.gb", 1_000)
    }

    @Test
    @Order(119)
    fun `mooneye-emulator-only-mbc1-rom_16Mb`() {
        runTest("mooneye/emulator-only/mbc1/rom_16Mb.gb", 1_000)
    }

    @Test
    @Order(120)
    fun `mooneye-emulator-only-mbc1-rom_1Mb`() {
        runTest("mooneye/emulator-only/mbc1/rom_1Mb.gb", 1_000)
    }

    @Test
    @Order(121)
    fun `mooneye-emulator-only-mbc1-rom_2Mb`() {
        runTest("mooneye/emulator-only/mbc1/rom_2Mb.gb", 1_000)
    }

    @Test
    @Order(122)
    fun `mooneye-emulator-only-mbc1-rom_4Mb`() {
        runTest("mooneye/emulator-only/mbc1/rom_4Mb.gb", 1_000)
    }

    @Test
    @Order(123)
    fun `mooneye-emulator-only-mbc1-rom_512kb`() {
        runTest("mooneye/emulator-only/mbc1/rom_512kb.gb", 1_000)
    }

    @Test
    @Order(124)
    fun `mooneye-emulator-only-mbc1-rom_8Mb`() {
        runTest("mooneye/emulator-only/mbc1/rom_8Mb.gb", 1_000)
    }

    @Test
    @Order(125)
    fun `mooneye-emulator-only-mbc2-bits_ramg`() {
        runTest("mooneye/emulator-only/mbc2/bits_ramg.gb", 1_000)
    }

    @Test
    @Order(126)
    fun `mooneye-emulator-only-mbc2-bits_romb`() {
        runTest("mooneye/emulator-only/mbc2/bits_romb.gb", 1_000)
    }

    @Test
    @Order(127)
    fun `mooneye-emulator-only-mbc2-bits_unused`() {
        runTest("mooneye/emulator-only/mbc2/bits_unused.gb", 1_000)
    }

    @Test
    @Order(128)
    fun `mooneye-emulator-only-mbc2-ram`() {
        runTest("mooneye/emulator-only/mbc2/ram.gb", 1_000)
    }

    @Test
    @Order(129)
    fun `mooneye-emulator-only-mbc2-rom_1Mb`() {
        runTest("mooneye/emulator-only/mbc2/rom_1Mb.gb", 1_000)
    }

    @Test
    @Order(130)
    fun `mooneye-emulator-only-mbc2-rom_2Mb`() {
        runTest("mooneye/emulator-only/mbc2/rom_2Mb.gb", 1_000)
    }

    @Test
    @Order(131)
    fun `mooneye-emulator-only-mbc2-rom_512kb`() {
        runTest("mooneye/emulator-only/mbc2/rom_512kb.gb", 1_000)
    }

    @Test
    @Order(132)
    fun `mooneye-emulator-only-mbc5-rom_16Mb`() {
        runTest("mooneye/emulator-only/mbc5/rom_16Mb.gb", 1_000)
    }

    @Test
    @Order(133)
    fun `mooneye-emulator-only-mbc5-rom_1Mb`() {
        runTest("mooneye/emulator-only/mbc5/rom_1Mb.gb", 1_000)
    }

    @Test
    @Order(134)
    fun `mooneye-emulator-only-mbc5-rom_2Mb`() {
        runTest("mooneye/emulator-only/mbc5/rom_2Mb.gb", 1_000)
    }

    @Test
    @Order(135)
    fun `mooneye-emulator-only-mbc5-rom_32Mb`() {
        runTest("mooneye/emulator-only/mbc5/rom_32Mb.gb", 1_000)
    }

    @Test
    @Order(136)
    fun `mooneye-emulator-only-mbc5-rom_4Mb`() {
        runTest("mooneye/emulator-only/mbc5/rom_4Mb.gb", 1_000)
    }

    @Test
    @Order(137)
    fun `mooneye-emulator-only-mbc5-rom_512kb`() {
        runTest("mooneye/emulator-only/mbc5/rom_512kb.gb", 1_000)
    }

    @Test
    @Order(138)
    fun `mooneye-emulator-only-mbc5-rom_64Mb`() {
        runTest("mooneye/emulator-only/mbc5/rom_64Mb.gb", 1_000)
    }

    @Test
    @Order(139)
    fun `mooneye-emulator-only-mbc5-rom_8Mb`() {
        runTest("mooneye/emulator-only/mbc5/rom_8Mb.gb", 1_000)
    }

    @Test
    @Order(140)
    fun `mooneye-manual-only-sprite_priority`() {
        runTest("mooneye/manual-only/sprite_priority.gb", 1_000)
    }

    @Test
    @Order(141)
    fun `mooneye-misc-boot_div-cgbABCDE`() {
        runTest("mooneye/misc/boot_div-cgbABCDE.gb", 1_000)
    }

    @Test
    @Order(142)
    fun `mooneye-misc-boot_regs-cgb`() {
        runTest("mooneye/misc/boot_regs-cgb.gb", 1_000)
    }

    companion object {
        private const val TEST_RESULTS_PATH = "build/test-results"
        private const val TEST_RESULTS_HTML_FILE = "test-results.html"

        private val resultFile = File("$TEST_RESULTS_PATH/$TEST_RESULTS_HTML_FILE")
        private var testCount: Int = 0
        private var successTestCount: Int = 0


        @BeforeAll
        @JvmStatic
        fun setup() {
            Logger.debug("TEST", "Start of tests")

            testCount = 0
            successTestCount = 0

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
            resultFile.appendText("""
                </table>
                Successfull tests: $successTestCount/$testCount
                </html>
            """.trimIndent())
        }

        private fun runTest(
            romPath: String,
            duration: Long,
            commands: List<JoypadEvent> = emptyList(),
            captureNameSuffix: String = "",
        ) {
            testCount++

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

            val imageBuffer = try {
                val rom = ClassLoader
                    .getSystemResourceAsStream("roms/$romPath")
                val romBytes = requireNotNull(rom?.readBytes())

                val viewModel = GameBoyViewModel()

                viewModel.loadRom(romBytes, romName)

                if (commands.isNotEmpty()) {
                    Thread.sleep(500)
                    val buttonChannel = viewModel.buttonChannel
                    commands.forEach { event ->
                        buttonChannel.trySend(event)
                        Thread.sleep(20)
                    }
                }

                Thread.sleep(duration)

                val frameBuffer = viewModel.stateFlow.value.frameBuffer
                assertNotNull(frameBuffer)

                val palette = Palette.DOC_BOY_TEST

                frameBuffer
                    .map {
                        palette.colors[it]
                    }
                    .toIntArray()
                    .also {
                        it.toPng("$TEST_RESULTS_PATH/$captureName")
                    }
            } catch (e: Exception) {
                resultFile.appendText("""
                <tr><th class='test'>$romPath</th>
                  <td class='INFO'>No reference</td>
                  <td class='FAIL'>FAIL<br>${e.message}</td>
                </tr>
                """.trimIndent())
                fail("Unknown error", e)
            }

            try {
                val reference = ImageIO
                    .read(
                        ClassLoader.getSystemResourceAsStream("references/$pngPath")
                    )
                    .getRGB(0, 0, 160, 144, null, 0, 160)

                val passed = reference.contentEquals(imageBuffer)

                val status = if (passed) "PASS" else "FAIL"
                resultFile.appendText("""
                <tr><th class='test'>$romPath</th>
                  <td class='INFO'>INFO<br><img class='screenshot' src='../../src/jvmTest/resources/references/$pngPath'></td>
                  <td class='$status'>$status<br><img class='screenshot' src='$captureName'></td>
                </tr>
                """.trimIndent())

                if (passed) {
                    successTestCount++
                } else {
                    fail("Screenshot does not match reference")
                }
            } catch (e: Exception) {
                resultFile.appendText("""
                <tr><th class='test'>$romPath</th>
                  <td class='INFO'>No reference</td>
                  <td class='UNKNOWN'>UNKNOWN<br><img class='screenshot' src='$captureName'></td>
                </tr>
                """.trimIndent())
                fail("No reference found", e)
            }
        }

        private fun IntArray.toPng(outputPath: String) {
            val image = BufferedImage(160, 144, BufferedImage.TYPE_INT_ARGB)
            image.setRGB(0, 0, 160, 144, this, 0, 160)
            ImageIO.write(image, "PNG", File(outputPath))
        }
    }
}
