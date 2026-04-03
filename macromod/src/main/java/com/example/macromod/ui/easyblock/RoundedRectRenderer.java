package com.example.macromod.ui.easyblock;

import net.minecraft.client.gui.DrawContext;

/**
 * Draws filled rounded rectangles using DrawContext.fill() calls.
 * Corners are approximated with a per-row circle equation.
 */
public final class RoundedRectRenderer {
    private RoundedRectRenderer() {}

    /**
     * Draw a filled rounded rectangle.
     *
     * @param ctx   draw context
     * @param x     left
     * @param y     top
     * @param w     width
     * @param h     height
     * @param r     corner radius (clamped to half of min(w,h))
     * @param color ARGB color
     */
    public static void draw(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        if (r <= 0) {
            ctx.fill(x, y, x + w, y + h, color);
            return;
        }
        r = Math.min(r, Math.min(w, h) / 2);

        // Body: three rectangles forming a cross
        ctx.fill(x + r, y, x + w - r, y + h, color);         // vertical center
        ctx.fill(x, y + r, x + r, y + h - r, color);         // left strip
        ctx.fill(x + w - r, y + r, x + w, y + h - r, color); // right strip

        // Rounded corners row-by-row
        for (int row = 0; row < r; row++) {
            double dy = r - row - 0.5;
            int dx = (int) (r - Math.sqrt(r * r - dy * dy));
            // top-left
            ctx.fill(x + dx, y + row, x + r, y + row + 1, color);
            // top-right
            ctx.fill(x + w - r, y + row, x + w - dx, y + row + 1, color);
            // bottom-left
            ctx.fill(x + dx, y + h - row - 1, x + r, y + h - row, color);
            // bottom-right
            ctx.fill(x + w - r, y + h - row - 1, x + w - dx, y + h - row, color);
        }
    }

    /** Draw a 1px rounded-rect border (outline only). */
    public static void drawOutline(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        // Top edge
        ctx.fill(x + r, y, x + w - r, y + 1, color);
        // Bottom edge
        ctx.fill(x + r, y + h - 1, x + w - r, y + h, color);
        // Left edge
        ctx.fill(x, y + r, x + 1, y + h - r, color);
        // Right edge
        ctx.fill(x + w - 1, y + r, x + w, y + h - r, color);
        // Corner arcs (1px outline)
        for (int row = 0; row < r; row++) {
            double dy = r - row - 0.5;
            int dx = (int) (r - Math.sqrt(r * r - dy * dy));
            int dxNext = (row + 1 < r) ? (int) (r - Math.sqrt(r * r - (dy - 1) * (dy - 1))) : r;
            // Draw 1px dots along the arc
            ctx.fill(x + dx, y + row, x + dx + 1, y + row + 1, color);
            ctx.fill(x + w - dx - 1, y + row, x + w - dx, y + row + 1, color);
            ctx.fill(x + dx, y + h - row - 1, x + dx + 1, y + h - row, color);
            ctx.fill(x + w - dx - 1, y + h - row - 1, x + w - dx, y + h - row, color);
        }
    }
}
