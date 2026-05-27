package com.wechantloup.gameboykmp

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
        val rom = ClassLoader
            .getSystemResourceAsStream("roms/acid/which.gb")
        val romBytes = requireNotNull(rom?.readBytes())

        val viewModel = GameBoyViewModel()

        viewModel.loadRom(romBytes, "which")

        Thread.sleep(1_000)

        val frameBuffer = viewModel.stateFlow.value.frameBuffer
        assertNotNull(frameBuffer)

        val palette = Palette.DOC_BOY_TEST
        val imageBuffer = frameBuffer
            .map {
                palette.colors[it]
            }
            .toIntArray()

        imageBuffer.toPng("build/test-results/which.png")

        val reference = ImageIO
            .read(
                ClassLoader.getSystemResourceAsStream("references/acid/which.png")
            )
            .getRGB(0, 0, 160, 144, null, 0, 160)

        assertContentEquals(reference, imageBuffer)
    }

    @Test
    fun `acid-dmg-acid2`() {
        val rom = ClassLoader
            .getSystemResourceAsStream("roms/acid/dmg-acid2.gb")
        val romBytes = requireNotNull(rom?.readBytes())

        val viewModel = GameBoyViewModel()

        viewModel.loadRom(romBytes, "dmg-acid2")

        Thread.sleep(1_000)

        val frameBuffer = viewModel.stateFlow.value.frameBuffer
        assertNotNull(frameBuffer)

        val palette = Palette.DOC_BOY_TEST
        val imageBuffer = frameBuffer
            .map {
                palette.colors[it]
            }
            .toIntArray()

        imageBuffer.toPng("build/test-results/dmg-acid2-output.png")

        val reference = ImageIO
            .read(
                ClassLoader.getSystemResourceAsStream("references/acid/dmg-acid2.png")
            )
            .getRGB(0, 0, 160, 144, null, 0, 160)

        assertContentEquals(reference, imageBuffer)
    }
}

prvate fun runTest(romPath: String) {

}

private fun IntArray.toPng(outputPath: String) {
    val image = BufferedImage(160, 144, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, 160, 144, this, 0, 160)
    ImageIO.write(image, "PNG", File(outputPath))
}
