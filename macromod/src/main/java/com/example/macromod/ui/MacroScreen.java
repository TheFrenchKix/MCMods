package com.example.macromod.ui;

import com.example.macromod.MacroModClient;
import com.example.macromod.model.Macro;
import com.example.macromod.model.MacroConfig;
import com.example.macromod.model.MacroStep;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Lunar-Client-inspired macro management screen.
 *
 * <p>Layout:
 * <pre>
 *  ┌─ panel ─────────────────────────────────────────────────────────┐
 *  │ HEADER: title + running indicator + [✕]                         │
 *  ├─ sidebar ──────┬─ content ───────────────────────────────────── │
 *  │  macro card    │  name / description / config chips / steps      │
 *  │  macro card    │                                                  │
 *  │    ...         │  [▶ Run] [✎ Edit] [✗ Delete] [⊕ Copy]          │
 *  │  [+ New Macro] │                                                  │
 *  └────────────────┴─────────────────────────────────────────────── ┘
 * </pre>
 * All drawing uses {@code DrawContext} primitives — no MC widget classes.
 * Mouse interaction is handled manually.
 * </p>
 */
@Environment(EnvType.CLIENT)
public class MacroScreen extends Screen {

    // ── Colours ──────────────────────────────────────────────────────
    private static final int C_BACKDROP    = 0xB8000000;
    private static final int C_PANEL       = 0xFF131320;
    private static final int C_SIDEBAR     = 0xFF0D0D1A;
    private static final int C_HEADER      = 0xFF0A0A14;
    private static final int C_DIVIDER     = 0xFF1E1E3A;
    private static final int C_CARD        = 0xFF1A1A2C;
    private static final int C_CARD_HOVER  = 0xFF22223A;
    private static final int C_CARD_SEL    = 0xFF183050;
    private static final int C_GREEN       = 0xFF0A6E32;
    private static final int C_GREEN_H     = 0xFF0CA844;
    private static final int C_RED         = 0xFF6E1010;
    private static final int C_RED_H       = 0xFFAA1818;
    private static final int C_BLUE        = 0xFF1A3E6E;
    private static final int C_BLUE_H      = 0xFF2460A8;
    private static final int C_RUNNING     = 0xFF00FF88;
    private static final int C_TEXT        = 0xFFFFFFFF;
    private static final int C_TEXT_SEC    = 0xFFAAAAAA;
    private static final int C_TEXT_DIM    = 0xFF555570;
    private static final int C_ACCENT_TEXT = 0xFF55DDFF;

    // ── Layout ───────────────────────────────────────────────────────
    private static final int SIDEBAR_W = 195;
    private static final int HEADER_H  = 38;
    private static final int FOOTER_H  = 38;
    private static final int CARD_H    = 50;
    private static final int CARD_GAP  = 3;
    private static final int CARD_PX   = 6;  // horizontal card padding
    private static final int ACT_W     = 70;
    private static final int ACT_H     = 22;
    private static final int NEW_W     = 140;
    private static final int NEW_H     = 22;

    // ── Panel bounds (computed in init) ──────────────────────────────
    private int panelX, panelY, panelW, panelH;

    // ── State ────────────────────────────────────────────────────────
    private Macro selectedMacro;
    private int   scrollY    = 0;
    private int   maxScrollY = 0;

    // Hover flags refreshed each render frame
    private boolean hoverClose, hoverNew, hoverAutoFarm;
    private boolean hoverRun, hoverEdit, hoverDelete, hoverDup;

    // Auto Farm button bounds
    private int autoFarmX, autoFarmY, autoFarmW, autoFarmH;

    // Action button x positions (right panel footer row)
    private final int[] actX = new int[4];
    private int actY;

    // Config chip hit areas for the selected macro detail panel [idx][x, y, w]
    private final int[][] chipDetailBounds = new int[5][3];

    // ─────────────────────────────────────────────────────────────────
    public MacroScreen() {
        super(Text.literal("MacroMod"));
    }

    @Override
    protected void init() {
        panelW = Math.min(this.width  - 40, 920);
        panelH = Math.min(this.height - 40, 560);
        panelX = (this.width  - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        recalcScroll();
    }

    // ═══════════════════════════════════════════════════════════════
    // Rendering
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, this.width, this.height, C_BACKDROP);

        // Panel shell
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, C_PANEL);
        // Sidebar bg
        ctx.fill(panelX, panelY, panelX + SIDEBAR_W, panelY + panelH, C_SIDEBAR);
        // Header bg
        ctx.fill(panelX, panelY, panelX + panelW, panelY + HEADER_H, C_HEADER);

        // Dividers
        int hDiv = panelY + HEADER_H;
        int vDiv = panelX + SIDEBAR_W;
        int fDiv = panelY + panelH - FOOTER_H;
        ctx.fill(panelX, hDiv,  panelX + panelW,   hDiv + 1,         C_DIVIDER);
        ctx.fill(vDiv,   panelY, vDiv + 1,          panelY + panelH,  C_DIVIDER);
        ctx.fill(panelX, fDiv,  panelX + panelW,   fDiv + 1,         C_DIVIDER);

        renderHeader(ctx, mx, my);
        renderSidebar(ctx, mx, my);
        renderDetail(ctx, mx, my);
    }

    // ── Header ───────────────────────────────────────────────────────

    private void renderHeader(DrawContext ctx, int mx, int my) {
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("§b✦ §fMacroMod §b✦"),
                panelX + 10, panelY + 12, C_ACCENT_TEXT);

        // Running indicator
        if (MacroModClient.getExecutor().isRunning()
                && MacroModClient.getExecutor().getCurrentMacro() != null) {
            String lbl = "▶  " + MacroModClient.getExecutor().getCurrentMacro().getName();
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(lbl),
                    panelX + panelW / 2, panelY + 13, C_RUNNING);
        }

        // Auto Farm button
        autoFarmW = 90; autoFarmH = 20;
        autoFarmX = panelX + panelW - 26 - 6 - autoFarmW;
        autoFarmY = panelY + (HEADER_H - autoFarmH) / 2;
        hoverAutoFarm = mx >= autoFarmX && mx < autoFarmX + autoFarmW
                && my >= autoFarmY && my < autoFarmY + autoFarmH;
        ctx.fill(autoFarmX, autoFarmY, autoFarmX + autoFarmW, autoFarmY + autoFarmH,
                hoverAutoFarm ? 0xFF3A6E3A : 0xFF1F4A1F);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("§a🌾 Auto Farm"),
                autoFarmX + autoFarmW / 2, autoFarmY + 6, C_TEXT);

        // Close [✕]
        int cx = panelX + panelW - 26, cy = panelY + 7;
        hoverClose = mx >= cx && mx < cx + 20 && my >= cy && my < cy + 20;
        ctx.fill(cx, cy, cx + 20, cy + 20, hoverClose ? C_RED_H : C_RED);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("✕"), cx + 10, cy + 6, C_TEXT);
    }

    // ── Sidebar ──────────────────────────────────────────────────────

    private void renderSidebar(DrawContext ctx, int mx, int my) {
        List<Macro> macros = MacroModClient.getManager().getAll();

        int cardX  = panelX + CARD_PX;
        int cardW  = SIDEBAR_W - CARD_PX * 2;
        int listY0 = panelY + HEADER_H + 4;
        int listH  = panelH - HEADER_H - FOOTER_H - 8;

        for (int i = 0; i < macros.size(); i++) {
            Macro m     = macros.get(i);
            int   cy    = listY0 + i * (CARD_H + CARD_GAP) - scrollY;
            int   vis0  = Math.max(cy, listY0);
            int   vis1  = Math.min(cy + CARD_H, listY0 + listH);
            if (vis0 >= vis1) continue;  // entirely clipped

            boolean running = isRunning(m);
            boolean sel     = isSel(m);
            boolean hov     = mx >= cardX && mx < cardX + cardW && my >= vis0 && my < vis1;

            ctx.fill(cardX, vis0, cardX + cardW, vis1,
                    sel ? C_CARD_SEL : (hov ? C_CARD_HOVER : C_CARD));

            if (sel || running) {
                ctx.fill(cardX, vis0, cardX + 3, vis1, running ? C_RUNNING : 0xFF00B4D8);
            }

            if (vis1 - vis0 >= 18) {
                String name = m.getName().length() > 19 ? m.getName().substring(0, 17) + ".." : m.getName();
                ctx.drawTextWithShadow(textRenderer, Text.literal(name), cardX + 8, cy + 8, C_TEXT);
            }
            if (vis1 - vis0 >= 30) {
                String sub = m.getSteps().size() + " steps  ·  " + m.getTotalBlockCount() + " blocks";
                ctx.drawTextWithShadow(textRenderer, Text.literal(sub), cardX + 8, cy + 21, C_TEXT_SEC);
            }
            if (running && vis1 - vis0 >= 42) {
                ctx.drawTextWithShadow(textRenderer, Text.literal("● RUNNING"), cardX + 8, cy + 34, C_RUNNING);
            }
        }

        // New Macro button
        int nbX = panelX + (SIDEBAR_W - NEW_W) / 2;
        int nbY = panelY + panelH - FOOTER_H + (FOOTER_H - NEW_H) / 2;
        hoverNew = mx >= nbX && mx < nbX + NEW_W && my >= nbY && my < nbY + NEW_H;
        ctx.fill(nbX, nbY, nbX + NEW_W, nbY + NEW_H, hoverNew ? C_GREEN_H : C_GREEN);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("+ New Macro"),
                nbX + NEW_W / 2, nbY + 7, C_TEXT);
    }

    // ── Detail panel ─────────────────────────────────────────────────

    private void renderDetail(DrawContext ctx, int mx, int my) {
        int dx  = panelX + SIDEBAR_W + 10;
        int dx2 = panelX + panelW - 10;
        int dw  = dx2 - dx;
        int dy  = panelY + HEADER_H + 10;

        if (selectedMacro == null) {
            String hint = "← Select a macro";
            ctx.drawTextWithShadow(textRenderer, Text.literal(hint),
                    dx + (dw - textRenderer.getWidth(hint)) / 2,
                    panelY + panelH / 2 - 4, C_TEXT_DIM);
            // Reset hover flags to avoid stale clicks
            hoverRun = hoverEdit = hoverDelete = hoverDup = false;
            return;
        }

        Macro m = selectedMacro;

        // Name
        ctx.drawTextWithShadow(textRenderer, Text.literal("§f§l" + m.getName()), dx, dy, C_TEXT);
        dy += 14;

        // Description
        if (m.getDescription() != null && !m.getDescription().isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, Text.literal(m.getDescription()), dx, dy, C_TEXT_SEC);
            dy += 12;
        }

        // Created date
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(m.getCreatedAt()));
        ctx.drawTextWithShadow(textRenderer, Text.literal("Created: §7" + date), dx, dy, C_TEXT_SEC);
        dy += 14;

        // Config chips (one row) — clickable to toggle
        int cx = dx;
        cx = chip(ctx, mx, my, cx, dy, "Loop",         m.getConfig().isLoop(),         false, 0) + 4;
        cx = chip(ctx, mx, my, cx, dy, "SkipMismatch", m.getConfig().isSkipMismatch(), false, 1) + 4;
        cx = chip(ctx, mx, my, cx, dy, "StopOnDanger", m.getConfig().isStopOnDanger(), true,  2) + 4;
        cx = chip(ctx, mx, my, cx, dy, "OnlyGround", m.getConfig().isOnlyGround(), false, 3) + 4;
             chip(ctx, mx, my, cx, dy, "Lock Crosshair", m.getConfig().isLockCrosshair(), false, 4);
        dy += 20;

        // Divider
        ctx.fill(dx, dy, dx2, dy + 1, C_DIVIDER);
        dy += 8;

        // Steps header
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("§bSteps §7(" + m.getSteps().size() + ")"), dx, dy, C_ACCENT_TEXT);
        dy += 14;

        int n = 0;
        for (MacroStep step : m.getSteps()) {
            if (dy > panelY + panelH - FOOTER_H - 8) {
                ctx.drawTextWithShadow(textRenderer, Text.literal("§7..."), dx + 4, dy, C_TEXT_DIM);
                break;
            }
            String lbl  = (++n) + ". " + (step.getLabel().length() > 26
                    ? step.getLabel().substring(0, 24) + ".." : step.getLabel());
            String dest = " §7→ " + step.getDestination().getX() + ","
                    + step.getDestination().getY() + "," + step.getDestination().getZ()
                    + " (" + step.getTargets().size() + " blk)";
            ctx.drawTextWithShadow(textRenderer, Text.literal("§f" + lbl + "§a" + dest), dx + 4, dy, C_TEXT);
            dy += 12;
        }

        // Action buttons row (in footer)
        actY = panelY + panelH - FOOTER_H + (FOOTER_H - ACT_H) / 2;
        int gap = 6;
        actX[0] = dx;
        actX[1] = actX[0] + ACT_W + gap;
        actX[2] = actX[1] + ACT_W + gap;
        actX[3] = actX[2] + ACT_W + gap;

        boolean running = isRunning(m);

        hoverRun = hitBtn(mx, my, actX[0]);
        ctx.fill(actX[0], actY, actX[0] + ACT_W, actY + ACT_H,
                running ? (hoverRun ? C_RED_H : C_RED) : (hoverRun ? C_GREEN_H : C_GREEN));
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal(running ? "■ Stop" : "▶ Run"), actX[0] + ACT_W / 2, actY + 7, C_TEXT);

        hoverEdit = hitBtn(mx, my, actX[1]);
        ctx.fill(actX[1], actY, actX[1] + ACT_W, actY + ACT_H, hoverEdit ? C_BLUE_H : C_BLUE);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("✎ Edit"),
                actX[1] + ACT_W / 2, actY + 7, C_TEXT);

        hoverDelete = hitBtn(mx, my, actX[2]);
        ctx.fill(actX[2], actY, actX[2] + ACT_W, actY + ACT_H, hoverDelete ? C_RED_H : C_RED);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("✗ Delete"),
                actX[2] + ACT_W / 2, actY + 7, C_TEXT);

        hoverDup = hitBtn(mx, my, actX[3]);
        ctx.fill(actX[3], actY, actX[3] + ACT_W, actY + ACT_H, hoverDup ? C_BLUE_H : C_BLUE);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("⊕ Copy"),
                actX[3] + ACT_W / 2, actY + 7, C_TEXT);
    }

    // ── Chip helper ──────────────────────────────────────────────────

    private int chip(DrawContext ctx, int mx, int my, int x, int y, String label, boolean on,
                     boolean danger, int idx) {
        String text = label + " " + (on ? "§aON" : "§cOFF");
        int w   = textRenderer.getWidth(text) + 8;
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + 14;
        int bg = on ? (danger ? (hov ? C_RED_H : C_RED) : (hov ? C_GREEN_H : C_GREEN))
                    : (hov ? 0xFF2A2A40 : 0xFF222230);
        ctx.fill(x, y, x + w, y + 14, bg);
        ctx.drawTextWithShadow(textRenderer, Text.literal(text), x + 4, y + 3, C_TEXT);
        chipDetailBounds[idx][0] = x;
        chipDetailBounds[idx][1] = y;
        chipDetailBounds[idx][2] = w;
        return x + w;
    }

    // ── Hit-test helpers ─────────────────────────────────────────────

    private boolean hitBtn(int mx, int my, int bx) {
        return mx >= bx && mx < bx + ACT_W && my >= actY && my < actY + ACT_H;
    }

    private boolean isRunning(Macro m) {
        return MacroModClient.getExecutor().isRunning()
                && MacroModClient.getExecutor().getCurrentMacro() != null
                && MacroModClient.getExecutor().getCurrentMacro().getId().equals(m.getId());
    }

    private boolean isSel(Macro m) {
        return selectedMacro != null && selectedMacro.getId().equals(m.getId());
    }

    // ═══════════════════════════════════════════════════════════════
    // Input handling
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int imx = (int) mx, imy = (int) my;
        List<Macro> macros = MacroModClient.getManager().getAll();

        // Outside panel → close
        if (imx < panelX || imx > panelX + panelW || imy < panelY || imy > panelY + panelH) {
            close();
            return true;
        }

        if (hoverClose) { close(); return true; }
        if (hoverAutoFarm && client != null) {
            client.setScreen(new AutoFarmScreen(this));
            return true;
        }

        // Config chip toggles on the detail panel
        if (selectedMacro != null) {
            MacroConfig cfg = selectedMacro.getConfig();
            for (int i = 0; i < 5; i++) {
                int cx = chipDetailBounds[i][0], cy = chipDetailBounds[i][1], cw = chipDetailBounds[i][2];
                if (cw > 0 && imx >= cx && imx < cx + cw && imy >= cy && imy < cy + 14) {
                    switch (i) {
                        case 0 -> cfg.setLoop(!cfg.isLoop());
                        case 1 -> cfg.setSkipMismatch(!cfg.isSkipMismatch());
                        case 2 -> cfg.setStopOnDanger(!cfg.isStopOnDanger());
                        case 3 -> cfg.setOnlyGround(!cfg.isOnlyGround());
                        case 4 -> cfg.setLockCrosshair(!cfg.isLockCrosshair());
                    }
                    MacroModClient.getManager().save(selectedMacro);
                    return true;
                }
            }
        }

        // Sidebar cards
        int cardX  = panelX + CARD_PX;
        int cardW  = SIDEBAR_W - CARD_PX * 2;
        int listY0 = panelY + HEADER_H + 4;
        int listH  = panelH - HEADER_H - FOOTER_H - 8;

        if (imx >= cardX && imx < cardX + cardW) {
            for (int i = 0; i < macros.size(); i++) {
                int cy   = listY0 + i * (CARD_H + CARD_GAP) - scrollY;
                int vis0 = Math.max(cy, listY0);
                int vis1 = Math.min(cy + CARD_H, listY0 + listH);
                if (imy >= vis0 && imy < vis1) {
                    selectedMacro = macros.get(i);
                    return true;
                }
            }
        }

        // New Macro button
        if (hoverNew) {
            String name = "Macro " + (macros.size() + 1);
            selectedMacro = MacroModClient.getManager().create(name);
            recalcScroll();
            return true;
        }

        // Action buttons
        if (selectedMacro != null) {
            if (hoverRun) {
                if (isRunning(selectedMacro)) {
                    MacroModClient.getExecutor().stop();
                } else {
                    MacroModClient.getExecutor().start(selectedMacro.getId(), null);
                    close();
                }
                return true;
            }
            if (hoverEdit && client != null) {
                client.setScreen(new MacroEditScreen(selectedMacro, this));
                return true;
            }
            if (hoverDelete) {
                MacroModClient.getManager().delete(selectedMacro.getId());
                selectedMacro = null;
                recalcScroll();
                return true;
            }
            if (hoverDup) {
                Macro dup = MacroModClient.getManager().duplicate(selectedMacro.getId());
                if (dup != null) selectedMacro = dup;
                recalcScroll();
                return true;
            }
        }

        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        if (mx >= panelX && mx < panelX + SIDEBAR_W
                && my >= panelY + HEADER_H && my < panelY + panelH - FOOTER_H) {
            scrollY = Math.max(0, Math.min(maxScrollY, scrollY - (int) (vAmt * 14)));
            return true;
        }
        return super.mouseScrolled(mx, my, hAmt, vAmt);
    }

    @Override
    public boolean shouldPause() { return false; }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    private void recalcScroll() {
        int count   = MacroModClient.getManager().getAll().size();
        int listH   = panelH - HEADER_H - FOOTER_H - 8;
        maxScrollY  = Math.max(0, count * (CARD_H + CARD_GAP) - listH);
        scrollY     = Math.min(scrollY, maxScrollY);
    }
}
