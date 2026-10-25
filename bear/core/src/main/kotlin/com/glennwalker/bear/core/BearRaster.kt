package com.glennwalker.bear.core

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

/**
 * An n × n bear. Cells are [EMPTY], [FUR] (lit), [INNER_EAR] (dim) or [HOLE]
 * (a feature cut out of the fur: an unlit dot that hides whatever is behind it).
 */
class BearGrid(
    val n: Int,
    val cells: ByteArray,
    val center: Int,
    val anchorBottom: Int,
    val halfWidth: Int,
    /** [Tint] ordinal per cell; almost all are [Tint.NONE]. */
    val tints: ByteArray = ByteArray(n * n),
) {
    operator fun get(x: Int, y: Int): Int = cells[y * n + x].toInt()

    fun tint(x: Int, y: Int): Tint = Tint.entries[tints[y * n + x].toInt()]

    companion object {
        const val EMPTY = 0
        const val FUR = 1
        const val INNER_EAR = 2
        const val HOLE = 3
    }
}

/** Rasterises the bear from rules at any size. A faithful port of the prototype's `build()`. */
object BearRaster {
    fun build(look: BearLook, n: Int): BearGrid {
        val g = BearGeometry(look, n)
        val cells = ByteArray(n * n)
        val tints = ByteArray(n * n)
        fun set(x: Int, y: Int, v: Int, tint: Tint = Tint.NONE) {
            if (x >= 0 && y >= 0 && x < n && y < n) {
                cells[y * n + x] = v.toByte()
                tints[y * n + x] = tint.ordinal.toByte()
            }
        }
        fun get(x: Int, y: Int): Int = if (x >= 0 && y >= 0 && x < n && y < n) cells[y * n + x].toInt() else 0
        fun hole(x: Int, y: Int) {
            val v = get(x, y)
            if (v == BearGrid.FUR || v == BearGrid.INNER_EAR) set(x, y, BearGrid.HOLE)
        }

        val k = g.k
        val mood = look.mood
        val worried = mood == Mood.WORRIED
        val sleepy = mood == Mood.SLEEPY
        val happy = mood == Mood.HAPPY
        val relieved = mood == Mood.RELIEVED
        val cub = look.age == Age.CUB

        // Body and ears.
        val ax = g.bodyX
        val er = g.earRadius
        val exr = g.earOffset
        val ety = g.earTop
        for (y in 0 until n) for (x in 0 until n) {
            val cx = x + 0.5
            val body = sq((cx - ax) / (g.halfWidth + 0.5)) + sq((y - g.middle) / g.radiusY) <= 1
            val leftEar = sq(cx - (ax - exr)) + sq((y + 0.5 - ety - 0.5)) <= er * er
            val rightEar = sq(cx - (ax + exr)) + sq((y + 0.5 - ety - 0.5)) <= er * er
            if (body || leftEar || rightEar) set(x, y, BearGrid.FUR)
        }

        // Inner ears.
        if (g.roomy && !look.back) {
            for (side in intArrayOf(-1, 1)) {
                val ecx = ax + side * exr
                val ecy = ety + 0.5
                var y = floor(ecy - er).toInt()
                while (y <= ecy + er) {
                    var x = floor(ecx - er).toInt()
                    while (x <= ecx + er) {
                        val d = hypot(x + 0.5 - ecx, y + 0.5 - ecy)
                        if (d <= er * g.innerEarFraction && y <= ety + (if (k == 2) 1 else 0) && get(x, y) == BearGrid.FUR) {
                            set(x, y, BearGrid.INNER_EAR)
                        }
                        x++
                    }
                    y++
                }
            }
        }

        // Feet.
        for (r in 0 until k) for (i in 0 until g.footWidth) {
            if (g.leftFootDown) set(g.leftFootX + g.footWidth - 1 - i, g.footY + r, BearGrid.FUR)
            if (g.rightFootDown) set(g.rightFootX + i, g.footY + r, BearGrid.FUR)
        }

        val grid = BearGrid(n, cells, g.center, g.anchorBottom, g.halfWidth, tints)
        if (look.back) return grid

        val blink = look.blinking
        val faceX = g.faceX
        if (g.roomy) {
            fun px(dx: Int) = g.faceCellX(dx)
            fun py(dy: Int) = g.faceCellY(dy)
            fun cut(dx: Int, dy: Int) { for (j in 0 until k) for (i in 0 until k) hole(px(dx) + i, py(dy) + j) }
            fun lit(dx: Int, dy: Int) { for (j in 0 until k) for (i in 0 until k) set(px(dx) + i, py(dy) + j, BearGrid.FUR) }

            fun eye(x0: Int, mirrored: Boolean) {
                if (sleepy || blink || relieved) { cut(x0, 1); cut(x0 + 1, 2); cut(x0 + 2, 1); return }
                if (happy) { cut(x0, 1); cut(x0 + 1, 0); cut(x0 + 2, 1); return }
                val y0 = if (worried) -1 else 0
                val eh = if (worried) 4 else 3
                for (j in 0 until eh) for (i in 0 until 3) cut(x0 + i, y0 + j)
                if (k == 2) {
                    // Rounded 6 × 6 eye, 2 × 2 shine and a second highlight.
                    val ex0 = px(x0)
                    val ey0 = py(y0)
                    val w = 6
                    val h = eh * 2
                    set(ex0, ey0, BearGrid.FUR); set(ex0 + w - 1, ey0, BearGrid.FUR)
                    set(ex0, ey0 + h - 1, BearGrid.FUR); set(ex0 + w - 1, ey0 + h - 1, BearGrid.FUR)
                    val sx = if (mirrored) ex0 + 3 else ex0 + 1
                    set(sx, ey0 + 1, BearGrid.FUR); set(sx + 1, ey0 + 1, BearGrid.FUR)
                    set(sx, ey0 + 2, BearGrid.FUR); set(sx + 1, ey0 + 2, BearGrid.FUR)
                    set(if (mirrored) ex0 + 1 else ex0 + 4, ey0 + h - 2, BearGrid.FUR)
                } else {
                    lit(if (mirrored) x0 + 2 else x0, y0)
                }
            }
            eye(-4, mirrored = false)
            eye(2, mirrored = true)

            if (worried) { cut(-2, -4); cut(-3, -4); cut(-4, -3); cut(2, -4); cut(3, -4); cut(4, -3) }

            val my: Int
            if (cub) {
                cut(0, 3)
                if (k == 2) { hole(px(0) - 1, py(3)); hole(px(0) + 2, py(3)) }
                my = 4
                if (!look.carry) when {
                    happy -> { cut(-1, my); cut(0, my); cut(1, my); cut(0, my + 1) }
                    worried -> { cut(0, my); cut(-1, my + 1); cut(1, my + 1) }
                    sleepy -> cut(0, my)
                    else -> { cut(-1, my); cut(1, my) }
                }
            } else {
                cut(-1, 3); cut(0, 3); cut(1, 3); cut(0, 4)
                my = 5
                if (!look.carry) when {
                    happy -> for ((i, j) in ADULT_GRIN) cut(i, my + j)
                    worried -> { cut(-1, my + 1); cut(1, my + 1); cut(-2, my + 2); cut(2, my + 2) }
                    sleepy -> {}
                    else -> { cut(-1, my + 1); cut(1, my + 1); cut(-2, my); cut(2, my) }
                }
            }

            if (look.carry) {
                val by = my + 1
                for (j in -2..2) for (i in -2..2) if (abs(i) + abs(j) <= 3) cut(i, by + j)
                for (j in -1..1) for (i in -1..1) lit(i, by + j)
            }
            if (look.puff > 0) {
                val big = look.puff > 1
                val hu = jsRound(g.halfWidth / k.toDouble()) + 1 + (if (big) 1 else 0)
                val pu = my - 1 - (if (big) 2 else 0)
                for ((i, j) in PUFF) lit(hu + i, pu + j)
                if (big) for ((i, j) in PUFF_BIG) lit(hu + i, pu + j)
            }
        } else {
            // Tiny face: dot eyes, dot nose, small mouth.
            val a = 2
            val c = faceX
            val ey = g.tinyEyeY
            hole(c - a, ey); hole(c + a, ey)
            if (!(sleepy || relieved || blink) && (worried || cub)) { hole(c - a, ey - 1); hole(c + a, ey - 1) }
            if (!look.carry) {
                if (cub) {
                    if (happy) { hole(c - 1, ey + 1); hole(c, ey + 1); hole(c + 1, ey + 1) } else if (!sleepy) hole(c, ey + 1)
                } else {
                    hole(c, ey + 1)
                    if (happy) { hole(c - 1, ey + 2); hole(c, ey + 2); hole(c + 1, ey + 2) } else if (!sleepy) { hole(c - 1, ey + 2); hole(c + 1, ey + 2) }
                }
            } else {
                set(c, ey + 2, BearGrid.FUR); set(c - 1, ey + 2, BearGrid.FUR); set(c + 1, ey + 2, BearGrid.FUR)
            }
        }

        fun block(x: Int, y: Int, tint: Tint) { for (j in 0 until k) for (i in 0 until k) set(x + i, y + j, BearGrid.FUR, tint) }
        if (worried && g.roomy) {
            val dx = g.center + g.sway + g.halfWidth + 2 * k
            val dy = g.top - k
            for ((i, j) in SWEAT) block(dx + i * k, dy + j * k, Tint.SWEAT)
        }
        if (sleepy && g.roomy) {
            val zx = g.center + g.halfWidth - k
            val zy = g.top - 4 * k - look.zFrame * k
            for ((i, j) in ZED) block(zx + i * k, zy + j * k, Tint.SLEEP)
        }
        return grid
    }

    private fun sq(v: Double) = v * v

    private val ADULT_GRIN = listOf(-2 to 0, -1 to 0, 0 to 0, 1 to 0, 2 to 0, -1 to 1, 0 to 1, 1 to 1)
    private val PUFF = listOf(0 to 0, 1 to 0, -1 to 0, 0 to 1, 0 to -1)
    private val PUFF_BIG = listOf(2 to 0, 1 to 1, 1 to -1)
    private val SWEAT = listOf(0 to 0, -1 to 1, 1 to 1, 0 to 1, 0 to 2)
    private val ZED = listOf(0 to 0, 1 to 0, 2 to 0, 1 to 1, 0 to 2, 1 to 2, 2 to 2)
}
