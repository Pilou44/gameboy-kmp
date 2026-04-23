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
fun GameBoyScreen(frameBuffer: IntArray) {
    Canvas(
        modifier = Modifier.size(
            width = (160 * SCALE).dp,
            height = (144 * SCALE).dp
        )
    ) {
        for (y in 0 until 144) {
            for (x in 0 until 160) {
                val argb = frameBuffer[y * 160 + x]
                drawRect(
                    color = Color(argb),
                    topLeft = Offset(
                        x = (x * SCALE).toFloat(),
                        y = (y * SCALE).toFloat()
                    ),
                    size = Size(SCALE.toFloat(), SCALE.toFloat())
                )
            }
        }
    }
}
