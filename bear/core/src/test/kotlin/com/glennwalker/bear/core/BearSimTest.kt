package com.glennwalker.bear.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BearSimTest {
    private fun sim() = BearSim(Random(7))

    private fun BearSim.runUntil(maxTicks: Int = 5000, done: BearSim.() -> Boolean): Int {
        repeat(maxTicks) { i ->
            step()
            if (done()) return i + 1
        }
        error("condition not reached in $maxTicks ticks (mode=$mode)")
    }

    @Test
    fun patWhileWorriedStartsSighAndEndsHappier() {
        val s = sim()
        s.step()
        s.makeWorry()
        assertEquals(Mode.WORRY, s.mode)
        s.step()
        s.pat()
        assertEquals(Mode.SIGH, s.mode)
        val before = s.happiness

        val looks = mutableListOf<BearLook>()
        val ticks = s.runUntil { looks += look(); mode != Mode.SIGH }

        assertEquals(Tuning.SIGH_TICKS, ticks)
        assertEquals(Mode.WANDER, s.mode)
        assertTrue(s.happiness > before + Tuning.SIGH_HAPPINESS - Tuning.SIGH_TICKS * Tuning.HAPPINESS_DECAY - 0.01)
        assertEquals(2, s.hearts.size)
        // In-breath stretches, out-breath squashes with a puff, then relieved.
        assertEquals(1, looks.first().heightAdjust)
        assertTrue(looks.any { it.heightAdjust == -1 && it.puff == 2 && it.mood == Mood.RELIEVED })
    }

    @Test
    fun patWhenCalmGivesHeartAndHappiness() {
        val s = sim()
        val before = s.happiness
        s.pat()
        assertEquals(before + Tuning.PAT_HAPPINESS, s.happiness)
        assertEquals(1, s.hearts.size)
        assertEquals(Mood.HAPPY, s.look().mood)
    }

    @Test
    fun feedingMakesTheBearWalkOverAndEat() {
        val s = sim()
        s.setStats(health = 40.0)
        s.feed()
        s.runUntil { mode == Mode.EAT }
        s.runUntil { food == null }
        assertEquals(Mode.WANDER, s.mode)
        assertTrue(s.health > 60)
    }

    @Test
    fun lowEnergySendsItToSleepUntilRested() {
        val s = sim()
        s.setStats(energy = 5.0)
        s.step()
        assertEquals(Mode.SLEEP, s.mode)
        assertEquals(Mood.SLEEPY, s.look().mood)
        s.runUntil { mode != Mode.SLEEP }
        assertTrue(s.energy >= Tuning.WAKE_AT_ENERGY)
    }

    @Test
    fun swipingTheBallUpThrowsItAndTheBearFetches() {
        val s = sim()
        s.setStats(energy = 90.0, happiness = 90.0)
        s.startFetch()
        s.step()
        s.pointerDown(Floor.HAND_X, Floor.HAND_Y, 0)
        assertEquals(Mode.READY, s.mode)
        s.pointerMove(Floor.HAND_X, 170.0, 30)
        s.pointerMove(Floor.HAND_X + 4, 140.0, 60)
        s.pointerUp(Floor.HAND_X + 6, 120.0, 80)
        assertEquals(Mode.WATCH, s.mode)
        assertEquals(BallState.FLY, s.ball.state)

        s.runUntil { mode == Mode.CHASE }
        s.runUntil { mode == Mode.PICK }
        s.runUntil { mode == Mode.WANDER }
        s.runUntil { ball.state == BallState.HAND }
    }

    @Test
    fun slowDragDropsTheBallBackInTheHand() {
        val s = sim()
        s.startFetch()
        s.step()
        s.pointerDown(Floor.HAND_X, Floor.HAND_Y, 0)
        s.pointerMove(Floor.HAND_X, 185.0, 200)
        s.pointerUp(Floor.HAND_X, 185.0, 400)
        assertEquals(Mode.WANDER, s.mode)
        assertEquals(BallState.HAND, s.ball.state)
    }

    @Test
    fun tapOnTheBearPatsIt() {
        val s = sim()
        val spot = s.bearSpot()
        val ux = spot.groundX.toDouble()
        val uy = spot.groundY - spot.n * 0.4
        assertTrue(s.isOnBear(ux, uy))
        s.pointerDown(ux, uy, 0)
        s.pointerUp(ux, uy, 50)
        assertEquals(1, s.hearts.size)
    }

    @Test
    fun statsStayInRangeAndRunsAreDeterministic() {
        val a = BearSim(Random(42))
        val b = BearSim(Random(42))
        repeat(20_000) {
            a.step(); b.step()
            if (it % 997 == 0) { a.feed(); b.feed() }
            assertTrue(a.happiness in 0.0..100.0 && a.energy in 0.0..100.0 && a.health in 0.0..100.0)
        }
        assertEquals(a.x, b.x)
        assertEquals(a.z, b.z)
        assertEquals(a.mode, b.mode)
    }
}
