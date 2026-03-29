package com.mwa.n0name.gui.components;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Animated button with hover scale, press effect, and accent color.
 */
public class ButtonComponent extends AnimatedComponent {

    private String label;
    private String icon; // optional prefix icon character
    private int accentColor;
    private Runnable onClick;

    private static final int BG_NORMAL = 0xFF2D2D50;
    private static final int TEXT_COLOR = 0xFFE0E0E8;

    public ButtonComponent(int x, int y, int width, int height, String label, int accentColor, Runnable onClick) {
        super(x, y, width, height);
        this.label = label;
        this.accentColor = accentColor;
        this.onClick = onClick;
    }

    public ButtonComponent(int x, int y, int width, int height, String icon, String label, int accentColor, Runnable onClick) {
        this(x, y, width, height, label, accentColor, onClick);
        this.icon = icon;
    }

    public void setLabel(String label) { this.label = label; }
    public void setAccentColor(int color) { this.accentColor = color; }
    public void setOnClick(Runnable onClick) { this.onClick = onClick; }
    public String getLabel() { return label; }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        // Scale effect: hover -> 1.02, press -> 0.97
        float scale = 1f;
        if (pressProgress > 0.01f) {
            scale = 1f - 0.03f * pressProgress;
        } else if (hoverProgress > 0.01f) {
            scale = 1f + 0.02f * hoverProgress;
        }

        // Calculate scaled draw bounds (centered scaling)
        int cx = x + width / 2;
        int cy = y + height / 2;
        int sw = (int) (width * scale);
        int sh = (int) (height * scale);
        int sx = cx - sw / 2;
        int sy = cy - sh / 2;

        // Background: brighten on hover
        int bg = hoverProgress > 0.01f ? brighten(BG_NORMAL, 0.05f * hoverProgress) : BG_NORMAL;
        drawRoundedRect(ctx, sx, sy, sw, sh, bg);

        // Accent bottom border
        ctx.fill(sx + 2, sy + sh - 2, sx + sw - 2, sy + sh, withAlpha(accentColor, (int) (180 + 75 * hoverProgress)));

        // Text
        String text = (icon != null ? icon + " " : "") + label;
        int tw = tr.getWidth(text);
        int tx = sx + (sw - tw) / 2;
        int ty = sy + (sh - 8) / 2;
        int textColor = hoverProgress > 0.01f ? lerpColor(TEXT_COLOR, accentColor, hoverProgress * 0.5f) : TEXT_COLOR;
        ctx.drawTextWithShadow(tr, text, tx, ty, textColor);
    }

    @Override
    protected void onClick(int button) {
        if (button == 0 && onClick != null) {
            onClick.run();
        }
    }
}
