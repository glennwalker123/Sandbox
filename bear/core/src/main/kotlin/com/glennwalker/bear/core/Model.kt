package com.glennwalker.bear.core

enum class Age { CUB, ADULT }

enum class Mood { CALM, HAPPY, WORRIED, SLEEPY, RELIEVED }

/** What the bear is doing. Mirrors the prototype's `mode`. */
enum class Mode { WANDER, READY, WATCH, CHASE, PICK, RETURN, BORED, TIRED, EAT, SLEEP, WORRY, SIGH }

/**
 * Everything a renderer needs to draw the bear for one frame.
 * Mirrors the prototype's `(st, o)` pair plus the tick-driven bits of `build()`.
 */
data class BearLook(
    val age: Age,
    val mood: Mood,
    /** Stretch (+1) or squash (-1) during the sigh. */
    val heightAdjust: Int = 0,
    /** Puff of air on the out-breath: 0 none, 1 small, 2 big. */
    val puff: Int = 0,
    /** Walking foot: 0 both down, 1 left lifted, 2 right lifted. */
    val step: Int = 0,
    /** Walking away from the viewer: no face. */
    val back: Boolean = false,
    /** Face offset while walking sideways: -1, 0 or 1. */
    val look: Int = 0,
    /** Ball held in the mouth. */
    val carry: Boolean = false,
    val tick: Int = 0,
) {
    val blinking: Boolean
        get() = tick % 40 < 2 && mood != Mood.HAPPY && mood != Mood.SLEEPY && mood != Mood.RELIEVED

    /** Nervous sideways sway while worried, -1 or 1. Only shown at standard size and up. */
    val sway: Int
        get() = if (mood == Mood.WORRIED) (if ((tick / 4) % 4 < 2) -1 else 1) else 0

    /** Which of the three rising Z positions is showing while sleepy. */
    val zFrame: Int
        get() = (tick / 5) % 3

    val eyesClosed: Boolean
        get() = mood == Mood.SLEEPY || mood == Mood.RELIEVED || blinking
}

/** The big relieved sigh, [Tuning.SIGH_TICKS] long: in-breath stretch, out-breath squash with a puff. */
object Sigh {
    fun at(age: Age, timer: Int, tick: Int = timer): BearLook = when {
        timer < 12 -> BearLook(age, Mood.CALM, heightAdjust = 1, tick = tick)
        timer < 26 -> BearLook(age, Mood.RELIEVED, heightAdjust = -1, puff = if (timer < 19) 1 else 2, tick = tick)
        else -> BearLook(age, Mood.RELIEVED, tick = tick)
    }
}

/** Detail tier for a bear drawn at size [n] (25 is the reference size). */
enum class Detail {
    TINY, STANDARD, DETAILED;

    companion object {
        fun of(n: Int): Detail = when {
            n >= 44 -> DETAILED
            n >= 21 -> STANDARD
            else -> TINY
        }
    }
}
