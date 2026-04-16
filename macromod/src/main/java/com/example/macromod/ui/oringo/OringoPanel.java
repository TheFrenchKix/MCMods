package com.example.macromod.ui.oringo;

/**
 * Draggable panel primitive inspired by Oringo's ClickGUI panel model.
 */
public class OringoPanel {

    public final String id;
    public final String title;
    public int x;
    public int y;
    public int width;
    public int headerHeight;
    public boolean expanded = true;

    private boolean dragging;
    private int dragOffsetX;
    private int dragOffsetY;

    public OringoPanel(String id, String title, int x, int y, int width, int headerHeight) {
        this.id = id;
        this.title = title;
        this.x = x;
        this.y = y;
        this.width = width;
        this.headerHeight = headerHeight;
    }

    public boolean isOverHeader(int mx, int my) {
        return mx >= x && mx < x + width && my >= y && my < y + headerHeight;
    }

    public void startDrag(int mx, int my) {
        dragging = true;
        dragOffsetX = mx - x;
        dragOffsetY = my - y;
    }

    public void stopDrag() {
        dragging = false;
    }

    public boolean isDragging() {
        return dragging;
    }

    public void updateDrag(int mx, int my) {
        if (!dragging) return;
        x = mx - dragOffsetX;
        y = my - dragOffsetY;
    }
}
