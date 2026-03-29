package com.mwa.n0name.gui.components;

import net.minecraft.client.gui.DrawContext;

/**
 * Base class for all animated UI components.
 * Provides hover/press animation state and hit testing.
 */
public abstract class AnimatedComponent {

    protected int x, y, width, height;
    protected boolean visible = true;
    protected float alpha = 1.0f;

    // Animation state
    protected float hoverProgress = 0f;   // 0 = normal, 1 = fully hovered
    protected float pressProgress = 0f;   // 0 = normal, 1 = fully pressed (snaps, then decays)
    protected boolean hovered = false;
    protected boolean pressed = false;

    private static final float HOVER_SPEED = 0.15f;
    private static final float PRESS_DECAY = 0.2f;

    public AnimatedComponent(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public void setAlpha(float alpha) { this.alpha = alpha; }
    public float getAlpha() { return alpha; }

    public boolean isHovered(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (!visible) return;

        hovered = isHovered(mouseX, mouseY);

        // Animate hover
        if (hovered) {
            hoverProgress = Math.min(1f, hoverProgress + HOVER_SPEED);
        } else {
            hoverProgress = Math.max(0f, hoverProgress - HOVER_SPEED);
        }

        // Animate press decay
        if (!pressed && pressProgress > 0f) {
            pressProgress = Math.max(0f, pressProgress - PRESS_DECAY);
        }

        renderContent(ctx, mouseX, mouseY, delta);
    }

    protected abstract void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta);

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        if (isHovered((int) mouseX, (int) mouseY)) {
            pressed = true;
            pressProgress = 1.0f;
            onClick(button);
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (pressed) {
            pressed = false;
            return true;
        }
        return false;
    }

    protected void onClick(int button) {}

    // --- Drawing helpers ---

    public static void drawRoundedRect(DrawContext ctx, int x, int y, int w, int h, int color) {
        // Simulated rounded corners: fill main body, skip corner pixels
        int r = 2; // corner radius in pixels
        ctx.fill(x + r, y, x + w - r, y + h, color);       // center
        ctx.fill(x, y + r, x + r, y + h - r, color);       // left strip
        ctx.fill(x + w - r, y + r, x + w, y + h - r, color); // right strip
        // Corner fills (1px inset)
        ctx.fill(x + 1, y + 1, x + r, y + r, color);
        ctx.fill(x + w - r, y + 1, x + w - 1, y + r, color);
        ctx.fill(x + 1, y + h - r, x + r, y + h - 1, color);
        ctx.fill(x + w - r, y + h - r, x + w - 1, y + h - 1, color);
    }

    protected static int brighten(int color, float amount) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * (1f + amount)));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * (1f + amount)));
        int b = Math.min(255, (int) ((color & 0xFF) * (1f + amount)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    protected static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    protected static int lerpColor(int from, int to, float progress) {
        int fa = (from >> 24) & 0xFF, fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
        int ta = (to >> 24) & 0xFF, tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
        int a = (int) (fa + (ta - fa) * progress);
        int r = (int) (fr + (tr - fr) * progress);
        int g = (int) (fg + (tg - fg) * progress);
        int b = (int) (fb + (tb - fb) * progress);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
