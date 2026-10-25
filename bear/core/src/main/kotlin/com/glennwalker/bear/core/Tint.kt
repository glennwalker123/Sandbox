package com.glennwalker.bear.core

/**
 * Tiny neon hints. Almost every dot is the plain LED colour ([NONE]); a tint marks only a
 * few small things (a heart, a sweat drop) so colour stays a hint, never a fill.
 * Each tint has a lit and a dim shade, as RGB, shared by the app and the web preview.
 */
enum class Tint(val lit: Int, val dim: Int) {
    NONE(0xDCE4C6, 0x7D846F),
    PINK(0xFF6B9A, 0x8A4058),
    AMBER(0xFFC857, 0x8A6E33),
    MINT(0x7CF2C8, 0x437F69),
    CYAN(0x6FE3FF, 0x3D7A8A),
    LILAC(0xC3A6FF, 0x6A5A8E),
    ;

    companion object {
        /** HUD icons: happiness, energy, health. */
        val STATS = listOf(PINK, AMBER, MINT)
        val HEART = PINK
        val SWEAT = CYAN
        val SLEEP = LILAC

        /** The coloured part of each food: the berry's flesh, the apple's leaf, the honey's label. */
        fun food(kind: FoodKind) = when (kind) {
            FoodKind.BERRY -> PINK
            FoodKind.APPLE -> MINT
            FoodKind.FISH -> NONE
            FoodKind.HONEY -> AMBER
        }
    }
}
