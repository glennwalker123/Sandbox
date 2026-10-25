package com.glennwalker.bear.core

import com.glennwalker.bear.core.Floor.HEIGHT
import com.glennwalker.bear.core.Floor.WIDTH
import com.glennwalker.bear.core.Floor.screenX
import com.glennwalker.bear.core.Floor.screenY
import com.glennwalker.bear.core.Floor.sizeAt
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * The front screen as a 120 × 200 grid of dots. A faithful port of the prototype's `render()`.
 *
 * Each dot is [OFF], [LIT], [DIM] or [SHADOW] (dim on a checker pattern). [HOLE] is an unlit
 * dot that was drawn on purpose (a bear feature), so it hides what is behind it.
 */
class SceneRaster(private val hud: HudStyle = HudStyle.ROW) {
    val dots = ByteArray(WIDTH * HEIGHT)

    fun render(sim: BearSim): ByteArray {
        dots.fill(0)
        val items = buildList<Pair<Double, () -> Unit>> {
            add(sim.z to { drawBear(sim) })
            if (sim.ballVisible) add(sim.ballSpot().z to { drawBall(sim) })
            sim.food?.let { f ->
                add(f.z to { if (f.kind == FoodKind.BERRY) drawFood(f) else drawFoodSprite(f, Sprites.food(f.kind)) })
            }
        }
        items.sortedByDescending { it.first }.forEach { it.second() }
        sim.hearts.forEach { stamp(Sprites.HEART, it.x - 5, it.y - 8, 2) }
        drawHud(sim)
        return dots
    }

    private fun put(x: Double, y: Double, v: Int) = put(jsRound(x), jsRound(y), v)

    private fun put(x: Int, y: Int, v: Int) {
        if (x < 0 || y < 0 || x >= WIDTH || y >= HEIGHT || v == 0) return
        dots[y * WIDTH + x] = v.toByte()
    }

    private fun stamp(sprite: List<String>, x: Double, y: Double, k: Int = 1) {
        val x0 = jsRound(x)
        val y0 = jsRound(y)
        sprite.forEachIndexed { j, row ->
            row.forEachIndexed { i, ch ->
                val v = when (ch) { '#' -> LIT; '+' -> DIM; 'o' -> HOLE; else -> 0 }
                if (v != 0) for (b in 0 until k) for (q in 0 until k) put(x0 + i * k + q, y0 + j * k + b, v)
            }
        }
    }

    private fun shadow(cx: Double, cy: Double, rx: Double, ry: Double) {
        var y = floor(cy - ry).toInt()
        while (y <= cy + ry) {
            var x = floor(cx - rx).toInt()
            while (x <= cx + rx) {
                val inside = sq((x + 0.5 - cx) / rx) + sq((y + 0.5 - cy) / ry) <= 1
                if (inside && x >= 0 && y >= 0 && x < WIDTH && y < HEIGHT && dots[y * WIDTH + x].toInt() == 0) {
                    dots[y * WIDTH + x] = SHADOW.toByte()
                }
                x++
            }
            y++
        }
    }

    private fun drawBear(sim: BearSim) {
        val spot = sim.bearSpot()
        val bear = BearRaster.build(sim.look(), spot.n)
        val n = spot.n
        shadow(spot.groundX.toDouble(), spot.groundY + 3.0, bear.halfWidth * 0.9, max(1.0, 2.4 * n / 50))
        val x0 = spot.groundX - bear.center
        val y0 = spot.groundY - spot.lift - bear.anchorBottom - 1
        for (j in 0 until n) for (i in 0 until n) {
            val v = bear[i, j]
            if (v == 0) continue
            put(x0 + i, y0 + j, if (v == BearGrid.FUR) LIT else if (v == BearGrid.INNER_EAR) DIM else HOLE)
        }
    }

    private fun drawBall(sim: BearSim) {
        val b = sim.ballSpot()
        if (b.shadow) shadow(b.x, jsRound(b.y + b.radius + b.lift) + 2.0, b.radius * 0.9, max(1.0, b.radius * 0.3))
        val cx = jsRound(b.x) + 0.5
        val cy = jsRound(b.y) + 0.5
        val r = b.radius
        var y = floor(cy - r - 1).toInt()
        while (y <= cy + r + 1) {
            var x = floor(cx - r - 1).toInt()
            while (x <= cx + r + 1) {
                if (sq((x + 0.5 - cx) / r) + sq((y + 0.5 - cy) / r) <= 1) put(x, y, LIT)
                x++
            }
            y++
        }
        if (r >= 4) {
            val sx = jsRound(cx - r * 0.45)
            val sy = jsRound(cy - r * 0.45)
            put(sx, sy, DIM); put(sx + 1, sy, DIM); put(sx, sy + 1, DIM)
        }
    }

    private fun drawFood(food: Food) {
        val s = sizeAt(food.z)
        val gx = jsRound(screenX(food.x, food.z))
        val gy = screenY(food.z)
        val r = (2.4 * s + 0.6) * 2 * food.bite
        shadow(gx + 0.5, jsRound(gy) + 2.0, (2.4 * s + 0.6) * 1.8, max(1.0, 2 * s))
        if (r < 1) return
        val cx = gx + 0.5
        val cy = jsRound(gy - food.height * 2 - r) + 0.5
        fun inside(x: Int, y: Int) = sq((x + 0.5 - cx) / r) + sq((y + 0.5 - cy) / r) <= 1
        var y = floor(cy - r - 1).toInt()
        while (y <= cy + r + 1) {
            var x = floor(cx - r - 1).toInt()
            while (x <= cx + r + 1) {
                if (inside(x, y)) {
                    val edge = !inside(x - 1, y) || !inside(x + 1, y) || !inside(x, y - 1) || !inside(x, y + 1)
                    put(x, y, if (edge) LIT else DIM)
                }
                x++
            }
            y++
        }
        if (food.bite > 0.6 && s > 0.65) stamp(Sprites.LEAF, gx.toDouble(), jsRound(cy - r) - 4.0, 2)
    }

    /** Foods other than the berry are drawn from their 9 × 9 icon, doubled up close. Bites come off the left. */
    private fun drawFoodSprite(food: Food, sprite: List<String>) {
        val s = sizeAt(food.z)
        val gx = jsRound(screenX(food.x, food.z))
        val gy = screenY(food.z)
        shadow(gx + 0.5, jsRound(gy) + 2.0, (2.4 * s + 0.6) * 1.8, max(1.0, 2 * s))
        val k = if (s > 0.8) 2 else 1
        val w = sprite[0].length
        val eaten = floor((1 - food.bite) * w).toInt()
        if (eaten >= w) return
        val x0 = gx - (w * k) / 2
        val y0 = jsRound(gy - food.height * 2) - sprite.size * k
        sprite.forEachIndexed { j, row ->
            row.forEachIndexed { i, ch ->
                if (i < eaten) return@forEachIndexed
                val v = when (ch) { '#' -> LIT; '+' -> DIM; else -> 0 }
                if (v != 0) for (b in 0 until k) for (q in 0 until k) put(x0 + i * k + q, y0 + j * k + b, v)
            }
        }
    }

    private fun drawHud(sim: BearSim) {
        if (hud == HudStyle.STACKED) drawStackedHud(sim) else drawRowHud(sim)
    }

    /** All three stats on one line: icon, then five thin segments, for each. */
    private fun drawRowHud(sim: BearSim) {
        doubleArrayOf(sim.happiness, sim.energy, sim.health).forEachIndexed { n, v ->
            val x0 = 9.0 + n * 38
            val y = 8.0
            stamp(Sprites.STAT_ICONS[n], x0, y, 2)
            val filled = ceil(v / 20 - 0.01).toInt()
            for (i in 0 until 5) {
                val x = x0 + 12 + i * 3
                if (i < filled) stamp(Sprites.SEGMENT_THIN_FULL, x, y) else stamp(Sprites.SEGMENT_THIN_EMPTY, x, y + 8)
            }
        }
    }

    /** The prototype's HUD: one stat per row. */
    private fun drawStackedHud(sim: BearSim) {
        doubleArrayOf(sim.happiness, sim.energy, sim.health).forEachIndexed { n, v ->
            val y = 8.0 + n * 14
            stamp(Sprites.STAT_ICONS[n], 8.0, y, 2)
            val filled = ceil(v / 20 - 0.01).toInt()
            for (i in 0 until 5) {
                val x = 22.0 + i * 5
                if (i < filled) stamp(Sprites.SEGMENT_FULL, x, y) else stamp(Sprites.SEGMENT_EMPTY, x, y + 8)
            }
        }
    }

    private fun sq(v: Double) = v * v

    companion object {
        const val OFF = 0
        const val HOLE = 1
        const val DIM = 2
        const val LIT = 3
        const val SHADOW = 4

        /** Is the dot at (x, y) with value [v] shown dim? Shadows are a checker pattern. */
        fun isDim(v: Int, x: Int, y: Int) = v == DIM || (v == SHADOW && (x + y) % 2 == 0)
    }
}

enum class HudStyle { ROW, STACKED }

/** Small hand-drawn sprites. '#' lit, '+' dim, anything else unlit. */
object Sprites {
    val HEART = listOf(".#.#.", "#####", "#####", ".###.", "..#..")
    val BOLT = listOf("..##.", ".##..", "#####", "..##.", ".##..")
    val CROSS = listOf("..#..", "..#..", "#####", "..#..", "..#..")
    val STAT_ICONS = listOf(HEART, BOLT, CROSS)
    val LEAF = listOf("#.", "##")
    val SEGMENT_FULL = List(10) { "###" }
    val SEGMENT_EMPTY = listOf("+++", "+++")
    val SEGMENT_THIN_FULL = List(10) { "##" }
    val SEGMENT_THIN_EMPTY = listOf("++", "++")

    // 9 × 9 icons for the care menu and the foods on the floor.
    val PAW = listOf(
        "..#...#..",
        ".###.###.",
        "..#...#..",
        "#.......#",
        "#..###..#",
        "..#####..",
        ".#######.",
        ".#######.",
        "..##.##..",
    )
    val BALL = listOf(
        "..#####..",
        ".#######.",
        "##++#####",
        "##+######",
        "#########",
        "#########",
        "#########",
        ".#######.",
        "..#####..",
    )
    val BERRY = listOf(
        "....#....",
        "...##....",
        "..#####..",
        ".#+++++#.",
        "#+++++++#",
        "#+++++++#",
        "#+++++++#",
        ".#+++++#.",
        "..#####..",
    )
    val APPLE = listOf(
        "....#.++.",
        "....#++..",
        ".###.###.",
        "#########",
        "#########",
        "#########",
        "#########",
        ".#######.",
        "..##.##..",
    )
    val FISH = listOf(
        ".........",
        "....###..",
        "#..#####.",
        "##.####.#",
        "#########",
        "##.######",
        "#..#####.",
        "....###..",
        ".........",
    )
    val HONEY = listOf(
        "..#####..",
        "...+++...",
        ".#######.",
        "#########",
        "##+++++##",
        "##+++++##",
        "#########",
        "#########",
        ".#######.",
    )

    fun food(kind: FoodKind) = when (kind) {
        FoodKind.BERRY -> BERRY
        FoodKind.APPLE -> APPLE
        FoodKind.FISH -> FISH
        FoodKind.HONEY -> HONEY
    }
}
