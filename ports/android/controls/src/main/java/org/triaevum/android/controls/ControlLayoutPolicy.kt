package org.triaevum.android.controls;

/** Coordinate conversions for persistent touch-control layouts. */
public final class ControlLayoutPolicy {
    /** Version 2 stores X/Y as fractions of the current view dimensions. */
    public static final int NORMALIZED_LAYOUT_VERSION = 2;

    private ControlLayoutPolicy() {}

    public static float encodeCoordinate(float position, float extent) {
        if (!Float.isFinite(position) || !Float.isFinite(extent) || extent <= 0.0f) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, position / extent));
    }

    /**
     * Decode a normalized position or a legacy pixel position and keep the
     * control center inside the available view.
     */
    public static float decodeCoordinate(
            float savedPosition, float extent, boolean normalized, float margin) {
        if (!Float.isFinite(extent) || extent <= 0.0f) {
            return 0.0f;
        }
        float decoded;
        if (!Float.isFinite(savedPosition)) {
            decoded = extent * 0.5f;
        } else {
            decoded = normalized
                    ? Math.max(0.0f, Math.min(1.0f, savedPosition)) * extent
                    : savedPosition;
        }
        float requestedMargin = Float.isFinite(margin) ? Math.max(0.0f, margin) : 0.0f;
        float safeMargin = Math.min(requestedMargin, extent * 0.5f);
        return Math.max(safeMargin, Math.min(extent - safeMargin, decoded));
    }
}
