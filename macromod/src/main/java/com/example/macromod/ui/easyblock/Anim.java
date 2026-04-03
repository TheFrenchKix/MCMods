package com.example.macromod.ui.easyblock;

/**
 * Per-frame animation utilities (VapeLite-style).
 * All methods are frame-rate independent via divisor-based smoothing.
 */
public final class Anim {
    private Anim() {}

    /** Smoothly interpolate current toward target. Call once per frame. */
    public static float smooth(float current, float target, float divisor) {
        return current + (target - current) / divisor;
    }

    /** Linearly interpolate between two ARGB colors by factor t (0–1). */
    public static int lerpColor(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) ((from >> 24 & 0xFF) + ((to >> 24 & 0xFF) - (from >> 24 & 0xFF)) * t);
        int r = (int) ((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
        int g = (int) ((from >>  8 & 0xFF) + ((to >>  8 & 0xFF) - (from >>  8 & 0xFF)) * t);
        int b = (int) ((from       & 0xFF) + ((to       & 0xFF) - (from       & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
