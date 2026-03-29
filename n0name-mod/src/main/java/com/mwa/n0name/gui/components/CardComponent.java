package com.mwa.n0name.gui.components;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Macro card component: displays macro name, type badge, step count,
 * action buttons, and repeat toggle.
 */
public class CardComponent extends AnimatedComponent {

    private String title;
    private String badge;
    private int badgeColor;
    private String subtitle; // e.g. "12 steps"
    private final List<ButtonComponent> actionButtons = new ArrayList<>();
    private ToggleComponent repeatToggle;

    private static final int BG_COLOR = 0xF01E1E32;
    private static final int BORDER_COLOR = 0xFF333350;
    private static final int TITLE_COLOR = 0xFFE0E0E8;
    private static final int SUBTITLE_COLOR = 0xFF707088;
    private static final int BADGE_TEXT_COLOR = 0xFFFFFFFF;

    public CardComponent(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public void setTitle(String title) { this.title = title; }
    public void setBadge(String badge, int badgeColor) {
        this.badge = badge;
        this.badgeColor = badgeColor;
    }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public String getTitle() { return title; }

    public void addActionButton(ButtonComponent btn) {
        actionButtons.add(btn);
    }

    public void clearActionButtons() { actionButtons.clear(); }

    public void setRepeatToggle(ToggleComponent toggle) {
        this.repeatToggle = toggle;
    }

    public ToggleComponent getRepeatToggle() { return repeatToggle; }

    /**
     * Re-layout internal components based on current card position.
     */
    public void layout() {
        // Action buttons: bottom row, evenly spaced
        int btnY = y + height - 26;
        int btnCount = actionButtons.size();
        if (btnCount > 0) {
            int totalBtnW = btnCount * 36 + (btnCount - 1) * 4;
            int btnStartX = x + 8;
            for (int i = 0; i < btnCount; i++) {
                ButtonComponent btn = actionButtons.get(i);
                btn.setPosition(btnStartX + i * 40, btnY);
                btn.setSize(36, 20);
            }
        }

        // Repeat toggle: right side of button row
        if (repeatToggle != null) {
            repeatToggle.setPosition(x + width - 80, btnY + 2);
        }
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        // Scale effect
        float scale = 1f;
        if (pressProgress > 0.01f) {
            scale = 1f - 0.02f * pressProgress;
        } else if (hoverProgress > 0.01f) {
            scale = 1f + 0.015f * hoverProgress;
        }

        int cx = x + width / 2;
        int cy = y + height / 2;
        int sw = (int) (width * scale);
        int sh = (int) (height * scale);
        int sx = cx - sw / 2;
        int sy = cy - sh / 2;

        // Background
        int bg = hoverProgress > 0.01f ? brighten(BG_COLOR, 0.04f * hoverProgress) : BG_COLOR;
        drawRoundedRect(ctx, sx, sy, sw, sh, bg);

        // Border
        ctx.fill(sx, sy, sx + sw, sy + 1, BORDER_COLOR);
        ctx.fill(sx, sy + sh - 1, sx + sw, sy + sh, BORDER_COLOR);
        ctx.fill(sx, sy, sx + 1, sy + sh, BORDER_COLOR);
        ctx.fill(sx + sw - 1, sy, sx + sw, sy + sh, BORDER_COLOR);

        // Title
        if (title != null) {
            ctx.drawTextWithShadow(tr, title, sx + 10, sy + 8, TITLE_COLOR);
        }

        // Type badge
        if (badge != null) {
            int bw = tr.getWidth(badge) + 8;
            int bx = sx + 10;
            int by = sy + 22;
            drawRoundedRect(ctx, bx, by, bw, 12, withAlpha(badgeColor, 180));
            ctx.drawTextWithShadow(tr, badge, bx + 4, by + 2, BADGE_TEXT_COLOR);
        }

        // Subtitle (step count)
        if (subtitle != null) {
            int subX = badge != null ? sx + 10 + tr.getWidth(badge) + 16 : sx + 10;
            ctx.drawTextWithShadow(tr, subtitle, subX, sy + 24, SUBTITLE_COLOR);
        }

        // Render action buttons
        for (ButtonComponent btn : actionButtons) {
            btn.render(ctx, mouseX, mouseY, delta);
        }

        // Render repeat toggle
        if (repeatToggle != null) {
            repeatToggle.render(ctx, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        // Check action buttons first
        for (ButtonComponent btn : actionButtons) {
            if (btn.mouseClicked(mouseX, mouseY, button)) return true;
        }
        if (repeatToggle != null && repeatToggle.mouseClicked(mouseX, mouseY, button)) return true;

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (ButtonComponent btn : actionButtons) {
            btn.mouseReleased(mouseX, mouseY, button);
        }
        if (repeatToggle != null) repeatToggle.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
