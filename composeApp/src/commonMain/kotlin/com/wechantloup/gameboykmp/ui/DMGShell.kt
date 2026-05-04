package com.wechantloup.gameboykmp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun DmgShell(
    scale: Int,
    screenBorderColor: Int = Palette.Dmg.colors[0],
    screen: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scaleDp = with(density) { scale.toDp() }

    val screenWidth = scaleDp * 160
    val screenHeight = scaleDp * 144

    val shellWidth = scaleDp * 318
    val shellHeight = scaleDp * 528
    Box(
        modifier = Modifier
            .requiredSize(
                width = shellWidth,
                height = shellHeight,
            )
            .clip(
                RoundedCornerShape(
                    topStart = scaleDp * 14.6f,
                    topEnd = scaleDp * 14.6f,
                    bottomStart = scaleDp * 14.6f,
                    bottomEnd = scaleDp * 71.9f,
                )
            )
            .background(Color(0xFFC0C0C0)),
    ) {

        val grayZoneWidth = scaleDp * 270.5f
        val grayZoneHeight = scaleDp * 205.9f
        Box(
            modifier = Modifier
                .width(grayZoneWidth)
                .height(grayZoneHeight)
                .offset(
                    x = (shellWidth - grayZoneWidth) / 2,
                    y = scaleDp * 42.6f,
                )
                .clip(
                    RoundedCornerShape(
                        topStart = scaleDp * 11,
                        topEnd = scaleDp * 11,
                        bottomStart = scaleDp * 11,
                        bottomEnd = scaleDp * 45,
                    )
                )
                .background(Color(0xFF7B747C)),
        ) {
            val screenBorderWidth = scaleDp * 171.8f
            val screenBorderHeight = scaleDp * 156f
            Box(
                modifier = Modifier
                    .width(screenBorderWidth)
                    .height(screenBorderHeight)
                    .offset(
                        x = scaleDp * 50f,
                        y = scaleDp * 24.4f,
                    )
                    .background(Color(screenBorderColor)),
            ) {
                Box(
                    modifier = Modifier
                        .width(screenWidth)
                        .height(screenHeight)
                        .offset(
                            x = (screenBorderWidth - screenWidth) / 2,
                            y = (screenBorderHeight - screenHeight) / 2,
                        )
                        .background(Color.Black)
                ) {
                    screen()
                }
            }
        }
    }
}

@Preview
@Composable
fun DmgShellScale1Preview() {
    MaterialTheme {
        DmgShell(scale = 1) {}
    }
}

@Preview
@Composable
fun DmgShellScale3Preview() {
    MaterialTheme {
        DmgShell(scale = 3) {}
    }
}
