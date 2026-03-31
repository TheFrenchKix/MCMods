package com.example.macromod.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import com.example.macromod.manager.AutoFishingManager;

/**
 * Auto Farm settings screen — example options for common farming automation.
 */
@Environment(EnvType.CLIENT)
public class AutoFarmScreen extends Screen {

    // ── Colours ──────────────────────────────────────────────────────
    private static final int C_BACKDROP  = 0xB8000000;
    private static final int C_PANEL     = 0xFF131320;
    private static final int C_HEADER    = 0xFF0A0A14;
    private static final int C_DIVIDER   = 0xFF1E1E3A;
    private static final int C_GREEN     = 0xFF0A6E32;
    private static final int C_GREEN_H   = 0xFF0CA844;
    private static final int C_RED       = 0xFF6E1010;
    private static final int C_RED_H     = 0xFFAA1818;
    private static final int C_BLUE      = 0xFF1A3E6E;
    private static final int C_BLUE_H    = 0xFF2460A8;
    private static final int C_TEXT      = 0xFFFFFFFF;
    private static final int C_ACCENT    = 0xFF55DDFF;

    // ── Layout ───────────────────────────────────────────────────────
    private static final int HEADER_H = 38;
    private static final int FOOTER_H = 38;
    private static final int BTN_H    = 22;

    // ── Panel bounds ─────────────────────────────────────────────────
    private int panelX, panelY, panelW, panelH;

    // ── Options ──────────────────────────────────────────────────────
    private boolean optAutoFish           = false;
    private boolean optAttackMobs         = false;
    private boolean optAutoSmelt          = false;
    private int     optFarmRadius         = 8;

    // Sub-options for Auto Fish attack
    private boolean optFishAttack         = false;
    private boolean optFishAttackDistance = false;  // false=Close, true=Distance
    private int     optFishAttackSlot     = -1;     // -1 = no item selected

    // ── Hit areas ────────────────────────────────────────────────────
    /** [i][x, y, w] for each toggle chip (0=AutoFish, 1=AttackMobs, 2=AutoSmelt, 3=FishAttack, 4=FishAttackMode) */
    private final int[][] chipBounds       = new int[5][3];
    /** [leftBtnX, rightBtnX, y] for the radius cycler */
    private final int[]   radiusBounds     = new int[3];
    /** [x, y] per hotbar slot; x == -1 means slot is empty/not rendered */
    private final int[][] hotbarSlotBounds = new int[9][2];

    private boolean hoverClose, hoverBack;

    private final Screen parent;

    public AutoFarmScreen(Screen parent) {
        super(Text.literal("Auto Farm"));
        this.parent = parent;
    }

    // ═══════════════════════════════════════════════════════════════
    // Init
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        panelW = Math.min(this.width  - 80, 500);
        panelH = Math.min(this.height - 80, 440);
        panelX = (this.width  - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        // Sync toggle state from manager
        AutoFishingManager mgr = AutoFishingManager.getInstance();
        optAutoFish           = mgr.isEnabled();
        optFishAttack         = mgr.isAttackEnabled();
        optFishAttackDistance = mgr.isAttackModeDistance();
        optFishAttackSlot     = mgr.getAttackHotbarSlot();

        // Reset hotbar bounds (filled each render)
        for (int i = 0; i < 9; i++) hotbarSlotBounds[i][0] = -1;
    }

    // ═══════════════════════════════════════════════════════════════
    // Render
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        // no-op: drawn in render()
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, this.width, this.height, C_BACKDROP);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, C_PANEL);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + HEADER_H, C_HEADER);

        int hDiv = panelY + HEADER_H;
        int fDiv = panelY + panelH - FOOTER_H;
        ctx.fill(panelX, hDiv, panelX + panelW, hDiv + 1, C_DIVIDER);
        ctx.fill(panelX, fDiv, panelX + panelW, fDiv + 1, C_DIVIDER);

        renderHeader(ctx, mx, my);
        renderBody(ctx, mx, my);
        renderFooter(ctx, mx, my);

        super.render(ctx, mx, my, delta);
    }

    private void renderHeader(DrawContext ctx, int mx, int my) {
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("§b🌾 §fAuto Farm"),
            panelX + 10, panelY + 12, C_TEXT);

        int cx = panelX + panelW - 26, cy = panelY + 9;
        hoverClose = mx >= cx && mx < cx + 18 && my >= cy && my < cy + 18;
        ctx.fill(cx, cy, cx + 18, cy + 18, hoverClose ? C_RED_H : C_RED);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("✕"), cx + 9, cy + 5, C_TEXT);
    }

    private void renderBody(DrawContext ctx, int mx, int my) {
        int x  = panelX + 16;
        int dy = panelY + HEADER_H + 12;

        // ── Crop section ────────────────────────────────────────────
        ctx.drawTextWithShadow(textRenderer, Text.literal("Farming"), x, dy, C_ACCENT);
        dy += 14;
        ctx.fill(x, dy, panelX + panelW - 16, dy + 1, C_DIVIDER);
        dy += 8;

        dy = renderChip(ctx, mx, my, x, dy, "Auto Fish", optAutoFish, false, 0) + 6;

        // ── Auto Fish sub-options ────────────────────────────────────
        if (optAutoFish) {
            int sx = x + 16;
            dy = renderChip(ctx, mx, my, sx, dy, "Attack", optFishAttack, false, 3) + 6;

            if (optFishAttack) {
                int sx2 = sx + 16;

                // Mode chip (Close / Distance) — stored in chipBounds[4]
                String modeLabel = "Mode  " + (optFishAttackDistance ? "§bDistance" : "§aClose");
                int    modeW     = textRenderer.getWidth(modeLabel) + 14;
                boolean mhov     = mx >= sx2 && mx < sx2 + modeW && my >= dy && my < dy + 16;
                ctx.fill(sx2, dy, sx2 + modeW, dy + 16, mhov ? 0xFF444466 : 0xFF2A2A44);
                ctx.drawTextWithShadow(textRenderer, Text.literal(modeLabel), sx2 + 7, dy + 4, C_TEXT);
                chipBounds[4][0] = sx2; chipBounds[4][1] = dy; chipBounds[4][2] = modeW;
                dy += 22;

                // Hotbar item selector
                ctx.drawTextWithShadow(textRenderer,
                        Text.literal("§7Attack item:"), sx2, dy, 0xFFAAAAAA);
                dy += 13;
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player != null) {
                    for (int i = 0; i < 9; i++) {
                        ItemStack stack = mc.player.getInventory().getStack(i);
                        if (stack.isEmpty()) { hotbarSlotBounds[i][0] = -1; continue; }
                        int bx   = sx2 + i * 20;
                        boolean sel  = optFishAttackSlot == i;
                        boolean shov = mx >= bx && mx < bx + 18 && my >= dy && my < dy + 18;
                        ctx.fill(bx, dy, bx + 18, dy + 18,
                                sel  ? 0xFF2060A0 : shov ? 0xFF444466 : 0xFF1E1E3A);
                        ctx.drawItem(stack, bx + 1, dy + 1);
                        hotbarSlotBounds[i][0] = bx;
                        hotbarSlotBounds[i][1] = dy;
                    }
                    dy += 22;
                }
            }
        }

        // ── Combat section ──────────────────────────────────────────
        ctx.drawTextWithShadow(textRenderer, Text.literal("§cCombat"), x, dy, C_ACCENT);
        dy += 14;
        ctx.fill(x, dy, panelX + panelW - 16, dy + 1, C_DIVIDER);
        dy += 8;

        dy = renderChip(ctx, mx, my, x, dy, "Attack Mobs Near Farm", optAttackMobs, true, 1) + 10;

        int rightEdge = panelX + panelW - 16;
        String valStr = "§a" + optFarmRadius + " §7blk";
        int valW      = textRenderer.getWidth(valStr);
        int lbx = rightEdge - valW - 30;
        int rbx = rightEdge - 14;
        boolean hovL = mx >= lbx && mx < lbx + 14 && my >= dy && my < dy + 16;
        boolean hovR = mx >= rbx && mx < rbx + 14 && my >= dy && my < dy + 16;
        ctx.fill(lbx, dy, lbx + 14, dy + 14, hovL ? 0xFF444466 : 0xFF2A2A44);
        ctx.fill(rbx, dy, rbx + 14, dy + 14, hovR ? 0xFF444466 : 0xFF2A2A44);
        ctx.drawTextWithShadow(textRenderer, Text.literal("<"), lbx + 3, dy + 3, C_TEXT);
        ctx.drawTextWithShadow(textRenderer, Text.literal(">"), rbx + 3, dy + 3, C_TEXT);
        ctx.drawTextWithShadow(textRenderer, Text.literal(valStr), rbx - valW - 4, dy + 3, C_TEXT);
        radiusBounds[0] = lbx; radiusBounds[1] = rbx; radiusBounds[2] = dy;

        dy += 20;

        // ── Info note ───────────────────────────────────────────────
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("§8Note: these options will apply when a macro runs."),
            x, dy, 0xFF666680);
    }

    private void renderFooter(DrawContext ctx, int mx, int my) {
        int backW  = 110;
        int btnY   = panelY + panelH - FOOTER_H + (FOOTER_H - BTN_H) / 2;
        int backX  = panelX + (panelW - backW) / 2;

        hoverBack = mx >= backX && mx < backX + backW && my >= btnY && my < btnY + BTN_H;
        ctx.fill(backX, btnY, backX + backW, btnY + BTN_H, hoverBack ? C_BLUE_H : C_BLUE);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("← Back"),
            backX + backW / 2, btnY + 7, C_TEXT);
    }

    /** Draws a toggle chip, stores hit area, returns bottom Y. */
    private int renderChip(DrawContext ctx, int mx, int my, int x, int y,
                           String label, boolean on, boolean danger, int idx) {
        String text = label + "  " + (on ? "§aON" : "§cOFF");
        int w   = textRenderer.getWidth(text) + 14;
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + 16;
        int bg;
        if (on)   bg = danger ? (hov ? C_RED_H  : C_RED)   : (hov ? C_GREEN_H : C_GREEN);
        else      bg = hov    ? 0xFF2A2A40 : 0xFF1E1E30;
        ctx.fill(x, y, x + w, y + 16, bg);
        ctx.drawTextWithShadow(textRenderer, Text.literal(text), x + 7, y + 4, C_TEXT);
        chipBounds[idx][0] = x; chipBounds[idx][1] = y; chipBounds[idx][2] = w;
        return y + 16;
    }

    // ═══════════════════════════════════════════════════════════════
    // Input
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int imx = (int) mx, imy = (int) my;

        if (hoverClose || imx < panelX || imx > panelX + panelW
                       || imy < panelY || imy > panelY + panelH) {
            close();
            return true;
        }
        if (hoverBack) { close(); return true; }

        // Toggle chips
        for (int i = 0; i < 5; i++) {
            int cx = chipBounds[i][0], cy = chipBounds[i][1], cw = chipBounds[i][2];
            if (cw > 0 && imx >= cx && imx < cx + cw && imy >= cy && imy < cy + 16) {
                switch (i) {
                    case 0 -> {
                        optAutoFish = !optAutoFish;
                        AutoFishingManager.getInstance().setEnabled(optAutoFish);
                        syncFishAttack();
                    }
                    case 1 -> optAttackMobs = !optAttackMobs;
                    case 2 -> optAutoSmelt  = !optAutoSmelt;
                    case 3 -> { optFishAttack         = !optFishAttack;         syncFishAttack(); }
                    case 4 -> { optFishAttackDistance = !optFishAttackDistance; syncFishAttack(); }
                }
                return true;
            }
        }

        // Hotbar item slot selector
        if (optAutoFish && optFishAttack) {
            for (int i = 0; i < 9; i++) {
                if (hotbarSlotBounds[i][0] < 0) continue;
                int sx = hotbarSlotBounds[i][0], sy = hotbarSlotBounds[i][1];
                if (imx >= sx && imx < sx + 18 && imy >= sy && imy < sy + 18) {
                    optFishAttackSlot = (optFishAttackSlot == i) ? -1 : i;
                    syncFishAttack();
                    return true;
                }
            }
        }

        // Radius cycler
        if (radiusBounds[2] > 0 && imy >= radiusBounds[2] && imy < radiusBounds[2] + 16) {
            if (imx >= radiusBounds[0] && imx < radiusBounds[0] + 14) {
                optFarmRadius = Math.max(1, optFarmRadius - 1);
                return true;
            }
            if (imx >= radiusBounds[1] && imx < radiusBounds[1] + 14) {
                optFarmRadius = Math.min(32, optFarmRadius + 1);
                return true;
            }
        }

        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Pushes the current fish-attack config to the manager. */
    private void syncFishAttack() {
        AutoFishingManager.getInstance().setAttackConfig(
                optFishAttack, optFishAttackDistance, optFishAttackSlot);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
