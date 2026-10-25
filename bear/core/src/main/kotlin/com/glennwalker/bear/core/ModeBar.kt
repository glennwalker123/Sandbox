package com.glennwalker.bear.core

/**
 * The title bar shown across the top of the screen during an activity, in the spirit of an
 * old desktop window: lit border lines, dim stripes, the title in a 5 × 7 dot font, and a
 * close box with a cross in the right corner.
 */
object ModeBar {
    const val LEFT = 4
    const val RIGHT = 115
    const val TOP = 2
    const val BOTTOM = 12

    /** Close box, in dots. */
    const val BOX_LEFT = 104
    const val BOX_RIGHT = 112

    /** Rows the HUD moves down by while the bar is showing. */
    const val HUD_SHIFT = 12

    /** Generous touch target around the close box, in scene dots. */
    fun closeHit(x: Double, y: Double) = x >= BOX_LEFT - 8 && x <= Floor.WIDTH && y >= 0 && y <= BOTTOM + 6

    /** Draws the bar for [title] with [put] (x, y, value). */
    fun draw(title: String, put: (Int, Int, Int) -> Unit) {
        val lit = SceneRaster.LIT
        val dim = SceneRaster.DIM
        val hole = SceneRaster.HOLE
        // Clear the band, then the border lines top and bottom.
        for (y in TOP..BOTTOM) for (x in LEFT..RIGHT) put(x, y, hole)
        for (x in LEFT..RIGHT) { put(x, TOP, lit); put(x, BOTTOM, lit) }
        // Title, centred between the left edge and the close box, with clear space around it.
        val width = DotFont.width(title)
        val textLeft = (LEFT + BOX_LEFT - 2) / 2 - width / 2
        val textRight = textLeft + width - 1
        // Stripes on alternate rows, stopping short of the title and the close box.
        for (y in TOP + 2 until BOTTOM step 2) for (x in LEFT + 2..BOX_LEFT - 3) {
            if (x < textLeft - 3 || x > textRight + 3) put(x, y, dim)
        }
        DotFont.draw(title, textLeft, TOP + 2) { x, y -> put(x, y, lit) }
        // Close box: a square outline with a cross inside.
        val top = TOP + 1
        val bottom = BOTTOM - 1
        for (x in BOX_LEFT..BOX_RIGHT) { put(x, top, lit); put(x, bottom, lit) }
        for (y in top..bottom) { put(BOX_LEFT, y, lit); put(BOX_RIGHT, y, lit) }
        for (i in 2..(BOX_RIGHT - BOX_LEFT - 2)) {
            put(BOX_LEFT + i, top + i, lit)
            put(BOX_RIGHT - i, top + i, lit)
        }
    }
}

/** A 5 × 7 capital-letter dot font, one dot between letters. */
object DotFont {
    fun width(text: String) = if (text.isEmpty()) 0 else text.length * 6 - 1

    fun draw(text: String, left: Int, top: Int, dot: (Int, Int) -> Unit) {
        text.uppercase().forEachIndexed { i, ch ->
            val glyph = GLYPHS[ch] ?: return@forEachIndexed
            glyph.forEachIndexed { y, row -> row.forEachIndexed { x, c -> if (c == '#') dot(left + i * 6 + x, top + y) } }
        }
    }

    private val GLYPHS: Map<Char, List<String>> = mapOf(
        'A' to listOf(".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
        'B' to listOf("####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."),
        'C' to listOf(".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."),
        'D' to listOf("####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."),
        'E' to listOf("#####", "#....", "#....", "####.", "#....", "#....", "#####"),
        'F' to listOf("#####", "#....", "#....", "####.", "#....", "#....", "#...."),
        'G' to listOf(".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".###."),
        'H' to listOf("#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
        'I' to listOf(".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###."),
        'J' to listOf("..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##.."),
        'K' to listOf("#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"),
        'L' to listOf("#....", "#....", "#....", "#....", "#....", "#....", "#####"),
        'M' to listOf("#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"),
        'N' to listOf("#...#", "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#"),
        'O' to listOf(".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
        'P' to listOf("####.", "#...#", "#...#", "####.", "#....", "#....", "#...."),
        'Q' to listOf(".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"),
        'R' to listOf("####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"),
        'S' to listOf(".####", "#....", "#....", ".###.", "....#", "....#", "####."),
        'T' to listOf("#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."),
        'U' to listOf("#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
        'V' to listOf("#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."),
        'W' to listOf("#...#", "#...#", "#...#", "#.#.#", "#.#.#", "#.#.#", ".#.#."),
        'X' to listOf("#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"),
        'Y' to listOf("#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."),
        'Z' to listOf("#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"),
        ' ' to List(7) { "....." },
    )
}
