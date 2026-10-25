package com.glennwalker.bear.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Compares the port against reference data produced by running bear-prototype.html
 * (see tools/prototype-frames.mjs). Every bear and every scene frame must match dot for dot.
 */
class PrototypeMatchTest {
    private fun resource(name: String): List<String> =
        javaClass.getResource("/prototype/$name")!!.readText().trimEnd().lines()

    @Test
    fun everyBearMatchesThePrototype() {
        val lines = resource("bears.txt")
        var i = 0
        var cases = 0
        val failures = mutableListOf<String>()
        while (i < lines.size) {
            val header = lines[i].split(" ")
            val age = if (header[0] == "cub") Age.CUB else Age.ADULT
            val mood = Mood.valueOf(header[1].uppercase())
            val n = header[2].toInt()
            val t = header[3].toInt()
            val opts = if (header[4] == "-") emptyMap() else header[4].split(",").associate { it.substringBefore("=") to it.substringAfter("=").toInt() }
            val look = BearLook(
                age, mood,
                heightAdjust = opts["hAdj"] ?: 0,
                puff = opts["puff"] ?: 0,
                step = opts["step"] ?: 0,
                back = (opts["back"] ?: 0) == 1,
                look = opts["look"] ?: 0,
                carry = (opts["carry"] ?: 0) == 1,
                tick = t,
            )
            val expected = lines.subList(i + 1, i + 1 + n)
            val grid = BearRaster.build(look, n)
            val actual = (0 until n).map { y -> (0 until n).joinToString("") { x -> grid[x, y].toString() } }
            val meta = listOf(grid.center, grid.anchorBottom, grid.halfWidth)
            if (actual != expected || meta != header.subList(5, 8).map { it.toInt() }) {
                failures += lines[i] + "\n" + expected.zip(actual).joinToString("\n") { (e, a) -> "$e   $a" }
            }
            cases++
            i += n + 1
        }
        if (failures.isNotEmpty()) fail("${failures.size} of $cases bears differ. First:\n${failures.first()}")
    }

    /** Plays back the prototype's random numbers in order. */
    private class Replay(private val values: List<Double>) : Random() {
        var used = 0
        override fun nextBits(bitCount: Int): Int = error("unexpected random call")
        override fun nextDouble(): Double = values[used++]
    }

    @Test
    fun scriptedSessionMatchesThePrototypeFrameByFrame() {
        val lines = resource("scene.txt")
        var i = 0
        val ticks = lines[i++].substringAfter(" ").toInt()
        val actionCount = lines[i++].substringAfter(" ").toInt()
        val actions = lines.subList(i, i + actionCount).groupBy({ it.substringBefore(" ").toInt() }, { it.substringAfter(" ") })
        i += actionCount
        val randomCount = lines[i++].substringAfter(" ").toInt()
        val random = Replay(lines.subList(i, i + randomCount).map { it.toDouble() })
        i += randomCount
        i++ // "hashes"
        val hashes = lines[i++].split(" ")
        val fullFrames = mutableMapOf<Int, List<String>>()
        while (i < lines.size) {
            val tick = lines[i].substringAfter(" ").toInt()
            fullFrames[tick] = lines.subList(i + 1, i + 201)
            i += 201
        }

        // The prototype starts with the ball in hand and the stats stacked down the left.
        val sim = BearSim(random).apply { startFetch() }
        val raster = SceneRaster(HudStyle.STACKED)
        for (tick in 1..ticks) {
            for (a in actions[tick].orEmpty()) {
                val p = a.split(" ")
                when (p[0]) {
                    "feed" -> sim.feed()
                    "pat" -> sim.pat()
                    "worry" -> sim.makeWorry()
                    "age" -> sim.toggleAge()
                    "energy" -> sim.setStats(energy = p[1].toDouble())
                    "pointerdown" -> sim.pointerDown(p[1].toDouble(), p[2].toDouble(), p[3].toLong())
                    "pointermove" -> sim.pointerMove(p[1].toDouble(), p[2].toDouble(), p[3].toLong())
                    "pointerup" -> sim.pointerUp(p[1].toDouble(), p[2].toDouble(), p[3].toLong())
                    else -> error("unknown action $a")
                }
            }
            sim.step()
            val dots = raster.render(sim)
            var h = 0x811c9dc5.toInt()
            for (v in dots) h = (h xor v.toInt()) * 0x01000193
            val hash = Integer.toUnsignedString(h, 16)
            if (hash != hashes[tick - 1]) {
                val near = fullFrames.keys.filter { it >= tick }.minOrNull()
                val detail = near?.let { t ->
                    if (t == tick) {
                        val actual = (0 until 200).map { y -> (0 until 120).joinToString("") { x -> dots[y * 120 + x].toString() } }
                        "\n" + fullFrames.getValue(t).zip(actual).mapIndexedNotNull { y, (e, a) -> if (e != a) "row $y\n$e\n$a" else null }.take(6).joinToString("\n")
                    } else ""
                } ?: ""
                fail("Frame $tick differs (mode=${sim.mode}, x=${sim.x}, z=${sim.z}).$detail")
            }
        }
        assertEquals(randomCount, random.used, "random numbers used")
    }
}
