package com.wechantloup.gameboykmp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize

private const val SCALE = 3

const val GAME_BOY_SCREEN_WIDTH_PX = 160
const val GAME_BOY_SCREEN_HEIGHT_PX = 144

@Composable
fun BitmapGameBoyScreen(
    coloredFrameBuffer: IntArray,
    scale: Int = SCALE,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(coloredFrameBuffer) {
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
