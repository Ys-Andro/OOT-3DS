package org.triaevum.android.controls

/**
 * Coordinate conversions for persistent touch-control layouts.
 * The normalized storage format keeps user layouts stable across resolutions.
 */
object ControlLayoutPolicy {
    /** Version 2 stores X/Y as fractions of the current view dimensions. */
    const val NORMALIZED_LAYOUT_VERSION = 2

    @JvmStatic
    fun encodeCoordinate(position: Float, extent: Float): Float {
        if (!position.isFinite() || !extent.isFinite() || extent <= 0f) return 0f
        return (position / extent).coerceIn(0f, 1f)
    }

    /**
     * Decode a normalized position or a legacy pixel position and keep the
     * control center inside the available view.
     */
    @JvmStatic
    fun decodeCoordinate(
        savedPosition: Float,
        extent: Float,
        normalized: Boolean,
        margin: Float
    ): Float {
        if (!extent.isFinite() || extent <= 0f) return 0f
        val decoded = if (!savedPosition.isFinite()) {
            extent * 0.5f
        } else if (normalized) {
            savedPosition.coerceIn(0f, 1f) * extent
        } else {
            savedPosition
        }
        val requestedMargin = if (margin.isFinite()) margin.coerceAtLeast(0f) else 0f
        val safeMargin = requestedMargin.coerceAtMost(extent * 0.5f)
        return decoded.coerceIn(safeMargin, extent - safeMargin)
    }
}
