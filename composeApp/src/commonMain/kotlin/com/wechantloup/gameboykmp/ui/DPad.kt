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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

enum class DPadDirection { UP, DOWN, LEFT, RIGHT }

private val ColorBase      = Color(0xFF1C1C1E)
private val ColorPressed   = Color(0xFF0F0F10)
private val ColorHighlight = Color(0xFF3A3A3C)
private val ColorShadow    = Color(0x99000000)
private val ColorArrow     = Color(0xFF5A5A5E)

@Composable
fun DPad(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    onDirectionPressed: (DPadDirection) -> Unit = {},
    onDirectionReleased: (DPadDirection) -> Unit = {},
) {
    var pressedDirection by remember { mutableStateOf<DPadDirection?>(null) }

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

private fun hitTest(position: Offset, size: IntSize): DPadDirection? {
    val x   = position.x
    val y   = position.y
    val arm = size.width.toFloat() / 3f

    val inVertical   = x in arm..(2f * arm)
    val inHorizontal = y in arm..(2f * arm)

    if (!inVertical && !inHorizontal) return null   // corner → outside cross

    return when {
        y < arm       -> DPadDirection.UP
        y > 2f * arm  -> DPadDirection.DOWN
        x < arm       -> DPadDirection.LEFT
        x > 2f * arm  -> DPadDirection.RIGHT
        else          -> null  // dead center, no direction
    }
}

// ─── Drawing ─────────────────────────────────────────────────────────────────

private fun DrawScope.drawDPad(pressed: DPadDirection?) {
    val w           = size.width
    val h           = size.height
    val arm         = w / 3f
    val cr          = CornerRadius(arm * 0.22f)
    // Each arm extends `overlap` pixels into the center so the center
    // square (drawn last, no rounding) hides the unwanted inner corners.
    val overlap     = 3f
    val shadowDx    = 2.5f
    val shadowDy    = 2.5f

    fun armColor(dir: DPadDirection) = if (pressed == dir) ColorPressed else ColorBase

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
        color        = armColor(DPadDirection.UP),
        topLeft      = Offset(arm, 0f),
        size         = Size(arm, arm + overlap),
        cornerRadius = cr,
    )
    // DOWN
    drawRoundRect(
        color        = armColor(DPadDirection.DOWN),
        topLeft      = Offset(arm, 2f * arm - overlap),
        size         = Size(arm, arm + overlap),
        cornerRadius = cr,
    )
    // LEFT
    drawRoundRect(
        color        = armColor(DPadDirection.LEFT),
        topLeft      = Offset(0f, arm),
        size         = Size(arm + overlap, arm),
        cornerRadius = cr,
    )
    // RIGHT
    drawRoundRect(
        color        = armColor(DPadDirection.RIGHT),
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

    if (pressed != DPadDirection.UP) {
        drawRoundRect(ColorHighlight, Offset(arm + hlInset, arm * 0.07f),        Size(arm - 2f * hlInset, hlThick), hlCr)
    }
    if (pressed != DPadDirection.DOWN) {
        drawRoundRect(ColorHighlight, Offset(arm + hlInset, 3f * arm - arm * 0.07f - hlThick), Size(arm - 2f * hlInset, hlThick), hlCr)
    }
    if (pressed != DPadDirection.LEFT) {
        drawRoundRect(ColorHighlight, Offset(arm * 0.07f, arm + hlInset),        Size(hlThick, arm - 2f * hlInset), hlCr)
    }
    if (pressed != DPadDirection.RIGHT) {
        drawRoundRect(ColorHighlight, Offset(3f * arm - arm * 0.07f - hlThick, arm + hlInset), Size(hlThick, arm - 2f * hlInset), hlCr)
    }

    // ── Arrow triangles ──────────────────────────────────────────────────────
    val cx          = arm * 1.5f
    val cy          = arm * 1.5f
    val arrowSize   = arm * 0.23f
    val arrowInset  = arm * 0.27f

    drawArrow(Offset(cx, arrowInset),               DPadDirection.UP,    arrowSize)
    drawArrow(Offset(cx, 3f * arm - arrowInset),    DPadDirection.DOWN,  arrowSize)
    drawArrow(Offset(arrowInset, cy),               DPadDirection.LEFT,  arrowSize)
    drawArrow(Offset(3f * arm - arrowInset, cy),    DPadDirection.RIGHT, arrowSize)
}

private fun DrawScope.drawArrow(tip: Offset, direction: DPadDirection, s: Float) {
    val path = Path()
    when (direction) {
        DPadDirection.UP -> {
            path.moveTo(tip.x,        tip.y)
            path.lineTo(tip.x - s,   tip.y + s * 1.2f)
            path.lineTo(tip.x + s,   tip.y + s * 1.2f)
        }
        DPadDirection.DOWN -> {
            path.moveTo(tip.x,        tip.y)
            path.lineTo(tip.x - s,   tip.y - s * 1.2f)
            path.lineTo(tip.x + s,   tip.y - s * 1.2f)
        }
        DPadDirection.LEFT -> {
            path.moveTo(tip.x,        tip.y)
            path.lineTo(tip.x + s * 1.2f, tip.y - s)
            path.lineTo(tip.x + s * 1.2f, tip.y + s)
        }
        DPadDirection.RIGHT -> {
            path.moveTo(tip.x,        tip.y)
            path.lineTo(tip.x - s * 1.2f, tip.y - s)
            path.lineTo(tip.x - s * 1.2f, tip.y + s)
        }
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
