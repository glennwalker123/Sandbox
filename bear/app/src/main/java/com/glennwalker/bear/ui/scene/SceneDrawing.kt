package com.glennwalker.bear.ui.scene

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.glennwalker.bear.core.BearGeometry
import com.glennwalker.bear.core.BearSim
import com.glennwalker.bear.core.Floor
import com.glennwalker.bear.core.Food
import com.glennwalker.bear.ui.bear.drawBear
import com.glennwalker.bear.ui.bear.quadTo
import com.glennwalker.bear.ui.theme.Led
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Maps the 120 × 200 scene canvas onto the available space, centred, keeping its shape.
 */
class SceneFrame(width: Float, height: Float) {
    val unit = minOf(width / Floor.WIDTH, height / Floor.HEIGHT)
    val left = (width - unit * Floor.WIDTH) / 2
    val top = (height - unit * Floor.HEIGHT) / 2

    fun toScreen(x: Double, y: Double) = Offset(left + (x * unit).toFloat(), top + (y * unit).toFloat())
    fun sceneX(px: Float) = ((px - left) / unit).toDouble()
    fun sceneY(py: Float) = ((py - top) / unit).toDouble()
}

fun DrawScope.drawScene(sim: BearSim, frame: SceneFrame) {
    drawFloor(frame)

    val items = buildList<Pair<Double, DrawScope.() -> Unit>> {
        add(sim.z to { drawBearInScene(sim, frame) })
        if (sim.ballVisible) add(sim.ballSpot().z to { drawBall(sim, frame) })
        sim.food?.let { f -> add(f.z to { drawFood(f, frame) }) }
    }
    items.sortedByDescending { it.first }.forEach { it.second(this) }

    sim.hearts.forEach { drawHeart(frame, it.x - 5, it.y - 8, 10.0, Led.Lit) }
    drawHud(sim, frame)
}

/** A few faint depth lines so the floor reads without the dot grid. */
private fun DrawScope.drawFloor(frame: SceneFrame) {
    for (z in doubleArrayOf(Floor.Z_MIN, 0.4, 0.75, Floor.Z_MAX)) {
        val y = Floor.screenY(z)
        val half = 56.0
        drawLine(Led.Off, frame.toScreen(60 - half, y + 3), frame.toScreen(60 + half, y + 3), frame.unit * 0.6f)
    }
}

private fun DrawScope.shadow(frame: SceneFrame, cx: Double, cy: Double, rx: Double, ry: Double) {
    drawOval(
        Led.Shadow,
        frame.toScreen(cx - rx, cy - ry),
        Size((2 * rx * frame.unit).toFloat(), (2 * ry * frame.unit).toFloat()),
    )
}

private fun DrawScope.drawBearInScene(sim: BearSim, frame: SceneFrame) {
    val spot = sim.bearSpot()
    val look = sim.look()
    val g = BearGeometry(look, spot.n)
    shadow(frame, spot.groundX.toDouble(), spot.groundY + 3.0, g.halfWidth * 0.9, max(1.0, 2.4 * spot.n / 50))
    val x0 = spot.groundX - g.center
    val y0 = spot.groundY - spot.lift - g.anchorBottom - 1
    drawBear(look, spot.n, frame.toScreen(x0.toDouble(), y0.toDouble()), frame.unit, Led.Lit, Led.Dim, Led.Off)
}

private fun DrawScope.drawBall(sim: BearSim, frame: SceneFrame) {
    val b = sim.ballSpot()
    if (b.shadow) shadow(frame, b.x, b.y + b.radius + b.lift + 2, b.radius * 0.9, max(1.0, b.radius * 0.3))
    val c = frame.toScreen(b.x + 0.5, b.y + 0.5)
    drawCircle(Led.Lit, (b.radius * frame.unit).toFloat(), c)
    if (b.radius >= 4) {
        drawCircle(Led.Dim, (b.radius * 0.22 * frame.unit).toFloat(), frame.toScreen(b.x + 0.5 - b.radius * 0.4, b.y + 0.5 - b.radius * 0.4))
    }
}

private fun DrawScope.drawFood(food: Food, frame: SceneFrame) {
    val s = Floor.sizeAt(food.z)
    val gx = Floor.screenX(food.x, food.z).roundToInt() + 0.5
    val gy = Floor.screenY(food.z)
    shadow(frame, gx, gy.roundToInt() + 2.0, (2.4 * s + 0.6) * 1.8, max(1.0, 2 * s))
    val r = (2.4 * s + 0.6) * 2 * food.bite
    if (r < 1) return
    val cy = gy - food.height * 2 - r
    val center = frame.toScreen(gx, cy)
    drawCircle(Led.Lit, (r * frame.unit).toFloat(), center)
    drawCircle(Led.Dim, ((r - 1) * frame.unit).toFloat(), center)
    if (food.bite > 0.6 && s > 0.65) {
        // Leaf.
        val leaf = Path().apply {
            val a = frame.toScreen(gx - 0.5, cy - r - 4)
            val b = frame.toScreen(gx + 3.5, cy - r)
            val m = frame.toScreen(gx - 0.5, cy - r)
            moveTo(a.x, a.y)
            quadTo(a, m.x, m.y, b.x, b.y)
            quadTo(b, a.x + frame.unit, b.y - frame.unit, a.x, a.y)
            close()
        }
        drawPath(leaf, Led.Lit)
    }
}

/** Heart in a [size] × [size] box at scene position (x, y). */
fun DrawScope.drawHeart(frame: SceneFrame, x: Double, y: Double, size: Double, color: Color) {
    val path = Path().apply {
        val p = { u: Double, v: Double -> frame.toScreen(x + u * size, y + v * size) }
        val top = p(0.5, 0.28); val bottom = p(0.5, 0.95)
        moveTo(top.x, top.y)
        val l1 = p(0.3, -0.05); val l2 = p(-0.1, 0.25); val l3 = p(0.05, 0.55)
        cubicTo(l1.x, l1.y, l2.x, l2.y, l3.x, l3.y)
        lineTo(bottom.x, bottom.y)
        val r3 = p(0.95, 0.55); val r2 = p(1.1, 0.25); val r1 = p(0.7, -0.05)
        lineTo(r3.x, r3.y)
        cubicTo(r2.x, r2.y, r1.x, r1.y, top.x, top.y)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawBolt(frame: SceneFrame, x: Double, y: Double, size: Double) {
    val pts = listOf(0.62 to 0.0, 0.12 to 0.56, 0.48 to 0.56, 0.36 to 1.0, 0.88 to 0.42, 0.52 to 0.42, 0.62 to 0.0)
    val path = Path()
    pts.forEachIndexed { i, (u, v) ->
        val o = frame.toScreen(x + u * size, y + v * size)
        if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
    }
    path.close()
    drawPath(path, Led.Lit)
}

private fun DrawScope.drawCross(frame: SceneFrame, x: Double, y: Double, size: Double) {
    val bar = size * 0.36
    val r = CornerRadius((bar * 0.3 * frame.unit).toFloat())
    drawRoundRect(Led.Lit, frame.toScreen(x + (size - bar) / 2, y), Size((bar * frame.unit).toFloat(), (size * frame.unit).toFloat()), r)
    drawRoundRect(Led.Lit, frame.toScreen(x, y + (size - bar) / 2), Size((size * frame.unit).toFloat(), (bar * frame.unit).toFloat()), r)
}

/** Top-left HUD: happiness, energy, health, five segments each. */
private fun DrawScope.drawHud(sim: BearSim, frame: SceneFrame) {
    val stats = doubleArrayOf(sim.happiness, sim.energy, sim.health)
    stats.forEachIndexed { row, value ->
        val y = 8.0 + row * 14
        when (row) {
            0 -> drawHeart(frame, 8.0, y, 10.0, Led.Lit)
            1 -> drawBolt(frame, 8.0, y, 10.0)
            else -> drawCross(frame, 8.0, y, 10.0)
        }
        val filled = kotlin.math.ceil(value / 20 - 0.01).toInt()
        val corner = CornerRadius(frame.unit * 0.8f)
        for (i in 0 until 5) {
            val x = 22.0 + i * 5
            if (i < filled) {
                drawRoundRect(Led.Lit, frame.toScreen(x, y), Size(3 * frame.unit, 10 * frame.unit), corner)
            } else {
                drawRoundRect(Led.Dim, frame.toScreen(x, y + 8), Size(3 * frame.unit, 2 * frame.unit), corner)
            }
        }
    }
}
