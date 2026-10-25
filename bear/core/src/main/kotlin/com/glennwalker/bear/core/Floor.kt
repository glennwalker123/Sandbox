package com.glennwalker.bear.core

import kotlin.math.floor

/**
 * The scene's perspective floor, in screen units: a 120 × 200 canvas, the size of the
 * front-screen dot matrix. World x runs left to right, world z runs away from the viewer.
 */
object Floor {
    const val WIDTH = 120
    const val HEIGHT = 200

    const val HORIZON = -4.2
    const val FRONT = 97.0
    const val Z_MIN = 0.1
    const val Z_MAX = 1.25

    /** Where the ball rests "in your hand" at the bottom of the screen. */
    const val HAND_X = 60.0
    const val HAND_Y = 186.0

    fun depthScale(z: Double): Double = 1.0 / (1.0 + z)

    fun screenX(x: Double, z: Double): Double = (30 + x * depthScale(z)) * 2

    fun screenY(z: Double): Double = (HORIZON + (FRONT - HORIZON) * depthScale(z)) * 2

    /** Size relative to the nearest point of the floor. */
    fun sizeAt(z: Double): Double = depthScale(z) / depthScale(Z_MIN)
}

/** JavaScript's Math.round: halves round towards positive infinity. */
internal fun jsRound(v: Double): Int = floor(v + 0.5).toInt()
