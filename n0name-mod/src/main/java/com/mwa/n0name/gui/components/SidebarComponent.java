package com.mwa.n0name.gui.components;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Sidebar navigation with selectable items and animated accent bar.
 */
public class SidebarComponent extends AnimatedComponent {

    public record SidebarItem(String icon, String label, int color) {}

    private final List<SidebarItem> items = new ArrayList<>();
    private int selectedIndex = 0;
    private float accentBarY = 0f; // animated Y position of the accent bar
    private Consumer<Integer> onSelect;

    private static final int ITEM_H = 36;
    private static final int BG_COLOR = 0xF01A1A28;
    private static final int HOVER_COLOR = 0xFF222238;
    private static final int SELECTED_COLOR = 0xFF2A2A48;
    private static final int TEXT_COLOR = 0xFFE0E0E8;
    private static final int MUTED_COLOR = 0xFF707088;
    private static final int BORDER_COLOR = 0xFF333350;
    private static final float ACCENT_SPEED = 0.2f;

    public SidebarComponent(int x, int y, int width, int height, Consumer<Integer> onSelect) {
        super(x, y, width, height);
        this.onSelect = onSelect;
    }

    public void addItem(SidebarItem item) {
        items.add(item);
    }

    public void addItem(String icon, String label, int color) {
        items.add(new SidebarItem(icon, label, color));
    }

    public int getSelectedIndex() { return selectedIndex; }
    public void setSelectedIndex(int index) { this.selectedIndex = index; }

    public SidebarItem getSelectedItem() {
        return selectedIndex >= 0 && selectedIndex < items.size() ? items.get(selectedIndex) : null;
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        // Background
        ctx.fill(x, y, x + width, y + height, BG_COLOR);
        // Right border
        ctx.fill(x + width - 1, y, x + width, y + height, BORDER_COLOR);

        // Animate accent bar position
        float targetBarY = y + 8 + selectedIndex * ITEM_H;
        if (Math.abs(accentBarY - targetBarY) > 0.5f) {
            accentBarY += (targetBarY - accentBarY) * ACCENT_SPEED;
        } else {
            accentBarY = targetBarY;
        }

        // Draw items
        for (int i = 0; i < items.size(); i++) {
            SidebarItem item = items.get(i);
            int iy = y + 8 + i * ITEM_H;
            boolean sel = i == selectedIndex;
            boolean hov = mouseX >= x && mouseX < x + width - 1 && mouseY >= iy && mouseY < iy + ITEM_H;

            // Highlight
            if (sel) {
                ctx.fill(x, iy, x + width - 1, iy + ITEM_H, SELECTED_COLOR);
            } else if (hov) {
                ctx.fill(x, iy, x + width - 1, iy + ITEM_H, HOVER_COLOR);
            }

            // Icon
            int iconX = x + 12;
            int textY = iy + (ITEM_H - 8) / 2;
            ctx.drawTextWithShadow(tr, item.icon(), iconX, textY, sel ? item.color() : MUTED_COLOR);

            // Label
            int labelX = iconX + tr.getWidth(item.icon()) + 6;
            ctx.drawTextWithShadow(tr, item.label(), labelX, textY, sel ? item.color() : (hov ? TEXT_COLOR : MUTED_COLOR));
        }

        // Animated accent bar (left edge)
        int barH = ITEM_H;
        ctx.fill(x, (int) accentBarY, x + 3, (int) accentBarY + barH,
                items.isEmpty() ? 0xFFFFFFFF : items.get(selectedIndex).color());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || button != 0) return false;
        if (mouseX < x || mouseX >= x + width - 1) return false;

        for (int i = 0; i < items.size(); i++) {
            int iy = y + 8 + i * ITEM_H;
            if (mouseY >= iy && mouseY < iy + ITEM_H) {
                if (i != selectedIndex) {
                    selectedIndex = i;
                    if (onSelect != null) onSelect.accept(i);
                }
                return true;
            }
        }
        return false;
    }
}
