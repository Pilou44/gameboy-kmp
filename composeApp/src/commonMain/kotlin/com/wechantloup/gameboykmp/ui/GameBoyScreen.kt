package com.wechantloup.gameboykmp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private const val SCALE = 3

@Composable
fun GameBoyScreen(
    frameBuffer: IntArray,
    palette: Palette = Palette.Dmg,
    scale: Int = SCALE,
) {
    Canvas(
        modifier = Modifier.size(
            width = (160 * scale).dp,
            height = (144 * scale).dp
        )
    ) {
        for (y in 0 until 144) {
            for (x in 0 until 160) {
                val paletteColor = frameBuffer[y * 160 + x]
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
