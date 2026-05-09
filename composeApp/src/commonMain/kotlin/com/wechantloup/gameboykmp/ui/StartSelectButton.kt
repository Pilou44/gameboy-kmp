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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ─── Colors ───────────────────────────────────────────────────────────────────
private val PillBase      = Color(0xFF4A4A52)
private val PillPressed   = Color(0xFF2E2E34)
private val PillRim       = Color(0xFF1C1C1E)
private val PillHighlight = Color(0xFF72727C)
private val PillShadow    = Color(0x99000000)
private val LabelColor    = Color(0xFF5A5A62)

// Tilt angle matching the original DMG — negative = tilted left like /
private const val TILT_DEG = -25f

/**
 * A single Start or Select button in Game Boy DMG style.
 *
 * The pill and its label are drawn on a tilted axis.
 * Duplicate this composable and pass different [label] values
 * ("START" / "SELECT") and wire up [onPressed] / [onReleased].
 *
 * Usage:
 * ```
 * val nintendoFont = FontFamily(Font("nintend_bold.ttf"))
 *
 * Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
 *     StartSelectButton(label = "SELECT", fontFamily = nintendoFont,
 *         onPressed = { … }, onReleased = { … })
 *     StartSelectButton(label = "START",  fontFamily = nintendoFont,
 *         onPressed = { … }, onReleased = { … })
 * }
 * ```
 */
@Composable
fun StartSelectButton(
    label: String,
    modifier: Modifier = Modifier,
    pillWidth: Dp = 44.dp,
    pillHeight: Dp = 14.dp,
    fontFamily: FontFamily = FontFamily.Default,
    onPressed: () -> Unit = {},
    onReleased: () -> Unit = {},
) {
    val density      = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    var pressed by remember { mutableStateOf(false) }

    val pillW   = with(density) { pillWidth.toPx() }
    val pillH   = with(density) { pillHeight.toPx() }
    val tiltRad = abs(TILT_DEG) * PI.toFloat() / 180f

    // After rotating by TILT_DEG, the bounding box of the pill changes.
    // We add enough padding so nothing gets clipped.
    val padding = with(density) { 6.dp.toPx() }

    // The bounding box of the rotated pill
    val rotatedW = pillW * cos(tiltRad) + pillH * sin(tiltRad)
    val rotatedH = pillW * sin(tiltRad) + pillH * cos(tiltRad)

    // Label font size & metrics — measure it to know its height
    val labelFontSize = with(density) { (pillH * 0.85f / density.fontScale).sp }
    val labelGap      = with(density) { 4.dp.toPx() }
    val labelMeasured = textMeasurer.measure(
        text  = label,
        style = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize   = labelFontSize,
        ),
    )
    val labelH = labelMeasured.size.height.toFloat()
    val labelW = labelMeasured.size.width.toFloat()

    // Canvas size: enough for the tilted pill + label below it + padding
    val canvasW = with(density) { (rotatedW + padding * 2f).toDp() }
    val canvasH = with(density) { (rotatedH + labelGap + labelH + padding * 2f).toDp() }

    // Center of the pill inside the canvas (horizontally centered, near top)
    val pillCenterX = rotatedW / 2f + padding
    val pillCenterY = rotatedH / 2f + padding

    Canvas(
        modifier = modifier
            .size(width = canvasW, height = canvasH)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Simple bounding-box hit test on the rotated pill area
                    val inBounds = down.position.x in 0f..size.width.toFloat() &&
                            down.position.y in 0f..(rotatedH + padding * 2f)
                    if (inBounds) {
                        pressed = true
                        onPressed()
                        down.consume()
                        do {
                            val event  = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            change.consume()
                        } while (true)
                        pressed = false
                        onReleased()
                    }
                }
            }
    ) {
        // ── Pill (drawn in a rotated DrawScope) ──────────────────────────────
        rotate(degrees = TILT_DEG, pivot = Offset(pillCenterX, pillCenterY)) {
            drawPill(
                center  = Offset(pillCenterX, pillCenterY),
                width   = pillW,
                height  = pillH,
                pressed = pressed,
            )
        }

        // ── Label (also slightly rotated to follow the same axis) ────────────
        rotate(degrees = TILT_DEG, pivot = Offset(pillCenterX, pillCenterY)) {
            val labelX = pillCenterX - labelW / 2f
            val labelY = pillCenterY + pillH / 2f + labelGap
            drawText(
                textLayoutResult = labelMeasured,
                color            = LabelColor,
                topLeft          = Offset(labelX, labelY),
            )
        }
    }
}

// ─── Drawing ─────────────────────────────────────────────────────────────────

private fun DrawScope.drawPill(
    center: Offset,
    width: Float,
    height: Float,
    pressed: Boolean,
) {
    val left   = center.x - width  / 2f
    val top    = center.y - height / 2f
    val cr     = CornerRadius(height / 2f)

    // 1. Shadow
    if (!pressed) {
        drawRoundRect(
            color        = PillShadow,
            topLeft      = Offset(left + 1.5f, top + 2.5f),
            size         = Size(width, height),
            cornerRadius = cr,
        )
    }

    // 2. Main face
    drawRoundRect(
        color        = if (pressed) PillPressed else PillBase,
        topLeft      = Offset(left, top),
        size         = Size(width, height),
        cornerRadius = cr,
    )

    // 3. Rim
    drawRoundRect(
        color        = PillRim,
        topLeft      = Offset(left, top),
        size         = Size(width, height),
        cornerRadius = cr,
        style        = Stroke(width = height * 0.08f),
    )

    // 4. Highlight strip along the top edge of the pill
    if (!pressed) {
        val hlInset = height * 0.28f
        drawRoundRect(
            color        = PillHighlight,
            topLeft      = Offset(left + hlInset, top + height * 0.10f),
            size         = Size(width - hlInset * 2f, height * 0.22f),
            cornerRadius = CornerRadius(height * 0.11f),
        )
    }
}

@Preview
@Composable
private fun StartSelectButtonPreview() {
    MaterialTheme {
        StartSelectButton(
            label = "START",
        )
    }
}
