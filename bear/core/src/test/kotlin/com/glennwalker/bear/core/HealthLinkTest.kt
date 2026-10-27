package com.glennwalker.bear.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HealthLinkTest {
    private fun sim() = BearSim(Random(4)).apply { setStats(happiness = 50.0, energy = 40.0, health = 40.0) }

    @Test
    fun aGoodNightGivesEnergyOncePerNight() {
        val s = sim()
        val link = HealthLink()
        val boost = link.apply(s, HealthReading(day = 100, sleepMinutes = 8 * 60, steps = null))
        assertEquals(Tuning.SLEEP_ENERGY_MAX, boost.energy)
        assertEquals(40.0 + Tuning.SLEEP_ENERGY_MAX, s.energy)
        // Reading the same night again adds nothing.
        assertEquals(0.0, link.apply(s, HealthReading(100, 8 * 60, null)).energy)
        // A short night the next day gives a smaller boost, never a penalty.
        assertEquals(Tuning.SLEEP_ENERGY_MAX / 2, link.apply(s, HealthReading(101, 4 * 60, null)).energy)
    }

    @Test
    fun stepsAddHealthAsTheyAccumulateUpToTheGoal() {
        val s = sim()
        val link = HealthLink()
        val first = link.apply(s, HealthReading(100, null, 2000))
        assertEquals(Tuning.STEPS_HEALTH_MAX / 4, first.health)
        val second = link.apply(s, HealthReading(100, null, 6000))
        assertEquals(Tuning.STEPS_HEALTH_MAX / 2, second.health)
        val beyondGoal = link.apply(s, HealthReading(100, null, 20000))
        assertEquals(Tuning.STEPS_HEALTH_MAX / 4, beyondGoal.health)
        assertEquals(40.0 + Tuning.STEPS_HEALTH_MAX, s.health)
        // A new day starts counting again.
        assertEquals(Tuning.STEPS_HEALTH_MAX / 8, link.apply(s, HealthReading(101, null, 1000)).health)
    }

    @Test
    fun describesTheReadingAndTheBoost() {
        assertEquals("Last night 7h 10m · 5,230 steps today", HealthLink.describe(HealthReading(1, 430, 5230)))
        assertEquals("+29 energy from sleep · +21 health from steps", HealthLink.describe(HealthLink.Boost(28.6, 21.0)))
        assertTrue(HealthLink.describe(HealthReading(1, null, null)).startsWith("No sleep data"))
    }
}
