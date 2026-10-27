package com.glennwalker.bear.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Your sleep and steps, as read from the phone's health data. Any part can be missing. */
data class HealthReading(
    /** The day this reading is for, as days since 1970-01-01 in local time. */
    val day: Long,
    /** Minutes asleep last night (the night ending on [day]). */
    val sleepMinutes: Int?,
    /** Steps so far on [day]. */
    val steps: Int?,
)

/**
 * Turns your sleep and steps into the bear's energy and health. Good sleep gives the bear
 * energy once per night; steps add health through the day. It only ever adds: a short night
 * or a quiet day simply gives less, never a penalty.
 */
class HealthLink {
    /** What a reading added, to show the person. */
    data class Boost(val energy: Double, val health: Double) {
        val any get() = energy >= 0.5 || health >= 0.5
    }

    var last: HealthReading? = null
        private set
    var lastBoost = Boost(0.0, 0.0)
        private set

    private var sleepDay = Long.MIN_VALUE
    private var stepsDay = Long.MIN_VALUE
    private var stepsCounted = 0

    fun apply(sim: BearSim, reading: HealthReading): Boost {
        last = reading
        var energy = 0.0
        var health = 0.0
        if (reading.sleepMinutes != null && reading.day != sleepDay) {
            sleepDay = reading.day
            val rested = (reading.sleepMinutes / (Tuning.SLEEP_GOAL_HOURS * 60)).coerceIn(0.0, 1.0)
            energy = Tuning.SLEEP_ENERGY_MAX * rested
        }
        if (reading.steps != null) {
            if (reading.day != stepsDay) {
                stepsDay = reading.day
                stepsCounted = 0
            }
            // Only steps not yet counted today, and only up to the day's goal.
            val upTo = min(reading.steps, Tuning.STEP_GOAL)
            val fresh = max(0, upTo - stepsCounted)
            stepsCounted = max(stepsCounted, upTo)
            health = Tuning.STEPS_HEALTH_MAX * fresh / Tuning.STEP_GOAL
        }
        val boost = Boost(energy, health)
        lastBoost = boost
        if (boost.any) {
            sim.setStats(energy = sim.energy + energy, health = sim.health + health)
            sim.cheer()
        }
        return boost
    }

    companion object {
        /** "Last night 7h 10m · 5,230 steps today", or what's missing. */
        fun describe(reading: HealthReading?): String {
            if (reading == null) return "No sleep or step data yet"
            val sleep = reading.sleepMinutes?.let { "Last night ${it / 60}h ${it % 60}m" } ?: "No sleep data for last night"
            val steps = reading.steps?.let { "${grouped(it)} steps today" } ?: "no step data today"
            return "$sleep · $steps"
        }

        /** "+29 energy · +21 health", or empty when nothing was added. */
        fun describe(boost: Boost): String = listOfNotNull(
            boost.energy.roundToInt().takeIf { it > 0 }?.let { "+$it energy from sleep" },
            boost.health.roundToInt().takeIf { it > 0 }?.let { "+$it health from steps" },
        ).joinToString(" · ")

        private fun grouped(n: Int): String = n.toString().reversed().chunked(3).joinToString(",").reversed()
    }
}
