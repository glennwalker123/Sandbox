package com.glennwalker.bear.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Expected numbers are what the prototype's build() computes for the same inputs. */
class BearGeometryTest {
    @Test
    fun cubAtReferenceSize() {
        val g = BearGeometry(BearLook(Age.CUB, Mood.CALM), 25)
        assertEquals(Detail.STANDARD, g.detail)
        assertEquals(1, g.k)
        assertEquals(12.5, g.bodyX)
        assertEquals(8, g.halfWidth)
        assertEquals(10, g.height)
        assertEquals(20, g.bottom)
        assertEquals(11, g.top)
        assertEquals(6.0, g.earOffset)
        assertEquals(12, g.earTop)
        assertEquals(4, g.footOffset)
        assertEquals(2, g.footWidth)
        assertEquals(6, g.leftFootX)
        assertEquals(16, g.rightFootX)
        assertEquals(14, g.eyeTop)
    }

    @Test
    fun adultAtReferenceSize() {
        val g = BearGeometry(BearLook(Age.ADULT, Mood.CALM), 25)
        assertEquals(10, g.halfWidth)
        assertEquals(13, g.height)
        assertEquals(8, g.top)
        assertEquals(7.0, g.earOffset)
        assertEquals(12, g.eyeTop)
    }

    @Test
    fun detailTiers() {
        assertEquals(Detail.DETAILED, BearGeometry(BearLook(Age.CUB, Mood.CALM), 52).detail)
        assertEquals(2, BearGeometry(BearLook(Age.CUB, Mood.CALM), 52).k)
        assertEquals(Detail.TINY, BearGeometry(BearLook(Age.CUB, Mood.CALM), 13).detail)
        assertFalse(BearGeometry(BearLook(Age.CUB, Mood.CALM), 13).roomy)
        assertTrue(BearGeometry(BearLook(Age.CUB, Mood.CALM), 21).roomy)
    }

    @Test
    fun sleepyBearSitsLowerAndWorriedBearSways() {
        val calm = BearGeometry(BearLook(Age.CUB, Mood.CALM), 25)
        val sleepy = BearGeometry(BearLook(Age.CUB, Mood.SLEEPY), 25)
        assertEquals(calm.top + 1, sleepy.top)
        val worried = BearGeometry(BearLook(Age.CUB, Mood.WORRIED, tick = 0), 25)
        assertEquals(-1, worried.sway)
        assertEquals(1, BearGeometry(BearLook(Age.CUB, Mood.WORRIED, tick = 8), 25).sway)
    }

    @Test
    fun walkingLiftsOneFootAtATime() {
        val g = BearGeometry(BearLook(Age.CUB, Mood.CALM, step = 1), 25)
        assertFalse(g.leftFootDown)
        assertTrue(g.rightFootDown)
    }
}
