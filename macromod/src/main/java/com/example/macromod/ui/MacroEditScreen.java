package com.example.macromod.ui;

import com.example.macromod.MacroModClient;
import com.example.macromod.model.Macro;
import com.example.macromod.model.MacroConfig;
import com.example.macromod.model.MacroStep;
import com.example.macromod.ui.easyblock.Anim;
import com.example.macromod.ui.easyblock.BasePopupScreen;
import com.example.macromod.ui.easyblock.EasyBlockGui;
import com.example.macromod.ui.easyblock.RoundedRectRenderer;
import com.example.macromod.ui.easyblock.ToggleRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Macro editor — popup screen with scrollable left panel and responsive layout.
 *
 * Layout:
 *   ┌──────────────────────────────────┐
 *   │  Header (HH px)                  │
 *   ├─────────────────┬────────────────│
 *   │ [Fixed: fields] │  Steps list    │
 *   │─────────────────│  (scrollable)  │
 *   │ [Scrollable:    │                │
 *   │  Config/Timing/ │                │
 *   │  Attack/WL]     │                │
 *   ├─────────────────┴────────────────│
 *   │  Footer: Save / Cancel           │
 *   └──────────────────────────────────┘
 */
@Environment(EnvType.CLIENT)
public class MacroEditScreen extends BasePopupScreen {

    // ── Layout constants ──────────────────────────────────────────────
    private static final int HH = 36, FH = 40, R = 8, RB = 5;
    private static final int ROW_GAP = 4;
    private static final int EL_ROW_H = 16;   // entity whitelist row height (tight, no gap)
    /** Max visible rows before entity list gets its own scrollbar. */
    private static final int EL_MAX_VISIBLE = 5;

    // Computed each init()
    private int px, py, pw, ph, lw;

    // ── Edit state ────────────────────────────────────────────────────
    private final Macro macro;
    private String  editName, editDesc;
    private boolean editLoop, editSkipMismatch, editMineOnlyDefinedTargets, editAttackDanger, editOnlyGround, editLockCam;
    private boolean editRandomAttackCps;
    private int     editAttackCPS, editMiningDelay, editMoveTimeout;
    private float   editArrivalRadius;
    private boolean editAttackEnabled, editAttackWlOnly;
    private final List<String> editAttackWl = new ArrayList<>();
    private int     editAttackRange;

    // ── Toggle animations ─────────────────────────────────────────────
    private final float[] tgAnim = new float[9];

    // ── Left panel scroll ─────────────────────────────────────────────
    private int leftScroll    = 0;
    private int leftScrollMax = 0;
    /** Y coord where the scrollable section starts (computed each frame). */
    private int scrollStartY  = 0;

    // ── Text fields ───────────────────────────────────────────────────
    private TextFieldWidget nameField, descField;

    // ── Step list ─────────────────────────────────────────────────────
    private int stepScroll = 0, selStep = -1;

    // ── Hit-area caches (set during render, read during click) ────────
    private final int[][] tgBounds  = new int[9][4];  // [idx][x,y,w,h], w<0 = inactive
    private final int[][] cycBounds = new int[4][4];  // [idx][lbx,rbx,y, active?]
    private int atkRngLbx = -1, atkRngRbx = -1, atkRngY = -1;
    private int stepBtnY;
    private final int[] stepBtnX = new int[4];
    private int stepBtnW, stepBtnH;
    private int saveX, cancelX, footerBtnY, fbW = 90, fbH = 24;
    /** Entity whitelist: list shown this frame + their rendered Y start. */
    private final List<String> elShown = new ArrayList<>();
    private int elX, elRowY0, elW;
    /** Scroll offset for the entity whitelist sub-list (rows). */
    private int elScroll = 0;

    // ── Hover states ─────────────────────────────────────────────────
    private boolean hoverSave, hoverCancel;
    private boolean hoverAdd, hoverUp, hoverDown, hoverRem;

    // ─────────────────────────────────────────────────────────────────
    public MacroEditScreen(Macro macro, Screen parent) {
        super(Text.literal("Edit: " + macro.getName()), parent);
        this.macro = macro;
        MacroConfig c = macro.getConfig();
        editName          = macro.getName();
        editDesc          = macro.getDescription() != null ? macro.getDescription() : "";
        editLoop          = c.isLoop();
        editSkipMismatch  = c.isSkipMismatch();
        editMineOnlyDefinedTargets = c.isMineOnlyDefinedTargets();
        editAttackDanger  = c.isAttackDanger();
        editOnlyGround    = c.isOnlyGround();
        editLockCam       = c.isLockCrosshair();
        editAttackCPS     = c.getAttackCPS();
        editMiningDelay   = c.getMiningDelay();
        editMoveTimeout   = c.getMoveTimeout();
        editArrivalRadius = c.getArrivalRadius();
        editAttackEnabled = c.isAttackEnabled();
        editAttackWlOnly  = c.isAttackWhitelistOnly();
        editRandomAttackCps = c.isRandomAttackCps();
        editAttackWl.addAll(c.getAttackWhitelist());
        editAttackRange   = c.getAttackRange();
        boolean[] v = {editLoop, editSkipMismatch, editMineOnlyDefinedTargets, editAttackDanger, editOnlyGround,
            editLockCam, editAttackEnabled, editAttackWlOnly, editRandomAttackCps};
        for (int i = 0; i < 9; i++) tgAnim[i] = v[i] ? 1f : 0f;
    }

    // ═════════════════════════════════════════════════════════════════
    // Init — called on open and every resize
    // ═════════════════════════════════════════════════════════════════
    @Override
    protected void init() {
        // Responsive sizing: fills up to 900×540, always leaves 40px margin
        pw = Math.min(900, Math.max(400, width  - 40));
        ph = Math.min(540, Math.max(280, height - 40));
        px = (width  - pw) / 2;
        py = (height - ph) / 2;
        lw = pw * 44 / 100;  // 44% → left panel

        int fx = px + 14, fw = lw - 26;
        int nameY = py + HH + 26;
        int descY = py + HH + 60;

        // Create text fields — NOT added as drawables (we render them manually
        // inside drawScreen so they stay inside the popup scale transform).
        nameField = new TextFieldWidget(textRenderer, fx, nameY, fw, 16, Text.literal("Name"));
        nameField.setMaxLength(64);
        nameField.setText(editName);
        nameField.setChangedListener(s -> editName = s);
        nameField.setDrawsBackground(false);
        addSelectableChild(nameField);

        descField = new TextFieldWidget(textRenderer, fx, descY, fw, 16, Text.literal("Description"));
        descField.setMaxLength(256);
        descField.setText(editDesc);
        descField.setChangedListener(s -> editDesc = s);
        descField.setDrawsBackground(false);
        addSelectableChild(descField);
    }

    // ═════════════════════════════════════════════════════════════════
    // drawScreen — called by BasePopupScreen inside the scale transform
    // ═════════════════════════════════════════════════════════════════
    @Override
    protected void drawScreen(DrawContext ctx, int mx, int my, float delta) {
        // Animate toggles per-frame
        boolean[] vals = {editLoop, editSkipMismatch, editMineOnlyDefinedTargets, editAttackDanger, editOnlyGround,
            editLockCam, editAttackEnabled, editAttackWlOnly, editRandomAttackCps};
        for (int i = 0; i < 9; i++) tgAnim[i] = Anim.smooth(tgAnim[i], vals[i] ? 1f : 0f, 20f);

        // Reset hit caches
        for (int[] b : tgBounds)  b[2] = -1;
        for (int[] c : cycBounds) c[3] = 0;
        atkRngY = -1;
        hoverSave = hoverCancel = hoverAdd = hoverUp = hoverDown = hoverRem = false;

        // Panel background
        RoundedRectRenderer.draw(ctx, px, py, pw, ph, R, EasyBlockGui.C_BG);

        // Header
        ctx.fill(px, py, px + pw, py + HH, EasyBlockGui.C_PANEL_HEAD);
        ctx.fill(px, py + HH - 1, px + pw, py + HH, EasyBlockGui.C_DIVIDER);
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("\u00A7e\u2605 \u00A7f\u00A7lEdit: " + macro.getName()),
                px + 14, py + (HH - 8) / 2, EasyBlockGui.C_TEXT);

        // Dividers
        ctx.fill(px + lw, py + HH, px + lw + 1, py + ph, EasyBlockGui.C_DIVIDER);
        ctx.fill(px, py + ph - FH, px + pw, py + ph - FH + 1, EasyBlockGui.C_DIVIDER);

        drawLeftPanel(ctx, mx, my);
        drawRightPanel(ctx, mx, my);
        drawFooter(ctx, mx, my);
    }

    // ═════════════════════════════════════════════════════════════════
    // Left panel
    // ═════════════════════════════════════════════════════════════════
    private void drawLeftPanel(DrawContext ctx, int mx, int my) {
        int x   = px + 14;
        int re  = px + lw - 4;   // right edge for controls
        int fw  = lw - 26;
        int dy  = py + HH + 8;

        // ── Fixed section: Name + Description ─────────────────────────
        ctx.drawTextWithShadow(textRenderer, Text.literal("Name"), x, dy, EasyBlockGui.C_TEXT2);
        dy += 12;
        ctx.fill(x - 2, dy - 1, x + fw + 2, dy + 17, EasyBlockGui.C_CARD);
        // Focus indicator underline
        ctx.fill(x - 2, dy + 16, x + fw + 2, dy + 17,
                nameField.isFocused() ? EasyBlockGui.C_ACCENT : EasyBlockGui.C_DIVIDER);
        nameField.setPosition(x, dy);
        nameField.setWidth(fw);
        nameField.render(ctx, mx, my, 0f);
        dy += 22;

        ctx.drawTextWithShadow(textRenderer, Text.literal("Description"), x, dy, EasyBlockGui.C_TEXT2);
        dy += 12;
        ctx.fill(x - 2, dy - 1, x + fw + 2, dy + 17, EasyBlockGui.C_CARD);
        ctx.fill(x - 2, dy + 16, x + fw + 2, dy + 17,
                descField.isFocused() ? EasyBlockGui.C_ACCENT : EasyBlockGui.C_DIVIDER);
        descField.setPosition(x, dy);
        descField.setWidth(fw);
        descField.render(ctx, mx, my, 0f);
        dy += 22;

        // Separator
        ctx.fill(x, dy + 2, re, dy + 3, EasyBlockGui.C_DIVIDER);
        dy += 8;

        // Store where scrollable section begins
        scrollStartY = dy;
        int scrollBot = py + ph - FH - 4;
        int visH      = scrollBot - scrollStartY;

        // ── Scrollable section with scissor clipping ──────────────────
        ctx.enableScissor(px, scrollStartY, px + lw, scrollBot);
        int sy = scrollStartY - leftScroll;

        // Config
        sy = section(ctx, x, re, sy, "Config", EasyBlockGui.C_ACCENT);
        sy = toggle(ctx, mx, my, x, re, sy, "Loop",           tgAnim[0], 0) + ROW_GAP;
        sy = toggle(ctx, mx, my, x, re, sy, "Skip Mismatch",  tgAnim[1], 1) + ROW_GAP;
        sy = toggle(ctx, mx, my, x, re, sy, "Only Defined Blocks", tgAnim[2], 2) + ROW_GAP;
        sy = toggle(ctx, mx, my, x, re, sy, "Attack Danger",  tgAnim[3], 3) + ROW_GAP;
        sy = toggle(ctx, mx, my, x, re, sy, "Only Ground",    tgAnim[4], 4) + ROW_GAP;
        sy = toggle(ctx, mx, my, x, re, sy, "Lock Camera",    tgAnim[5], 5) + 8;

        // Timing
        sy = section(ctx, x, re, sy, "Timing", EasyBlockGui.C_ACCENT);
        sy = cycler(ctx, mx, my, x, re, sy, "Mining Delay",   editMiningDelay + " ms",               0) + ROW_GAP;
        sy = cycler(ctx, mx, my, x, re, sy, "Move Timeout",   editMoveTimeout + " ms",               1) + ROW_GAP;
        sy = cycler(ctx, mx, my, x, re, sy, "Arrival Radius", String.format("%.1f blk", editArrivalRadius), 2) + 8;

        // Attack
        sy = section(ctx, x, re, sy, "Attack", EasyBlockGui.C_DANGER);
        sy = toggle(ctx, mx, my, x, re, sy, "Enable Attack", tgAnim[6], 6) + ROW_GAP;

        if (editAttackEnabled) {
            if (editAttackDanger) {
                sy = cycler(ctx, mx, my, x, re, sy, "Attack CPS", editAttackCPS + " CPS", 3) + ROW_GAP;
                sy = toggle(ctx, mx, my, x, re, sy, "Random CPS (7\u201311)", tgAnim[8], 8) + ROW_GAP;
            }
            sy = toggle(ctx, mx, my, x, re, sy, "Whitelist only", tgAnim[7], 7) + ROW_GAP;

            // Range
            sy = rangeRow(ctx, mx, my, x, re, sy) + ROW_GAP;

            // Entity whitelist
            if (editAttackWlOnly) {
                sy = entityList(ctx, mx, my, x, re, sy);
            }
        }

        ctx.disableScissor();

        // Update scroll max from total rendered height
        int totalContentH = (sy + leftScroll) - scrollStartY;
        leftScrollMax = Math.max(0, totalContentH - visH);
        leftScroll    = Math.min(leftScroll, leftScrollMax);

        // Scrollbar
        if (leftScrollMax > 0) {
            int trackX = px + lw - 5, trackH = visH;
            ctx.fill(trackX, scrollStartY, trackX + 3, scrollStartY + trackH, EasyBlockGui.C_DIVIDER);
            int thumbH = Math.max(16, trackH * visH / Math.max(1, totalContentH));
            int thumbY = scrollStartY + (int)((long) leftScroll * (trackH - thumbH) / leftScrollMax);
            ctx.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, EasyBlockGui.C_ACCENT);
        }
    }

    // ── Section header ────────────────────────────────────────────────
    private int section(DrawContext ctx, int x, int re, int y, String title, int accent) {
        ctx.fill(x, y, re, y + 1, EasyBlockGui.C_DIVIDER);
        ctx.drawTextWithShadow(textRenderer, Text.literal(title), x, y + 5, EasyBlockGui.C_TEXT);
        RoundedRectRenderer.draw(ctx, x, y + 17, textRenderer.getWidth(title), 2, 1, accent);
        return y + 24;
    }

    // ── Toggle row ────────────────────────────────────────────────────
    private int toggle(DrawContext ctx, int mx, int my, int x, int re, int y,
                       String label, float anim, int idx) {
        int tW = ToggleRenderer.TOGGLE_W, tH = ToggleRenderer.TOGGLE_H;
        int rowH = tH + 4;
        ctx.drawTextWithShadow(textRenderer, Text.literal(label), x, y + (rowH - 8) / 2, EasyBlockGui.C_TEXT);
        int tgX = re - tW - 2, tgY = y + (rowH - tH) / 2;
        ToggleRenderer.draw(ctx, tgX, tgY, anim);
        tgBounds[idx][0] = tgX; tgBounds[idx][1] = tgY;
        tgBounds[idx][2] = tW;  tgBounds[idx][3] = tH;
        return y + rowH;
    }

    // ── Cycler row (< value >) ────────────────────────────────────────
    private int cycler(DrawContext ctx, int mx, int my, int x, int re, int y,
                       String label, String val, int idx) {
        ctx.drawTextWithShadow(textRenderer, Text.literal(label), x, y + 5, EasyBlockGui.C_TEXT2);
        int bw = 14, vh = 18;
        int valW = textRenderer.getWidth(val) + 10;
        int rbx  = re - bw - 2;
        int lbx  = rbx - valW - 2 - bw;
        boolean hl = hit(mx, my, lbx, y, bw, vh), hr = hit(mx, my, rbx, y, bw, vh);
        RoundedRectRenderer.draw(ctx, lbx,       y, bw,   vh, RB, hl ? EasyBlockGui.C_NAV_ACT : EasyBlockGui.C_NAV_BG);
        RoundedRectRenderer.draw(ctx, lbx+bw+2,  y, valW, vh, RB, EasyBlockGui.C_CARD);
        RoundedRectRenderer.draw(ctx, rbx,        y, bw,   vh, RB, hr ? EasyBlockGui.C_NAV_ACT : EasyBlockGui.C_NAV_BG);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("\u2039"), lbx + bw/2, y + 5, EasyBlockGui.C_TEXT);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(val), lbx+bw+2+valW/2, y + 5, EasyBlockGui.C_TEXT);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("\u203A"), rbx + bw/2, y + 5, EasyBlockGui.C_TEXT);
        cycBounds[idx][0] = lbx; cycBounds[idx][1] = rbx;
        cycBounds[idx][2] = y;   cycBounds[idx][3] = 1;   // active
        return y + vh;
    }

    // ── Attack range row ──────────────────────────────────────────────
    private int rangeRow(DrawContext ctx, int mx, int my, int x, int re, int y) {
        ctx.drawTextWithShadow(textRenderer, Text.literal("Range"), x, y + 5, EasyBlockGui.C_TEXT2);
        String rv = editAttackRange + " blk";
        int bw = 14, vh = 18, valW = textRenderer.getWidth(rv) + 10;
        int rbx = re - bw - 2, lbx = rbx - valW - 2 - bw;
        boolean hl = hit(mx, my, lbx, y, bw, vh), hr = hit(mx, my, rbx, y, bw, vh);
        RoundedRectRenderer.draw(ctx, lbx,      y, bw,   vh, RB, hl ? EasyBlockGui.C_NAV_ACT : EasyBlockGui.C_NAV_BG);
        RoundedRectRenderer.draw(ctx, lbx+bw+2, y, valW, vh, RB, EasyBlockGui.C_CARD);
        RoundedRectRenderer.draw(ctx, rbx,       y, bw,   vh, RB, hr ? EasyBlockGui.C_NAV_ACT : EasyBlockGui.C_NAV_BG);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("\u2039"), lbx + bw/2, y + 5, EasyBlockGui.C_TEXT);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(rv), lbx+bw+2+valW/2, y + 5, EasyBlockGui.C_TEXT);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("\u203A"), rbx + bw/2, y + 5, EasyBlockGui.C_TEXT);
        atkRngLbx = lbx; atkRngRbx = rbx; atkRngY = y;
        return y + vh;
    }

    // ── Entity whitelist ──────────────────────────────────────────────
    private int entityList(DrawContext ctx, int mx, int my, int x, int re, int y) {
        refreshNearby();
        ctx.drawTextWithShadow(textRenderer, Text.literal("Entity whitelist:"), x, y, EasyBlockGui.C_TEXT2);
        y += 14;
        int listW = re - 4 - x;
        elX = x; elW = listW; elRowY0 = y;
        elShown.clear();
        elShown.addAll(nearbyTypes);

        int totalItems  = elShown.size();
        int visibleRows = Math.min(totalItems, EL_MAX_VISIBLE);
        int listH       = visibleRows * EL_ROW_H;
        int maxScroll   = Math.max(0, totalItems - EL_MAX_VISIBLE);
        elScroll = Math.max(0, Math.min(elScroll, maxScroll));

        boolean needScroll = totalItems > EL_MAX_VISIBLE;
        int sbW    = needScroll ? 4 : 0;
        int innerW = listW - (needScroll ? sbW + 2 : 0);

        for (int i = 0; i < totalItems; i++) {
            String id = elShown.get(i);
            int ey    = y + (i - elScroll) * EL_ROW_H;
            if (ey + EL_ROW_H <= y || ey >= y + listH) continue;
            boolean wl = editAttackWl.contains(id);
            boolean hv = hit(mx, my, x, ey, innerW, EL_ROW_H);
                RoundedRectRenderer.draw(ctx, x, ey, innerW, EL_ROW_H, RB,
                    wl ? EasyBlockGui.C_CARD_SEL : (hv ? EasyBlockGui.C_NAV_ACT : EasyBlockGui.C_CARD));
            if (wl) ctx.fill(x, ey + 3, x + 3, ey + EL_ROW_H - 3, EasyBlockGui.C_ACCENT);
            String disp = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
            disp = Character.toUpperCase(disp.charAt(0)) + disp.substring(1).replace('_', ' ');
            ctx.drawTextWithShadow(textRenderer, Text.literal(disp), x + 8, ey + (EL_ROW_H - 8) / 2,
                    wl ? EasyBlockGui.C_TEXT : EasyBlockGui.C_TEXT2);
        }

        if (needScroll && listH > 0 && maxScroll > 0) {
            int sbX    = x + innerW + 2;
            int thumbH = Math.max(8, listH * visibleRows / totalItems);
            int thumbY = y + (int)((long) elScroll * (listH - thumbH) / maxScroll);
            ctx.fill(sbX, y, sbX + sbW, y + listH, EasyBlockGui.C_DIVIDER);
            ctx.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, EasyBlockGui.C_ACCENT);
        }

        return y + listH + 4;
    }

    // ─── Nearby entity types ──────────────────────────────────────────
    private final List<String> nearbyTypes = new ArrayList<>();
    private void refreshNearby() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        Set<String> seen = new LinkedHashSet<>();
        mc.world.getEntitiesByClass(LivingEntity.class,
                        mc.player.getBoundingBox().expand(20), e -> !e.equals(mc.player))
                .forEach(e -> seen.add(Registries.ENTITY_TYPE.getId(e.getType()).toString()));
        seen.addAll(editAttackWl);
        nearbyTypes.clear();
        nearbyTypes.addAll(seen);
    }

    // ═════════════════════════════════════════════════════════════════
    // Right panel — step list
    // ═════════════════════════════════════════════════════════════════
    private void drawRightPanel(DrawContext ctx, int mx, int my) {
        int rx = px + lw + 12;
        int rw = pw - lw - 22;
        int dy = py + HH + 10;

        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Steps (" + macro.getSteps().size() + ")"),
                rx, dy, EasyBlockGui.C_TEXT);
        dy += 16;

        // Action buttons
        stepBtnH = 22;
        int gap  = 4;
        stepBtnW = (rw - gap * 3) / 4;
        String[] lbls = {"+ Add", "\u25B2 Up", "\u25BC Down", "\u2717 Rem"};
        int[] bgs  = {EasyBlockGui.C_ACCENT, EasyBlockGui.C_NAV_BG, EasyBlockGui.C_NAV_BG, 0xFF8B3A3A};
        int[] hbgs = {EasyBlockGui.C_ACCENT_HI, EasyBlockGui.C_NAV_ACT, EasyBlockGui.C_NAV_ACT, EasyBlockGui.C_DANGER};
        stepBtnY = dy;
        for (int i = 0; i < 4; i++) {
            stepBtnX[i] = rx + i * (stepBtnW + gap);
            boolean hv = hit(mx, my, stepBtnX[i], dy, stepBtnW, stepBtnH);
            RoundedRectRenderer.draw(ctx, stepBtnX[i], dy, stepBtnW, stepBtnH, RB, hv ? hbgs[i] : bgs[i]);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(lbls[i]),
                    stepBtnX[i] + stepBtnW / 2, dy + (stepBtnH - 8) / 2, EasyBlockGui.C_TEXT);
            switch (i) { case 0 -> hoverAdd = hv; case 1 -> hoverUp = hv;
                         case 2 -> hoverDown = hv; case 3 -> hoverRem = hv; }
        }
        dy += stepBtnH + 6;

        // Step entries
        int entH   = 30;
        int listBot = py + ph - FH - 6;
        List<MacroStep> steps = macro.getSteps();

        ctx.enableScissor(rx, dy, rx + rw, listBot);
        if (steps.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, Text.literal("No steps yet."),
                    rx, dy + 14, EasyBlockGui.C_TEXT3);
        } else {
            int maxVis = Math.max(1, (listBot - dy) / entH);
            int end    = Math.min(stepScroll + maxVis, steps.size());
            for (int i = stepScroll; i < end; i++) {
                MacroStep step = steps.get(i);
                int ey = dy + (i - stepScroll) * entH;
                boolean sel = (i == selStep);
                boolean hv  = hit(mx, my, rx, ey, rw, entH - 2);
                RoundedRectRenderer.draw(ctx, rx, ey, rw, entH - 2, RB,
                        sel ? EasyBlockGui.C_CARD_SEL : (hv ? EasyBlockGui.C_CARD_HOV : EasyBlockGui.C_CARD));
                if (sel) ctx.fill(rx, ey + R, rx + 3, ey + entH - 2 - R, EasyBlockGui.C_ACCENT);
                String nm = (i + 1) + ". " + step.getLabel();
                if (nm.length() > 34) nm = nm.substring(0, 32) + "..";
                BlockPos d = step.getDestination();
                String co = d.getX() + "," + d.getY() + "," + d.getZ()
                        + " (" + step.getTargets().size() + " blk)";
                ctx.drawTextWithShadow(textRenderer, Text.literal(nm), rx + 8, ey + 5, EasyBlockGui.C_TEXT);
                ctx.drawTextWithShadow(textRenderer, Text.literal(co), rx + 8, ey + 17, EasyBlockGui.C_TEXT2);
            }
        }
        ctx.disableScissor();
    }

    // ═════════════════════════════════════════════════════════════════
    // Footer
    // ═════════════════════════════════════════════════════════════════
    private void drawFooter(DrawContext ctx, int mx, int my) {
        footerBtnY = py + ph - FH + (FH - fbH) / 2;
        int tot = fbW * 2 + 12;
        saveX   = px + (pw - tot) / 2;
        cancelX = saveX + fbW + 12;
        hoverSave   = hit(mx, my, saveX,   footerBtnY, fbW, fbH);
        hoverCancel = hit(mx, my, cancelX, footerBtnY, fbW, fbH);
        RoundedRectRenderer.draw(ctx, saveX,   footerBtnY, fbW, fbH, RB,
                hoverSave   ? EasyBlockGui.C_ACCENT_HI : EasyBlockGui.C_ACCENT);
        RoundedRectRenderer.draw(ctx, cancelX, footerBtnY, fbW, fbH, RB,
            hoverCancel ? EasyBlockGui.C_DANGER : 0xFF8B3A3A);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("\u2714  Save"),
                saveX + fbW / 2, footerBtnY + (fbH - 8) / 2, EasyBlockGui.C_TEXT);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("\u2717  Cancel"),
                cancelX + fbW / 2, footerBtnY + (fbH - 8) / 2, EasyBlockGui.C_TEXT);
    }

    // ═════════════════════════════════════════════════════════════════
    // Input
    // ═════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(Click click, boolean handled) {
        double mx = click.x(), my = click.y();
        int btn = click.button();
        int imx = (int) mx, imy = (int) my;

        // Click outside panel → close
        if (imx < px || imx > px + pw || imy < py || imy > py + ph) {
            animClose();
            return true;
        }

        // Fixed section (name/desc fields) — delegate to Screen children
        if (imx < px + lw && imy >= py + HH && imy < scrollStartY) {
            return super.mouseClicked(click, handled);
        }

        // Toggles
        for (int i = 0; i < 9; i++) {
            int[] b = tgBounds[i];
            if (b[2] > 0 && hit(imx, imy, b[0], b[1], b[2], b[3])) {
                switch (i) {
                    case 0 -> editLoop = !editLoop;
                    case 1 -> editSkipMismatch = !editSkipMismatch;
                    case 2 -> editMineOnlyDefinedTargets = !editMineOnlyDefinedTargets;
                    case 3 -> editAttackDanger = !editAttackDanger;
                    case 4 -> editOnlyGround = !editOnlyGround;
                    case 5 -> editLockCam = !editLockCam;
                    case 6 -> editAttackEnabled = !editAttackEnabled;
                    case 7 -> editAttackWlOnly = !editAttackWlOnly;
                    case 8 -> editRandomAttackCps = !editRandomAttackCps;
                }
                return true;
            }
        }

        // Cyclers
        for (int i = 0; i < 4; i++) {
            int[] c = cycBounds[i];
            if (c[3] == 0) continue;
            if (imy >= c[2] && imy < c[2] + 18) {
                if (imx >= c[0] && imx < c[0] + 14) { cycleNum(i, -1); return true; }
                if (imx >= c[1] && imx < c[1] + 14) { cycleNum(i, +1); return true; }
            }
        }

        // Attack range
        if (atkRngY >= 0 && imy >= atkRngY && imy < atkRngY + 18) {
            if (imx >= atkRngLbx && imx < atkRngLbx + 14) { editAttackRange = Math.max(2,  editAttackRange - 1); return true; }
            if (imx >= atkRngRbx && imx < atkRngRbx + 14) { editAttackRange = Math.min(50, editAttackRange + 1); return true; }
        }

        // Entity whitelist rows (only if in left panel scrollable area)
        if (editAttackEnabled && editAttackWlOnly && !elShown.isEmpty()
                && imx >= elX && imx < elX + elW) {
            int visRows = Math.min(elShown.size(), EL_MAX_VISIBLE);
            int listH   = visRows * EL_ROW_H;
            for (int i = 0; i < elShown.size(); i++) {
                int ey = elRowY0 + (i - elScroll) * EL_ROW_H;
                if (ey < elRowY0 || ey >= elRowY0 + listH) continue;
                if (imy >= ey && imy < ey + EL_ROW_H) {
                    String id = elShown.get(i);
                    if (editAttackWl.contains(id)) editAttackWl.remove(id);
                    else editAttackWl.add(id);
                    return true;
                }
            }
        }

        // Step buttons
        if (hoverAdd) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null)
                macro.addStep(new MacroStep("Step " + (macro.getSteps().size() + 1), mc.player.getBlockPos()));
            return true;
        }
        if (hoverUp && selStep > 0) {
            List<MacroStep> s = macro.getSteps(); s.add(--selStep, s.remove(selStep + 1)); return true;
        }
        if (hoverDown && selStep >= 0 && selStep < macro.getSteps().size() - 1) {
            List<MacroStep> s = macro.getSteps(); s.add(++selStep, s.remove(selStep - 1)); return true;
        }
        if (hoverRem && selStep >= 0 && selStep < macro.getSteps().size()) {
            macro.removeStep(selStep);
            if (selStep >= macro.getSteps().size()) selStep = macro.getSteps().size() - 1;
            return true;
        }

        // Step list rows
        int rx       = px + lw + 12;
        int listTop  = stepBtnY + stepBtnH + 6;
        if (imx >= rx && imy >= listTop) {
            int idx = stepScroll + (imy - listTop) / 30;
            if (idx >= 0 && idx < macro.getSteps().size()) { selStep = idx; return true; }
        }

        // Footer
        if (hoverSave)   { applyChanges(); animClose(); return true; }
        if (hoverCancel) { animClose(); return true; }

        return super.mouseClicked(click, handled);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        int imx = (int) mx; int imy = (int) my;
        if (imx < px + lw) {
            // Check if hovering the entity list sub-scroll area
            int listH = Math.min(elShown.size(), EL_MAX_VISIBLE) * EL_ROW_H;
            if (elShown.size() > EL_MAX_VISIBLE
                    && imx >= elX && imx < elX + elW
                    && imy >= elRowY0 && imy < elRowY0 + listH) {
                int maxScroll = elShown.size() - EL_MAX_VISIBLE;
                elScroll = Math.max(0, Math.min(maxScroll, elScroll - (int) vAmt));
            } else {
                // Left panel scroll
                leftScroll = Math.max(0, Math.min(leftScrollMax, leftScroll - (int)(vAmt * 14)));
            }
        } else {
            // Right panel: step list scroll
            int maxStep = Math.max(0, macro.getSteps().size() - 5);
            stepScroll = Math.max(0, Math.min(maxStep, stepScroll - (int) vAmt));
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput key) {
        int keyCode = key.key();
        // Escape: unfocus field first, then close on second press
        if (keyCode == 256) {
            if (getFocused() != null) {
                setFocused(null);
                return true;
            }
            animClose();
            return true;
        }
        return super.keyPressed(key);
    }

    // ═════════════════════════════════════════════════════════════════
    // Helpers
    // ═════════════════════════════════════════════════════════════════

    private static boolean hit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void cycleNum(int idx, int dir) {
        switch (idx) {
            case 0 -> editMiningDelay  = wrap(editMiningDelay  + dir * 50,   50,   2000);
            case 1 -> editMoveTimeout  = wrap(editMoveTimeout  + dir * 500,  500,  60000);
            case 2 -> { int s = Math.round(editArrivalRadius / 0.5f) + dir; editArrivalRadius = wrap(s, 1, 10) * 0.5f; }
            case 3 -> editAttackCPS    = wrap(editAttackCPS    + dir,        1,    20);
        }
    }

    private static int wrap(int v, int lo, int hi) {
        if (v < lo) return hi; if (v > hi) return lo; return v;
    }

    private void applyChanges() {
        if (editName != null && !editName.isEmpty()) macro.setName(editName);
        macro.setDescription(editDesc);
        MacroConfig c = macro.getConfig();
        c.setLoop(editLoop);               c.setSkipMismatch(editSkipMismatch);
        c.setMineOnlyDefinedTargets(editMineOnlyDefinedTargets);
        c.setAttackDanger(editAttackDanger); c.setAttackCPS(editAttackCPS);
        c.setRandomAttackCps(editRandomAttackCps);
        c.setOnlyGround(editOnlyGround);   c.setLockCrosshair(editLockCam);
        c.setMiningDelay(editMiningDelay);  c.setMoveTimeout(editMoveTimeout);
        c.setArrivalRadius(editArrivalRadius); c.setAttackEnabled(editAttackEnabled);
        c.setAttackWhitelistOnly(editAttackWlOnly); c.setAttackWhitelist(editAttackWl);
        c.setAttackRange(editAttackRange);
        MacroModClient.getManager().save(macro);
    }
}
