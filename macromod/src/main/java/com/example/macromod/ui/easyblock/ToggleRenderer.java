package com.example.macromod.ui.easyblock;

import net.minecraft.client.gui.DrawContext;

/**
 * Animated pill-shaped toggle switch.
 * Pass an animation value 0.0 (off) to 1.0 (on).
 */
public final class ToggleRenderer {
    private ToggleRenderer() {}

    public static final int TOGGLE_W = 30;
    public static final int TOGGLE_H = 14;

    private static final int C_OFF_BG  = 0xFF3A3A4A;
    private static final int C_ON_BG   = 0xFF4677FF;
    private static final int C_KNOB    = 0xFFFFFFFF;

    /**
     * Draw a toggle switch.
     *
     * @param ctx  draw context
     * @param x    left x
     * @param y    top y
     * @param t    animation progress 0.0 (off) to 1.0 (on)
     */
    public static void draw(DrawContext ctx, int x, int y, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int bg = Anim.lerpColor(C_OFF_BG, C_ON_BG, t);

        // Background pill
        int r = TOGGLE_H / 2;
        RoundedRectRenderer.draw(ctx, x, y, TOGGLE_W, TOGGLE_H, r, bg);

        // Knob (circle approximated as rounded square)
        int kd = TOGGLE_H - 4;  // knob diameter
        int kx = (int) (x + 2 + (TOGGLE_W - kd - 4) * t);
        int ky = y + 2;
        RoundedRectRenderer.draw(ctx, kx, ky, kd, kd, kd / 2, C_KNOB);
    }
}
