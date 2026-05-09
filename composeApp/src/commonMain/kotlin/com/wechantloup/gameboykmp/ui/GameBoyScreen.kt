package com.wechantloup.gameboykmp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize

private const val SCALE = 3

const val GAME_BOY_SCREEN_WIDTH_PX = 160
const val GAME_BOY_SCREEN_HEIGHT_PX = 144

@Composable
fun GameBoyScreen(
    frameBuffer: IntArray,
    palette: Palette = Palette.Dmg,
    scale: Int = SCALE,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    Canvas(
        modifier = modifier.size(
            width = with(density) { (GAME_BOY_SCREEN_WIDTH_PX * scale).toDp() },
            height = with(density) { (GAME_BOY_SCREEN_HEIGHT_PX * scale).toDp() }
        )
    ) {
        for (y in 0 until GAME_BOY_SCREEN_HEIGHT_PX) {
            for (x in 0 until GAME_BOY_SCREEN_WIDTH_PX) {
                val paletteColor = frameBuffer[y * GAME_BOY_SCREEN_WIDTH_PX + x]
                val argb = palette.colors[paletteColor]
                drawRect(
                    color = Color(argb),
                    topLeft = Offset(
                        x = (x * scale).toFloat(),
                        y = (y * scale).toFloat()
                    ),
                    size = Size(scale.toFloat(), scale.toFloat())
                )
            }
        }
    }
}

@Composable
fun BitmapGameBoyScreen(
    frameBuffer: IntArray,
    palette: Palette = Palette.Dmg,
    scale: Int = SCALE,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(frameBuffer, palette) {
        val coloredFrameBuffer = IntArray(frameBuffer.size) { palette.colors[frameBuffer[it]] }
        intArrayToImageBitmap(
            pixels = coloredFrameBuffer,
            width = GAME_BOY_SCREEN_WIDTH_PX,
            height = GAME_BOY_SCREEN_HEIGHT_PX,
        )
    }

    val density = LocalDensity.current
    Canvas(
        modifier = modifier.size(
            width = with(density) { (GAME_BOY_SCREEN_WIDTH_PX * scale).toDp() },
            height = with(density) { (GAME_BOY_SCREEN_HEIGHT_PX * scale).toDp() }
        )
    ) {
        drawImage(
            imageBitmap,
            dstSize = IntSize(
                width = GAME_BOY_SCREEN_WIDTH_PX * scale,
                height = GAME_BOY_SCREEN_HEIGHT_PX * scale,
            ),
            filterQuality = FilterQuality.None,
        )
    }
}

expect fun intArrayToImageBitmap(pixels: IntArray, width: Int, height: Int): ImageBitmap
