package com.example.macromod.ui.easyblock;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Base screen with VapeLite-style popup animation.
 * Opens with a zoom-in settle (1.15 → 1.0), closes with zoom-out (1.0 → 1.5).
 * Subclasses implement {@link #drawScreen} for content.
 */
public abstract class BasePopupScreen extends Screen {

    private final Screen parent;

    // Animation state
    private float percent = 1.15f;
    private float backdropAlpha = 0f;
    private boolean closing = false;

    // Animation constants
    private static final float OPEN_TARGET     = 1.0f;
    private static final float OPEN_DIV        = 20f;   // ~333ms at 60fps
    private static final float CLOSE_TARGET    = 1.5f;
    private static final float CLOSE_DIV       = 12f;   // ~200ms at 60fps
    private static final float CLOSE_THRESHOLD = 1.35f;
    private static final float BACKDROP_MAX    = 0.7f;
    private static final float BACKDROP_DIV    = 15f;

    protected BasePopupScreen(Text title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    /** Subclasses draw their content here. Called inside the scale transform. */
    protected abstract void drawScreen(DrawContext ctx, int mx, int my, float delta);

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        // Per-frame animation update
        if (closing) {
            percent = Anim.smooth(percent, CLOSE_TARGET, CLOSE_DIV);
            backdropAlpha = Anim.smooth(backdropAlpha, 0f, BACKDROP_DIV);
            if (percent >= CLOSE_THRESHOLD) {
                if (client != null) client.setScreen(parent);
                return;
            }
        } else {
            percent = Anim.smooth(percent, OPEN_TARGET, OPEN_DIV);
            backdropAlpha = Anim.smooth(backdropAlpha, BACKDROP_MAX, BACKDROP_DIV);
        }

        // Backdrop
        int alpha = (int) (backdropAlpha * 255);
        ctx.fill(0, 0, width, height, alpha << 24);

        // Scale transform around screen center
        ctx.getMatrices().push();
        ctx.getMatrices().translate(width / 2.0, height / 2.0, 0);
        ctx.getMatrices().scale(percent, percent, 1f);
        ctx.getMatrices().translate(-width / 2.0, -height / 2.0, 0);

        drawScreen(ctx, mx, my, delta);

        ctx.getMatrices().pop();
    }

    /** Start the close animation. Screen closes when animation completes. */
    public void animClose() {
        closing = true;
    }

    /** True when the open animation has settled (percent ≈ 1.0). */
    public boolean isFullyOpen() {
        return !closing && Math.abs(percent - OPEN_TARGET) < 0.01f;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int mods) {
        if (keyCode == 256) { // Escape
            animClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, mods);
    }
}
