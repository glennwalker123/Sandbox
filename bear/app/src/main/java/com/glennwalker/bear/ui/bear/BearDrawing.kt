package com.glennwalker.bear.ui.bear

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.glennwalker.bear.core.Age
import com.glennwalker.bear.core.BearGeometry
import com.glennwalker.bear.core.BearLook
import com.glennwalker.bear.core.Mood
import kotlin.math.roundToInt

/**
 * Placeholder bear: flat vector shapes in the LED palette, laid out on the same
 * [BearGeometry] the dot-matrix rasteriser will use, so size and placement carry over.
 *
 * The bear fills an [n] × [n] cell box whose top-left is [origin]; one cell is [unit] px.
 */
fun DrawScope.drawBear(
    look: BearLook,
    n: Int,
    origin: Offset,
    unit: Float,
    fur: Color,
    innerEar: Color,
    feature: Color,
) {
    val g = BearGeometry(look, n)
    val k = g.k.toFloat()
    fun p(x: Double, y: Double) = Offset(origin.x + (x * unit).toFloat(), origin.y + (y * unit).toFloat())
    fun cells(x: Double, y: Double, w: Double, h: Double, color: Color, round: Float = 0.35f) =
        drawRoundRect(color, p(x, y), Size((w * unit).toFloat(), (h * unit).toFloat()), CornerRadius(round * unit * k))

    // Feet, body, ears.
    if (g.leftFootDown) cells(g.leftFootX.toDouble(), g.footY - 0.5, g.footWidth.toDouble(), g.k + 0.5, fur)
    if (g.rightFootDown) cells(g.rightFootX.toDouble(), g.footY - 0.5, g.footWidth.toDouble(), g.k + 0.5, fur)
    drawOval(
        fur,
        p(g.bodyX - g.radiusX, g.bodyCenterY - g.radiusY),
        Size((2 * g.radiusX * unit).toFloat(), (2 * g.radiusY * unit).toFloat()),
    )
    for (side in intArrayOf(-1, 1)) {
        val ex = g.bodyX + side * g.earOffset
        drawCircle(fur, (g.earRadius * unit).toFloat(), p(ex, g.earY))
        if (g.roomy && !look.back) {
            drawCircle(innerEar, (g.earRadius * g.innerEarFraction * unit).toFloat(), p(ex, g.earY - g.earRadius * 0.15))
        }
    }
    if (look.back) return

    val stroke = Stroke(width = unit * k * 0.95f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    // Face features are placed on the standard grid: (dx, dy) cell offsets from the face centre.
    val fc = g.faceX + if (g.k == 1) 0.5 else 0.0
    fun fx(dx: Double) = fc + dx * g.k
    fun fy(dy: Double) = g.eyeTop + (dy + 0.5) * g.k
    fun line(vararg pts: Pair<Double, Double>) {
        val path = Path()
        pts.forEachIndexed { i, (dx, dy) ->
            val o = p(fx(dx), fy(dy))
            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        }
        drawPath(path, feature, style = stroke)
    }
    fun curve(from: Pair<Double, Double>, via: Pair<Double, Double>, to: Pair<Double, Double>) {
        val a = p(fx(from.first), fy(from.second))
        val c = p(fx(via.first), fy(via.second))
        val b = p(fx(to.first), fy(to.second))
        // Control point so the curve passes through `via` at its midpoint.
        val cx = 2 * c.x - (a.x + b.x) / 2
        val cy = 2 * c.y - (a.y + b.y) / 2
        drawPath(Path().apply { moveTo(a.x, a.y); quadTo(a, cx, cy, b.x, b.y) }, feature, style = stroke)
    }
    fun dot(dx: Double, dy: Double, r: Double = 0.55) = drawCircle(feature, (r * k * unit).toFloat(), p(fx(dx), fy(dy)))
    fun halfDisc(dx: Double, dy: Double, w: Double, h: Double) {
        val left = p(fx(dx - w / 2), fy(dy) - 0.5 * g.k)
        drawArc(
            feature, 0f, 180f, useCenter = true,
            topLeft = Offset(left.x, left.y - (h * k * unit).toFloat()),
            size = Size((w * k * unit).toFloat(), (2 * h * k * unit).toFloat()),
        )
    }

    val cub = look.age == Age.CUB
    val mood = look.mood

    if (!g.roomy) {
        drawTinyFace(g, look, { x, y -> p(x, y) }, unit, feature)
        return
    }

    // Eyes: 3 × 3 (worried 3 × 4) with a shine dot in the top outer corner.
    for (side in intArrayOf(-1, 1)) {
        val x0 = if (side < 0) -4.0 else 2.0
        when {
            look.eyesClosed -> curve(x0 to 1.0, x0 + 1 to 2.0, x0 + 2 to 1.0)
            mood == Mood.HAPPY -> curve(x0 to 1.0, x0 + 1 to 0.0, x0 + 2 to 1.0)
            else -> {
                val y0 = if (mood == Mood.WORRIED) -1.0 else 0.0
                val h = if (mood == Mood.WORRIED) 4.0 else 3.0
                val tl = p(fx(x0 - 0.5), fy(y0 - 0.5))
                drawRoundRect(
                    feature, tl, Size(3 * k * unit, (h * k * unit).toFloat()),
                    CornerRadius(if (g.k == 2) 1.6f * unit else 0.6f * unit),
                )
                val shineX = if (side < 0) x0 else x0 + 2
                drawCircle(fur, 0.5f * k * unit, p(fx(shineX), fy(y0)))
                if (g.k == 2) {
                    val second = if (side < 0) x0 + 2 else x0
                    drawCircle(fur, 0.3f * k * unit, p(fx(second), fy(y0 + h - 1)))
                }
            }
        }
    }
    if (mood == Mood.WORRIED) {
        line(-4.0 to -3.0, -2.0 to -4.0)
        line(4.0 to -3.0, 2.0 to -4.0)
    }

    // Nose and mouth.
    val mouthY: Double
    if (cub) {
        if (g.k == 2) cells(fx(-1.0), fy(3.0) - 0.5 * g.k, 2.0 * g.k, g.k.toDouble(), feature, 0.5f) else dot(0.0, 3.0, 0.6)
        mouthY = 4.0
        if (!look.carry) when (mood) {
            Mood.HAPPY -> halfDisc(0.0, 4.0, 3.0, 1.5)
            Mood.WORRIED -> curve(-1.0 to 5.0, 0.0 to 4.0, 1.0 to 5.0)
            Mood.SLEEPY -> dot(0.0, 4.0, 0.45)
            else -> { dot(-1.0, 4.0); dot(1.0, 4.0) }
        }
    } else {
        val tri = Path().apply {
            val a = p(fx(-1.5), fy(2.5)); val b = p(fx(1.5), fy(2.5)); val c = p(fx(0.0), fy(4.4))
            moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close()
        }
        drawPath(tri, feature)
        drawPath(tri, feature, style = Stroke(width = unit * k * 0.6f, join = StrokeJoin.Round))
        mouthY = 5.0
        if (!look.carry) when (mood) {
            Mood.HAPPY -> halfDisc(0.0, 5.0, 5.0, 2.0)
            Mood.WORRIED -> line(-2.0 to 7.0, -1.0 to 6.0, 1.0 to 6.0, 2.0 to 7.0)
            Mood.SLEEPY -> {}
            else -> line(-2.0 to 5.0, -1.0 to 6.0, 0.0 to 5.0, 1.0 to 6.0, 2.0 to 5.0)
        }
    }

    if (look.carry) {
        val by = mouthY + 1
        drawCircle(feature, 2.6f * k * unit, p(fx(0.0), fy(by)))
        drawCircle(fur, 1.5f * k * unit, p(fx(0.0), fy(by)))
    }
    if (look.puff > 0) {
        val big = look.puff > 1
        val hu = (g.halfWidth / g.k.toDouble()).roundToInt() + 1 + if (big) 1 else 0
        val pu = mouthY - 1 - if (big) 2 else 0
        drawCircle(fur, (if (big) 1.9f else 1.3f) * k * unit, p(fx(hu + if (big) 0.5 else 0.0), fy(pu)))
    }

    // Sweat drop and Zs sit outside the body, off to the right.
    if (mood == Mood.WORRIED) {
        val dx = g.center + g.sway + g.halfWidth + 2.0 * g.k + 0.5 * g.k
        val dy = g.top - g.k.toDouble()
        val drop = Path().apply {
            val tip = p(dx, dy); val l = p(dx - 1.3 * g.k, dy + 2.2 * g.k); val r = p(dx + 1.3 * g.k, dy + 2.2 * g.k)
            moveTo(tip.x, tip.y); lineTo(l.x, l.y)
            val bottom = p(dx, dy + 3.0 * g.k)
            quadTo(l, bottom.x - 0.2f * unit, bottom.y + 0.4f * unit, r.x, r.y); close()
        }
        drawPath(drop, fur)
    }
    if (mood == Mood.SLEEPY) {
        val zx = (g.center + g.halfWidth - g.k).toDouble()
        val zy = (g.top - 4 * g.k - look.zFrame * g.k).toDouble()
        val zPath = Path().apply {
            val a = p(zx + 0.4, zy + 0.4); val b = p(zx + 3 * g.k - 0.4, zy + 0.4)
            val c = p(zx + 0.4, zy + 3 * g.k - 0.4); val d = p(zx + 3 * g.k - 0.4, zy + 3 * g.k - 0.4)
            moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y)
        }
        drawPath(zPath, fur, style = Stroke(width = unit * k * 0.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Quadratic curve from [from] (the current point), drawn as the equivalent cubic. */
internal fun Path.quadTo(from: Offset, cx: Float, cy: Float, x: Float, y: Float) {
    val t = 2f / 3
    cubicTo(from.x + t * (cx - from.x), from.y + t * (cy - from.y), x + t * (cx - x), y + t * (cy - y), x, y)
}

/** Below standard size: dot eyes, dot nose, small mouth. */
private fun DrawScope.drawTinyFace(
    g: BearGeometry,
    look: BearLook,
    p: (Double, Double) -> Offset,
    unit: Float,
    feature: Color,
) {
    val c = g.faceX + 0.5
    val ey = g.tinyEyeY + 0.5
    val cub = look.age == Age.CUB
    for (side in intArrayOf(-1, 1)) {
        val ex = c + side * 2
        if (look.eyesClosed) {
            drawLine(feature, p(ex - 0.5, ey + 0.2), p(ex + 0.5, ey + 0.2), unit * 0.6f, StrokeCap.Round)
        } else {
            val tall = look.mood == Mood.WORRIED || cub
            val top = if (tall) ey - 1.0 else ey - 0.5
            drawRoundRect(feature, p(ex - 0.45, top - 0.05), Size(unit * 0.9f, unit * (if (tall) 1.6f else 0.9f)), CornerRadius(unit * 0.45f))
        }
    }
    if (look.carry) return
    val happy = look.mood == Mood.HAPPY
    val sleepy = look.mood == Mood.SLEEPY
    if (cub) {
        if (happy) drawLine(feature, p(c - 1.0, ey + 1), p(c + 1.0, ey + 1), unit * 0.8f, StrokeCap.Round)
        else if (!sleepy) drawCircle(feature, unit * 0.45f, p(c, ey + 1))
    } else {
        drawCircle(feature, unit * 0.5f, p(c, ey + 1))
        if (happy) drawLine(feature, p(c - 1.0, ey + 2), p(c + 1.0, ey + 2), unit * 0.8f, StrokeCap.Round)
        else if (!sleepy) {
            drawCircle(feature, unit * 0.4f, p(c - 1, ey + 2))
            drawCircle(feature, unit * 0.4f, p(c + 1, ey + 2))
        }
    }
}

