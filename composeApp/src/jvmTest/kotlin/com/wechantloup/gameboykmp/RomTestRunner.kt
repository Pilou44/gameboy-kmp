package com.wechantloup.gameboykmp

import com.wechantloup.gameboykmp.logger.Logger
import com.wechantloup.gameboykmp.ui.GameBoyViewModel
import com.wechantloup.gameboykmp.ui.Palette
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull

class RomTestRunner {
    @Test
    fun `acid-which`() {
        runTest("acid/which.gb", 200)
    }

    @Test
    fun `acid-dmg-acid2`() {
        runTest("acid/dmg-acid2.gb", 200)
    }

    @Test
    fun `blarrg-cpu_instrs-01`() {
        runTest("blarrg/cpu_instrs/01-special.gb", 3_000)
    }

    @Test
    fun `blarrg-cpu_instrs-02`() {
        runTest("blarrg/cpu_instrs/02-interrupts.gb", 1_000)
    }

    @Test
    fun `blarrg-cpu_instrs-03`() {
        runTest("blarrg/cpu_instrs/03-op_sp,hl.gb", 3_000)
    }

    @Test
    fun `blarrg-cpu_instrs-04`() {
        runTest("blarrg/cpu_instrs/04-op_r,imm.gb", 3_000)
    }

    @Test
    fun `blarrg-cpu_instrs-05`() {
        runTest("blarrg/cpu_instrs/05-op_rp.gb", 4_000)
    }

    @Test
    fun `blarrg-cpu_instrs-06`() {
        runTest("blarrg/cpu_instrs/06-ld_r,r.gb", 1_000)
    }

    @Test
    fun `blarrg-cpu_instrs-07`() {
        runTest("blarrg/cpu_instrs/07-jr,jp,call,ret,rst.gb", 1_000)
    }

    @Test
    fun `blarrg-cpu_instrs-08`() {
        runTest("blarrg/cpu_instrs/08-misc_instrs.gb", 1_000)
    }

    @Test
    fun `blarrg-cpu_instrs-09`() {
        runTest("blarrg/cpu_instrs/09-op_r,r.gb", 10_000)
    }

    @Test
    fun `blarrg-cpu_instrs-10`() {
        runTest("blarrg/cpu_instrs/10-bit_ops.gb", 14_000)
    }

    @Test
    fun `blarrg-cpu_instrs-11`() {
        runTest("blarrg/cpu_instrs/11-op_a,(hl).gb", 18_000)
    }

    @Test
    fun `blarrg-halt_bug`() {
        runTest("blarrg/halt_bug.gb", 2_000)
    }

    @Test
    fun `blarrg-instr_timing`() {
        runTest("blarrg/instr_timing.gb", 1_000)
    }

    @Test
    fun `blarrg-interrupt_time`() {
        runTest("blarrg/interrupt_time.gb", 1_000)
    }

    @Test
    fun `blarrg-mem_timing-01-read_timing`() {
        runTest("blarrg/mem_timing/01-read_timing.gb", 500)
    }

    @Test
    fun `blarrg-mem_timing-02-write_timing`() {
        runTest("blarrg/mem_timing/02-write_timing.gb", 500)
    }

    @Test
    fun `blarrg-mem_timing-03-modify_timing`() {
        runTest("blarrg/mem_timing/03-modify_timing.gb", 500)
    }

    @Test
    fun `blarrg-mem_timing-2-01-read_timing`() {
        runTest("blarrg/mem_timing-2/01-read_timing.gb", 500)
    }

    @Test
    fun `blarrg-mem_timing-2-02-write_timing`() {
        runTest("blarrg/mem_timing-2/02-write_timing.gb", 500)
    }

    @Test
    fun `blarrg-mem_timing-2-03-modify_timing`() {
        runTest("blarrg/mem_timing-2/03-modify_timing.gb", 500)
    }
}

private fun runTest(romPath: String, duration: Long) {
    val romName = romPath.substring(
        romPath.lastIndexOf('/') + 1,
        romPath.lastIndexOf('.'),
    )
    val pngPath = romPath.substring(
        0,
        romPath.lastIndexOf('.'),
    ) + ".png"
    Logger.debug("TEST", pngPath)

    val rom = ClassLoader
        .getSystemResourceAsStream("roms/$romPath")
    val romBytes = requireNotNull(rom?.readBytes())

    val viewModel = GameBoyViewModel()

    viewModel.loadRom(romBytes, romName)

    Thread.sleep(duration)

    val frameBuffer = viewModel.stateFlow.value.frameBuffer
    assertNotNull(frameBuffer)

    val palette = Palette.DOC_BOY_TEST
    val imageBuffer = frameBuffer
        .map {
            palette.colors[it]
        }
        .toIntArray()

    imageBuffer.toPng("build/test-results/$romName.png")

    val reference = ImageIO
        .read(
            ClassLoader.getSystemResourceAsStream("references/$pngPath")
        )
        .getRGB(0, 0, 160, 144, null, 0, 160)

    assertContentEquals(reference, imageBuffer)
}

private fun IntArray.toPng(outputPath: String) {
    val image = BufferedImage(160, 144, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, 160, 144, this, 0, 160)
    ImageIO.write(image, "PNG", File(outputPath))
}
