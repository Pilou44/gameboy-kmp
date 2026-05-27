package com.wechantloup.gameboykmp

import com.wechantloup.gameboykmp.ui.GameBoyViewModel
import com.wechantloup.gameboykmp.ui.Palette
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull

class RomTestRunner {
    @Test
    fun `acid-dmg-acid2`() {
        val rom = ClassLoader
            .getSystemResourceAsStream("roms/acid/dmg-acid2.gb")
        val romBytes = requireNotNull(rom?.readBytes())

        val viewModel = GameBoyViewModel()

        viewModel.loadRom(romBytes, "dmg-acid2")

        Thread.sleep(10_000) // 10 seconds, adjust as needed

        val frameBuffer = viewModel.stateFlow.value.frameBuffer
        assertNotNull(frameBuffer)

        val palette = Palette.TRUE_POCKET
        val imageBuffer = frameBuffer
            .map {
                palette.colors[it]
            }
            .toIntArray()

        imageBuffer.toPng("build/test-results/dmg-acid2-output.png")
    }
}

private fun IntArray.toPng(outputPath: String) {
    val image = BufferedImage(160, 144, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, 160, 144, this, 0, 160)
    ImageIO.write(image, "PNG", File(outputPath))
}
