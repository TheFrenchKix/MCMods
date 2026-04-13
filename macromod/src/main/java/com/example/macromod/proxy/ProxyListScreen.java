package com.example.macromod.proxy;

import com.example.macromod.ui.easyblock.Anim;
import com.example.macromod.ui.easyblock.BasePopupScreen;
import com.example.macromod.ui.easyblock.EasyBlockGui;
import com.example.macromod.ui.easyblock.RoundedRectRenderer;
import com.example.macromod.ui.easyblock.ToggleRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Scrollable proxy list screen. Shows all saved proxies, lets user
 * select the active one, add, edit, or delete entries, and toggle the
 * global proxy enable switch.
 */
public class ProxyListScreen extends BasePopupScreen {

    private static final int R     = 10;
    private static final int HH    = 30;
    private static final int FH    = 40;
    private static final int PH    = 340;
    private static final int ROW_H = 32;

    private int px, py, pw;
    private int scrollOffset = 0;

    // Hover state (recomputed every frame)
    private int     hoverRow  = -1;
    private int     hoverEdit = -1;
    private int     hoverDel  = -1;
    private boolean hoverAdd, hoverClose, hoverToggle;

    private float toggleAnim;

    public ProxyListScreen(Screen parent) {
        super(Text.literal("Proxy List"), parent);
        toggleAnim = ProxyManager.getInstance().isGlobalEnabled() ? 1f : 0f;
    }

    @Override
    protected void init() {
        pw = Math.min(500, Math.max(340, width - 80));
        px = (width - pw) / 2;
        py = (height - PH) / 2;
    }

    // -- Drawing -----------------------------------------------------------

    @Override
    protected void drawScreen(DrawContext ctx, int mx, int my, float delta) {
        boolean ge = ProxyManager.getInstance().isGlobalEnabled();
        toggleAnim = Anim.smooth(toggleAnim, ge ? 1f : 0f, 20f);

        hoverRow = hoverEdit = hoverDel = -1;
        hoverAdd = hoverClose = hoverToggle = false;

        RoundedRectRenderer.draw(ctx, px, py, pw, PH, R, EasyBlockGui.C_BG);
        drawHeader(ctx, mx, my);
        ctx.fill(px, py + PH - FH, px + pw, py + PH - FH + 1, EasyBlockGui.C_DIVIDER);
        drawList(ctx, mx, my);
        drawFooter(ctx, mx, my);
    }

    private void drawHeader(DrawContext ctx, int mx, int my) {
        ctx.fill(px, py, px + pw, py + HH, 0xFF101018);
        ctx.fill(px, py + HH - 1, px + pw, py + HH, EasyBlockGui.C_DIVIDER);
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("\u00A7b\u26A1 \u00A7f\u00A7lProxy List"),
            px + 14, py + (HH - 8) / 2, EasyBlockGui.C_TEXT);

        int tx = px + pw - 14 - ToggleRenderer.TOGGLE_W;
        int ty = py + (HH - ToggleRenderer.TOGGLE_H) / 2;
        ToggleRenderer.draw(ctx, tx, ty, toggleAnim);
        hoverToggle = mx >= tx && mx <= tx + ToggleRenderer.TOGGLE_W
                   && my >= ty && my <= ty + ToggleRenderer.TOGGLE_H;

        boolean ge = ProxyManager.getInstance().isGlobalEnabled();
        String lbl = ge ? "ON" : "OFF";
        ctx.drawTextWithShadow(textRenderer, Text.literal(lbl),
            tx - textRenderer.getWidth(lbl) - 4, py + (HH - 8) / 2,
            ge ? 0xFF55FF55 : 0xFFAAAAAA);
    }

    private void drawList(DrawContext ctx, int mx, int my) {
        List<ProxyConfig> proxies = ProxyManager.getInstance().getProxies();
        int active    = ProxyManager.getInstance().getActiveIndex();
        int listTop   = py + HH + 4;
        int listBot   = py + PH - FH - 4;
        int listH     = listBot - listTop;
        int visCount  = listH / ROW_H;
        int maxScroll = Math.max(0, proxies.size() - visCount);
        scrollOffset  = Math.max(0, Math.min(scrollOffset, maxScroll));

        if (proxies.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("No proxies. Click \u00A7b+ Add Proxy\u00A7r to get started."),
                px + pw / 2, listTop + listH / 2 - 4, EasyBlockGui.C_TEXT3);
            return;
        }

        if (scrollOffset > 0)
            ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("\u25B2"), px + pw / 2, listTop + 1, EasyBlockGui.C_TEXT3);

        for (int i = 0; i < visCount && (i + scrollOffset) < proxies.size(); i++) {
            int idx  = i + scrollOffset;
            ProxyConfig cfg = proxies.get(idx);
            int rowY = listTop + 8 + i * ROW_H;

            boolean isActive  = (idx == active);
            boolean rowHit    = mx >= px + 6 && mx < px + pw - 6
                             && my >= rowY    && my < rowY + ROW_H;

            if (isActive)
                ctx.fill(px + 6, rowY, px + pw - 6, rowY + ROW_H - 2, 0x33AADDFF);
            else if (rowHit)
                ctx.fill(px + 6, rowY, px + pw - 6, rowY + ROW_H - 2, 0x11FFFFFF);

            // Radio button
            int cx = px + 6 + 11, cy = rowY + ROW_H / 2;
            drawRadio(ctx, cx, cy, 5, isActive ? 0xFF4488FF : 0xFF444466, isActive);

            // Delete button (rightmost)
            int delW = 22, delH = 14;
            int delX = px + pw - 6 - delW;
            int delY = rowY + (ROW_H - delH) / 2;
            boolean hDel = mx >= delX && mx < delX + delW && my >= delY && my < delY + delH;
            if (hDel) hoverDel = idx;
            RoundedRectRenderer.draw(ctx, delX, delY, delW, delH, 3, hDel ? 0xFFCC2222 : 0xFF882222);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("\u00D7"),
                delX + delW / 2, delY + 3, EasyBlockGui.C_TEXT);

            // Edit button
            int editW = 36, editH = 14;
            int editX = delX - editW - 4;
            int editY = delY;
            boolean hEdit = mx >= editX && mx < editX + editW && my >= editY && my < editY + editH;
            if (hEdit) hoverEdit = idx;
            RoundedRectRenderer.draw(ctx, editX, editY, editW, editH, 3,
                hEdit ? 0xFF3A6AAA : EasyBlockGui.C_ACCENT);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Edit"),
                editX + editW / 2, editY + 3, EasyBlockGui.C_TEXT);

            // Row click = select (only if not over Edit/Del)
            if (rowHit && !hEdit && !hDel) hoverRow = idx;

            // Label
            int labelX   = cx + 5 + 6;
            int maxLabelW = editX - labelX - 6;
            boolean hasName = cfg.getName() != null && !cfg.getName().isEmpty();
            String primary = hasName ? cfg.getName() : proxyUrl(cfg);
            primary = truncate(primary, maxLabelW);
            int labelColor = isActive ? 0xFFDDEEFF : EasyBlockGui.C_TEXT;
            ctx.drawTextWithShadow(textRenderer, Text.literal(primary),
                labelX, cy - (hasName ? 7 : 4), labelColor);

            if (hasName && !cfg.getHost().isEmpty()) {
                String sub = truncate(proxyUrl(cfg), maxLabelW);
                ctx.drawTextWithShadow(textRenderer, Text.literal(sub),
                    labelX, cy + 2, EasyBlockGui.C_TEXT3);
            }
        }

        if (scrollOffset < maxScroll)
            ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("\u25BC"), px + pw / 2, listBot - 6, EasyBlockGui.C_TEXT3);
    }

    private void drawRadio(DrawContext ctx, int cx, int cy, int r, int color, boolean filled) {
        ctx.fill(cx - r, cy - r + 1, cx + r, cy + r - 1, color);
        ctx.fill(cx - r + 1, cy - r, cx + r - 1, cy + r, color);
        if (!filled) {
            ctx.fill(cx - r + 2, cy - r + 3, cx + r - 2, cy + r - 3, EasyBlockGui.C_BG);
            ctx.fill(cx - r + 3, cy - r + 2, cx + r - 3, cy + r - 2, EasyBlockGui.C_BG);
        } else {
            ctx.fill(cx - r + 2, cy - r + 3, cx + r - 2, cy + r - 3, 0xFF99CCFF);
            ctx.fill(cx - r + 3, cy - r + 2, cx + r - 3, cy + r - 2, 0xFF99CCFF);
        }
    }

    private void drawFooter(DrawContext ctx, int mx, int my) {
        int btnH   = 22, btnW = 110;
        int btnY   = py + PH - FH + (FH - btnH) / 2;
        int gap    = 8;
        int startX = px + (pw - (btnW * 2 + gap)) / 2;

        hoverAdd   = drawBtn(ctx, startX,          btnY, btnW, btnH, "\u271A Add Proxy",
            mx, my, EasyBlockGui.C_ACCENT);
        hoverClose = drawBtn(ctx, startX + btnW + gap, btnY, btnW, btnH, "Close",
            mx, my, 0xFF882222);
    }

    private boolean drawBtn(DrawContext ctx, int x, int y, int w, int h,
                            String lbl, int mx, int my, int base) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        RoundedRectRenderer.draw(ctx, x, y, w, h, 5, hover ? brighten(base) : base);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(lbl),
            x + w / 2, y + (h - 8) / 2, EasyBlockGui.C_TEXT);
        return hover;
    }

    private static int brighten(int c) {
        return (c & 0xFF000000)
            | (Math.min(255, ((c >> 16) & 0xFF) + 30) << 16)
            | (Math.min(255, ((c >>  8) & 0xFF) + 30) <<  8)
            |  Math.min(255, ( c        & 0xFF) + 30);
    }

    // -- Input -------------------------------------------------------------

    @Override
    public boolean mouseClicked(Click click, boolean handled) {
        if (click.button() == 0) {
            if (hoverToggle) {
                ProxyManager pm = ProxyManager.getInstance();
                pm.setGlobalEnabled(!pm.isGlobalEnabled());
                pm.save();
                return true;
            }
            if (hoverEdit >= 0) {
                if (client != null) client.setScreen(new ProxyScreen(this, hoverEdit));
                return true;
            }
            if (hoverDel >= 0) {
                ProxyManager.getInstance().removeProxy(hoverDel);
                ProxyManager.getInstance().save();
                return true;
            }
            if (hoverRow >= 0) {
                ProxyManager pm = ProxyManager.getInstance();
                pm.setActiveIndex(hoverRow == pm.getActiveIndex() ? -1 : hoverRow);
                pm.save();
                return true;
            }
            if (hoverAdd) {
                if (client != null) client.setScreen(new ProxyScreen(this, -1));
                return true;
            }
            if (hoverClose) {
                animClose();
                return true;
            }
        }
        return super.mouseClicked(click, handled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        scrollOffset -= (int) Math.signum(vAmount);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput key) {
        if (key.key() == 256) { animClose(); return true; }
        return super.keyPressed(key);
    }

    // -- Helpers -----------------------------------------------------------

    private String proxyUrl(ProxyConfig cfg) {
        if (cfg.getHost() == null || cfg.getHost().isEmpty()) return "(empty)";
        return cfg.getProtocol() + "://" + cfg.getHost() + ":" + cfg.getPort();
    }

    private String truncate(String s, int maxW) {
        if (textRenderer == null || textRenderer.getWidth(s) <= maxW) return s;
        while (s.length() > 0 && textRenderer.getWidth(s + "...") > maxW)
            s = s.substring(0, s.length() - 1);
        return s + "...";
    }
}