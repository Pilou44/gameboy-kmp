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
        runTest("blarrg/mem_timing/03-modify_timing.gb", 500)
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
        runTest("blarrg/mem_timing-2/03-modify_timing.gb", 500)
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
            duration = 12_000,
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
            duration = 8_000,
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
            duration = 15_000,
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
                <tr><th class='test'>$romName</th>
                  <td class='INFO'>No reference</td>
                  <td class='FAIL'>FAIL<br>Unknown error</td>
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
