package org.triaevum.android.controls

import org.junit.Assert.assertEquals
import org.junit.Test

class ControlLayoutPolicyTest {
    @Test fun normalizedCoordinatesScaleWithCurrentDisplay() {
        val savedX = ControlLayoutPolicy.encodeCoordinate(600f, 2400f)
        val savedY = ControlLayoutPolicy.encodeCoordinate(810f, 1080f)

        assertEquals(0.25f, savedX, 0.0001f)
        assertEquals(0.75f, savedY, 0.0001f)
        assertEquals(480f, ControlLayoutPolicy.decodeCoordinate(savedX, 1920f, true, 24f), 0.001f)
        assertEquals(900f, ControlLayoutPolicy.decodeCoordinate(savedY, 1200f, true, 24f), 0.001f)
    }

    @Test fun legacyPixelCoordinatesAreClampedOnSmallerDisplays() {
        assertEquals(870f, ControlLayoutPolicy.decodeCoordinate(2100f, 900f, false, 30f), 0.001f)
        assertEquals(20f, ControlLayoutPolicy.decodeCoordinate(-80f, 400f, false, 20f), 0.001f)
    }

    @Test fun invalidExtentsAndCoordinatesAreSafe() {
        assertEquals(0f, ControlLayoutPolicy.encodeCoordinate(10f, 0f), 0.0f)
        assertEquals(0f, ControlLayoutPolicy.decodeCoordinate(10f, Float.NaN, true, 2f), 0.0f)
        assertEquals(50f, ControlLayoutPolicy.decodeCoordinate(Float.NaN, 100f, true, 50f), 0.0f)
        assertEquals(1f, ControlLayoutPolicy.encodeCoordinate(1000f, 100f), 0.0001f)
    }
}
