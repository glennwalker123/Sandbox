package com.glennwalker.bear.core

import kotlin.math.floor
import kotlin.math.max

/**
 * The bear's body layout at size [n], in grid cells, ported from the prototype's `build()`.
 *
 * Shared by every renderer so the bear sits in the same place whether it is drawn as
 * vectors or, later, rasterised to dots. Cell (0, 0) is the top-left of an n × n box.
 */
class BearGeometry(val look: BearLook, val n: Int) {
    val scale = n / 25.0
    val center = n shr 1
    val detail = Detail.of(n)

    /** Standard size and up: inner ears, sway, sweat drop and Zs are drawn. */
    val roomy = scale > 0.8

    /** Pixel size of face features: 2 for the detailed tier, else 1. */
    val k = if (n >= 44) 2 else 1

    private val cub = look.age == Age.CUB
    val sway = (if (roomy) look.sway else 0) * k
    private val faceLook = (if (look.back) 0 else look.look) * k

    /** Horizontal centre of the body. */
    val bodyX = (if (k == 2) center.toDouble() else center + 0.5) + sway
    val halfWidth = if (cub) max(4, jsRound(8 * scale)) else max(5, jsRound(9.5 * scale))
    val height = (if (cub) max(5, jsRound(10 * scale)) else max(6, jsRound(12.5 * scale))) + look.heightAdjust * k

    /** Last row of the body. */
    val bottom = jsRound(n * 0.8) - (k - 1)
    val top = bottom - height + 1 + (if (look.mood == Mood.SLEEPY && roomy) k else 0)
    val middle = (top + bottom) / 2.0

    /** Body ellipse, in continuous coordinates. */
    val bodyCenterY = middle + 0.5
    val radiusX = halfWidth + 0.5
    val radiusY = (bottom - top + 1) / 2.0 + 0.3

    /** Ears: circles at (bodyX ± earOffset, earY). */
    val earRadius = if (cub) max(1.2, 2.4 * scale) else max(1.2, 2.6 * scale)
    val earOffset = (if (cub) 5.8 * scale else 6.8 * scale).let { if (k == 2) it else jsRound(it).toDouble() }
    val earTop = top + jsRound(scale)
    val earY = earTop + 0.5
    /** Inner ear radius as a fraction of the ear. */
    val innerEarFraction = if (k == 2) 0.42 else 0.5

    /** Feet: [footWidth] cells wide, [k] rows tall, starting the row after [bottom]. */
    val footOffset = max(1, jsRound(halfWidth * 0.5))
    val footWidth = if (roomy) 2 * k else 1
    private val footColumn = floor(bodyX).toInt()
    val leftFootX = footColumn - footOffset - footWidth
    val rightFootX = footColumn + footOffset
    val footY = bottom + 1
    val leftFootDown = look.step != 1
    val rightFootDown = look.step != 2

    /** Bottom row used to stand the bear on the floor (the feet's last row is one below). */
    val anchorBottom = bottom + k - 1

    /** Column the face is centred on. */
    val faceX = center + sway + faceLook

    /** Face feature cell for offset (dx, dy) on the standard grid, scaled by [k]. */
    fun faceCellX(dx: Int): Int = faceX + dx * k - (k - 1)
    fun faceCellY(dy: Int): Int = eyeTop + dy * k
    val eyeTop = jsRound(middle - (if (cub) 1.5 else 2.0) * k)

    /** Tiny tier: the row the dot eyes sit on. */
    val tinyEyeY = jsRound(middle - if (cub) 0.5 else 1.0)
}
