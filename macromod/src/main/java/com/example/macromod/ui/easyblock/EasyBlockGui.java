package com.example.macromod.ui.easyblock;

import com.example.macromod.MacroModClient;
import com.example.macromod.manager.AutoFishingManager;
import com.example.macromod.model.Macro;
import com.example.macromod.model.MacroStep;
import com.example.macromod.ui.MacroEditScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Main ClickGUI — dark theme with blue accent, LiquidBounce-inspired layout.
 * Two tabs: Macros (sidebar + detail) and Auto Farm (settings).
 */
@Environment(EnvType.CLIENT)
public class EasyBlockGui extends BasePopupScreen {

    // ═══════════════════════════════════════════════════════════════════
    // Public color palette (used by sub-screens)
    // ═══════════════════════════════════════════════════════════════════
    public static final int C_BG         = 0xFF0C0C14;
    public static final int C_PANEL_HEAD = 0xFF101018;
    public static final int C_CARD       = 0xFF141420;
    public static final int C_CARD_HOV   = 0xFF1A1A2C;
    public static final int C_CARD_SEL   = 0xFF1A2844;
    public static final int C_DIVIDER    = 0xFF222233;
    public static final int C_ACCENT     = 0xFF4677FF;
    public static final int C_ACCENT_HI  = 0xFF5A8AFF;
    public static final int C_DANGER     = 0xFFFF4444;
    public static final int C_SUCCESS    = 0xFF44FF88;
    public static final int C_TEXT       = 0xFFFFFFFF;
    public static final int C_TEXT2      = 0xFFD3D3D3;
    public static final int C_TEXT3      = 0xFF808080;
    public static final int C_NAV_BG     = 0xFF1A1A28;
    public static final int C_NAV_ACT    = 0xFF252538;

    // ═══════════════════════════════════════════════════════════════════
    // Layout constants
    // ═══════════════════════════════════════════════════════════════════
    private static final int PANEL_MAX_W = 720, PANEL_MAX_H = 480;
    private static final int HEADER_H = 36;
    private static final int SIDEBAR_W = 200;
    private static final int CARD_H = 48, CARD_GAP = 3, CARD_PX = 6;
    private static final int FOOTER_H = 36;
    private static final int ACT_W = 68, ACT_H = 22;
    private static final int R = 8, RB = 5;

    // ═══════════════════════════════════════════════════════════════════
    // Panel bounds (computed in init)
    // ═══════════════════════════════════════════════════════════════════
    private int px, py, pw, ph;

    // ═══════════════════════════════════════════════════════════════════
    // State — Tabs
    // ═══════════════════════════════════════════════════════════════════
    private int activeTab = 0; // 0 = Macros, 1 = Auto Farm
    private int tabMacrosX, tabFarmX, tabY, tabW, tabH;

    // ═══════════════════════════════════════════════════════════════════
    // State — Macros tab
    // ═══════════════════════════════════════════════════════════════════
    private Macro selectedMacro;
    private int scrollY = 0, maxScrollY = 0;
    private float scrollSmooth = 0;
    private boolean hoverClose, hoverNew;
    private boolean hoverRun, hoverEdit, hoverDelete, hoverDup;
    private int[] actX = new int[4];
    private int actY;
    private final int[][] chipBounds = new int[5][3]; // [idx][x, y, w]

    // ═══════════════════════════════════════════════════════════════════
    // State — Auto Farm tab
    // ═══════════════════════════════════════════════════════════════════
    private boolean optAutoFish, optFishAttack, optFishAttackDistance, optAttackMobs;
    private int optFishAttackSlot = -1;
    private int optFarmRadius = 8;
    private float animFish, animFishAtk, animMobs;
    private final int[][] fishTgBounds = new int[3][4]; // 0=fish, 1=fishAtk, 2=mobs
    private final int[][] hotbar = new int[9][2];
    private int modeBtnX, modeBtnY, modeBtnW, modeBtnH;
    private int radLeftX, radRightX, radBtnY;

    // ═══════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════

    public EasyBlockGui() {
        super(Text.literal("MacroMod"), null);
        AutoFishingManager mgr = AutoFishingManager.getInstance();
        optAutoFish = mgr.isEnabled();
        optFishAttack = mgr.isAttackEnabled();
        optFishAttackDistance = mgr.isAttackModeDistance();
        optFishAttackSlot = mgr.getAttackHotbarSlot();
        animFish = optAutoFish ? 1f : 0f;
        animFishAtk = optFishAttack ? 1f : 0f;
        animMobs = optAttackMobs ? 1f : 0f;
    }

    @Override
    protected void init() {
        pw = Math.min(width - 40, PANEL_MAX_W);
        ph = Math.min(height - 40, PANEL_MAX_H);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        recalcScroll();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Render
    // ═══════════════════════════════════════════════════════════════════

    @Override
    protected void drawScreen(DrawContext ctx, int mx, int my, float delta) {
        // Smooth scroll
        scrollSmooth = Anim.smooth(scrollSmooth, scrollY, 12f);

        // Panel background
        RoundedRectRenderer.draw(ctx, px, py, pw, ph, R, C_BG);

        // Header background
        ctx.fill(px, py, px + pw, py + HEADER_H, C_PANEL_HEAD);
        ctx.fill(px, py + HEADER_H - 1, px + pw, py + HEADER_H, C_DIVIDER);

        drawHeader(ctx, mx, my);

        if (activeTab == 0) {
            drawMacrosTab(ctx, mx, my);
        } else {
            drawAutoFarmTab(ctx, mx, my);
        }
    }

    // ─── Header ───────────────────────────────────────────────────────

    private void drawHeader(DrawContext ctx, int mx, int my) {
        // Title
        ctx.drawTextWithShadow(textRenderer, Text.literal("\u00A79\u2726 \u00A7f\u00A7lMacroMod"),
                px + 12, py + (HEADER_H - 8) / 2, C_TEXT);

        // Tab buttons (centered)
        tabW = 70; tabH = 22;
        int tabTotalW = tabW * 2 + 4;
        tabMacrosX = px + (pw - tabTotalW) / 2;
        tabFarmX = tabMacrosX + tabW + 4;
        tabY = py + (HEADER_H - tabH) / 2;

        boolean hovTab0 = mx >= tabMacrosX && mx < tabMacrosX + tabW && my >= tabY && my < tabY + tabH;
        boolean hovTab1 = mx >= tabFarmX && mx < tabFarmX + tabW && my >= tabY && my < tabY + tabH;

        RoundedRectRenderer.draw(ctx, tabMacrosX, tabY, tabW, tabH, RB,
                activeTab == 0 ? C_ACCENT : (hovTab0 ? C_NAV_ACT : C_NAV_BG));
        RoundedRectRenderer.draw(ctx, tabFarmX, tabY, tabW, tabH, RB,
                activeTab == 1 ? C_ACCENT : (hovTab1 ? C_NAV_ACT : C_NAV_BG));
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Macros"),
                tabMacrosX + tabW / 2, tabY + (tabH - 8) / 2, C_TEXT);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Auto Farm"),
                tabFarmX + tabW / 2, tabY + (tabH - 8) / 2, C_TEXT);

        // Close button
        int cx = px + pw - 28, cy = py + (HEADER_H - 20) / 2;
        hoverClose = mx >= cx && mx < cx + 20 && my >= cy && my < cy + 20;
        RoundedRectRenderer.draw(ctx, cx, cy, 20, 20, RB,
                hoverClose ? 0xFFAA1818 : 0xFF6E1010);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("\u2715"),
                cx + 10, cy + 6, C_TEXT);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Macros Tab
    // ═══════════════════════════════════════════════════════════════════

    private void drawMacrosTab(DrawContext ctx, int mx, int my) {
        // Sidebar / detail divider
        int vDiv = px + SIDEBAR_W;
        ctx.fill(vDiv, py + HEADER_H, vDiv + 1, py + ph, C_DIVIDER);
        // Footer divider
        ctx.fill(px, py + ph - FOOTER_H, px + pw, py + ph - FOOTER_H + 1, C_DIVIDER);

        drawSidebar(ctx, mx, my);
        drawDetail(ctx, mx, my);
    }

    // ─── Sidebar ──────────────────────────────────────────────────────

    private void drawSidebar(DrawContext ctx, int mx, int my) {
        List<Macro> macros = MacroModClient.getManager().getAll();
        int cardX = px + CARD_PX;
        int cardW = SIDEBAR_W - CARD_PX * 2;
        int listY0 = py + HEADER_H + 4;
        int listH = ph - HEADER_H - FOOTER_H - 8;

        // Scissor clip the macro list
        ctx.enableScissor(px, listY0, px + SIDEBAR_W, listY0 + listH);

        int smoothOff = (int) scrollSmooth;
        for (int i = 0; i < macros.size(); i++) {
            Macro m = macros.get(i);
            int cy = listY0 + i * (CARD_H + CARD_GAP) - smoothOff;
            if (cy + CARD_H < listY0 || cy > listY0 + listH) continue;

            boolean running = isRunning(m);
            boolean sel = isSel(m);
            boolean hov = mx >= cardX && mx < cardX + cardW && my >= cy && my < cy + CARD_H;

            RoundedRectRenderer.draw(ctx, cardX, cy, cardW, CARD_H, RB,
                    sel ? C_CARD_SEL : (hov ? C_CARD_HOV : C_CARD));

            // Left accent bar
            if (sel || running) {
                ctx.fill(cardX, cy + RB, cardX + 3, cy + CARD_H - RB,
                        running ? C_SUCCESS : C_ACCENT);
            }

            // Name
            String name = m.getName();
            if (name.length() > 20) name = name.substring(0, 18) + "..";
            ctx.drawTextWithShadow(textRenderer, Text.literal(name),
                    cardX + 8, cy + 8, C_TEXT);

            // Subtitle
            String sub = m.getSteps().size() + " steps \u00B7 " + m.getTotalBlockCount() + " blocks";
            ctx.drawTextWithShadow(textRenderer, Text.literal(sub),
                    cardX + 8, cy + 22, C_TEXT3);

            // Running indicator
            if (running) {
                ctx.drawTextWithShadow(textRenderer, Text.literal("\u25CF RUNNING"),
                        cardX + 8, cy + 34, C_SUCCESS);
            }
        }

        ctx.disableScissor();

        // New Macro button (in footer area)
        int nbW = 140, nbH = 22;
        int nbX = px + (SIDEBAR_W - nbW) / 2;
        int nbY = py + ph - FOOTER_H + (FOOTER_H - nbH) / 2;
        hoverNew = mx >= nbX && mx < nbX + nbW && my >= nbY && my < nbY + nbH;
        RoundedRectRenderer.draw(ctx, nbX, nbY, nbW, nbH, RB,
                hoverNew ? C_ACCENT_HI : C_ACCENT);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("+ New Macro"),
                nbX + nbW / 2, nbY + 7, C_TEXT);
    }

    // ─── Detail panel ─────────────────────────────────────────────────

    private void drawDetail(DrawContext ctx, int mx, int my) {
        int dx = px + SIDEBAR_W + 12;
        int dw = pw - SIDEBAR_W - 22;
        int dy = py + HEADER_H + 10;

        if (selectedMacro == null) {
            String hint = "\u2190 Select a macro";
            ctx.drawTextWithShadow(textRenderer, Text.literal(hint),
                    dx + (dw - textRenderer.getWidth(hint)) / 2,
                    py + ph / 2 - 4, C_TEXT3);
            hoverRun = hoverEdit = hoverDelete = hoverDup = false;
            return;
        }

        Macro m = selectedMacro;

        // Name
        ctx.drawTextWithShadow(textRenderer, Text.literal("\u00A7f\u00A7l" + m.getName()),
                dx, dy, C_TEXT);
        dy += 14;

        // Description
        if (m.getDescription() != null && !m.getDescription().isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, Text.literal(m.getDescription()),
                    dx, dy, C_TEXT2);
            dy += 12;
        }

        // Created date
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(m.getCreatedAt()));
        ctx.drawTextWithShadow(textRenderer, Text.literal("Created: \u00A77" + date),
                dx, dy, C_TEXT3);
        dy += 16;

        // Config chips
        int cx = dx;
        cx = drawChip(ctx, mx, my, cx, dy, "Loop", m.getConfig().isLoop(), 0) + 4;
        cx = drawChip(ctx, mx, my, cx, dy, "SkipMismatch", m.getConfig().isSkipMismatch(), 1) + 4;
        cx = drawChip(ctx, mx, my, cx, dy, "AttackDanger", m.getConfig().isAttackDanger(), 2) + 4;
        cx = drawChip(ctx, mx, my, cx, dy, "OnlyGround", m.getConfig().isOnlyGround(), 3) + 4;
        drawChip(ctx, mx, my, cx, dy, "LockCam", m.getConfig().isLockCrosshair(), 4);
        dy += 20;

        // Divider
        ctx.fill(dx, dy, dx + dw, dy + 1, C_DIVIDER);
        dy += 8;

        // Steps header
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("\u00A79Steps \u00A77(" + m.getSteps().size() + ")"),
                dx, dy, C_ACCENT);
        dy += 14;

        // Steps list
        int listBot = py + ph - FOOTER_H - 8;
        ctx.enableScissor(dx, dy, dx + dw, listBot);
        for (int i = 0; i < m.getSteps().size(); i++) {
            if (dy > listBot) break;
            MacroStep step = m.getSteps().get(i);
            String lbl = (i + 1) + ". " + (step.getLabel().length() > 28
                    ? step.getLabel().substring(0, 26) + ".." : step.getLabel());
            BlockPos dest = step.getDestination();
            String co = " \u00A77\u2192 " + dest.getX() + "," + dest.getY() + "," + dest.getZ()
                    + " (" + step.getTargets().size() + ")";
            ctx.drawTextWithShadow(textRenderer, Text.literal("\u00A7f" + lbl + "\u00A7a" + co),
                    dx + 4, dy, C_TEXT);
            dy += 12;
        }
        ctx.disableScissor();

        // Action buttons in footer
        actY = py + ph - FOOTER_H + (FOOTER_H - ACT_H) / 2;
        int gap = 6;
        actX[0] = dx;
        actX[1] = actX[0] + ACT_W + gap;
        actX[2] = actX[1] + ACT_W + gap;
        actX[3] = actX[2] + ACT_W + gap;

        boolean running = isRunning(m);

        hoverRun = hitBtn(mx, my, actX[0]);
        RoundedRectRenderer.draw(ctx, actX[0], actY, ACT_W, ACT_H, RB,
                running ? (hoverRun ? 0xFFAA1818 : 0xFF6E1010)
                        : (hoverRun ? 0xFF0CA844 : 0xFF0A6E32));
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal(running ? "\u25A0 Stop" : "\u25B6 Run"),
                actX[0] + ACT_W / 2, actY + 7, C_TEXT);

        hoverEdit = hitBtn(mx, my, actX[1]);
        RoundedRectRenderer.draw(ctx, actX[1], actY, ACT_W, ACT_H, RB,
                hoverEdit ? C_ACCENT_HI : C_ACCENT);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("\u270E Edit"),
                actX[1] + ACT_W / 2, actY + 7, C_TEXT);

        hoverDelete = hitBtn(mx, my, actX[2]);
        RoundedRectRenderer.draw(ctx, actX[2], actY, ACT_W, ACT_H, RB,
                hoverDelete ? 0xFFAA1818 : 0xFF6E1010);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("\u2717 Delete"),
                actX[2] + ACT_W / 2, actY + 7, C_TEXT);

        hoverDup = hitBtn(mx, my, actX[3]);
        RoundedRectRenderer.draw(ctx, actX[3], actY, ACT_W, ACT_H, RB,
                hoverDup ? C_NAV_ACT : C_NAV_BG);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("\u2295 Copy"),
                actX[3] + ACT_W / 2, actY + 7, C_TEXT);
    }

    private int drawChip(DrawContext ctx, int mx, int my,
                          int x, int y, String label, boolean on, int idx) {
        String text = label + " " + (on ? "\u00A7aON" : "\u00A7cOFF");
        int w = textRenderer.getWidth(text) + 8;
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + 14;
        int bg = on ? (hov ? 0xFF0CA844 : 0xFF0A6E32) : (hov ? C_NAV_ACT : C_NAV_BG);
        RoundedRectRenderer.draw(ctx, x, y, w, 14, 3, bg);
        ctx.drawTextWithShadow(textRenderer, Text.literal(text), x + 4, y + 3, C_TEXT);
        chipBounds[idx][0] = x;
        chipBounds[idx][1] = y;
        chipBounds[idx][2] = w;
        return x + w;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Auto Farm Tab
    // ═══════════════════════════════════════════════════════════════════

    private void drawAutoFarmTab(DrawContext ctx, int mx, int my) {
        // Animate toggles (per-frame)
        animFish = Anim.smooth(animFish, optAutoFish ? 1f : 0f, 20f);
        animFishAtk = Anim.smooth(animFishAtk, optFishAttack ? 1f : 0f, 20f);
        animMobs = Anim.smooth(animMobs, optAttackMobs ? 1f : 0f, 20f);

        for (int[] b : fishTgBounds) b[2] = -1;
        for (int[] h : hotbar) h[0] = -1;
        modeBtnW = 0;

        int lx = px + 24;
        int rEdge = px + pw - 24;
        int dy = py + HEADER_H + 18;

        // ── Fishing section ───────────────────────────────────────────
        dy = drawSectionHeader(ctx, lx, dy, "Fishing");
        dy = drawFarmToggleRow(ctx, mx, my, lx, rEdge, dy, "Auto Fish", animFish, 0) + 10;

        if (optAutoFish) {
            ctx.fill(lx + 16, dy - 8, rEdge, dy - 7, C_DIVIDER);
            dy = drawFarmToggleRow(ctx, mx, my, lx + 16, rEdge, dy, "Attack fish", animFishAtk, 1) + 8;

            if (optFishAttack) {
                // Mode button
                String modeLabel = "Mode: " + (optFishAttackDistance ? "Distance" : "Close");
                int mw = textRenderer.getWidth(modeLabel) + 18, mh = 22;
                modeBtnX = lx + 32; modeBtnY = dy; modeBtnW = mw; modeBtnH = mh;
                boolean mhov = mx >= modeBtnX && mx < modeBtnX + mw && my >= dy && my < dy + mh;
                RoundedRectRenderer.draw(ctx, modeBtnX, modeBtnY, mw, mh, RB,
                        mhov ? C_NAV_ACT : C_NAV_BG);
                ctx.drawTextWithShadow(textRenderer, Text.literal(modeLabel),
                        modeBtnX + 9, modeBtnY + (mh - 8) / 2, C_TEXT2);
                dy += mh + 8;

                // Hotbar item selector
                ctx.drawTextWithShadow(textRenderer, Text.literal("Attack item:"),
                        lx + 32, dy, C_TEXT2);
                dy += 14;
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player != null) {
                    int slot = 0;
                    for (int i = 0; i < 9; i++) {
                        ItemStack stack = mc.player.getInventory().getStack(i);
                        if (stack.isEmpty()) { hotbar[i][0] = -1; continue; }
                        int bx = lx + 32 + slot * 22;
                        boolean sel = optFishAttackSlot == i;
                        boolean shov = mx >= bx && mx < bx + 20 && my >= dy && my < dy + 20;
                        RoundedRectRenderer.draw(ctx, bx, dy, 20, 20, 3,
                                sel ? C_ACCENT : (shov ? C_NAV_ACT : C_NAV_BG));
                        ctx.drawItem(stack, bx + 2, dy + 2);
                        hotbar[i][0] = bx; hotbar[i][1] = dy;
                        slot++;
                    }
                    dy += 26;
                }
            }
        }

        // ── Combat section ────────────────────────────────────────────
        dy += 6;
        dy = drawSectionHeader(ctx, lx, dy, "Combat");
        dy = drawFarmToggleRow(ctx, mx, my, lx, rEdge, dy, "Attack Mobs Near Farm", animMobs, 2) + 10;

        // Radius cycler
        String radVal = optFarmRadius + " blk";
        int radW = textRenderer.getWidth(radVal) + 12;
        ctx.drawTextWithShadow(textRenderer, Text.literal("Radius:"), lx, dy + 4, C_TEXT2);
        int radValX = rEdge - radW - 28;
        radLeftX = radValX - 20; radRightX = radValX + radW + 4; radBtnY = dy;
        boolean hL = mx >= radLeftX && mx < radLeftX + 16 && my >= dy && my < dy + 18;
        boolean hR = mx >= radRightX && mx < radRightX + 16 && my >= dy && my < dy + 18;
        RoundedRectRenderer.draw(ctx, radLeftX, dy, 16, 18, RB, hL ? C_NAV_ACT : C_NAV_BG);
        RoundedRectRenderer.draw(ctx, radRightX, dy, 16, 18, RB, hR ? C_NAV_ACT : C_NAV_BG);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("<"), radLeftX + 8, dy + 5, C_TEXT);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(">"), radRightX + 8, dy + 5, C_TEXT);
        RoundedRectRenderer.draw(ctx, radValX, dy, radW, 18, RB, C_CARD);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(radVal),
                radValX + radW / 2, dy + 5, C_TEXT);
        dy += 30;

        // Footer
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Settings apply while macros run."),
                lx, dy, C_TEXT3);
    }

    private int drawSectionHeader(DrawContext ctx, int x, int y, String title) {
        ctx.drawTextWithShadow(textRenderer, Text.literal(title), x, y, C_TEXT);
        RoundedRectRenderer.draw(ctx, x, y + 12, textRenderer.getWidth(title), 2, 1, C_ACCENT);
        return y + 22;
    }

    private int drawFarmToggleRow(DrawContext ctx, int mx, int my,
                                   int x, int rEdge, int y, String label, float anim, int idx) {
        int tw = ToggleRenderer.TOGGLE_W, th = ToggleRenderer.TOGGLE_H;
        int rowH = Math.max(th + 4, 20);
        ctx.drawTextWithShadow(textRenderer, Text.literal(label),
                x, y + (rowH - 8) / 2, C_TEXT);
        int tgX = rEdge - tw, tgY = y + (rowH - th) / 2;
        ToggleRenderer.draw(ctx, tgX, tgY, anim);
        fishTgBounds[idx][0] = tgX; fishTgBounds[idx][1] = tgY;
        fishTgBounds[idx][2] = tw;  fishTgBounds[idx][3] = th;
        return y + rowH;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Input
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int imx = (int) mx, imy = (int) my;

        // Outside panel
        if (imx < px || imx > px + pw || imy < py || imy > py + ph) {
            animClose();
            return true;
        }

        // Close button
        if (hoverClose) { animClose(); return true; }

        // Tab buttons
        if (imy >= tabY && imy < tabY + tabH) {
            if (imx >= tabMacrosX && imx < tabMacrosX + tabW) { activeTab = 0; return true; }
            if (imx >= tabFarmX && imx < tabFarmX + tabW)     { activeTab = 1; return true; }
        }

        if (activeTab == 0) return handleMacrosClick(imx, imy);
        else                return handleAutoFarmClick(imx, imy);
    }

    private boolean handleMacrosClick(int imx, int imy) {
        List<Macro> macros = MacroModClient.getManager().getAll();

        // Config chip toggles
        if (selectedMacro != null) {
            var cfg = selectedMacro.getConfig();
            for (int i = 0; i < 5; i++) {
                int cx = chipBounds[i][0], cy = chipBounds[i][1], cw = chipBounds[i][2];
                if (cw > 0 && imx >= cx && imx < cx + cw && imy >= cy && imy < cy + 14) {
                    switch (i) {
                        case 0 -> cfg.setLoop(!cfg.isLoop());
                        case 1 -> cfg.setSkipMismatch(!cfg.isSkipMismatch());
                        case 2 -> cfg.setAttackDanger(!cfg.isAttackDanger());
                        case 3 -> cfg.setOnlyGround(!cfg.isOnlyGround());
                        case 4 -> cfg.setLockCrosshair(!cfg.isLockCrosshair());
                    }
                    MacroModClient.getManager().save(selectedMacro);
                    return true;
                }
            }
        }

        // Sidebar macro cards
        int cardX = px + CARD_PX;
        int cardW = SIDEBAR_W - CARD_PX * 2;
        int listY0 = py + HEADER_H + 4;
        int listH = ph - HEADER_H - FOOTER_H - 8;

        if (imx >= cardX && imx < cardX + cardW
                && imy >= listY0 && imy < listY0 + listH) {
            int smoothOff = (int) scrollSmooth;
            for (int i = 0; i < macros.size(); i++) {
                int cy = listY0 + i * (CARD_H + CARD_GAP) - smoothOff;
                if (imy >= cy && imy < cy + CARD_H) {
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
                    animClose();
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

        return false;
    }

    private boolean handleAutoFarmClick(int imx, int imy) {
        // Fish toggles
        if (hitTg(imx, imy, fishTgBounds[0])) { optAutoFish = !optAutoFish; syncFish(); return true; }
        if (hitTg(imx, imy, fishTgBounds[1])) { optFishAttack = !optFishAttack; syncFish(); return true; }
        if (hitTg(imx, imy, fishTgBounds[2])) { optAttackMobs = !optAttackMobs; return true; }

        // Mode button
        if (modeBtnW > 0 && imx >= modeBtnX && imx < modeBtnX + modeBtnW
                && imy >= modeBtnY && imy < modeBtnY + modeBtnH) {
            optFishAttackDistance = !optFishAttackDistance;
            syncFish();
            return true;
        }

        // Hotbar slots
        for (int i = 0; i < 9; i++) {
            if (hotbar[i][0] < 0) continue;
            if (imx >= hotbar[i][0] && imx < hotbar[i][0] + 20
                    && imy >= hotbar[i][1] && imy < hotbar[i][1] + 20) {
                optFishAttackSlot = (optFishAttackSlot == i) ? -1 : i;
                syncFish();
                return true;
            }
        }

        // Radius cycler
        if (imy >= radBtnY && imy < radBtnY + 18) {
            if (imx >= radLeftX && imx < radLeftX + 16) {
                optFarmRadius = Math.max(1, optFarmRadius - 1); return true;
            }
            if (imx >= radRightX && imx < radRightX + 16) {
                optFarmRadius = Math.min(32, optFarmRadius + 1); return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        if (activeTab == 0 && mx >= px && mx < px + SIDEBAR_W
                && my >= py + HEADER_H && my < py + ph - FOOTER_H) {
            scrollY = Math.max(0, Math.min(maxScrollY, scrollY - (int) (vAmt * 14)));
            return true;
        }
        return super.mouseScrolled(mx, my, hAmt, vAmt);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════

    private boolean hitBtn(int mx, int my, int bx) {
        return mx >= bx && mx < bx + ACT_W && my >= actY && my < actY + ACT_H;
    }

    private boolean hitTg(int mx, int my, int[] b) {
        return b[2] > 0 && mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
    }

    private boolean isRunning(Macro m) {
        return MacroModClient.getExecutor().isRunning()
                && MacroModClient.getExecutor().getCurrentMacro() != null
                && MacroModClient.getExecutor().getCurrentMacro().getId().equals(m.getId());
    }

    private boolean isSel(Macro m) {
        return selectedMacro != null && selectedMacro.getId().equals(m.getId());
    }

    private void recalcScroll() {
        int count = MacroModClient.getManager().getAll().size();
        int listH = ph - HEADER_H - FOOTER_H - 8;
        maxScrollY = Math.max(0, count * (CARD_H + CARD_GAP) - listH);
        scrollY = Math.min(scrollY, maxScrollY);
    }

    private void syncFish() {
        AutoFishingManager.getInstance().setEnabled(optAutoFish);
        AutoFishingManager.getInstance().setAttackConfig(optFishAttack, optFishAttackDistance, optFishAttackSlot);
    }
}
