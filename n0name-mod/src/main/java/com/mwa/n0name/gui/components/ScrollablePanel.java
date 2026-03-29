package com.mwa.n0name.gui.components;

import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Scrollable container with smooth scroll interpolation and scissor clipping.
 */
public class ScrollablePanel extends AnimatedComponent {

    private final List<AnimatedComponent> children = new ArrayList<>();
    private float scrollOffset = 0f;     // actual (smoothed)
    private float scrollTarget = 0f;     // target
    private int contentHeight = 0;       // computed from children

    private static final float SCROLL_SPEED = 0.18f;
    private static final int SCROLL_AMOUNT = 24;
    private static final int SCROLLBAR_W = 4;
    private static final int SCROLLBAR_COLOR = 0x80707088;
    private static final int SCROLLBAR_HOVER = 0xC0707088;

    public ScrollablePanel(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public void addChild(AnimatedComponent child) {
        children.add(child);
    }

    public void removeChild(AnimatedComponent child) {
        children.remove(child);
    }

    public void clearChildren() {
        children.clear();
        scrollTarget = 0;
        scrollOffset = 0;
    }

    public List<AnimatedComponent> getChildren() { return children; }

    public void setContentHeight(int contentHeight) {
        this.contentHeight = contentHeight;
    }

    public void resetScroll() {
        scrollTarget = 0;
        scrollOffset = 0;
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Smooth scroll
        if (Math.abs(scrollOffset - scrollTarget) > 0.5f) {
            scrollOffset += (scrollTarget - scrollTarget > 0 ? 1 : -1) * Math.abs(scrollTarget - scrollOffset) * SCROLL_SPEED;
            scrollOffset += (scrollTarget - scrollOffset) * SCROLL_SPEED;
        } else {
            scrollOffset = scrollTarget;
        }

        // Clamp
        int maxScroll = Math.max(0, contentHeight - height);
        scrollTarget = Math.max(0, Math.min(scrollTarget, maxScroll));
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        // Scissor clip
        ctx.enableScissor(x, y, x + width, y + height);

        // Render children offset by scroll
        int yOff = (int) -scrollOffset;
        for (AnimatedComponent child : children) {
            int origY = child.getY();
            // Only render if visible in viewport
            if (origY + child.getHeight() + yOff > y && origY + yOff < y + height) {
                child.render(ctx, mouseX, mouseY, delta);
            }
        }

        ctx.disableScissor();

        // Scrollbar
        if (contentHeight > height) {
            int barH = Math.max(20, height * height / contentHeight);
            float pct = scrollOffset / Math.max(1, contentHeight - height);
            int barY = y + (int) (pct * (height - barH));
            int barX = x + width - SCROLLBAR_W - 2;
            boolean sbHovered = mouseX >= barX && mouseX < barX + SCROLLBAR_W + 2
                    && mouseY >= y && mouseY < y + height;
            ctx.fill(barX, barY, barX + SCROLLBAR_W, barY + barH,
                    sbHovered ? SCROLLBAR_HOVER : SCROLLBAR_COLOR);
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        if (!visible) return false;
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) return false;
        scrollTarget -= (float) (verticalAmount * SCROLL_AMOUNT);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) return false;
        for (AnimatedComponent child : children) {
            if (child.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (AnimatedComponent child : children) {
            child.mouseReleased(mouseX, mouseY, button);
        }
        return false;
    }
}
