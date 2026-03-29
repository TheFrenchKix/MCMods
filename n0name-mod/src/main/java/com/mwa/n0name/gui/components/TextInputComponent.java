package com.mwa.n0name.gui.components;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.function.Consumer;

/**
 * Text input field with cursor blink, placeholder, and focus accent border.
 */
public class TextInputComponent extends AnimatedComponent {

    private String value;
    private String placeholder;
    private int maxLength;
    private boolean focused = false;
    private int cursorPos;
    private long lastBlinkTime;
    private boolean cursorVisible = true;
    private Consumer<String> onChanged;
    private int accentColor;

    private static final int BG_COLOR = 0xFF1A1A30;
    private static final int BG_FOCUSED = 0xFF202040;
    private static final int BORDER_COLOR = 0xFF333350;
    private static final int TEXT_COLOR = 0xFFE0E0E8;
    private static final int PLACEHOLDER_COLOR = 0xFF555566;
    private static final int CURSOR_COLOR = 0xFFE0E0E8;
    private static final long BLINK_INTERVAL = 530;

    public TextInputComponent(int x, int y, int width, int height, String placeholder, int maxLength, int accentColor) {
        super(x, y, width, height);
        this.value = "";
        this.placeholder = placeholder;
        this.maxLength = maxLength;
        this.accentColor = accentColor;
        this.cursorPos = 0;
        this.lastBlinkTime = System.currentTimeMillis();
    }

    public String getValue() { return value; }
    public void setValue(String value) {
        this.value = value != null ? value : "";
        this.cursorPos = Math.min(cursorPos, this.value.length());
    }
    public boolean isFocused() { return focused; }
    public void setFocused(boolean focused) {
        this.focused = focused;
        if (focused) {
            cursorPos = value.length();
            lastBlinkTime = System.currentTimeMillis();
            cursorVisible = true;
        }
    }
    public void setOnChanged(Consumer<String> onChanged) { this.onChanged = onChanged; }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        // Background
        int bg = focused ? BG_FOCUSED : BG_COLOR;
        drawRoundedRect(ctx, x, y, width, height, bg);

        // Border: accent on focus, normal otherwise
        int borderColor = focused ? accentColor : BORDER_COLOR;
        // Bottom border only for focus accent
        ctx.fill(x + 1, y + height - 2, x + width - 1, y + height, borderColor);
        // Side borders (subtle)
        ctx.fill(x, y, x + width, y + 1, BORDER_COLOR);
        ctx.fill(x, y, x + 1, y + height, BORDER_COLOR);
        ctx.fill(x + width - 1, y, x + width, y + height, BORDER_COLOR);

        // Text or placeholder
        int textX = x + 6;
        int textY = y + (height - 8) / 2;
        if (value.isEmpty() && !focused) {
            ctx.drawTextWithShadow(tr, placeholder, textX, textY, PLACEHOLDER_COLOR);
        } else {
            // Clip text to fit
            String display = value;
            int maxW = width - 14;
            while (tr.getWidth(display) > maxW && display.length() > 0) {
                display = display.substring(1);
            }
            ctx.drawTextWithShadow(tr, display, textX, textY, TEXT_COLOR);

            // Cursor
            if (focused) {
                long now = System.currentTimeMillis();
                if (now - lastBlinkTime > BLINK_INTERVAL) {
                    cursorVisible = !cursorVisible;
                    lastBlinkTime = now;
                }
                if (cursorVisible) {
                    int cursorX = textX + tr.getWidth(value.substring(0, Math.min(cursorPos, value.length())));
                    ctx.fill(cursorX, textY - 1, cursorX + 1, textY + 9, CURSOR_COLOR);
                }
            }
        }
    }

    @Override
    protected void onClick(int button) {
        if (button == 0) {
            setFocused(true);
        }
    }

    /**
     * Handle a character typed while focused.
     */
    public boolean charTyped(char chr) {
        if (!focused) return false;
        if (value.length() >= maxLength) return false;
        if (chr < 32) return false; // control characters
        value = value.substring(0, cursorPos) + chr + value.substring(cursorPos);
        cursorPos++;
        lastBlinkTime = System.currentTimeMillis();
        cursorVisible = true;
        if (onChanged != null) onChanged.accept(value);
        return true;
    }

    /**
     * Handle special keys (backspace, delete, arrows).
     */
    public boolean keyPressed(int keyCode) {
        if (!focused) return false;
        switch (keyCode) {
            case 259 -> { // BACKSPACE
                if (cursorPos > 0) {
                    value = value.substring(0, cursorPos - 1) + value.substring(cursorPos);
                    cursorPos--;
                    if (onChanged != null) onChanged.accept(value);
                }
                return true;
            }
            case 261 -> { // DELETE
                if (cursorPos < value.length()) {
                    value = value.substring(0, cursorPos) + value.substring(cursorPos + 1);
                    if (onChanged != null) onChanged.accept(value);
                }
                return true;
            }
            case 263 -> { // LEFT
                cursorPos = Math.max(0, cursorPos - 1);
                lastBlinkTime = System.currentTimeMillis();
                cursorVisible = true;
                return true;
            }
            case 262 -> { // RIGHT
                cursorPos = Math.min(value.length(), cursorPos + 1);
                lastBlinkTime = System.currentTimeMillis();
                cursorVisible = true;
                return true;
            }
            case 268 -> { // HOME
                cursorPos = 0;
                return true;
            }
            case 269 -> { // END
                cursorPos = value.length();
                return true;
            }
        }
        return false;
    }

    /**
     * Unfocus when clicking elsewhere.
     */
    public void clickOutside(double mouseX, double mouseY) {
        if (focused && !isHovered((int) mouseX, (int) mouseY)) {
            setFocused(false);
        }
    }
}
