package com.glennwalker.bear.core

/**
 * Every tuning number in one place.
 *
 * Values are the prototype's, which run everything fast so behaviour is easy to see.
 * Stat rates are per simulation tick ([TICK_MS]); they become real-time rates in the
 * behaviour and persistence milestone.
 */
object Tuning {
    /** Simulation step, in milliseconds. */
    const val TICK_MS = 60L

    // Starting stats (0..100).
    const val START_HAPPINESS = 70.0
    const val START_ENERGY = 80.0
    const val START_HEALTH = 70.0

    // Happiness.
    const val HAPPINESS_DECAY = 0.03
    const val HAPPINESS_DECAY_WORRIED = 0.06
    const val PAT_HAPPINESS = 6.0
    const val SIGH_HAPPINESS = 25.0
    const val FETCH_HAPPINESS = 15.0
    /** Each reply from the bear in a conversation. */
    const val TALK_HAPPINESS = 2.0
    const val WORRY_IGNORED_HAPPINESS = -5.0

    // Energy.
    const val ENERGY_REST_GAIN = 0.004
    const val ENERGY_WALK_COST = 0.012
    const val ENERGY_RUN_COST = 0.16
    const val ENERGY_SLEEP_GAIN = 0.35
    const val WORRY_BUTTON_WAKE_ENERGY = 20.0
    const val SLEEP_BELOW_ENERGY = 12.0
    const val WAKE_AT_ENERGY = 95.0
    const val TIRED_BELOW_ENERGY = 25.0
    const val SLOW_BELOW_ENERGY = 35.0

    // Health.
    const val HEALTH_DECAY = 0.02
    const val HEALTH_GAIN_WHEN_WELL = 0.008
    const val WELL_HAPPINESS = 50.0
    const val WELL_ENERGY = 40.0
    const val SLOW_BELOW_HEALTH = 30.0

    // Mood.
    /** Prototype rule: below this happiness the bear shows a worried face. */
    const val WORRIED_FACE_BELOW_HAPPINESS = 35.0
    const val WORRIED_FACE_WHEN_UNHAPPY = true
    const val HOP_WHEN_HAPPIER_THAN = 80.0
    const val RANDOM_HOP_CHANCE = 0.012

    // Worry.
    const val WORRY_CHANCE = 0.0012
    const val WORRY_CHANCE_UNHAPPY = 0.01
    const val UNHAPPY_BELOW_HAPPINESS = 38.0
    const val WORRY_MAX_TICKS = 500
    const val WORRY_SWAY = 4.0
    const val WORRY_SWAY_RATE = 0.09
    const val SIGH_TICKS = 40

    // Movement. Base speeds are scaled by vitality (energy and health) and depth.
    const val WANDER_SPEED_X = 0.5
    const val WANDER_SPEED_Z = 0.01
    const val WANDER_SPEED_BOOST = 1.3
    const val CHASE_SPEED_X = 1.2
    const val CHASE_SPEED_Z = 0.022
    const val RETURN_SPEED_X = 1.1
    const val RETURN_SPEED_Z = 0.02
    const val EAT_SPEED_X = 0.8
    const val EAT_SPEED_Z = 0.015
    const val RUN_SPEED = 1.6
    const val CUB_PACE = 0.8
    const val ADULT_PACE = 0.95

    // Wander pauses, in ticks.
    const val PAUSE_MIN = 24
    const val PAUSE_RANDOM = 40
    const val PAUSE_WHEN_LOW = 40

    // Fetch.
    const val FETCH_SUCCESS_CUB = 0.45
    const val FETCH_SUCCESS_ADULT = 0.7
    const val PICK_TICKS = 8
    const val BORED_TICKS = 50
    const val TIRED_TICKS = 45

    // Your own sleep and steps, from Health Connect. Rewards only: nothing is taken away.
    /** Hours of sleep that give the full energy boost. */
    const val SLEEP_GOAL_HOURS = 8.0
    /** Energy added once per night, scaled by how close you got to [SLEEP_GOAL_HOURS]. */
    const val SLEEP_ENERGY_MAX = 45.0
    /** Steps in a day that give the full health boost. */
    const val STEP_GOAL = 8000
    /** Health added across a day, in proportion to steps, up to [STEP_GOAL]. */
    const val STEPS_HEALTH_MAX = 40.0

    // Food. What each food adds once eaten. The berry is the prototype's food.
    const val EAT_TICKS = 26
    val FOOD = mapOf(
        FoodKind.BERRY to FoodEffect(health = 25.0, energy = 8.0, happiness = 6.0),
        FoodKind.APPLE to FoodEffect(health = 20.0, energy = 5.0, happiness = 4.0),
        FoodKind.FISH to FoodEffect(health = 30.0, energy = 15.0, happiness = 8.0),
        FoodKind.HONEY to FoodEffect(health = 5.0, energy = 10.0, happiness = 20.0),
    )
}

data class FoodEffect(val health: Double, val energy: Double, val happiness: Double)
