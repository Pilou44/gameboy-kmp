package com.wechantloup.gameboykmp.ui.dmg

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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.wechantloup.gameboykmp.joypad.JoypadButton

//enum class DPadDirection { UP, DOWN, LEFT, RIGHT }

private val ColorBase      = Color(0xFF1C1C1E)
private val ColorPressed   = Color(0xFF0F0F10)
private val ColorHighlight = Color(0xFF3A3A3C)
private val ColorShadow    = Color(0x99000000)
private val ColorArrow     = Color(0xFF5A5A5E)

@Composable
fun DPad(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    onDirectionPressed: (JoypadButton) -> Unit = {},
    onDirectionReleased: (JoypadButton) -> Unit = {},
) {
    var pressedDirection by remember { mutableStateOf<JoypadButton?>(null) }

    Canvas(
        modifier = modifier
            .size(size)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val dir = hitTest(down.position, this.size)
                    pressedDirection = dir
                    dir?.let { onDirectionPressed(it) }
                    down.consume()

                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        val newDir = hitTest(change.position, this.size)
                        if (newDir != pressedDirection) {
                            pressedDirection?.let { onDirectionReleased(it) }
                            newDir?.let { onDirectionPressed(it) }
                            pressedDirection = newDir
                        }
                        change.consume()
                    } while (true)

                    pressedDirection?.let { onDirectionReleased(it) }
                    pressedDirection = null
                }
            }
    ) {
        drawDPad(pressedDirection)
    }
}

// ─── Hit-testing ────────────────────────────────────────────────────────────

private fun hitTest(position: Offset, size: IntSize): JoypadButton? {
    val x   = position.x
    val y   = position.y
    val arm = size.width.toFloat() / 3f

    val inVertical   = x in arm..(2f * arm)
    val inHorizontal = y in arm..(2f * arm)

    if (!inVertical && !inHorizontal) return null   // corner → outside cross

    return when {
        y < arm       -> JoypadButton.UP
        y > 2f * arm  -> JoypadButton.DOWN
        x < arm       -> JoypadButton.LEFT
        x > 2f * arm  -> JoypadButton.RIGHT
        else          -> null  // dead center, no direction
    }
}

// ─── Drawing ─────────────────────────────────────────────────────────────────

private fun DrawScope.drawDPad(pressed: JoypadButton?) {
    val w           = size.width
    val h           = size.height
    val arm         = w / 3f
    val cr          = CornerRadius(arm * 0.22f)
    // Each arm extends `overlap` pixels into the center so the center
    // square (drawn last, no rounding) hides the unwanted inner corners.
    val overlap     = 3f
    val shadowDx    = 2.5f
    val shadowDy    = 2.5f

    fun armColor(dir: JoypadButton) = if (pressed == dir) ColorPressed else ColorBase

    // ── Shadow ──────────────────────────────────────────────────────────────
    drawRoundRect(
        color     = ColorShadow,
        topLeft   = Offset(arm + shadowDx, shadowDy),
        size      = Size(arm, h),
        cornerRadius = cr,
    )
    drawRoundRect(
        color     = ColorShadow,
        topLeft   = Offset(shadowDx, arm + shadowDy),
        size      = Size(w, arm),
        cornerRadius = cr,
    )

    // ── Arms (each extends slightly past the junction) ───────────────────────
    // UP
    drawRoundRect(
        color        = armColor(JoypadButton.UP),
        topLeft      = Offset(arm, 0f),
        size         = Size(arm, arm + overlap),
        cornerRadius = cr,
    )
    // DOWN
    drawRoundRect(
        color        = armColor(JoypadButton.DOWN),
        topLeft      = Offset(arm, 2f * arm - overlap),
        size         = Size(arm, arm + overlap),
        cornerRadius = cr,
    )
    // LEFT
    drawRoundRect(
        color        = armColor(JoypadButton.LEFT),
        topLeft      = Offset(0f, arm),
        size         = Size(arm + overlap, arm),
        cornerRadius = cr,
    )
    // RIGHT
    drawRoundRect(
        color        = armColor(JoypadButton.RIGHT),
        topLeft      = Offset(2f * arm - overlap, arm),
        size         = Size(arm + overlap, arm),
        cornerRadius = cr,
    )

    // ── Center square (squares off the inner corners of every arm) ───────────
    drawRect(
        color   = ColorBase,
        topLeft = Offset(arm, arm),
        size    = Size(arm, arm),
    )

    // ── Highlight strips (simulate a slightly raised/glossy surface) ─────────
    val hlInset  = arm * 0.18f
    val hlThick  = arm * 0.09f
    val hlCr     = CornerRadius(hlThick / 2f)

    if (pressed != JoypadButton.UP) {
        drawRoundRect(ColorHighlight, Offset(arm + hlInset, arm * 0.07f),        Size(arm - 2f * hlInset, hlThick), hlCr)
    }
    if (pressed != JoypadButton.DOWN) {
        drawRoundRect(ColorHighlight, Offset(arm + hlInset, 3f * arm - arm * 0.07f - hlThick), Size(arm - 2f * hlInset, hlThick), hlCr)
    }
    if (pressed != JoypadButton.LEFT) {
        drawRoundRect(ColorHighlight, Offset(arm * 0.07f, arm + hlInset),        Size(hlThick, arm - 2f * hlInset), hlCr)
    }
    if (pressed != JoypadButton.RIGHT) {
        drawRoundRect(ColorHighlight, Offset(3f * arm - arm * 0.07f - hlThick, arm + hlInset), Size(hlThick, arm - 2f * hlInset), hlCr)
    }

    // ── Arrow triangles ──────────────────────────────────────────────────────
    val cx          = arm * 1.5f
    val cy          = arm * 1.5f
    val arrowSize   = arm * 0.23f
    val arrowInset  = arm * 0.27f

    drawArrow(Offset(cx, arrowInset),               JoypadButton.UP,    arrowSize)
    drawArrow(Offset(cx, 3f * arm - arrowInset),    JoypadButton.DOWN,  arrowSize)
    drawArrow(Offset(arrowInset, cy),               JoypadButton.LEFT,  arrowSize)
    drawArrow(Offset(3f * arm - arrowInset, cy),    JoypadButton.RIGHT, arrowSize)
}

private fun DrawScope.drawArrow(tip: Offset, direction: JoypadButton, s: Float) {
    val path = Path()
    when (direction) {
        JoypadButton.UP -> {
            path.moveTo(tip.x,        tip.y)
            path.lineTo(tip.x - s,   tip.y + s * 1.2f)
            path.lineTo(tip.x + s,   tip.y + s * 1.2f)
        }
        JoypadButton.DOWN -> {
            path.moveTo(tip.x,        tip.y)
            path.lineTo(tip.x - s,   tip.y - s * 1.2f)
            path.lineTo(tip.x + s,   tip.y - s * 1.2f)
        }
        JoypadButton.LEFT -> {
            path.moveTo(tip.x,        tip.y)
            path.lineTo(tip.x + s * 1.2f, tip.y - s)
            path.lineTo(tip.x + s * 1.2f, tip.y + s)
        }
        JoypadButton.RIGHT -> {
            path.moveTo(tip.x,        tip.y)
            path.lineTo(tip.x - s * 1.2f, tip.y - s)
            path.lineTo(tip.x - s * 1.2f, tip.y + s)
        }
        else-> {} //Ignore
    }
    path.close()
    drawPath(path, color = ColorArrow)
}

@Composable
@Preview
private fun DpadPreview() {
    MaterialTheme {
        DPad()
    }
}
