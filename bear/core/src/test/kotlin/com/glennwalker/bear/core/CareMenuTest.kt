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
    fun fetchIsAModeWithATitleBar() {
        val s = sim()
        assertFalse(s.ballVisible)
        CareMenu.choose(CareMenu.Item.FETCH, s)
        assertEquals(Activity.FETCH, s.activity)
        assertEquals(BallState.HAND, s.ball.state)
        val dots = SceneRaster().render(s)
        assertTrue((ModeBar.LEFT..ModeBar.RIGHT).all { x -> dots[ModeBar.TOP * Floor.WIDTH + x].toInt() == SceneRaster.LIT }, "bar border")
        assertEquals("Swipe the ball up to throw it", CareMenu.fetchStatus(s))
        s.endActivity()
        assertEquals(Activity.NONE, s.activity)
        assertFalse(s.fetchOut)
    }

    @Test
    fun theCloseBoxEndsTheMode() {
        val s = sim()
        CareMenu.choose(CareMenu.Item.TALK, s)
        assertEquals(Activity.TALK, s.activity)
        s.pointerDown(108.0, 7.0, 0)
        assertEquals(Activity.NONE, s.activity)
    }

    @Test
    fun leavingMidThrowPutsTheBallAwayOnceItsBack() {
        val s = sim()
        s.setStats(energy = 90.0, happiness = 90.0)
        CareMenu.choose(CareMenu.Item.FETCH, s)
        s.step()
        s.pointerDown(Floor.HAND_X, Floor.HAND_Y, 0)
        s.pointerMove(Floor.HAND_X, 150.0, 40)
        s.pointerUp(Floor.HAND_X, 120.0, 70)
        assertEquals(1, s.throws)
        s.endActivity()
        assertEquals(BallState.FLY, s.ball.state)
        s.runUntil { !fetchOut }
        assertEquals(BallState.GONE, s.ball.state)
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

class BearTalkTest {
    @Test
    fun stateDescribesTheBearInWords() {
        val s = BearSim(Random(1))
        s.setStats(happiness = 90.0, energy = 20.0, health = 60.0)
        val state = BearTalk.state(s)
        assertTrue("a cub" in state && "wandering" in state && "Happiness high" in state && "energy very low" in state, state)
        assertTrue(BearTalk.userTurn(s, "  hello  ").endsWith("]\nhello"))
    }

    @Test
    fun replyFallsBackGently() {
        val r = BearTalk.reply(null, null, "weird")
        assertEquals("…", r.say)
        assertEquals(BearTalk.OPENERS, r.suggestions)
        assertEquals(BearTalk.Reaction.NONE, r.reaction)
        assertEquals(BearTalk.Reaction.SIGH, BearTalk.reply("phew", listOf("a", "b", "c", "d"), "Sigh").reaction)
        assertEquals(3, BearTalk.reply("x", listOf("a", "b", "c", "d"), null).suggestions.size)
    }

    @Test
    fun aReassuringReplySettlesAWorriedBear() {
        val s = BearSim(Random(1))
        s.step()
        s.makeWorry()
        s.hear(BearTalk.Reaction.SIGH)
        assertEquals(Mode.SIGH, s.mode)
        val calm = BearSim(Random(1))
        val before = calm.happiness
        calm.hear(BearTalk.Reaction.HAPPY)
        assertEquals(1, calm.hearts.size)
        assertEquals(before + Tuning.TALK_HAPPINESS, calm.happiness)
    }
}

class TintTest {
    @Test
    fun colourIsOnlyAHint() {
        val s = BearSim(Random(2))
        s.pat()
        val raster = SceneRaster()
        val dots = raster.render(s)
        val tinted = dots.indices.filter { dots[it].toInt() != 0 && raster.tint(it) != Tint.NONE }
        val lit = dots.indices.count { dots[it].toInt() == SceneRaster.LIT || dots[it].toInt() == SceneRaster.DIM }
        assertTrue(tinted.isNotEmpty(), "the heart and HUD icons are tinted")
        assertTrue(tinted.all { raster.tint(it) in Tint.STATS }, "only the HUD icons and the heart")
        assertTrue(tinted.size * 4 < lit, "colour stays a small part of the screen")
    }

    @Test
    fun worriedBearHasACyanSweatDrop() {
        val grid = BearRaster.build(BearLook(Age.CUB, Mood.WORRIED), 25)
        val cyan = (0 until 25).flatMap { y -> (0 until 25).map { x -> x to y } }.filter { (x, y) -> grid.tint(x, y) == Tint.SWEAT }
        assertEquals(5, cyan.size)
    }
}
