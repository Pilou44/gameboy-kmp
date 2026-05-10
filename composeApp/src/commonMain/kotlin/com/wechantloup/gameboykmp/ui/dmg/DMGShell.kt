package com.wechantloup.gameboykmp.ui.dmg

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wechantloup.gameboykmp.ui.GAME_BOY_SCREEN_HEIGHT_PX
import com.wechantloup.gameboykmp.ui.GAME_BOY_SCREEN_WIDTH_PX
import com.wechantloup.gameboykmp.ui.Palette
import gameboykmp.composeapp.generated.resources.Res
import gameboykmp.composeapp.generated.resources.gill_sans
import gameboykmp.composeapp.generated.resources.nintend_bold
import org.jetbrains.compose.resources.Font

const val DMG_SHELL_COLOR = 0xFFC0C0C0

@Composable
fun DmgShell(
    scale: Int,
    screenBorderColor: Int = Palette.DMG.colors[0],
    screen: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scaleDp = with(density) { scale.toDp() }

    val screenWidth = scaleDp * GAME_BOY_SCREEN_WIDTH_PX
    val screenHeight = scaleDp * GAME_BOY_SCREEN_HEIGHT_PX

    val shellWidth = scaleDp * 318
    val shellHeight = scaleDp * 528

    val textMeasurer = rememberTextMeasurer()
    val fontScale = density.fontScale

    // The shell shape has asymmetric corners: the bottom-right is much more rounded,
    // matching the iconic DMG silhouette.
    val shellShape = RoundedCornerShape(
        topStart = scaleDp * 14.6f,
        topEnd = scaleDp * 14.6f,
        bottomStart = scaleDp * 14.6f,
        bottomEnd = scaleDp * 71.9f,
    )

    // Outer Box has no clip, so the bevel Canvas can draw across the shape boundary.
    Box(modifier = Modifier.requiredSize(width = shellWidth, height = shellHeight)) {

        // ── Shell body ──────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shellShape)
                .background(Color(DMG_SHELL_COLOR)),
        ) {
            val grayZoneWidth = scaleDp * 270.5f
            val grayZoneHeight = scaleDp * 205.9f

            val grayZoneShape = RoundedCornerShape(
                topStart = scaleDp * 11,
                topEnd = scaleDp * 11,
                bottomStart = scaleDp * 11,
                bottomEnd = scaleDp * 45,
            )

            // Outer Box for the screen recess area: no clip so its own bevel Canvas
            // can draw freely across the shape boundary.
            Box(
                modifier = Modifier
                    .width(grayZoneWidth)
                    .height(grayZoneHeight)
                    .offset(
                        x = (shellWidth - grayZoneWidth) / 2,
                        y = scaleDp * 42.6f,
                    )
            ) {
                // Gray recessed background
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(grayZoneShape)
                        .background(Color(0xFF7B747C))
                        .drawWithContent {
                            val startXLeft = (scaleDp * 11).toPx()
                            val startXRight = (scaleDp * 225.4f).toPx()
                            val startYTop = (scaleDp * 9.8f).toPx()
                            val lengthLeft = (scaleDp * 73.1f).toPx()
                            val lengthRight = (scaleDp * 32.9f).toPx()
                            val startYBottom = (scaleDp * 14.6f).toPx()
                            val stroke = (scaleDp * 1f).toPx()
                            drawContent()
                            drawLine(
                                color = Color(0xff781B54),
                                start = Offset(startXLeft, startYTop),
                                end = Offset(startXLeft + lengthLeft, startYTop),
                                strokeWidth = stroke.dp.toPx(),
                            )
                            drawLine(
                                color = Color(0xff0C065C),
                                start = Offset(startXLeft, startYBottom),
                                end = Offset(startXLeft + lengthLeft, startYBottom),
                                strokeWidth = stroke.dp.toPx(),
                            )
                            drawLine(
                                color = Color(0xff781B54),
                                start = Offset(startXRight, startYTop),
                                end = Offset(startXRight + lengthRight, startYTop),
                                strokeWidth = stroke.dp.toPx(),
                            )
                            drawLine(
                                color = Color(0xff0C065C),
                                start = Offset(startXRight, startYBottom),
                                end = Offset(startXRight + lengthRight, startYBottom),
                                strokeWidth = stroke.dp.toPx(),
                            )

                            val labelStyle = TextStyle(
                                fontSize = (scale * 3.9f / fontScale).sp,
                                color = Color(0xFFDDDDDD),
                            )
                            val text = "DOT MATRIX WITH STEREO SOUND"
                            val measuredLabel = textMeasurer.measure(text, labelStyle)

                            drawText(
                                textMeasurer = textMeasurer,
                                text = text,
                                style = labelStyle,
                                topLeft = Offset(
                                    // centered between the end of the left segment and the start of the right one
                                    x = (startXLeft + lengthLeft + startXRight) / 2 - measuredLabel.size.width / 2f,
                                    y = (startYTop + startYBottom) / 2 - measuredLabel.size.height / 2f,
                                ),
                            )
                        },
                ) {
                    val screenBorderWidth = scaleDp * 171.8f
                    val screenBorderHeight = scaleDp * 156f

                    // Colored screen border (palette-dependent)
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
                        // Screen pixel area
                        Box(
                            modifier = Modifier
                                .width(screenWidth)
                                .height(screenHeight)
                                .offset(
                                    x = (screenBorderWidth - screenWidth) / 2,
                                    y = (screenBorderHeight - screenHeight) / 2,
                                )
                        ) {
                            screen()
                        }
                    }
                }

                // ── Screen recess bevel ─────────────────────────────────────────────
                // The screen area is physically recessed into the shell, so the bevel
                // is inverted: dark on top/left, light on bottom/right.
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawBevel(
                        shape = grayZoneShape,
                        strokeWidth = scale * 2f,
                        inverted = true,
                    )
                }
            }

            // Directly in the shell body Box, after the gray zone Box
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = LocalDensity.current.density,
                    fontScale = 1f,
                )
            ) {
                val nintendoFamily = FontFamily(
                    Font(Res.font.nintend_bold)
                )

                val gameBoyFamily = FontFamily(
                    Font(Res.font.gill_sans)
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier
                        .width(grayZoneWidth)
                        .offset(
                            x= (shellWidth - grayZoneWidth) / 2,
                            y = scaleDp * 255f,
                        ),
                ) {
                    Text(
                        text = "Nintendo",
                        fontFamily = nintendoFamily,
                        fontSize = (scale * 8f).sp,
                        color = Color(0xFF04006B),
                        modifier = Modifier.offset(
                            y = -scaleDp * 6.1f,
                        )
                    )
                    Text(
                        text = "GAME BOY",
                        fontFamily = gameBoyFamily,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                        fontSize = (scale * 12f).sp,
                        color = Color(0xFF04006B),
                    )
                }
            }
        }

        // ── Shell outer bevel ───────────────────────────────────────────────────────
        // Light comes from the top-left, so the top and left edges are highlighted
        // and the bottom and right edges are in shadow.
        // Note: in Compose, sweepGradient starts at 3 o'clock and goes
        // counter-clockwise in screen space, so 0.25 = top, 0.75 = bottom.
        Canvas(modifier = Modifier.matchParentSize()) {
            drawBevel(
                shape = shellShape,
                strokeWidth = scale * 2.5f,
                inverted = false,
            )
        }
    }
}

/**
 * Draws a two-pass bevel stroke along the outline of [shape]:
 * - Pass 1: highlight (white) on the top and left edges.
 * - Pass 2: shadow (black) on the bottom and right edges.
 *
 * When [inverted] is true the roles are swapped, simulating a recessed surface.
 *
 * In Compose's sweepGradient the angle runs counter-clockwise in screen space
 * (Y-axis points down), so the positions are:
 *   0.25 → top edge
 *   0.50 → left edge
 *   0.75 → bottom edge
 *   0.00/1.00 → right edge
 */
private fun DrawScope.drawBevel(
    shape: Shape,
    strokeWidth: Float,
    inverted: Boolean,
) {
    val outline = shape.createOutline(size, layoutDirection, this)
    val stroke = Stroke(
        width = strokeWidth,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
    )
    val center = Offset(size.width / 2, size.height / 2)

    val highlightColor = if (inverted) Color.Black else Color.White
    val shadowColor = if (inverted) Color.White else Color.Black

    val highlightAlphaStrong = if (inverted) 0.40f else 0.65f
    val highlightAlphaLight = if (inverted) 0.20f else 0.30f
    val shadowAlphaStrong = if (inverted) 0.25f else 0.28f
    val shadowAlphaLight = if (inverted) 0.20f else 0.22f

    // Pass 1 — highlight on top (0.22–0.28) and left (0.42–0.58) edges
    drawOutline(
        outline = outline,
        brush = Brush.sweepGradient(
            colorStops = arrayOf(
                0.00f to Color.Transparent,
                0.18f to Color.Transparent,
                0.22f to highlightColor.copy(alpha = highlightAlphaStrong),
                0.28f to highlightColor.copy(alpha = highlightAlphaStrong),
                0.42f to highlightColor.copy(alpha = highlightAlphaLight),
                0.58f to highlightColor.copy(alpha = highlightAlphaLight),
                0.62f to Color.Transparent,
                1.00f to Color.Transparent,
            ),
            center = center,
        ),
        style = stroke,
    )

    // Pass 2 — shadow on right (0.00/1.00) and bottom (0.72–0.78) edges
    drawOutline(
        outline = outline,
        brush = Brush.sweepGradient(
            colorStops = arrayOf(
                0.00f to shadowColor.copy(alpha = shadowAlphaLight),
                0.08f to shadowColor.copy(alpha = shadowAlphaLight),
                0.12f to Color.Transparent,
                0.62f to Color.Transparent,
                0.72f to shadowColor.copy(alpha = shadowAlphaStrong),
                0.78f to shadowColor.copy(alpha = shadowAlphaStrong),
                0.88f to Color.Transparent,
                0.92f to shadowColor.copy(alpha = shadowAlphaLight),
                1.00f to shadowColor.copy(alpha = shadowAlphaLight),
            ),
            center = center,
        ),
        style = stroke,
    )
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

