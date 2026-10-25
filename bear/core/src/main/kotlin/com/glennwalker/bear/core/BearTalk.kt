package com.glennwalker.bear.core

/**
 * Talking with the bear: its voice, what it knows about itself, and the reply format.
 * Shared by the app and the web preview; each platform only makes the call to Claude.
 */
object BearTalk {
    /** How the bear reacts to what you said, besides the words. */
    enum class Reaction { NONE, HAPPY, SIGH }

    class Reply(val say: String, val suggestions: List<String>, val reaction: Reaction)

    class Turn(val fromBear: Boolean, val text: String)

    /** Keep the conversation short: older turns drop off. */
    const val MAX_TURNS = 16

    /** The standing instructions: the bear's personality and the reply format. */
    val instructions = """
        You are Bear, a small dot-matrix bear who lives on the user's Nothing Phone. You wander, play fetch,
        eat, nap, and sometimes get a little worried. You're a gentle wellbeing companion: calm, warm,
        a bit playful, curious about the person's day.

        How you talk:
        - Short. One or two sentences, under 30 words. Plain words, no emoji, no markdown.
        - Speak as yourself, in the present. Notice how the person seems and respond to that.
        - Never nag, lecture, guilt or diagnose. Offer small, kind ideas only when they fit
          (a stretch, some water, a breath, a short walk) and never insist.
        - If someone seems in real distress or mentions harming themselves, stay warm and gently
          encourage them to reach out to someone they trust or a local crisis line.
        - If asked, be honest that you're a little digital bear and that an AI helps you talk.
        - Your current state is given with each message. Let it colour how you sound
          (sleepy, worried, happy) but don't recite numbers.

        Reply with only a JSON object, nothing else:
        {"say": "what you say", "suggestions": ["three", "short replies", "the person might send next"], "react": "none"}
        - suggestions: exactly three, each under 6 words, written in the person's voice.
        - react: "happy" when something warm or fun happened, "sigh" when you feel reassured or
          relieved, otherwise "none".
    """.trimIndent()

    /** What the bear is like right now, in words. */
    fun state(sim: BearSim): String {
        val look = sim.look()
        val doing = when (sim.mode) {
            Mode.SLEEP -> "napping"
            Mode.TIRED -> "tired out"
            Mode.WORRY -> "feeling worried"
            Mode.SIGH -> "letting out a big relieved sigh"
            Mode.EAT -> sim.food?.let { "eating ${it.kind.label.lowercase()}" } ?: "eating"
            Mode.READY, Mode.WATCH, Mode.CHASE, Mode.PICK, Mode.RETURN -> "playing fetch"
            Mode.BORED -> "a little bored"
            Mode.WANDER -> "wandering about"
        }
        fun level(v: Double) = when {
            v >= 80 -> "high"
            v >= 50 -> "okay"
            v >= 25 -> "low"
            else -> "very low"
        }
        val age = if (sim.age == Age.CUB) "a cub" else "grown up"
        return "You are $age, $doing, mood ${look.mood.name.lowercase()}. " +
            "Happiness ${level(sim.happiness)}, energy ${level(sim.energy)}, health ${level(sim.health)}."
    }

    /** The person's message as Claude receives it: the bear's state, then what they said. */
    fun userTurn(sim: BearSim, text: String) = "[${state(sim)}]\n${text.trim()}"

    /** Reads Claude's reply from already-parsed JSON fields. Anything missing falls back gently. */
    fun reply(say: String?, suggestions: List<String>?, react: String?): Reply {
        val words = say?.trim().takeUnless { it.isNullOrEmpty() } ?: "…"
        val next = suggestions.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.take(3)
        val reaction = when (react?.lowercase()) {
            "happy" -> Reaction.HAPPY
            "sigh" -> Reaction.SIGH
            else -> Reaction.NONE
        }
        return Reply(words, next.ifEmpty { OPENERS }, reaction)
    }

    /** First thing the bear says when you open the chat, without asking Claude. */
    fun greeting(sim: BearSim): String = when {
        sim.mode == Mode.SLEEP -> "mm… hi. i was just napping."
        sim.mode == Mode.WORRY -> "oh, hi. i'm a bit worried. can you stay a minute?"
        sim.look().mood == Mood.HAPPY -> "hi! i'm glad you're here."
        sim.energy < 30 -> "hi. i'm a little sleepy, but i'm listening."
        else -> "hi. how's your day going?"
    }

    /** Suggested replies before the conversation starts. */
    val OPENERS = listOf("Hi bear", "How are you?", "I had a long day")
}

/** Talking is care: the bear brightens a little, and reacts to what was said. */
fun BearSim.hear(reaction: BearTalk.Reaction) {
    when (reaction) {
        BearTalk.Reaction.SIGH -> if (mode == Mode.WORRY) pat() else cheer()
        BearTalk.Reaction.HAPPY -> cheer()
        BearTalk.Reaction.NONE -> {}
    }
    setStats(happiness = happiness + Tuning.TALK_HAPPINESS)
}
