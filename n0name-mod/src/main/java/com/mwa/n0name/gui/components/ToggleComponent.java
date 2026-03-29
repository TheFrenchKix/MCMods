package com.mwa.n0name.gui.components;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Animated toggle switch with sliding knob.
 * Pill-shaped track (24x12), knob slides left↔right via toggleProgress lerp.
 */
public class ToggleComponent extends AnimatedComponent {

    private boolean checked;
    private float toggleProgress; // 0 = off (left), 1 = on (right)
    private String label;
    private int accentColor;
    private Runnable onToggle;

    private static final int TRACK_W = 24;
    private static final int TRACK_H = 12;
    private static final int KNOB_SIZE = 8;
    private static final int OFF_COLOR = 0xFF555555;
    private static final int TEXT_COLOR = 0xFFE0E0E8;
    private static final float TOGGLE_SPEED = 0.18f;

    public ToggleComponent(int x, int y, String label, boolean checked, int accentColor, Runnable onToggle) {
        super(x, y, TRACK_W + 6 + MinecraftClient.getInstance().textRenderer.getWidth(label), TRACK_H);
        this.label = label;
        this.checked = checked;
        this.toggleProgress = checked ? 1f : 0f;
        this.accentColor = accentColor;
        this.onToggle = onToggle;
    }

    public boolean isChecked() { return checked; }
    public void setChecked(boolean checked) {
        this.checked = checked;
    }
    public void setLabel(String label) { this.label = label; }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        // Animate toggle
        float target = checked ? 1f : 0f;
        if (Math.abs(toggleProgress - target) > 0.01f) {
            toggleProgress += (target - toggleProgress) * TOGGLE_SPEED;
        } else {
            toggleProgress = target;
        }

        // Track background
        int trackColor = lerpColor(OFF_COLOR, accentColor, toggleProgress);
        drawRoundedRect(ctx, x, y, TRACK_W, TRACK_H, trackColor);

        // Knob
        int knobTravel = TRACK_W - KNOB_SIZE - 4;
        int knobX = x + 2 + (int) (knobTravel * toggleProgress);
        int knobY = y + (TRACK_H - KNOB_SIZE) / 2;
        int knobColor = 0xFFE0E0E8;
        if (hoverProgress > 0.01f) knobColor = brighten(knobColor, 0.05f * hoverProgress);
        drawRoundedRect(ctx, knobX, knobY, KNOB_SIZE, KNOB_SIZE, knobColor);

        // Label
        int textX = x + TRACK_W + 6;
        int textY = y + (TRACK_H - 8) / 2;
        ctx.drawTextWithShadow(tr, label, textX, textY, TEXT_COLOR);
    }

    @Override
    protected void onClick(int button) {
        if (button == 0) {
            checked = !checked;
            if (onToggle != null) onToggle.run();
        }
    }
}
