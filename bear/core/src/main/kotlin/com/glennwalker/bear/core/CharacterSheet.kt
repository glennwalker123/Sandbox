package com.glennwalker.bear.core

/** What the character sheet shows: every mood and pose, animated by a shared tick. */
object CharacterSheet {
    /** One row: a mood or pose. [lift] raises the bear by that many reference cells (for the hop). */
    class Entry(val label: String, val look: (Age, Int) -> BearLook, val lift: (Int) -> Int = { 0 })

    val entries = listOf(
        Entry("Calm", { age, t -> BearLook(age, Mood.CALM, tick = t) }),
        Entry("Happy", { age, t -> BearLook(age, Mood.HAPPY, tick = t) }),
        Entry("Worried", { age, t -> BearLook(age, Mood.WORRIED, tick = t) }),
        Entry("Sleepy", { age, t -> BearLook(age, Mood.SLEEPY, tick = t) }),
        Entry("Relieved", { age, t -> BearLook(age, Mood.RELIEVED, tick = t) }),
        Entry("Hop", { age, t -> BearLook(age, Mood.HAPPY, tick = t) }, lift = { t -> BearSim.HOP.getOrElse(t % 20) { 0 } }),
        Entry("Walking", { age, t -> BearLook(age, Mood.CALM, step = walkStep(t), look = 1, tick = t) }),
        Entry("Walking away", { age, t -> BearLook(age, Mood.CALM, step = walkStep(t), back = true, tick = t) }),
        Entry("Carrying the ball", { age, t -> BearLook(age, Mood.CALM, carry = true, tick = t) }),
        Entry("Sigh", { age, t ->
            val s = t % (Tuning.SIGH_TICKS + 20)
            if (s < Tuning.SIGH_TICKS) Sigh.at(age, s, t) else BearLook(age, Mood.CALM, tick = t)
        }),
    )

    /** The three sizes the bear is judged at: up close, the Glyph reference size, far away. */
    val sizes = intArrayOf(52, 25, 13)

    private fun walkStep(t: Int) = if ((t / 3) % 2 == 0) 1 else 2

    /** Rows to raise a bear of size [n] for [entry] at [tick]. */
    fun lift(entry: Entry, tick: Int, n: Int): Int = jsRound(entry.lift(tick) * n / 25.0)
}
