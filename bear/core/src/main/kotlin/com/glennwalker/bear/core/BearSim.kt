package com.glennwalker.bear.core

import com.glennwalker.bear.core.Floor.HAND_X
import com.glennwalker.bear.core.Floor.HAND_Y
import com.glennwalker.bear.core.Floor.Z_MAX
import com.glennwalker.bear.core.Floor.Z_MIN
import com.glennwalker.bear.core.Floor.depthScale
import com.glennwalker.bear.core.Floor.screenX
import com.glennwalker.bear.core.Floor.screenY
import com.glennwalker.bear.core.Floor.sizeAt
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class BallState { HAND, DRAG, FLY, REST, CARRIED, GONE }

class Ball(
    var state: BallState = BallState.HAND,
    /** Screen position while in the hand or dragged, and the launch point once thrown. */
    var sx: Double = HAND_X,
    var sy: Double = HAND_Y,
    /** Landing spot in the world. */
    var tx: Double = 0.0,
    var tz: Double = 0.0,
    var t: Int = 0,
    var duration: Int = 0,
    var arcHeight: Double = 0.0,
)

/** Where to draw the ball this frame, in screen units. */
data class BallSpot(
    val x: Double,
    val y: Double,
    val radius: Double,
    /** Depth used for draw order; -1 is in front of everything. */
    val z: Double,
    val shadow: Boolean,
    /** Height above the floor, for placing the shadow. */
    val lift: Double,
)

enum class FoodState { DROP, REST }

class Food(
    val x: Double,
    val z: Double,
    var height: Double = 50.0,
    var t: Int = 0,
    var state: FoodState = FoodState.DROP,
    /** 1 = whole berry, 0 = eaten. */
    var bite: Double = 1.0,
)

class Heart(val x: Double, var y: Double, var life: Int)

/** Where to draw the bear this frame, in screen units. */
data class BearSpot(val groundX: Int, val groundY: Int, val lift: Int, val n: Int)

/**
 * The scene simulation, ported from the prototype's `step()` and input handlers.
 * Call [step] every [Tuning.TICK_MS] milliseconds.
 */
class BearSim(private val random: Random = Random.Default) {
    var age = Age.CUB
    var mode = Mode.WANDER
        private set

    var happiness = Tuning.START_HAPPINESS
        private set
    var energy = Tuning.START_ENERGY
        private set
    var health = Tuning.START_HEALTH
        private set

    /** Bear position in the world. */
    var x = 0.0
        private set
    var z = 0.25
        private set
    /** Facing: -1 left, 1 right. */
    var dir = 1
        private set
    var tick = 0
        private set

    val ball = Ball()
    var food: Food? = null
        private set
    val hearts = mutableListOf<Heart>()

    private var targetX = 0.0
    private var targetZ = 0.25
    private var pause = 20
    private var frame = 0
    private var happyTicks = 0
    private var hopIndex = -1
    private var away = false
    private var moving = false
    private var timer = 0
    private var handTicks = 0
    private var worryX = 0.0

    private val cub get() = age == Age.CUB

    // ---- What to draw ----

    fun look(): BearLook {
        var mood = Mood.CALM
        var heightAdjust = 0
        var puff = 0
        when {
            mode == Mode.SIGH -> Sigh.at(age, timer).let { mood = it.mood; heightAdjust = it.heightAdjust; puff = it.puff }
            mode == Mode.WORRY -> mood = Mood.WORRIED
            mode == Mode.SLEEP || mode == Mode.TIRED -> mood = Mood.SLEEPY
            happyTicks > 0 || mode == Mode.READY -> mood = Mood.HAPPY
            Tuning.WORRIED_FACE_WHEN_UNHAPPY && happiness < Tuning.WORRIED_FACE_BELOW_HAPPINESS -> mood = Mood.WORRIED
        }
        if (mode == Mode.EAT && timer > 0 && (tick shr 2) % 2 == 1) mood = Mood.HAPPY
        return BearLook(
            age = age,
            mood = mood,
            heightAdjust = heightAdjust,
            puff = puff,
            step = if (moving) (if (frame == 1) 1 else 2) else 0,
            back = away,
            look = if (!away && moving && mode != Mode.WORRY) dir else 0,
            carry = ball.state == BallState.CARRIED,
            tick = tick,
        )
    }

    /** Current hop height on the 25-cell reference grid (0 when not hopping). */
    val hopHeight: Int get() = if (hopIndex >= 0) HOP[hopIndex] else 0

    fun bearSize(): Int = max(13, jsRound(52 * sizeAt(z)))

    fun bearSpot(): BearSpot {
        val n = bearSize()
        val lift = if (hopIndex >= 0) jsRound(HOP[hopIndex] * n / 25.0) else 0
        return BearSpot(jsRound(screenX(x, z)), jsRound(screenY(z)), lift, n)
    }

    fun ballSpot(): BallSpot {
        val b = ball
        val restRadius = { z: Double -> (2.6 * sizeAt(z) + 0.4) * 2 }
        return when (b.state) {
            BallState.REST -> {
                val r = restRadius(b.tz)
                BallSpot(screenX(b.tx, b.tz), screenY(b.tz) - r, r, b.tz, shadow = true, lift = 0.0)
            }
            BallState.FLY -> {
                val total = b.duration.toDouble()
                val p = min(1.0, b.t / total)
                var h = b.arcHeight * 4 * p * (1 - p)
                if (b.t > total) {
                    val u = b.t - total
                    if (u < total * 0.35) {
                        val q = u / (total * 0.35)
                        h = b.arcHeight * 0.22 * 4 * q * (1 - q)
                    } else if (u < total * 0.55) {
                        val q = (u - total * 0.35) / (total * 0.2)
                        h = b.arcHeight * 0.06 * 4 * q * (1 - q)
                    }
                }
                val r = 6 + (restRadius(b.tz) - 6) * p
                val gx = b.sx + (screenX(b.tx, b.tz) - b.sx) * p
                val gy = b.sy + (screenY(b.tz) - r - b.sy) * p
                val lift = h * sizeAt(b.tz) * 2
                BallSpot(gx, gy - lift, r, -0.2 + (b.tz + 0.2) * p, shadow = p > 0.3, lift = lift)
            }
            else -> BallSpot(b.sx, b.sy, 6.0, -1.0, shadow = false, lift = 0.0)
        }
    }

    /** Ball is drawn on its own (not in the bear's mouth, not fetched away). */
    val ballVisible get() = ball.state != BallState.CARRIED && ball.state != BallState.GONE

    // ---- Actions ----

    fun pat() {
        if (mode == Mode.WORRY) { mode = Mode.SIGH; timer = 0; return }
        if (mode == Mode.SIGH) return
        happiness = clamp(happiness + Tuning.PAT_HAPPINESS)
        heartsAt(1)
        if (mode == Mode.SLEEP) return
        if (hopIndex < 0) hopIndex = 0
        happyTicks = 20
        away = false
        if (mode == Mode.WANDER) pause = max(pause, 18)
    }

    fun feed() {
        if (food != null) return
        val fz = 0.35 + random.nextDouble() * 0.6
        val limit = 22 / depthScale(fz)
        food = Food(x = (random.nextDouble() * 2 - 1) * limit, z = fz)
    }

    /** Debug: start a worry episode now, waking or interrupting the bear if needed. */
    fun makeWorry() {
        if (mode == Mode.SLEEP) { mode = Mode.WANDER; energy = clamp(energy + Tuning.WORRY_BUTTON_WAKE_ENERGY) }
        if (mode == Mode.READY || mode == Mode.WATCH) { toHand(); mode = Mode.WANDER }
        startWorry()
    }

    fun toggleAge() {
        age = if (cub) Age.ADULT else Age.CUB
    }

    /** Debug: set stats directly. */
    fun setStats(happiness: Double = this.happiness, energy: Double = this.energy, health: Double = this.health) {
        this.happiness = clamp(happiness)
        this.energy = clamp(energy)
        this.health = clamp(health)
    }

    // ---- Touch input, in screen units and milliseconds ----

    private class Drag(val x0: Double, val y0: Double, var grab: Boolean) {
        val points = ArrayDeque<Triple<Double, Double, Long>>()
    }

    private var drag: Drag? = null

    fun pointerDown(ux: Double, uy: Double, timeMs: Long) {
        val d = Drag(ux, uy, grab = false).also { it.points.addLast(Triple(ux, uy, timeMs)) }
        drag = d
        if (mode == Mode.SLEEP || mode == Mode.WORRY || mode == Mode.SIGH) return
        if (ball.state == BallState.HAND && hypot(ux - HAND_X, uy - HAND_Y) < 20 &&
            (mode == Mode.WANDER || mode == Mode.BORED)
        ) {
            d.grab = true
            ball.state = BallState.DRAG
            mode = Mode.READY
            pause = 0
        }
    }

    fun pointerMove(ux: Double, uy: Double, timeMs: Long) {
        val d = drag ?: return
        d.points.addLast(Triple(ux, uy, timeMs))
        if (d.points.size > 8) d.points.removeFirst()
        if (d.grab) {
            ball.sx = ux.coerceIn(8.0, 112.0)
            ball.sy = uy.coerceIn(100.0, 190.0)
        }
    }

    fun pointerUp(ux: Double, uy: Double, timeMs: Long) {
        val d = drag ?: return
        drag = null
        if (!d.grab) {
            if (hypot(ux - d.x0, uy - d.y0) < 6 && isOnBear(ux, uy)) pat()
            return
        }
        val old = d.points.firstOrNull { timeMs - it.third < 120 } ?: d.points.first()
        val dt = max(16L, timeMs - old.third).toDouble()
        val vx = (ux - old.first) / dt / 2
        val vy = (uy - old.second) / dt / 2
        if (vy > -0.03) {
            toHand(); mode = Mode.WANDER; pause = 10
            return
        }
        val power = min(1.0, (-vy - 0.03) / 0.17)
        val tz = 0.3 + power * (Z_MAX - 0.3)
        val ratio = (vx / -vy).coerceIn(-1.2, 1.2)
        val limit = 25 / depthScale(tz)
        val tx = ((ball.sx / 2 - 30) / depthScale(tz) * 0.4 + ratio * tz * 30).coerceIn(-limit, limit)
        ball.apply {
            state = BallState.FLY
            this.tx = tx
            this.tz = tz
            t = 0
            duration = jsRound(14 + tz * 10)
            arcHeight = 16 + tz * 14
        }
        mode = Mode.WATCH
        if (hopIndex < 0) hopIndex = 0
    }

    fun pointerCancel() {
        if (drag?.grab == true) { toHand(); mode = Mode.WANDER }
        drag = null
    }

    fun isOnBear(ux: Double, uy: Double): Boolean {
        val n = bearSize()
        val gx = screenX(x, z)
        val gy = screenY(z)
        return abs(ux - gx) < n * 0.45 && uy < gy + 4 && uy > gy - n * 0.8
    }

    // ---- Simulation ----

    fun step() {
        tick++
        moving = false
        if (hopIndex >= 0) { hopIndex++; if (hopIndex >= HOP.size) hopIndex = -1 }
        if (happyTicks > 0) happyTicks--
        hearts.forEach { it.y -= 0.8; it.life-- }
        hearts.removeAll { it.life <= 0 }

        if (ball.state == BallState.FLY) {
            ball.t++
            if (ball.t >= ball.duration * 1.55) {
                ball.state = BallState.REST
                if (mode == Mode.WATCH) {
                    if (energy < Tuning.TIRED_BELOW_ENERGY) { mode = Mode.TIRED; timer = Tuning.TIRED_TICKS } else mode = Mode.CHASE
                }
            }
        }
        food?.let { f ->
            if (f.state != FoodState.DROP) return@let
            f.t++
            val t = f.t.toDouble()
            when {
                t < 12 -> f.height = 50 * (1 - (t / 12) * (t / 12))
                t < 18 -> { val u = (t - 12) / 6; f.height = 3 * 4 * u * (1 - u) }
                else -> { f.height = 0.0; f.state = FoodState.REST }
            }
        }
        if (handTicks > 0 && --handTicks == 0) toHand()

        happiness = clamp(happiness - if (mode == Mode.WORRY) Tuning.HAPPINESS_DECAY_WORRIED else Tuning.HAPPINESS_DECAY)
        val well = happiness > Tuning.WELL_HAPPINESS && energy > Tuning.WELL_ENERGY
        health = clamp(health - Tuning.HEALTH_DECAY + if (well) Tuning.HEALTH_GAIN_WHEN_WELL else 0.0)
        val running = mode == Mode.CHASE || mode == Mode.RETURN
        // As in the prototype, `moving` was reset above, so the walking cost never applies here.
        energy = clamp(
            energy + when {
                mode == Mode.SLEEP -> Tuning.ENERGY_SLEEP_GAIN
                running -> -Tuning.ENERGY_RUN_COST
                moving -> -Tuning.ENERGY_WALK_COST
                else -> Tuning.ENERGY_REST_GAIN
            },
        )
        val vitality = (0.55 + 0.45 * energy / 100) * (0.7 + 0.3 * health / 100) *
            (if (cub) Tuning.CUB_PACE else Tuning.ADULT_PACE)
        val speed = Tuning.RUN_SPEED * vitality

        if ((mode == Mode.WANDER || mode == Mode.BORED) && food?.state == FoodState.REST) { mode = Mode.EAT; timer = -1 }
        if (mode == Mode.WANDER && energy < Tuning.SLEEP_BELOW_ENERGY) mode = Mode.SLEEP
        if (mode == Mode.WANDER) {
            val chance = if (happiness < Tuning.UNHAPPY_BELOW_HAPPINESS) Tuning.WORRY_CHANCE_UNHAPPY else Tuning.WORRY_CHANCE
            if (random.nextDouble() < chance) startWorry()
        }

        val depthX = 1 + z * 0.8
        val depthZ = 1 + z
        when (mode) {
            Mode.WANDER -> {
                if (pause > 0) {
                    pause--
                    away = false
                    if (pause == 0) pickTarget()
                    if (happiness > Tuning.HOP_WHEN_HAPPIER_THAN && pause > 0 && hopIndex < 0 &&
                        random.nextDouble() < Tuning.RANDOM_HOP_CHANCE
                    ) hopIndex = 0
                } else {
                    // Same operation order as the prototype, so runs match it exactly.
                    val vx = Tuning.WANDER_SPEED_X * depthX * vitality * Tuning.WANDER_SPEED_BOOST
                    val vz = Tuning.WANDER_SPEED_Z * depthZ * vitality * Tuning.WANDER_SPEED_BOOST
                    if (moveTo(targetX, targetZ, vx, vz)) {
                        val low = energy < Tuning.SLOW_BELOW_ENERGY || health < Tuning.SLOW_BELOW_HEALTH
                        pause = Tuning.PAUSE_MIN + floor(random.nextDouble() * Tuning.PAUSE_RANDOM).toInt() +
                            if (low) Tuning.PAUSE_WHEN_LOW else 0
                    }
                }
            }
            Mode.WORRY -> {
                timer++
                val limit = limitX(z)
                val nx = (worryX + sin(timer * Tuning.WORRY_SWAY_RATE) * Tuning.WORRY_SWAY).coerceIn(-limit, limit)
                dir = if (nx > x) 1 else -1
                x = nx
                moving = true
                away = false
                if (tick % 3 == 0) frame = frame xor 1
                if (timer > Tuning.WORRY_MAX_TICKS) {
                    mode = Mode.WANDER; pause = 20
                    happiness = clamp(happiness + Tuning.WORRY_IGNORED_HAPPINESS)
                }
            }
            Mode.SIGH -> {
                timer++
                away = false
                if (timer >= Tuning.SIGH_TICKS) {
                    mode = Mode.WANDER; pause = 30; happyTicks = 34; hopIndex = 0
                    happiness = clamp(happiness + Tuning.SIGH_HAPPINESS)
                    heartsAt(2)
                }
            }
            Mode.READY -> {
                away = false
                dir = if (ball.sx > screenX(x, z)) 1 else -1
                if (tick % 10 == 0 && hopIndex < 0) hopIndex = 0
            }
            Mode.WATCH -> {
                away = false
                dir = if (ballSpot().x > screenX(x, z)) 1 else -1
            }
            Mode.CHASE -> {
                if (moveTo(ball.tx, ball.tz + 0.05, Tuning.CHASE_SPEED_X * speed * depthX, Tuning.CHASE_SPEED_Z * speed * depthZ)) {
                    mode = Mode.PICK; timer = Tuning.PICK_TICKS; hopIndex = 0
                }
            }
            Mode.PICK -> {
                if (--timer <= 0) {
                    val success = (if (cub) Tuning.FETCH_SUCCESS_CUB else Tuning.FETCH_SUCCESS_ADULT) * (0.6 + 0.4 * happiness / 100)
                    if (random.nextDouble() < success) {
                        ball.state = BallState.CARRIED; mode = Mode.RETURN
                    } else {
                        mode = Mode.BORED; timer = Tuning.BORED_TICKS
                    }
                }
            }
            Mode.RETURN -> {
                if (moveTo(0.0, Z_MIN + 0.1, Tuning.RETURN_SPEED_X * speed * depthX, Tuning.RETURN_SPEED_Z * speed * depthZ)) {
                    mode = Mode.WANDER; pause = 30; away = false
                    ball.state = BallState.REST
                    ball.tx = x + dir * 7
                    ball.tz = z - 0.05
                    handTicks = 18; happyTicks = 24; hopIndex = 0
                    happiness = clamp(happiness + Tuning.FETCH_HAPPINESS)
                    heartsAt(2)
                }
            }
            Mode.BORED -> {
                away = false
                if (--timer <= 0) { mode = Mode.WANDER; pause = 1; ball.state = BallState.GONE; handTicks = 8 }
            }
            Mode.TIRED -> {
                away = false
                if (--timer <= 0) { mode = Mode.WANDER; pause = 20; ball.state = BallState.GONE; handTicks = 8 }
            }
            Mode.EAT -> {
                val f = food
                if (f == null) {
                    mode = Mode.WANDER
                } else if (timer < 0) {
                    val side = if (f.x > x) -2.5 else 2.5
                    if (moveTo(f.x + side, f.z + 0.04, Tuning.EAT_SPEED_X * speed * depthX, Tuning.EAT_SPEED_Z * speed * depthZ)) {
                        timer = Tuning.EAT_TICKS
                        dir = if (f.x > x) 1 else -1
                    }
                } else {
                    f.bite = max(0.0, (timer - 1) / Tuning.EAT_TICKS.toDouble())
                    if (--timer <= 0) {
                        food = null
                        health = clamp(health + Tuning.FOOD_HEALTH)
                        energy = clamp(energy + Tuning.FOOD_ENERGY)
                        happiness = clamp(happiness + Tuning.FOOD_HAPPINESS)
                        happyTicks = 16; hopIndex = 0
                        mode = Mode.WANDER; pause = 20
                    }
                }
            }
            Mode.SLEEP -> {
                away = false
                if (energy >= Tuning.WAKE_AT_ENERGY) { mode = Mode.WANDER; pause = 12; hopIndex = 0 }
            }
        }
    }

    // ---- Helpers ----

    private fun halfWidth(n: Double): Double = (if (cub) 8.0 else 9.5) * n / 25 + 3

    /** How far the bear can go sideways at depth [z] without leaving the screen. */
    private fun limitX(z: Double): Double = (58 - halfWidth(bearSize().toDouble())) / (2 * depthScale(z))

    private fun pickTarget() {
        targetZ = Z_MIN + random.nextDouble() * (Z_MAX - Z_MIN)
        val n = max(13.0, 52 * sizeAt(targetZ))
        targetX = (random.nextDouble() * 2 - 1) * (56 - halfWidth(n)) / (2 * depthScale(targetZ))
    }

    /** Walk towards (tx, tz) at up to (vx, vz) per tick. Returns true on arrival. */
    private fun moveTo(tx: Double, tz: Double, vx: Double, vz: Double): Boolean {
        val dx = tx - x
        val dz = tz - z
        if (abs(dx) < vx && abs(dz) < vz) {
            x = tx; z = tz; away = false
            return true
        }
        moving = true
        x += dx.coerceIn(-vx, vx)
        z += dz.coerceIn(-vz, vz)
        val limit = limitX(z)
        x = x.coerceIn(-limit, limit)
        if (abs(dx) > vx) dir = if (dx > 0) 1 else -1
        away = dz > vz * 0.5 && abs(dz) * 40 > abs(dx)
        if (tick % 3 == 0) frame = frame xor 1
        return false
    }

    private fun toHand() {
        ball.state = BallState.HAND
        ball.sx = HAND_X
        ball.sy = HAND_Y
    }

    private fun bearTop(): Int = jsRound(screenY(z)) - jsRound(bearSize() * 0.62)

    private fun heartsAt(count: Int) {
        for (i in 0 until count) {
            hearts += Heart(
                x = jsRound(screenX(x, z) + (i - (count - 1) / 2.0) * 14).toDouble(),
                y = (bearTop() - 4 - i * 6).toDouble(),
                life = 24 + i * 3,
            )
        }
    }

    private fun startWorry() {
        if (mode == Mode.WANDER || mode == Mode.BORED) {
            mode = Mode.WORRY; timer = 0; worryX = x; pause = 0
        }
    }

    private fun clamp(v: Double) = v.coerceIn(0.0, 100.0)

    companion object {
        /** Hop height per tick, on the 25-cell reference grid. */
        val HOP = intArrayOf(0, 1, 2, 3, 2, 1, 0)
    }
}
