package com.example.macromod.ui;

import com.example.macromod.MacroModClient;
import com.example.macromod.model.Macro;
import com.example.macromod.model.MacroConfig;
import com.example.macromod.model.MacroStep;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;

@Environment(EnvType.CLIENT)
public class MacroEditScreen extends Screen {

    // ── Colours (same palette as MacroScreen) ──────────────────
    private static final int C_BACKDROP  = 0xB8000000;
    private static final int C_PANEL     = 0xFF131320;
    private static final int C_HEADER    = 0xFF0A0A14;
    private static final int C_DIVIDER   = 0xFF1E1E3A;
    private static final int C_FIELD_BG  = 0xFF0E0E1C;
    private static final int C_GREEN     = 0xFF0A6E32;
    private static final int C_GREEN_H   = 0xFF0CA844;
    private static final int C_RED       = 0xFF6E1010;
    private static final int C_RED_H     = 0xFFAA1818;
    private static final int C_BLUE      = 0xFF1A3E6E;
    private static final int C_BLUE_H    = 0xFF2460A8;
    private static final int C_TEXT      = 0xFFFFFFFF;
    private static final int C_TEXT_SEC  = 0xFFAAAAAA;
    private static final int C_TEXT_DIM  = 0xFF555570;
    private static final int C_ACCENT    = 0xFF55DDFF;

    // ── Layout ─────────────────────────────────────────────────
    private static final int HEADER_H = 38;
    private static final int FOOTER_H = 38;
    private static final int LEFT_W   = 380;
    private static final int BTN_H    = 22;
    private static final int SA_W     = 68;
    private static final int FB_W     = 90;

    // ── Data ───────────────────────────────────────────────────
    private final Macro  macro;
    private final Screen parent;

    private String  editName;
    private String  editDescription;
    private boolean editLoop;
    private boolean editSkipMismatch;
    private boolean editStopOnDanger;
    private int     editMiningDelay;
    private int     editMoveTimeout;
    private float   editArrivalRadius;

    // ── Step list ──────────────────────────────────────────────
    private int stepScrollOffset  = 0;
    private int selectedStepIndex = -1;

    // ── Panel bounds ───────────────────────────────────────────
    private int panelX, panelY, panelW, panelH;
    private int nameFieldBodyY;
    private int nameFieldY;
    private int descFieldY;

    // ── Widgets ────────────────────────────────────────────────
    private TextFieldWidget nameField;
    private TextFieldWidget descriptionField;

    // ── Hit areas ──────────────────────────────────────────────
    /** chipBounds[i] = {x, y, w}  –  Loop / SkipMismatch / StopOnDanger */
    private final int[][] chipBounds   = new int[3][3];
    /** cyclerBounds[i] = {leftBtnX, rightBtnX, y}  –  Delay / Timeout / Radius */
    private final int[][] cyclerBounds = new int[3][3];

    private int   stepActY;
    private final int[] stepActX = new int[4];
    private int   footerBtnY, saveX, cancelX;

    // ── Per-frame hover flags ──────────────────────────────────
    private boolean hoverClose, hoverSave, hoverCancel;
    private boolean hoverAddStep, hoverMoveUp, hoverMoveDown, hoverRemStep;

    public MacroEditScreen(Macro macro, Screen parent) {
        super(Text.literal("Edit Macro"));
        this.macro  = macro;
        this.parent = parent;

        editName        = macro.getName();
        editDescription = macro.getDescription() != null ? macro.getDescription() : "";
        MacroConfig cfg = macro.getConfig();
        editLoop         = cfg.isLoop();
        editSkipMismatch = cfg.isSkipMismatch();
        editStopOnDanger = cfg.isStopOnDanger();
        editMiningDelay  = cfg.getMiningDelay();
        editMoveTimeout  = cfg.getMoveTimeout();
        editArrivalRadius = cfg.getArrivalRadius();
    }

    // ═══════════════════════════════════════════════════════════════
    // Init
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        panelW = Math.min(this.width  - 40, 920);
        panelH = Math.min(this.height - 40, 560);
        panelX = (this.width  - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        nameFieldBodyY = panelY + HEADER_H + 8;
        int fieldW = LEFT_W - 28;
        int leftX  = panelX + 14;

        nameFieldY = nameFieldBodyY + 22;
        nameField  = new TextFieldWidget(textRenderer, leftX, nameFieldY, fieldW, 18, Text.literal("Name"));
        nameField.setMaxLength(64);
        nameField.setText(editName);
        nameField.setChangedListener(s -> editName = s);
        addSelectableChild(nameField);
        addDrawable(nameField);

        descFieldY = nameFieldBodyY + 62;
        descriptionField = new TextFieldWidget(textRenderer, leftX, descFieldY, fieldW, 18, Text.literal("Description"));
        descriptionField.setMaxLength(256);
        descriptionField.setText(editDescription);
        descriptionField.setChangedListener(s -> editDescription = s);
        addSelectableChild(descriptionField);
        addDrawable(descriptionField);
    }

    // ═══════════════════════════════════════════════════════════════
    // Render
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        // Background + panel shell
        ctx.fill(0, 0, this.width, this.height, C_BACKDROP);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, C_PANEL);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + HEADER_H, C_HEADER);

        // Structural dividers
        int hDiv = panelY + HEADER_H;
        int vDiv = panelX + LEFT_W;
        int fDiv = panelY + panelH - FOOTER_H;
        ctx.fill(panelX, hDiv, panelX + panelW, hDiv + 1, C_DIVIDER);
        ctx.fill(vDiv,   panelY, vDiv + 1, panelY + panelH, C_DIVIDER);
        ctx.fill(panelX, fDiv, panelX + panelW, fDiv + 1, C_DIVIDER);

        renderHeader(ctx, mx, my);
        renderLeftPanel(ctx, mx, my);
        renderRightPanel(ctx, mx, my);
        renderFooter(ctx, mx, my);

        // Draw MC text-field widgets last so they appear on top
        super.render(ctx, mx, my, delta);
    }

    // ── Header ───────────────────────────────────────────────────

    private void renderHeader(DrawContext ctx, int mx, int my) {
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("\u00a7b\u2756 \u00a7fEdit: \u00a7b" + macro.getName()),
                panelX + 10, panelY + 12, C_TEXT);

        int cx = panelX + panelW - 26, cy = panelY + 9;
        hoverClose = mx >= cx && mx < cx + 18 && my >= cy && my < cy + 18;
        ctx.fill(cx, cy, cx + 18, cy + 18, hoverClose ? C_RED_H : C_RED);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("\u2715"), cx + 9, cy + 5, C_TEXT);
    }

    // ── Left panel ───────────────────────────────────────────────

    private void renderLeftPanel(DrawContext ctx, int mx, int my) {
        int x  = panelX + 14;
        int y0 = nameFieldBodyY;

        // Field labels (TextFieldWidgets draw themselves)
        ctx.drawTextWithShadow(textRenderer, Text.literal("\u00a77Name"),        x, y0 + 10, C_TEXT_SEC);
        ctx.drawTextWithShadow(textRenderer, Text.literal("\u00a77Description"), x, y0 + 50, C_TEXT_SEC);

        // Config section
        int dy = y0 + 88;
        ctx.fill(x, dy, panelX + LEFT_W - 10, dy + 1, C_DIVIDER);
        dy += 8;
        ctx.drawTextWithShadow(textRenderer, Text.literal("\u00a7bConfig"), x, dy, C_ACCENT);
        dy += 14;

        dy = renderChip(ctx, mx, my, x, dy, "Loop",           editLoop,         false, 0) + 5;
        dy = renderChip(ctx, mx, my, x, dy, "Skip Mismatch",  editSkipMismatch, false, 1) + 5;
        dy = renderChip(ctx, mx, my, x, dy, "Stop On Danger", editStopOnDanger, true,  2) + 8;

        // Numeric section
        ctx.fill(x, dy, panelX + LEFT_W - 10, dy + 1, C_DIVIDER);
        dy += 8;
        ctx.drawTextWithShadow(textRenderer, Text.literal("\u00a7bNumeric"), x, dy, C_ACCENT);
        dy += 14;

        dy = renderCycler(ctx, mx, my, x, dy, "Mining Delay",   editMiningDelay + " ms",                       0) + 4;
        dy = renderCycler(ctx, mx, my, x, dy, "Move Timeout",   editMoveTimeout + " ticks",                    1) + 4;
             renderCycler(ctx, mx, my, x, dy, "Arrival Radius", String.format("%.1f blk", editArrivalRadius),  2);
    }

    /** Draws an ON/OFF toggle chip and stores its hit area; returns bottom Y. */
    private int renderChip(DrawContext ctx, int mx, int my,
                           int x, int y, String label, boolean on, boolean danger, int idx) {
        String text = label + "  " + (on ? "\u00a7aON" : "\u00a7cOFF");
        int w = textRenderer.getWidth(text) + 14;
        int h = 16;
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        int bg;
        if (on) {
            bg = danger ? (hov ? C_RED_H : C_RED) : (hov ? C_GREEN_H : C_GREEN);
        } else {
            bg = hov ? 0xFF2A2A40 : 0xFF1E1E30;
        }
        ctx.fill(x, y, x + w, y + h, bg);
        ctx.drawTextWithShadow(textRenderer, Text.literal(text), x + 7, y + 4, C_TEXT);
        chipBounds[idx][0] = x;
        chipBounds[idx][1] = y;
        chipBounds[idx][2] = w;
        return y + h;
    }

    /** Draws a < value > cycler row and stores hit areas; returns bottom Y. */
    private int renderCycler(DrawContext ctx, int mx, int my,
                             int x, int y, String label, String valueStr, int idx) {
        ctx.drawTextWithShadow(textRenderer, Text.literal("\u00a77" + label), x, y, C_TEXT_SEC);
        y += 12;

        int arrowW = 18, h = 16;
        int valW   = textRenderer.getWidth(valueStr) + 14;

        boolean hovL = mx >= x && mx < x + arrowW && my >= y && my < y + h;
        ctx.fill(x, y, x + arrowW, y + h, hovL ? C_BLUE_H : C_BLUE);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("\u2039"), x + arrowW / 2, y + 4, C_TEXT);

        ctx.fill(x + arrowW, y, x + arrowW + valW, y + h, C_FIELD_BG);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(valueStr),
                x + arrowW + valW / 2, y + 4, C_TEXT);

        int rx = x + arrowW + valW;
        boolean hovR = mx >= rx && mx < rx + arrowW && my >= y && my < y + h;
        ctx.fill(rx, y, rx + arrowW, y + h, hovR ? C_BLUE_H : C_BLUE);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("\u203a"), rx + arrowW / 2, y + 4, C_TEXT);

        cyclerBounds[idx][0] = x;
        cyclerBounds[idx][1] = rx;
        cyclerBounds[idx][2] = y;
        return y + h;
    }

    // ── Right panel ───────────────────────────────────────────────

    private void renderRightPanel(DrawContext ctx, int mx, int my) {
        int rx  = panelX + LEFT_W + 10;
        int rw  = panelW - LEFT_W - 16;
        int y   = panelY + HEADER_H + 10;
        int gap = 4;

        ctx.drawTextWithShadow(textRenderer,
                Text.literal("\u00a7bSteps \u00a77(" + macro.getSteps().size() + ")"), rx, y, C_ACCENT);
        y += 16;

        stepActY     = y;
        stepActX[0]  = rx;
        stepActX[1]  = rx + SA_W + gap;
        stepActX[2]  = rx + (SA_W + gap) * 2;
        stepActX[3]  = rx + (SA_W + gap) * 3;

        hoverAddStep  = inSA(mx, my, 0);
        hoverMoveUp   = inSA(mx, my, 1);
        hoverMoveDown = inSA(mx, my, 2);
        hoverRemStep  = inSA(mx, my, 3);

        drawSmallBtn(ctx, stepActX[0], stepActY, SA_W, BTN_H, "+ Add",         hoverAddStep,  C_GREEN, C_GREEN_H);
        drawSmallBtn(ctx, stepActX[1], stepActY, SA_W, BTN_H, "\u25b2 Up",     hoverMoveUp,   C_BLUE,  C_BLUE_H);
        drawSmallBtn(ctx, stepActX[2], stepActY, SA_W, BTN_H, "\u25bc Down",   hoverMoveDown, C_BLUE,  C_BLUE_H);
        drawSmallBtn(ctx, stepActX[3], stepActY, SA_W, BTN_H, "\u2717 Remove", hoverRemStep,  C_RED,   C_RED_H);

        y += BTN_H + 6;

        // Step list
        int entryH = 28;
        int listH  = panelY + panelH - FOOTER_H - y - 4;
        List<MacroStep> steps = macro.getSteps();

        if (steps.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("\u00a77No steps yet"), rx, y + listH / 2, C_TEXT_DIM);
        } else {
            int maxVis = Math.max(1, listH / entryH);
            int end    = Math.min(stepScrollOffset + maxVis, steps.size());
            for (int i = stepScrollOffset; i < end; i++) {
                MacroStep step = steps.get(i);
                int ey  = y + (i - stepScrollOffset) * entryH;
                boolean sel = (i == selectedStepIndex);
                boolean hov = mx >= rx && mx < rx + rw && my >= ey && my < ey + entryH - 2;
                ctx.fill(rx, ey, rx + rw, ey + entryH - 2,
                        sel ? 0xFF183050 : (hov ? 0xFF22223A : 0xFF1A1A2C));
                if (sel) ctx.fill(rx, ey, rx + 3, ey + entryH - 2, 0xFF00B4D8);

                String nm = (i + 1) + ". " + (step.getLabel().length() > 28
                        ? step.getLabel().substring(0, 26) + ".." : step.getLabel());
                BlockPos dest = step.getDestination();
                String coord  = dest.getX() + "," + dest.getY() + "," + dest.getZ()
                        + "  (" + step.getTargets().size() + " blk)";
                ctx.drawTextWithShadow(textRenderer, Text.literal(nm),                 rx + 6, ey + 4,  C_TEXT);
                ctx.drawTextWithShadow(textRenderer, Text.literal("\u00a77" + coord),  rx + 6, ey + 15, C_TEXT_SEC);
            }
        }
    }

    private void drawSmallBtn(DrawContext ctx, int x, int y, int w, int h,
                              String label, boolean hov, int bg, int bgH) {
        ctx.fill(x, y, x + w, y + h, hov ? bgH : bg);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(label), x + w / 2, y + 7, C_TEXT);
    }

    private boolean inSA(int mx, int my, int i) {
        return mx >= stepActX[i] && mx < stepActX[i] + SA_W
                && my >= stepActY && my < stepActY + BTN_H;
    }

    // ── Footer ────────────────────────────────────────────────────

    private void renderFooter(DrawContext ctx, int mx, int my) {
        footerBtnY = panelY + panelH - FOOTER_H + (FOOTER_H - BTN_H) / 2;
        int total  = FB_W * 2 + 10;
        saveX   = panelX + (panelW - total) / 2;
        cancelX = saveX + FB_W + 10;

        hoverSave   = mx >= saveX   && mx < saveX   + FB_W && my >= footerBtnY && my < footerBtnY + BTN_H;
        hoverCancel = mx >= cancelX && mx < cancelX + FB_W && my >= footerBtnY && my < footerBtnY + BTN_H;

        ctx.fill(saveX,   footerBtnY, saveX   + FB_W, footerBtnY + BTN_H, hoverSave   ? C_GREEN_H : C_GREEN);
        ctx.fill(cancelX, footerBtnY, cancelX + FB_W, footerBtnY + BTN_H, hoverCancel ? C_RED_H   : C_RED);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("\u2714 Save"),   saveX   + FB_W / 2, footerBtnY + 7, C_TEXT);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("\u2717 Cancel"), cancelX + FB_W / 2, footerBtnY + 7, C_TEXT);
    }

    // ═══════════════════════════════════════════════════════════════
    // Input
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int imx = (int) mx, imy = (int) my;

        // Close / outside-panel click
        if (hoverClose) { backToParent(); return true; }
        if (imx < panelX || imx > panelX + panelW || imy < panelY || imy > panelY + panelH) {
            backToParent(); return true;
        }

        // Bool chips
        for (int i = 0; i < 3; i++) {
            int cx = chipBounds[i][0], cy = chipBounds[i][1], cw = chipBounds[i][2];
            if (cw > 0 && imx >= cx && imx < cx + cw && imy >= cy && imy < cy + 16) {
                switch (i) {
                    case 0 -> editLoop         = !editLoop;
                    case 1 -> editSkipMismatch = !editSkipMismatch;
                    case 2 -> editStopOnDanger = !editStopOnDanger;
                }
                return true;
            }
        }

        // Numeric cyclers
        for (int i = 0; i < 3; i++) {
            int lx = cyclerBounds[i][0], rx = cyclerBounds[i][1], cy = cyclerBounds[i][2];
            if (imy >= cy && imy < cy + 16) {
                if (imx >= lx && imx < lx + 18) { cycleNumeric(i, -1); return true; }
                if (imx >= rx && imx < rx + 18) { cycleNumeric(i, +1); return true; }
            }
        }

        // Step action buttons
        if (hoverAddStep) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                BlockPos pos = mc.player.getBlockPos();
                macro.addStep(new MacroStep("Step " + (macro.getSteps().size() + 1), pos));
            }
            return true;
        }
        if (hoverMoveUp && selectedStepIndex > 0) {
            List<MacroStep> steps = macro.getSteps();
            MacroStep s = steps.remove(selectedStepIndex);
            steps.add(--selectedStepIndex, s);
            return true;
        }
        if (hoverMoveDown && selectedStepIndex >= 0
                && selectedStepIndex < macro.getSteps().size() - 1) {
            List<MacroStep> steps = macro.getSteps();
            MacroStep s = steps.remove(selectedStepIndex);
            steps.add(++selectedStepIndex, s);
            return true;
        }
        if (hoverRemStep && selectedStepIndex >= 0
                && selectedStepIndex < macro.getSteps().size()) {
            macro.removeStep(selectedStepIndex);
            if (selectedStepIndex >= macro.getSteps().size())
                selectedStepIndex = macro.getSteps().size() - 1;
            return true;
        }

        // Step list click
        int listTop = stepActY + BTN_H + 6;
        int rightX  = panelX + LEFT_W + 10;
        if (imx >= rightX && imy >= listTop) {
            int idx = stepScrollOffset + (imy - listTop) / 28;
            if (idx >= 0 && idx < macro.getSteps().size()) {
                selectedStepIndex = idx;
                return true;
            }
        }

        // Footer
        if (hoverSave)   { applyChanges(); backToParent(); return true; }
        if (hoverCancel) { backToParent(); return true; }

        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        if (mx >= panelX + LEFT_W) {
            int maxOff = Math.max(0, macro.getSteps().size() - 5);
            stepScrollOffset = Math.max(0, Math.min(maxOff, stepScrollOffset - (int) vAmt));
            return true;
        }
        return super.mouseScrolled(mx, my, hAmt, vAmt);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() { backToParent(); }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    private void backToParent() {
        if (client != null) client.setScreen(parent);
    }

    private void cycleNumeric(int idx, int dir) {
        switch (idx) {
            case 0 -> editMiningDelay  = clamp(editMiningDelay + dir * 50, 50, 450);
            case 1 -> editMoveTimeout  = clamp(editMoveTimeout + dir * 100, 100, 2000);
            case 2 -> {
                int steps = Math.round(editArrivalRadius / 0.5f) + dir;
                editArrivalRadius = clamp(steps, 1, 10) * 0.5f;
            }
        }
    }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) return hi;
        if (v > hi) return lo;
        return v;
    }

    private void applyChanges() {
        macro.setName(editName);
        macro.setDescription(editDescription);
        MacroConfig cfg = macro.getConfig();
        cfg.setLoop(editLoop);
        cfg.setSkipMismatch(editSkipMismatch);
        cfg.setStopOnDanger(editStopOnDanger);
        cfg.setMiningDelay(editMiningDelay);
        cfg.setMoveTimeout(editMoveTimeout);
        cfg.setArrivalRadius(editArrivalRadius);
        MacroModClient.getManager().save(macro);
    }
}

