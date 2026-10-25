package com.glennwalker.bear.ui.matrix

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.glennwalker.bear.core.Tint
import com.glennwalker.bear.ui.theme.Led

/**
 * A [cols] × [rows] grid of round LEDs fitted into the available space, centred.
 * Dot spacing and size follow the prototype: 6 px pitch, 2.4 px radius.
 */
class DotLayout(width: Float, height: Float, val cols: Int, val rows: Int) {
    val pitch = minOf(width / cols, height / rows)
    val left = (width - pitch * cols) / 2
    val top = (height - pitch * rows) / 2
    val diameter = pitch * 0.8f

    fun center(col: Int, row: Int) = Offset(left + (col + 0.5f) * pitch, top + (row + 0.5f) * pitch)

    /** Grid position for a point on screen, in (fractional) dots. */
    fun gridX(px: Float) = ((px - left) / pitch).toDouble()
    fun gridY(py: Float) = ((py - top) / pitch).toDouble()
}

/** Renders every unlit dot once into a bitmap, so each frame only draws lit and dim dots. */
fun CacheDrawScope.unlitDots(layout: DotLayout): ImageBitmap {
    val bitmap = ImageBitmap(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
    CanvasDrawScope().draw(this, layoutDirection, Canvas(bitmap), size) {
        drawRect(Led.Background)
        val points = ArrayList<Offset>(layout.cols * layout.rows)
        for (r in 0 until layout.rows) for (c in 0 until layout.cols) points += layout.center(c, r)
        drawDots(points, Led.Off, layout)
    }
    return bitmap
}

fun DrawScope.drawDots(points: List<Offset>, color: Color, layout: DotLayout) {
    if (points.isNotEmpty()) {
        drawPoints(points, PointMode.Points, color, strokeWidth = layout.diameter, cap = StrokeCap.Round)
    }
}

/**
 * A dot-matrix display. [paint] is called every draw with a [Dots] to fill with lit and dim
 * dot positions (grid columns and rows), each with an optional neon [Tint]; everything
 * else shows as an unlit dot.
 */
@Composable
fun DotMatrix(
    cols: Int,
    rows: Int,
    modifier: Modifier = Modifier,
    paint: (Dots) -> Unit,
) {
    // Read through state so a new `paint` redraws without rebuilding the unlit-dot bitmap.
    val currentPaint by rememberUpdatedState(paint)
    Spacer(
        modifier.drawWithCache {
            val layout = DotLayout(size.width, size.height, cols, rows)
            val unlit = unlitDots(layout)
            val dots = Dots(layout)
            onDrawBehind {
                dots.clear()
                currentPaint(dots)
                drawImage(unlit)
                dots.draw(this)
            }
        },
    )
}

/** Collects one frame's dots, grouped by colour, skipping any outside the grid. */
class Dots(private val layout: DotLayout) {
    private val byColor = HashMap<Int, ArrayList<Offset>>()

    fun lit(col: Int, row: Int, tint: Tint = Tint.NONE) = add(col, row, tint.lit)

    fun dim(col: Int, row: Int, tint: Tint = Tint.NONE) = add(col, row, tint.dim)

    private fun add(col: Int, row: Int, rgb: Int) {
        if (col in 0 until layout.cols && row in 0 until layout.rows) byColor.getOrPut(rgb) { ArrayList() } += layout.center(col, row)
    }

    fun clear() = byColor.values.forEach { it.clear() }

    fun draw(scope: DrawScope) {
        for ((rgb, points) in byColor) scope.drawDots(points, Color(0xFF000000.toInt() or rgb), layout)
    }
}
