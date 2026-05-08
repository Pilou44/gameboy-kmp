package com.wechantloup.gameboykmp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity

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
