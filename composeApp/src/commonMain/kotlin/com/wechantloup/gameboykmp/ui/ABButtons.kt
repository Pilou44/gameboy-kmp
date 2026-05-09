package com.wechantloup.gameboykmp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class ABButton { A, B }

// ─── Colors (original Game Boy DMG palette) ───────────────────────────────────
private val BtnBase      = Color(0xFF8C1C3C)
private val BtnPressed   = Color(0xFF5C1026)
private val BtnRim       = Color(0xFF3A0A18)
private val BtnHighlight = Color(0xFFB83060)
private val BtnSpecular  = Color(0xFFD45080)
private val BtnShadow    = Color(0x99000000)

// Tilt of the B→A axis: 30° from horizontal, matching the original Game Boy
private const val TILT_DEG = 30.0

// ─── Public composable ────────────────────────────────────────────────────────

/**
 * Renders the A and B buttons in Game Boy DMG style.
 *
 * The buttons are arranged along a 30° axis (B lower-left, A upper-right).
 * Pass your `nintend_bold` FontFamily via [fontFamily].
 *
 * Usage:
 * ```
 * val nintendoFont = FontFamily(Font("nintend_bold.ttf"))
 *
 * ABButtons(
 *     fontFamily       = nintendoFont,
 *     onButtonPressed  = { btn -> /* send JoypadEvent */ },
 *     onButtonReleased = { btn -> /* release JoypadEvent */ },
 * )
 * ```
 */
@Composable
fun ABButtons(
    modifier: Modifier = Modifier,
    buttonRadius: Dp = 26.dp,
    spacing: Dp = 18.dp,            // edge-to-edge gap between A and B
    fontFamily: FontFamily = FontFamily.Default,
    onButtonPressed: (ABButton) -> Unit = {},
    onButtonReleased: (ABButton) -> Unit = {},
) {
    val density      = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    var pressedButton by remember { mutableStateOf<ABButton?>(null) }

    // All geometry in pixels
    val radiusPx  = with(density) { buttonRadius.toPx() }
    val spacingPx = with(density) { spacing.toPx() }
    val padding   = radiusPx * 0.5f

    val angleRad = TILT_DEG * PI / 180.0
    val d  = 2f * radiusPx + spacingPx      // center-to-center distance
    val dx = (d * cos(angleRad)).toFloat()
    val dy = (d * sin(angleRad)).toFloat()

    // Canvas dimensions (converted back to Dp for Modifier.size)
    val canvasW = with(density) { (dx + 2f * radiusPx + 2f * padding).toDp() }
    val canvasH = with(density) { (dy + 2f * radiusPx + 2f * padding).toDp() }

    // B = lower-left, A = upper-right
    val bCenter = Offset(padding + radiusPx,        padding + radiusPx + dy)
    val aCenter = Offset(padding + radiusPx + dx,   padding + radiusPx)

    // Font size in sp (pixels → dp → sp, accounting for fontScale)
    val fontSize: TextUnit = (radiusPx / density.density / density.fontScale * 0.9f).sp

    Canvas(
        modifier = modifier
            .size(width = canvasW, height = canvasH)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val btn  = hitTest(down.position, aCenter, bCenter, radiusPx)
                    pressedButton = btn
                    btn?.let { onButtonPressed(it) }
                    down.consume()

                    do {
                        val event  = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        val newBtn = hitTest(change.position, aCenter, bCenter, radiusPx)
                        if (newBtn != pressedButton) {
                            pressedButton?.let { onButtonReleased(it) }
                            newBtn?.let { onButtonPressed(it) }
                            pressedButton = newBtn
                        }
                        change.consume()
                    } while (true)

                    pressedButton?.let { onButtonReleased(it) }
                    pressedButton = null
                }
            }
    ) {
        // Draw B first so A renders on top if they ever overlap
        drawButton(
            label        = "B",
            center       = bCenter,
            radius       = radiusPx,
            pressed      = pressedButton == ABButton.B,
            textMeasurer = textMeasurer,
            fontFamily   = fontFamily,
            fontSize     = fontSize,
        )
        drawButton(
            label        = "A",
            center       = aCenter,
            radius       = radiusPx,
            pressed      = pressedButton == ABButton.A,
            textMeasurer = textMeasurer,
            fontFamily   = fontFamily,
            fontSize     = fontSize,
        )
    }
}

// ─── Hit-testing ─────────────────────────────────────────────────────────────

private fun hitTest(
    position: Offset,
    aCenter: Offset,
    bCenter: Offset,
    radius: Float,
): ABButton? {
    fun dist(p: Offset, c: Offset): Float {
        val ex = p.x - c.x
        val ey = p.y - c.y
        return sqrt(ex * ex + ey * ey)
    }
    return when {
        dist(position, aCenter) <= radius -> ABButton.A
        dist(position, bCenter) <= radius -> ABButton.B
        else                              -> null
    }
}

// ─── Drawing ─────────────────────────────────────────────────────────────────

private fun DrawScope.drawButton(
    label: String,
    center: Offset,
    radius: Float,
    pressed: Boolean,
    textMeasurer: TextMeasurer,
    fontFamily: FontFamily,
    fontSize: TextUnit,
) {
    // 1. Drop shadow (only when not pressed — pressed buttons "sink in")
    if (!pressed) {
        drawCircle(
            color  = BtnShadow,
            radius = radius,
            center = center + Offset(2.5f, 3.5f),
        )
    }

    // 2. Main button face
    drawCircle(
        color  = if (pressed) BtnPressed else BtnBase,
        radius = radius,
        center = center,
    )

    // 3. Outer rim (thin dark ring simulating the edge of the plastic cap)
    drawCircle(
        color  = BtnRim,
        radius = radius,
        center = center,
        style  = Stroke(width = radius * 0.07f),
    )

    // 4. Highlight arc (top-left, 120° sweep → light comes from above-left)
    if (!pressed) {
        val hlRadius = radius * 0.80f
        drawArc(
            color      = BtnHighlight,
            startAngle = 205f,
            sweepAngle = 130f,
            useCenter  = false,
            topLeft    = Offset(center.x - hlRadius, center.y - hlRadius),
            size       = Size(hlRadius * 2f, hlRadius * 2f),
            style      = Stroke(width = radius * 0.11f, cap = StrokeCap.Round),
        )

        // 5. Small specular dot (upper-left quadrant)
        drawCircle(
            color  = BtnSpecular.copy(alpha = 0.50f),
            radius = radius * 0.20f,
            center = center + Offset(-radius * 0.40f, -radius * 0.40f),
        )
    }

    // 6. Label (A / B)
    val textColor = Color.White.copy(alpha = if (pressed) 0.65f else 1f)
    val measured  = textMeasurer.measure(
        text  = label,
        style = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize   = fontSize,
            color      = textColor,
        ),
    )
    // Shift text 1 px down when pressed to reinforce the "sinking" illusion
    val textOffset = Offset(
        x = center.x - measured.size.width  / 2f,
        y = center.y - measured.size.height / 2f + if (pressed) 1.5f else -1f,
    )
    drawText(measured, topLeft = textOffset)
}

@Composable
@Preview
fun ABButtonsPreview() {
    MaterialTheme {
        ABButtons()
    }
}
