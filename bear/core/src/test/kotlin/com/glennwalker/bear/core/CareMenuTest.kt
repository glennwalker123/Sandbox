package com.glennwalker.bear.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CareMenuTest {
    private fun sim() = BearSim(Random(3))

    private fun BearSim.runUntil(done: BearSim.() -> Boolean) {
        repeat(5000) { step(); if (done()) return }
        error("not reached (mode=$mode)")
    }

    @Test
    fun ballStaysAwayUntilFetchIsChosen() {
        val s = sim()
        assertFalse(s.ballVisible)
        assertEquals("Play fetch", CareMenu.label(CareMenu.Item.FETCH, s))
        CareMenu.choose(CareMenu.Item.FETCH, s)
        assertEquals(BallState.HAND, s.ball.state)
        assertEquals("Put the ball away", CareMenu.label(CareMenu.Item.FETCH, s))
        CareMenu.choose(CareMenu.Item.FETCH, s)
        assertFalse(s.fetchOut)
    }

    @Test
    fun ballCannotBePutAwayMidThrow() {
        val s = sim()
        s.setStats(energy = 90.0)
        CareMenu.choose(CareMenu.Item.FETCH, s)
        s.step()
        s.pointerDown(Floor.HAND_X, Floor.HAND_Y, 0)
        s.pointerMove(Floor.HAND_X, 150.0, 40)
        s.pointerUp(Floor.HAND_X, 120.0, 70)
        assertTrue(s.fetchInPlay)
        assertFalse(CareMenu.enabled(CareMenu.Item.FETCH, s))
        CareMenu.choose(CareMenu.Item.FETCH, s)
        assertEquals(BallState.FLY, s.ball.state)
    }

    @Test
    fun eachFoodHasItsOwnEffect() {
        for (item in CareMenu.items.filter { it.food != null }) {
            val s = sim()
            s.setStats(happiness = 30.0, energy = 50.0, health = 30.0)
            CareMenu.choose(item, s)
            assertEquals(item.food, s.food?.kind)
            assertFalse(CareMenu.enabled(CareMenu.Item.HONEY, s), "one food at a time")
            s.runUntil { food == null }
            val effect = Tuning.FOOD.getValue(item.food!!)
            // Stats drift a little while the bear walks over, so allow a few points either way.
            assertEquals(30.0 + effect.health, s.health, 3.0, "$item health")
            assertEquals(50.0 + effect.energy, s.energy, 3.0, "$item energy")
            assertNull(s.food)
        }
    }

    @Test
    fun hudFitsOnOneLine() {
        val s = sim()
        val dots = SceneRaster().render(s)
        val hudRows = (0 until Floor.HEIGHT).filter { y -> (0 until Floor.WIDTH).any { x -> dots[y * Floor.WIDTH + x].toInt() != 0 && y < 40 } }
        assertEquals(8..17, hudRows.first()..hudRows.last())
    }
}
