package com.mwa.n0name.gui.components;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Modal overlay component.
 * Renders a centered panel over a darkened backdrop.
 * Supports fade+scale open/close animation.
 */
public class ModalComponent extends AnimatedComponent {

    private String title;
    private final List<AnimatedComponent> children = new ArrayList<>();
    private Runnable onClose;
    private boolean open = false;
    private float openProgress = 0f; // 0 = closed, 1 = fully open

    private static final int BACKDROP_COLOR = 0xAA000000;
    private static final int BG_COLOR = 0xF0181830;
    private static final int HEADER_COLOR = 0xFF1A1A38;
    private static final int BORDER_COLOR = 0xFF333350;
    private static final int TITLE_COLOR = 0xFFE0E0E8;
    private static final int CLOSE_COLOR = 0xFF707088;
    private static final float OPEN_SPEED = 0.16f;

    private int screenWidth, screenHeight;

    public ModalComponent(int modalWidth, int modalHeight, String title, Runnable onClose) {
        super(0, 0, modalWidth, modalHeight); // position is calculated dynamically
        this.title = title;
        this.onClose = onClose;
    }

    public void setScreenSize(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.x = (screenWidth - width) / 2;
        this.y = (screenHeight - height) / 2;
    }

    public void open() {
        open = true;
        visible = true;
    }

    public void close() {
        open = false;
    }

    public boolean isOpen() { return open || openProgress > 0.01f; }

    public void addChild(AnimatedComponent child) {
        children.add(child);
    }

    public void clearChildren() { children.clear(); }

    public List<AnimatedComponent> getChildren() { return children; }

    public void setTitle(String title) { this.title = title; }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Animate open/close
        float target = open ? 1f : 0f;
        if (Math.abs(openProgress - target) > 0.01f) {
            openProgress += (target - openProgress) * OPEN_SPEED;
        } else {
            openProgress = target;
            if (!open) {
                visible = false;
                return;
            }
        }

        if (openProgress < 0.01f) return;

        // Backdrop
        int backdropAlpha = (int) (0xAA * openProgress);
        ctx.fill(0, 0, screenWidth, screenHeight, (backdropAlpha << 24));

        // Center position
        x = (screenWidth - width) / 2;
        y = (screenHeight - height) / 2;

        // Scale animation (0.9 -> 1.0)
        float scale = 0.9f + 0.1f * openProgress;
        int sw = (int) (width * scale);
        int sh = (int) (height * scale);
        int sx = (screenWidth - sw) / 2;
        int sy = (screenHeight - sh) / 2;

        // Background
        drawRoundedRect(ctx, sx, sy, sw, sh, BG_COLOR);

        // Border
        ctx.fill(sx, sy, sx + sw, sy + 1, BORDER_COLOR);
        ctx.fill(sx, sy + sh - 1, sx + sw, sy + sh, BORDER_COLOR);
        ctx.fill(sx, sy, sx + 1, sy + sh, BORDER_COLOR);
        ctx.fill(sx + sw - 1, sy, sx + sw, sy + sh, BORDER_COLOR);

        // Header
        ctx.fill(sx, sy, sx + sw, sy + 26, HEADER_COLOR);
        ctx.fill(sx, sy + 25, sx + sw, sy + 26, BORDER_COLOR);

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        if (title != null) {
            ctx.drawTextWithShadow(tr, title, sx + 10, sy + 8, TITLE_COLOR);
        }

        // Close button
        ctx.drawTextWithShadow(tr, "\u00D7", sx + sw - 16, sy + 8, CLOSE_COLOR);

        // Render children
        for (AnimatedComponent child : children) {
            child.render(ctx, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isOpen()) return false;

        // Close button click
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int sx = (screenWidth - width) / 2;
        int sy = (screenHeight - height) / 2;
        if (mouseX >= sx + width - 20 && mouseX < sx + width && mouseY >= sy && mouseY < sy + 26) {
            close();
            if (onClose != null) onClose.run();
            return true;
        }

        // Backdrop click -> close
        if (mouseX < sx || mouseX > sx + width || mouseY < sy || mouseY > sy + height) {
            close();
            if (onClose != null) onClose.run();
            return true;
        }

        // Children
        for (AnimatedComponent child : children) {
            if (child.mouseClicked(mouseX, mouseY, button)) return true;
        }

        return true; // consume click on modal body
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (AnimatedComponent child : children) {
            child.mouseReleased(mouseX, mouseY, button);
        }
        return isOpen();
    }
}
